#Requires -Version 5.1
#
# Sets up the Windows toolchain for the mutation tester.
#
#   .\chocolatey-setup.ps1                        see what's installed, change nothing
#   .\chocolatey-setup.ps1 --install              install whatever's missing
#   .\chocolatey-setup.ps1 --install --minimal    skip Racket and Puppeteer's Chrome
#   .\chocolatey-setup.ps1 --install --only dotnet,node
#   .\chocolatey-setup.ps1 --install --skip racket
#   .\chocolatey-setup.ps1 --install --portable   no Chocolatey, no admin needed
#   .\chocolatey-setup.ps1 --help
#
# Notes:
#   - Existing tools are used as-is. Nothing is upgraded or reinstalled.
#   - The system PATH is never touched. We write demo-env.ps1 / demo-env.cmd
#     instead; dot-source one to set up a shell.
#   - Admin is optional. Without it we skip Chocolatey and unzip into .tools\.
#   - Only the JDK and Maven are required. The rest each gate one language sample.
#
# To undo: delete .tools\, demo-env.ps1, demo-env.cmd.

param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Arguments)

$ErrorActionPreference = 'Continue'   # plenty of these tools write to stderr on success
$ProgressPreference    = 'SilentlyContinue'

# ---------------------------------------------------------------------------
# Arguments
# ---------------------------------------------------------------------------

$Mode      = 'check'
$Minimal   = $false
$Portable  = $false
$OnlyList  = @()
$SkipList  = @()
$BadArg    = $false

if (-not $Arguments) { $Arguments = @() }

for ($i = 0; $i -lt $Arguments.Count; $i++) {
    $a = $Arguments[$i]
    $val = $null
    if ($a -match '^(--[a-z-]+)=(.*)$') { $a = $Matches[1]; $val = $Matches[2] }

    switch -Regex ($a) {
        '^(--install|-Install|-i|/install)$'   { $Mode = 'install' }
        '^(--check|-Check|-c|/check)$'         { $Mode = 'check' }
        '^(--help|-Help|-h|/\?|/help)$'        { $Mode = 'help' }
        '^(--minimal|-Minimal)$'               { $Minimal = $true }
        '^(--portable|-Portable|--no-admin)$'  { $Portable = $true }
        '^(--only|-Only)$' {
            if ($null -eq $val) { $i++; $val = $Arguments[$i] }
            $OnlyList = @($val -split '[,;]' | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ })
        }
        '^(--skip|-Skip)$' {
            if ($null -eq $val) { $i++; $val = $Arguments[$i] }
            $SkipList = @($val -split '[,;]' | ForEach-Object { $_.Trim().ToLower() } | Where-Object { $_ })
        }
        default { Write-Host "Unknown argument: $a" -ForegroundColor Yellow; $BadArg = $true }
    }
}

$RootDir     = $PSScriptRoot
$ToolsDir    = Join-Path $RootDir '.tools'
$ShimDir     = Join-Path $ToolsDir 'bin'
$DownloadDir = Join-Path $ToolsDir 'downloads'

function Show-Help {
    # Echo the header block, stopping at the first non-comment line.
    foreach ($line in [IO.File]::ReadAllLines($PSCommandPath)) {
        if ($line -match '^\s*#Requires') { continue }
        if ($line -notmatch '^#') { break }
        Write-Host ($line -replace '^#\s?', '')
    }
}

if ($Mode -eq 'help' -or $BadArg) { Show-Help; exit ([int]$BadArg) }

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

function Info { param($m) Write-Host "[INFO] $m" -ForegroundColor Cyan }
function Ok   { param($m) Write-Host "[OK]   $m" -ForegroundColor Green }
function Warn { param($m) Write-Host "[WARN] $m" -ForegroundColor Yellow }
function Fail { param($m) Write-Host "[FAIL] $m" -ForegroundColor Red }
function Head {
    param($m)
    Write-Host ''
    Write-Host ('=' * 76)
    Write-Host $m
    Write-Host ('=' * 76)
}

# ---------------------------------------------------------------------------
# Process / filesystem helpers
# ---------------------------------------------------------------------------

# Run a command and capture its output, with a timeout so nothing can wedge us.
# Read the pipes async or we deadlock once the child fills the 4 KB buffer, and
# route .cmd/.bat through cmd.exe since CreateProcess won't run them (mvn.cmd).
function Invoke-Native {
    param(
        [string]   $Exe,
        [string[]] $Argv = @(),
        [int]      $TimeoutSec = 30
    )
    if (-not $Exe) { return '' }

    $file = $Exe
    $list = @($Argv)
    if ($Exe -match '\.(cmd|bat)$') {
        $file = "$env:SystemRoot\System32\cmd.exe"
        $list = @('/d', '/c', $Exe) + $Argv
    }

    $quoted = foreach ($x in $list) {
        if ("$x" -match '[\s"]') { '"' + ("$x" -replace '"', '\"') + '"' } else { "$x" }
    }

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName               = $file
    $psi.Arguments              = ($quoted -join ' ')
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError  = $true
    $psi.RedirectStandardInput  = $true      # anything that prompts gets EOF instead of hanging
    $psi.UseShellExecute        = $false
    $psi.CreateNoWindow         = $true

    $p = New-Object System.Diagnostics.Process
    $p.StartInfo = $psi
    try {
        if (-not $p.Start()) { return '' }
        $outTask = $p.StandardOutput.ReadToEndAsync()
        $errTask = $p.StandardError.ReadToEndAsync()
        try { $p.StandardInput.Close() } catch { }
        if (-not $p.WaitForExit($TimeoutSec * 1000)) {
            try { $p.Kill() } catch { }
            Warn "'$Exe' did not respond within ${TimeoutSec}s; killed."
            return ''
        }
        return (@($outTask.Result, $errTask.Result) -join "`n")
    } catch {
        return ''
    } finally {
        try { $p.Dispose() } catch { }
    }
}

function Test-Admin {
    try {
        $id = [Security.Principal.WindowsIdentity]::GetCurrent()
        return (New-Object Security.Principal.WindowsPrincipal($id)).IsInRole(
            [Security.Principal.WindowsBuiltInRole]::Administrator)
    } catch { return $false }
}

function New-Dir {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        New-Item -ItemType Directory -Path $Path -Force | Out-Null
    }
    return $Path
}

# Pull PATH back out of the registry so tools installed a moment ago show up.
# Dedupe while we're at it - Choco tends to add the same dirs to both Machine
# and User scope, and Windows truncates anything past 8191 chars.
function Sync-ProcessPath {
    $raw = @()
    foreach ($scope in 'Machine', 'User') {
        $v = [Environment]::GetEnvironmentVariable('Path', $scope)
        if ($v) { $raw += ($v -split ';') }
    }
    $seen = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    $keep = New-Object System.Collections.Generic.List[string]
    foreach ($e in $raw) {
        $t = "$e".Trim().TrimEnd('\')
        if ($t -and $seen.Add($t)) { [void]$keep.Add($t) }
    }
    $joined = $keep -join ';'
    if ($joined.Length -le 8191) { $env:Path = $joined }
    else { Warn "Deduped PATH is $($joined.Length) chars (limit 8191); keeping the current one." }
}

function Enable-Tls12 {
    try {
        [Net.ServicePointManager]::SecurityProtocol =
            [Net.ServicePointManager]::SecurityProtocol -bor [Net.SecurityProtocolType]::Tls12
    } catch { }
    # So downloads work behind a corporate proxy.
    try {
        if ([Net.WebRequest]::DefaultWebProxy) {
            [Net.WebRequest]::DefaultWebProxy.Credentials = [Net.CredentialCache]::DefaultCredentials
        }
    } catch { }
}

function Get-RemoteFile {
    param([string]$Uri, [string]$OutFile, [string]$Label = 'file')
    Enable-Tls12
    New-Dir (Split-Path -Parent $OutFile) | Out-Null
    if (Test-Path -LiteralPath $OutFile) {
        $age = (Get-Date) - (Get-Item -LiteralPath $OutFile).LastWriteTime
        if ($age.TotalDays -lt 7 -and (Get-Item -LiteralPath $OutFile).Length -gt 0) {
            Info "Reusing cached download for $Label."
            return $true
        }
    }
    Info "Downloading $Label ..."
    try {
        Invoke-WebRequest -Uri $Uri -OutFile $OutFile -UseBasicParsing -TimeoutSec 900
        return $true
    } catch {
        Warn "Download of $Label failed: $($_.Exception.Message)"
        return $false
    }
}

# Unzip and hand back whichever directory holds bin\. These archives nest
# everything one level down (jdk-17.0.x+7\, apache-maven-3.9.9\, ...).
function Expand-ToolArchive {
    param([string]$Archive, [string]$Dest)
    if (Test-Path -LiteralPath $Dest) { Remove-Item -LiteralPath $Dest -Recurse -Force -ErrorAction SilentlyContinue }
    New-Dir $Dest | Out-Null
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
        [IO.Compression.ZipFile]::ExtractToDirectory($Archive, $Dest)
    } catch {
        try { Expand-Archive -LiteralPath $Archive -DestinationPath $Dest -Force -ErrorAction Stop }
        catch { Warn "Could not unpack $Archive : $($_.Exception.Message)"; return $null }
    }
    if (Test-Path -LiteralPath (Join-Path $Dest 'bin')) { return $Dest }
    $child = Get-ChildItem -LiteralPath $Dest -Directory -ErrorAction SilentlyContinue |
             Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'bin') } |
             Select-Object -First 1
    if ($child) { return $child.FullName }
    return $Dest
}

# ---------------------------------------------------------------------------
# Chocolatey - only used when we're already elevated
# ---------------------------------------------------------------------------

$script:ChocoExe    = $null
$script:ChocoUsable = $null

function Get-Choco {
    if ($null -ne $script:ChocoUsable) { return $script:ChocoExe }
    $script:ChocoUsable = $false

    if ($Portable) { Info 'Chocolatey disabled (--portable).'; return $null }
    if (-not (Test-Admin)) { return $null }

    $cmd = Get-Command choco -ErrorAction SilentlyContinue
    if ($cmd) { $script:ChocoExe = $cmd.Source }
    elseif (Test-Path -LiteralPath "$env:ProgramData\chocolatey\bin\choco.exe") {
        $script:ChocoExe = "$env:ProgramData\chocolatey\bin\choco.exe"
    }

    if (-not $script:ChocoExe -and $Mode -eq 'install') {
        Info 'Installing Chocolatey ...'
        try {
            Enable-Tls12
            Set-ExecutionPolicy Bypass -Scope Process -Force -ErrorAction SilentlyContinue
            $installer = (New-Object Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1')
            Invoke-Expression $installer | Out-Null
            Sync-ProcessPath
            if (Test-Path -LiteralPath "$env:ProgramData\chocolatey\bin\choco.exe") {
                $script:ChocoExe = "$env:ProgramData\chocolatey\bin\choco.exe"
            }
        } catch {
            Warn "Chocolatey bootstrap failed: $($_.Exception.Message)"
        }
    }

    if ($script:ChocoExe) { $script:ChocoUsable = $true }
    return $script:ChocoExe
}

# Only called when the tool is actually missing, so no --force and no upgrade
# path that could stomp on something the user installed themselves.
function Install-ChocoPackage {
    param([string]$Package, [string]$Label, [int]$TimeoutSec = 1800)

    $choco = Get-Choco
    if (-not $choco) { return $false }

    Info "Chocolatey: installing $Label ($Package). This can take several minutes."
    $out = Invoke-Native $choco @(
        'install', $Package, '-y', '--no-progress', '--limit-output', '--no-color',
        '--ignore-package-exit-codes', '--execution-timeout', "$TimeoutSec"
    ) -TimeoutSec ($TimeoutSec + 120)

    foreach ($line in ($out -split "`r?`n")) {
        if ($line -match 'ERROR|WARNING|Installed|already installed|reboot') { Write-Host "       $line" }
    }
    Sync-ProcessPath
    return $true   # caller re-probes anyway; choco's exit code isn't worth trusting
}

# ---------------------------------------------------------------------------
# Version probes - each returns a [version], or $null if unusable
# ---------------------------------------------------------------------------

function Probe-Jdk {
    param([string]$Exe)
    # Needs javac next to it, or it's a JRE and Maven will die with
    # "No compiler is provided in this environment".
    $dir = Split-Path -Parent $Exe
    if (-not (Test-Path -LiteralPath (Join-Path $dir 'javac.exe'))) { return $null }
    $out = Invoke-Native $Exe @('-version') -TimeoutSec 25
    if ($out -match '"(\d+)(?:\.(\d+))?') {
        $maj = [int]$Matches[1]
        $min = 0
        if ($Matches.ContainsKey(2) -and $Matches[2]) { $min = [int]$Matches[2] }
        if ($maj -eq 1) { return [version]("{0}.0" -f $min) }   # 1.8.0_402 -> 8.0
        return [version]("{0}.0" -f $maj)
    }
    return $null
}

# Ask the JVM where it lives instead of guessing from the path. The java.exe
# first on PATH is usually Oracle's javapath shim, which has javac beside it but
# isn't a JDK home - guessing there gives you a JAVA_HOME with no bin\ or lib\.
function Resolve-JavaHome {
    param([string]$Exe)
    $out = Invoke-Native $Exe @('-XshowSettings:properties', '-version') -TimeoutSec 25
    foreach ($line in ($out -split "`r?`n")) {
        if ($line -match '^\s*java\.home\s*=\s*(.+?)\s*$') {
            $h = $Matches[1]
            if (Test-Path -LiteralPath (Join-Path $h 'bin\java.exe')) { return $h }
        }
    }
    # If it won't say, fall back to the usual <home>\bin\java.exe layout.
    $guess = Split-Path -Parent (Split-Path -Parent $Exe)
    if ($guess -and (Test-Path -LiteralPath (Join-Path $guess 'bin\java.exe'))) { return $guess }
    return $null
}

function Resolve-JdkExe {
    param([string]$Exe)
    $h = Resolve-JavaHome $Exe
    if ($h) { return (Join-Path $h 'bin\java.exe') }
    return $Exe
}

function Probe-Maven {
    param([string]$Exe)
    $out = Invoke-Native $Exe @('-v') -TimeoutSec 60
    if ($out -match 'Apache Maven (\d+)\.(\d+)') { return [version]("{0}.{1}" -f $Matches[1], $Matches[2]) }
    # mvn -v needs a JDK. Without one, read the version off the install path so
    # we don't report Maven missing for the wrong reason.
    if ($Exe -match 'maven-(\d+)\.(\d+)') { return [version]("{0}.{1}" -f $Matches[1], $Matches[2]) }
    return $null
}

function Probe-Dotnet {
    param([string]$Exe)
    # Highest SDK wins. .NET is side-by-side, so an existing 8.x/9.x is fine.
    $out = Invoke-Native $Exe @('--list-sdks') -TimeoutSec 45
    if (-not $out) { return $null }
    $best = $null
    foreach ($line in ($out -split "`r?`n")) {
        if ($line -match '^(\d+)\.(\d+)\.(\d+)') {
            $v = [version]("{0}.{1}" -f $Matches[1], $Matches[2])
            if (-not $best -or $v -gt $best) { $best = $v }
        }
    }
    return $best
}

function Probe-Node {
    param([string]$Exe)
    $out = Invoke-Native $Exe @('--version') -TimeoutSec 25
    if ($out -match 'v(\d+)\.(\d+)') { return [version]("{0}.{1}" -f $Matches[1], $Matches[2]) }
    return $null
}

function Probe-Python {
    param([string]$Exe)
    $out = Invoke-Native $Exe @('--version') -TimeoutSec 20
    if ($out -match 'Python (\d+)\.(\d+)') { return [version]("{0}.{1}" -f $Matches[1], $Matches[2]) }
    return $null
}

function Probe-Racket {
    param([string]$Exe)
    $out = Invoke-Native $Exe @('--version') -TimeoutSec 30
    if ($out -match 'v(\d+)\.(\d+)') { return [version]("{0}.{1}" -f $Matches[1], $Matches[2]) }
    if ($out -match 'v(\d+)')        { return [version]("{0}.0" -f $Matches[1]) }
    return $null
}

# ---------------------------------------------------------------------------
# Discovery - candidates come back in priority order, whatever the user
# already has first, our own .tools\ copies last
# ---------------------------------------------------------------------------

function Get-PathCandidates {
    param([string]$Name, [switch]$ExcludeStoreStubs)
    $out = @()
    foreach ($c in @(Get-Command $Name -All -ErrorAction SilentlyContinue)) {
        $src = $c.Source
        if (-not $src) { continue }
        # Skip the Store alias stubs in WindowsApps - they're 0-byte and will
        # sit there for minutes waiting on the Store UI that never opens.
        if ($ExcludeStoreStubs -and $src -match '\\Microsoft\\WindowsApps\\') { continue }
        try { if ((Get-Item -LiteralPath $src).Length -le 0) { continue } } catch { continue }
        $out += $src
    }
    return $out
}

function Get-GlobCandidates {
    param([string[]]$Patterns)
    $out = @()
    foreach ($p in $Patterns) {
        if (-not $p) { continue }
        try { $out += @(Get-ChildItem -Path $p -ErrorAction SilentlyContinue | ForEach-Object { $_.FullName }) } catch { }
    }
    return $out
}

function Get-RegistryValues {
    param([string]$KeyGlob, [string]$ValueName)
    $out = @()
    try {
        foreach ($k in @(Get-ItemProperty -Path $KeyGlob -ErrorAction SilentlyContinue)) {
            $v = $k.$ValueName
            if ($v) { $out += $v }
        }
    } catch { }
    return $out
}

function Find-Jdk {
    $c = @()
    if ($env:JAVA_HOME) { $c += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
    $c += Get-PathCandidates 'java'
    foreach ($jh in (Get-RegistryValues 'HKLM:\SOFTWARE\JavaSoft\JDK\*' 'JavaHome')) { $c += (Join-Path $jh 'bin\java.exe') }
    foreach ($p in (Get-RegistryValues 'HKLM:\SOFTWARE\Eclipse Adoptium\JDK\*\hotspot\MSI' 'Path')) { $c += (Join-Path $p 'bin\java.exe') }
    $c += Get-GlobCandidates @(
        'C:\Program Files\Eclipse Adoptium\jdk*\bin\java.exe',
        'C:\Program Files\Java\jdk*\bin\java.exe',
        'C:\Program Files\Microsoft\jdk*\bin\java.exe',
        'C:\Program Files\Amazon Corretto\jdk*\bin\java.exe',
        'C:\Program Files\Zulu\zulu*\bin\java.exe',
        'C:\Program Files\BellSoft\*\bin\java.exe',
        'C:\Program Files\RedHat\java*\bin\java.exe',
        "$env:ProgramData\chocolatey\lib\*jdk*\tools\*\bin\java.exe",
        "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk*\bin\java.exe",
        (Join-Path $ToolsDir 'jdk\*\bin\java.exe'),
        (Join-Path $ToolsDir 'jdk\bin\java.exe')
    )
    return $c
}

function Find-Maven {
    $c = @()
    foreach ($h in @($env:MAVEN_HOME, $env:M2_HOME)) {
        if ($h) { $c += (Join-Path $h 'bin\mvn.cmd') }
    }
    $c += Get-PathCandidates 'mvn'
    $c += Get-GlobCandidates @(
        "$env:ProgramData\chocolatey\lib\maven\apache-maven-*\bin\mvn.cmd",
        'C:\Program Files\Apache\maven*\bin\mvn.cmd',
        'C:\Program Files\apache-maven-*\bin\mvn.cmd',
        'C:\apache-maven-*\bin\mvn.cmd',
        "$env:USERPROFILE\scoop\apps\maven\current\bin\mvn.cmd",
        (Join-Path $ToolsDir 'maven\*\bin\mvn.cmd'),
        (Join-Path $ToolsDir 'maven\bin\mvn.cmd')
    )
    return $c
}

function Find-Dotnet {
    $c = @()
    if ($env:DOTNET_ROOT) { $c += (Join-Path $env:DOTNET_ROOT 'dotnet.exe') }
    # Ours has to win over a system install: dotnet.exe only finds SDKs sitting
    # next to it, so DOTNET_ROOT won't make our SDK 10 visible to the one in
    # Program Files.
    $c += @(Join-Path $ToolsDir 'dotnet\dotnet.exe')
    $c += Get-PathCandidates 'dotnet'
    foreach ($p in (Get-RegistryValues 'HKLM:\SOFTWARE\dotnet\Setup\InstalledVersions\x64' 'InstallLocation')) {
        $c += (Join-Path $p 'dotnet.exe')
    }
    $c += @(
        "$env:ProgramFiles\dotnet\dotnet.exe",
        "$env:LOCALAPPDATA\Microsoft\dotnet\dotnet.exe"
    )
    return $c
}

function Find-Node {
    $c = @()
    $c += Get-PathCandidates 'node'
    if ($env:NVM_SYMLINK) { $c += (Join-Path $env:NVM_SYMLINK 'node.exe') }
    $c += Get-GlobCandidates @(
        'C:\Program Files\nodejs\node.exe',
        "$env:APPDATA\nvm\v*\node.exe",
        "$env:LOCALAPPDATA\nvs\node\*\*\node.exe",
        "$env:ProgramData\chocolatey\lib\nodejs*\tools\node.exe",
        (Join-Path $ToolsDir 'node\*\node.exe'),
        (Join-Path $ToolsDir 'node\node.exe')
    )
    return $c
}

function Find-Python {
    $c = @()
    # py.exe lists every registered interpreter and won't hang on the Store.
    $py = (Get-Command py -ErrorAction SilentlyContinue)
    if ($py) {
        $out = Invoke-Native $py.Source @('-0p') -TimeoutSec 15
        foreach ($line in ($out -split "`r?`n")) {
            if ($line -match '([A-Za-z]:\\[^\r\n]*python\.exe)') { $c += $Matches[1].Trim() }
        }
    }
    $c += Get-PathCandidates 'python'  -ExcludeStoreStubs
    $c += Get-PathCandidates 'python3' -ExcludeStoreStubs
    $c += Get-GlobCandidates @(
        "$env:LOCALAPPDATA\Programs\Python\Python3*\python.exe",
        'C:\Python3*\python.exe',
        'C:\Program Files\Python3*\python.exe',
        'C:\Program Files (x86)\Python3*\python.exe',
        "$env:ProgramData\chocolatey\lib\python3*\tools\python.exe"
    )
    return $c
}

function Find-Racket {
    $c = @()
    $c += Get-PathCandidates 'racket'
    $c += Get-GlobCandidates @(
        'C:\Program Files\Racket*\Racket.exe',
        'C:\Program Files (x86)\Racket*\Racket.exe',
        "$env:LOCALAPPDATA\Racket*\Racket.exe",
        "$env:ProgramData\chocolatey\lib\racket\tools\*\Racket.exe",
        "$env:USERPROFILE\scoop\apps\racket\current\Racket.exe"
    )
    return $c
}

# ---------------------------------------------------------------------------
# Portable installs - straight into .tools\, no admin, nothing machine-wide
# ---------------------------------------------------------------------------

function Install-JdkPortable {
    Info 'Fetching Temurin JDK 17 from Adoptium (portable, no admin required) ...'
    Enable-Tls12
    $url = $null
    try {
        $api = Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -Uri `
            'https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse'
        foreach ($asset in ($api.Content | ConvertFrom-Json)) {
            $link = $asset.binary.package.link
            if ($link -and $link -match '\.zip$') { $url = $link; break }
        }
    } catch {
        Warn "Adoptium API unreachable: $($_.Exception.Message)"
    }
    if (-not $url) { $url = 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' }

    $zip = Join-Path $DownloadDir 'temurin-jdk17.zip'
    if (-not (Get-RemoteFile $url $zip 'Temurin JDK 17 (~180 MB)')) { return $false }
    $dir = Expand-ToolArchive $zip (Join-Path $ToolsDir 'jdk')
    return [bool]$dir
}

function Install-MavenPortable {
    # Pull from archive.apache.org - the dlcdn mirrors drop old releases.
    $ver = '3.9.9'
    $url = "https://archive.apache.org/dist/maven/maven-3/$ver/binaries/apache-maven-$ver-bin.zip"
    $zip = Join-Path $DownloadDir "apache-maven-$ver-bin.zip"
    if (-not (Get-RemoteFile $url $zip "Apache Maven $ver (~9 MB)")) { return $false }
    $dir = Expand-ToolArchive $zip (Join-Path $ToolsDir 'maven')
    return [bool]$dir
}

function Install-NodePortable {
    Enable-Tls12
    $ver = $null
    try {
        $idx = (Invoke-WebRequest -UseBasicParsing -TimeoutSec 60 -Uri 'https://nodejs.org/dist/index.json').Content | ConvertFrom-Json
        foreach ($rel in $idx) {
            if ($rel.lts -and $rel.version -match '^v(\d+)' -and [int]$Matches[1] -ge 20) { $ver = $rel.version; break }
        }
    } catch {
        Warn "nodejs.org index unreachable: $($_.Exception.Message)"
    }
    if (-not $ver) { $ver = 'v22.11.0' }

    $url = "https://nodejs.org/dist/$ver/node-$ver-win-x64.zip"
    $zip = Join-Path $DownloadDir "node-$ver-win-x64.zip"
    if (-not (Get-RemoteFile $url $zip "Node.js $ver (~30 MB)")) { return $false }

    # Node's zip has no bin\, so Expand-ToolArchive can't find its landmark.
    # Unpack it here and look for node.exe instead.
    $dest = Join-Path $ToolsDir 'node'
    if (Test-Path -LiteralPath $dest) { Remove-Item -LiteralPath $dest -Recurse -Force -ErrorAction SilentlyContinue }
    New-Dir $dest | Out-Null
    try {
        Add-Type -AssemblyName System.IO.Compression.FileSystem -ErrorAction Stop
        [IO.Compression.ZipFile]::ExtractToDirectory($zip, $dest)
    } catch {
        try { Expand-Archive -LiteralPath $zip -DestinationPath $dest -Force -ErrorAction Stop }
        catch { Warn "Could not unpack Node.js: $($_.Exception.Message)"; return $false }
    }
    return [bool](Get-ChildItem -Path (Join-Path $dest '*\node.exe') -ErrorAction SilentlyContinue)
}

function Install-DotnetSdk {
    param([int]$Major)
    # Microsoft's installer rather than the Choco package - it's the supported
    # route, needs no admin, and installs alongside whatever .NET is already here.
    $script = Join-Path $DownloadDir 'dotnet-install.ps1'
    if (-not (Get-RemoteFile 'https://dot.net/v1/dotnet-install.ps1' $script 'dotnet-install.ps1')) { return $false }

    $target = Join-Path $ToolsDir 'dotnet'
    New-Dir $target | Out-Null
    Info ".NET SDK $Major.0 -> $target (side-by-side; existing .NET installs are untouched)"
    try {
        & powershell.exe -NoProfile -ExecutionPolicy Bypass -File $script `
            -Channel "$Major.0" -InstallDir $target -NoPath 2>&1 |
            ForEach-Object { Write-Host "       $_" }
        return (Test-Path -LiteralPath (Join-Path $target 'dotnet.exe'))
    } catch {
        Warn "dotnet-install.ps1 failed: $($_.Exception.Message)"
        return $false
    }
}

function Install-Racket {
    if (Install-ChocoPackage 'racket' 'Racket (~600 MB)' 2700) { return $true }
    Warn 'Racket has no reliable no-admin install. Install it manually from https://download.racket-lang.org/'
    Warn 'and re-run this script; everything else will still work without it.'
    return $false
}

# ---------------------------------------------------------------------------
# Tool table
# ---------------------------------------------------------------------------

$Tools = @(
    [ordered]@{
        Key = 'java'; Label = 'JDK'; Min = [version]'17.0'; Required = $true; Heavy = $false
        Gate = 'the mutation tester itself'
        Find = { Find-Jdk }; Probe = { param($e) Probe-Jdk $e }
        Normalize = { param($e) Resolve-JdkExe $e }
        Install = { if (Install-ChocoPackage 'temurin17' 'Temurin JDK 17') { $true } else { Install-JdkPortable } }
        BinDir = { param($e) Split-Path -Parent $e }
    }
    [ordered]@{
        Key = 'maven'; Label = 'Maven'; Min = [version]'3.8'; Required = $true; Heavy = $false
        Gate = 'building the mutation tester'
        Find = { Find-Maven }; Probe = { param($e) Probe-Maven $e }
        Install = { if (Install-ChocoPackage 'maven' 'Apache Maven') { $true } else { Install-MavenPortable } }
        BinDir = { param($e) Split-Path -Parent $e }
    }
    [ordered]@{
        Key = 'dotnet'; Label = '.NET SDK'; Min = [version]'10.0'; Required = $false; Heavy = $false
        Gate = 'the C# sample (samples\CSProject targets net10.0)'
        Find = { Find-Dotnet }; Probe = { param($e) Probe-Dotnet $e }
        Install = { Install-DotnetSdk 10 }
        BinDir = { param($e) Split-Path -Parent $e }
    }
    [ordered]@{
        Key = 'node'; Label = 'Node.js'; Min = [version]'20.0'; Required = $false; Heavy = $false
        Gate = 'the JavaScript sample (Jest + Puppeteer)'
        Find = { Find-Node }; Probe = { param($e) Probe-Node $e }
        Install = { if (Install-ChocoPackage 'nodejs-lts' 'Node.js LTS') { $true } else { Install-NodePortable } }
        BinDir = { param($e) Split-Path -Parent $e }
    }
    [ordered]@{
        Key = 'python'; Label = 'Python'; Min = [version]'3.9'; Required = $false; Heavy = $false
        Gate = 'the Python sample (pytest)'
        Find = { Find-Python }; Probe = { param($e) Probe-Python $e }
        Install = { Install-ChocoPackage 'python3' 'Python 3' }
        BinDir = { param($e) Split-Path -Parent $e }
    }
    [ordered]@{
        Key = 'racket'; Label = 'Racket'; Min = [version]'8.0'; Required = $false; Heavy = $true
        Gate = 'the Racket sample'
        Find = { Find-Racket }; Probe = { param($e) Probe-Racket $e }
        Install = { Install-Racket }
        BinDir = { param($e) Split-Path -Parent $e }
    }
)

function Test-Selected {
    param($Tool)   # don't type this - [ordered] gives an OrderedDictionary, not a Hashtable
    if ($OnlyList.Count -gt 0 -and $OnlyList -notcontains $Tool.Key) { return $false }
    if ($SkipList -contains $Tool.Key) { return $false }
    if ($Minimal -and $Tool.Heavy)     { return $false }
    return $true
}

# First candidate that runs and is new enough wins. Hang onto anything that ran
# but was too old, so the summary can say "found 9.0, need 10.0" not "missing".
function Resolve-Tool {
    param($Tool)

    $seen     = New-Object 'System.Collections.Generic.HashSet[string]' ([StringComparer]::OrdinalIgnoreCase)
    $fallback = $null

    foreach ($exe in (& $Tool.Find)) {
        if (-not $exe) { continue }
        $exe = "$exe".Trim()
        if (-not $seen.Add($exe)) { continue }
        if (-not (Test-Path -LiteralPath $exe)) { continue }

        $v = & $Tool.Probe $exe
        if (-not $v) { continue }

        if ($v -ge $Tool.Min) {
            # Swap a shim path for the real install (see Resolve-JdkExe).
            if ($Tool.Contains('Normalize')) { $exe = & $Tool.Normalize $exe }
            return [pscustomobject]@{ Exe = $exe; Version = $v; Meets = $true }
        }
        if (-not $fallback) {
            $fallback = [pscustomobject]@{ Exe = $exe; Version = $v; Meets = $false }
        }
    }
    return $fallback
}

# ---------------------------------------------------------------------------
# Find everything, install only what's missing
# ---------------------------------------------------------------------------

Head 'Mutation Tester - Windows environment setup'
Write-Host "Mode          : $Mode$(if ($Minimal) { ' (minimal)' })"
Write-Host "Project root  : $RootDir"
Write-Host "Administrator : $(if (Test-Admin) { 'yes' } else { 'no - Chocolatey will be skipped, portable installs used instead' })"
if ($OnlyList.Count) { Write-Host "Only          : $($OnlyList -join ', ')" }
if ($SkipList.Count) { Write-Host "Skipping      : $($SkipList -join ', ')" }

Sync-ProcessPath
if ($Mode -eq 'install') { New-Dir $ToolsDir | Out-Null; New-Dir $DownloadDir | Out-Null }

# Keep dotnet quiet and predictable during a demo.
$env:DOTNET_CLI_TELEMETRY_OPTOUT     = '1'
$env:DOTNET_NOLOGO                   = '1'
$env:DOTNET_SKIP_FIRST_TIME_EXPERIENCE = '1'

# Stop dotnet leaving MSBuild and Roslyn servers running in the background.
# They hold open handles on the build output, so deleting a mutant's work
# directory fails on Windows with "Cannot delete file". MSBuild reads its
# properties from the environment, so UseSharedCompilation works here too.
$env:MSBUILDDISABLENODEREUSE       = '1'
$env:DOTNET_CLI_USE_MSBUILD_SERVER = '0'
$env:UseSharedCompilation          = 'false'

$Resolved = [ordered]@{}

Head 'Detecting installed tools'

foreach ($tool in $Tools) {
    if (-not (Test-Selected $tool)) {
        Info "$($tool.Label): skipped."
        $Resolved[$tool.Key] = [pscustomobject]@{ Tool = $tool; Exe = $null; Version = $null; State = 'skipped' }
        continue
    }

    $found = Resolve-Tool $tool

    if ($found -and $found.Meets) {
        Ok "$($tool.Label) $($found.Version) already installed - using it as-is."
        Write-Host "       $($found.Exe)" -ForegroundColor DarkGray
    }
    elseif ($Mode -eq 'install') {
        if ($found) { Warn "$($tool.Label) $($found.Version) found but $($tool.Min)+ is needed. Installing $($tool.Min)+ alongside it." }
        else        { Info "$($tool.Label) not found. Installing ..." }

        [void](& $tool.Install)
        Sync-ProcessPath
        $found = Resolve-Tool $tool

        if ($found -and $found.Meets) { Ok "$($tool.Label) $($found.Version) installed." }
        else { Fail "$($tool.Label) still unavailable after install." }
    }
    else {
        if ($found) { Warn "$($tool.Label) $($found.Version) found, but $($tool.Min)+ is required." }
        else        { Warn "$($tool.Label) not found." }
    }

    $state = 'missing'
    if ($found -and $found.Meets) { $state = 'ok' } elseif ($found) { $state = 'old' }

    $Resolved[$tool.Key] = [pscustomobject]@{
        Tool    = $tool
        Exe     = $(if ($found) { $found.Exe } else { $null })
        Version = $(if ($found) { $found.Version } else { $null })
        State   = $state
    }

    # Maven needs a JDK, so hand it the one we just found rather than whatever
    # JAVA_HOME happened to be pointing at.
    if ($tool.Key -eq 'java' -and $state -eq 'ok') {
        $env:JAVA_HOME = Split-Path -Parent (Split-Path -Parent $Resolved['java'].Exe)
        Info "JAVA_HOME for this session: $env:JAVA_HOME"
    }
}

# ---------------------------------------------------------------------------
# demo-env.ps1 / demo-env.cmd
# ---------------------------------------------------------------------------

function Get-ToolBinDirs {
    $dirs = New-Object System.Collections.Generic.List[string]
    foreach ($key in $Resolved.Keys) {
        $r = $Resolved[$key]
        if ($r.State -ne 'ok' -or -not $r.Exe) { continue }
        $d = & $r.Tool.BinDir $r.Exe
        if ($d -and -not $dirs.Contains($d)) { [void]$dirs.Add($d) }
    }
    return $dirs
}

# The sample configs run `python3 -m pytest ...`, which is a Unix name Windows
# doesn't have. Drop a shim in .tools\bin and put that on the session PATH -
# beats hardlinking python3.exe into Program Files, and it's easy to delete.
function New-Shims {
    $py = $Resolved['python']
    if (-not $py -or $py.State -ne 'ok') { return }
    New-Dir $ShimDir | Out-Null

    $target = $py.Exe
    $venvPy = Join-Path $ToolsDir 'pyvenv\Scripts\python.exe'
    if (Test-Path -LiteralPath $venvPy) { $target = $venvPy }

    Set-Content -LiteralPath (Join-Path $ShimDir 'python3.cmd') -Encoding ASCII -Value @(
        '@echo off',
        "`"$target`" %*"
    )
    Set-Content -LiteralPath (Join-Path $ShimDir 'pytest.cmd') -Encoding ASCII -Value @(
        '@echo off',
        "`"$target`" -m pytest %*"
    )
}

function Write-EnvScripts {
    $dirs = @(Get-ToolBinDirs)
    if (Test-Path -LiteralPath $ShimDir) { $dirs = @($ShimDir) + $dirs }

    $javaHome = $null
    if ($Resolved['java'].State -eq 'ok') {
        $javaHome = Split-Path -Parent (Split-Path -Parent $Resolved['java'].Exe)
    }
    $dotnetRoot = $null
    if ($Resolved['dotnet'].State -eq 'ok') {
        $dotnetRoot = Split-Path -Parent $Resolved['dotnet'].Exe
    }

    $ps = New-Object System.Collections.Generic.List[string]
    [void]$ps.Add('# Generated by chocolatey-setup.ps1 - do not edit, re-run the script instead.')
    [void]$ps.Add('# Puts the detected toolchain on PATH for THIS shell only. Nothing global changes.')
    [void]$ps.Add('#   . .\demo-env.ps1')
    [void]$ps.Add('')
    foreach ($d in $dirs) { [void]$ps.Add(('$env:Path = "{0};" + $env:Path' -f $d)) }
    if ($javaHome)   { [void]$ps.Add(('$env:JAVA_HOME = "{0}"'   -f $javaHome)) }
    if ($dotnetRoot) { [void]$ps.Add(('$env:DOTNET_ROOT = "{0}"' -f $dotnetRoot)) }
    [void]$ps.Add('$env:DOTNET_CLI_TELEMETRY_OPTOUT = "1"')
    [void]$ps.Add('$env:DOTNET_NOLOGO = "1"')
    # Keep build servers from holding handles on files we need to delete.
    [void]$ps.Add('$env:MSBUILDDISABLENODEREUSE = "1"')
    [void]$ps.Add('$env:DOTNET_CLI_USE_MSBUILD_SERVER = "0"')
    [void]$ps.Add('$env:UseSharedCompilation = "false"')
    [void]$ps.Add('Write-Host "Mutation tester environment ready." -ForegroundColor Green')
    Set-Content -LiteralPath (Join-Path $RootDir 'demo-env.ps1') -Value $ps -Encoding UTF8

    $cmd = New-Object System.Collections.Generic.List[string]
    [void]$cmd.Add('@echo off')
    [void]$cmd.Add(':: Generated by chocolatey-setup.ps1 - do not edit, re-run the script instead.')
    foreach ($d in $dirs) { [void]$cmd.Add(('set "PATH={0};%PATH%"' -f $d)) }
    if ($javaHome)   { [void]$cmd.Add(('set "JAVA_HOME={0}"'   -f $javaHome)) }
    if ($dotnetRoot) { [void]$cmd.Add(('set "DOTNET_ROOT={0}"' -f $dotnetRoot)) }
    [void]$cmd.Add('set "DOTNET_CLI_TELEMETRY_OPTOUT=1"')
    [void]$cmd.Add('set "DOTNET_NOLOGO=1"')
    [void]$cmd.Add('set "MSBUILDDISABLENODEREUSE=1"')
    [void]$cmd.Add('set "DOTNET_CLI_USE_MSBUILD_SERVER=0"')
    [void]$cmd.Add('set "UseSharedCompilation=false"')
    [void]$cmd.Add('echo Mutation tester environment ready.')
    Set-Content -LiteralPath (Join-Path $RootDir 'demo-env.cmd') -Value $cmd -Encoding ASCII
}

# Same PATH, applied to this process, so the restore and summary below see the
# tools right away instead of needing a fresh shell.
function Enter-ToolEnv {
    $dirs = @(Get-ToolBinDirs)
    if (Test-Path -LiteralPath $ShimDir) { $dirs = @($ShimDir) + $dirs }
    foreach ($d in $dirs) {
        if (($env:Path -split ';') -notcontains $d) { $env:Path = "$d;$env:Path" }
    }
}

# ---------------------------------------------------------------------------
# Project dependencies
# ---------------------------------------------------------------------------

function Invoke-Restore {
    param([string]$Label, [string]$WorkDir, [string]$Exe, [string[]]$Argv, [int]$TimeoutSec = 1200)
    if (-not $Exe -or -not (Test-Path -LiteralPath $WorkDir)) { return }
    Info "$Label ..."
    $sw = [Diagnostics.Stopwatch]::StartNew()
    Push-Location $WorkDir
    try {
        $out = Invoke-Native $Exe $Argv -TimeoutSec $TimeoutSec
        $bad = @($out -split "`r?`n" | Where-Object { $_ -match 'BUILD FAILURE|^\[ERROR\]|error [A-Z]+\d+|npm ERR!' })
        if ($bad.Count -gt 0) {
            Warn "$Label reported problems:"
            $bad | Select-Object -First 8 | ForEach-Object { Write-Host "       $_" -ForegroundColor DarkYellow }
        } else {
            Ok "$Label done ($([int]$sw.Elapsed.TotalSeconds)s)."
        }
    } finally {
        Pop-Location
        $sw.Stop()
    }
}

function Restore-Project {
    Head 'Restoring project dependencies'

    $mvn = $Resolved['maven']
    if ($mvn.State -eq 'ok') {
        Invoke-Restore 'Maven dependencies (root)' $RootDir $mvn.Exe `
            @('-B', '--no-transfer-progress', '-DskipTests', 'dependency:go-offline') 1800
        $sample = Join-Path $RootDir 'samples\MavenProject'
        if (Test-Path -LiteralPath $sample) {
            Invoke-Restore 'Maven dependencies (samples\MavenProject)' $sample $mvn.Exe `
                @('-B', '--no-transfer-progress', '-DskipTests', 'dependency:go-offline') 1800
        }
    }

    $dn = $Resolved['dotnet']
    if ($dn.State -eq 'ok') {
        $csDir = Join-Path $RootDir 'samples\CSProject'
        $proj  = Join-Path $csDir 'tests\CSProject.Tests\CSProject.Tests.csproj'
        if (Test-Path -LiteralPath $proj) {
            Invoke-Restore 'NuGet restore (samples\CSProject)' $csDir $dn.Exe `
                @('restore', $proj, '--nologo', '--verbosity', 'minimal') 900
        }
    }

    $node = $Resolved['node']
    if ($node.State -eq 'ok') {
        $jsDir = Join-Path $RootDir 'samples\JSProject'
        $npm   = Join-Path (Split-Path -Parent $node.Exe) 'npm.cmd'
        if ((Test-Path -LiteralPath $jsDir) -and (Test-Path -LiteralPath $npm)) {
            Invoke-Restore 'npm install (samples\JSProject)' $jsDir $npm @('install', '--no-fund', '--no-audit') 1800
            if ($Minimal) {
                Info 'Skipping the Puppeteer Chrome download (--minimal).'
            } else {
                $npx = Join-Path (Split-Path -Parent $node.Exe) 'npx.cmd'
                Info 'Puppeteer Chrome is a silent ~170 MB download; allow a few minutes.'
                Invoke-Restore 'Puppeteer Chrome' $jsDir $npx @('--yes', 'puppeteer', 'browsers', 'install', 'chrome') 2400
            }
        }
    }

    $py = $Resolved['python']
    if ($py.State -eq 'ok') {
        # Own venv so we never pip install into the user's system Python.
        # The python3 shim points at this.
        $venv   = Join-Path $ToolsDir 'pyvenv'
        $venvPy = Join-Path $venv 'Scripts\python.exe'
        if (-not (Test-Path -LiteralPath $venvPy)) {
            Info 'Creating a repo-local Python virtual environment (.tools\pyvenv) ...'
            [void](Invoke-Native $py.Exe @('-m', 'venv', $venv) -TimeoutSec 300)
        }
        if (Test-Path -LiteralPath $venvPy) {
            Invoke-Restore 'pip install pytest' $RootDir $venvPy `
                @('-m', 'pip', 'install', '--quiet', '--disable-pip-version-check', 'pytest') 600
        } else {
            Warn 'Could not create the Python virtual environment; skipping pytest.'
        }
        New-Shims
    }
}

if ($Mode -eq 'install') {
    New-Shims
    Enter-ToolEnv
    Restore-Project
}

New-Shims
Enter-ToolEnv
Write-EnvScripts

# ---------------------------------------------------------------------------
# Report
# ---------------------------------------------------------------------------

Head 'Summary'

$rows = foreach ($key in $Resolved.Keys) {
    $r = $Resolved[$key]
    $status = switch ($r.State) {
        'ok'      { 'OK' }
        'old'     { 'TOO OLD' }
        'skipped' { 'skipped' }
        default   { 'MISSING' }
    }
    [pscustomobject]@{
        Tool     = $r.Tool.Label
        Status   = $status
        Version  = $(if ($r.Version) { "$($r.Version)" } else { '-' })
        Required = $(if ($r.Tool.Required) { 'yes' } else { 'no' })
        Location = $(if ($r.Exe) { $r.Exe } else { '-' })
    }
}
$rows | Format-Table -AutoSize | Out-String | Write-Host

# Something skipped on purpose via --only/--skip isn't a failure.
$blocking = @($Resolved.Keys | Where-Object {
    $Resolved[$_].Tool.Required -and $Resolved[$_].State -notin @('ok', 'skipped')
})
$degraded = @($Resolved.Keys | Where-Object { -not $Resolved[$_].Tool.Required -and $Resolved[$_].State -notin @('ok', 'skipped') })

foreach ($k in $degraded) {
    Warn "$($Resolved[$k].Tool.Label) unavailable - $($Resolved[$k].Tool.Gate) will not run. Everything else still works."
}

if ($blocking.Count -eq 0) {
    Ok 'All required tools are available.'
    Write-Host ''
    Write-Host 'Next steps:'
    Write-Host '  . .\demo-env.ps1                       # load the toolchain into this shell'
    Write-Host '  mvn package -DskipTests'
    Write-Host '  java -jar target\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples\MavenProject maven.config'
    if ($Mode -ne 'install') {
        Write-Host ''
        Info 'Check mode: nothing was installed. Only demo-env.ps1 / demo-env.cmd were written.'
    }
    exit 0
}

foreach ($k in $blocking) { Fail "$($Resolved[$k].Tool.Label) is required for $($Resolved[$k].Tool.Gate)." }
Write-Host ''
if ($Mode -ne 'install') {
    Warn 'Re-run with --install to set these up:    .\chocolatey-setup.ps1 --install'
} else {
    Warn 'Install did not resolve everything. Try:'
    Warn '  .\chocolatey-setup.ps1 --install --portable      (skip Chocolatey, unpack into .tools\)'
    Warn 'or run the same command from an elevated PowerShell.'
}
exit 1
