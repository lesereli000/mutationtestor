# MutationTester

A mutation tester for Java, C#, JavaScript, Python and Racket. Mutations are
written once, language-agnostically, in spec files (see
[docs/spec-format.md](docs/spec-format.md)); each language's adapter finds
them in that language's parse tree.

```bash
mvn package -DskipTests
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar <project_directory> <config_file>
```

For example, from the repository root:

```bash
java -jar target/MutationTester-1.0-SNAPSHOT-jar-with-dependencies.jar samples/PytestProject configs/samples/python-operators.config
```

Setup, configs and reports are covered in the
[setup guide](docs/setup-guide/MutationTester-Setup-Guide.md).

## Where things are

| Folder | What it holds |
|---|---|
| `specs/` | The mutation spec files. `OperatorMutations.spec`, `StatementMutation.spec`, `StringMutation.spec` and `context-sensitive-mutations.spec` are the main sets; `pit/` holds one PIT mutator each; `single-operator/` holds one operator each (O01–O25), used by the baseline tests. |
| `configs/samples/` | A config per sample project and spec, named `<language>-<spec>.config`. Each starts with the command that runs it. |
| `configs/external/` | Configs for the real-world projects under `external/`. |
| `samples/` | Small projects in each language, with tests, to run the tool on. Each project's `Demo*` files are the original single-operator examples. |
| `external/` | Git submodules of real open-source projects, for testing at scale (`git submodule update --init`). |
| `scripts/` | Dependency setup (`setup-dependencies`, `chocolatey-setup.ps1`), the all-languages demo, and `cleanup.ps1`. Run them from anywhere; they find the repository root themselves. |
| `docs/` | The spec format, the setup guide (with its screenshots), and per-language operator notes. |
| `src/main/antlr4/` | The ANTLR grammars for each language, and ISL for Racket. |
| `src/main/java/mutationtester/` | `Main` (the command line), the ANTLR base classes the grammars need, and the packages below. |
| `src/test/resources/msl-baseline/` | Every mutant each spec produced on each corpus. `MslBaselineTests` fails if one is lost; `pending-review-losses.txt` lists explained losses awaiting review. |

| Package | What it does |
|---|---|
| `msl` | Parses spec files into rules and pattern trees, and matches them against parse trees. |
| `scanner` | One adapter per language: what `{quantity}`, `{statement}` and the other placeholders mean there, and how to rewrite its code. |
| `model` | `MutationPoint` (one mutant) and `MutantResult` (its outcome). |
| `coverage` | Reads coverage reports, so mutants on uncovered lines are skipped. |
| `report` | The HTML and PIT-style reports. |
| `equivalent` | Stores mutants marked equivalent; `EquivalentMutantApiServer` serves them to the HTML report on port 8081. |
| `safety` | Prototype: classifies mutants as safe, review or block before they run (`SafetyFlowDemo` prints examples). |

Generated output goes to `output/` (reports), `demo-output/` (the demo
script) and `target/`; all three are ignored by git.
