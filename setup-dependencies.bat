@echo off
:: setup-dependencies.bat — Windows launcher for setup.ps1
::
:: Usage:
::   setup-dependencies.bat
::
:: Requires PowerShell 5+ (built into Windows 10/11).
:: This script will automatically prompt for Administrator privileges if needed.

setlocal
set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%setup.ps1"

if not exist "%PS_SCRIPT%" (
    echo [ERROR] setup.ps1 not found in %SCRIPT_DIR%
    exit /b 1
)

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%"