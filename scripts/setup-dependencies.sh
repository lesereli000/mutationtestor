#!/usr/bin/env bash
#
# Sets up the macOS or Linux toolchain for the mutation tester.
# On Windows, use scripts\chocolatey-setup.ps1 instead.
#
#   bash scripts/setup-dependencies.sh                        see what's installed, change nothing
#   bash scripts/setup-dependencies.sh --install              install whatever's missing
#   bash scripts/setup-dependencies.sh --install --minimal    skip Racket and Puppeteer's Chrome
#   bash scripts/setup-dependencies.sh --install --only dotnet,node
#   bash scripts/setup-dependencies.sh --install --skip racket
#   bash scripts/setup-dependencies.sh --help
#
# Tools: java, maven, dotnet, node, python, racket
#
# Notes:
#   - Existing tools are used as-is. Nothing is upgraded or reinstalled.
#   - Your shell profile is never touched. We write demo-env.sh instead; run
#     "source demo-env.sh" to set up a shell.
#   - Only the JDK and Maven are required. The rest each gate one language sample.
#   - Installs use Homebrew or apt. .NET, and Node on Linux, go in .tools/ instead,
#     as do the JDK and Maven if there's no root access. Racket needs Homebrew or apt.
#   - pytest goes in a venv in .tools/, never into your system Python.
#
# To undo: delete .tools/ and demo-env.sh.

# Written for bash 3.2, which is what macOS ships: no associative arrays or mapfile.
set -uo pipefail

MODE=check
MINIMAL=false
ONLY=""
SKIP=""

show_help() {
  # Echo the header block, stopping at the first non-comment line.
  awk 'NR > 1 && !/^#/ { exit } NR > 1 { sub(/^# ?/, ""); print }' "$0"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --install|-i) MODE=install ;;
    --check|-c)   MODE=check ;;
    --help|-h)    show_help; exit 0 ;;
    --minimal)    MINIMAL=true ;;
    --only=*)     ONLY="${1#*=}" ;;
    --skip=*)     SKIP="${1#*=}" ;;
    --only|--skip)
      if [ $# -lt 2 ]; then
        echo "$1 needs a comma-separated list of tools."
        exit 1
      fi
      if [ "$1" = "--only" ]; then ONLY="$2"; else SKIP="$2"; fi
      shift
      ;;
    *)
      echo "Unknown argument: $1"
      show_help
      exit 1
      ;;
  esac
  shift
done

# The repository root; this script is in scripts/
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS_DIR="$ROOT_DIR/.tools"
DOWNLOAD_DIR="$TOOLS_DIR/downloads"
LOG_DIR="$TOOLS_DIR/logs"
JS_DIR="$ROOT_DIR/samples/JSProject"
TOOL_KEYS="java maven dotnet node python racket"
FAILED_STEPS=0
START_PATH="$PATH"

case "$(uname -s)" in
  Darwin) OS=mac ;;
  Linux)  OS=linux ;;
  *)
    echo "This script is for macOS and Linux. On Windows, run scripts\\chocolatey-setup.ps1."
    exit 1
    ;;
esac

# ---------------------------------------------------------------------------
# Output
# ---------------------------------------------------------------------------

if [ -t 1 ]; then
  CYAN=$'\033[36m' GREEN=$'\033[32m' YELLOW=$'\033[33m' RED=$'\033[31m' PLAIN=$'\033[0m'
else
  CYAN="" GREEN="" YELLOW="" RED="" PLAIN=""
fi

info() { echo "${CYAN}[INFO]${PLAIN} $*"; }
ok()   { echo "${GREEN}[OK]${PLAIN}   $*"; }
warn() { echo "${YELLOW}[WARN]${PLAIN} $*"; }
fail() { echo "${RED}[FAIL]${PLAIN} $*"; }

head_line() {
  local rule="============================================================================"
  echo
  echo "$rule"
  echo "$1"
  echo "$rule"
}

# ---------------------------------------------------------------------------
# Small helpers
# ---------------------------------------------------------------------------

have() {
  command -v "$1" >/dev/null 2>&1
}

in_list() {
  case ",$2," in
    *",$1,"*) return 0 ;;
    *)        return 1 ;;
  esac
}

# True when $1 (major.minor) is at least $2.
version_ge() {
  local a_major a_minor b_major b_minor
  a_major="${1%%.*}"; a_minor="${1#*.}"
  b_major="${2%%.*}"; b_minor="${2#*.}"
  [ "$a_minor" = "$1" ] && a_minor=0
  [ "$b_minor" = "$2" ] && b_minor=0
  [ "$a_major" -gt "$b_major" ] && return 0
  [ "$a_major" -eq "$b_major" ] && [ "$a_minor" -ge "$b_minor" ]
}

first_version() {
  grep -Eo '[0-9]+(\.[0-9]+)?' | head -n 1
}

package_manager() {
  if have brew; then
    echo brew
  elif [ "$OS" = linux ] && have apt-get; then
    echo apt
  fi
}

run_root() {
  if [ "$(id -u)" -eq 0 ]; then
    "$@"
  else
    sudo "$@"
  fi
}

APT_UPDATED=false
apt_install() {
  if [ "$(id -u)" -ne 0 ]; then
    if ! have sudo; then
      warn "Installing $* needs root or sudo."
      return 1
    fi
    # With no terminal to type the password into, sudo would wait forever
    if [ ! -t 0 ] && ! sudo -n true 2>/dev/null; then
      warn "Installing $* needs sudo, and there's no terminal to ask for the password."
      return 1
    fi
  fi
  if [ "$APT_UPDATED" = false ]; then
    run_root apt-get update -qq || return 1
    APT_UPDATED=true
  fi
  run_root env DEBIAN_FRONTEND=noninteractive apt-get install -y -qq "$@"
}

# Fresh containers and minimal installs may have neither curl nor wget.
fetch() {
  if ! have curl && ! have wget && [ "$(package_manager)" = apt ]; then
    apt_install curl ca-certificates
  fi
  if have curl; then
    curl -fsSL "$1" -o "$2"
  elif have wget; then
    wget -q "$1" -O "$2"
  else
    warn "Downloading $1 needs curl or wget."
    return 1
  fi
}

# ---------------------------------------------------------------------------
# Finding tools
# ---------------------------------------------------------------------------

# find_<key> lists candidate executables, most preferred first. Homebrew's JDK
# and the Racket app aren't on PATH, so look where they land too.
find_java() {
  [ -n "${JAVA_HOME:-}" ] && echo "$JAVA_HOME/bin/javac"
  command -v javac
  local dir
  for dir in /opt/homebrew/opt/openjdk@17 /usr/local/opt/openjdk@17 \
             /opt/homebrew/opt/openjdk /usr/local/opt/openjdk /usr/lib/jvm/* \
             "$TOOLS_DIR"/jdk/* "$TOOLS_DIR"/jdk/*/Contents/Home; do
    echo "$dir/bin/javac"
  done
}

find_maven() {
  command -v mvn
  echo /opt/homebrew/bin/mvn
  echo /usr/local/bin/mvn
  local exe
  for exe in "$TOOLS_DIR"/maven/*/bin/mvn; do
    echo "$exe"
  done
}

find_dotnet() {
  command -v dotnet
  echo "$HOME/.dotnet/dotnet"
  echo /usr/local/share/dotnet/dotnet
  echo /usr/share/dotnet/dotnet
  echo /usr/lib/dotnet/dotnet
  echo "$TOOLS_DIR/dotnet/dotnet"
}

find_node() {
  command -v node
  echo /opt/homebrew/bin/node
  echo /usr/local/bin/node
  echo "/opt/homebrew/opt/node@$NODE_MAJOR/bin/node"
  echo "/usr/local/opt/node@$NODE_MAJOR/bin/node"
  local exe
  for exe in "$TOOLS_DIR"/node/*/bin/node; do
    echo "$exe"
  done
}

find_python() {
  command -v python3
  command -v python
  echo /opt/homebrew/bin/python3
  echo /usr/local/bin/python3
}

find_racket() {
  command -v racket
  echo /snap/bin/racket
  echo /opt/homebrew/bin/racket
  echo /usr/local/bin/racket
  local exe
  for exe in /Applications/Racket*/bin/racket; do
    echo "$exe"
  done
}

# probe_<key> prints the version of the given executable, or nothing if it
# doesn't really run. macOS has a /usr/bin/javac stub that exists but only
# complains, and a JDK is needed rather than a JRE, so java is probed by javac.
probe_java() {
  "$1" -version 2>&1 </dev/null | grep '^javac ' | first_version
}

# Maven can color this line, so the version has to be cut out of it
probe_maven() {
  "$1" -v 2>&1 </dev/null | sed -n 's/.*Apache Maven \([0-9.]*\).*/\1/p' | first_version
}

probe_dotnet() {
  "$1" --list-sdks 2>/dev/null </dev/null | awk '{ print $1 }' \
    | sort -t. -k1,1n -k2,2n | tail -n 1 | first_version
}

probe_node() {
  "$1" --version 2>/dev/null </dev/null | first_version
}

probe_python() {
  "$1" --version 2>&1 </dev/null | grep '^Python ' | first_version
}

probe_racket() {
  "$1" --version 2>/dev/null </dev/null | grep 'Racket' | first_version
}

# First candidate that runs and is new enough wins. Hang onto anything that ran
# but was too old, so the summary can say "found 18.19, need 20.0" not "missing".
resolve_tool() {
  local key="$1" min="$2" exe ver
  FOUND_EXE="" FOUND_VER="" FOUND_OK=false
  while IFS= read -r exe <&3; do
    if [ -z "$exe" ] || [ ! -x "$exe" ] || [ -d "$exe" ]; then continue; fi
    ver="$("probe_$key" "$exe")"
    [ -n "$ver" ] || continue
    if version_ge "$ver" "$min"; then
      FOUND_EXE="$exe" FOUND_VER="$ver" FOUND_OK=true
      return 0
    fi
    if [ -z "$FOUND_EXE" ]; then
      FOUND_EXE="$exe" FOUND_VER="$ver"
    fi
  done 3<<EOF
$("find_$key" 2>/dev/null)
EOF
}

# ---------------------------------------------------------------------------
# Installing tools
# ---------------------------------------------------------------------------

# Prints the manual route when there's no package manager to use.
install_package() {
  local brew_args="$1" apt_args="$2" manual="$3"
  case "$(package_manager)" in
    brew) brew install $brew_args ;;
    apt)  apt_install $apt_args ;;
    *)
      warn "No Homebrew or apt found. Install it from $manual and re-run this script."
      return 1
      ;;
  esac
}

machine_arch() {
  case "$(uname -m)" in
    x86_64)        echo x64 ;;
    aarch64|arm64) echo "$1" ;;
  esac
}

# Downloads a .tar.gz and unpacks it into .tools/<name>, replacing what's there.
unpack_into_tools() {
  local name="$1" url="$2" archive="$DOWNLOAD_DIR/$1.tar.gz"
  fetch "$url" "$archive" || return 1
  rm -rf "${TOOLS_DIR:?}/$name"
  mkdir -p "$TOOLS_DIR/$name"
  tar -xzf "$archive" -C "$TOOLS_DIR/$name"
}

# The JDK and Maven are the two required tools, so without root or a package
# manager they fall back to the official archives in .tools/.
install_java() {
  install_package "openjdk@17" "openjdk-17-jdk-headless" "https://adoptium.net/" && return 0
  local os arch
  arch="$(machine_arch aarch64)"
  if [ -z "$arch" ]; then
    warn "No Temurin JDK build for $(uname -m). Install JDK 17 from https://adoptium.net/"
    return 1
  fi
  os=linux
  [ "$OS" = mac ] && os=mac
  info "Downloading Temurin JDK 17 (~190 MB) into .tools/jdk"
  unpack_into_tools jdk "https://api.adoptium.net/v3/binary/latest/17/ga/$os/$arch/jdk/hotspot/normal/eclipse"
}

install_maven() {
  install_package "maven" "maven" "https://maven.apache.org/download.cgi" && return 0
  local ver=3.9.9
  info "Downloading Apache Maven $ver into .tools/maven"
  unpack_into_tools maven "https://archive.apache.org/dist/maven/maven-3/$ver/binaries/apache-maven-$ver-bin.tar.gz"
}

# Microsoft's installer rather than a package: distro feeds lag behind the .NET
# 10 the C# sample targets, and this needs no root and leaves other SDKs alone.
install_dotnet() {
  # .NET won't start without ICU. libicu-dev is the one name that works on
  # every release; the library itself is versioned (libicu74).
  if [ "$(package_manager)" = apt ]; then
    apt_install libicu-dev ca-certificates
  fi
  local script="$DOWNLOAD_DIR/dotnet-install.sh"
  fetch https://dot.net/v1/dotnet-install.sh "$script" || return 1
  info ".NET SDK 10.0 -> $TOOLS_DIR/dotnet (side-by-side; existing .NET installs are untouched)"
  bash "$script" --channel 10.0 --install-dir "$TOOLS_DIR/dotnet" --no-path
}

# Node 24, not the newest: on Node 26 Puppeteer's Chrome download stops
# unpacking partway and still reports success. apt's is too old (Ubuntu 24.04
# ships 18), so on Linux unpack the official build into .tools/ like the
# Windows portable install.
NODE_MAJOR=24
install_node() {
  if [ "$(package_manager)" = brew ]; then
    brew install "node@$NODE_MAJOR"
    return
  fi

  local arch ver=""
  arch="$(machine_arch arm64)"
  if [ -z "$arch" ]; then
    warn "No Node.js build for $(uname -m). Install Node 20+ from https://nodejs.org/"
    return 1
  fi

  # Releases are listed newest first, one per line
  if fetch https://nodejs.org/dist/index.json "$DOWNLOAD_DIR/node-index.json"; then
    ver="$(grep -Eo "\"version\":\"v$NODE_MAJOR\.[0-9]+\.[0-9]+\"" "$DOWNLOAD_DIR/node-index.json" \
      | head -n 1 | grep -Eo 'v[0-9.]+')"
  fi
  ver="${ver:-v24.11.0}"

  info "Downloading Node.js $ver (~30 MB) into .tools/node"
  unpack_into_tools node "https://nodejs.org/dist/$ver/node-$ver-linux-$arch.tar.gz"
}

install_python() {
  install_package "python3" "python3 python3-venv" "https://www.python.org/downloads/"
}

install_racket() {
  install_package "--cask racket" "racket" "https://download.racket-lang.org/"
}

# ---------------------------------------------------------------------------
# Tool table
# ---------------------------------------------------------------------------

tool_info() {
  case "$1" in
    java)   T_LABEL="JDK";      T_MIN=17.0; T_REQUIRED=yes; T_GATE="the mutation tester itself" ;;
    maven)  T_LABEL="Maven";    T_MIN=3.8;  T_REQUIRED=yes; T_GATE="building the mutation tester" ;;
    dotnet) T_LABEL=".NET SDK"; T_MIN=10.0; T_REQUIRED=no;  T_GATE="the C# sample (samples/CSProject targets net10.0)" ;;
    node)   T_LABEL="Node.js";  T_MIN=20.0; T_REQUIRED=no;  T_GATE="the JavaScript sample (Jest + Puppeteer)" ;;
    python) T_LABEL="Python";   T_MIN=3.9;  T_REQUIRED=no;  T_GATE="the Python sample (pytest)" ;;
    racket) T_LABEL="Racket";   T_MIN=8.0;  T_REQUIRED=no;  T_GATE="the Racket sample" ;;
  esac
}

is_selected() {
  if [ -n "$ONLY" ] && ! in_list "$1" "$ONLY"; then return 1; fi
  if in_list "$1" "$SKIP"; then return 1; fi
  if [ "$MINIMAL" = true ] && [ "$1" = racket ]; then return 1; fi
  return 0
}

record_tool() {
  printf -v "STATE_$1" '%s' "$2"
  printf -v "EXE_$1" '%s' "$3"
  printf -v "VER_$1" '%s' "$4"
}

state_of() { local name="STATE_$1"; echo "${!name}"; }
exe_of()   { local name="EXE_$1";   echo "${!name}"; }
ver_of()   { local name="VER_$1";   echo "${!name}"; }

detect_tool() {
  local key="$1"
  tool_info "$key"

  if ! is_selected "$key"; then
    info "$T_LABEL: skipped."
    record_tool "$key" skipped "" ""
    return
  fi

  resolve_tool "$key" "$T_MIN"

  if [ "$FOUND_OK" = true ]; then
    ok "$T_LABEL $FOUND_VER already installed - using it as-is."
    echo "       $FOUND_EXE"
  elif [ "$MODE" = install ]; then
    if [ -n "$FOUND_EXE" ]; then
      warn "$T_LABEL $FOUND_VER found but $T_MIN+ is needed. Installing $T_MIN+ alongside it."
    else
      info "$T_LABEL not found. Installing ..."
    fi
    "install_$key"
    hash -r
    resolve_tool "$key" "$T_MIN"
    if [ "$FOUND_OK" = true ]; then
      ok "$T_LABEL $FOUND_VER installed."
    else
      fail "$T_LABEL still unavailable after install (needed for $T_GATE)."
    fi
  elif [ -n "$FOUND_EXE" ]; then
    warn "$T_LABEL $FOUND_VER found, but $T_MIN+ is required (needed for $T_GATE)."
  else
    warn "$T_LABEL not found (needed for $T_GATE)."
  fi

  local state=missing
  if [ "$FOUND_OK" = true ]; then
    state=ok
  elif [ -n "$FOUND_EXE" ]; then
    state=old
  fi
  record_tool "$key" "$state" "$FOUND_EXE" "$FOUND_VER"

  # Maven needs a JDK, so hand it the one we just found rather than whatever
  # JAVA_HOME happened to be pointing at.
  if [ "$key" = java ] && [ "$state" = ok ]; then
    local home
    home="$("$(dirname "$FOUND_EXE")/java" -XshowSettings:properties -version 2>&1 </dev/null \
      | sed -n 's/^ *java\.home = //p')"
    if [ -n "$home" ]; then
      export JAVA_HOME="$home"
      info "JAVA_HOME for this session: $JAVA_HOME"
    fi
  fi
}

# ---------------------------------------------------------------------------
# demo-env.sh
# ---------------------------------------------------------------------------

# The sample configs run `python3 -m pytest` and `pytest`, which only find
# pytest through the venv, so its bin/ goes first. Other tools are only added
# when they aren't on PATH already, so /usr/bin doesn't jump the queue. Anything
# in .tools/ is always added, in case this shell has already loaded demo-env.sh.
tool_path() {
  local dirs="" key dir
  if [ -x "$TOOLS_DIR/pyvenv/bin/python" ]; then
    dirs="$TOOLS_DIR/pyvenv/bin"
  fi
  for key in $TOOL_KEYS; do
    [ "$(state_of "$key")" = ok ] || continue
    dir="$(dirname "$(exe_of "$key")")"
    case ":$dirs:" in *":$dir:"*) continue ;; esac
    # Compared with the PATH we started with, since install mode adds these to ours
    case "$dir" in
      "$TOOLS_DIR"/*) ;;
      *) case ":$START_PATH:" in *":$dir:"*) continue ;; esac ;;
    esac
    dirs="${dirs:+$dirs:}$dir"
  done
  echo "$dirs"
}

write_env_script() {
  local dirs
  dirs="$(tool_path)"
  {
    echo '# Generated by scripts/setup-dependencies.sh - do not edit, re-run the script instead.'
    echo '# Puts the detected toolchain on PATH for THIS shell only. Nothing global changes.'
    echo '#   source demo-env.sh'
    echo
    [ -n "$dirs" ] && echo "export PATH=\"$dirs:\$PATH\""
    if [ "$(state_of java)" = ok ] && [ -n "${JAVA_HOME:-}" ]; then
      echo "export JAVA_HOME=\"$JAVA_HOME\""
    fi
    if [ "$(state_of dotnet)" = ok ]; then
      echo "export DOTNET_ROOT=\"$(dirname "$(exe_of dotnet)")\""
    fi
    echo 'export DOTNET_CLI_TELEMETRY_OPTOUT=1'
    echo 'export DOTNET_NOLOGO=1'
    echo 'export MSBUILDDISABLENODEREUSE=1'
    echo 'export DOTNET_CLI_USE_MSBUILD_SERVER=0'
    echo 'export UseSharedCompilation=false'
    echo 'echo "Mutation tester environment ready."'
  } > "$ROOT_DIR/demo-env.sh"
}

# ---------------------------------------------------------------------------
# Project dependencies
# ---------------------------------------------------------------------------

# Runs a command in a folder and judges it by its exit code. The output goes to
# a log, and the end of that log is shown if the step fails.
run_step() {
  local label="$1" dir="$2" log start
  shift 2
  [ -d "$dir" ] || return 0
  log="$LOG_DIR/$(echo "$label" | tr -c 'A-Za-z0-9\n' '-').log"
  start=$SECONDS
  info "$label ..."
  if (cd "$dir" && "$@") >"$log" 2>&1 </dev/null; then
    ok "$label done ($((SECONDS - start))s)."
    return 0
  fi
  fail "$label failed. The end of $log:"
  tail -n 8 "$log" | sed 's/^/       /'
  FAILED_STEPS=$((FAILED_STEPS + 1))
  return 1
}

# Ask the sample's own puppeteer where its pinned Chrome lives, so a download
# that failed quietly is caught here instead of as a JS test failure later.
CHROME_PATH_JS='try{const p=require("puppeteer").executablePath();console.log((require("fs").existsSync(p)?"OK ":"MISSING ")+p)}catch(e){console.log("MISSING "+e.message.split("\n")[0])}'
CHROME_START_JS='require("puppeteer").launch({headless:true,args:["--no-sandbox","--disable-setuid-sandbox"]}).then(b=>b.close()).then(()=>console.log("STARTED")).catch(e=>console.log(e.message.split("\n")[0]))'

chrome_status() {
  local out
  out="$(cd "$JS_DIR" && "$(exe_of node)" -e "$CHROME_PATH_JS" 2>/dev/null </dev/null | grep -E '^(OK|MISSING) ' | head -n 1)"
  echo "${out:-MISSING could not load puppeteer from samples/JSProject/node_modules}"
}

chrome_starts() {
  (cd "$JS_DIR" && "$(exe_of node)" -e "$CHROME_START_JS" 2>/dev/null </dev/null) | grep -q '^STARTED$'
}

# Same libraries Chrome needs on Debian and Ubuntu. Only libasound changed its
# name outright in the 64-bit time_t move; apt maps the other old names.
install_chrome_libraries() {
  local asound=libasound2
  if apt-cache show libasound2t64 >/dev/null 2>&1; then
    asound=libasound2t64
  fi
  apt_install ca-certificates fonts-liberation "$asound" libatk-bridge2.0-0 libatk1.0-0 \
    libc6 libcairo2 libcups2 libdbus-1-3 libexpat1 libfontconfig1 libgbm1 \
    libgcc-s1 libglib2.0-0 libgtk-3-0 libnspr4 libnss3 libpango-1.0-0 \
    libpangocairo-1.0-0 libstdc++6 libx11-6 libx11-xcb1 libxcb1 libxcomposite1 \
    libxcursor1 libxdamage1 libxext6 libxfixes3 libxi6 libxrandr2 libxrender1 \
    libxss1 libxtst6
}

ensure_chrome() {
  local status npx
  npx="$(dirname "$(exe_of node)")/npx"
  status="$(chrome_status)"

  if [ "${status%% *}" = OK ]; then
    ok "Puppeteer Chrome already installed (${status#* })."
  else
    # An interrupted download leaves the version folder without its executable,
    # and Puppeteer then refuses to install over it, so clear that one folder.
    local partial
    partial="$(echo "${status#* }" | sed -n 's#^\(.*/chrome/[^/]*-[0-9][0-9.]*\)/.*#\1#p')"
    if [ -n "$partial" ] && [ -d "$partial" ]; then
      info "Removing a partial Chrome download ($partial)."
      rm -rf "$partial"
    fi
    info "Puppeteer Chrome is a silent ~170 MB download; allow a few minutes."
    run_step "Puppeteer Chrome" "$JS_DIR" "$npx" --yes puppeteer browsers install chrome
    status="$(chrome_status)"
    if [ "${status%% *}" = OK ]; then
      ok "Puppeteer Chrome verified (${status#* })."
    else
      warn "Puppeteer Chrome is still missing: ${status#* }"
      warn "The JS browser test (testRunCommandUnmutatedJSProject) will be skipped until it is installed."
      if ! version_ge "$NODE_MAJOR.99" "$(ver_of node)"; then
        warn "Node $(ver_of node) can't unpack it; Node $NODE_MAJOR can. Install Node $NODE_MAJOR and re-run."
      else
        warn "Retry by hand:  cd samples/JSProject && npx puppeteer browsers install chrome"
      fi
      FAILED_STEPS=$((FAILED_STEPS + 1))
      return
    fi
  fi

  # Being on disk isn't enough on Linux: Chrome also needs the desktop libraries
  # a server or container won't have, and it fails at startup without them.
  [ "$OS" = linux ] || return 0
  chrome_starts && return 0

  if [ "$(package_manager)" = apt ]; then
    info "Chrome won't start yet. Installing the Linux libraries it needs."
    install_chrome_libraries
  fi
  if chrome_starts; then
    ok "Puppeteer Chrome starts."
  else
    warn "Puppeteer Chrome is installed but won't start. Its missing libraries show with:"
    warn "  ldd \"${status#* }\" | grep 'not found'"
    FAILED_STEPS=$((FAILED_STEPS + 1))
  fi
}

# Own venv so we never pip install into the user's system Python. Its bin/ has
# the python3 and pytest the sample configs call.
setup_python() {
  local venv="$TOOLS_DIR/pyvenv" py
  py="$(exe_of python)"

  if [ -d "$venv" ] && { ! "$venv/bin/python" --version >/dev/null 2>&1 || [ ! -x "$venv/bin/pip" ]; }; then
    info "Removing a broken virtual environment (.tools/pyvenv)."
    rm -rf "$venv"
  fi
  if [ ! -d "$venv" ] && ! "$py" -m venv "$venv" >/dev/null 2>&1; then
    rm -rf "$venv"
    # Debian and Ubuntu's python3 can't make a venv until python3-venv is in.
    # Without root for that, make one without pip and bootstrap pip into it.
    if [ "$(package_manager)" = apt ] && apt_install python3-venv; then
      run_step "Python virtual environment (.tools/pyvenv)" "$ROOT_DIR" "$py" -m venv "$venv" || return
    else
      run_step "Python virtual environment (.tools/pyvenv)" "$ROOT_DIR" "$py" -m venv --without-pip "$venv" || return
      if ! fetch https://bootstrap.pypa.io/get-pip.py "$DOWNLOAD_DIR/get-pip.py"; then
        fail "Could not download get-pip.py to put pip in .tools/pyvenv."
        FAILED_STEPS=$((FAILED_STEPS + 1))
        return
      fi
      run_step "Bootstrap pip (get-pip.py)" "$ROOT_DIR" "$venv/bin/python" "$DOWNLOAD_DIR/get-pip.py" --quiet || return
    fi
  fi

  run_step "pip install pytest" "$ROOT_DIR" "$venv/bin/python" -m pip install --quiet \
    --disable-pip-version-check pytest
}

restore_project() {
  head_line "Restoring project dependencies"
  mkdir -p "$LOG_DIR"

  if [ "$(state_of maven)" = ok ]; then
    local mvn
    mvn="$(exe_of maven)"
    run_step "Maven dependencies (root)" "$ROOT_DIR" \
      "$mvn" -B --no-transfer-progress -DskipTests dependency:go-offline
    run_step "Maven dependencies (samples/MavenProject)" "$ROOT_DIR/samples/MavenProject" \
      "$mvn" -B --no-transfer-progress -DskipTests dependency:go-offline
  fi

  if [ "$(state_of dotnet)" = ok ]; then
    run_step "NuGet restore (samples/CSProject)" "$ROOT_DIR/samples/CSProject" \
      "$(exe_of dotnet)" restore tests/CSProject.Tests/CSProject.Tests.csproj --nologo --verbosity minimal
  fi

  if [ "$(state_of node)" = ok ]; then
    run_step "npm install (samples/JSProject)" "$JS_DIR" \
      "$(dirname "$(exe_of node)")/npm" install --no-fund --no-audit
    if [ "$MINIMAL" = true ]; then
      info "Skipping the Puppeteer Chrome download (--minimal)."
    else
      ensure_chrome
    fi
  fi

  if [ "$(state_of python)" = ok ]; then
    setup_python
  fi
}

show_chrome_status() {
  [ "$(state_of node)" = ok ] && [ "$MINIMAL" = false ] || return 0
  if [ ! -d "$JS_DIR/node_modules/puppeteer" ]; then
    warn "Puppeteer isn't installed yet; --install fetches it and its Chrome."
    return
  fi
  local status
  status="$(chrome_status)"
  if [ "${status%% *}" = OK ]; then
    ok "Puppeteer Chrome found (${status#* })."
  else
    warn "Puppeteer Chrome is missing: ${status#* }"
    warn "The JS browser test (testRunCommandUnmutatedJSProject) will be skipped until it is installed."
  fi
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

head_line "Mutation Tester - macOS/Linux environment setup"
echo "Mode          : $MODE$([ "$MINIMAL" = true ] && echo ' (minimal)')"
echo "Project root  : $ROOT_DIR"
echo "Package tool  : $(package_manager | grep . || echo 'none found')"
[ -z "$ONLY" ] || echo "Only          : $ONLY"
[ -z "$SKIP" ] || echo "Skipping      : $SKIP"

if [ "$MODE" = install ]; then
  mkdir -p "$DOWNLOAD_DIR"
fi

# Keep dotnet quiet and predictable during a demo, and stop it leaving MSBuild
# and Roslyn servers running in the background. MSBuild reads its properties
# from the environment, so UseSharedCompilation works here too.
export DOTNET_CLI_TELEMETRY_OPTOUT=1 DOTNET_NOLOGO=1 DOTNET_SKIP_FIRST_TIME_EXPERIENCE=1
export MSBUILDDISABLENODEREUSE=1 DOTNET_CLI_USE_MSBUILD_SERVER=0 UseSharedCompilation=false

head_line "Detecting installed tools"

for key in $TOOL_KEYS; do
  detect_tool "$key"
done

if [ "$MODE" = check ]; then
  show_chrome_status
else
  write_env_script
  # npm and npx find node through PATH, so this process needs the tools too
  TOOL_PATH="$(tool_path)"
  export PATH="${TOOL_PATH:+$TOOL_PATH:}$PATH"
  restore_project
  write_env_script
fi

head_line "Summary"
printf '%-9s %-8s %-8s %-9s %s\n' Tool Status Version Required Location
printf '%-9s %-8s %-8s %-9s %s\n' ---- ------ ------- -------- --------
MISSING_REQUIRED=0
for key in $TOOL_KEYS; do
  tool_info "$key"
  state="$(state_of "$key")"
  printf '%-9s %-8s %-8s %-9s %s\n' "$T_LABEL" "$state" "$(ver_of "$key" | grep . || echo -)" \
    "$T_REQUIRED" "$(exe_of "$key" | grep . || echo -)"
  if [ "$T_REQUIRED" = yes ] && [ "$state" != ok ] && [ "$state" != skipped ]; then
    MISSING_REQUIRED=$((MISSING_REQUIRED + 1))
  fi
done
echo

if [ "$MISSING_REQUIRED" -gt 0 ]; then
  fail "$MISSING_REQUIRED required tool(s) are missing."
  [ "$MODE" = install ] || info "Run 'bash scripts/setup-dependencies.sh --install' to install them."
  exit 1
fi

if [ "$FAILED_STEPS" -gt 0 ]; then
  fail "$FAILED_STEPS setup step(s) failed; see above."
  exit 1
fi

ok "All required tools are available."
echo
echo "Next steps:"
if [ "$MODE" = check ]; then
  echo "  bash scripts/setup-dependencies.sh --install   # install anything missing and fetch dependencies"
fi
echo "  source demo-env.sh                             # load the toolchain into this shell"
echo "  mvn package -DskipTests"
echo "  bash scripts/demo-all-languages.sh"
