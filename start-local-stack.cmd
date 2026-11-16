@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 (
  echo Java 17+ is required but was not found on PATH.
  echo Install JDK 17 or newer and then rerun this script.
  exit /b 1
)

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js is required but was not found on PATH.
  echo Install Node.js and then rerun this script.
  exit /b 1
)

echo.
echo Starting local Kafka and application stack...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\setup-kafka.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\kafka.ps1" -Action Start

start "Security Market" /D "%~dp0backend\security_master_market" cmd /k "mvnw.cmd spring-boot:run"
start "Custodian" /D "%~dp0backend\custodian_service" cmd /k "mvnw.cmd spring-boot:run"
start "Borrower" /D "%~dp0backend\borrower_service" cmd /k "mvnw.cmd spring-boot:run"
start "Valuation" /D "%~dp0backend\valuation_accural" cmd /k "mvnw.cmd spring-boot:run"

if exist "%~dp0frontend\Borrower_ui\package.json" (
  if not exist "%~dp0frontend\Borrower_ui\node_modules" (
    echo Installing Borrower UI dependencies...
    call npm.cmd install --prefix "%~dp0frontend\Borrower_ui" --no-audit --no-fund
  )
  start "Borrower UI" /D "%~dp0frontend\Borrower_ui" cmd /k "npm.cmd run dev -- --host 127.0.0.1"
)

if exist "%~dp0frontend\Custodian_ui\package.json" (
  if not exist "%~dp0frontend\Custodian_ui\node_modules" (
    echo Installing Custodian UI dependencies...
    call npm.cmd install --prefix "%~dp0frontend\Custodian_ui" --no-audit --no-fund
  )
  start "Custodian UI" /D "%~dp0frontend\Custodian_ui" cmd /k "npm.cmd run dev -- --host 127.0.0.1"
)

echo.
echo Kafka is running on localhost:9092
for %%I in (8080 8081 8082 8083 5173 5174) do (
  echo Port %%I is being used by the local stack.
)

echo.
echo Local stack started in separate windows.
echo Close the windows to stop each process.
exit /b 0
