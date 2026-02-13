import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;

import org.apache.commons.io.FileUtils;

public class Main {
    private static final long MAX_CMD_TIME = 60000;
    private static final String WORKING_DIR = "output/";

    static String runCommand(String command, String directory) {
        Process process;
        List<String> commandList = new ArrayList<>(Arrays.asList(command.split(" ")));
        try {
            // run the command on the correct shell based on OS
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }

            // change child process's directory to
            File workingDir;
            if (directory != null) {
                workingDir = new File(directory);
            } else {
                workingDir = null;
            }
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            process = pb.start();

        } catch (IOException e) {
            System.out.println("Error running command " + command + ": " + e.getMessage());
            e.printStackTrace();
            return "";
        }

        // Consume output while child process runs
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append(System.lineSeparator());
            }
        } catch (IOException e) {
            process.destroyForcibly();
            System.out.println("Error running command " + command + ": " + e.getMessage());
            e.printStackTrace();
            return "";
        }

        // Wait for child process to finish
        try {
            boolean timeout = !process.waitFor(MAX_CMD_TIME, TimeUnit.MILLISECONDS);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Integer> future = executor.submit(() ->process.waitFor());
            try {
                int exitCode = future.get(MAX_CMD_TIME, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                process.destroyForcibly();
                System.out.println("Process " + commandList.get(0) + " timed out.");
                return "";
            } catch (ExecutionException | InterruptedException e) {
                process.destroyForcibly();
                System.out.println("Error running command " + command + ": " + e.getMessage());
                e.printStackTrace();
                return "";
                }
            executor.shutdown();

            process.destroy();
            System.out.println("Process " + commandList.get(0) + " finished");
            return output.toString();


        } catch (InterruptedException e) {
            process.destroyForcibly();
            System.out.println("Interrupted while waiting for process " + commandList.get(0) + " to finish");
            e.printStackTrace();
            return "";
        }
    }

    public static void main(String[] args) {
        // TODO: Refactor to use <project_directory> <config_file>
        if (args.length < 5) {
            System.out.println("Usage: java src.main.java.Main <project_directory> <source_code_directory> <mutator_type> \"command to compile code\" \"command to run the code\"");
        }

        // TODO: read config file and pass args to mutationTest

        mutationTest(args[0], args[1], args[2], args[3], args[4]);

        System.out.println("Quitting");
    }

    private static void mutationTest(String projectDir, String sourceCode, String mutatorType, String command1, String command2) {
        Path projectPath = Path.of(projectDir);
        Path sourcePath = Path.of(sourceCode);
        Path mutantPath = Path.of(WORKING_DIR);


        // clone project directory into our working directory
        try {
            FileUtils.deleteDirectory(mutantPath.toFile());
            FileUtils.copyDirectory(sourcePath.toFile(), mutantPath.toFile());
        } catch (IOException e) {
            System.err.println("Error copying " + sourcePath + " to " + mutantPath + ": " + e.getMessage());
            return;
        }

        // scan for mutations


        // mutate the files
        try {
            String mutatedCode = Mutator.mutate(sourcePath, Mutator.MutatorType.valueOf(mutatorType));
            Files.createDirectories(mutantPath.getParent());
            Files.writeString(mutantPath, mutatedCode);
        } catch (Exception e) {
            System.out.println("Error mutating code: " + e.getMessage());
            e.printStackTrace();
        }

        // run tests
        String compileOutput;
        String runOutput;
        if (!command1.isEmpty()) {
            String compileCommand = command1.replace(sourcePath.toString(), mutantPath.toAbsolutePath().toString());
            compileOutput = runCommand(compileCommand, mutantPath.toString());
        }
        if (!command2.isEmpty()) {
            String runCommand = command2.replace(sourcePath.toString(), mutantPath.toAbsolutePath().toString());
            runOutput = runCommand(runCommand, mutantPath.toString());
        }

        // check test output


        // generate report


    }
}
