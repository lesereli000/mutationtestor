package mutationtester;

import mutationtester.msl.*;

import static org.junit.jupiter.api.Assertions.*;
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
    void testEmptyOutput() {
        assertFalse(Main.hasTestFailures(""));
        assertFalse(Main.hasTestFailures(null));
    }

    @Test
    void testFalsePositive() {
        assertFalse(Main.hasTestFailures("There are 0 failures."));
        assertFalse(Main.hasTestFailures("No failures here."));
    }
}
