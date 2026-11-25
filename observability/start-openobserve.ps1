$ErrorActionPreference = 'Stop'

$Root = $PSScriptRoot

$env:ZO_ROOT_USER_EMAIL = "admin@example.com"
$env:ZO_ROOT_USER_PASSWORD = "ChangeThis-StrongPassword2026!"
$env:ZO_DATA_DIR = Join-Path $Root "data"
$env:ZO_LOCAL_MODE = "true"

New-Item -ItemType Directory -Force -Path $env:ZO_DATA_DIR | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $Root "logs") | Out-Null

$Executable = Join-Path $Root "openobserve\openobserve.exe"

if (-not (Test-Path -LiteralPath $Executable)) {
    throw "OpenObserve executable not found: $Executable"
}

Write-Host "Starting OpenObserve..."
Write-Host "Root user: $env:ZO_ROOT_USER_EMAIL"
Write-Host "Data directory: $env:ZO_DATA_DIR"

Start-Process `
    -FilePath $Executable `
    -WorkingDirectory $Root `
    -WindowStyle Normal
