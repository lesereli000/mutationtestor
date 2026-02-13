import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

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
        assertFalse(output.isEmpty());

        String[] split = output.split("[1-9]\\d* [Ff]ailure\\(?s?\\)?|[Ff]ailure\\(?s?\\)?: [1-9]\\d*|[1-9]\\d* [Ff]ailed");
        assertEquals(expectFailure, split.length > 1);
    }

    @Test
    public void testRunCommandUnmutatedMavenProject() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        testOutput(testCommand, directory, directory, false);
    }

    @Test
    public void testRunCommandUnmutatedPytestProject() {
        Path directory = Path.of("samples/PytestProject");
        String testCommand = "pytest ./tests/TestAdd.py";

        testOutput(testCommand, directory, directory, false);
    }

    @Test
    public void testRunCommandUnmutatedRacketProject() {
        Path directory = Path.of("samples/RacketProject");
        String testCommand = "Racket ./project/tests/racket-project.rkt";

        testOutput(testCommand, directory, directory, false);
    }
/* No Sample Projects Yet
    @Test
    public void testRunCommandUnmutatedJSProject() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        testOutput(testCommand, directory, directory, 0);
    }

    @Test
    public void testRunCommandUnmutatedCSProject() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        testOutput(testCommand, directory, directory, 0);
    }
    */

    private static void mutantSetup(Path projectPath, Path testPath, Path sourcePath, Mutator.MutatorType mutationType) {
        // copy sample code into a sandbox folder
        try {
            FileUtils.copyDirectory(projectPath.toFile(), testPath.toFile());
        } catch (IOException e) {
            fail("Directory Setup Failed");
        }

        // mutate source code in sandbox folder
        try {
            Path mutantPath = testPath.resolve(sourcePath).normalize();
            for (Iterator<File> it = FileUtils.iterateFiles(mutantPath.toFile(), null, false); it.hasNext(); ) {
                File source = it.next();
                String mutatedCode = Mutator.mutate(source.toPath(), mutationType);
                Files.writeString(source.toPath(), mutatedCode);
            }
        } catch (Exception e) {
            fail("Mutation Failed");
        }
    }

    private static void mutantTearDown(Path testPath) {
        // delete copied directory
        try {
            FileUtils.deleteDirectory(testPath.toFile());
        } catch (IOException e) {
            fail("Directory Tear Down Failed");
        }
    }

    @Test
    public void testRunCommandWithMavenMutants() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    @Test
    public void testRunCommandWithPytestMutants() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/PytestProject");
        Path sourcePath = Path.of("src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "pytest ./tests/TestAdd.py";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    @Test
    public void testRunCommandWithRacketMutants() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/RacketProject");
        Path sourcePath = Path.of("project/src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "Racket ./project/tests/racket-project.rkt";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, true);

        mutantTearDown(testPath);
    }

    /* No Sample Projects Yet
    @Test
    public void testRunCommandWithJSMutants() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, 2);

        mutantTearDown(testPath);
    }

    @Test
    public void testRunCommandWithCSMutants() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        testOutput(testCommand, sourcePath, testPath, 2);

        mutantTearDown(testPath);
    }
     */
}
