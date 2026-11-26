$ErrorActionPreference = 'Stop'

$BaseUrl = 'http://127.0.0.1:5080'
$Organization = 'default'
$Username = 'admin@example.com'
$Password = 'ChangeThis-StrongPassword2026!'
$Headers = @{ Authorization = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${Username}:${Password}")) }

function Panel($Id, $Title, $Type, $Stream, $Query, $X, $Y, $W, $H) {
    [ordered]@{
        id = $Id
        type = $Type
        title = $Title
        description = ''
        config = [ordered]@{ show_legends = $true; legends_position = $null; decimals = 2; drilldown = @() }
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
            config = [ordered]@{ promql_legend = ''; layer_type = 'scatter'; weight_fixed = 1; limit = 0; min = 0; max = 100; time_shift = @() }
        })
        layout = [ordered]@{ x = $X; y = $Y; w = $W; h = $H; i = ([int]([Math]::Floor([double]$X / 12) + [Math]::Floor([double]$Y / 8) * 2 + 1)) }
        htmlContent = ''
        markdownContent = ''
        customChartContent = ''
    }
}

function Create-Dashboard($Title, $Description, $Panels) {
    $Payload = [ordered]@{
        version = 8
        title = $Title
        description = $Description
        tabs = @([ordered]@{
            tabId = 'default'
            name = 'Default'
            panels = $Panels
        })
        variables = [ordered]@{ list = @(); showDynamicFilters = $true }
        defaultDatetimeDuration = [ordered]@{ type = 'relative'; relativeTimePeriod = '30m' }
    }

    Invoke-RestMethod `
        -Uri "$BaseUrl/api/$Organization/dashboards?folder=default" `
        -Method Post `
        -Headers $Headers `
        -ContentType 'application/json' `
        -Body ($Payload | ConvertTo-Json -Depth 30) `
        -TimeoutSec 30
}

foreach ($DashboardId in @('7530933348496048128', '7530933348856758272')) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/$Organization/dashboards/$DashboardId?folder=default" -Method Delete -Headers $Headers -TimeoutSec 30 | Out-Null
    } catch {
        Write-Warning "Could not remove dashboard $DashboardId`: $($_.Exception.Message)"
    }
}

$BorrowerPanels = @(
    (Panel 'borrower-api' 'Borrower API Requests' 'bar' 'spring_metrics' 'SELECT metric, count(*) AS "events" FROM "spring_metrics" WHERE service = ''borrower-service'' AND metric LIKE ''http_server_requests%'' GROUP BY metric' 0 0 12 8),
    (Panel 'borrower-jvm' 'Borrower JVM Memory' 'line' 'spring_metrics' 'SELECT metric, avg(value) AS "average" FROM "spring_metrics" WHERE service = ''borrower-service'' AND metric LIKE ''jvm_memory%'' GROUP BY metric' 12 0 12 8),
    (Panel 'borrower-hikari' 'Borrower Database Pool' 'bar' 'spring_metrics' 'SELECT metric, avg(value) AS "average" FROM "spring_metrics" WHERE service = ''borrower-service'' AND metric LIKE ''hikaricp%'' GROUP BY metric' 0 8 12 8),
    (Panel 'borrower-kafka' 'Borrower Kafka Metrics' 'bar' 'spring_metrics' 'SELECT metric, count(*) AS "events" FROM "spring_metrics" WHERE service = ''borrower-service'' AND metric LIKE ''spring_kafka%'' GROUP BY metric' 12 8 12 8),
    (Panel 'borrower-outbox' 'Borrower Outbox by Status' 'bar' 'spring_metrics' 'SELECT status, avg(value) AS "count" FROM "spring_metrics" WHERE service = ''borrower-service'' AND metric = ''securities_messaging_outbox_events'' GROUP BY status' 0 16 12 8),
    (Panel 'borrower-inbox' 'Borrower Inbox by Status' 'bar' 'spring_metrics' 'SELECT status, avg(value) AS "count" FROM "spring_metrics" WHERE service = ''borrower-service'' AND metric = ''securities_messaging_inbox_events'' GROUP BY status' 12 16 12 8),
    (Panel 'borrower-logs' 'Borrower Application Logs' 'table' 'borrower_service' 'SELECT * FROM "borrower_service" LIMIT 50' 0 24 24 8)
)

$CustodianPanels = @(
    (Panel 'custodian-api' 'Custodian API Requests' 'bar' 'spring_metrics' 'SELECT metric, count(*) AS "events" FROM "spring_metrics" WHERE service = ''custodian-service'' AND metric LIKE ''http_server_requests%'' GROUP BY metric' 0 0 12 8),
    (Panel 'custodian-jvm' 'Custodian JVM Memory' 'line' 'spring_metrics' 'SELECT metric, avg(value) AS "average" FROM "spring_metrics" WHERE service = ''custodian-service'' AND metric LIKE ''jvm_memory%'' GROUP BY metric' 12 0 12 8),
    (Panel 'custodian-hikari' 'Custodian Database Pool' 'bar' 'spring_metrics' 'SELECT metric, avg(value) AS "average" FROM "spring_metrics" WHERE service = ''custodian-service'' AND metric LIKE ''hikaricp%'' GROUP BY metric' 0 8 12 8),
    (Panel 'custodian-kafka' 'Custodian Kafka Metrics' 'bar' 'spring_metrics' 'SELECT metric, count(*) AS "events" FROM "spring_metrics" WHERE service = ''custodian-service'' AND metric LIKE ''spring_kafka%'' GROUP BY metric' 12 8 12 8),
    (Panel 'custodian-outbox' 'Custodian Outbox by Status' 'bar' 'spring_metrics' 'SELECT status, avg(value) AS "count" FROM "spring_metrics" WHERE service = ''custodian-service'' AND metric = ''securities_messaging_outbox_events'' GROUP BY status' 0 16 12 8),
    (Panel 'custodian-inbox' 'Custodian Inbox by Status' 'bar' 'spring_metrics' 'SELECT status, avg(value) AS "count" FROM "spring_metrics" WHERE service = ''custodian-service'' AND metric = ''securities_messaging_inbox_events'' GROUP BY status' 12 16 12 8),
    (Panel 'custodian-logs' 'Custodian Application Logs' 'table' 'custodian_service' 'SELECT * FROM "custodian_service" LIMIT 50' 0 24 24 8)
)

$BorrowerResult = Create-Dashboard 'Security Lending - Borrower' 'Borrower API, JVM, database, Kafka, outbox, inbox, and application lifecycle monitoring.' $BorrowerPanels
$CustodianResult = Create-Dashboard 'Security Lending - Custodian' 'Custodian API, JVM, database, Kafka, outbox, inbox, settlement, and application lifecycle monitoring.' $CustodianPanels

Write-Host 'Borrower dashboard:'
$BorrowerResult | ConvertTo-Json -Depth 10
Write-Host 'Custodian dashboard:'
$CustodianResult | ConvertTo-Json -Depth 10
