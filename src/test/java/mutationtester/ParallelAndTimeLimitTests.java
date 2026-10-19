package mutationtester;

import static mutationtester.GitTestRepos.commitToDataBranch;
import static mutationtester.GitTestRepos.git;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.coverage.CoverageData;
import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.report.MutationReport;

/** Parallel workers (GitHub Actions only) and stopping early with a partial report. */
public class ParallelAndTimeLimitTests {

    @TempDir
    Path tmp;

    // ---- worker count ----

    @Test
    void workersAreOneOutsideGitHubActions() {
        assertEquals(1, Main.workerCount(Map.of()));
        assertEquals(1, Main.workerCount(Map.of("MUTATION_WORKERS", "4")));
        assertEquals(1, Main.workerCount(Map.of("GITHUB_ACTIONS", "false", "MUTATION_WORKERS", "4")));
    }

    @Test
    void workersDefaultToOneInGitHubActions() {
        assertEquals(1, Main.workerCount(Map.of("GITHUB_ACTIONS", "true")));
        assertEquals(1, Main.workerCount(Map.of("GITHUB_ACTIONS", "true", "MUTATION_WORKERS", " ")));
    }

    @Test
    void workersComeFromMutationWorkersCappedAtFour() {
        assertEquals(3, Main.workerCount(Map.of("GITHUB_ACTIONS", "true", "MUTATION_WORKERS", "3")));
        assertEquals(4, Main.workerCount(Map.of("GITHUB_ACTIONS", "true", "MUTATION_WORKERS", "16")));
        for (String bad : List.of("0", "-2", "two", "1.5")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                    () -> Main.workerCount(Map.of("GITHUB_ACTIONS", "true", "MUTATION_WORKERS", bad)), bad);
            assertTrue(e.getMessage().contains("MUTATION_WORKERS"));
        }
    }

    // ---- time limit ----

    @Test
    void timeLimitConfigIsOptionalAndPositive() throws Exception {
        assertNull(Main.readConfig(config("").toFile()).timeLimitMinutes);
        assertEquals(45.0, Main.readConfig(config("time_limit_minutes=45").toFile()).timeLimitMinutes);
        assertEquals(0.5, Main.readConfig(config("time_limit_minutes=0.5").toFile()).timeLimitMinutes);
        for (String bad : List.of("0", "-1", "abc", "NaN", "Infinity")) {
            IOException e = assertThrows(IOException.class,
                    () -> Main.readConfig(config("time_limit_minutes=" + bad).toFile()), bad);
            assertTrue(e.getMessage().contains("time_limit_minutes"), e.getMessage());
        }
    }

    @Test
    void deadlineCountsFromTheStartAndCancelWins() {
        AtomicLong now = new AtomicLong(1_000);
        RunControl control = new RunControl(1.0, now::get, 0); // deadline at 60 000 ms

        assertFalse(control.shouldStop());
        now.set(60_000);
        assertTrue(control.shouldStop());
        assertEquals(RunControl.StopReason.TIME_LIMIT, control.stopReason());
        assertEquals("stopped after 3 of 10 mutants (time limit of 1 min reached)", control.describeStop(3, 10));

        control.cancel();
        assertTrue(control.isCancelled());
        assertEquals("stopped after 3 of 10 mutants (the run was cancelled)", control.describeStop(3, 10));
        assertFalse(RunControl.unlimited().shouldStop());
    }

    @Test
    void stoppedEarlyExitsWith3AndSkipsTheGate() {
        Main.RunOutcome partial = partialOutcome();
        assertEquals(Main.EXIT_STOPPED_EARLY, Main.exitCodeFor(partial, null));
        assertEquals(Main.EXIT_STOPPED_EARLY, Main.exitCodeFor(partial, 10.0));
        assertTrue(Main.gateLine(partial, 70.0).contains("NOT CHECKED"));

        String md = Main.buildStepSummary(partial, 70.0, Main.EXIT_STOPPED_EARLY);
        assertTrue(md.contains("**Partial run:** stopped after 2 of 5 mutants (time limit of 1 min reached). 3 mutants were not tested"), md);
        assertTrue(md.contains("Mutation score (tested mutants): 50.0%"), md);
    }

    // ---- MutantRunner ----

    @Test
    void parallelWorkersGiveTheSameResultsInTheSameOrder() throws Exception {
        Path project = sampleProject(tmp.resolve("workdir-1"), 6, false);
        List<MutationPoint> points = scan(project);
        assertEquals(6, points.size());

        MutantRunner.Outcome sequential = MutantRunner.run(points, project, "", "java Check.java",
                CoverageData.empty(), 1, RunControl.unlimited(), false);
        MutantRunner.Outcome parallel = MutantRunner.run(points, project, "", "java Check.java",
                CoverageData.empty(), 3, RunControl.unlimited(), false);

        assertEquals(statuses(sequential), statuses(parallel));
        assertEquals(1, statuses(parallel).stream().filter(s -> s == MutantResult.MutantStatus.KILLED).count());
        for (int i = 0; i < points.size(); i++) {
            assertTrue(parallel.results().get(i).getMutationPoint() == points.get(i), "results keep the original order");
        }
        assertEquals(0, parallel.notTested());
        assertNull(parallel.stopReason());

        try (Stream<Path> siblings = Files.list(tmp)) {
            assertEquals(List.of("workdir-1"), siblings.map(p -> p.getFileName().toString()).toList(),
                    "worker copies are deleted");
        }
        assertTrue(Files.readString(project.resolve("src").resolve("Calc.java")).contains("return a + b;"),
                "the main copy is left unmutated");
    }

    @Test
    void noNewMutantsStartAfterTheDeadline() throws Exception {
        Path project = sampleProject(tmp.resolve("workdir-2"), 4, false);
        List<MutationPoint> points = scan(project);
        // Each check of the clock moves it forward 50 ms; the 60 ms limit passes on the second check.
        AtomicLong now = new AtomicLong();
        RunControl control = new RunControl(0.001, () -> now.addAndGet(50), 0);

        MutantRunner.Outcome outcome = MutantRunner.run(points, project, "", "java Check.java",
                CoverageData.empty(), 1, control, false);

        assertEquals(1, outcome.results().size());
        assertEquals(3, outcome.notTested());
        assertEquals(RunControl.StopReason.TIME_LIMIT, outcome.stopReason());
        assertTrue(outcome.stoppedEarly());
    }

    // ---- end to end ----

    @Test
    void timeLimitWritesAPartialReportAndExitsWith3() throws Exception {
        Path project = sampleProject(Files.createTempDirectory(tmp, "project"), 3, true);
        Path cfg = runConfig(project, "time_limit_minutes=0.001\nmin_mutation_score=0\n");

        assertEquals(Main.EXIT_STOPPED_EARLY, Main.run(new String[] { project.toString(), cfg.toString() }));

        String summaryPage = Files.readString(Path.of("output", "report", "mutation-report-demo.html"));
        assertTrue(summaryPage.contains("Partial report:"), "banner on the summary page");
        assertTrue(summaryPage.contains("time limit of 0.001 min reached"), summaryPage);
        assertTrue(Files.exists(Path.of("output", "report", "equivalents.json")));
    }

    /**
     * GitHub cancels a job with SIGINT/SIGTERM; the tool should still leave a
     * partial report. Linux only: on Windows, Process.destroy() ends the JVM
     * without running shutdown hooks, so there is nothing to test there.
     */
    @Test
    @EnabledOnOs(OS.LINUX)
    void cancellingWritesAPartialReport() throws Exception {
        Path project = sampleProject(Files.createTempDirectory(tmp, "project"), 6, true);
        // Slow tests, so the cancel arrives while mutants are still waiting.
        Files.writeString(project.resolve("Check.java"), Files.readString(project.resolve("Check.java"))
                .replace("System.out.println(\"ok\");", "Thread.sleep(1500); System.out.println(\"ok\");"));
        Path cfg = runConfig(project, "");
        Path runDir = Files.createDirectories(tmp.resolve("run"));
        Path log = tmp.resolve("run.log");

        Process tool = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                "mutationtester.Main", project.toString(), cfg.toString())
                .directory(runDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        long waitUntil = System.currentTimeMillis() + 120_000;
        while (!Files.readString(log).contains("[2/6]") && tool.isAlive() && System.currentTimeMillis() < waitUntil) {
            Thread.sleep(200);
        }
        assertTrue(tool.isAlive(), "the run should still be going:\n" + Files.readString(log));
        tool.destroy(); // SIGTERM
        assertTrue(tool.waitFor(30, TimeUnit.SECONDS));

        String output = Files.readString(log);
        Path report = runDir.resolve("output").resolve("report").resolve("mutation-report-demo.html");
        assertTrue(Files.exists(report), output);
        assertTrue(Files.readString(report).contains("the run was cancelled"), output);
    }

    // ---- helpers ----

    private Path config(String extraLine) throws IOException {
        Path cfg = Files.createTempFile(tmp, "test", ".config");
        Files.writeString(cfg, "source_code_directory=src\nmutator_type=AOR_PLUS_TO_MINUS\ntest_command=x\n" + extraLine + "\n");
        return cfg;
    }

    private Path runConfig(Path project, String extra) throws IOException {
        Path cfg = project.resolveSibling(project.getFileName() + ".config");
        Files.writeString(cfg, "source_code_directory=src\nmutator_type=AOR_PLUS_TO_MINUS\ntest_command=java Check.java\n" + extra);
        return cfg;
    }

    private static List<MutationPoint> scan(Path project) {
        return Main.scanForMutations(project.resolve("src"), Mutator.MutatorType.AOR_PLUS_TO_MINUS, null);
    }

    private static List<MutantResult.MutantStatus> statuses(MutantRunner.Outcome outcome) {
        return outcome.results().stream().map(MutantResult::getStatus).toList();
    }

    private static Main.RunOutcome partialOutcome() {
        List<MutantResult> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            MutationPoint point = new MutationPoint(new File("Fake.java"), 2, 4, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
            results.add(new MutantResult(point,
                    i == 0 ? MutantResult.MutantStatus.KILLED : MutantResult.MutantStatus.SURVIVED, "", i != 0));
        }
        MutationReport report = new MutationReport(results);
        report.markPartial(3, "stopped after 2 of 5 mutants (time limit of 1 min reached)");
        return new Main.RunOutcome(Main.RunStatus.STOPPED_EARLY, report, EquivalentsBranch.Snapshot.none(), null);
    }

    /**
     * {@code count} AOR mutants: "a + b" is checked by the single-file Java
     * "tests" (killed), the "x + n" ones are not (survive). As a git repo with
     * a mutation-data branch, a run never falls back to a local equivalents file.
     */
    private static Path sampleProject(Path dir, int count, boolean asGitRepo) throws Exception {
        Files.createDirectories(dir.resolve("src"));
        StringBuilder calc = new StringBuilder("public class Calc {\n    public static int add(int a, int b) {\n        return a + b;\n    }\n");
        for (int i = 1; i < count; i++) {
            calc.append("\n    public static int plus").append(i).append("(int x) {\n        return x + ").append(i).append(";\n    }\n");
        }
        calc.append("}\n");
        Files.writeString(dir.resolve("src").resolve("Calc.java"), calc.toString());
        Files.writeString(dir.resolve("Check.java"), """
                import java.nio.file.Files;
                import java.nio.file.Path;

                public class Check {
                    public static void main(String[] args) throws Exception {
                        if (!Files.readString(Path.of("src", "Calc.java")).contains("return a + b;")) {
                            System.out.println("add is broken");
                            System.exit(1);
                        }
                        System.out.println("ok");
                    }
                }
                """);
        if (asGitRepo) {
            GitTestRepos.init(dir);
            git(dir, "add", "-A");
            git(dir, "commit", "--quiet", "-m", "init");
            commitToDataBranch(dir, "data", EquivalentsBranch.FILE, EquivalentsBranch.toEquivalentsJson(List.of()));
        }
        return dir;
    }
}
