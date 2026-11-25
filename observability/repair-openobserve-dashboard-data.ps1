$ErrorActionPreference = 'Stop'

$BaseUrl = 'http://127.0.0.1:5080'
$Organization = 'default'
$Headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin@example.com:ChangeThis-StrongPassword2026!')) }

function Make-Panel($Id, $Title, $Stream, $Query, $X, $Y) {
    [ordered]@{
        id = $Id
        type = 'table'
        title = $Title
        description = ''
        config = [ordered]@{ show_legends = $false; legends_position = $null; decimals = 2; drilldown = @() }
        queryType = 'sql'
        queries = @([ordered]@{
            query = $Query
            vrlFunctionQuery = ''
            customQuery = $true
            fields = [ordered]@{
                stream = $Stream
                stream_type = 'logs'
                x = @()
                y = @()
                z = @()
                breakdown = @()
                filter = [ordered]@{ filterType = 'group'; logicalOperator = 'AND'; conditions = @() }
            }
            config = [ordered]@{ promql_legend = ''; layer_type = 'scatter'; weight_fixed = 1; limit = 50; min = 0; max = 100; time_shift = @() }
        })
        layout = [ordered]@{ x = $X; y = $Y; w = 12; h = 8; i = ([int]([Math]::Floor([double]$X / 12) + [Math]::Floor([double]$Y / 8) * 2 + 1)) }
        htmlContent = ''
        markdownContent = ''
        customChartContent = ''
    }
}

function Repair-Dashboard($Id, $Service, $LogStream) {
    $EnvelopeJson = & curl.exe -u 'admin@example.com:ChangeThis-StrongPassword2026!' -s "$BaseUrl/api/$Organization/dashboards/$Id?folder=default"
    $Envelope = $EnvelopeJson | ConvertFrom-Json
    $Inner = $Envelope.v8

    $Panels = @(
        (Make-Panel "${Service}-metrics" "$($Inner.title) Metrics" 'spring_metrics' "SELECT _timestamp, metric, status, value FROM spring_metrics WHERE service = '$Service' LIMIT 100" 0 0),
        (Make-Panel "${Service}-api" "$($Inner.title) API Metrics" 'spring_metrics' "SELECT _timestamp, metric, value FROM spring_metrics WHERE service = '$Service' AND metric LIKE 'http_server_requests%' LIMIT 100" 12 0),
        (Make-Panel "${Service}-jvm" "$($Inner.title) JVM Metrics" 'spring_metrics' "SELECT _timestamp, metric, value FROM spring_metrics WHERE service = '$Service' AND metric LIKE 'jvm_%' LIMIT 100" 0 8),
        (Make-Panel "${Service}-database" "$($Inner.title) Database Metrics" 'spring_metrics' "SELECT _timestamp, metric, value FROM spring_metrics WHERE service = '$Service' AND metric LIKE 'hikaricp_%' LIMIT 100" 12 8),
        (Make-Panel "${Service}-kafka" "$($Inner.title) Kafka Metrics" 'spring_metrics' "SELECT _timestamp, metric, value FROM spring_metrics WHERE service = '$Service' AND metric LIKE 'spring_kafka%' LIMIT 100" 0 16),
        (Make-Panel "${Service}-outbox-inbox" "$($Inner.title) Outbox and Inbox" 'spring_metrics' "SELECT _timestamp, metric, status, value FROM spring_metrics WHERE service = '$Service' AND (metric = 'securities_messaging_outbox_events' OR metric = 'securities_messaging_inbox_events') LIMIT 100" 12 16),
        (Make-Panel "${Service}-logs" "$($Inner.title) Application Logs" $LogStream "SELECT * FROM $LogStream LIMIT 100" 0 24)
    )

    $TabsValue = @([ordered]@{ tabId = 'default'; name = 'Default'; panels = $Panels })
    $VariablesValue = [ordered]@{ list = @(); showDynamicFilters = $true }
    $DurationValue = [ordered]@{ type = 'relative'; relativeTimePeriod = '30m' }
    $Inner | Add-Member -NotePropertyName tabs -NotePropertyValue $TabsValue -Force
    $Inner | Add-Member -NotePropertyName variables -NotePropertyValue $VariablesValue -Force
    $Inner | Add-Member -NotePropertyName defaultDatetimeDuration -NotePropertyValue $DurationValue -Force

    $TempFile = Join-Path $env:TEMP "$Id-dashboard.json"
    $Json = $Inner | ConvertTo-Json -Depth 30
    [IO.File]::WriteAllText($TempFile, $Json, [Text.UTF8Encoding]::new($false))

    & curl.exe -u 'admin@example.com:ChangeThis-StrongPassword2026!' `
        -X PUT `
        -H 'Content-Type: application/json' `
        --data-binary "@$TempFile" `
        "$BaseUrl/api/$Organization/dashboards/$Id?folder=default&hash=$($Inner.hash)"
}

Repair-Dashboard '7530935381722660864' 'borrower-service' 'borrower_service'
Repair-Dashboard '7530935381785575424' 'custodian-service' 'custodian_service'
