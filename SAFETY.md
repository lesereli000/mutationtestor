# Safety-aware mutation testing

Before writing each mutant, the runner compares the original and mutated code and labels cases as SAFE, REVIEW, BLOCK, or UNKNOWN, with an explanation. ANTLR syntax-tree analyzers support Python, Java, JavaScript, C#, and Racket. They check supported branch conditions for recognized call names inside the branch; Python, Java, JavaScript, and C# also recognize selected loop conditions. They can relate a condition to a call even when they are more than eight lines apart, while ignoring unrelated neighboring functions, comments, and strings. Python additionally checks mutations inside certain direct calls and can report a possible chain through up to four uniquely named same-file functions. The chain is a syntactic name match, not proof of the runtime call target: shadowing, reassignment, method dispatch, aliases, and imports are not resolved. Duplicate names and recursive cycles are not followed. Unsupported constructs and unresolved cases are UNKNOWN. Racket loop forms and direct call changes in non-Python languages are not yet analyzed structurally.

Python and JavaScript also flag some changed early-return guards when a recognized operation appears later in the same function. JavaScript can follow possible same-file helper-name chains. These paths may be imprecise, particularly for callbacks; they are evidence for review rather than proof that an operation will run.

In an interactive terminal, enter `c` to continue once, `s` to skip, or `a` to stop the mutation run. BLOCK cannot be continued. In a noninteractive run, REVIEW, UNKNOWN, and BLOCK are skipped; the config key `unattended_safety=run_unknown` tests UNKNOWN mutants instead (REVIEW and BLOCK are still skipped), which the GitHub Actions workflow template sets. Decisions are not saved. Safety-skipped or aborted mutants appear in the console and HTML report and are excluded from the mutation score.

The local assessors analyze one source file. They can miss indirect calls, aliases, other files, generated code, malicious code, and risks in the test or build commands; they can also flag harmless calls. Python syntax errors or unresolved effects are UNKNOWN, not safe. The limited destructive-command block still uses a text check, constrained in Python to calls identified as process or database operations. Neither local nor model review is a sandbox or a guarantee of safety. Baseline compile, test, and coverage commands run before this per-mutant gate and can have side effects. Run only trusted projects and commands in an appropriately isolated environment. The client has not yet confirmed the proposed safety design.

## Optional model review

For a broader contextual explanation, explicitly opt in to OpenAI review before starting the program:

```sh
export OPENAI_API_KEY='your-key'
export MUTATION_SAFETY_MODEL='your-supported-model'
export MUTATION_SAFETY_SEND_CODE=yes
```

Run the normal JAR command in that terminal. This sends the **entire original and mutated source file, its file name, and the compile and test commands** for each eligible mutant to OpenAI. These can contain sensitive data; use this option only if you are authorized to share them. It does not send other project files or test contents, so the model lacks that context. Requests may incur API charges and increase runtime. A file larger than 30,000 characters is not sent. Model review is disabled by default; unset `MUTATION_SAFETY_SEND_CODE` to disable it. The model is asked to reason about the change, but its response cannot approve execution or override a block. Missing credentials, service failures, refusals, incomplete responses, and malformed answers fall back to local review or unknown. The remote integration has been tested using simulated responses, not a live API account. Assessments do not save decisions between runs.

Run the safety checks and decision demo:

```sh
mvn -Dtest=SafetyGateTests,SafetyIntegrationTests,PythonSyntaxSafetyAssessorTests,AntlrSyntaxSafetyAssessorTests,ContextualSafetyAssessorTests,OpenAiSafetyReviewerTests test
java -cp target/classes mutationtester.safety.SafetyFlowDemo
```

The first demo lets you choose what to do for several examples; it does not execute the displayed code. The safe example proceeds without a prompt, while review and unknown cases ask for input. A blocked case only allows skip or abort.

To see the live gate with an actual project, first build the JAR and then run:

```sh
mvn -DskipTests package
bash demo-safety.sh
```

This runs the included Python inventory project and its real unit tests. Eight mutants cover file-writing and deletion conditions, an in-memory database, fake network and process transports, a loop condition, arithmetic, and a discount boundary that the tests intentionally miss. The tests write only inside Python temporary directories and make no real network requests or real process launches. At each prompt, `c` runs the mutant, `s` skips it, and `a` aborts the mutation run. The script prints a new temporary directory containing its report, so it does not overwrite the regular project report. Run it again to try other choices. Redirected input or CI is treated as noninteractive by the real runner. For an explicit blocked example, use the interactive `SafetyFlowDemo`; the real-project demo does not execute destructive operations.
