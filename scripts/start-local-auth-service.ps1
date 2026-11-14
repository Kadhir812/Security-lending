[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidateSet('Borrower', 'Custodian')]
    [string]$Service
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$borrowerProperties = Join-Path $root 'backend\borrower_service\src\main\resources\application.properties'
$custodianProperties = Join-Path $root 'backend\custodian_service\src\main\resources\application.properties'

function Get-ConfiguredValue([string]$Path, [string]$Name) {
    $line = Get-Content $Path | Where-Object { $_ -match ('^' + [regex]::Escape($Name) + '=') } | Select-Object -First 1
    if (-not $line) {
        throw "Missing $Name in $Path"
    }

    $value = $line.Substring($Name.Length + 1)
    if ($value -match '^\$\{([^:}]+)(?::([^}]*))?\}$') {
        $environmentValue = [Environment]::GetEnvironmentVariable($Matches[1])
        if (-not [string]::IsNullOrWhiteSpace($environmentValue)) {
            return $environmentValue
        }
        if ($Matches[2]) {
            return $Matches[2]
        }
        throw "$Name requires an environment value."
    }
    return $value
}

$sharedSecret = [Environment]::GetEnvironmentVariable('JWT_SHARED_SECRET')
if ([string]::IsNullOrWhiteSpace($sharedSecret)) {
    $sharedSecret = Get-ConfiguredValue $borrowerProperties 'app.auth.jwt-secret'
}
$issuer = Get-ConfiguredValue $custodianProperties 'app.auth.issuer'
$borrowerAudience = Get-ConfiguredValue $borrowerProperties 'app.auth.audience'
$custodianAudience = Get-ConfiguredValue $custodianProperties 'app.auth.audience'

if ([Convert]::FromBase64String($sharedSecret).Length -lt 32) {
    throw 'JWT_SHARED_SECRET must decode to at least 32 bytes.'
}

$env:JWT_SHARED_SECRET = $sharedSecret
$env:JWT_ISSUER = $issuer
$env:BORROWER_JWT_AUDIENCE = $borrowerAudience
$env:CUSTODIAN_JWT_AUDIENCE = $custodianAudience
$env:BORROWER_TOKEN_AUDIENCES = "$borrowerAudience,$custodianAudience"

$serviceDirectory = Join-Path $root "backend\$($Service.ToLowerInvariant())_service"
$logName = $Service.ToLowerInvariant() + '.log'
$logPath = Join-Path $root "app_logs\live-u1-bb-ba-ak-u2-20261111-1255\$logName"
Set-Location $serviceDirectory
& .\mvnw.cmd spring-boot:run 2>&1 | Tee-Object -Append -FilePath $logPath
exit $LASTEXITCODE
