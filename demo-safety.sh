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
# The run happens in $demo_workdir, so the spec is given by its full path
# (cygpath gives Windows's form under Git Bash, for java).
spec="$sample/safety-demo.spec"
if command -v cygpath >/dev/null 2>&1; then spec="$(cygpath -m "$spec")"; fi
config="$demo_workdir/safety-demo.config"
{ cat "$sample/safety-demo.config"; echo; echo "custom_mutation_spec=$spec"; } > "$config"
cd "$demo_workdir"
exec java -jar "$jar" "$sample" "$config"
