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
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.equivalent.EquivalentMutantStore;
import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.report.MutationReport;

/** Exit codes, min_mutation_score, bundled specs and branch equivalents. */
public class CiIntegrationTests {

    @TempDir
    Path tmp;

    // ---- config ----

    @Test
    void minMutationScoreIsOptional() throws Exception {
        assertNull(Main.readConfig(config("").toFile()).minMutationScore);
        assertEquals(72.5, Main.readConfig(config("min_mutation_score=72.5").toFile()).minMutationScore);
        assertEquals(0.0, Main.readConfig(config("min_mutation_score=0").toFile()).minMutationScore);
        assertEquals(100.0, Main.readConfig(config("min_mutation_score=100").toFile()).minMutationScore);
    }

    @Test
    void minMutationScoreMustBeFrom0To100() {
        for (String bad : List.of("abc", "-5", "101", "NaN", "Infinity", "70%")) {
            IOException e = assertThrows(IOException.class,
                    () -> Main.readConfig(config("min_mutation_score=" + bad).toFile()), bad);
            assertTrue(e.getMessage().contains("min_mutation_score"), e.getMessage());
        }
    }

    @Test
    void unknownMutatorTypesAreRejectedUpFront() throws Exception {
        Path cfg = tmp.resolve("bad.config");
        Files.writeString(cfg, "source_code_directory=src\nmutator_type=AOR_PLUS_TO_MINUS, NOPE\ntest_command=x\n");
        IOException e = assertThrows(IOException.class, () -> Main.readConfig(cfg.toFile()));
        assertTrue(e.getMessage().contains("NOPE"));

        Files.writeString(cfg, "source_code_directory=src\nmutator_type=ALL\ntest_command=x\n");
        assertEquals("ALL", Main.readConfig(cfg.toFile()).mutatorType);
    }

    // ---- exit codes and gate ----

    @Test
    void setupFailuresExitWith1() {
        Main.RunOutcome failed = Main.RunOutcome.setupFailed("broken");
        assertEquals(Main.EXIT_SETUP_FAILED, Main.exitCodeFor(failed, null));
        assertEquals(Main.EXIT_SETUP_FAILED, Main.exitCodeFor(failed, 50.0));
    }

    @Test
    void noMutantsOnlyFailsWithAThreshold() {
        Main.RunOutcome none = Main.RunOutcome.noMutants("nothing");
        assertEquals(Main.EXIT_OK, Main.exitCodeFor(none, null));
        assertEquals(Main.EXIT_SETUP_FAILED, Main.exitCodeFor(none, 50.0));
        assertTrue(Main.gateLine(none, 50.0).contains("no mutants to score"));
    }

    @Test
    void scoreIsComparedAsPrinted() {
        assertEquals(Main.EXIT_OK, Main.exitCodeFor(completed(3, 1), null));
        assertEquals(Main.EXIT_OK, Main.exitCodeFor(completed(3, 1), 70.0));     // 75.0%
        assertEquals(Main.EXIT_OK, Main.exitCodeFor(completed(7, 3), 70.0));     // exactly 70.0%
        assertEquals(Main.EXIT_OK, Main.exitCodeFor(completed(1749, 751), 70.0)); // 69.96% prints as 70.0%
        assertEquals(Main.EXIT_BELOW_THRESHOLD, Main.exitCodeFor(completed(1, 1), 70.0));
        assertEquals(Main.EXIT_SETUP_FAILED, Main.exitCodeFor(completed(0, 0), 70.0)); // all equivalent

        assertEquals("MUTATION SCORE GATE: PASSED (75.0% >= 70.0%)", Main.gateLine(completed(3, 1), 70.0));
        assertEquals("MUTATION SCORE GATE: FAILED (50.0% < 70.0%)", Main.gateLine(completed(1, 1), 70.0));
        assertNull(Main.gateLine(completed(1, 1), null));
    }

    @Test
    void stepSummaryShowsScoreGateAndSurvivors() {
        String md = Main.buildStepSummary(completed(1, 2), 70.0, Main.EXIT_BELOW_THRESHOLD);
        assertTrue(md.contains("**Mutation score: 33.3%** (minimum 70.0%) ❌"), md);
        assertTrue(md.contains("| 1 | 2 | 0 | 0 | 3 |"), md);
        assertTrue(md.contains("- `Fake.java:2` `+` → `-`"), md);

        String failed = Main.buildStepSummary(Main.RunOutcome.setupFailed("Tests fail\non the original code."), null, 1);
        assertTrue(failed.contains("**Did not run:** Tests fail on the original code."), failed);
    }

    // ---- bundled specs ----

    @Test
    void customSpecFallsBackToTheBundledCopy() throws Exception {
        assertFalse(Main.loadCustomRules("no/such/dir/OperatorMutations.txt").isEmpty());
        assertFalse(Main.loadCustomRules("StatementMutation.txt").isEmpty());
        IOException e = assertThrows(IOException.class, () -> Main.loadCustomRules("Nope.txt"));
        assertTrue(e.getMessage().contains("Nope.txt"));
        assertThrows(IOException.class, () -> Main.loadCustomRules(null));
    }

    // ---- run() end to end ----

    @Test
    void usageAndConfigErrorsExitWith1() throws Exception {
        assertEquals(Main.EXIT_SETUP_FAILED, Main.run(new String[0]));
        assertEquals(Main.EXIT_SETUP_FAILED, Main.run(new String[] { tmp.toString(), tmp.resolve("missing.config").toString() }));

        Path cfg = tmp.resolve("spec.config");
        Files.writeString(cfg, "source_code_directory=src\nmutator_type=CUSTOM\ncustom_mutation_spec=Nope.txt\ntest_command=x\n");
        assertEquals(Main.EXIT_SETUP_FAILED, Main.run(new String[] { tmp.toString(), cfg.toString() }));
    }

    @Test
    void passingRunWritesTheReportAndBackup() throws Exception {
        Path project = sampleProject(true);
        assertEquals(Main.EXIT_OK, Main.run(new String[] { project.toString(), runConfig(project, "0").toString() }));

        assertTrue(Files.exists(Path.of("output", "report", "mutation-report-demo.html")));
        String survivors = Files.readString(Path.of("output", "report", "all-survivors.html"));
        assertTrue(survivors.contains("id=\"equivalents-data\""));
        assertTrue(survivors.contains("\"source\":\"branch\""));
        assertEquals(Set.of(), EquivalentsBranch.parseEquivalentsFile(
                Files.readString(Path.of("output", "report", "equivalents.json"))));
    }

    @Test
    void lowScoreExitsWith2AndStillWritesTheReport() throws Exception {
        Path project = sampleProject(true);
        Files.deleteIfExists(Path.of("output", "report", "all-survivors.html"));

        assertEquals(Main.EXIT_BELOW_THRESHOLD, Main.run(new String[] { project.toString(), runConfig(project, "100").toString() }));
        assertTrue(Files.exists(Path.of("output", "report", "all-survivors.html")));
    }

    @Test
    void equivalentsFromTheBranchRaiseTheScore() throws Exception {
        Path project = sampleProject(true);
        String survivorId = EquivalentMutantStore.buildMutantId(new MutationPoint(
                project.resolve("src").resolve("Calc.java").toFile(), 7, 17, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS));
        // Left in pending/ on purpose: the run applies batches the sync step hasn't folded yet.
        commitToDataBranch(project, "mark", "pending/1.json",
                "{\"add\": [\"" + survivorId.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]}");

        assertEquals(Main.EXIT_OK, Main.run(new String[] { project.toString(), runConfig(project, "100").toString() }));
        assertEquals(Set.of(survivorId), EquivalentsBranch.parseEquivalentsFile(
                Files.readString(Path.of("output", "report", "equivalents.json"))));
    }

    @Test
    void failingBaselineExitsWith1() throws Exception {
        Path project = sampleProject(false);
        assertEquals(Main.EXIT_SETUP_FAILED, Main.run(new String[] { project.toString(), runConfig(project, "0").toString() }));
    }

    // ---- helpers ----

    private Path config(String extraLine) throws IOException {
        Path cfg = Files.createTempFile(tmp, "test", ".config");
        Files.writeString(cfg, "source_code_directory=src\nmutator_type=AOR_PLUS_TO_MINUS\ntest_command=x\n" + extraLine + "\n");
        return cfg;
    }

    /** n killed and m survived mutants, all on Fake.java line 2. */
    private static Main.RunOutcome completed(int killed, int survived) {
        List<MutantResult> results = new ArrayList<>();
        for (int i = 0; i < killed + survived; i++) {
            MutationPoint point = new MutationPoint(new File("Fake.java"), 2, 4, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
            results.add(new MutantResult(point,
                    i < killed ? MutantResult.MutantStatus.KILLED : MutantResult.MutantStatus.SURVIVED, "", i >= killed));
        }
        return new Main.RunOutcome(Main.RunStatus.COMPLETED, new MutationReport(results), EquivalentsBranch.Snapshot.none(), null);
    }

    /**
     * Two AOR mutants: "a + b" is checked by the "tests" (killed), "x + 1" is
     * not (survives), so the score is 50%. The tests are a single-file Java
     * program, which runs anywhere the JDK does. The project is a git repo
     * with a mutation-data branch so a local equivalents store is never used.
     */
    private Path sampleProject(boolean testsPass) throws Exception {
        Path project = GitTestRepos.init(Files.createTempDirectory(tmp, "project"));
        Files.createDirectories(project.resolve("src"));
        Files.writeString(project.resolve("src").resolve("Calc.java"), """
                public class Calc {
                    public static int add(int a, int b) {
                        return a + b;
                    }

                    public static int next(int x) {
                        return x + 1;
                    }
                }
                """);
        Files.writeString(project.resolve("Check.java"), """
                import java.nio.file.Files;
                import java.nio.file.Path;

                public class Check {
                    public static void main(String[] args) throws Exception {
                        String source = Files.readString(Path.of("src", "Calc.java"));
                        if (%s || !source.contains("return a + b;")) {
                            System.out.println("add is broken");
                            System.exit(1);
                        }
                        System.out.println("ok");
                    }
                }
                """.formatted(testsPass ? "false" : "true"));
        git(project, "add", "-A");
        git(project, "commit", "--quiet", "-m", "init");
        commitToDataBranch(project, "data", EquivalentsBranch.FILE, EquivalentsBranch.toEquivalentsJson(List.of()));
        return project;
    }

    private Path runConfig(Path project, String minScore) throws IOException {
        Path cfg = project.resolveSibling(project.getFileName() + ".config");
        Files.writeString(cfg, "source_code_directory=src\nmutator_type=AOR_PLUS_TO_MINUS\n"
                + "test_command=java Check.java\nmin_mutation_score=" + minScore + "\n");
        return cfg;
    }
}
