# setup.ps1 - Single Command Environment Setup for Windows

$ErrorActionPreference = "Stop"
$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# 1. Check for Admin privileges.
$isAdmin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "Requesting Administrator privileges to install system tools..." -ForegroundColor Cyan
    # Relaunch the script as admin to install chocolatey and packages
    Start-Process powershell -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`" -SystemDepsOnly"
    Write-Host "Waiting for system dependencies to finish installing in the new window..." -ForegroundColor Cyan
    # Wait a moment for the user to accept the prompt before continuing
    Start-Sleep -Seconds 15
}

# 2. Install System Dependencies (Runs in Admin window)
if ($args -contains "-SystemDepsOnly" -or $isAdmin) {
    Write-Host "==================================================="
    Write-Host "Installing System Tools via Chocolatey..."
    Write-Host "==================================================="
    
    # Ensure Chocolatey is installed
    if (-not (Get-Command choco -ErrorAction SilentlyContinue)) {
        Set-ExecutionPolicy Bypass -Scope Process -Force
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
        Invoke-Expression ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))
    }
    
    # Install or upgrade everything in packages.config to ensure the latest versions
    choco upgrade "$RootDir\packages.config" -y
    
    if ($args -contains "-SystemDepsOnly") {
        Write-Host "System installation complete. This window will close in 5 seconds."
        Start-Sleep -Seconds 5
        exit
    }
}

# 3. Reload Path to ensure new tools are available in current session
$env:Path = [System.Environment]::GetEnvironmentVariable("Path","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("Path","User")

# 4. Initialize Project Dependencies
Write-Host "==================================================="
Write-Host "Initializing Project Dependencies..."
Write-Host "==================================================="

Write-Host "`n[1/5] Downloading Maven dependencies..."
Push-Location $RootDir
mvn -q -DskipTests dependency:go-offline
Pop-Location

Push-Location "$RootDir\samples\MavenProject"
mvn -q -DskipTests dependency:go-offline
Pop-Location

Write-Host "`n[2/5] Installing and updating JavaScript dependencies..."
Push-Location "$RootDir\samples\JSProject"
$env:PUPPETEER_SKIP_DOWNLOAD="true"
npm install
npm update
Write-Host "Installing Puppeteer Chrome browser..."
npx puppeteer browsers install chrome
Pop-Location

Write-Host "`n[3/5] Installing Python dependencies..."
$py = if (Get-Command python -ErrorAction SilentlyContinue) { "python" } elseif (Get-Command python3 -ErrorAction SilentlyContinue) { "python3" } else { $null }
if ($py) {
    $venvDir = "$RootDir\samples\PytestProject\.venv"
    # Check if stale or invalid venv exists (e.g. checked-in from macOS/Linux or missing interpreter)
    if (Test-Path $venvDir) {
        $pyVenvExe = "$venvDir\Scripts\python.exe"
        $isValid = $false
        if (Test-Path $pyVenvExe) {
            try {
                $null = & $pyVenvExe --version
                if ($LASTEXITCODE -eq 0) { $isValid = $true }
            } catch {
                $isValid = $false
            }
        }
        if (-not $isValid) {
            Write-Host "Removing stale or invalid Python virtual environment..." -ForegroundColor Yellow
            Remove-Item -Recurse -Force $venvDir
        }
    }
    
    # Create virtual environment if it doesn't exist
    if (-not (Test-Path $venvDir)) {
        Write-Host "Creating Python virtual environment..."
        & $py -m venv $venvDir
    }
    
    # Install/upgrade pytest in the virtual environment
    Write-Host "Installing/updating pytest in the virtual environment..."
    & "$venvDir\Scripts\python.exe" -m pip install --upgrade --quiet pytest
} else {
    Write-Host "Python not found, skipping." -ForegroundColor Yellow
}

Write-Host "`n[4/5] Restoring and updating .NET dependencies..."
Push-Location "$RootDir\samples\CSProject"
$projFile = "tests\CSProject.Tests\CSProject.Tests.csproj"
if (Test-Path $projFile) {
    # Parse PackageReference elements using SelectNodes and update them to latest
    [xml]$xml = Get-Content $projFile
    $packages = $xml.SelectNodes("//PackageReference")
    foreach ($pkg in $packages) {
        $name = $pkg.GetAttribute("Include")
        if ($name) {
            Write-Host "Auto-updating NuGet package: $name"
            dotnet add "tests\CSProject.Tests" package $name
        }
    }
}
dotnet restore $projFile
Pop-Location

Write-Host "`n[5/5] Restoring and updating Racket dependencies..."
if (Get-Command raco -ErrorAction SilentlyContinue) {
    $rktPaths = @(
        "$RootDir\samples\RacketProject\project",
        "$RootDir\samples\RacketCalculator\calculator"
    )
    foreach ($path in $rktPaths) {
        Write-Host "Restoring racket package in $path..."
        raco pkg install --auto --batch --skip-installed --no-docs $path
        Write-Host "Updating racket package dependencies in $path..."
        raco pkg update --auto --batch --no-docs $path
    }
} else {
    Write-Host "Racket command raco not found, skipping." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "============================================================================"
Write-Host "Next steps"
Write-Host "============================================================================"
Write-Host "1. Build the mutation tester:"
Write-Host "`nSetup complete! You can now build the project." -ForegroundColor Green
Write-Host "   mvn package -DskipTests"
Write-Host ""
Write-Host "2. Invoke the tool with the project directory and config file as arguments:"
Write-Host "   java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar <project_directory> <config_file>"
Write-Host ""
