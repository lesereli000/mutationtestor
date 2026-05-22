# chocolatey-setup.ps1 - One-shot Windows setup for the mutation tester.
#
# Usage (run in an elevated PowerShell prompt):
#   .\chocolatey-setup.ps1 --install      # install/upgrade every required tool
#   .\chocolatey-setup.ps1 --check        # only verify versions, install nothing
#   .\chocolatey-setup.ps1 --help
#
# Required versions (kept in sync with the project sources):
#   - JDK              : 17 or newer (matches pom.xml maven.compiler.source/target = 17)
#   - Maven            : 3.9+
#   - .NET SDK         : 10.0.x  (samples/CSProject targets net10.0)
#   - Python           : 3.11+
#   - Node.js / npm    : 20 LTS or newer (Puppeteer ^24 requires Node 18+)
#   - Racket           : 8.x
#   - Chocolatey       : latest
#
# This script is idempotent: if a tool is missing it is installed, and if it is
# present at the wrong version it is upgraded to the pinned version below.
# Winget is NOT used. Chocolatey is bootstrapped automatically if absent.

# NOTE: no [CmdletBinding()] / param() block here on purpose.
# We accept --install / --check / --help via the automatic $args array,
# which only works in plain (non-advanced) script mode.

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------
# Argument parsing (supports both --install and -Install styles)
# ---------------------------------------------------------------------------

$Mode = "check"
foreach ($a in $args) {
        switch -Regex ($a) {
                '^(--install|-Install|-i|/install)$' { $Mode = "install" }
                '^(--check|-Check|-c|/check)$'       { $Mode = "check" }
                '^(--help|-Help|-h|/?|/help)$'       { $Mode = "help" }
                default { Write-Warning "Unknown argument: $a" }
        }
}

if ($Mode -eq "help") {
        Get-Content $MyInvocation.MyCommand.Path | Select-Object -First 22
        exit 0
}

$RootDir = Split-Path -Parent $MyInvocation.MyCommand.Path

# ---------------------------------------------------------------------------
# Pinned versions
# ---------------------------------------------------------------------------

$Required = @{
        JdkMajor      = 17
        DotnetMajor   = 10
        PythonMinMajor = 3
        PythonMinMinor = 11
        NodeMinMajor  = 20
        RacketMajor   = 8
}

$ChocoPackages = @{
        Jdk    = @{ Name = "temurin17"; Version = $null }     # Temurin OpenJDK 17
        Maven  = @{ Name = "maven";     Version = $null }
        Dotnet = @{ Name = "dotnet-10.0-sdk"; Version = $null } # .NET 10 SDK
        Python = @{ Name = "python311"; Version = $null }
        Node   = @{ Name = "nodejs-lts"; Version = $null }    # 20.x LTS
        Racket = @{ Name = "racket";    Version = $null }
}

# ---------------------------------------------------------------------------
# Logging helpers
# ---------------------------------------------------------------------------

function Info  { param($m) Write-Host "[INFO] $m" -ForegroundColor Cyan }
function Ok    { param($m) Write-Host "[OK]   $m" -ForegroundColor Green }
function Warn  { param($m) Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Fail  { param($m) Write-Host "[FAIL] $m" -ForegroundColor Red }

# Show a Write-Progress bar while running $Body, then report elapsed time.
# This makes slow, otherwise-silent commands visually obvious.
function Invoke-Step {
        param(
                [string]$Activity,
                [string]$Status = "Running...",
                [scriptblock]$Body
        )
        Write-Progress -Id 1 -Activity $Activity -Status $Status -PercentComplete -1
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        try { & $Body }
        finally {
                $sw.Stop()
                Write-Progress -Id 1 -Activity $Activity -Completed
                Info "$Activity finished ($([int]$sw.Elapsed.TotalSeconds)s)"
        }
}

function Have {
        param($cmd)
        $null -ne (Get-Command $cmd -ErrorAction SilentlyContinue)
}

# Run a native command without letting its stderr text trip
# $ErrorActionPreference = 'Stop' (Java's -version writes to stderr even on
# success, which PowerShell 5.1 otherwise surfaces as a NativeCommandError).
function Invoke-Native {
        param(
                [string]$Exe,
        # NOTE: do NOT name this $Args - that collides with PowerShell's
        # automatic $args variable and the parameter never gets bound, so
        # every caller ends up invoking $Exe with no arguments. For
        # 'node'/'python'/'racket' that opens a REPL and hangs; for
        # 'java'/'dotnet' it prints the help text and can deadlock on the
        # stdout pipe. Either way the script reports a bogus 30 s timeout.
                [string[]]$Argv,
                [int]$TimeoutSec = 30   # hard cap so a wedged child can't hang the whole script
        )
        # PS 5.1 / .NET Framework: ProcessStartInfo only exposes .Arguments
        # (a single string), not the .ArgumentList collection from .NET Core.
        $quoted = foreach ($a in $Argv) {
                if ($a -match '[\s"]') { '"' + ($a -replace '"', '\"') + '"' } else { $a }
        }
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName  = $Exe
        $psi.Arguments = ($quoted -join ' ')
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError  = $true
        $psi.UseShellExecute        = $false
        $psi.CreateNoWindow         = $true
        $p = New-Object System.Diagnostics.Process
        $p.StartInfo = $psi
        try {
                [void]$p.Start()
                if (-not $p.WaitForExit($TimeoutSec * 1000)) {
                        try { $p.Kill($true) } catch { try { $p.Kill() } catch {} }
                        Write-Host "[WARN] '$Exe' did not return within $TimeoutSec s; killed." -ForegroundColor Yellow
                        return ""
                }
                $out = $p.StandardOutput.ReadToEnd()
                $err = $p.StandardError.ReadToEnd()
                return (($out, $err) -join "`n")
        } catch {
                return ""
        } finally {
                $p.Dispose()
        }
}

function Refresh-Path {
        # Windows caps a single env var at 8191 chars. The raw concat of
        # Machine + User PATH often overflows after several Choco installs
        # because the same directories end up in both scopes. Dedupe
        # (case-insensitive, preserving first-seen order) before assigning.
        $machine = [System.Environment]::GetEnvironmentVariable("Path","Machine")
        $user    = [System.Environment]::GetEnvironmentVariable("Path","User")
        $raw     = @()
        if ($machine) { $raw += $machine -split ';' }
        if ($user)    { $raw += $user    -split ';' }

        $seen   = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::OrdinalIgnoreCase)
        $unique = New-Object System.Collections.Generic.List[string]
        foreach ($e in $raw) {
                $t = $e.Trim().TrimEnd('\')
                if (-not $t) { continue }
                if ($seen.Add($t)) { [void]$unique.Add($t) }
        }
        $joined = $unique -join ';'

        if ($joined.Length -gt 8191) {
                Write-Host ("[WARN] Deduped PATH is {0} chars (>8191). Keeping current `$env:Path; user PATH likely needs manual cleanup via 'Edit environment variables for your account'." -f $joined.Length) -ForegroundColor Yellow
        } else {
                $env:Path = $joined
        }

        # Pull in any other env vars Chocolatey/installers set (JAVA_HOME, etc.)
        foreach ($scope in 'Machine','User') {
                foreach ($name in 'JAVA_HOME','MAVEN_HOME','M2_HOME','DOTNET_ROOT') {
                        $val = [System.Environment]::GetEnvironmentVariable($name,$scope)
                        if ($val) {
                                try { Set-Item -Path "env:$name" -Value $val } catch {}
                        }
                }
        }
}

function Assert-Admin {
        $id = [System.Security.Principal.WindowsIdentity]::GetCurrent()
        $principal = New-Object System.Security.Principal.WindowsPrincipal($id)
        if (-not $principal.IsInRole([System.Security.Principal.WindowsBuiltInRole]::Administrator)) {
                Fail "This script must be run from an elevated (Administrator) PowerShell prompt."
                Fail "Right-click PowerShell and choose 'Run as administrator', then re-run:"
                Fail "    .\chocolatey-setup.ps1 --install"
                exit 1
        }
}

# ---------------------------------------------------------------------------
# Chocolatey bootstrap
# ---------------------------------------------------------------------------

function Ensure-Choco {
        if (Have choco) { Ok "Chocolatey is already installed."; return }
        Info "Bootstrapping Chocolatey (no Winget required)..."
        Set-ExecutionPolicy Bypass -Scope Process -Force
        [System.Net.ServicePointManager]::SecurityProtocol = `
        [System.Net.ServicePointManager]::SecurityProtocol -bor 3072
        Invoke-Expression ((New-Object System.Net.WebClient).DownloadString(
                'https://community.chocolatey.org/install.ps1'))
        Refresh-Path
        if (-not (Have choco)) {
                Fail "Chocolatey installation failed."
                exit 1
        }
        Ok "Chocolatey installed."
}

# ---------------------------------------------------------------------------
# Generic install / upgrade-to-correct-version helper
# ---------------------------------------------------------------------------

function Install-OrUpgrade {
        param(
                [string]$Label,
                [string]$Package,
                [string]$Version,                 # optional pinned version
                [scriptblock]$Verify = $null      # optional post-install probe; should return $true if the tool is actually usable
        )
        $baseFlags = @("-y", "--no-progress", "--limit-output", "--no-color")

        $chocoArgs = @("install", $Package) + $baseFlags
        if ($Version) { $chocoArgs += @("--version=$Version", "--allow-downgrade") }
        Info "Installing/upgrading $Label via Chocolatey ($Package)..."
        & choco @chocoArgs
        if ($LASTEXITCODE -ne 0) {
                # Already installed at a different version - force upgrade
                Info "Forcing upgrade of $Label..."
                $upgradeArgs = @("upgrade", $Package) + $baseFlags
                if ($Version) { $upgradeArgs += @("--version=$Version", "--allow-downgrade") }
                & choco @upgradeArgs
                if ($LASTEXITCODE -ne 0) {
                        Fail "Failed to install/upgrade $Label."
                        return $false
                }
        }
        Refresh-Path

        # Self-heal when Chocolatey's local DB thinks the package is installed
        # but the actual binary is gone (typical after the user deletes the
        # install dir manually). 'choco install' would no-op, leaving the tool
        # unusable; --force re-downloads and re-extracts everything.
        if ($Verify -and -not (& $Verify)) {
                Warn "$Label looks installed to Chocolatey but the binary is missing/broken; forcing a full reinstall..."
                $forceArgs = @("install", $Package) + $baseFlags + @("--force")
                if ($Version) { $forceArgs += @("--version=$Version", "--allow-downgrade") }
                & choco @forceArgs
                if ($LASTEXITCODE -ne 0) {
                        Fail "Forced reinstall of $Label failed."
                        return $false
                }
                Refresh-Path
                if (-not (& $Verify)) {
                        Fail "$Label still not usable after forced reinstall."
                        return $false
                }
        }
        return $true
}

# ---------------------------------------------------------------------------
# Per-tool verification
# ---------------------------------------------------------------------------

function Get-JavaMajor {
        if (-not (Have java)) { return $null }
        $out = Invoke-Native "java" @("-version")
        if ($out -match '"(\d+)(?:\.(\d+))?') {
                $maj = [int]$Matches[1]
                if ($maj -eq 1 -and $Matches[2]) { return [int]$Matches[2] }
                return $maj
        }
        return $null
}

function Get-DotnetMajor {
        if (-not (Have dotnet)) { return $null }
        $out = Invoke-Native "dotnet" @("--list-sdks")
        $majors = @()
        foreach ($line in ($out -split "`n")) {
                if ($line -match '^(\d+)\.') { $majors += [int]$Matches[1] }
        }
        if ($majors.Count -eq 0) { return $null }
        return ($majors | Sort-Object -Descending | Select-Object -First 1)
}

function Get-NodeMajor {
        if (-not (Have node)) { return $null }
        $v = Invoke-Native "node" @("--version")
        if ($v -match 'v(\d+)') { return [int]$Matches[1] }
        return $null
}

# Resolve a real python executable, skipping the Microsoft Store App Execution
# Alias stubs under %LOCALAPPDATA%\Microsoft\WindowsApps. Those stubs are
# 0-byte placeholders that, when invoked from a non-interactive script, can
# hang indefinitely waiting on the Store to open. That hang has no output
# and silently wedges the entire setup right after the previous step.
function Resolve-RealPython {
        param([string]$Name)
        $cmds = @(Get-Command $Name -All -ErrorAction SilentlyContinue)
        foreach ($c in $cmds) {
                $src = $c.Source
                if (-not $src) { continue }
                if ($src -match '\\Microsoft\\WindowsApps\\') { continue }      # Store alias stub
                try {
                        $len = (Get-Item $src -ErrorAction Stop).Length
                        if ($len -le 0) { continue }                                # Zero-byte stub
                } catch { continue }
                return $src
        }
        return $null
}

function Get-PythonVersion {
        # Do NOT trust 'py.exe' or the Microsoft Store python aliases - both
        # can hang for minutes waiting on the Store with no output. Instead,
        # enumerate concrete install paths plus any non-Store python on PATH.
        $candidates = New-Object System.Collections.Generic.List[string]
        $globs = @(
                "C:\Python3*\python.exe",
                "C:\Program Files\Python3*\python.exe",
                "C:\Program Files (x86)\Python3*\python.exe",
                (Join-Path $env:LOCALAPPDATA "Programs\Python\Python3*\python.exe"),
                (Join-Path $env:ProgramData "chocolatey\lib\python3\tools\python.exe"),
                (Join-Path $env:ProgramData "chocolatey\lib\python\tools\python.exe")
        )
        foreach ($g in $globs) {
                Get-ChildItem -Path $g -ErrorAction SilentlyContinue |
                        ForEach-Object { [void]$candidates.Add($_.FullName) }
        }
        foreach ($n in 'python','python3') {
                $real = Resolve-RealPython $n
                if ($real) { [void]$candidates.Add($real) }
        }

        foreach ($exe in ($candidates | Select-Object -Unique)) {
                if (-not (Test-Path $exe)) { continue }
                $v = Invoke-Native $exe @("--version") -TimeoutSec 10
                if ($v -match '(\d+)\.(\d+)\.(\d+)') {
                        return [pscustomobject]@{ Major=[int]$Matches[1]; Minor=[int]$Matches[2]; Exe=$exe }
                }
        }
        return $null
}

function Get-RacketMajor {
        # PATH-only detection is unreliable right after Choco installs Racket:
        # the package updates Machine PATH via the registry, but that change
        # doesn't propagate into the already-running PowerShell process, so
        # Get-Command racket comes up empty and the verify probe falsely fails.
        # Fall back to well-known install locations.
        $exe = $null
        $cmd = Get-Command racket -ErrorAction SilentlyContinue
        if ($cmd) { $exe = $cmd.Source }
        if (-not $exe) {
                $candidates = @(
                        "C:\Program Files\Racket\Racket.exe",
                        "C:\Program Files (x86)\Racket\Racket.exe",
                        (Join-Path $env:LOCALAPPDATA "Racket\Racket.exe"),
                        (Join-Path $env:ProgramData "chocolatey\lib\racket\tools\Racket.exe")
                )
                foreach ($c in $candidates) {
                        if (Test-Path $c) { $exe = $c; break }
                }
        }
        if (-not $exe) { return $null }
        $v = Invoke-Native $exe @("--version") -TimeoutSec 10
        if ($v -match '(\d+)\.') { return [int]$Matches[1] }
        return $null
}

function Get-MavenOk {
        if (-not (Have mvn)) { return $false }
        return $true
}

# ---------------------------------------------------------------------------
# Ensure-<Tool> functions - install if missing OR if version is wrong
# ---------------------------------------------------------------------------

function Ensure-Jdk {
        $maj = Get-JavaMajor
        if ($maj -and $maj -ge $Required.JdkMajor) { Ok "JDK $maj already installed (>= $($Required.JdkMajor))."; return }
        if ($maj) { Warn "Java $maj found, but JDK $($Required.JdkMajor)+ is required. Installing Temurin 17..." }
        else      { Info "Installing JDK $($Required.JdkMajor)..." }
        if ($Mode -eq "install") {
                [void](Install-OrUpgrade "Temurin JDK 17" $ChocoPackages.Jdk.Name $null `
            -Verify { $m = Get-JavaMajor; $m -and $m -ge $Required.JdkMajor })
        }
}

function Ensure-Maven {
        if (Get-MavenOk) { Ok "Maven already installed."; return }
        Info "Installing Maven..."
        if ($Mode -eq "install") {
                [void](Install-OrUpgrade "Maven" $ChocoPackages.Maven.Name $null `
            -Verify { Get-MavenOk })
        }
}

function Ensure-Dotnet {
        $maj = Get-DotnetMajor
        if ($maj -and $maj -ge $Required.DotnetMajor) { Ok ".NET SDK $maj already installed (>= $($Required.DotnetMajor))."; return }
        if ($maj) { Warn ".NET SDK $maj found, but .NET $($Required.DotnetMajor)+ is required. Installing $($Required.DotnetMajor)..." }
        else      { Info "Installing .NET SDK $($Required.DotnetMajor)..." }

        if ($Mode -ne "install") { return }

        # Try Chocolatey first.
        $ok = Install-OrUpgrade ".NET SDK $($Required.DotnetMajor)" $ChocoPackages.Dotnet.Name $null
        Refresh-Path
        if ((Get-DotnetMajor) -ge $Required.DotnetMajor) { return }

        # Fallback: use Microsoft's official dotnet-install.ps1 (no Winget needed).
        Warn "Chocolatey package did not yield .NET $($Required.DotnetMajor)+. Falling back to Microsoft's dotnet-install.ps1..."
        $tmp = Join-Path $env:TEMP "dotnet-install.ps1"
        # Cache the bootstrap script for a day so reruns skip the re-download.
        $needFetch = $true
        if (Test-Path $tmp) {
                $age = (Get-Date) - (Get-Item $tmp).LastWriteTime
                if ($age.TotalHours -lt 24) { $needFetch = $false; Info "Reusing cached dotnet-install.ps1." }
        }
        if ($needFetch) {
                Invoke-WebRequest -UseBasicParsing `
            -Uri "https://dot.net/v1/dotnet-install.ps1" `
            -OutFile $tmp
        }
        & $tmp -Channel "$($Required.DotnetMajor).0" -InstallDir "$env:ProgramFiles\dotnet"
        $env:Path = "$env:ProgramFiles\dotnet;$env:Path"
        Refresh-Path
}

function Ensure-Python {
        $info = Get-PythonVersion
        if ($info -and ($info.Major -gt $Required.PythonMinMajor -or
                ($info.Major -eq $Required.PythonMinMajor -and $info.Minor -ge $Required.PythonMinMinor))) {
                Ok "Python $($info.Major).$($info.Minor) already installed."
                return
        }
        if ($info) { Warn "Python $($info.Major).$($info.Minor) found, but $($Required.PythonMinMajor).$($Required.PythonMinMinor)+ is required. Upgrading..." }
        else       { Info "Installing Python..." }
        if ($Mode -eq "install") {
                [void](Install-OrUpgrade "Python" $ChocoPackages.Python.Name $null `
            -Verify {
                        $i = Get-PythonVersion
                        $i -and ($i.Major -gt $Required.PythonMinMajor -or
                                ($i.Major -eq $Required.PythonMinMajor -and $i.Minor -ge $Required.PythonMinMinor))
                })
        }
}

function Ensure-Node {
        $maj = Get-NodeMajor
        if ($maj -ge $Required.NodeMinMajor) { Ok "Node.js $maj already installed."; return }
        if ($maj) { Warn "Node.js $maj found, but $($Required.NodeMinMajor)+ is required. Upgrading..." }
        else      { Info "Installing Node.js LTS..." }
        if ($Mode -eq "install") {
                [void](Install-OrUpgrade "Node.js LTS" $ChocoPackages.Node.Name $null `
            -Verify { (Get-NodeMajor) -ge $Required.NodeMinMajor })
        }
}

function Ensure-Racket {
        $maj = Get-RacketMajor
        if ($maj -ge $Required.RacketMajor) {
                Ok "Racket $maj already installed."
                Ensure-RacketOnPath   # heal stale PATH even on the warm path
                return
        }
        if ($maj) { Warn "Racket $maj found, but $($Required.RacketMajor)+ is required. Upgrading..." }
        else      { Info "Installing Racket (package is ~600 MB, may take 5-10 minutes)..." }
        if ($Mode -eq "install") {
                Invoke-Step "Installing Racket" "Downloading ~600 MB installer, please wait..." {
                        [void](Install-OrUpgrade "Racket" $ChocoPackages.Racket.Name $null `
                -Verify { (Get-RacketMajor) -ge $Required.RacketMajor })
                }
                Ensure-RacketOnPath
        }
}

# Chocolatey's racket package drops Racket at C:\Program Files\Racket but
# does not reliably add that dir to Machine PATH. Get-RacketMajor papers
# over the gap for the script's own verification, but external processes
# spawned later (mvn surefire running 'racket project/tests/x.rkt', for
# instance) inherit PATH and fail with "'racket' is not recognized."
# Add the install dir to Machine PATH and the current session if missing
# so child processes find it.
function Ensure-RacketOnPath {
        $candidates = @(
                "C:\Program Files\Racket",
                "C:\Program Files (x86)\Racket",
                (Join-Path $env:LOCALAPPDATA "Racket")
        )
        $dir = $candidates | Where-Object { Test-Path (Join-Path $_ "Racket.exe") } | Select-Object -First 1
        if (-not $dir) { return }

        # Persist into Machine PATH for future shells.
        $machine = [Environment]::GetEnvironmentVariable('Path','Machine')
        $entries = $machine -split ';' | ForEach-Object { $_.Trim().TrimEnd('\') }
        if ($entries -notcontains $dir) {
                Info "Adding $dir to Machine PATH (for future shells and child processes)..."
                try {
                        [Environment]::SetEnvironmentVariable('Path', ($entries + $dir -join ';'), 'Machine')
                } catch {
                        Warn "Failed to update Machine PATH: $($_.Exception.Message)"
                }
        }

        # Make the current session see it immediately so any later step in
        # this run (e.g. a Maven test that shells out to racket) works.
        if (($env:Path -split ';') -notcontains $dir) {
                $env:Path = "$env:Path;$dir"
        }
}

# Read-only probe: returns @{Version=...; Chrome=<path>} when both the
# Puppeteer npm package and a Chrome binary are present, otherwise $null.
# Used both by the upfront 'already installed' message and Verify-All.
function Get-PuppeteerInfo {
        $pkg = Join-Path $RootDir "samples\JSProject\node_modules\puppeteer\package.json"
        if (-not (Test-Path $pkg)) { return $null }
        $cacheRoot = Join-Path $env:USERPROFILE ".cache\puppeteer\chrome"
        if (-not (Test-Path $cacheRoot)) { return $null }
        $chrome = Get-ChildItem -Path $cacheRoot -Filter chrome.exe -Recurse -ErrorAction SilentlyContinue |
                Select-Object -First 1
        if (-not $chrome) { return $null }
        $ver = (Get-Content $pkg -Raw | ConvertFrom-Json).version
        return [pscustomobject]@{ Version = $ver; Chrome = $chrome.FullName; Build = $chrome.Directory.Parent.Name }
}

# Print an 'already installed' line up-front when Puppeteer + cached Chrome
# are present, mirroring the per-tool messages. Otherwise note that the
# parallel restore phase will install it.
function Ensure-Puppeteer {
        $info = Get-PuppeteerInfo
        if ($info) { Ok "Puppeteer $($info.Version) already installed."; return }
        Info "Puppeteer not yet installed; the parallel restore phase will fetch it (~170 MB Chrome)."
}

# ---------------------------------------------------------------------------
# Project-level dependency downloads
# ---------------------------------------------------------------------------

function Restore-MavenDeps {
        if (-not (Have mvn)) { Warn "Skipping Maven restore: mvn not on PATH."; return }
        Info "Resolving Maven dependencies (root) -- first run downloads jars from the internet..."
        Invoke-Step "Maven resolve (root)" "Downloading dependencies, please wait..." {
                Push-Location $RootDir
                try { mvn -DskipTests dependency:go-offline } finally { Pop-Location }
        }

        $javaSample = Join-Path $RootDir "samples\MavenProject"
        if (Test-Path $javaSample) {
                Info "Resolving Maven dependencies (samples\MavenProject)..."
                Invoke-Step "Maven resolve (samples\MavenProject)" "Downloading dependencies, please wait..." {
                        Push-Location $javaSample
                        try { mvn -DskipTests dependency:go-offline } finally { Pop-Location }
                }
        }
}

function Restore-NpmDeps {
        if (-not (Have npm)) { Warn "Skipping npm install: npm not on PATH."; return }
        $jsDir = Join-Path $RootDir "samples\JSProject"
        if (-not (Test-Path $jsDir)) { return }
        Info "Installing JS sample dependencies (npm install)..."
        Push-Location $jsDir
        try {
                Invoke-Step "npm install (JSProject)" "Installing packages..." {
                        npm install
                }
                Info "Installing Puppeteer Chrome browser (~170 MB download, may take a few minutes)..."
                Invoke-Step "Puppeteer Chrome download" "Downloading Chrome, please wait..." {
                        npx puppeteer browsers install chrome
                }
        } finally { Pop-Location }
}

function Restore-PythonDeps {
        $info = Get-PythonVersion
        if (-not $info) { Warn "Skipping Python venv: python not on PATH."; return }
        $venvDir    = Join-Path $RootDir "samples\PytestProject\.venv"
        $venvPython = Join-Path $venvDir "Scripts\python.exe"

        if (Test-Path $venvDir) {
                $works = $false
                if (Test-Path $venvPython) {
                        try { & $venvPython --version 2>$null | Out-Null; $works = ($LASTEXITCODE -eq 0) } catch {}
                }
                if (-not $works) {
                        Info "Removing stale Python virtual environment..."
                        Remove-Item -Recurse -Force $venvDir
                }
        }
        if (-not (Test-Path $venvDir)) {
                Info "Creating Python virtual environment..."
                & $info.Exe -m venv $venvDir
        }
        Info "Installing pytest..."
        & $venvPython -m pip install --quiet pytest
}

function Restore-DotnetDeps {
        if (-not (Have dotnet)) { Warn "Skipping dotnet restore: dotnet not on PATH."; return }
        $proj = Join-Path $RootDir "samples\CSProject\tests\CSProject.Tests\CSProject.Tests.csproj"
        if (-not (Test-Path $proj)) { return }
        Info "Restoring .NET test project..."
        Invoke-Step "dotnet restore (CSProject)" "Restoring NuGet packages..." {
                Push-Location (Join-Path $RootDir "samples\CSProject")
                try { dotnet restore $proj } finally { Pop-Location }
        }
}

# Run all four project-restore steps concurrently as background jobs.
# Each is independent (separate package managers, separate caches), so
# parallelizing overlaps network and CPU and shaves time off cold runs.
# Output is streamed as it arrives, prefixed with a [label] for clarity.
function Restore-AllInParallel {
        $jobs = New-Object System.Collections.Generic.List[object]
        $sharedPath = $env:Path

        if (Have mvn) {
                $jobs.Add(@{ Name = "maven"; Job = Start-Job -Name "maven" -ScriptBlock {
                        param($root, $pathEnv)
                        $env:Path = $pathEnv
                        Set-Location $root
                        # -B / --no-transfer-progress kill the byte-by-byte 'Progress (4):'
                        # spam and color codes; otherwise a cold restore prints hundreds
                        # of useless lines for one resolve.
                        mvn -B --no-transfer-progress -DskipTests dependency:go-offline 2>&1
                        $sample = Join-Path $root "samples\MavenProject"
                        if (Test-Path $sample) {
                                Set-Location $sample
                                mvn -B --no-transfer-progress -DskipTests dependency:go-offline 2>&1
                        }
                } -ArgumentList $RootDir, $sharedPath })
        }

        if (Have npm) {
                $jsDir = Join-Path $RootDir "samples\JSProject"
                if (Test-Path $jsDir) {
                        $jobs.Add(@{ Name = "npm"; Job = Start-Job -Name "npm" -ScriptBlock {
                                param($dir, $pathEnv)
                                $env:Path = $pathEnv
                                # Don't let node's deprecation/info messages on stderr blow
                                # up Receive-Job in the parent as NativeCommandError.
                                $ErrorActionPreference = 'Continue'
                                # Force Puppeteer's installer to emit progress even though
                                # Start-Job has no TTY -- otherwise the Chrome download
                                # (~170 MB) is completely silent and looks like a hang.
                                $env:PUPPETEER_LOG_LEVEL = "info"
                                $env:npm_config_progress = "true"
                                Set-Location $dir
                                # Merge stderr -> stdout so warnings come through as plain
                                # output, not as PowerShell error records.
                                npm install 2>&1
                                npx puppeteer browsers install chrome 2>&1
                        } -ArgumentList $jsDir, $sharedPath })
                }
        }

        $pyInfo = Get-PythonVersion
        if ($pyInfo) {
                $venvDir    = Join-Path $RootDir "samples\PytestProject\.venv"
                $venvPython = Join-Path $venvDir "Scripts\python.exe"
                $jobs.Add(@{ Name = "python"; Job = Start-Job -Name "python" -ScriptBlock {
                        param($venv, $venvPy, $pyExe, $pathEnv)
                        $env:Path = $pathEnv
                        $ErrorActionPreference = 'Continue'
                        if (Test-Path $venv) {
                                $works = $false
                                if (Test-Path $venvPy) {
                                        try { & $venvPy --version 2>$null | Out-Null; $works = ($LASTEXITCODE -eq 0) } catch {}
                                }
                                if (-not $works) { Remove-Item -Recurse -Force $venv }
                        }
                        if (-not (Test-Path $venv)) { & $pyExe -m venv $venv 2>&1 }
                        & $venvPy -m pip install --quiet pytest 2>&1
                } -ArgumentList $venvDir, $venvPython, $pyInfo.Exe, $sharedPath })
        }

        if (Have dotnet) {
                $proj = Join-Path $RootDir "samples\CSProject\tests\CSProject.Tests\CSProject.Tests.csproj"
                if (Test-Path $proj) {
                        $csDir = Join-Path $RootDir "samples\CSProject"
                        $jobs.Add(@{ Name = "dotnet"; Job = Start-Job -Name "dotnet" -ScriptBlock {
                                param($dir, $project, $pathEnv)
                                $env:Path = $pathEnv
                                $ErrorActionPreference = 'Continue'
                                Set-Location $dir
                                # --nologo trims the banner; --verbosity minimal cuts per-package noise.
                                dotnet restore $project --nologo --verbosity minimal 2>&1
                        } -ArgumentList $csDir, $proj, $sharedPath })
                }
        }

        if ($jobs.Count -eq 0) { Warn "No project-restore jobs to run."; return }

        Info "Started $($jobs.Count) parallel restore job(s): $(($jobs | ForEach-Object Name) -join ', ')"
        Info "Note: 'npm' includes a ~170 MB silent Puppeteer Chrome download; expect 3-10 min."
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $lastHeartbeat = [System.Diagnostics.Stopwatch]::StartNew()

        # Drop high-volume status lines that don't tell the user anything useful.
        # Errors, warnings, and the final BUILD SUCCESS/FAILURE still come through.
        $noise = @(
                '^\[INFO\] Resolved (plugin|dependency):',
                '^\[INFO\] Downloading from',
                '^\[INFO\] Downloaded from',
                '^Downloading from',
                '^Downloaded from',
                '^Progress \(\d+\):',
                '^\s*$'
        ) -join '|'

        $emit = {
                param($name, $chunk)
                foreach ($line in $chunk) {
                        $text = "$line"
                        if ($text -match $noise) { continue }
                        Write-Host "[$name] $text"
                }
        }

        while ($jobs | Where-Object { $_.Job.State -eq 'Running' }) {
                foreach ($j in $jobs) {
                        $chunk = Receive-Job -Job $j.Job -ErrorAction SilentlyContinue
                        & $emit $j.Name $chunk
                }
                # @(...) forces an array so .Count works under Set-StrictMode when
                # Where-Object returns 0 or 1 items (scalars don't expose .Count).
                $done = @($jobs | Where-Object { $_.Job.State -ne 'Running' }).Count
                $pct  = [int](100 * $done / $jobs.Count)
                Write-Progress -Id 1 -Activity "Project restores (parallel)" `
            -Status "$done/$($jobs.Count) jobs complete" -PercentComplete $pct

                # Heartbeat every 15s so silent downloads (Puppeteer Chrome, big
                # NuGet/Maven jars) don't look like a wedged script.
                if ($lastHeartbeat.Elapsed.TotalSeconds -ge 15) {
                        $still = $jobs | Where-Object { $_.Job.State -eq 'Running' } | ForEach-Object { $_.Name }
                        $mins  = [int]$sw.Elapsed.TotalMinutes
                        $secs  = $sw.Elapsed.Seconds
                        Info ("Heartbeat: {0}m{1:D2}s elapsed; still running: {2}" -f $mins, $secs, ($still -join ', '))
                        $lastHeartbeat.Restart()
                }
                Start-Sleep -Milliseconds 500
        }

        foreach ($j in $jobs) {
                $chunk = Receive-Job -Job $j.Job -ErrorAction SilentlyContinue
                & $emit $j.Name $chunk
                $state = $j.Job.State
                Remove-Job $j.Job -Force
                if ($state -eq 'Completed') { Ok "Restore job '$($j.Name)' finished." }
                else                         { Fail "Restore job '$($j.Name)' ended in state: $state" }
        }
        $sw.Stop()
        Write-Progress -Id 1 -Activity "Project restores (parallel)" -Completed
        Info "All restore jobs done in $([int]$sw.Elapsed.TotalSeconds)s."
}

# ---------------------------------------------------------------------------
# Final verification (runs after install completes - order matters)
# ---------------------------------------------------------------------------

function Verify-All {
        Write-Host ""
        Write-Host "============================================================================"
        Write-Host "Version verification"
        Write-Host "============================================================================"
        Refresh-Path

        $bad = 0
        $jm = Get-JavaMajor;   if ($jm -and $jm -ge $Required.JdkMajor) { Ok "Java $jm" } else { Fail "Java major=$jm (need $($Required.JdkMajor)+)"; $bad++ }
        if (Get-MavenOk)       { Ok "Maven present" }          else { Fail "Maven missing"; $bad++ }
        $dm = Get-DotnetMajor; if ($dm -and $dm -ge $Required.DotnetMajor) { Ok ".NET SDK $dm" } else { Fail ".NET SDK major=$dm (need $($Required.DotnetMajor)+)"; $bad++ }
        $pi = Get-PythonVersion
        if ($pi -and ($pi.Major -gt $Required.PythonMinMajor -or ($pi.Major -eq $Required.PythonMinMajor -and $pi.Minor -ge $Required.PythonMinMinor))) {
                Ok "Python $($pi.Major).$($pi.Minor)"
        } else { Fail "Python missing or too old"; $bad++ }
        $nm = Get-NodeMajor;   if ($nm -ge $Required.NodeMinMajor) { Ok "Node.js $nm" } else { Fail "Node.js major=$nm (need $($Required.NodeMinMajor)+)"; $bad++ }
        $rm = Get-RacketMajor; if ($rm -ge $Required.RacketMajor)  { Ok "Racket $rm"  } else { Fail "Racket major=$rm (need $($Required.RacketMajor)+)"; $bad++ }

        # Puppeteer: the JS sample's tests drive a headless Chrome that gets
        # downloaded into the per-user puppeteer cache, separate from any
        # Chrome the user already has installed.
        $pup = Get-PuppeteerInfo
        if ($pup) {
                Ok "Puppeteer $($pup.Version) (+ Chrome at $($pup.Build))"
        } else {
                $pkg   = Join-Path $RootDir "samples\JSProject\node_modules\puppeteer\package.json"
                $cache = Join-Path $env:USERPROFILE ".cache\puppeteer\chrome"
                $missing = @()
                if (-not (Test-Path $pkg))   { $missing += "npm package" }
                if (-not (Test-Path $cache)) { $missing += "Chrome binary" }
                Fail "Puppeteer missing: $($missing -join ', ')"
                $bad++
        }

        return $bad
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

Write-Host "============================================================================"
Write-Host "Mutation Tester - Chocolatey-based Windows setup"
Write-Host "Mode: $Mode    Project root: $RootDir"
Write-Host "============================================================================"

Refresh-Path

if ($Mode -eq "install") {
        Assert-Admin
        Ensure-Choco
}

# Install/upgrade tools FIRST, then verify versions.
Ensure-Jdk
Ensure-Maven
Ensure-Dotnet
Ensure-Python
Ensure-Node
Ensure-Racket
Ensure-Puppeteer

if ($Mode -eq "install") {
        Write-Host ""
        Write-Host "============================================================================"
        Write-Host "Restoring project dependencies (in parallel)"
        Write-Host "============================================================================"
        Restore-AllInParallel
}

$bad = Verify-All

Write-Host ""
if ($bad -eq 0) {
        Ok "All required tools are installed at the correct version."
        Write-Host ""
        Write-Host "Next steps:"
        Write-Host "  mvn package -DskipTests"
        Write-Host "  java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar [project_dir] [config]"
        exit 0
} else {
        Fail "$bad tool(s) are still missing or at the wrong version."
        if ($Mode -ne "install") {
                Warn "Re-run with --install to fix:    .\chocolatey-setup.ps1 --install"
        } else {
                Warn "Open a NEW elevated PowerShell window (so PATH changes take effect) and re-run --install."
        }
        exit 1
}