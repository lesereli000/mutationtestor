package mutationtester;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/** Each sample project's own test suite passes before any mutant is applied. */
public class SampleProjectsTests {

    private static void testOutput(String testCommand, Path directory, boolean expectFailure) {
        String output = Main.runCommand(testCommand, directory.toString());

        // grade output
        assertFalse(output.isEmpty(), "Test output should not be empty for command: " + testCommand);
        // Puppeteer's Chrome is optional (the setup script skips it with --minimal).
        assumeFalse(output.contains("Could not find Chrome"),
                "Skipping: Puppeteer's Chrome is not installed. Run 'npx puppeteer browsers install chrome' in samples/JSProject.");
        assertEquals(expectFailure, Main.hasTestFailures(output),
                "Failure detection mismatch for: " + testCommand + "\nOutput:\n" + output);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedMavenProject() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        testOutput(testCommand, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedPytestProject() {
        Path directory = Path.of("samples/PytestProject");
        String testCommand = "python3 -m pytest ./tests/TestAdd.py";
        testOutput(testCommand, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedRacketProject() {
        Path directory = Path.of("samples/RacketProject");
        String testCommand = "racket ./project/tests/racket-project.rkt";

        testOutput(testCommand, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedJSProject() {
        Path directory = Path.of("samples/JSProject");
        String testCommand = "cd browser-tests && node run-with-puppeteer.js";

        testOutput(testCommand, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedCSProject() {
        Path directory = Path.of("samples/CSProject");
        String testCommand = "dotnet test tests/CSProject.Tests/CSProject.Tests.csproj";

        testOutput(testCommand, directory, false);
    }
}
