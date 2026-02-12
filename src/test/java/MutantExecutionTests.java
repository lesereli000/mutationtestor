import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.*;

public class MutantExecutionTests {

    @Test
    public void testRunCommandHasMavenOutput() {
        String directory = "samples/MavenProject";
        String testCommand = "mvn test";

        String output = Main.runCommand(testCommand, directory);
        assertFalse(output.isEmpty());
    }

    @Test
    public void testRunCommandHasPytestOutput() {
        String directory = "samples/PytestProject";
        String testCommand = "pytest ./tests/TestAdd.py";

        String output = Main.runCommand(testCommand, directory);
        assertFalse(output.isEmpty());
    }

    @Test
    public void testRunCommandHasRacketOutput() {
        String directory = "samples/RacketProject";
        String testCommand = "Racket ./project/tests/racket-project.rkt";

        String output = Main.runCommand(testCommand, directory);
        assertFalse(output.isEmpty());
    }
/* No sample Projects yet
    @Test
    public void testRunCommandHasJSOutput() {
        String directory = "samples/MavenProject";
        String testCommand = "mvn test";

        String output = Main.runCommand(testCommand, directory);
        assertFalse(output.isEmpty());
        System.out.println(output);
    }

    @Test
    public void testRunCommandHasCSOutput() {
        String directory = "samples/MavenProject";
        String testCommand = "mvn test";

        String output = Main.runCommand(testCommand, directory);
        assertFalse(output.isEmpty());
        System.out.println(output);
    } */

    @Test
    public void testRunCommandWithMavenMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

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

        // run tests
        String output;
        testCommand = testCommand.replace(sourcePath.toString(), testPath.toString());
        output = Main.runCommand(testCommand, testPath.toString());
        System.out.println(output);

        assertFalse(output.isEmpty());

        try {
            FileUtils.deleteDirectory(testPath.toFile());
        } catch (IOException e) {
            fail("Directory Tear Down Failed");
        }
    }

    @Test
    public void testRunCommandWithPytestMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/PytestProject");
        Path sourcePath = Path.of("src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "pytest ./tests/TestAdd.py";

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

        // run tests
        String output;
        testCommand = testCommand.replace(sourcePath.toString(), testPath.toString());
        output = Main.runCommand(testCommand, testPath.toString());
        System.out.println(output);

        assertFalse(output.isEmpty());

        try {
            FileUtils.deleteDirectory(testPath.toFile());
        } catch (IOException e) {
            fail("Directory Tear Down Failed");
        }
    }

    @Test
    public void testRunCommandWithRacketMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/RacketProject");
        Path sourcePath = Path.of("project/src");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "Racket ./project/tests/racket-project.rkt";

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

        // run tests
        String output;
        testCommand = testCommand.replace(sourcePath.toString(), testPath.toString());
        output = Main.runCommand(testCommand, testPath.toString());
        System.out.println(output);

        assertFalse(output.isEmpty());

        try {
            FileUtils.deleteDirectory(testPath.toFile());
        } catch (IOException e) {
            fail("Directory Tear Down Failed");
        }
    }

    /* No Sample Projects Yet
    @Test
    public void testRunCommandWithJSMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

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

        // run tests
        String output;
        testCommand = testCommand.replace(sourcePath.toString(), testPath.toString());
        output = Main.runCommand(testCommand, testPath.toString());
        System.out.println(output);

        assertFalse(output.isEmpty());

        try {
            FileUtils.deleteDirectory(testPath.toFile());
        } catch (IOException e) {
            fail("Directory Tear Down Failed");
        }
    }

    @Test
    public void testRunCommandWithCSMutantsHasOutput() {
        Path testPath = Path.of("output/TestDir");
        Path projectPath = Path.of("samples/MavenProject");
        Path sourcePath = Path.of("src/main/java");
        Mutator.MutatorType mutationType = Mutator.MutatorType.AOR_PLUS_TO_MINUS;
        String testCommand = "mvn test";

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

        // run tests
        String output;
        testCommand = testCommand.replace(sourcePath.toString(), testPath.toString());
        output = Main.runCommand(testCommand, testPath.toString());
        System.out.println(output);

        assertFalse(output.isEmpty());

        try {
            FileUtils.deleteDirectory(testPath.toFile());
        } catch (IOException e) {
            fail("Directory Tear Down Failed");
        }
    }
     */
}
