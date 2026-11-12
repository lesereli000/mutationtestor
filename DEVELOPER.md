# Developer guide

Last updated: 12 November 2026

This is the developer guide for people who run or change MutationTester. When you extend the tool (new mutators, languages, samples, reports, safety, coverage, tests), update this file in the same change so the handoff stays accurate.

The short user README is [`README.md`](README.md). Spec syntax in full is [`docs/spec-format.md`](docs/spec-format.md). Setup, configs, and reports are in the [setup guide](docs/setup-guide/MutationTester-Setup-Guide.md).

Commands are labeled **User** or **Developer**.

## Who this is for

This document assumes you are already familiar with mutation testing (mutants, killed, survived, mutation score). The purpose of this document is to explain how this tester is different, and how it operates: one shared spec, five implemented languages, a jar, optional safety, and an HTML report.

- **Users:** sections [1](#1-project-overview)–[3](#3-using-the-tool) and [7](#7-known-issues-and-workarounds).
- **Developers:** all of it.

For adding things to the tool (new mutators, a new language, samples, reports, safety, coverage, tests), jump to [section 5](#5-extending-the-tester-developer-guide).

## Common terms

| Term | Meaning |
| --- | --- |
| **MSL** | The shared spec files under [`specs/`](specs/). Not a second programming language that is compiled. |
| **Adapter (scanner)** | Front-end for one language. Parses source and maps local spelling onto MSL trees. |
| **Spec run** | Every run reads `custom_mutation_spec=<file>`. `mutator_type` is deprecated and ignored. |
| **Placeholder** | A typed hole in a spec, e.g. `{quantity}`, `{constructor_call}`. New placeholders: [section 5.4](#54-add-a-new-kind-of-site-new-placeholder). |
| **Workdir** | A copy of the project under `output/workdir-*`. The original tree is not edited. |

## Contents

- [Developer guide](#developer-guide)
  - [Who this is for](#who-this-is-for)
  - [Common terms](#common-terms)
  - [Contents](#contents)
  - [1. Project overview](#1-project-overview)
    - [Example](#example)
  - [2. Current status](#2-current-status)
    - [Not yet implemented](#not-yet-implemented)
  - [3. Using the tool](#3-using-the-tool)
    - [3.1 Getting started](#31-getting-started)
    - [3.2 Config files](#32-config-files)
    - [3.3 Spec files (MSL)](#33-spec-files-msl)
    - [3.4 Reports and output](#34-reports-and-output)
    - [3.5 Safety-aware mutations](#35-safety-aware-mutations)
  - [4. How it works](#4-how-it-works)
    - [4.1 Architecture](#41-architecture)
    - [4.2 Languages](#42-languages)
    - [4.3 How a spec line becomes mutants](#43-how-a-spec-line-becomes-mutants)
  - [5. Extending the tester (developer guide)](#5-extending-the-tester-developer-guide)
    - [5.0 Which path to take](#50-which-path-to-take)
    - [5.1 Add a mutation in the spec (usual path)](#51-add-a-mutation-in-the-spec-usual-path)
    - [5.2 Teach adapters a new operator or construct](#52-teach-adapters-a-new-operator-or-construct)
    - [5.3 Add a small named spec](#53-add-a-small-named-spec)
    - [5.4 Add a new kind of site (new placeholder)](#54-add-a-new-kind-of-site-new-placeholder)
    - [5.5 Add a language](#55-add-a-language)
    - [5.6 Add a sample project or config](#56-add-a-sample-project-or-config)
    - [5.7 Add or change a report field](#57-add-or-change-a-report-field)
    - [5.8 Add a safety check](#58-add-a-safety-check)
    - [5.9 Add a coverage format](#59-add-a-coverage-format)
    - [5.10 Add setup / toolchain support](#510-add-setup--toolchain-support)
    - [5.11 Tests after you add something](#511-tests-after-you-add-something)
  - [6. Development notes](#6-development-notes)
    - [Why it is built this way](#why-it-is-built-this-way)
  - [7. Known issues and workarounds](#7-known-issues-and-workarounds)
    - [Setup](#setup)
    - [Jar](#jar)
    - [Skipped mutants](#skipped-mutants)
    - [Safety](#safety)
    - [Easy to mix up](#easy-to-mix-up)
  - [See also](#see-also)

---

## 1. Project overview

This tester is designed to be universal: it works with multiple languages. One spec file describes the mutations; adapters currently translate Java, JavaScript, Python, C#, and Racket into that spec.

### Example

Spec (`specs/OperatorMutations.spec`):

```text
Replace: {quantity} && {quantity}
With: {quantity} || {quantity}
```

| Language | Source | Mutant |
| --- | --- | --- |
| Java / JS / C# | `return a > 0 && b > 0;` | `return a > 0 \|\| b > 0;` |
| Python | `return a > 0 and b > 0` | `return a > 0 or b > 0` |
| Racket | `(and (> a 0) (> b 0))` | `(or (> a 0) (> b 0))` |

Each language still needs an adapter. A new kind of construct (not just another operator swap) needs adapter support, then a spec line. Operator variations of constructs adapters already match are spec-only. Details: [`docs/spec-format.md`](docs/spec-format.md).

---

## 2. Current status

What works today:

- **Five adapters.** Mixed-language projects as separate files (e.g. [`samples/SharedPitProject`](samples/SharedPitProject)). Embedded snippets (e.g., Python inside a Java string) are not mutated.
- **Specs** under [`specs/`](specs/): `OperatorMutations.spec`, `StatementMutation.spec`, `StringMutation.spec`, `TypeMutations.spec`, `context-sensitive-mutations.spec`. `specs/pit/` is one PIT-style mutator per file. `specs/single-operator/` is O01–O25 for baseline tests. `specs/catalog/` holds pit / mutmut / stryker / classic catalogs. Adapters map `and`, `equals()`, `bitwise-and`, `Math.floorDiv`, and similar.
- **Config:** `java -jar <jar> <project> <config>`. Required: `source_code_directory`, `custom_mutation_spec`, `test_command`. Sample configs: [`configs/samples/`](configs/samples/). External-project configs: [`configs/external/`](configs/external/). Coverage optional; uncovered lines are `NO COVERAGE`.
- **Loop:** copy to workdir, baseline tests, scan from the spec, then compile/test/restore each mutant. Parallel workers (`MUTATION_WORKERS`) run only in GitHub Actions; a local jar run is always one mutant at a time. Scores `KILLED` / `SURVIVED` / `NO COVERAGE`.
- **Reports:** console plus HTML (summary, all survivors, per-file source, equivalents). Safety gate before each write (`SAFE` / `REVIEW` / `BLOCK` / `UNKNOWN`). Users run a fat jar. [`samples/`](samples/) holds demos. [`external/`](external/) holds optional git submodules for scale tests (`git submodule update --init`).

### Not yet implemented

These are known gaps, not bugs in the current tree.

- **No coverage as survived.** Uncovered mutants stay `NO COVERAGE`. They are not listed as `SURVIVED` and do not appear on the all-survivors page. The mutation score is killed / (all mutants except safety skips), so `NO COVERAGE` still lowers the score. Folding uncovered mutants into the survived count and the survivor list is not implemented.
- **Multi-file / multi-context mutations.** Each mutant is one edit in one source file. The matcher does not change several files in one mutant, and it does not use types, imports, or definitions from other files as context. Safety review is the same: one file’s syntax tree (Python and JavaScript can follow a short same-file helper name). Calls into other files stay `UNKNOWN`.
- **Continue all at safety prompts.** Interactive runs still ask `c` / `s` / `a` once per `REVIEW` / `UNKNOWN` mutant. There is no continue-all key.

---

## 3. Using the tool

### 3.1 Getting started

Users need Java 17+ and the compile/test tools their project already uses. Building the tester from source also needs Maven 3.8+. Language samples need Python 3.8+, Node 18+, .NET SDK, and/or Racket 8.x. Missing Python only drops the Python sample.

Install toolchains (Chocolatey, demo-env, Docker): [`docs/setup-guide/MutationTester-Setup-Guide.md`](docs/setup-guide/MutationTester-Setup-Guide.md). Scripts live in [`scripts/`](scripts/) (`chocolatey-setup.ps1`, `setup-dependencies.sh` / `.bat`, `demo-all-languages`).

Docker is optional. It is useful for testing the tool in a blank environment. Everyday jar and Maven work only needs Java (and `demo-env` for sample runtimes). Docker’s install is slow.

**User** — run a prebuilt jar (from the directory that contains the config, or pass a full config path):

```powershell
java -jar path\to\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar path\to\project path\to\config
Start-Process output\report\mutation-report-demo.html
```

**User** — Maven sample in this repo (needs Maven on PATH):

```powershell
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/MavenProject configs/samples/java-operators.config
```

That mutates operators under `src/main/java` using `specs/OperatorMutations.spec` and runs `mvn test`. Example console line:

```text
Testing Add1.java:6:17 (+ to -) ... KILLED.
```

**Killed** means a test failed, so the tests noticed the change. **Survived** means every test still passed. **No coverage** means no test ran that line, so the tester did not re-run tests for it.

The HTML report is `output/report/` **in the folder where you typed `java -jar`**, not automatically inside the sample project. After the Maven command above, stay in the repo root:

1. Wait for `HTML report generated`.
2. Open `output/report/mutation-report-demo.html` (score and per-file table).
3. Use **View All Survivors** or `output/report/all-survivors.html` for survived mutants. Per-file pages under `allFiles/` come after that.

If you `cd samples/SafetyDemoProject` and run that project's jar command, its report is `samples/SafetyDemoProject/output/report/`. That is a different run from the Maven sample at the repo root.

**Developer** — build the jar:

```powershell
git clone https://github.com/lesereli000/mutationtestor.git
cd mutationtestor
. .\demo-env.ps1
mvn -DskipTests package
```

Older clones may still use `github.com/EllieJin04/mutationtestor`.

Sample projects: [`samples/`](samples/). Specs: [`specs/`](specs/). Ready-made configs: [`configs/samples/`](configs/samples/). Each sample config starts with the `java -jar` command that runs it. The spec path in a config is from the directory where you launched `java -jar`. If that file is missing, the jar uses the same path bundled under `specs/` (e.g. `specs/OperatorMutations.spec`).

### 3.2 Config files

Plain text, one `key=value` per line. `#` comments and blank lines ignored.

```text
java -jar <jar> <project_directory> <config_file>
```

- `<project_directory>` is the tree to copy.
- `source_code_directory` is relative to that tree (the files to mutate).
- `custom_mutation_spec` is relative to the current working directory (where you typed `java -jar`).

Mixing those two is the usual “config not found” / “spec not found” bug.

Required: `source_code_directory`, `custom_mutation_spec`, `test_command` (must pass on the original). `compile_command` may be empty.

`mutator_type` is deprecated. If present, the tool prints a warning and still uses `custom_mutation_spec`. `mutator_type=AOR_PLUS_TO_MINUS` does not select operators.

To run the same project with different mutations, copy a sample config, change `custom_mutation_spec`, and pass the new file as the third argument. Quote the path if the name has spaces (Windows “Copy” often produces `newPython - Copy.config`; without quotes that becomes extra arguments):

```powershell
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/MavenProject configs/samples/java-operators.config
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/JavaTypeMutations configs/samples/java-types.config
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/MavenProject "C:\path\my statements.config"
```

A copied operators config with `custom_mutation_spec=specs/StatementMutation.spec` is the statements run on `samples/MavenProject`.

Example ([`configs/samples/java-operators.config`](configs/samples/java-operators.config)):

```text
source_code_directory=src/main/java
custom_mutation_spec=specs/OperatorMutations.spec
test_command=mvn clean test
coverage_command=mvn org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report
coverage_report=target/site/jacoco/jacoco.xml
```

Optional fields (see the [setup guide](docs/setup-guide/MutationTester-Setup-Guide.md)):

| Key | Meaning |
| --- | --- |
| `test_timeout_seconds` | Cap for one `test_command` run (default `180`). A hang is stopped and counted as killed. |
| `min_mutation_score` | 0–100. Exit code 2 when the score is below it (CI). |
| `exclude_source` | Comma-separated folders/files (relative to the project) that are never mutated. |
| `time_limit_minutes` | After this long, no new mutants start; the report is partial; exit code 3. |
| `unattended_safety` | `skip` (default) or `run_unknown` when there is no terminal for safety prompts. |

**Developer** — adding a Replace/With (or Delete/Insert) rule is [5.1](#51-add-a-mutation-in-the-spec-usual-path). A one-operator spec file like `specs/pit/constructor-calls.spec` is [5.3](#53-add-a-small-named-spec).

### 3.3 Spec files (MSL)

Under [`specs/`](specs/). Point `custom_mutation_spec` at one of them or at your own file. The **group heading** in the spec is the name that appears in the HTML report. `mutator_type=AOR_PLUS_TO_MINUS` does nothing; pick a spec instead.

| Spec | Names in the report |
| --- | --- |
| [`specs/OperatorMutations.spec`](specs/OperatorMutations.spec) | Math Operators, Conditionals Boundary, Negate Conditionals, Logical Operators, Logical Operand Removal, Invert Negation, Invert and Remove Increments |
| [`specs/StatementMutation.spec`](specs/StatementMutation.spec) | Statement Mutations |
| [`specs/TypeMutations.spec`](specs/TypeMutations.spec) | Type Mutations (Integer and floating point, Integer sizes, and the other groups in that file) |
| [`specs/operators-and-statements.spec`](specs/operators-and-statements.spec) | Operator groups plus Statement Mutations (`Include:`) |
| [`specs/pit/`](specs/pit/) | One PIT-style mutator per file (heading in that file) |
| [`specs/single-operator/O01.spec`](specs/single-operator/O01.spec) | Rule O01 — `{quantity} + {quantity}` → `{quantity} - {quantity}` only |

For only `+` → `-`, set `custom_mutation_spec=specs/single-operator/O01.spec`. Full matrix: [`docs/spec-format.md`](docs/spec-format.md).

A group is a heading (HTML label), rules, then `End`:

```text
Math Operators:

    Replace: {quantity} + {quantity}
    With: {quantity} - {quantity}
    And: Inverse

End
```

Three rule kinds:

| Rule | What it does |
| --- | --- |
| `Replace:` / `With:` | Rewrite the matched tree. |
| `Delete:` | Replace the match with the language’s empty statement (`;`, `pass`, `(void)`). |
| `Insert:` + `Before:` or `After:` | Write a new statement on its own line next to the anchor. |

- `And: Inverse` adds the reverse of a Replace/With.
- `Except:` lines skip a site when a tree matches an **ancestor** of the mutated node, with the mutated node in `{match}` (structural; one line covers Java/Python/Racket forms of the same guard).
- `Include:` pulls in another spec file (config still names one `custom_mutation_spec`). Example: [`specs/operators-and-statements.spec`](specs/operators-and-statements.spec).
- `Group: <name>` opens a nested group, closed by its own `End`. Report labels join names with `>` (see [`specs/TypeMutations.spec`](specs/TypeMutations.spec)).
- `{quantity}` means an expression. Other placeholders (`{invocation_statement}`, `{constructor_call}`, `{name}`, `{block}`, …) are typed holes, not Java syntax.

Write symbols as in source: `<=` and `&&`. Do not write `LE`, `LEQ`, or `and` in the spec. Adapters map those. A misspelled keyword (`Remove:` for `Delete:`) is an error, not a skipped note.

Full pattern language, placeholders, and the per-language coverage matrix: [`docs/spec-format.md`](docs/spec-format.md).

### 3.4 Reports and output

Console: counts and lists. HTML under `output/report/` **of the current working directory** (where you typed `java -jar`). Two jar runs from two folders produce two reports; opening the wrong `output/report/` is the usual mix-up.

After a run:

1. Open `mutation-report-demo.html` from that folder.
2. Then `all-survivors.html` (or **View All Survivors**) for survived mutants.
3. Then `allFiles/<file>.html` for one source file.

| File | What it shows |
| --- | --- |
| `mutation-report-demo.html` | Score, files, link to survivors |
| `all-survivors.html` | Every `SURVIVED` mutant; Back to summary; Mark as Equivalent |
| `allFiles/<file>.html` | Source with tooltips (language, type, original, replacement) |
| `equivalents.json` | Mutants marked equivalent (also used by CI on the `mutation-data` branch) |

| Status | Meaning |
| --- | --- |
| `KILLED` | Tests failed (including a test hang stopped at `test_timeout_seconds`) |
| `SURVIVED` | Tests passed |
| `NO COVERAGE` | Coverage said the line never ran, so tests were not re-run |

Compile error counts as not survived. Safety skip/abort is listed separately and left out of the score. `NO COVERAGE` is also left out of the killed and survived counts, but it stays in the score denominator (killed / (total − safety skips)), so uncovered mutants lower the score. A `time_limit_minutes` stop produces a partial report.

### 3.5 Safety-aware mutations

Before each mutant is written, the tester labels `SAFE`, `REVIEW`, `BLOCK`, or `UNKNOWN`.

| Label | Prompt |
| --- | --- |
| `SAFE` | Runs |
| `REVIEW` / `UNKNOWN` | `c` continue, `s` skip, `a` abort the run |
| `BLOCK` | `s` or `a` only |

There is no “continue all”. Each `REVIEW` / `UNKNOWN` waits for one `c`, `s`, or `a`.

No interactive console (pipes, many IDEs, CI): `REVIEW` / `BLOCK` are skipped. `UNKNOWN` is skipped unless `unattended_safety=run_unknown`. The GitHub Actions template uses `skip`.

Not a sandbox. Baseline compile/test still run first. Details: [`SAFETY.md`](SAFETY.md). Demo project: [`samples/SafetyDemoProject`](samples/SafetyDemoProject) (`demo-safety.sh`). That demo writes its own `output/report/` under `samples/SafetyDemoProject/`.

Optional model review is off by default. It needs `MUTATION_SAFETY_SEND_CODE=yes`, `MUTATION_SAFETY_API_KEY`, `MUTATION_SAFETY_MODEL`, and `MUTATION_SAFETY_API_URL` (HTTPS OpenAI Responses API for your organization’s approved provider). Unset `MUTATION_SAFETY_SEND_CODE` to disable. A missing or invalid URL stays on local review and sends no request. This sends the whole original and mutated file plus compile/test commands. Details: [`SAFETY.md`](SAFETY.md).

---

## 4. How it works

### 4.1 Architecture

ANTLR builds a parse tree. The adapter matches MSL trees against that CST and writes edits in the language. `msl/` parses the spec and emits `MutationPoint`s. `Main` copies the project, then `MutantRunner` runs the mutants (one worker, or several copies in parallel).

Adapters own spelling (`and` vs `&&`) and which CST nodes are `{quantity}`, `{statement}`, and the other placeholders. `msl/` owns the mutation (`&&` becomes `||`, `Delete:` becomes `;` / `pass` / `(void)`).

If the adapter never matches a node, there is no mutant. Quoted text `"a + b"` and assignment `x = a + b` (the `=`) are not operators; the `+` in `x = a + b` still is. A construct a language does not have simply matches nothing there.

Where the code lives (developers):

| Path | Role |
| --- | --- |
| `Main.java` | CLI, config, workdir, timeouts. No language rules here. |
| `MutantRunner.java` | Sequential or parallel execution |
| `model/` | `MutationPoint`, `MutantResult` (`Mutator.MutatorType` is leftover; runs do not use it) |
| `msl/` | Spec parser (`MutationRuleGenerator`, `MSLPatternParser`) and rules (`ReplaceRule`, `DeleteRule`, `InsertRule`) |
| `scanner/` | One adapter per language (`createAdapter`) |
| `src/main/antlr4` | Grammars |
| `safety/`, `coverage/`, `report/`, `equivalent/`, `ci/` | Supporting packages |
| `specs/`, `configs/`, `samples/`, `external/` | Specs, configs, demos, scale corpora |
| `src/test/java/mutationtester/` | Tests |
| `src/test/resources/msl-baseline/` | Recorded mutants; `MslBaselineTests` fails if one is lost |

### 4.2 Languages

| Language | Adapter | Grammar | Sample |
| --- | --- | --- | --- |
| Java | `JavaMutationScanner` | `JavaLexer.g4` / `JavaParser.g4` | [`samples/MavenProject`](samples/MavenProject) |
| JavaScript | `JavaScriptMutationScanner` | `JavaScriptLexer.g4` / `JavaScriptParser.g4` | [`samples/JSProject`](samples/JSProject) |
| Python | `PythonMutationScanner` | `PythonLexer.g4` / `PythonParser.g4` | [`samples/PytestProject`](samples/PytestProject) |
| C# | `CSharpMutationScanner` | `CSharpLexer.g4` / `CSharpParser.g4` | [`samples/CSProject`](samples/CSProject) |
| Racket | `RacketMutationScanner` | `ISL.g4` | [`samples/RacketProject`](samples/RacketProject) |

- **Python:** `f"sum={a + b}"` can mutate the `+`; `s = "a + b"` cannot. `for x in items`, `*args`, and `@app.route` are not operators.
- **Java:** `List<String>`, `for (Item x : items)`, `x instanceof String`, `T extends A & B`, and `x = 1` are syntax.
- **JavaScript:** `===` / `!==` / `??` are real MSL operators (see the spec-format operator list).
- **C#:** `string.IsNullOrEmpty(s)` is how that adapter spells `{a} is null` (null or empty).
- **Racket:** `(and ...)`, `(bitwise-and ...)`, constructor to `'()`. Macros are not mutated. Extra sample: [`samples/RacketRequiredStructs`](samples/RacketRequiredStructs).

Which constructs each adapter supports is the coverage matrix in [`docs/spec-format.md`](docs/spec-format.md) (`CoverageMatrixTests` checks it).

### 4.3 How a spec line becomes mutants

`MutationRuleGenerator` reads the spec. Every pattern is parsed into an `MSLTree` by `MSLPatternParser`. Then:

| Spec | Rule class |
| --- | --- |
| `Replace:` / `With:` | `ReplaceRule` |
| `Delete:` | `DeleteRule` |
| `Insert:` + `Before:` / `After:` | `InsertRule` |

`MSLMatcher` walks each file’s CST with the language adapter (`matchNode`, `matchesPlaceholder`). The adapter writes the edit (`replace`, `delete`, `insert`).

Unknown spec **keywords** are an error at load time. A construct or placeholder a language does not implement produces no mutant there; the run lists those unused rules per extension.

---

## 5. Extending the tester (developer guide)

Put new mutations in the spec when you can. Adapters only map spelling, parse lookalikes, and tag constructs. The same swap copied into five scanners is the expensive path this project is built to avoid.

The tests under `src/test/java/mutationtester/` show the pieces wired together: [`SpecIncludeTests`](src/test/java/mutationtester/SpecIncludeTests.java) (`Include:`), [`TypeMutationSpecTests`](src/test/java/mutationtester/TypeMutationSpecTests.java) (type rules), [`SafetyIntegrationTests`](src/test/java/mutationtester/SafetyIntegrationTests.java) (`c`/`s`/`a` and restore). Sample configs under [`configs/samples/`](configs/samples/) are the runnable counterparts.

The pattern language and coverage matrix live in [`docs/spec-format.md`](docs/spec-format.md). Placeholders added in the spec-coverage work: [`docs/added-placeholders.md`](docs/added-placeholders.md). Constructs: [`docs/added-constructs.md`](docs/added-constructs.md).

### 5.0 Which path to take

| Want to… | Cheapest place | Section |
| --- | --- | --- |
| Another operator swap (`+` to `-`, `&&` to `\|\|`, `%` to `*`) | `specs/OperatorMutations.spec` (or a private `.spec`) | [5.1](#51-add-a-mutation-in-the-spec-usual-path) |
| A symbol or construct the pattern parser does not know yet | `MSLPatternParser`, then adapter `matchNode`, then the spec | [5.2](#52-teach-adapters-a-new-operator-or-construct) |
| A small named spec (one PIT-style mutator) | New file under `specs/pit/` plus a config | [5.3](#53-add-a-small-named-spec) |
| A new kind of site (new placeholder) | `MSLPlaceholder` + adapter `matchesPlaceholder` + spec-format | [5.4](#54-add-a-new-kind-of-site-new-placeholder) |
| A sixth language | ANTLR + adapter `createAdapter` + `Main` + samples + tests | [5.5](#55-add-a-language) |
| A demo project or config | `samples/` and `configs/samples/` | [5.6](#56-add-a-sample-project-or-config) |
| A report label, type, or page | `report/` | [5.7](#57-add-or-change-a-report-field) |
| A safety call name or language assessor | `safety/` | [5.8](#58-add-a-safety-check) |
| A coverage file format | `coverage/CoverageData` | [5.9](#59-add-a-coverage-format) |
| A toolchain the demo should install | `scripts/` and the setup guide, not `Main` | [5.10](#510-add-setup--toolchain-support) |
| Which tests to run after any of the above | | [5.11](#511-tests-after-you-add-something) |

Rule of thumb: if adapters already match the node, a spec line is enough. If they do not, fix the adapter (or add the language) and update the coverage matrix. If the site is a new placeholder, that is [5.4](#54-add-a-new-kind-of-site-new-placeholder).

### 5.1 Add a mutation in the spec (usual path)

Use this for operator variations of sites adapters already match. One Replace/With (or Delete/Insert) block applies in every language that implements the construct.

Files: [`specs/OperatorMutations.spec`](specs/OperatorMutations.spec), [`specs/StatementMutation.spec`](specs/StatementMutation.spec), [`specs/StringMutation.spec`](specs/StringMutation.spec), [`specs/TypeMutations.spec`](specs/TypeMutations.spec), or [`specs/context-sensitive-mutations.spec`](specs/context-sensitive-mutations.spec). Headings become HTML labels. Write symbols as in source: `<=` and `&&`.

Example — mutate remainder to plus, under Math Operators in `specs/OperatorMutations.spec`:

```text
Replace: {quantity} % {quantity}
With: {quantity} + {quantity}
```

- Optional `And: Inverse` also generates the reverse.
- Optional `Except:` trees skip sites. `{match}` pins the mutated node:

```text
Replace: {quantity} <= {quantity}
With: {quantity} < {quantity}
Except: if ({match: {quantity} <= 0}) {quantity} = 0
Except: for ({quantity}; {match}; {quantity}) {statement}
Except: while ({match}) {statement}
And: Inverse
```

No scanner Java for this path. If the mutant is missing in one language, that adapter is not matching the node ([5.2](#52-teach-adapters-a-new-operator-or-construct) / [5.5](#55-add-a-language)).

Config:

```text
custom_mutation_spec=specs/OperatorMutations.spec
```

### 5.2 Teach adapters a new operator or construct

`MSLPatternParser` is the allow-list the spec parser knows (operators longest-first, including `===`, `!==`, `??`, `//`, `**`, compound assigns). If the factory rejects the symbol (`Unsupported MSL operator "<=>"`), add it there.

Then teach each adapter to see it:

- `matchNode`: CST children for that `MSLNode` kind (defaults cover shared expression kinds).
- `matchesPlaceholder`: which CST nodes fill `{quantity}`, `{statement}`, and the rest.
- `replace` / `delete` / `insert`: text written into the mutant; skip (no mutant) when the language has no equivalent.

Language idioms live in the adapter: Python `and` for `&&`, Racket `(not (= a b))` for `!=`, Java `Math.floorDiv` for `//`.

After matching works, write the spec as in [5.1](#51-add-a-mutation-in-the-spec-usual-path). Add a coverage-matrix row if this is a new construct (`CoverageMatrixTests`; update with `-Dmsl.matrix.update=true`). Add a quality-test case if the new spelling has lookalikes (Python `for x in items` must not become `==`).

### 5.3 Add a small named spec

Runs always use a spec file. To ship one PIT-style mutator as its own file (like constructor calls), add a `.spec` under [`specs/pit/`](specs/pit/) and a config that points at it.

Example already in the tree ([`specs/pit/constructor-calls.spec`](specs/pit/constructor-calls.spec)):

```text
Constructor Calls

    Replace: {constructor_call}
    With: null

End
```

Config:

```text
custom_mutation_spec=specs/pit/constructor-calls.spec
```

`Mutator.MutatorType` in `model/Mutator.java` is leftover from the old enum path. Do not add names there for new work. `mutator_type=` in a config is ignored.

### 5.4 Add a new kind of site (new placeholder)

Placeholders are typed holes (`MSLPlaceholder`). Unknown placeholder names (`{quantty}`) are an error when the spec loads. A placeholder a language does not implement matches nothing there.

To add `{delete_statement}` (or any new placeholder):

1. Add it to `MSLPlaceholder` and document it in [`docs/spec-format.md`](docs/spec-format.md) (and [`docs/added-placeholders.md`](docs/added-placeholders.md) if it is new coverage work).
2. Implement `matchesPlaceholder` on each language adapter that can see the construct.
3. Use it in a spec (`Replace:` / `Delete:` / `Insert:`). `Delete:` already maps to the language empty statement.
4. Parity or tree-rule tests: `StatementMutationsCrossLanguageParityTests`, `MslTreeRulesCrossLanguageTests`, `MslStatementsTests` / `MslExpressionsTests` as they fit.
5. Coverage matrix if it is a new construct (`CoverageMatrixTests`).

This is the expensive path. First recognition of a construct still needs adapter translation once; later variations of that construct can be spec-only.

### 5.5 Add a language

A new language still needs an adapter. It should reuse the files under [`specs/`](specs/). Missing `+` to `-` in the new language means the adapter is not matching the node.

Checklist (summary):

1. Grammar under `src/main/antlr4` (`antlr4-maven-plugin` generates package `mutationtester`)
2. `scanner/GoMutationScanner` (name for the language) with `createAdapter(File)` returning `MSLScannerAdapter` (`matchNode`, `matchesPlaceholder`, `replace`, `delete`, `insert`, `getNodeText`, `getTokenPosition`, `getRootContext`)
3. Two hooks in `Main.java`: `shouldScanFile` (add `.go`; skip build dirs as needed) and `findCustomMutationsInFile` (`createAdapter`)
4. `SafetyController.assessLocal` branch (today `.py` uses `PythonSyntaxSafetyAssessor`; `.java` / `.js` / `.cs` / `.rkt` use `AntlrSyntaxSafetyAssessor`)
5. `samples/GoProject` plus `configs/samples/go-operators.config`
6. Coverage matrix column and `CoverageMatrixTests`
7. Parity tests (`OperatorMutationsCrossLanguageParityTests` `LANGUAGES` array) plus `*MslMutantQualityTests` / `*ScannerTests`
8. Optional [`docs/operators/`](docs/operators/) notes and setup-script updates

Example `Main.java` custom-spec hook (copy for `.go`):

```java
} else if (fileName.endsWith(".go")) {
    adapter = GoMutationScanner.createAdapter(file);
}
```

There is no per-language `scanFile(MutatorType)` path anymore.

### 5.6 Add a sample project or config

[`samples/`](samples/) is the demo tree. Mixed-language: [`samples/SharedPitProject`](samples/SharedPitProject). Safety: [`samples/SafetyDemoProject`](samples/SafetyDemoProject). Scale corpora: [`external/`](external/) (git submodules).

Configs for samples go in [`configs/samples/`](configs/samples/), named `<language>-<spec>.config`. Each file starts with the `java -jar` command that runs it.

A new sample needs:

- Source under a small `source_code_directory`
- Tests the project’s own tool can run
- A config with `compile_command`, `test_command`, `custom_mutation_spec`

Example:

```text
# Run from the repository root:
#   java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/GoProject configs/samples/go-operators.config
source_code_directory=src
custom_mutation_spec=specs/OperatorMutations.spec
compile_command=
test_command=go test ./...
```

### 5.7 Add or change a report field

Reports live in `report/` only. `HtmlReportGenerator` writes `output/report/mutation-report-demo.html`, `all-survivors.html`, `allFiles/<file>.html`, and `equivalents.json`. Labels come from the spec group heading when present (`Math Operators: + -> -`).

Equivalent-mutant UI: `equivalent/` plus `report/EquivalentsPageData.java`. CI sync of equivalents: `ci/EquivalentsSync.java` and [`docs/ci/README.md`](docs/ci/README.md).

Tests: `PitStyleReportDetailsTests`, `EquivalentsPageDataTests`, `EquivalentMutantStoreTests`.

### 5.8 Add a safety check

`safety/` and [`SAFETY.md`](SAFETY.md). `SafetyController` labels each mutant `SAFE` / `REVIEW` / `BLOCK` / `UNKNOWN` before write.

Python files: `PythonSyntaxSafetyAssessor`. Java, JS, C#, Racket: `AntlrSyntaxSafetyAssessor`. Fallback: `ContextualSafetyAssessor`.

A new language assessor is a class plus a branch in `SafetyController.assessLocal`. Config: `unattended_safety` for CI.

### 5.9 Add a coverage format

`coverage/CoverageData.load` picks a parser from the `coverage_report` path: `.info` / `.lcov` -> LCOV; `.xml` -> JaCoCo / Cobertura; a directory -> Python trace.

A new format: add a loader in `CoverageData`, branch in `load()`, and a test in `CoverageDataTests`.

### 5.10 Add setup / toolchain support

[`scripts/chocolatey-setup.ps1`](scripts/chocolatey-setup.ps1), [`scripts/setup-dependencies.sh`](scripts/setup-dependencies.sh), [`demo-env.ps1`](demo-env.ps1) (generated locally), [`docs/setup-guide/MutationTester-Setup-Guide.md`](docs/setup-guide/MutationTester-Setup-Guide.md), [`Dockerfile`](Dockerfile). The `Dockerfile` is optional: use it to test the tool in a blank environment. The Docker install is slow.

GitHub Actions for *other* projects that use this jar: [`docs/ci/README.md`](docs/ci/README.md) and [`docs/ci/mutation-workflow.yml`](docs/ci/mutation-workflow.yml).

### 5.11 Tests after you add something

`src/test/java/mutationtester/`. On PowerShell, quote `-Dtest`. Those classes are also the worked examples for Include, type mutations, and safety (see the start of [section 5](#5-extending-the-tester-developer-guide)).

| After you change… | Command |
| --- | --- |
| Spec operator swap ([5.1](#51-add-a-mutation-in-the-spec-usual-path)) | `mvn "-Dtest=OperatorMutationsCrossLanguageParityTests" test` |
| New operator, construct, or alias ([5.2](#52-teach-adapters-a-new-operator-or-construct)) | `mvn "-Dtest=MSLTests,MslExpressionsTests,MslStatementsTests,CoverageMatrixTests,OperatorMutationsCrossLanguageParityTests,PythonMslMutantQualityTests,JavaMslMutantQualityTests" test` |
| Named PIT-style spec ([5.3](#53-add-a-small-named-spec)) | `mvn "-Dtest=CatalogSpecsTests,EndToEndMSLTests" test` |
| New placeholder ([5.4](#54-add-a-new-kind-of-site-new-placeholder)) | `mvn "-Dtest=StatementMutationsCrossLanguageParityTests,MslTreeRulesCrossLanguageTests,EndToEndMSLTests" test` |
| New language ([5.5](#55-add-a-language)) | Parity classes plus `CoverageMatrixTests` and a new `*MslMutantQualityTests` / `*ScannerTests` |
| Report ([5.7](#57-add-or-change-a-report-field)) | `mvn "-Dtest=PitStyleReportDetailsTests,EquivalentsPageDataTests" test` |
| Safety ([5.8](#58-add-a-safety-check)) | `mvn "-Dtest=SafetyGateTests,PythonSyntaxSafetyAssessorTests,AntlrSyntaxSafetyAssessorTests,OpenAiSafetyReviewerTests,SafetyIntegrationTests" test` |
| Coverage ([5.9](#59-add-a-coverage-format)) | `mvn "-Dtest=CoverageDataTests" test` |
| Time limits / parallel workers | `mvn "-Dtest=ParallelAndTimeLimitTests" test` |
| CI exit codes / bundled specs | `mvn "-Dtest=CiIntegrationTests" test` |

Recorded mutants (do not drop one without review):

```powershell
mvn "-Dtest=MslBaselineTests" test
```

Rewrite the baseline after reviewing additions: `-Dmsl.baseline.update=true`. External corpora: `-Dmsl.baseline.external=true` (needs `git submodule update --init`). Coverage matrix update: `-Dmsl.matrix.update=true`.

Then a small jar run against a sample:

```powershell
mvn -DskipTests package
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/MavenProject configs/samples/java-operators.config
```

---

## 6. Development notes

Integration branch is `main`. Do feature work on a named branch and open a PR into `main`.

```powershell
git checkout main
git pull
git checkout -b my-feature
```

Do not push `main` directly. Do not commit `temp_` files, generated `demo-env.ps1` / `demo-env.cmd`, `output/`, or `demo-output/`.

**Developer** — full test suite:

```powershell
. .\demo-env.ps1
mvn test
```

Work by mutation type, not by language. Spec files are for operator and construct variations. Adapters are for parse lookalikes and CST matching. Spec spelling is `<=` and `&&`. Adding something new: [section 5](#5-extending-the-tester-developer-guide).

### Why it is built this way

- One spec, not five PIT clones: client asked for a universal rules language.
- `<=` / `&&` in the spec: parser names (`LE`) stay in the adapter.
- Tree rules (`Replace` / `Delete` / `Insert`) so new categories are spec lines once adapters match the construct. Not a regex rewriter.
- Jar for users; Chocolatey / `demo-env` for local developer toolchains. Docker is optional (blank-environment check; slow to install).
- Safety is a judgment (`SAFE`/`REVIEW`/`BLOCK`), not a skip-list file.

---

## 7. Known issues and workarounds

### Setup

PowerShell: no `&&`.

```powershell
# Wrong
mvn test && java -jar ...

# Right: two lines, or
mvn test; java -jar ...
```

Quote Maven: `mvn "-Dtest=A,B" test` (unquoted comma is PowerShell syntax).

- `. .\demo-env.ps1` every new shell.
- `--minimal` skips Racket and Chrome.
- `--portable` uses `.tools\` without Admin.
- Docker is optional. Useful for testing the tool in a blank environment; everyday jar use is Java plus demo-env. The install is slow. Docker is not a mutant sandbox.
- Wrong Puppeteer Chrome version: install from `samples/JSProject`, not global `npx`.
- Sample configs often use `python3`. On Windows, `demo-env` or `python` may be what is on PATH. Load racket/dotnet via demo-env.

### Jar

Two arguments.

```powershell
# Wrong (missing config)
java -jar tool.jar samples/MavenProject

# Right
java -jar tool.jar samples/MavenProject configs/samples/java-operators.config
```

- Config and spec paths are from cwd. Sample configs live under `configs/samples/`, specs under `specs/`.
- `source_code_directory=.` on a huge tree is slow. Use the project’s `src` folder, or `exclude_source`.
- Baseline must pass. Default test cap is 180s (`test_timeout_seconds`). Reports overwrite `output/report/`.

### Skipped mutants

- `NO COVERAGE`: add tests or omit `coverage_command`. Maven `prototypeSample.java` is a usual example.
- Strings, comments, generics, for-in, assignment `=` are skipped on purpose when they are not the matched construct.
- Spec `LE` or `and`: use `<=` and `&&`.
- `Except:` is a tree with `{match}`, not C-style text. A comparison in a loop *body* is still mutated.
- Racket null stand-in `'()` vs `#f`: adapter, not spec.

### Safety

- No console -> skip `REVIEW`/`BLOCK`; `UNKNOWN` follows `unattended_safety` (default skip). Use a real PowerShell window for `c`/`s`/`a`.
- `BLOCK` cannot continue. Score ignores safety skips.

### Easy to mix up

- `mutator_type=AOR_PLUS_TO_MINUS` does nothing. The spec file is `custom_mutation_spec`. The report label is `Math Operators: + -> -`.
- `test_command=echo ok` means every executed mutant `SURVIVES` (smoke scan).
- `SharedPitProject` is for scanning, not a full Maven app.
- Client demo: prebuilt jar only, not `git clone && mvn package` in the room.

---

## See also

- [`README.md`](README.md) — short user README
- [`docs/spec-format.md`](docs/spec-format.md) — MSL syntax, placeholders, coverage matrix
- [`docs/setup-guide/MutationTester-Setup-Guide.md`](docs/setup-guide/MutationTester-Setup-Guide.md) — Chocolatey, demo-env, Docker, config fields
- [`docs/ci/README.md`](docs/ci/README.md) — GitHub Actions workflow for other projects
- [`SAFETY.md`](SAFETY.md) — safety labels, prompts, CI skip policy, optional approved Responses API review
- [`docs/operators/racket.md`](docs/operators/racket.md) — Racket notes (built-in enum names there are deprecated)
- [`docs/added-placeholders.md`](docs/added-placeholders.md) / [`docs/added-constructs.md`](docs/added-constructs.md) — what the spec-coverage work added
- [`specs/`](specs/), [`configs/`](configs/), [`samples/`](samples/), [`external/`](external/)
