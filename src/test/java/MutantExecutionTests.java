import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

public class MutantExecutionTests {

    private static boolean hasTestOutput(String testCommand, Path sourcePath, Path testPath) {
        // run tests
        String output;
        testCommand = testCommand.replace(sourcePath.toString(), testPath.toString());
        output = Main.runCommand(testCommand, testPath.toString());
        System.out.println(output);

        return !output.isEmpty();
    }

    @Test
    public void testRunCommandHasMavenOutput() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        assertTrue(hasTestOutput(testCommand, directory, directory));
    }

    @Test
    public void testRunCommandHasPytestOutput() {
        Path directory = Path.of("samples/PytestProject");
        String testCommand = "pytest ./tests/TestAdd.py";

        assertTrue(hasTestOutput(testCommand, directory, directory));
    }

    @Test
    public void testRunCommandHasRacketOutput() {
        Path directory = Path.of("samples/RacketProject");
        String testCommand = "Racket ./project/tests/racket-project.rkt";

        assertTrue(hasTestOutput(testCommand, directory, directory));
    }
/* No Sample Projects Yet
    @Test
    public void testRunCommandHasMavenOutput() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        assertTrue(hasTestOutput(testCommand, directory, directory));
    }

    @Test
    public void testRunCommandHasMavenOutput() {
        Path directory = Path.of("samples/MavenProject");
        String testCommand = "mvn test";

        assertTrue(hasTestOutput(testCommand, directory, directory));
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
    public void testRunCommandWithMavenMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        assertTrue(hasTestOutput(testCommand, sourcePath, testPath));

        mutantTearDown(testPath);
    }

    @Test
    public void testRunCommandWithPytestMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/PytestProject");
        Path sourcePath = Path.of("src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "pytest ./tests/TestAdd.py";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        assertTrue(hasTestOutput(testCommand, sourcePath, testPath));

        mutantTearDown(testPath);
    }

    @Test
    public void testRunCommandWithRacketMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/RacketProject");
        Path sourcePath = Path.of("project/src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "Racket ./project/tests/racket-project.rkt";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        hasTestOutput(testCommand, sourcePath, testPath);

        mutantTearDown(testPath);
    }

    /* No Sample Projects Yet
    @Test
    public void testRunCommandWithMavenMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        assertTrue(hasTestOutput(testCommand, sourcePath, testPath));

        mutantTearDown(testPath);
    }

    @Test
    public void testRunCommandWithMavenMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

        mutantSetup(projectPath, testPath, sourcePath, mutationType);

        assertTrue(hasTestOutput(testCommand, sourcePath, testPath));

        mutantTearDown(testPath);
    }
     */
}
