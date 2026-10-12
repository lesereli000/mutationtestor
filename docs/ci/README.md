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
   `EllieJin04/mutationtestor` is private, the jar download needs a token that
   can read it: a fine-grained personal access token limited to that
   repository with **Contents: read only**. Fine-grained tokens can only reach
   repositories owned by whoever creates them, so the repository owner has to
   create it.
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
