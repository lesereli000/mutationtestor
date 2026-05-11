package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

public class MutantExecutionTests {

    private static void testOutput(String testCommand, Path sourcePath, Path testPath, boolean expectFailure) {
        // run tests
        String output;
        testCommand = testCommand.replace(sourcePath.toString(), testPath.toString());
        output = Main.runCommand(testCommand, testPath.toString());

        // grade output
        assertFalse(output.isEmpty(), "Test output should not be empty for command: " + testCommand);
        assertEquals(expectFailure, Main.hasTestFailures(output),
                "Failure detection mismatch for: " + testCommand + "\nOutput:\n" + output);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedMavenProject() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        testOutput(testCommand, directory, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedPytestProject() {
        Path directory = Path.of("samples/PytestProject");
        String testCommand = "pytest ./tests/TestAdd.py";
        testOutput(testCommand, directory, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedRacketProject() {
        Path directory = Path.of("samples/RacketProject");
        String testCommand = "racket ./project/tests/racket-project.rkt";

        testOutput(testCommand, directory, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedJSProject() {
        Path directory = Path.of("samples/JSProject");
        String testCommand = "npm test";

        testOutput(testCommand, directory, directory, false);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandUnmutatedCSProject() {
        Path directory = Path.of("samples/CSProject");
        String testCommand = "dotnet test tests/CSProject.Tests/CSProject.Tests.csproj";

        testOutput(testCommand, directory, directory, false);
    }

    private static void mutantSetup(Path projectPath, Path testPath, Path sourcePath,
            Mutator.MutatorType mutationType) {
        // copy sample code into a sandbox folder
        try {
            if (Files.exists(testPath)) {
                FileUtils.deleteDirectory(testPath.toFile());
            }

            FileUtils.copyDirectory(projectPath.toFile(), testPath.toFile());
        } catch (IOException e) {
            fail("Directory Setup Failed: " + e.getMessage());
        }

        try {
            Path mutantPath = testPath.resolve(sourcePath).normalize();
            String[] extensions = { "java", "cs", "js", "py", "rkt" };
            for (Iterator<File> it = FileUtils.iterateFiles(mutantPath.toFile(), extensions, true); it.hasNext();) {
                File source = it.next();
                try {
                    String mutatedCode = Mutator.mutate(source.toPath(), mutationType);
                    Files.writeString(source.toPath(), mutatedCode);
                } catch (Exception e) {
                    System.err.println("Skipping mutation for " + source.getName() + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail("Mutant Setup Failed: " + e.getMessage());
        }
    }

    private static void mutantTearDown(Path testPath) {
        // delete copied directory
        try {
            FileUtils.deleteDirectory(testPath.toFile());
        } catch (IOException e) {
            fail("Directory Tear Down Failed: " + e.getMessage());
        }
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithMavenMutants() {
        Path testPath = Path.of("output/TestDir/MavenProject");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithPytestMutants() {
        Path testPath = Path.of("output/TestDir/PytestProject");
        Path projectPath = Path.of("samples/PytestProject");
        Path sourcePath = Path.of("src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "pytest ./tests/TestAdd.py";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithRacketMutants() {
        Path testPath = Path.of("output/TestDir/RacketProject");
        Path projectPath = Path.of("samples/RacketProject");
        Path sourcePath = Path.of("project/src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "Racket ./project/tests/racket-project.rkt";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithJSMutants() {
        Path testPath = Path.of("output/TestDir/JSProject");
        Path projectPath = Path.of("samples/JSProject");
        Path sourcePath = Path.of("src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "npm test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithCSMutants() {
        Path testPath = Path.of("output/TestDir/CSProject");
        Path projectPath = Path.of("samples/CSProject");
        Path sourcePath = Path.of("src/CSProject");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "dotnet test tests/CSProject.Tests/CSProject.Tests.csproj";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    @AfterAll
    public static void tearDown() {
        Path testDirectory = Path.of("output/TestDir");
        try {
            if (Files.exists(testDirectory)) {
                FileUtils.deleteDirectory(testDirectory.toFile());
            }
        } catch (Exception e) {
            fail("Final tear down failed: " + e.getMessage());
        }
    }


    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithRacketCalculatorMultiplyMutants() {
        Path testPath = Path.of("output/TestDir/RacketCalculator_Multiply");
        Path projectPath = Path.of("samples/RacketCalculator");
        Path sourcePath = Path.of("calculator/src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE;
        String testCommand = "racket ./calculator/tests/all-tests.rkt";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);
        testOutput(testCommand, sourcePath, testPath, true);
        mutantTearDown(testPath);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithRacketCalculatorNegateConditionalsMutants() {
        Path testPath = Path.of("output/TestDir/RacketCalculator_NegateConditionals");
        Path projectPath = Path.of("samples/RacketCalculator");
        Path sourcePath = Path.of("calculator/src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.NEGATE_CONDITIONALS;
        String testCommand = "racket ./calculator/tests/all-tests.rkt";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);
        testOutput(testCommand, sourcePath, testPath, true);
        mutantTearDown(testPath);
    }

    @Test
    @Execution(ExecutionMode.CONCURRENT)
    public void testRunCommandWithRacketCalculatorDivideMutants() {
        Path testPath = Path.of("output/TestDir/RacketCalculator_Divide");
        Path projectPath = Path.of("samples/RacketCalculator");
        Path sourcePath = Path.of("calculator/src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY;
        String testCommand = "racket ./calculator/tests/all-tests.rkt";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);
        testOutput(testCommand, sourcePath, testPath, true);
        mutantTearDown(testPath);
    }
}
