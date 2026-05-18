# setup.ps1 — Windows setup script for the multi-language mutation tester demo.
#
# Usage (run in an elevated PowerShell prompt):
#   .\setup.ps1            # check whether required tools are present
#   .\setup.ps1 --install  # install missing tools via Chocolatey, then download project deps
#
# Chocolatey (https://chocolatey.org) is used as the Windows package manager,
# matching the spirit of the original request.  If Chocolatey is not found and
# --install is specified, this script installs it automatically.

param(
    [switch]$Install,
    [switch]$Help
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

if ($Help) {
    Get-Content $MyInvocation.MyCommand.Path | Select-Object -First 12
    exit 0
}

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

function Info  { param($msg) Write-Host "[INFO] $msg" }
function Ok    { param($msg) Write-Host "[OK]   $msg" -ForegroundColor Green }
function Warn  { param($msg) Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Miss  { param($msg) Write-Host "[MISS] $msg" -ForegroundColor Red }

function Have {
    param($cmd)
    $null -ne (Get-Command $cmd -ErrorAction SilentlyContinue)
}

# ---------------------------------------------------------------------------
# Chocolatey bootstrap
# ---------------------------------------------------------------------------

function Ensure-Choco {
    if (Have choco) { return }
    if (-not $Install) {
        Warn "Chocolatey not found. Run with --install to install it automatically."
        return
    }

    Info "Installing Chocolatey..."
    Set-ExecutionPolicy Bypass -Scope Process -Force
    [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
    Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

    # Reload PATH so choco is available immediately
    $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                [System.Environment]::GetEnvironmentVariable("Path","User")
}

# ---------------------------------------------------------------------------
# Tool checks / installs
# ---------------------------------------------------------------------------

function Require-Tool {
    param(
        [string]$Cmd,
        [string]$Label,
        [string]$ChocoPackage,
        [string]$InstallNote
    )

    if (Have $Cmd) {
        $ver = try { & $Cmd --version 2>&1 | Select-Object -First 1 } catch { "unknown" }
        return
    }

    Miss "$Label is not installed or not on PATH."

    if ($Install) {
        if (Have choco) {
            Info "Installing $Label via Chocolatey..."
            choco install $ChocoPackage -y
            # Reload PATH after install
            $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                        [System.Environment]::GetEnvironmentVariable("Path","User")
        } else {
            Warn $InstallNote
        }
    } else {
        Warn $InstallNote
    }
}

function Check-Racket {
    if (Have racket) {
        $ver = try { racket --version 2>&1 | Select-Object -First 1 } catch { "unknown" }
        Ok "Racket: $ver"
        return
    }

    Miss "Racket is not installed or not on PATH."

    if ($Install) {
        if (Have choco) {
            Info "Installing Racket via Chocolatey..."
            choco install racket -y
            $env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
                        [System.Environment]::GetEnvironmentVariable("Path","User")
        } else {
            Warn "Install Racket from https://download.racket-lang.org/"
        }
    } else {
        Warn "Install Racket from https://download.racket-lang.org/"
    }
}

# ---------------------------------------------------------------------------
# Project-level dependency downloads
# ---------------------------------------------------------------------------

function Download-Maven {
    if (-not (Have mvn)) {
        Warn "Skipping Maven dependency download because mvn is missing."
        return
    }
    Info "Downloading root Maven dependencies..."
    Push-Location $RootDir
    mvn -q -DskipTests dependency:go-offline
    Pop-Location

    Info "Downloading Java sample Maven dependencies..."
    Push-Location "$RootDir\samples\MavenProject"
    mvn -q -DskipTests dependency:go-offline
    Pop-Location
}
# for JS

function Verify-Puppeteer {
    Info "Verifying Puppeteer installation..."
 
    $jsProjectDir = "$RootDir\samples\JSProject"
    $puppeteerPackage = "$jsProjectDir\node_modules\puppeteer\package.json"
 
    # Check package exists
    if (-not (Test-Path $puppeteerPackage)) {
        Miss "Puppeteer not found in node_modules. npm install may have failed."
        Warn "Try running manually: cd samples\JSProject && npm install"
        return
    }
 
    # Read and display version
    try {
        $pkg = Get-Content $puppeteerPackage -Raw | ConvertFrom-Json
        Ok "Puppeteer: $($pkg.version)"
    } catch {
        Warn "Could not read Puppeteer version from package.json"
    }
 
    # Run the browser test to confirm Chrome headless works
    Info "Running baseline browser test to verify Puppeteer works..."
    Push-Location $jsProjectDir
    try {
        $output = node browser-tests\run-with-puppeteer.js 2>&1
        $outputStr = $output -join "`n"
 
        if ($outputStr -match "ALL TESTS PASSED") {
            Ok "Puppeteer browser test: ALL TESTS PASSED"
        } elseif ($outputStr -match "success rate: 100") {
            Ok "Puppeteer browser test: passed"
        } else {
            Miss "Puppeteer browser test did not pass."
            Warn "Output:"
            Write-Host $outputStr
            Warn "Check that browser-tests\test-runner.html loads correctly."
        }
    } catch {
        Miss "Failed to run Puppeteer browser test: $_"
        Warn "Try running manually: node browser-tests\run-with-puppeteer.js"
    } finally {
        Pop-Location
    }
}

 


function Download-JavaScript {
    if (-not (Have npm)) {
        Warn "Skipping JavaScript dependencies because npm is missing."
        return
    }
 
    Info "Installing JavaScript sample dependencies (npm install)..."
    Push-Location "$RootDir\samples\JSProject"
    try {
        npm install
        Info "Installing Puppeteer Chrome browser..."
        npx puppeteer browsers install chrome
    } finally {
        Pop-Location
    }
 
    # Verify Puppeteer installed correctly
    Verify-Puppeteer
}


function Download-Python {
    $py = if (Have python) { "python" } elseif (Have python3) { "python3" } else { $null }
    if (-not $py) {
        Warn "Skipping Python dependencies because python/python3 is missing."
        return
    }
    $venvDir = "$RootDir\samples\PytestProject\.venv"
    $venvPython = "$venvDir\Scripts\python.exe"

    # Remove stale venv if its interpreter is missing or broken (e.g., after Python version change or cross-OS clone)
    if (Test-Path $venvDir) {
        $venvWorks = $false
        if (Test-Path $venvPython) {
            try { & $venvPython --version 2>$null | Out-Null; $venvWorks = $LASTEXITCODE -eq 0 } catch {}
        }
        if (-not $venvWorks) {
            Info "Removing stale Python virtual environment (interpreter missing or broken)..."
            Remove-Item -Recurse -Force $venvDir
        }
    }

    if (-not (Test-Path $venvDir)) {
        Info "Creating Python virtual environment..."
        & $py -m venv $venvDir
    }
    Info "Installing pytest into virtual environment..."
    & $venvPython -m pip install --quiet pytest
}

function Download-DotNet {
    if (-not (Have dotnet)) {
        Warn "Skipping .NET restore because dotnet is missing."
        return
    }
    Info "Restoring C# sample dependencies..."
    Push-Location "$RootDir\samples\CSProject"
    dotnet restore tests\CSProject.Tests\CSProject.Tests.csproj
    Pop-Location
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

Write-Host "============================================================================"
Write-Host "Mutation Tester Dependency Setup (Windows / PowerShell)"
Write-Host "============================================================================"
Write-Host "Project root: $RootDir"
Write-Host ""

# Reload PATH from registry so tools installed outside this terminal session are visible
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" +
        [System.Environment]::GetEnvironmentVariable("Path","User")

if ($Install) {
    Ensure-Choco
}

Require-Tool java   "Java/JDK 17+"       openjdk17          "Install JDK 17 from https://adoptium.net/"
Require-Tool mvn    "Maven"              maven              "Install Maven from https://maven.apache.org/"
Require-Tool dotnet ".NET SDK (net10.0)" dotnet-sdk         "Install .NET SDK from https://dotnet.microsoft.com/download"
Require-Tool python "Python 3"           python             "Install Python 3 from https://www.python.org/downloads/"
Require-Tool npm    "Node.js/npm"        nodejs             "Install Node.js from https://nodejs.org/"
Check-Racket

# Check Puppeteer (project-level dep, not a system tool)
if (Have npm) {
    $puppeteerPkg = "$RootDir\samples\JSProject\node_modules\puppeteer\package.json"
    if (Test-Path $puppeteerPkg) {
        $pkg = Get-Content $puppeteerPkg -Raw | ConvertFrom-Json
        Ok "Puppeteer (JSProject): $($pkg.version)"
    } else {
        Miss "Puppeteer not installed in samples\JSProject\node_modules."
        Warn "Run '.\setup.ps1 --install' or: cd samples\JSProject && npm install"
    }
}

if ($Install) {
    Write-Host ""
    Write-Host "============================================================================"
    Write-Host "Downloading project dependencies"
    Write-Host "============================================================================"
    Download-Maven
    Download-JavaScript
    Download-Python
    Download-DotNet
} else {
    Write-Host ""
    Info "Run '.\setup.ps1 --install' to install missing tools and download project dependencies."
}

Write-Host ""
Write-Host "============================================================================"
Write-Host "Next steps"
Write-Host "============================================================================"
Write-Host "1. Build the mutation tester:"
Write-Host "   mvn package -DskipTests"
Write-Host ""
Write-Host "2. Invoke the tool with the project directory and config file as arguments:"
Write-Host "   java -jar target/MutationTester.jar <project_directory> <config_file>"
Write-Host ""

