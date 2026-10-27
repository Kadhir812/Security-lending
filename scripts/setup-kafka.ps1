[CmdletBinding()]
param(
    [string]$ArchivePath
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$KafkaVersion = '4.4.0'
$KafkaArchiveName = 'kafka_2.13-{0}.tgz' -f $KafkaVersion
$KafkaArchiveUrl = 'https://dlcdn.apache.org/kafka/{0}/{1}' -f $KafkaVersion, $KafkaArchiveName
$KafkaChecksumUrl = "$KafkaArchiveUrl.sha512"
$KafkaHome = Join-Path $ProjectRoot 'kafka'
$KafkaStorageJar = Join-Path $KafkaHome "libs\kafka-storage-$KafkaVersion.jar"
$ProjectConfig = Join-Path $ProjectRoot 'config\kraft\server.properties'
$KafkaConfigDirectory = Join-Path $KafkaHome 'config\kraft'
$KafkaConfig = Join-Path $KafkaConfigDirectory 'server.properties'
$EnvFile = Join-Path $ProjectRoot 'config\kafka.env'
$RuntimeRoot = Join-Path $ProjectRoot 'kafka-runtime'
$DataDirectory = Join-Path $RuntimeRoot 'data'
$BrokerLogDirectory = Join-Path $RuntimeRoot 'brokER-logs'
$AppLogDirectory = Join-Path $ProjectRoot 'app-logs'

function Invoke-NativeCommand {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Push-Location $ProjectRoot
    try {
        $output = @(& $FilePath @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    if ($exitCode -ne 0) {
        $output | ForEach-Object { Write-Host $_ }
        throw "Command failed with exit code ${exitCode}: $FilePath"
    }

    return (($output | ForEach-Object { "$_".Trim() }) -join "`n").Trim()
}

function Invoke-KafkaStorageTool {
    param(
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    Push-Location $ProjectRoot
    try {
        $output = @(& java.exe `
            '-Dkafka.logs.dir=kafka-runtime/brokER-logs' `
            '-Dlog4j2.configurationFile=kafka/config/tools-log4j2.yaml' `
            '-cp' 'kafka/libs/*' `
            'kafka.tools.StorageTool' @Arguments 2>&1)
        $exitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
    if ($exitCode -ne 0) {
        $output | ForEach-Object { Write-Host $_ }
        throw "Kafka StorageTool failed with exit code ${exitCode}."
    }

    return (($output | ForEach-Object { "$_".Trim() }) -join "`n").Trim()
}

if (-not (Test-Path -LiteralPath $EnvFile -PathType Leaf)) {
    throw "Project Kafka environment file not found: $EnvFile"
}
if (-not (Test-Path -LiteralPath $ProjectConfig -PathType Leaf)) {
    throw "Project KRaft configuration not found: $ProjectConfig"
}
if (-not (Get-Command java.exe -ErrorAction SilentlyContinue)) {
    throw 'Java was not found on PATH. Install Java 17 or newer, then reopen PowerShell.'
}
if (-not (Get-Command tar.exe -ErrorAction SilentlyContinue)) {
    throw 'Windows tar.exe was not found on PATH; it is required to extract Kafka.'
}
[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12

$envLines = [System.IO.File]::ReadAllLines($EnvFile)
$clusterIdLines = @($envLines | Where-Object { $_ -match '^KAFKA_CLUSTER_ID=' })
if ($clusterIdLines.Count -ne 1) {
    throw "Expected exactly one KAFKA_CLUSTER_ID entry in $EnvFile"
}
$clusterId = ($clusterIdLines[0] -replace '^KAFKA_CLUSTER_ID=', '').Trim()

if (-not (Test-Path -LiteralPath $KafkaStorageJar -PathType Leaf)) {
    if (Test-Path -LiteralPath $KafkaHome) {
        $existingItems = @(Get-ChildItem -LiteralPath $KafkaHome -Force -ErrorAction SilentlyContinue)
        if ($existingItems.Count -gt 0) {
            throw "Kafka folder exists but does not contain the Windows tools: $KafkaStorage. Move or repair that folder, then rerun."
        }
    }

    if (-not $ArchivePath) {
        $ArchivePath = Join-Path ([System.IO.Path]::GetTempPath()) $KafkaArchiveName
        Write-Host "Downloading official Apache Kafka $KafkaVersion binary package..."
        Invoke-WebRequest -Uri $KafkaArchiveUrl -OutFile $ArchivePath
    }

    $ArchivePath = (Resolve-Path -LiteralPath $ArchivePath).Path
    Write-Host 'Fetching the official SHA-512 checksum...'
    $checksumText = (Invoke-WebRequest -Uri $KafkaChecksumUrl).Content
    $expectedHash = (($checksumText -replace '^[^:]*:\s*', '') -replace '\s', '').ToLowerInvariant()
    if ($expectedHash -notmatch '^[0-9a-f]{128}$') {
        throw "Could not parse Apache's SHA-512 checksum from $KafkaChecksumUrl"
    }
    $actualHash = (Get-FileHash -LiteralPath $ArchivePath -Algorithm SHA512).Hash.ToLowerInvariant()
    if ($actualHash -ne $expectedHash) {
        throw "Kafka archive checksum mismatch. Expected $expectedHash but got $actualHash"
    }
    Write-Host 'Kafka archive SHA-512 verified.'

    $extractDirectory = Join-Path ([System.IO.Path]::GetTempPath()) (
        'kafka-{0}-{1}' -f $KafkaVersion, [guid]::NewGuid().ToString('N')
    )
    try {
        New-Item -ItemType Directory -Path $extractDirectory | Out-Null
        Invoke-NativeCommand -FilePath 'tar.exe' -Arguments @(
            '-xzf', $ArchivePath, '-C', $extractDirectory, '--strip-components=1'
        ) | Out-Null
        if (-not (Test-Path -LiteralPath (Join-Path $extractDirectory "libs\kafka-storage-$KafkaVersion.jar"))) {
            throw 'The downloaded archive did not contain the Kafka storage library.'
        }
        if (Test-Path -LiteralPath $KafkaHome) {
            $existingItems = @(Get-ChildItem -LiteralPath $KafkaHome -Force -ErrorAction SilentlyContinue)
            if ($existingItems.Count -gt 0) {
                throw "Kafka folder is not empty: $KafkaHome"
            }
            Remove-Item -LiteralPath $KafkaHome
        }
        Move-Item -LiteralPath $extractDirectory -Destination $KafkaHome
    }
    finally {
        if (Test-Path -LiteralPath $extractDirectory) {
            Remove-Item -LiteralPath $extractDirectory -Recurse -Force
        }
    }

    if (-not $PSBoundParameters.ContainsKey('ArchivePath') -and (Test-Path -LiteralPath $ArchivePath)) {
        Remove-Item -LiteralPath $ArchivePath -Force
    }
}

foreach ($directory in @($KafkaConfigDirectory, $DataDirectory, $BrokerLogDirectory, $AppLogDirectory)) {
    New-Item -ItemType Directory -Force -Path $directory | Out-Null
}
Copy-Item -LiteralPath $ProjectConfig -Destination $KafkaConfig -Force

$metaProperties = Join-Path $DataDirectory 'meta.properties'
if (Test-Path -LiteralPath $metaProperties -PathType Leaf) {
    $storedClusterId = (Select-String -LiteralPath $metaProperties -Pattern '^cluster\.id=(.+)$' |
        Select-Object -First 1).Matches.Groups[1].Value
    if (-not $clusterId) {
        throw "KRaft storage is already formatted with cluster ID $storedClusterId, but KAFKA_CLUSTER_ID is empty in $EnvFile."
    }
    if ($clusterId -ne $storedClusterId) {
        throw "KAFKA_CLUSTER_ID ($clusterId) does not match formatted storage ($storedClusterId). Existing data was not changed."
    }
    Write-Host "KRaft storage is already formatted for cluster $clusterId; no changes made."
    exit 0
}

$existingData = @(Get-ChildItem -LiteralPath $DataDirectory -Force)
if ($existingData.Count -gt 0) {
    throw "KRaft data directory is not empty and has no meta.properties: $DataDirectory"
}

$oldLogDir = $env:LOG_DIR
$oldLog4jOptions = $env:KAFKA_LOG4J_OPTS
try {
    $env:LOG_DIR = 'kafka-runtime/brokER-logs'
    $env:KAFKA_LOG4J_OPTS = '-Dlog4j2.configurationFile=kafka/config/tools-log4j2.yaml'

    if (-not $clusterId) {
        $clusterId = Invoke-KafkaStorageTool -Arguments @('random-uuid')
        if ($clusterId -notmatch '^[A-Za-z0-9_-]{20,}$') {
            throw "Kafka returned an invalid cluster ID: $clusterId"
        }

        $updatedEnvLines = foreach ($line in $envLines) {
            if ($line -match '^KAFKA_CLUSTER_ID=') {
                "KAFKA_CLUSTER_ID=$clusterId"
            }
            else {
                $line
            }
        }
        $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
        [System.IO.File]::WriteAllLines($EnvFile, [string[]]$updatedEnvLines, $utf8NoBom)
        Write-Host "Saved generated cluster ID to config/kafka.env."
    }

    Write-Host 'Formatting project-local KRaft storage...'
    Invoke-KafkaStorageTool -Arguments @(
        'format', '-t', $clusterId, '-c', $KafkaConfig
    ) | ForEach-Object { Write-Host $_ }
}
finally {
    if ($null -eq $oldLogDir) {
        Remove-Item Env:\LOG_DIR -ErrorAction SilentlyContinue
    }
    else {
        $env:LOG_DIR = $oldLogDir
    }
    if ($null -eq $oldLog4jOptions) {
        Remove-Item Env:\KAFKA_LOG4J_OPTS -ErrorAction SilentlyContinue
    }
    else {
        $env:KAFKA_LOG4J_OPTS = $oldLog4jOptions
    }
}

Write-Host "KRaft storage formatted for cluster $clusterId at $DataDirectory"
