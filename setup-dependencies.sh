#!/usr/bin/env bash
# Lightweight environment setup for the multi-language mutation tester demo.
#
# Usage:
#   bash setup-dependencies.sh
#   bash setup-dependencies.sh --install
#
# Default mode checks whether the required tools are available.
# --install mode tries to install missing tools with the local package manager
# and downloads project-level dependencies for the sample projects.

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
INSTALL=false

if [[ "${1:-}" == "--install" ]]; then
  INSTALL=true
elif [[ "${1:-}" == "--help" || "${1:-}" == "-h" ]]; then
  sed -n '1,14p' "$0"
  exit 0
elif [[ -n "${1:-}" ]]; then
  echo "Unknown option: $1"
  echo "Usage: bash setup-dependencies.sh [--install]"
  exit 1
fi

info() {
  echo "[INFO] $*"
}

ok() {
  echo "[OK]   $*"
}

warn() {
  echo "[WARN] $*"
}

fail() {
  echo "[MISS] $*"
}

have() {
  command -v "$1" >/dev/null 2>&1
}

package_manager() {
  if have brew; then
    echo "brew"
  elif have apt-get; then
    echo "apt"
  elif have choco; then
    echo "choco"
  else
    echo ""
  fi
}

install_with_manager() {
  local tool="$1"
  local brew_package="$2"
  local apt_package="$3"
  local choco_package="$4"
  local manager

  manager="$(package_manager)"
  if [[ -z "$manager" ]]; then
    warn "No supported package manager found for installing $tool."
    return 1
  fi

  info "Installing $tool with $manager"
  case "$manager" in
    brew)
      brew install "$brew_package"
      ;;
    apt)
      sudo apt-get update
      sudo apt-get install -y "$apt_package"
      ;;
    choco)
      choco install -y "$choco_package"
      ;;
  esac
}

require_tool() {
  local command_name="$1"
  local label="$2"
  local brew_package="$3"
  local apt_package="$4"
  local choco_package="$5"
  local install_note="$6"

  if have "$command_name"; then
    ok "$label: $($command_name --version 2>/dev/null | head -n 1)"
    return 0
  fi

  fail "$label is not installed or not on PATH."
  if [[ "$INSTALL" == true ]]; then
    install_with_manager "$label" "$brew_package" "$apt_package" "$choco_package" || warn "$install_note"
  else
    warn "$install_note"
  fi
}

download_maven_dependencies() {
  if ! have mvn; then
    warn "Skipping Maven dependency download because mvn is missing."
    return
  fi

  info "Downloading root Maven dependencies"
  (cd "$ROOT_DIR" && mvn -q -DskipTests dependency:go-offline)

  info "Downloading Java sample Maven dependencies"
  (cd "$ROOT_DIR/samples/MavenProject" && mvn -q -DskipTests dependency:go-offline)
}

download_javascript_dependencies() {
  if ! have npm; then
    warn "Skipping JavaScript dependencies because npm is missing."
    return
  fi

  info "Installing JavaScript sample dependencies"
  (cd "$ROOT_DIR/samples/JSProject" && npm install)
}

download_python_dependencies() {
  local py=""
  if have python3; then
    py="python3"
  elif have python; then
    py="python"
  else
    warn "Skipping Python dependencies because python3/python is missing."
    return
  fi

  local venv_dir="$ROOT_DIR/samples/PytestProject/.venv"

  # Remove stale venv if its interpreter is broken (e.g., cross-platform or version mismatch)
  if [[ -d "$venv_dir" ]] && ! "$venv_dir/bin/python" --version >/dev/null 2>&1; then
    info "Removing stale Python virtual environment (interpreter not found)..."
    rm -rf "$venv_dir"
  fi

  if [[ ! -d "$venv_dir" ]]; then
    info "Creating Python virtual environment..."
    "$py" -m venv "$venv_dir"
  fi

  info "Installing pytest into virtual environment..."
  "$venv_dir/bin/python" -m pip install --quiet pytest
}

download_dotnet_dependencies() {
  if ! have dotnet; then
    warn "Skipping .NET restore because dotnet is missing."
    return
  fi

  info "Restoring C# sample dependencies"
  (cd "$ROOT_DIR/samples/CSProject" && dotnet restore tests/CSProject.Tests/CSProject.Tests.csproj)
}

check_racket() {
  if have racket; then
    ok "Racket: $(racket --version 2>/dev/null | head -n 1)"
  else
    fail "Racket is not installed or not on PATH."
    warn "Install Racket from https://download.racket-lang.org/ or with your package manager."
  fi
}

echo "============================================================================"
echo "Mutation Tester Dependency Setup"
echo "============================================================================"
echo "Project root: $ROOT_DIR"
echo

require_tool java "Java/JDK 17+" openjdk openjdk-17-jdk openjdk17 "Install JDK 17 or newer."
require_tool mvn "Maven" maven maven maven "Install Maven."
require_tool dotnet ".NET SDK for net10.0 sample" dotnet-sdk dotnet-sdk-10.0 dotnet-sdk "Install a .NET SDK that supports net10.0."
require_tool python3 "Python 3" python python3 python "Install Python 3."
require_tool npm "Node.js/npm" node nodejs nodejs "Install Node.js and npm."
check_racket

if [[ "$INSTALL" == true ]]; then
  echo
  echo "============================================================================"
  echo "Downloading project dependencies"
  echo "============================================================================"
  download_maven_dependencies
  download_javascript_dependencies
  download_python_dependencies
  download_dotnet_dependencies
else
  echo
  info "Run 'bash setup-dependencies.sh --install' to install/download project dependencies."
fi

echo
echo "============================================================================"
echo "Next steps"
echo "============================================================================"
echo "1. Build the mutation tester:"
echo "   mvn package -DskipTests"
echo
echo "2. Run the multi-language demo:"
echo "   bash demo-all-languages.sh"
echo
