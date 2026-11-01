@echo off
setlocal
set "SCRIPT_DIR=%~dp0"
where powershell.exe >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%start-local.ps1" %*
  exit /b %ERRORLEVEL%
)

echo PowerShell is unavailable. Starting the frontend and backend with cmd.exe.
start "DataAgent Frontend" /D "%SCRIPT_DIR%..\data-agent-frontend-nuxt" cmd.exe /k pnpm.cmd dev
for %%A in (%*) do (
  if /I "%%~A"=="-BackendNoPackage" goto backend_dev
  if /I "%%~A"=="-NoPackage" goto backend_dev
)
start "DataAgent Backend" /D "%SCRIPT_DIR%.." cmd.exe /k "mvnw.cmd -pl data-agent-management -am package -DskipTests ^&^& java -jar data-agent-management\target\spring-ai-alibaba-data-agent-management-1.0.0-SNAPSHOT.jar"
exit /b 0

:backend_dev
start "DataAgent Backend" /D "%SCRIPT_DIR%.." cmd.exe /k mvnw.cmd -pl data-agent-management spring-boot:run
exit /b 0
