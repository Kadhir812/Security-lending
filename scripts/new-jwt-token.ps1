[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$Audience,
    [Parameter(Mandatory)][string]$Subject,
    [Parameter(Mandatory)][string]$OrganizationReference,
    [string]$ClientId,
    [ValidateSet('human', 'service')][string]$IdentityKind,
    [string[]]$Roles = @(),
    [string]$Scope,
    [ValidateRange(1, 60)][int]$ExpiresMinutes = 15
)

$ErrorActionPreference = 'Stop'
$encodedSecret = [Environment]::GetEnvironmentVariable('JWT_SHARED_SECRET')
if ([string]::IsNullOrWhiteSpace($encodedSecret)) {
    throw 'Set JWT_SHARED_SECRET in this terminal before creating a token.'
}

$secret = [Convert]::FromBase64String($encodedSecret)
if ($secret.Length -lt 32) {
    throw 'JWT_SHARED_SECRET must decode to at least 32 bytes.'
}

function ConvertTo-Base64Url([byte[]]$Value) {
    [Convert]::ToBase64String($Value).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$issuer = [Environment]::GetEnvironmentVariable('JWT_ISSUER')
if ([string]::IsNullOrWhiteSpace($issuer)) { $issuer = 'securities-lending' }
$audiences = @($Audience -split '[,\s]+' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
if ($audiences.Count -eq 0) {
    throw 'At least one JWT audience is required.'
}
$claims = [ordered]@{
    iss = $issuer
    sub = $Subject
    aud = if ($audiences.Count -eq 1) { $audiences[0] } else { $audiences }
    iat = $now
    nbf = $now - 5
    exp = $now + ($ExpiresMinutes * 60)
    organization_reference = $OrganizationReference
}
if ($ClientId) { $claims.client_id = $ClientId }
if ($IdentityKind) { $claims.identity_kind = $IdentityKind }
if ($Roles.Count -gt 0) { $claims.roles = @($Roles | Select-Object -Unique) }
if ($Scope) { $claims.scope = $Scope }

$header = [ordered]@{ alg = 'HS256'; typ = 'JWT' } | ConvertTo-Json -Compress
$payload = $claims | ConvertTo-Json -Compress
$utf8 = [System.Text.Encoding]::UTF8
$unsigned = "$(ConvertTo-Base64Url ($utf8.GetBytes($header))).$(ConvertTo-Base64Url ($utf8.GetBytes($payload)))"
$hmac = [System.Security.Cryptography.HMACSHA256]::new($secret)
try {
    $signature = ConvertTo-Base64Url ($hmac.ComputeHash($utf8.GetBytes($unsigned)))
} finally {
    $hmac.Dispose()
}

Write-Output "$unsigned.$signature"
