@echo off
:: setup-dependencies.bat — Windows launcher for chocolatey-setup.ps1
::
:: Usage:
::   setup-dependencies.bat            (check tools)
::   setup-dependencies.bat --install  (install missing tools + project deps)
::
:: Requires PowerShell 5+ (built into Windows 10/11).
:: Run from an elevated (Administrator) command prompt when using --install.

setlocal
set "SCRIPT_DIR=%~dp0"
set "PS_SCRIPT=%SCRIPT_DIR%chocolatey-setup.ps1"

if not exist "%PS_SCRIPT%" (
    echo [ERROR] chocolatey-setup.ps1 not found in %SCRIPT_DIR%
    exit /b 1
)

if /i "%~1"=="--install" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" --install
) else if /i "%~1"=="--help" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" --help
) else if "%~1"=="" (
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%PS_SCRIPT%" --check
) else (
    echo Unknown option: %~1
    echo Usage: setup-dependencies.bat [--install]
    exit /b 1
)
