#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd -- "$(dirname -- "$0")" && pwd)"
jar="$project_root/target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar"
sample="$project_root/samples/SafetyDemoProject"

if [[ ! -f "$jar" ]]; then
    echo "Build the program first: mvn -DskipTests package"
    exit 1
fi

demo_workdir="$(mktemp -d "${TMPDIR:-/tmp}/mutation-safety-demo.XXXXXX")"
echo "Demo output and report: $demo_workdir/output"
echo "Eight cases: file writing, database, network, loop, arithmetic, untested boundary, process and file deletion."
echo "The tests use temporary files and make no real network requests."
echo "Choose c to continue, s to skip, or a to abort at each prompt."
cd "$demo_workdir"
exec java -jar "$jar" "$sample" "$sample/safety-demo.config"
