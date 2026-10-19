# Running MutationTester in GitHub Actions

A project can run mutation testing on every PR with one workflow file and
nothing else in its main branch. The workflow downloads a released jar, so the
tool is never built in the project's CI.

## Setting up a project

1. Copy [`mutation-workflow.yml`](mutation-workflow.yml) to
   `.github/workflows/mutation.yml` in the project.
2. Edit its **Write config** step (the same keys as a local config file, see
   the setup guide) and swap the `setup-java` step for whatever the project's
   tests need.
3. Add a repository secret `MUTATION_TOOL_TOKEN`. While
   `lesereli000/mutationtestor` is private, the jar download needs a token
   that can read it. Fine-grained tokens can only reach repositories owned by
   whoever creates them, so the repository owner creates it:
   1. Profile picture → **Settings** → **Developer settings** (bottom of the
      sidebar) → **Personal access tokens** → **Fine-grained tokens** →
      **Generate new token**.
   2. Resource owner: the tool repository's owner. Repository access: **Only
      select repositories** → `mutationtestor`. Repository permissions:
      **Contents: Read-only** (Metadata: Read-only is added automatically).
   3. In the project: **Settings** → **Secrets and variables** → **Actions** →
      **New repository secret** named `MUTATION_TOOL_TOKEN`.

   When the token expires, the jar download fails until it is replaced.
4. If the project has a branch ruleset covering all branches, allow
   `github-actions[bot]` to push to `mutation-data` (or exclude that branch).

The first run creates the `mutation-data` branch on its own.

## What a run does

- **Pass/fail.** Set `min_mutation_score` (0-100) in the config. The tool exits
  with:

  | Exit code | Meaning |
  |---|---|
  | 0 | Finished, and the score meets `min_mutation_score` (or none is set) |
  | 1 | Could not run: bad arguments or config, tests failing on the original code, or a threshold is set but there were no mutants to score |
  | 2 | Finished, but the score is below `min_mutation_score` |
  | 3 | Stopped early (`time_limit_minutes` reached, or the job was cancelled); the report is partial and the minimum is not checked |

  The score is compared as printed (one decimal place), and equivalent mutants
  are left out of it.
- **Job summary.** The score, counts and surviving mutants appear on the run's
  summary page.
- **Report.** The full HTML report is uploaded as the `mutation-report`
  artifact, even when the run fails. It works offline: unzip it and open
  `mutation-report-demo.html`. It also contains `equivalents.json`, the exact
  list of equivalent mutants that run used.
- **Bundled specs.** `custom_mutation_spec=OperatorMutations.txt` (also
  `StatementMutation.txt`, `StringMutation.txt`) loads the copy inside the jar
  when no file by that name exists, so the project doesn't need one.

## Long runs

Every mutant runs the project's test suite once, so mutation testing is slow.
In rough order of payoff:

- **Keep `test_command` lean.** Leave out `clean` (it recompiles everything
  for every mutant), and leave out tests that are slow and not worth mutating
  against, e.g. GUI tests: `mvn -q test -Dtest='!main/ui/**'` skips the
  `main.ui` package. Use the same exclusion in `coverage_command`.
- **Don't mutate code you aren't testing.** `exclude_source` (comma-separated
  folders or files, relative to the project root) keeps code out of mutation
  entirely: no mutants, and not in the score. When GUI tests are skipped,
  exclude the GUI code too, e.g. `exclude_source=src/main/java/main/ui`;
  otherwise its mutants show up as NO COVERAGE and lower the score.
- **Turn on coverage.** With `coverage_command` and `coverage_report` set,
  mutants on lines no test runs are marked NO COVERAGE without running the
  suite. If the coverage command fails, nothing is skipped and the log shows
  the command's output. For Maven:
  - If the project's `pom.xml` already uses the `jacoco-maven-plugin`:
    `coverage_command=mvn -q test jacoco:report`. Adding the plugin again on
    the command line attaches a second JaCoCo agent, and the test JVM crashes
    with `duplicate class definition for java.lang.$JaCoCo`.
  - Otherwise: `coverage_command=mvn -q org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report`.

  Either way `coverage_report=target/site/jacoco/jacoco.xml`, and use the same
  test exclusions as `test_command`.
- **Parallel workers (GitHub Actions only).** Set `MUTATION_WORKERS` (1-4,
  default 1) in the mutation job's `env:`. Each worker runs the tests in its
  own copy of the project. Results are the same as a one-at-a-time run, but
  tests that share a port, a database or files outside the project can
  collide and wrongly kill mutants; when first enabling it, compare one run
  with and one without. Outside GitHub Actions mutants are always tested one
  at a time and the variable is ignored.
- **A time limit.** `time_limit_minutes` (counted from when the tool starts)
  stops it starting new mutants; mutants already running finish, and the
  report is written for the tested ones, marked partial, with exit code 3. If
  the job is cancelled instead (Cancel button or a GitHub timeout), the tool
  makes a best-effort partial report in the few seconds GitHub allows. The
  template nests three limits so the report is always uploaded: the config's
  `time_limit_minutes: 45`, the run step's `timeout-minutes: 55`, the job's
  `timeout-minutes: 60`.

## Marking equivalent mutants

1. Download the `mutation-report` artifact and open `all-survivors.html`.
2. Click **Mark as Equivalent** on mutants that can't be killed. The page
   updates right away; nothing is saved yet.
3. Click **Save to GitHub**. GitHub opens with a small file already filled in
   on the `mutation-data` branch. Click **Commit changes**.
4. The next workflow run applies the marks. Re-run the job to apply them to
   the current PR.

To un-mark, click **✓ Equivalent** on a marked mutant and save the same way.
**History** on the page lists every change to the list.

How it's stored: the `mutation-data` branch holds one `equivalents.json`.
Each save adds a batch file under `pending/` (`{"add": [...], "remove": [...]}`),
because GitHub can only prefill a new file from a link. The workflow's
`sync-equivalents` job folds those batches into `equivalents.json` and deletes
them in one commit. Runs also apply any batches not folded yet, so a save is
never missed.

Very large saves are split into several batch files, because GitHub limits
how long a link can be; the page lists one link per file.

### Local runs

Run locally, the tool reads the `mutation-data` branch if the project has it
(whatever was last fetched; it doesn't fetch, so it never asks for
credentials). Otherwise it uses `output/report/equivalent-mutants.json`, which
the local API server writes. A locally generated report offers **Download
equivalent-mutants.json** instead of Save to GitHub.

## Releasing the tool

Pushing a `v*` tag runs [`release.yml`](../../.github/workflows/release.yml),
which tests, builds and publishes `mutationtester-<tag>.jar` on a GitHub
Release. Tags with a `-` (`v0.1.0-rc.1`) are prereleases. A tag push uses the
workflow file from the tagged commit, so a branch can be released and tried in
a test project before it is merged:

```bash
git tag v0.1.0-rc.1
git push origin v0.1.0-rc.1
```

Then point the test project's `TOOL_VERSION` at that tag.
