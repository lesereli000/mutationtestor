# Developer guide

This is the working README for people who run or change MutationTester. It is a live document while the tester is still wrapping up.

Commands are labeled **User** or **Developer**.

## Who this is for

This document assumes you already are familiar with mutation testing (mutants, killed, survived, mutation score). The purpose of this document is to explain how this tester is different, and how it operates: one shared spec, five implemented languages, a jar, optional safety, and an HTML report.

- **Users:** sections [1](#1-project-overview)–[3](#3-using-the-tool) and [7](#7-known-issues-and-workarounds).
- **Developers:** all of it.

For adding things to the tool (new mutators, a new language, samples, reports, safety, coverage, tests), jump to [section 5](#5-extending-the-tester-developer-guide).

## Common terms

| Term | Meaning |
| --- | --- |
| **MSL** | The shared spec files. Not a second programming language that is compiled. |
| **Adapter (scanner)** | Front-end for one language. Parses source and maps local spelling onto MSL. |
| **Built-in type** | `mutator_type=AOR_PLUS_TO_MINUS` (Java enum). |
| **CUSTOM** | `mutator_type=CUSTOM` plus a spec file. |
| **Placeholder** | A site kind in a spec, e.g. `{quantity}`, `{constructor_call}`. New placeholders: [section 5.4](#54-add-a-new-kind-of-site-new-placeholder). |
| **Workdir** | A copy of the project under `output/workdir-*`. The original tree is not edited. |

## Contents

1. [Project overview](#1-project-overview)
2. [Current status](#2-current-status)
3. [Using the tool](#3-using-the-tool)
   - [3.1 Getting started](#31-getting-started)
   - [3.2 Config files](#32-config-files)
   - [3.3 Spec files (MSL)](#33-spec-files-msl)
   - [3.4 Reports and output](#34-reports-and-output)
   - [3.5 Safety-aware mutations](#35-safety-aware-mutations)
4. [How it works](#4-how-it-works)
   - [4.1 Architecture](#41-architecture)
   - [4.2 Languages](#42-languages)
   - [4.3 How a spec line becomes mutants](#43-how-a-spec-line-becomes-mutants)
5. [Extending the tester](#5-extending-the-tester-developer-guide)
6. [Development notes](#6-development-notes)
7. [Known issues and workarounds](#7-known-issues-and-workarounds)

---

## 1. Project overview

This tester is designed to be universal, in other words work with multiple languages. One spec file describes the mutations; adapters currently translate Java, JavaScript, Python, C#, and Racket into that spec.

### Example

Spec:

```text
Replace {quantity} && {quantity}
With {quantity} || {quantity}
```

| Language | Source | Mutant |
| --- | --- | --- |
| Java / JS / C# | `return a > 0 && b > 0;` | `return a > 0 \|\| b > 0;` |
| Python | `return a > 0 and b > 0` | `return a > 0 or b > 0` |
| Racket | `(and (> a 0) (> b 0))` | `(or (> a 0) (> b 0))` |

Each language still needs an adapter. A new kind of site (not just another operator swap) may still need shared-engine work. Operator variations of site adapters already emitted are spec-only.

---

## 2. Current status

What works today:

- **Five adapters.** Mixed-language projects as separate files (e.g. [`samples/SharedPitProject`](samples/SharedPitProject)). Embedded snippets (e.g., Python inside a Java string) are not mutated.
- **Specs:** [`OperatorMutations.txt`](OperatorMutations.txt), [`StatementMutation.txt`](StatementMutation.txt), [`StringMutation.txt`](StringMutation.txt). Adapters map `and`, `equals()`, `bitwise-and`, `Math.floorDiv`, and similar.
- **Config:** `java -jar <project> <config>`. `mutator_type` is `ALL`, enum names, or `CUSTOM` plus a spec. `compile_command` and `test_command` are the project’s own tools. Coverage optional; uncovered lines are `NO COVERAGE`.
- **Loop:** copy to workdir, baseline tests, one mutant, compile, test, restore. Scores `KILLED` / `SURVIVED` / `NO COVERAGE`.
- **Reports:** console plus HTML (summary, all survivors, per-file source). Safety gate before each write (`SAFE` / `REVIEW` / `BLOCK` / `UNKNOWN`). Users run a fat jar. [`samples/`](samples/) holds demos.

---

## 3. Using the tool

### 3.1 Getting started

Users need Java 17+ and the compile/test tools their project already uses. Building the tester from source also needs Maven 3.8+. Language samples need Python 3.8+, Node 18+, .NET SDK, and/or Racket 8.x. Missing Python only drops the Python sample.

Install toolchains (Chocolatey, demo-env, Docker): [`demo/MutationTester-Setup-Guide.md`](demo/MutationTester-Setup-Guide.md).

Docker is optional. It is useful for testing the tool in a blank environment. Everyday jar and Maven work only needs Java (and `demo-env` for sample runtimes). Docker’s install is slow.

**User** — run a prebuilt jar (from the directory that contains the config, or pass a full config path):

```powershell
java -jar path\to\MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar path\to\project path\to\config
Start-Process output\report\mutation-report-demo.html
```

**User** — Maven sample in this repo (needs Maven on PATH):

```powershell
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/MavenProject maven.config
```

That mutates `+` to `-` under `src/main/java` and runs `mvn test`. Example console line:

```text
Testing Add1.java:6:17 (+ to -) ... KILLED.
```

**Developer** — build the jar:

```powershell
git clone https://github.com/lesereli000/mutationtestor.git
cd mutationtestor
. .\demo-env.ps1
mvn -DskipTests package
```

Older clones may still use `github.com/EllieJin04/mutationtestor`.

Sample projects: [`samples/`](samples/). Specs at repo root: [`OperatorMutations.txt`](OperatorMutations.txt), [`StatementMutation.txt`](StatementMutation.txt), [`StringMutation.txt`](StringMutation.txt). The spec path in a config is from the directory where you launched `java -jar`.

### 3.2 Config files

Plain text, one `key=value` per line. `#` comments and blank lines ignored.

```text
java -jar <jar> <project_directory> <config_file>
```

- `<project_directory>` is the tree to copy.
- `source_code_directory` is relative to that tree (the files to mutate).
- `custom_mutation_spec` is relative to the current working directory (where you typed `java -jar`).

Mixing those two is the usual “config not found” / “spec not found” bug.

Required: `source_code_directory`, `mutator_type`, `test_command` (must pass on the original). `compile_command` may be empty.

`mutator_type`:

| Value | Meaning |
| --- | --- |
| `ALL` | Every built-in enum in `Mutator.java` |
| One name | e.g. `AOR_PLUS_TO_MINUS` or `CONSTRUCTOR_CALLS` |
| Comma-separated | e.g. `CONSTRUCTOR_CALLS,NON_VOID_METHOD_CALLS` |
| `CUSTOM` | Read `custom_mutation_spec` instead |

**Developer** — adding a new name to that list is [5.3](#53-add-a-built-in-mutator_type-name). Adding a Replace/With rule the `CUSTOM` path will pick up is [5.1](#51-add-a-mutation-in-the-spec-usual-path).

### 3.3 Spec files (MSL)

Repo root. [`OperatorMutations.txt`](OperatorMutations.txt), [`StatementMutation.txt`](StatementMutation.txt), [`StringMutation.txt`](StringMutation.txt). Point `custom_mutation_spec` at one of them or at your own file.

A rule is a heading (HTML label), then:

```text
Replace: {quantity} && {quantity}
With: {quantity} || {quantity}
```

- `And: Inverse` adds the reverse.
- `Except:` lines skip sites whose source text matches (C-style `for`/`while` guards are the usual case).
- `{quantity}` means an operand.
- Other placeholders (`{invocation_statement}`, `{constructor_call}`) are statement-shaped sites, not Java syntax.

Write symbols as in source: `<=` and `&&`. Do not write `LE`, `LEQ`, or `and` in the spec. Adapters map those.

### 3.4 Reports and output

Console: counts and lists. HTML under `output/report/` (current working directory).

| File | What it shows |
| --- | --- |
| `mutation-report-demo.html` | Score, files, link to survivors |
| `all-survivors.html` | Every `SURVIVED` mutant; Back to summary; Mark as Equivalent |
| `allFiles/<file>.html` | Source with tooltips (language, type, original, replacement) |

| Status | Meaning |
| --- | --- |
| `KILLED` | Tests failed |
| `SURVIVED` | Tests passed |
| `NO COVERAGE` | Coverage said the line never ran, so tests were not re-run |

Compile error counts as not survived. Safety skip/abort is listed separately and left out of the score.

### 3.5 Safety-aware mutations

Before each mutant is written, the tester labels `SAFE`, `REVIEW`, `BLOCK`, or `UNKNOWN`.

| Label | Prompt |
| --- | --- |
| `SAFE` | Runs |
| `REVIEW` / `UNKNOWN` | `c` continue, `s` skip, `a` abort the run |
| `BLOCK` | `s` or `a` only |

No interactive console (pipes, many IDEs): `REVIEW` / `UNKNOWN` / `BLOCK` are skipped.

Not a sandbox. Baseline compile/test still run first. Details: [`SAFETY.md`](SAFETY.md).

OpenAI review is off unless you set `MUTATION_SAFETY_SEND_CODE=yes` (sends the whole file). Unset to disable.

---

## 4. How it works

### 4.1 Architecture

ANTLR builds a parse tree. The adapter turns operators and some statements into shared nodes. `msl/` matches the spec and emits `MutationPoint`s. `Main` runs them one at a time.

Adapters own spelling (`and` vs `&&`). `msl/` owns the mutation (`&&` becomes `||`). Binary operators already follow that split. Statement deletion, constructors, returns, and conditions still have dedicated Java rule classes ([4.3](#43-how-a-spec-line-becomes-mutants)).

If the adapter never emits a node, there is no mutant. Quoted text `"a + b"` and assignment `x = a + b` (the `=`) are not operators; the `+` in `x = a + b` still is.

Where the code lives (developers):

| Path | Role |
| --- | --- |
| `Main.java` | Loop, workdir, timeouts. No language rules here. |
| `model/` | Mutator enums, `MutationPoint`, `MutantResult` |
| `msl/` | Spec parser and rules |
| `scanner/` | One adapter per language |
| `src/main/antlr4` | Grammars |
| `safety/`, `coverage/`, `report/`, `equivalent/` | Supporting packages |
| `samples/`, `src/test/java/mutationtester/` | Demos and tests |

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
- **JavaScript:** `if (a === b)` is treated like `==`.
- **C#:** `string.IsNullOrEmpty(s)` is the spec spelling; Java uses a null-or-empty stand-in.
- **Racket:** `(and ...)`, `(bitwise-and ...)`, constructor to `'()`. Macros and some quoted lists are still rough.

### 4.3 How a spec line becomes mutants

`MutationRuleGenerator` reads Replace/With and picks a rule class:

| Spec pattern | Rule class |
| --- | --- |
| `{quantity} + {quantity}` (any binary op the factory knows) | `ReplacementRule` (the shared path) |
| `{invocation_statement}` With: `;` | `StatementRemovalRule` (remove a discarded call) |
| `{constructor_call}`, `{return_value}`, `{non_void_method_call}`, `{equality_condition}`, `{order_condition}` | `ScannerBackedRule`, which calls the same native scan as `mutator_type=CONSTRUCTOR_CALLS` (and friends) |
| `string.IsNullOrEmpty({quantity})` | `StringCallReplacementRule` |

Unknown spec lines are skipped. A new operator swap is a spec edit ([5.1](#51-add-a-mutation-in-the-spec-usual-path)). A new placeholder currently needs a new `createRule` branch ([5.4](#54-add-a-new-kind-of-site-new-placeholder)).

---

## 5. Extending the tester (developer guide)

Put new mutations in the spec when you can. Adapters only map spelling and parse lookalikes. The same swap copied into five scanners is the expensive path this project is built to avoid.

### 5.0 Which path to take

| Want to… | Cheapest place | Section |
| --- | --- | --- |
| Another operator swap (`+` to `-`, `&&` to `\|\|`, `%` to `*`) | `OperatorMutations.txt` (or a private `.spec`) | [5.1](#51-add-a-mutation-in-the-spec-usual-path) |
| A binary symbol the factory does not know yet | `CANONICAL_OPERATORS`, then aliases, then the spec | [5.2](#52-teach-adapters-a-new-operator-spelling) |
| A name you can put in `mutator_type=` without a spec | `Mutator.MutatorType` plus each language’s `scanFile` | [5.3](#53-add-a-built-in-mutator_type-name) |
| A new kind of site (`{delete_statement}`, a new placeholder) | `MutationRuleGenerator.createRule` plus adapter methods | [5.4](#54-add-a-new-kind-of-site-new-placeholder) |
| A sixth language | ANTLR + adapter + `Main` + samples + tests | [5.5](#55-add-a-language) |
| A demo project or config | `samples/` | [5.6](#56-add-a-sample-project-or-config) |
| A report label, type, or page | `report/` | [5.7](#57-add-or-change-a-report-field) |
| A safety call name or language assessor | `safety/` | [5.8](#58-add-a-safety-check) |
| A coverage file format | `coverage/CoverageData` | [5.9](#59-add-a-coverage-format) |
| A toolchain the demo should install | setup scripts, not `Main` | [5.10](#510-add-setup--toolchain-support) |
| Which tests to run after any of the above | | [5.11](#511-tests-after-you-add-something) |

Rule of thumb: if adapters already emit the node, a spec line is enough. If they do not, fix the adapter (or add the language). If the site is a new category, that is [5.4](#54-add-a-new-kind-of-site-new-placeholder).

### 5.1 Add a mutation in the spec (usual path)

Use this for operator variations of sites adapters already emit. One Replace/With block applies in Java, JavaScript, Python, C#, and Racket.

Files: [`OperatorMutations.txt`](OperatorMutations.txt), [`StatementMutation.txt`](StatementMutation.txt), or [`StringMutation.txt`](StringMutation.txt) at repo root. Headings become HTML labels. Write symbols as in source: `<=` and `&&`.

Example — mutate remainder to plus, under Math Operators in `OperatorMutations.txt`:

```text
Replace: {quantity} % {quantity}
With: {quantity} + {quantity}
```

- Optional `And: Inverse` also generates the reverse.
- Optional `Except:` lines skip sites whose source text matches.

No scanner Java for this path. If the mutant is missing in one language, that adapter is not emitting the node ([5.2](#52-teach-adapters-a-new-operator-spelling) / [5.5](#55-add-a-language)).

### 5.2 Teach adapters a new operator spelling

`MSLExpressionFactory.CANONICAL_OPERATORS` is the allow-list the spec parser knows:

```text
+  -  *  /  %  //  **
<  <=  >  >=  ==  !=
&&  ||  &  |  ^  <<  >>  >>>
```

If the factory rejects the symbol (`Unsupported MSL operator "<=>"`), add it to that set. Then map language spellings on each adapter:

| Method | Role |
| --- | --- |
| `getOperatorAliases` | Source tokens that mean the canonical op (Python `&&` -> `and`) |
| `toLanguageOperator` | Text written into the mutant; return `null` when the language has no equivalent |
| `createOperatorEdit` | Override when the op is not one swappable token (Racket `(not (= a b))`, Python `is not`, Java `Math.floorDiv` as `//`) |

### 5.3 Add a built-in `mutator_type` name

Use this when a config should say `mutator_type=AOR_PLUS_TO_MINUS` (or a new enum) with no spec file.

1. Add a name to `Mutator.MutatorType` in `model/Mutator.java`. `mutator_type=ALL` includes every enum automatically. `CUSTOM` stays the spec-driven branch.
2. Handle it in each language scanner’s `scanFile`. Java routes several PIT-style types through `UniversalPitMutationScanner`; operator swaps live in that language’s visitor switch.
3. If `CUSTOM` + a spec placeholder should reuse the same native scan, add a `createRule` branch that returns `ScannerBackedRule(MutatorType.YOUR_NAME, label)`.

An operator swap that can be written as Replace/With belongs in [5.1](#51-add-a-mutation-in-the-spec-usual-path) even if an enum already exists. The spec path is what a universal run uses.

### 5.4 Add a new kind of site (new placeholder)

`createRule` in `MutationRuleGenerator` is an exact-string switch. Unknown Replace/With lines are skipped.

To add `{delete_statement}` (or any new placeholder):

1. Decide the rule class. Implement `MutationRule` (`matches` + `apply`, or override `findMutationPoints` like `ScannerBackedRule`).
2. Add the from/to strings in `createRule`.
3. Add adapter methods on `MSLScannerAdapter` with a safe default, then override in each language scanner that can see the construct.
4. Add a spec block in `StatementMutation.txt` (or a new file) and a `CUSTOM` config.
5. Parity test: `StatementMutationsCrossLanguageParityTests` pattern, one expected mutant per language.

This is the expensive path. A planned general construct node would make a new category look more like [5.1](#51-add-a-mutation-in-the-spec-usual-path) once adapters already tag the site. Until then, first recognition of a construct still needs adapter translation once; later variations of that construct can be spec-only.

### 5.5 Add a language

A new language still needs an adapter. It should reuse [`OperatorMutations.txt`](OperatorMutations.txt), [`StatementMutation.txt`](StatementMutation.txt), and [`StringMutation.txt`](StringMutation.txt). Missing `+` to `-` in the new language means the adapter is not emitting the node.

Checklist (summary):

1. Grammar under `src/main/antlr4`
2. New `*MutationScanner` with `createAdapter` and `scanFile`
3. Three hooks in `Main.java`: `shouldScanFile`, `findCustomMutationsInFile`, `findMutationsInFile`
4. `ScannerBackedRule` branch
5. `SafetyController.assessLocal` branch
6. `samples/` project with config
7. Parity tests
8. Optional setup-script updates

### 5.6 Add a sample project or config

[`samples/`](samples/) is the demo tree. Needs:

- Source under a small `source_code_directory`
- Tests the project’s own tool can run
- A config

### 5.7 Add or change a report field

Reports live in `report/` only. Labels come from the spec heading when present.

### 5.8 Add a safety check

`safety/` and [`SAFETY.md`](SAFETY.md). `SafetyController` labels each mutant `SAFE` / `REVIEW` / `BLOCK` / `UNKNOWN` before write.

### 5.9 Add a coverage format

`coverage/CoverageData.load` picks a parser from the `coverage_report` path.

### 5.10 Add setup / toolchain support

[`chocolatey-setup.ps1`](chocolatey-setup.ps1), [`demo-env.ps1`](demo-env.ps1), [`demo/MutationTester-Setup-Guide.md`](demo/MutationTester-Setup-Guide.md), [`Dockerfile`](Dockerfile). The `Dockerfile` is optional: use it to test the tool in a blank environment. The Docker install is slow.

### 5.11 Tests after you add something

`src/test/java/mutationtester/`. On PowerShell, quote `-Dtest`.

| After you change… | Command |
| --- | --- |
| Spec operator swap ([5.1](#51-add-a-mutation-in-the-spec-usual-path)) | `mvn "-Dtest=OperatorMutationsCrossLanguageParityTests" test` |
| New canonical symbol or alias ([5.2](#52-teach-adapters-a-new-operator-spelling)) | `mvn "-Dtest=MSLTests,OperatorMutationsCrossLanguageParityTests,PythonMslMutantQualityTests,JavaMslMutantQualityTests" test` |
| Built-in enum ([5.3](#53-add-a-built-in-mutator_type-name)) | `mvn "-Dtest=PlusToMinusTests,UniversalPitBehaviorTests" test` (or the class that already covers that `MutatorType`) |
| New placeholder ([5.4](#54-add-a-new-kind-of-site-new-placeholder)) | `mvn "-Dtest=StatementMutationsCrossLanguageParityTests,EndToEndMSLTests" test` |
| New language ([5.5](#55-add-a-language)) | Parity classes plus a new `*MslMutantQualityTests` and `*ScannerTests` |
| Report ([5.7](#57-add-or-change-a-report-field)) | `mvn "-Dtest=PitStyleReportDetailsTests,AllSurvivorsPageTests" test` |
| Safety ([5.8](#58-add-a-safety-check)) | `mvn "-Dtest=SafetyGateTests,PythonSyntaxSafetyAssessorTests,AntlrSyntaxSafetyAssessorTests" test` |
| Coverage ([5.9](#59-add-a-coverage-format)) | `mvn "-Dtest=CoverageDataTests" test` |

Then a small jar run against a sample:

```powershell
mvn -DskipTests package
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/MavenProject maven.config
```

---

## 6. Development notes

Branches: `main` (integration), `MSL` (shared spec), named feature branches.

Example:

```powershell
git checkout mcdonacj_branch
```

then PR into `MSL`. Do not push `main` or `MSL` unless asked. Do not commit `temp_` files, `demo-env.ps1`, or `output/`.

**Developer** — full test suite:

```powershell
. .\demo-env.ps1
mvn test
```

Work by mutation type, not by language. Spec files are for operator variations. Adapters are for parse lookalikes. Spec spelling is `<=` and `&&`. Adding something new: [section 5](#5-extending-the-tester-developer-guide).

### Why it is built this way

- One spec, not five PIT clones: client asked for a universal rules language.
- `<=` / `&&` in the spec: parser names (`LE`) stay in the adapter.
- General nodes (planned): cheaper new categories than `createRule` + five hooks. Not a regex rewriter.
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
- Docker is optional. Useful for testing the tool in a blank environment; skip it for everyday jar use. The install is slow. Docker is not a mutant sandbox.
- Wrong Puppeteer Chrome version: install from `samples/JSProject`, not global `npx`.
- Windows: `python` in `test_command` unless `python3` exists. Load racket/dotnet via demo-env.

### Jar

Two arguments.

```powershell
# Wrong (missing config)
java -jar tool.jar samples/MavenProject

# Right
java -jar tool.jar samples/MavenProject maven.config
```

- Config and spec paths are from cwd.
- `source_code_directory=.` on a huge tree is slow. Use the project’s `src` folder.
- Baseline must pass. 180s per command. Reports overwrite `output/report/`.

### Skipped mutants

- `NO COVERAGE`: add tests or omit `coverage_command`. Maven `prototypeSample.java` is a usual example.
- Strings, comments, generics, for-in, assignment `=` are skipped on purpose.
- Spec `LE` or `and`: use `<=` and `&&`.
- `Except: for/while` is C-style text; Racket loops often still mutate.
- Racket null stand-in `'()` vs `#f`: adapter, not spec.

### Safety

- No console -> skip `REVIEW`/`UNKNOWN`/`BLOCK`. Use a real PowerShell window for `c`/`s`/`a`.
- `BLOCK` cannot continue. Score ignores safety skips.

### Easy to mix up

- `AOR_PLUS_TO_MINUS` is the config enum. `Math Operators: + -> -` is the report label.
- `test_command=echo ok` means every executed mutant `SURVIVES` (smoke scan).
- `SharedPitProject` is for scanning, not a full Maven app.
- Client demo: prebuilt jar only, not `git clone && mvn package` in the room.

---

## See also

- [`SAFETY.md`](SAFETY.md) — safety labels, prompts, and optional OpenAI review
- [`demo/MutationTester-Setup-Guide.md`](demo/MutationTester-Setup-Guide.md) — Chocolatey, demo-env, Docker
- [`docs/operators/racket.md`](docs/operators/racket.md) — Racket operator names (other languages do not have matching files yet)
- [`samples/`](samples/) — language demos
