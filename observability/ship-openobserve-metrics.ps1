$ErrorActionPreference = 'Stop'

$OpenObserveUrl = 'http://127.0.0.1:5080'
$Organization = 'default'
$Username = 'admin@example.com'
$Password = 'ChangeThis-StrongPassword2026!'
$BasicAuth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))

$Services = @(
    @{ name = 'borrower-service'; url = 'http://127.0.0.1:8082/actuator/prometheus' },
    @{ name = 'custodian-service'; url = 'http://127.0.0.1:8081/actuator/prometheus' },
    @{ name = 'valuation-service'; url = 'http://127.0.0.1:8083/actuator/prometheus' },
    @{ name = 'security-market-service'; url = 'http://127.0.0.1:8080/actuator/prometheus' }
)

function Send-Metrics {
    param([string]$Stream, [object[]]$Records)
    if ($Records.Count -eq 0) { return }

    $Body = $Records | ConvertTo-Json -Depth 8 -Compress
    if ($Records.Count -eq 1) { $Body = "[$Body]" }

    Invoke-RestMethod `
        -Uri "$OpenObserveUrl/api/$Organization/$Stream/_json" `
        -Method Post `
        -Headers @{ Authorization = "Basic $BasicAuth" } `
        -ContentType 'application/json' `
        -Body $Body `
        -TimeoutSec 15 | Out-Null
}

function Parse-PrometheusLabels {
    param([string]$LabelText)

    $Labels = @{}
    if (-not $LabelText) { return $Labels }

    foreach ($Match in [regex]::Matches($LabelText, '([A-Za-z_][A-Za-z0-9_]*)="((?:\\.|[^"\\])*)"')) {
        $Labels[$Match.Groups[1].Value] = $Match.Groups[2].Value.Replace('\"', '"').Replace('\\', '\')
    }
    return $Labels
}

Write-Host 'Shipping Spring Boot metrics to OpenObserve every 15 seconds.'
Write-Host 'Press Ctrl+C to stop.'

while ($true) {
    $Timestamp = [DateTime]::UtcNow.ToString('o')

    foreach ($Service in $Services) {
        try {
            $Text = Invoke-RestMethod -Uri $Service.url -Method Get -TimeoutSec 10
            $Records = @()

            foreach ($Line in ($Text -split "`r`?`n") | Where-Object { $_ -and -not $_.StartsWith('#') }) {
                if ($Line -match '^([A-Za-z_:][A-Za-z0-9_:]*)(?:\{([^}]*)\})?\s+(.+)$') {
                    $MetricName = $Matches[1]
                    $LabelText = $Matches[2]
                    $ValueText = $Matches[3].Trim()

                    $Value = 0.0
                    if ([double]::TryParse($ValueText, [Globalization.NumberStyles]::Float, [Globalization.CultureInfo]::InvariantCulture, [ref]$Value)) {
                        $Record = [ordered]@{
                            timestamp = $Timestamp
                            service = $Service.name
                            metric = $MetricName
                            value = $Value
                        }

                        foreach ($Entry in (Parse-PrometheusLabels $LabelText).GetEnumerator()) {
                            $Record[$Entry.Key] = $Entry.Value
                        }
                        $Records += [pscustomobject]$Record
                    }
                }
            }

            Send-Metrics -Stream 'spring_metrics' -Records $Records
        } catch {
            Write-Warning "Metrics unavailable for $($Service.name): $($_.Exception.Message)"
        }
    }

    Start-Sleep -Seconds 15
}
