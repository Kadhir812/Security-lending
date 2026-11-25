$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot
$LogsDirectory = Join-Path $Root 'logs'
$OpenObserveUrl = 'http://127.0.0.1:5080'
$Organization = 'default'
$Username = 'admin@example.com'
$Password = 'ChangeThis-StrongPassword2026!'
$BasicAuth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}"))

New-Item -ItemType Directory -Force -Path $LogsDirectory | Out-Null

$FileMap = @{
    'borrower-service.log' = 'borrower_service'
    'custodian-service.log' = 'custodian_service'
    'valuation-service.log' = 'valuation_service'
    'security-market-service.log' = 'security_market_service'
}

$Positions = @{}

function Send-LogBatch {
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

Write-Host "Shipping application logs to OpenObserve at $OpenObserveUrl"
Write-Host "Press Ctrl+C to stop."

while ($true) {
    foreach ($FileName in $FileMap.Keys) {
        $Stream = $FileMap[$FileName]
        $Path = Join-Path $LogsDirectory $FileName
        if (-not (Test-Path -LiteralPath $Path)) { continue }

        $Position = if ($Positions.ContainsKey($FileName)) { [long]$Positions[$FileName] } else { 0 }
        $Length = (Get-Item -LiteralPath $Path).Length

        if ($Length -lt $Position) { $Position = 0 }
        if ($Length -eq $Position) { continue }

        try {
            $StreamReader = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::Read, [IO.FileShare]::ReadWrite)
            $StreamReader.Position = $Position
            $Reader = [IO.StreamReader]::new($StreamReader)
            $Text = $Reader.ReadToEnd()
            $Reader.Close()
            $StreamReader.Close()

            $Records = @()
            foreach ($Line in $Text -split "`r`?`n" | Where-Object { $_.Trim() }) {
                $Record = $null
                try {
                    $Record = $Line | ConvertFrom-Json -ErrorAction Stop
                } catch {
                    $Record = @{ message = $Line; source = $Stream }
                }
                if ($Record -ne $null) {
                    $Record | Add-Member -NotePropertyName source -NotePropertyValue $Stream -Force
                    $Records += $Record
                }
            }

            Send-LogBatch -Stream $Stream -Records $Records
            $Positions[$FileName] = $Length
        } catch {
            Write-Warning "Could not ship $FileName`: $($_.Exception.Message)"
        }
    }

    Start-Sleep -Milliseconds 1000
}
