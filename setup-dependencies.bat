@echo off
:: Launcher for chocolatey-setup.ps1. Arguments pass straight through, so
:: anything the script takes works here:
::
::   setup-dependencies.bat                      see what's installed
::   setup-dependencies.bat --install            install what's missing
::   setup-dependencies.bat --install --minimal  skip the slow downloads
::   setup-dependencies.bat --install --portable no Chocolatey, no admin
::   setup-dependencies.bat --help

setlocal
set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%chocolatey-setup.ps1"

if not exist "%PS_SCRIPT%" (
    echo [ERROR] chocolatey-setup.ps1 not found in %SCRIPT_DIR%
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" %*
exit /b %ERRORLEVEL%
