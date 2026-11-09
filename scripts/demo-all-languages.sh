#!/usr/bin/env bash
# ============================================================================
#  demo-all-languages.sh
#  Demonstrates mutation testing across all 5 supported languages
#  using a single compiled JAR.
#
#  Usage: bash scripts/demo-all-languages.sh [spec_file]
#    spec_file defaults to specs/OperatorMutations.spec
#
#  Reports are saved to: demo-output/{java,javascript,csharp,python,scheme}/
# ============================================================================

# Full path of the spec argument, taken before changing directory
# (pwd -W gives a Windows path under Git Bash, for java).
if [ -n "$1" ]; then
    SPEC_DIR="$(cd "$(dirname "$1")" 2>/dev/null && (pwd -W 2>/dev/null || pwd))" || SPEC_DIR=""
    SPEC="$SPEC_DIR/$(basename "$1")"
fi

# Run from the repository root, wherever the script is called from.
cd "$(dirname "$0")/.." || exit 1

JAR="target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar"
DEMO_DIR="demo-output"
PASS=0
FAIL=0
SPEC="${SPEC:-specs/OperatorMutations.spec}"
if [ ! -f "$SPEC" ]; then
    echo "[ERROR] Spec file not found: ${1:-$SPEC}"
    exit 1
fi

# Copies a sample config into the demo folder, pointed at $SPEC; sets CFG.
make_config() {
    CFG="$DEMO_DIR/configs/$1"
    { cat "configs/samples/$1"; echo; echo "custom_mutation_spec=$SPEC"; } > "$CFG"
}

echo
echo "============================================================================"
echo "  MUTATION TESTER - Multi-Language Demo"
echo "  Running all 5 language projects with the same JAR"
echo "  Spec: $SPEC"
echo "============================================================================"
echo

# Check JAR exists
if [ ! -f "$JAR" ]; then
    echo "[INFO] JAR not found. Building..."
    mvn package -q -DskipTests
    if [ $? -ne 0 ]; then
        echo "[ERROR] Build failed. Exiting."
        exit 1
    fi
fi

# Clean previous demo output
rm -rf "$DEMO_DIR"
mkdir -p "$DEMO_DIR/configs"

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [1/5] JAVA (Maven Project) - MSL Operators"
echo "============================================================================"
echo
make_config java-operators.config
java -jar "$JAR" samples/MavenProject "$CFG"
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/java/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [2/5] JAVASCRIPT (Jest Project) - MSL Operators"
echo "============================================================================"
echo
make_config javascript-operators.config
java -jar "$JAR" samples/JSProject "$CFG"
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/javascript/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [3/5] C# (.NET Project) -  MSL Operators"
echo "============================================================================"
echo
make_config csharp-operators.config
java -jar "$JAR" samples/CSProject "$CFG"
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/csharp/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [4/5] PYTHON (Pytest Project) -  MSL Operators"
echo "============================================================================"
echo
make_config python-operators.config
java -jar "$JAR" samples/PytestProject "$CFG"
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/python/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [5/5] RACKET -  MSL Operators"
echo "============================================================================"
echo
make_config racket-operators.config
java -jar "$JAR" samples/RacketProject "$CFG"
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/racket/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  DEMO COMPLETE"
echo "============================================================================"
echo "  Projects passed: $PASS / 5"
if [ $FAIL -gt 0 ]; then
    echo "  Projects failed: $FAIL / 5"
    echo "  NOTE: Some failures may be due to missing runtimes"
    echo "        (e.g. dotnet, racket, pytest, node/npm)"
fi
echo
echo "  All reports saved to: $DEMO_DIR/"
echo "    demo-output/java/          - mutation-report-demo.html"
echo "    demo-output/javascript/    - mutation-report-demo.html"
echo "    demo-output/csharp/        - mutation-report-demo.html"
echo "    demo-output/python/        - mutation-report-demo.html"
echo "    demo-output/racket/        - mutation-report-demo.html"
echo "============================================================================"
echo
