[CmdletBinding()]
param(
    [ValidateSet('Start', 'Stop', 'Status', 'Verify')][string]$Action = 'Status',
    [string]$LogDirectory
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Runtime = Join-Path $ProjectRoot 'kafka-runtime'
$Logs = if ([string]::IsNullOrWhiteSpace($LogDirectory)) {
    Join-Path $ProjectRoot 'app-logs'
} elseif ([System.IO.Path]::IsPathRooted($LogDirectory)) {
    $LogDirectory
} else {
    Join-Path $ProjectRoot $LogDirectory
}
$PidFile = Join-Path $Runtime 'broker.pid'
$Java = (Get-Command java.exe -ErrorAction Stop).Source
New-Item -ItemType Directory -Force -Path $Runtime, $Logs | Out-Null
$JavaArgs = @('-Xms256m', '-Xmx512m', '-Djava.io.tmpdir=kafka-runtime',
    '-Dkafka.logs.dir=kafka-runtime/brokER-logs',
    '-Dlog4j2.configurationFile=kafka/config/tools-log4j2.yaml', '-cp', 'kafka/libs/*')

function Get-Broker {
    if (Test-Path -LiteralPath $PidFile) {
        $brokerId = [int](Get-Content -LiteralPath $PidFile -Raw).Trim()
        $process = Get-CimInstance Win32_Process -Filter "ProcessId = $brokerId"
        # Guard against a stale PID referring to an unrelated process.
        if ($process -and $process.CommandLine -like '*kafka.Kafka*' -and
            $process.CommandLine.Contains($ProjectRoot)) {
            return $process
        }
    }
}

function Test-BrokerPort {
    $client = [System.Net.Sockets.TcpClient]::new()
    try { $client.Connect('127.0.0.1', 9092); return $true }
    catch { return $false }
    finally { $client.Dispose() }
}

function Invoke-KafkaCli {
    param([string]$Name, [string[]]$CliArgs, [string]$InputFile)
    $outFile = Join-Path $Logs "$Name.log"
    $errFile = Join-Path $Logs "$Name.stderr.log"
    $parameters = @{
        FilePath = $Java; ArgumentList = $JavaArgs + $CliArgs
        WorkingDirectory = $ProjectRoot; WindowStyle = 'Hidden'
        RedirectStandardOutput = $outFile; RedirectStandardError = $errFile
        PassThru = $true
    }
    if ($InputFile) { $parameters.RedirectStandardInput = $InputFile }
    $process = Start-Process @parameters
    $null = $process.Handle
    if (-not $process.WaitForExit(60000)) {
        $process.Kill()
        throw "$Name timed out; see $errFile"
    }
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw "$Name failed: $(Get-Content -LiteralPath $errFile -Raw)"
    }
    return (Get-Content -LiteralPath $outFile -Raw)
}

switch ($Action) {
    'Start' {
        if (Get-Broker) { Write-Host 'Workspace Kafka is already running.'; break }
        if (Test-BrokerPort) { throw 'Port 9092 is already occupied; no process was changed.' }
        if (-not (Test-Path (Join-Path $Runtime 'data/meta.properties'))) {
            throw 'Run scripts/setup-kafka.ps1 first.'
        }
        $config = Join-Path $ProjectRoot 'kafka/config/kraft/server.properties'
        $process = Start-Process -FilePath $Java -ArgumentList ($JavaArgs + @('kafka.Kafka', ('"' + $config + '"'))) `
            -WorkingDirectory $ProjectRoot -WindowStyle Hidden -PassThru `
            -RedirectStandardOutput (Join-Path $Logs 'broker.stdout.log') `
            -RedirectStandardError (Join-Path $Logs 'broker.stderr.log')
        Set-Content -LiteralPath $PidFile -Value $process.Id
        $deadline = [DateTime]::UtcNow.AddSeconds(60)
        while ([DateTime]::UtcNow -lt $deadline) {
            if ($process.HasExited) { throw 'Kafka exited; inspect app-logs/broker.stderr.log and broker.stdout.log.' }
            if (Test-BrokerPort) { Write-Host "Kafka listening at localhost:9092 (PID $($process.Id))."; return }
            Start-Sleep -Milliseconds 500
        }
        throw 'Kafka did not become ready within 60 seconds; inspect app-logs.'
    }
    'Stop' {
        $broker = Get-Broker
        if ($broker) { Stop-Process -Id $broker.ProcessId; Write-Host 'Workspace Kafka stopped.' }
        else { Write-Host 'Workspace Kafka is not running.' }
        if (Test-Path $PidFile) { Remove-Item -LiteralPath $PidFile }
    }
    'Status' {
        $broker = Get-Broker
        if ($broker) { Write-Host "Workspace Kafka PID $($broker.ProcessId); port 9092 reachable: $(Test-BrokerPort)" }
        else { Write-Host 'Workspace Kafka is not running.' }
    }
    'Verify' {
        if (-not (Get-Broker) -or -not (Test-BrokerPort)) { throw 'Start workspace Kafka with scripts/kafka.ps1 -Action Start first.' }
        $topic = 'workspace-smoke-' + [guid]::NewGuid().ToString('N')
        $message = 'workspace-kafka-verified-' + [guid]::NewGuid().ToString('N')
        Invoke-KafkaCli -Name 'topic' -CliArgs @('org.apache.kafka.tools.TopicCommand', '--bootstrap-server', 'localhost:9092',
            '--create', '--topic', $topic, '--partitions', '1', '--replication-factor', '1') | Write-Host
        $inputFile = Join-Path $Logs 'producer-input.txt'
        [System.IO.File]::WriteAllText($inputFile, $message + "`n", [System.Text.UTF8Encoding]::new($false))
        Invoke-KafkaCli -Name 'producer' -InputFile $inputFile -CliArgs @('org.apache.kafka.tools.ConsoleProducer',
            '--bootstrap-server', 'localhost:9092', '--topic', $topic, '--producer-property', 'acks=all') | Out-Null
        $received = Invoke-KafkaCli -Name 'consumer' -CliArgs @('org.apache.kafka.tools.consumer.ConsoleConsumer',
            '--bootstrap-server', 'localhost:9092', '--topic', $topic, '--from-beginning', '--max-messages', '1', '--timeout-ms', '20000')
        if ($received.Trim() -cne $message) { throw "Message mismatch. Expected $message; received $received" }
        $result = [ordered]@{ verifiedAt = [DateTime]::UtcNow.ToString('o'); bootstrapServer = 'localhost:9092'
            topic = $topic; sent = $message; received = $received.Trim(); passed = $true }
        $result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $Logs 'verification.json')
        Write-Host "PASS: producer and consumer matched exactly on topic $topic"
        Write-Host "Message: $message"
    }
}
