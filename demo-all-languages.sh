#!/usr/bin/env bash
# ============================================================================
#  demo-all-languages.sh
#  Demonstrates mutation testing across all 5 supported languages
#  using a single compiled JAR.
#
#  Usage: Run from the project root directory
#         bash demo-all-languages.sh
#         (or chmod +x demo-all-languages.sh && ./demo-all-languages.sh)
#
#  Reports are saved to: demo-output/{java,javascript,csharp,python,scheme}/
# ============================================================================

JAR="target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar"
DEMO_DIR="demo-output"
PASS=0
FAIL=0

echo
echo "============================================================================"
echo "  MUTATION TESTER - Multi-Language Demo"
echo "  Running all 5 language projects with the same JAR"
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
mkdir -p "$DEMO_DIR"

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [1/5] JAVA (Maven Project) - AOR_PLUS_TO_MINUS"
echo "============================================================================"
echo
java -jar "$JAR" samples/MavenProject maven.config
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/java/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [2/5] JAVASCRIPT (Jest Project) - NLT_INTEGER_TO_EXPONENTIAL"
echo "============================================================================"
echo
java -jar "$JAR" samples/JSProject js.config
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/javascript/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [3/5] C# (.NET Project) - REMOVE_INCREMENT"
echo "============================================================================"
echo
java -jar "$JAR" samples/CSProject cs.config
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/csharp/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [4/5] PYTHON (Pytest Project) - AOR_PLUS_TO_MINUS"
echo "============================================================================"
echo
java -jar "$JAR" samples/PytestProject python.config
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/python/"
fi

# --------------------------------------------------------------------------
echo
echo "============================================================================"
echo "  [5/5] SCHEME/RACKET - TYPE_INT_TO_DOUBLE"
echo "============================================================================"
echo
java -jar "$JAR" samples/SchemeProject scheme.config
if [ $? -ne 0 ]; then FAIL=$((FAIL+1)); else PASS=$((PASS+1)); fi
if [ -d "output/report" ]; then
    cp -r output/report/. "$DEMO_DIR/scheme/"
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
echo "    demo-output/scheme/        - mutation-report-demo.html"
echo "============================================================================"
echo
