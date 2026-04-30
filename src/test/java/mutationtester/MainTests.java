package mutationtester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;

import org.junit.jupiter.api.Test;

public class MainTests {

    @Test
    void testMavenSuccess() {
        String output = "[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0\n[INFO] BUILD SUCCESS";
        assertFalse(Main.hasTestFailures(output));
    }

    @Test
    void testMavenFailure() {
        String output = "[INFO] Tests run: 5, Failures: 1, Errors: 0, Skipped: 0\n[INFO] BUILD FAILURE";
        assertTrue(Main.hasTestFailures(output));
    }

    @Test
    void testMavenCompilationError() {
        String output = "[ERROR] COMPILATION ERROR : \n[ERROR] /path/to/File.java:[10,5] ; expected\n[INFO] BUILD FAILURE";
        assertTrue(Main.hasTestFailures(output));
    }

    @Test
    void testNpmJestSuccess() {
        String output = "Test Suites: 1 passed, 1 total\nTests: 5 passed, 5 total\nSnapshots: 0 total\nTime: 1.2s";
        assertFalse(Main.hasTestFailures(output));
    }

    @Test
    void testNpmJestFailure() {
        String output = "Test Suites: 1 failed, 1 total\nTests: 4 passed, 1 failed, 5 total\nSnapshots: 0 total\nTime: 1.2s";
        assertTrue(Main.hasTestFailures(output));
    }

    @Test
    void testPytestSuccess() {
        String output = "======= 5 passed in 0.05s =======";
        assertFalse(Main.hasTestFailures(output));
    }

    @Test
    void testPytestFailure() {
        String output = "======= 4 passed, 1 failed in 0.05s =======";
        assertTrue(Main.hasTestFailures(output));
    }

    @Test
    void testDotnetSuccess() {
        String output = "Test Run Successful.\nTotal tests: 5\n     Passed: 5\n Total time: 1.2s";
        assertFalse(Main.hasTestFailures(output));
    }

    @Test
    void testDotnetFailure() {
        String output = "Test Run Failed.\nTotal tests: 5\n     Passed: 4\n     Failed: 1\n Total time: 1.2s";
        assertTrue(Main.hasTestFailures(output));
    }

    @Test
    void testRacketFailure() {
        String output = "1 failure(s)\n1 failure";
        assertTrue(Main.hasTestFailures(output));
    }

    @Test
    void testBuildFailedKeyword() {
        assertTrue(Main.hasTestFailures("Some error occurred. BUILD FAILED."));
    }

    @Test
    void testTestRunFailedKeyword() {
        assertTrue(Main.hasTestFailures("Critical error: Test run failed."));
    }

    @Test
    void testTestRunAbortedKeyword() {
        assertTrue(Main.hasTestFailures("Test Run Aborted."));
    }

    @Test
    void testPermissionDeniedDuringDotnetRun() {
        assertTrue(Main.hasTestFailures("System.Net.Sockets.SocketException (13): Permission denied"));
    }

    @Test
    void testNonZeroProcessExitCode() {
        assertTrue(Main.hasTestFailures("PROCESS_EXIT_CODE: 1"));
    }

    @Test
    void testEmptyOutput() {
        assertFalse(Main.hasTestFailures(""));
        assertFalse(Main.hasTestFailures(null));
    }

    @Test
    void testFalsePositive() {
        assertFalse(Main.hasTestFailures("There are 0 failures."));
        assertFalse(Main.hasTestFailures("No failures here."));
    }

    @Test
    void testScanForMutationsRecursesIntoNestedSourceDirectories() {
        List<MutationPoint> points = Main.scanForMutations(
                Path.of("samples/CSProject/src"),
                Mutator.MutatorType.CONDITIONAL_TRUE,
                "AUTO",
                null);

        assertTrue(points.stream().anyMatch(p -> p.getSourceFile().getName().equals("ConditionalDemo.cs")));
    }

    @Test
    void testReadConfigAcceptsCoverageSettings() throws Exception {
        Path config = Files.createTempFile("mutation-config", ".txt");
        Files.writeString(config, """
                source_code_directory=src
                mutator_type=AOR_PLUS_TO_MINUS
                compile_command=
                test_command=mvn test
                coverage_command=mvn test jacoco:report
                coverage_report=target/site/jacoco/jacoco.xml
                """);

        Main.Config parsed = Main.readConfig(config.toFile());

        assertTrue(parsed.coverageCommand.equals("mvn test jacoco:report"));
        assertTrue(parsed.coverageReport.equals("target/site/jacoco/jacoco.xml"));
    }
}
