$ErrorActionPreference = 'Stop'

$BaseUrl = 'http://127.0.0.1:5080'
$Organization = 'default'
$Auth = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes('admin@example.com:ChangeThis-StrongPassword2026!'))
$Headers = @{ Authorization = $Auth }

foreach ($Id in @('7530935381722660864','7530935381785575424')) {
    try {
        & curl.exe -u 'admin@example.com:ChangeThis-StrongPassword2026!' -s -X DELETE "$BaseUrl/api/$Organization/dashboards/$Id?folder=default" | Out-Null
    } catch {}
}

function Panel($Id, $Title, $Type, $Stream, $Query, $X, $Y, $W, $H) {
    $XFields = @()
    $YFields = @()
    if ($Type -eq 'table') {
        $XFields = @()
        $YFields = @()
    } elseif ($Query -match 'GROUP BY status') {
        $XFields = @([ordered]@{ label = 'status'; alias = 'status'; column = 'status'; color = $null })
        $YFields = @([ordered]@{ label = 'count'; alias = 'count'; column = 'count'; color = $null; aggregationFunction = 'avg' })
    } elseif ($Query -match 'AS "average"') {
        $XFields = @([ordered]@{ label = 'metric'; alias = 'metric'; column = 'metric'; color = $null })
        $YFields = @([ordered]@{ label = 'average'; alias = 'average'; column = 'average'; color = $null; aggregationFunction = 'avg' })
    } elseif ($Query -match 'AS "events"') {
        $XFields = @([ordered]@{ label = 'metric'; alias = 'metric'; column = 'metric'; color = $null })
        $YFields = @([ordered]@{ label = 'events'; alias = 'events'; column = 'events'; color = $null; aggregationFunction = 'sum' })
    } elseif ($Query -match 'AS "count"') {
        $XFields = @([ordered]@{ label = 'metric'; alias = 'metric'; column = 'metric'; color = $null })
        $YFields = @([ordered]@{ label = 'count'; alias = 'count'; column = 'count'; color = $null; aggregationFunction = 'sum' })
    } else {
        $XFields = @([ordered]@{ label = 'metric'; alias = 'metric'; column = 'metric'; color = $null })
        $YFields = @([ordered]@{ label = 'value'; alias = 'value'; column = 'value'; color = $null; aggregationFunction = 'avg' })
    }

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
                x = $XFields
                y = $YFields
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
        role = ''
        owner = 'admin@example.com'
        tabs = @([ordered]@{ tabId = 'default'; name = 'Default'; panels = $Panels })
        variables = [ordered]@{ list = @(); showDynamicFilters = $true }
        defaultDatetimeDuration = [ordered]@{ type = 'relative'; relativeTimePeriod = '24h' }
    }

    $Json = $Payload | ConvertTo-Json -Depth 30
    $Temp = Join-Path $env:TEMP 'openobserve-dashboard.json'
    [IO.File]::WriteAllText($Temp, $Json, [Text.UTF8Encoding]::new($false))

    & curl.exe -u 'admin@example.com:ChangeThis-StrongPassword2026!' `
        -X POST `
        -H 'Content-Type: application/json' `
        --data-binary "@$Temp" `
        "$BaseUrl/api/$Organization/dashboards?folder=default"
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

Create-Dashboard 'Security Lending - Borrower' 'Borrower API, JVM, database, Kafka, outbox, inbox, and application lifecycle monitoring.' $BorrowerPanels
Create-Dashboard 'Security Lending - Custodian' 'Custodian API, JVM, database, Kafka, outbox, inbox, settlement, and application lifecycle monitoring.' $CustodianPanels
