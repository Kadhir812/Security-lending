[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$MySqlUrl,
    [Parameter(Mandatory = $true)][string]$KafkaBootstrap
)

$ErrorActionPreference = 'Stop'
$ProjectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if ($MySqlUrl -notmatch '^jdbc:mysql://.+/$') {
    throw 'Supply a disposable MySQL server URL ending in /, without a database name.'
}
$Maven = (Get-Command mvn.cmd -ErrorAction Stop).Source
$Repository = Join-Path $ProjectRoot '.m2'
$PreviousMySql = $env:STAGE1_MYSQL_URL
$PreviousKafka = $env:STAGE2_KAFKA_BOOTSTRAP

function Invoke-MavenVerification {
    param([string[]]$BuildArguments)
    # Windows PowerShell otherwise treats harmless native stderr warnings as terminating errors.
    $PreviousErrors = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Maven '-B' '-ntp' "-Dmaven.repo.local=$Repository" @BuildArguments
        $BuildExitCode = $LASTEXITCODE
    }
    finally { $ErrorActionPreference = $PreviousErrors }
    if ($BuildExitCode -ne 0) { throw "Maven verification failed with exit code $BuildExitCode." }
}

Push-Location $ProjectRoot
try {
    $env:STAGE1_MYSQL_URL = $MySqlUrl
    $env:STAGE2_KAFKA_BOOTSTRAP = $KafkaBootstrap
    # The first run prepares Custodian's separate test runtime as well as all component tests.
    Invoke-MavenVerification -BuildArguments @('-Pmessaging-it', 'clean', 'verify')
    Invoke-MavenVerification -BuildArguments @('-Pround-trip-it', '-pl', ':borrower-service', 'clean', 'verify')
}
finally {
    Pop-Location
    $env:STAGE1_MYSQL_URL = $PreviousMySql
    $env:STAGE2_KAFKA_BOOTSTRAP = $PreviousKafka
}
