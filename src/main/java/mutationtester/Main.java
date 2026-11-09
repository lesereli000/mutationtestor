package mutationtester;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;

import mutationtester.coverage.CoverageData;
import mutationtester.equivalent.EquivalentMutantStore;
import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.MSLScannerAdapter;
import mutationtester.msl.MutationRule;
import mutationtester.msl.MutationRuleGenerator;
import mutationtester.report.EquivalentsPageData;
import mutationtester.report.HtmlReportGenerator;
import mutationtester.report.MutationReport;
import mutationtester.scanner.CSharpMutationScanner;
import mutationtester.scanner.JavaMutationScanner;
import mutationtester.scanner.JavaScriptMutationScanner;
import mutationtester.scanner.PythonMutationScanner;
import mutationtester.scanner.RacketMutationScanner;
import mutationtester.safety.SafetyAction;
import mutationtester.safety.SafetyController;
import mutationtester.safety.SafetyAssessment;

public class Main {
    private static final long MAX_CMD_TIME = 180000;
    static final long DEFAULT_TEST_TIMEOUT_SECONDS = MAX_CMD_TIME / 1000;
    // How long one run of the test command may take (config: test_timeout_seconds).
    // A mutant that makes the tests loop forever is stopped at this limit.
    static long testTimeoutMillis = MAX_CMD_TIME;
    private static final String OUTPUT_ROOT = "output";
    private static final String WORKING_DIR_PREFIX = "workdir-";
    static final SafetyController SAFETY = SafetyController.fromEnvironment(
            System.console() == null ? null : new BufferedReader(new InputStreamReader(System.in)), System.out);
    private static final Path EQUIVALENTS_BACKUP = Path.of(OUTPUT_ROOT, "report", "equivalents.json");
    private static final int MAX_SUMMARY_SURVIVORS = 50;

    /** Process exit codes, so CI can fail a job on them. */
    static final int EXIT_OK = 0;
    static final int EXIT_SETUP_FAILED = 1;
    static final int EXIT_BELOW_THRESHOLD = 2;
    static final int EXIT_STOPPED_EARLY = 3;

    static final int MAX_WORKERS = 4;
    /** How long a cancel waits for the partial report; GitHub kills the process about 7.5 s after asking. */
    private static final long CANCEL_GRACE_SECONDS = 6;

    /** Every test/build process still running, so a cancel can kill them. */
    private static final Set<Process> RUNNING_PROCESSES = ConcurrentHashMap.newKeySet();

    /** Set by parallel workers so runCommand's progress lines don't interleave. */
    static final ThreadLocal<Boolean> QUIET = ThreadLocal.withInitial(() -> false);

    static class Config {
        String sourceCodeDirectory;
        String compileCommand;
        String testCommand;
        String testMode;          // BROWSER, CLI, or AUTO
        String customMutationSpec; // path to the .spec file whose rules make the mutants
        String coverageCommand;   // optional command that writes a coverage report
        String coverageReport;    // optional lcov/cobertura/jacoco report path, relative to project dir
        long testTimeoutSeconds = DEFAULT_TEST_TIMEOUT_SECONDS; // limit for one run of test_command
        Double minMutationScore;  // optional 0-100; below it the run exits with EXIT_BELOW_THRESHOLD
        Double timeLimitMinutes;  // optional; no new mutants start after this, and the report is partial
        List<String> excludeSources = List.of(); // folders/files, relative to project dir, never mutated
        boolean runUnknownUnattended;  // unattended_safety=run_unknown: test UNKNOWN mutants with no terminal
    }

    enum RunStatus { COMPLETED, STOPPED_EARLY, NO_MUTANTS, SETUP_FAILED }

    /** How a run ended. The report and equivalents are only set when it completed or stopped early. */
    record RunOutcome(RunStatus status, MutationReport report, EquivalentsBranch.Snapshot equivalents, String message) {
        static RunOutcome setupFailed(String message) {
            return new RunOutcome(RunStatus.SETUP_FAILED, null, null, message);
        }

        static RunOutcome noMutants(String message) {
            return new RunOutcome(RunStatus.NO_MUTANTS, null, null, message);
        }
    }

    static String runCommand(String command, String directory) {
        return runCommand(command, directory, MAX_CMD_TIME);
    }

    /** Runs the project's test command with the configured test_timeout_seconds limit. */
    static String runTests(String testCommand, String directory) {
        return runCommand(testCommand, directory, testTimeoutMillis);
    }

    static String runCommand(String command, String directory, long timeoutMillis) {
        Process process;
        List<String> commandList = new ArrayList<>(Arrays.asList(command.split(" ")));
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }

            File workingDir = (directory != null) ? new File(directory) : null;
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            process = pb.start();
            RUNNING_PROCESSES.add(process);

        } catch (IOException e) {
            System.out.println("Error running command " + command + ": " + e.getMessage());
            e.printStackTrace();
            return "";
        }

        StringBuilder output = new StringBuilder();
        ExecutorService outputExecutor = Executors.newSingleThreadExecutor();
        Future<?> outputFuture = outputExecutor.submit(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append(System.lineSeparator());
                }
            } catch (IOException e) {
                if (process.isAlive() && !"Stream closed".equals(e.getMessage())) {
                    throw new RuntimeException(e);
                }
            }
        });

        try {
            boolean completed = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
            if (!completed) {
                destroyProcessTree(process);
                if (!QUIET.get()) {
                    System.out.println("Process " + commandList.get(0) + " timed out after "
                            + (timeoutMillis / 1000) + " s.");
                }
                output.append("PROCESS_TIMEOUT").append(System.lineSeparator());
                outputFuture.cancel(true);
                return output.toString();
            }

            waitForOutputReader(outputFuture);

            int exitCode = process.exitValue();
            if (exitCode != 0) {
                output.append("PROCESS_EXIT_CODE: ").append(exitCode).append(System.lineSeparator());
            }

            outputExecutor.shutdown();
            process.destroy();
            if (!QUIET.get()) {
                System.out.println("Process " + commandList.get(0) + " finished");
            }
            return output.toString();

        } catch (ExecutionException e) {
            destroyProcessTree(process);
            System.out.println("Error reading command output " + command + ": " + e.getMessage());
            e.printStackTrace();
            output.append("PROCESS_EXECUTION_ERROR").append(System.lineSeparator());
            return output.toString();
        } catch (InterruptedException e) {
            destroyProcessTree(process);
            Thread.currentThread().interrupt();
            System.out.println("Interrupted while waiting for process " + commandList.get(0) + " to finish");
            e.printStackTrace();
            return "";
        } finally {
            outputExecutor.shutdownNow();
            RUNNING_PROCESSES.remove(process);
        }
    }

    /** For a cancel: kill everything still running without waiting, the grace period is short. */
    private static void killRunningProcesses() {
        for (Process process : RUNNING_PROCESSES) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }
    }

    /** Explains a baseline failure that was really the test_timeout_seconds limit. */
    private static void printTimeoutHint(String baselineOutput) {
        if (baselineOutput.contains("PROCESS_TIMEOUT")) {
            System.err.println("The tests took longer than test_timeout_seconds ("
                    + (testTimeoutMillis / 1000) + " s) on the original code. Raise test_timeout_seconds in the config.");
        }
    }

    private static void destroyProcessTree(Process process) {
        List<ProcessHandle> descendants = process.descendants().toList();
        descendants.forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
            for (ProcessHandle descendant : descendants) {
                descendant.onExit().get(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            System.err.println("Warning: Timed out while waiting for child process cleanup: " + e.getMessage());
        }
    }

    private static void waitForOutputReader(Future<?> outputFuture)
            throws InterruptedException, ExecutionException {
        try {
            outputFuture.get(5, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            outputFuture.cancel(true);
        }
    }

    public static void main(String[] args) {

        // These are for testing purposes because the GUI tree view does not open in
        // test mode
        // MyParser py = new MyParser("samples/PytestProject/src/Add1.py");
        // MyParser java = new MyParser("samples/MavenProject/src/main/java/Add1.java");
        // MyParser js = new MyParser("samples/JSProject/src/Add1.js");
        // MyParser cs = new MyParser("samples/CSProject/src/CSProject/Add1.cs");

        System.exit(run(args));
    }

    /** Runs the tool and returns the process exit code (see the EXIT_ constants). */
    static int run(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: java -jar MutationTester.jar <project_directory> <config_file>");
            return EXIT_SETUP_FAILED;
        }

        String projectDirectory = args[0];
        String configFile = args[1];

        // The clock for time_limit_minutes starts here, so it covers the whole run.
        long startedMillis = RunControl.nowMillis();

        Config config;
        try {
            config = readConfig(configFile);
        } catch (IOException e) {
            return finish(RunOutcome.setupFailed("Error reading config file " + configFile + ": " + e.getMessage()), null, null);
        }

        int workers;
        try {
            workers = workerCount(System.getenv());
        } catch (IllegalArgumentException e) {
            return finish(RunOutcome.setupFailed(e.getMessage()), config.minMutationScore, null);
        }

        testTimeoutMillis = config.testTimeoutSeconds * 1000;
        SAFETY.setRunUnknownUnattended(config.runUnknownUnattended);

        // Check the spec before copying the project and running the baseline, so a
        // mistake in it is reported straight away as one line.
        try {
            loadCustomRules(config.customMutationSpec);
        } catch (IOException e) {
            return finish(RunOutcome.setupFailed(e.getMessage()), config.minMutationScore, null);
        }

        RunControl control = new RunControl(config.timeLimitMinutes, RunControl::nowMillis, startedMillis);

        // On a cancel (GitHub Actions cancelling the job, Ctrl+C) the JVM runs this
        // before exiting: stop the workers, kill the test processes, and give the
        // main thread a few seconds to write a partial report.
        Thread cancelHook = new Thread(() -> {
            if (control.finished.getCount() == 0) {
                return;
            }
            System.err.println("\nStop requested: writing a report from the mutants finished so far...");
            control.cancel();
            killRunningProcesses();
            try {
                control.finished.await(CANCEL_GRACE_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "mutationtester-cancel");
        Runtime.getRuntime().addShutdownHook(cancelHook);

        try {
            RunOutcome outcome = mutationTest(
                    projectDirectory,
                    config.sourceCodeDirectory,
                    config.compileCommand,
                    config.testCommand,
                    config.customMutationSpec,
                    config.coverageCommand,
                    config.coverageReport,
                    config.excludeSources,
                    workers,
                    control);
            return finish(outcome, config.minMutationScore, control);
        } finally {
            control.finished.countDown();
            try {
                Runtime.getRuntime().removeShutdownHook(cancelHook);
            } catch (IllegalStateException e) {
                // Already shutting down; the hook is running.
            }
        }
    }

    /**
     * Parallel workers are a GitHub Actions feature: outside Actions this is
     * always 1. In Actions it is MUTATION_WORKERS (1 when not set), at most
     * MAX_WORKERS.
     *
     * @throws IllegalArgumentException when MUTATION_WORKERS is not a whole number of 1 or more
     */
    static int workerCount(Map<String, String> env) {
        if (!"true".equals(env.get("GITHUB_ACTIONS"))) {
            return 1;
        }
        String raw = env.get("MUTATION_WORKERS");
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        int workers;
        try {
            workers = Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            workers = 0;
        }
        if (workers < 1) {
            throw new IllegalArgumentException(
                    "MUTATION_WORKERS must be a whole number from 1 to " + MAX_WORKERS + ", got: " + raw);
        }
        if (workers > MAX_WORKERS) {
            System.out.println("MUTATION_WORKERS=" + workers + " is above the maximum of " + MAX_WORKERS
                    + "; using " + MAX_WORKERS + ".");
            return MAX_WORKERS;
        }
        return workers;
    }

    private static int finish(RunOutcome outcome, Double minScore, RunControl control) {
        if (outcome.status() == RunStatus.SETUP_FAILED && control != null && control.isCancelled()) {
            outcome = RunOutcome.setupFailed("The run was cancelled before any mutants were tested.");
        }
        int exitCode = exitCodeFor(outcome, minScore);
        System.out.println();
        if (outcome.status() == RunStatus.SETUP_FAILED) {
            System.err.println("ERROR: Mutation testing did not run: " + outcome.message());
        } else {
            if (outcome.status() == RunStatus.STOPPED_EARLY) {
                System.out.println("RUN STOPPED EARLY: " + outcome.report().getPartialReason()
                        + ". The report covers only the mutants that were tested.");
            }
            String gate = gateLine(outcome, minScore);
            if (gate != null) {
                System.out.println(gate);
            }
        }
        writeStepSummary(outcome, minScore, exitCode);
        System.out.println("Quitting");
        return exitCode;
    }

    /**
     * EXIT_SETUP_FAILED when the run could not happen, or when a threshold is
     * set but nothing could be scored. EXIT_STOPPED_EARLY when the time limit
     * or a cancel stopped it before every mutant was tested. Otherwise
     * EXIT_BELOW_THRESHOLD when the score (rounded to one decimal, as printed)
     * is under the threshold.
     */
    static int exitCodeFor(RunOutcome outcome, Double minScore) {
        return switch (outcome.status()) {
            case SETUP_FAILED -> EXIT_SETUP_FAILED;
            case STOPPED_EARLY -> EXIT_STOPPED_EARLY;
            case NO_MUTANTS -> minScore == null ? EXIT_OK : EXIT_SETUP_FAILED;
            case COMPLETED -> {
                if (minScore == null) {
                    yield EXIT_OK;
                }
                if (outcome.report().getTotalMutants() == 0) {
                    yield EXIT_SETUP_FAILED;
                }
                yield roundedScore(outcome.report()) >= minScore ? EXIT_OK : EXIT_BELOW_THRESHOLD;
            }
        };
    }

    static String gateLine(RunOutcome outcome, Double minScore) {
        if (minScore == null) {
            return null;
        }
        if (outcome.status() == RunStatus.STOPPED_EARLY) {
            return String.format("MUTATION SCORE GATE: NOT CHECKED (the run stopped early; minimum is %.1f%%)", minScore);
        }
        if (outcome.status() != RunStatus.COMPLETED || outcome.report().getTotalMutants() == 0) {
            return String.format("MUTATION SCORE GATE: FAILED (no mutants to score; minimum is %.1f%%)", minScore);
        }
        double score = roundedScore(outcome.report());
        return score >= minScore
                ? String.format("MUTATION SCORE GATE: PASSED (%.1f%% >= %.1f%%)", score, minScore)
                : String.format("MUTATION SCORE GATE: FAILED (%.1f%% < %.1f%%)", score, minScore);
    }

    private static double roundedScore(MutationReport report) {
        return Math.round(report.getMutationScore() * 10) / 10.0;
    }

    /** Appends a markdown summary to the job page when running in GitHub Actions. */
    private static void writeStepSummary(RunOutcome outcome, Double minScore, int exitCode) {
        String summaryFile = System.getenv("GITHUB_STEP_SUMMARY");
        if (summaryFile == null || summaryFile.isBlank()) {
            return;
        }
        try {
            Files.writeString(Path.of(summaryFile), buildStepSummary(outcome, minScore, exitCode),
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("WARNING: Could not write the job summary: " + e.getMessage());
        }
    }

    static String buildStepSummary(RunOutcome outcome, Double minScore, int exitCode) {
        StringBuilder md = new StringBuilder("## Mutation testing\n\n");
        if (outcome.status() == RunStatus.SETUP_FAILED) {
            md.append("**Did not run:** ").append(oneLine(outcome.message())).append("\n");
            return md.toString();
        }

        String gate = gateLine(outcome, minScore);
        if (outcome.status() == RunStatus.NO_MUTANTS) {
            md.append(oneLine(outcome.message())).append("\n");
            if (gate != null) {
                md.append("\n").append(exitCode == EXIT_OK ? "✅ " : "❌ ").append(gate).append("\n");
            }
            return md.toString();
        }

        MutationReport report = outcome.report();
        if (report.isPartial()) {
            md.append("⚠️ **Partial run:** ").append(oneLine(report.getPartialReason())).append(". ")
                    .append(report.getNotTested()).append(" mutant").append(report.getNotTested() == 1 ? " was" : "s were")
                    .append(" not tested; the score covers only the tested ones and the minimum was not checked.\n\n");
            md.append(String.format("**Mutation score (tested mutants): %.1f%%**", roundedScore(report)));
        } else {
            md.append(String.format("**Mutation score: %.1f%%**", roundedScore(report)));
            if (minScore != null) {
                md.append(String.format(" (minimum %.1f%%) ", minScore)).append(exitCode == EXIT_OK ? "✅" : "❌");
            }
        }
        md.append("\n\n");
        md.append("| Killed | Survived | No coverage | Equivalent (excluded) | Scored |\n");
        md.append("|---:|---:|---:|---:|---:|\n");
        md.append(String.format("| %d | %d | %d | %d | %d |\n\n", report.getKilled(), report.getSurvived(),
                report.getNoCoverage(), report.getEquivalent(), report.getTotalMutants()));
        if (outcome.equivalents() != null && outcome.equivalents().source() != EquivalentsBranch.Source.NONE) {
            md.append("Equivalent mutants from: ").append(oneLine(outcome.equivalents().description())).append("\n\n");
        }

        List<MutantResult> survivors = report.getSurvivedResults();
        if (!survivors.isEmpty()) {
            md.append("### Surviving mutants\n\n");
            for (MutantResult result : survivors.subList(0, Math.min(survivors.size(), MAX_SUMMARY_SURVIVORS))) {
                MutationPoint p = result.getMutationPoint();
                md.append("- `").append(p.getSourceFile().getName()).append(":").append(p.getLineNumber()).append("` ")
                        .append(code(p.getOperator())).append(" → ").append(code(p.getReplacement()));
                if (p.getCustomLabel() != null) {
                    md.append(" (").append(oneLine(p.getCustomLabel())).append(")");
                }
                md.append("\n");
            }
            if (survivors.size() > MAX_SUMMARY_SURVIVORS) {
                md.append("- …and ").append(survivors.size() - MAX_SUMMARY_SURVIVORS).append(" more\n");
            }
            md.append("\n");
        }
        md.append("Download the **mutation-report** artifact for the full HTML report. ")
                .append("To mark equivalent mutants, open `all-survivors.html` from it.\n");
        return md.toString();
    }

    private static String code(String text) {
        String flat = oneLine(text).replace("`", "'");
        return flat.isEmpty() ? "*(empty)*" : "`" + flat + "`";
    }

    private static String oneLine(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").trim();
    }


    /** Package-visible overload used by tests to pass a File directly. */
    static Config readConfig(File configFile) throws IOException {
        return readConfig(configFile.getAbsolutePath());
    }

    private static Config readConfig(String configFile) throws IOException {
        Path configPath = Path.of(configFile);

        if (!Files.exists(configPath)) {
            throw new IOException("Config file not found");
        }

        Map<String, String> values = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(configPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int idx = line.indexOf('=');
                if (idx < 0) {
                    continue;
                }

                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                values.put(key, value);
            }
        }

        Config config = new Config();
        config.sourceCodeDirectory = values.get("source_code_directory");
        config.compileCommand = values.getOrDefault("compile_command", "");
        config.testCommand = values.get("test_command");

        // Read mutation strategy (defaults to AUTO)
        config.testMode = values.getOrDefault("test_mode", "AUTO");
        config.customMutationSpec = values.getOrDefault("custom_mutation_spec", null);
        config.coverageCommand = values.getOrDefault("coverage_command", "");
        config.coverageReport = values.getOrDefault("coverage_report", "");

        String timeout = values.get("test_timeout_seconds");
        if (timeout != null && !timeout.isEmpty()) {
            try {
                config.testTimeoutSeconds = Long.parseLong(timeout);
            } catch (NumberFormatException e) {
                config.testTimeoutSeconds = 0;
            }
            if (config.testTimeoutSeconds <= 0) {
                throw new IOException("test_timeout_seconds must be a whole number of seconds above 0, not \""
                        + timeout + "\"");
            }
        }

        if (config.sourceCodeDirectory == null || config.sourceCodeDirectory.isEmpty()) {
            throw new IOException("Missing source_code_directory in config file");
        }

        // Mutations always come from a spec file; mutator_type used to choose a
        // built-in mutator and is now ignored.
        if (values.containsKey("mutator_type")) {
            System.err.println("WARNING: mutator_type is deprecated and ignored; the mutations come from"
                    + " custom_mutation_spec.");
        }
        if (config.customMutationSpec == null || config.customMutationSpec.isEmpty()) {
            throw new IOException("Missing custom_mutation_spec in config file (the .spec file of mutation rules)");
        }

        if (config.testCommand == null || config.testCommand.isEmpty()) {
            throw new IOException("Missing test_command in config file");
        }

        String minScore = values.get("min_mutation_score");
        if (minScore != null && !minScore.isEmpty()) {
            double parsed;
            try {
                parsed = Double.parseDouble(minScore);
            } catch (NumberFormatException e) {
                parsed = Double.NaN;
            }
            if (!(parsed >= 0 && parsed <= 100)) {
                throw new IOException("min_mutation_score must be a number from 0 to 100, got: " + minScore);
            }
            config.minMutationScore = parsed;
        }

        String timeLimit = values.get("time_limit_minutes");
        if (timeLimit != null && !timeLimit.isEmpty()) {
            double parsed;
            try {
                parsed = Double.parseDouble(timeLimit);
            } catch (NumberFormatException e) {
                parsed = Double.NaN;
            }
            if (!(parsed > 0 && parsed < Double.POSITIVE_INFINITY)) {
                throw new IOException("time_limit_minutes must be a number above 0, got: " + timeLimit);
            }
            config.timeLimitMinutes = parsed;
        }

        String unattended = values.getOrDefault("unattended_safety", "skip").trim();
        if (!unattended.equals("skip") && !unattended.equals("run_unknown")) {
            throw new IOException("unattended_safety must be skip or run_unknown, got: " + unattended);
        }
        config.runUnknownUnattended = unattended.equals("run_unknown");

        String exclude = values.get("exclude_source");
        if (exclude != null && !exclude.isEmpty()) {
            List<String> entries = new ArrayList<>();
            for (String entry : exclude.split(",")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (Path.of(trimmed).isAbsolute() || trimmed.startsWith("/") || trimmed.startsWith("\\")) {
                    throw new IOException("exclude_source entries must be relative to the project folder, got: " + trimmed);
                }
                entries.add(trimmed);
            }
            config.excludeSources = List.copyOf(entries);
        }

        return config;
    }

    private static RunOutcome mutationTest(String projectDir, String sourceCode, String command1,
        String command2, String customMutationSpec,
        String coverageCommand, String coverageReport, List<String> excludeSources, int workers, RunControl control) {
        Path projectPath = Path.of(projectDir);
        Path sourcePath = Path.of(sourceCode);
        Path outputRoot = Path.of(OUTPUT_ROOT);
        Path mutantPath = outputRoot.resolve(WORKING_DIR_PREFIX + System.currentTimeMillis());

        System.out.println("Setting up working directory");
        try {
            FileUtils.forceMkdir(outputRoot.toFile());
            cleanupWorkDirectories(outputRoot, null);
            FileUtils.copyDirectory(projectPath.toFile(), mutantPath.toFile());
        } catch (IOException e) {
            System.err.println("Error copying " + sourcePath + " to " + mutantPath + ": " + e.getMessage());
            return RunOutcome.setupFailed("Could not copy " + projectPath + " to " + mutantPath + ": " + e.getMessage());
        }
        System.out.println("Working directory created: " + mutantPath);

        try {

        System.out.println("\nRunning baseline tests");
        if (!command1.isEmpty()) {
            runCommand(command1, mutantPath.toString());
        }
        String baselineOutput = runTests(command2, mutantPath.toString());
        if (hasTestFailures(baselineOutput)) {
            System.err.println("ERROR: Tests fail on original code! Fix tests before running mutation testing.");
            printTimeoutHint(baselineOutput);
            System.err.println("Baseline output:\n" + baselineOutput);
            return RunOutcome.setupFailed("Tests fail on the original code. Fix them before running mutation testing.");
        }
        System.out.println("Baseline tests pass");

        Path sourceDir = mutantPath.resolve(sourcePath);
        CoverageData coverageData = loadCoverageData(mutantPath, sourceDir, coverageCommand, coverageReport);

        System.out.println("\nScanning for mutations...");

        List<MutationPoint> mutationPoints = scanForMutations(
                sourceDir, customMutationSpec, excludedPaths(mutantPath, excludeSources));
        System.out.println("Found " + mutationPoints.size() + " mutation points");

        if (mutationPoints.isEmpty()) {
            System.out.println("No mutations found. Exiting.");
            return RunOutcome.noMutants("No mutants were generated for " + sourceCode + ".");
        }

        // Loaded before testing so a cancel never waits on a git fetch.
        EquivalentsBranch.Snapshot equivalents = loadEquivalents(projectPath);

        System.out.println("\nTesting mutants");
        MutantRunner.Outcome tested = MutantRunner.run(
                mutationPoints, mutantPath, command1, command2, coverageData, workers, control, false);

        RunOutcome outcome = finishRun(tested, equivalents, control, "Generating report");
        System.out.println("\n Mutation testing complete");
        return outcome;
        } finally {
            // After a cancel the JVM is about to exit; spend the time on the report, not on deleting files.
            if (!control.isCancelled()) {
                cleanupWorkDirectories(outputRoot, mutantPath);
            }
        }
    }

    /**
     * Scores the run with equivalent mutants left out, writes the HTML report
     * (with those IDs embedded) and a copy of the IDs used next to it. Runs
     * before the working copy is deleted: mutant IDs read the mutated files.
     * A run that stopped early is scored over the mutants that were tested.
     */
    private static RunOutcome finishRun(MutantRunner.Outcome tested, EquivalentsBranch.Snapshot equivalents,
            RunControl control, String heading) {
        if (equivalents.source() != EquivalentsBranch.Source.NONE) {
            System.out.println("\nEquivalent mutants: " + equivalents.description());
        }

        System.out.println("\n" + heading);
        List<MutantResult> results = tested.results();
        MutationReport report = new MutationReport(results, equivalents.ids());
        if (tested.stoppedEarly()) {
            report.markPartial(tested.notTested(),
                    control.describeStop(results.size(), results.size() + tested.notTested()));
        }
        report.printReport();

        try {
            HtmlReportGenerator.generateHtmlReport(report, results, EquivalentsPageData.forRun(equivalents));
            Files.writeString(EQUIVALENTS_BACKUP, EquivalentsBranch.toEquivalentsJson(equivalents.ids()), StandardCharsets.UTF_8);
            System.out.println("HTML report generated at: output/report/mutation-report-demo.html");
        } catch (IOException e) {
            System.err.println("Failed to generate HTML report: " + e.getMessage());
        }
        RunStatus status = tested.stoppedEarly() ? RunStatus.STOPPED_EARLY : RunStatus.COMPLETED;
        return new RunOutcome(status, report, equivalents, null);
    }

    /**
     * Equivalent mutants come from the project's mutation-data branch when it
     * has one (fetched first in GitHub Actions; locally whatever was last
     * fetched is used, so a run never prompts for credentials). Otherwise the
     * local store the API server writes is used.
     */
    static EquivalentsBranch.Snapshot loadEquivalents(Path projectPath) {
        boolean inGitHubActions = "true".equals(System.getenv("GITHUB_ACTIONS"));
        EquivalentsBranch.Snapshot fromBranch = EquivalentsBranch.load(projectPath, inGitHubActions);
        if (fromBranch != null) {
            return fromBranch;
        }

        try {
            Set<String> local = EquivalentMutantStore.loadHiddenMutantIds();
            if (!local.isEmpty()) {
                return new EquivalentsBranch.Snapshot(local, EquivalentsBranch.Source.LOCAL_FILE,
                        EquivalentMutantStore.getStorePath() + " (" + local.size() + ")");
            }
        } catch (IOException e) {
            System.err.println("WARNING: Could not read " + EquivalentMutantStore.getStorePath() + ": " + e.getMessage());
        }
        return EquivalentsBranch.Snapshot.none();
    }

    private static void cleanupWorkDirectories(Path outputRoot, Path activeWorkdir) {
        File outputDirectory = outputRoot.toFile();
        File[] children = outputDirectory.listFiles();

        if (children == null) {
            return;
        }

        String activePath = activeWorkdir != null ? activeWorkdir.toAbsolutePath().normalize().toString() : null;
        for (File child : children) {
            if (!child.isDirectory() || !child.getName().startsWith(WORKING_DIR_PREFIX)) {
                continue;
            }

            String childPath = child.toPath().toAbsolutePath().normalize().toString();
            if (activePath != null && activePath.equals(childPath)) {
                continue;
            }

            try {
                FileUtils.deleteDirectory(child);
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temporary work directory " + child + ": " + e.getMessage());
            }
        }

        if (activeWorkdir != null) {
            try {
                FileUtils.deleteDirectory(activeWorkdir.toFile());
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temporary work directory " + activeWorkdir + ": " + e.getMessage());
            }
        }
    }

    private static CoverageData loadCoverageData(
            Path mutantPath,
            Path sourceDir,
            String coverageCommand,
            String coverageReport) {
        if (coverageReport == null || coverageReport.isBlank()) {
            return CoverageData.empty();
        }

        if (coverageCommand != null && !coverageCommand.isBlank()) {
            System.out.println("\nRunning coverage command");
            String coverageOutput = runCommand(coverageCommand, mutantPath.toString());
            if (hasTestFailures(coverageOutput)) {
                System.err.println("WARNING: Coverage command reported a failure; no-coverage classification disabled.");
                System.err.println("Coverage command output:\n" + coverageOutput);
                return CoverageData.empty();
            }
        }

        Path coveragePath = findCoverageReport(mutantPath, coverageReport);
        try {
            CoverageData coverageData = CoverageData.load(coveragePath);
            if (Files.exists(coveragePath)) {
                coverageData.includeSourceFiles(sourceDir);
            }
            if (coverageData.hasCoverage()) {
                System.out.println("Coverage report loaded: " + coveragePath);
            } else {
                System.err.println("WARNING: Coverage report had no line coverage data: " + coveragePath);
            }
            return coverageData;
        } catch (IOException e) {
            System.err.println("WARNING: Failed to load coverage report " + coveragePath + ": " + e.getMessage());
            return CoverageData.empty();
        }
    }

    private static Path findCoverageReport(Path mutantPath, String coverageReport) {
        Path configuredPath = mutantPath.resolve(coverageReport).normalize();
        if (Files.exists(configuredPath)) {
            return configuredPath;
        }

        Path parent = configuredPath.getParent();
        Path fileName = configuredPath.getFileName();
        if (parent == null || fileName == null || !Files.exists(parent)) {
            return configuredPath;
        }

        try (java.util.stream.Stream<Path> paths = Files.walk(parent)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().equals(fileName))
                    .findFirst()
                    .orElse(configuredPath);
        } catch (IOException e) {
            return configuredPath;
        }
    }

    static MutantResult testSingleMutant(
            MutationPoint point,
            Path workingDir,
            String compileCommand,
            String testCommand,
            CoverageData coverageData) {
        return testSingleMutant(point, point.getSourceFile(), workingDir, compileCommand, testCommand, coverageData, SAFETY);
    }

    static MutantResult testSingleMutant(
            MutationPoint point, Path workingDir, String compileCommand, String testCommand,
            CoverageData coverageData, SafetyController safety) {
        return testSingleMutant(point, point.getSourceFile(), workingDir, compileCommand, testCommand, coverageData, safety);
    }

    /** For a parallel worker, whose copy of the point's file is {@code originalFile}. */
    static MutantResult testSingleMutant(
            MutationPoint point, File originalFile, Path workingDir, String compileCommand, String testCommand,
            CoverageData coverageData) {
        return testSingleMutant(point, originalFile, workingDir, compileCommand, testCommand, coverageData, SAFETY);
    }

    /**
     * @param originalFile the file to mutate: the point's own file, or the same
     *                     file in a parallel worker's copy of the project
     */
    static MutantResult testSingleMutant(
            MutationPoint point, File originalFile, Path workingDir, String compileCommand, String testCommand,
            CoverageData coverageData, SafetyController safety) {
        String originalContent = null;
        boolean mutationWritten = false;
        SafetyAssessment safetyAssessment = null;

        try {
            if (isNoCoverageMutant(point, coverageData)) {
                return new MutantResult(
                        point,
                        MutantResult.MutantStatus.NO_COVERAGE,
                        "Skipped mutation execution: line was not covered by baseline coverage.",
                        true);
            }

            originalContent = Files.readString(originalFile.toPath());

            String mutatedContent = applyMutation(originalContent, point);
            if (mutatedContent.equals(originalContent)) {
                return new MutantResult(point, MutantResult.MutantStatus.COMPILE_ERROR,
                        "Mutation was not applied; the source location or expected text did not match.", false);
            }
            SafetyController.Outcome outcome;
            synchronized (safety) {   // parallel workers ask one at a time
                outcome = safety.decideWithAssessment(point, originalContent, mutatedContent,
                        compileCommand, testCommand);
            }
            safetyAssessment = outcome.assessment();
            SafetyAction action = outcome.action();
            if (action != SafetyAction.CONTINUE) {
                return new MutantResult(point,
                        action == SafetyAction.ABORT ? MutantResult.MutantStatus.SAFETY_ABORTED : MutantResult.MutantStatus.SAFETY_SKIPPED,
                        outcome.assessment().reason(), false, safetyAssessment);
            }
            mutationWritten = true;
            Files.writeString(originalFile.toPath(), mutatedContent);

            String compileOutput = "";
            if (!compileCommand.isEmpty()) {
                compileOutput = runCommand(compileCommand, workingDir.toString());
                if (hasTestFailures(compileOutput)) {
                    return new MutantResult(
                            point,
                            MutantResult.MutantStatus.COMPILE_ERROR,
                            compileOutput,
                            false, safetyAssessment);
                }
            }

            // A mutant that makes the tests hang is stopped at test_timeout_seconds
            // and counted as killed, as PIT does
            String testOutput = runTests(testCommand, workingDir.toString());

            boolean testsPass = !hasTestFailures(testOutput);

            MutantResult.MutantStatus status;
            if (testsPass) {
                status = MutantResult.MutantStatus.SURVIVED;
            } else {
                status = MutantResult.MutantStatus.KILLED;
            }

            return new MutantResult(point, status, testOutput, testsPass, safetyAssessment);

        } catch (IOException e) {
            return new MutantResult(
                    point,
                    MutantResult.MutantStatus.COMPILE_ERROR,
                    e.getMessage(),
                    false, safetyAssessment);
        } finally {
            if (mutationWritten) {
                try {
                    Path originalPath = originalFile.toPath();
                    Path parent = originalPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.writeString(originalPath, originalContent);
                } catch (IOException e) {
                    System.err.println("ERROR: Failed to restore " + originalFile + ": " + e.getMessage());
                }
            }
        }
    }

    private static boolean isNoCoverageMutant(MutationPoint point, CoverageData coverageData) {
        if (point == null || coverageData == null || !coverageData.hasCoverage()) {
            return false;
        }

        Path sourceFile = point.getSourceFile().toPath();
        return coverageData.hasCoverageForFile(sourceFile)
                && !coverageData.isLineCovered(sourceFile, point.getLineNumber());
    }

    static String applyMutation(String content, MutationPoint point) {
        if (point.getOperator().contains("\n")) {
            return applyMultiLineMutation(content, point);
        }

        String[] lines = content.split("\n", -1);
        int lineIndex = point.getLineNumber() - 1;

        if (lineIndex < 0 || lineIndex >= lines.length) {
            System.err.println("Warning: Line number " + point.getLineNumber() + " out of bounds");
            return content;
        }

        String line = lines[lineIndex];

        if (point.getColumnNumber() < 0 || point.getColumnNumber() > line.length()) {
            System.err.println("Warning: Column number " + point.getColumnNumber() + " out of bounds");
            return content;
        }

        if (!line.startsWith(point.getOperator(), point.getColumnNumber())) {
            System.err.println("Warning: Mutation text does not match the original source at " + point);
            return content;
        }

        String newLine = line.substring(0, point.getColumnNumber()) +
                point.getReplacement() +
                line.substring(Math.min(point.getColumnNumber() + point.getOperator().length(), line.length()));

        lines[lineIndex] = newLine;

        return String.join("\n", lines);
    }

    /**
     * Applies a mutation whose original text spans several lines (e.g. a
     * Racket (= a b) form wrapped in (not ...)). The original text must sit
     * exactly at the point's line/column, otherwise the content is unchanged.
     */
    private static String applyMultiLineMutation(String content, MutationPoint point) {
        int offset = 0;
        for (int line = 1; line < point.getLineNumber(); line++) {
            int newline = content.indexOf('\n', offset);
            if (newline < 0) {
                System.err.println("Warning: Line number " + point.getLineNumber() + " out of bounds");
                return content;
            }
            offset = newline + 1;
        }
        offset += point.getColumnNumber();

        if (!content.startsWith(point.getOperator(), offset)) {
            System.err.println("Warning: Mutation text not found at line "
                    + point.getLineNumber() + ", column " + point.getColumnNumber());
            return content;
        }
        return content.substring(0, offset)
                + point.getReplacement()
                + content.substring(offset + point.getOperator().length());
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    static boolean hasTestFailures(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }



        String lower = output.toLowerCase();

        // Check for explicit success indicators first
        if (lower.contains("all tests passed") || 
            lower.contains("status: pass") ||
            lower.contains("success rate: 100.0%")) {
            return false;  // Tests passed!
        }
        
        // Then check for failure patterns
        return lower.matches("(?s).*\\b[1-9]\\d*\\s+failure(s)?\\b.*")
                || lower.matches("(?s).*\\bfailure(s)?\\s*:\\s*[1-9]\\d*\\b.*")
                || lower.matches("(?s).*\\b[1-9]\\d*\\s+failed\\b.*")
                || lower.matches("(?s).*\\bfailed\\s*:\\s*[1-9]\\d*\\b.*")
                || lower.matches("(?s).*\\b[1-9]\\d*\\s+failing\\b.*")
                || lower.contains("0 tests passed, ")   // rackunit: "0 tests passed, N failed"
                || lower.contains("tests failed")       // rackunit summary variant
                || lower.contains("\nfailure\n")        // rackunit per-failure block header (lowercased)
                || lower.contains("check-equal? failed")
            || lower.contains("not recognized as an internal or external command")
            || lower.contains("commandnotfoundexception")
            || lower.contains("no module named pytest")
                || lower.contains("build failed")
                || lower.contains("build failure")
                || lower.contains("test run failed")
                || lower.contains("test run aborted")
                || lower.contains("socketexception")
                || lower.contains("permission denied")
                || lower.contains("process_exit_code:")
                || lower.contains("process_timeout")
                || lower.contains("process_execution_error")
                || lower.contains("type checker:")
                || lower.contains("type mismatch");
    }

    /** Every mutant the spec file's rules produce in the source files under sourceDir. */
    static List<MutationPoint> scanForMutations(Path sourceDir, String customMutationSpec) {
        return scanForMutations(sourceDir, customMutationSpec, List.of());
    }

    /** @param excluded absolute folders/files whose source files are never mutated */
    static List<MutationPoint> scanForMutations(Path sourceDir, String customMutationSpec, List<Path> excluded) {
        List<MutationPoint> points = new ArrayList<>();

        List<MutationRule> rules;
        // Rules each language's adapter can't apply, by file extension.
        Map<String, Set<String>> unsupported = new TreeMap<>();
        try {
            rules = loadCustomRules(customMutationSpec);
            System.out.println("  Loaded " + rules.size() + " custom mutation rules");
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            return points;
        }

        try {
            for (Iterator<File> it = FileUtils.iterateFiles(sourceDir.toFile(), null, true); it.hasNext();) {
                File file = it.next();
                if (!shouldScanFile(file) || isExcluded(file, excluded)) {
                    continue;
                }

                points.addAll(findCustomMutationsInFile(file, rules, unsupported));
            }
        } catch (Exception e) {
            System.err.println("Error scanning for mutations: " + e.getMessage());
            e.printStackTrace();
        }

        for (Map.Entry<String, Set<String>> entry : unsupported.entrySet()) {
            System.out.println("  Rules not applied to " + entry.getKey() + " files (the language adapter"
                    + " doesn't know a construct or placeholder they use):");
            for (String label : entry.getValue()) System.out.println("    - " + label);
        }
        return points;
    }

    /**
     * The exclude_source entries inside the working copy. An entry that doesn't
     * exist there is probably a typo, so it is reported rather than ignored.
     */
    static List<Path> excludedPaths(Path workingCopy, List<String> excludeSources) {
        List<Path> excluded = new ArrayList<>();
        for (String entry : excludeSources) {
            Path path = workingCopy.resolve(entry).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                System.out.println("Excluding from mutation: " + entry);
            } else {
                System.err.println("WARNING: exclude_source entry not found in the project: " + entry);
            }
            excluded.add(path);
        }
        return excluded;
    }

    private static boolean isExcluded(File file, List<Path> excluded) {
        if (excluded.isEmpty()) {
            return false;
        }
        // Path.startsWith compares whole folder names, so "ui" doesn't exclude "uikit".
        Path path = file.toPath().toAbsolutePath().normalize();
        return excluded.stream().anyMatch(path::startsWith);
    }

    /**
     * Loads a spec from disk or, when no file exists at that path, the copy
     * bundled in the jar at the same path under specs/ (specs/OperatorMutations.spec,
     * specs/catalog/pit.spec, ...), so a deployed jar works from any directory.
     */
    static List<MutationRule> loadCustomRules(String customMutationSpec) throws IOException {
        if (customMutationSpec == null || customMutationSpec.isEmpty()) {
            throw new IOException("Missing custom_mutation_spec in config file (the .spec file of mutation rules)");
        }

        File specFile = new File(customMutationSpec);
        if (specFile.exists()) {
            return MutationRuleGenerator.generateRules(specFile);
        }

        // The same path under specs/ (specs/catalog/pit.spec), else the file name directly under specs/.
        String path = specFile.toPath().normalize().toString().replace('\\', '/');
        String resource = path.startsWith("specs/") ? path : "specs/" + path;
        if (Main.class.getClassLoader().getResource(resource) == null) {
            resource = "specs/" + specFile.getName();
        }
        try (InputStream in = Main.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Spec file not found: " + customMutationSpec
                        + " (no file at that path, and no bundled spec of that name under specs/)");
            }
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            System.out.println("  Using the bundled " + resource);
            try {
                return MutationRuleGenerator.generateRules(lines);
            } catch (IOException e) {
                throw new IOException(specFile.getName() + ", " + e.getMessage(), e);
            }
        }
    }

    private static boolean shouldScanFile(File file) {
        String normalizedPath = file.getPath().replace('\\', '/');
        String fileName = file.getName();
        boolean supportedSource = fileName.endsWith(".java")
                || fileName.endsWith(".py")
                || fileName.endsWith(".js")
                || fileName.endsWith(".cs")
                || fileName.endsWith(".rkt");

        return supportedSource
                && !normalizedPath.contains("/obj/")
                && !normalizedPath.contains("/bin/")
                && !normalizedPath.contains("/.git/")
                && !normalizedPath.contains("/target/")
                && !normalizedPath.contains("/node_modules/");
    }

    private static List<MutationPoint> findCustomMutationsInFile(File file, List<MutationRule> rules,
                                                                  Map<String, Set<String>> unsupported) throws IOException {
        String fileName = file.getName();
        MSLScannerAdapter adapter = null;

        if (fileName.endsWith(".java")) {
            adapter = JavaMutationScanner.createAdapter(file);
        } else if (fileName.endsWith(".py")) {
            adapter = PythonMutationScanner.createAdapter(file);
        } else if (fileName.endsWith(".js")) {
            adapter = JavaScriptMutationScanner.createAdapter(file);
        } else if (fileName.endsWith(".cs")) {
            adapter = CSharpMutationScanner.createAdapter(file);
        } else if (fileName.endsWith(".rkt")) {
            adapter = RacketMutationScanner.createAdapter(file);
        }

        if (adapter == null) {
            return Collections.emptyList();
        }

        List<MutationPoint> points = new ArrayList<>();
        String extension = fileName.substring(fileName.lastIndexOf('.'));
        for (MutationRule rule : rules) {
            final MSLScannerAdapter language = adapter;
            if (!rule.getTrees().stream().allMatch(language::supports)) {
                unsupported.computeIfAbsent(extension, e -> new TreeSet<>()).add(rule.getLabel());
                continue;
            }
            points.addAll(rule.findMutationPoints(adapter, file));
        }
        return points;
    }
}
