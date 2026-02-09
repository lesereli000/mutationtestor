import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final long MAX_CMD_TIME = 30000;
    private static final String WORKING_DIR = "output/";

    private static void runCommand(List<String> command, String processName) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            process = builder.start();
        } catch (IOException e) {
            System.out.println("Error running command " + processName + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }

        try {
            boolean timeout = !process.waitFor(MAX_CMD_TIME, TimeUnit.MILLISECONDS);

            if (timeout) {
                process.destroyForcibly();
                System.out.println("Process " + processName + " timed out.");
            } else {
                process.destroy();
                System.out.println("Process " + processName + " finished");
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted while waiting for process " + processName + " to finish");
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.out.println("Usage: java src.main.java.Main <input_file> <mutator_type> \"command to compile code\" \"command to run the code\"");
            return;
        }

        Path sourcePath = Path.of(args[0]);
        if(!sourcePath.toFile().exists()) {

        }
        Path mutantPath = Path.of(WORKING_DIR.concat(sourcePath.getFileName().toString()));

        try {
            String mutatedCode = Mutator.mutate(sourcePath, Mutator.MutatorType.valueOf(args[1]));
            Files.createDirectories(mutantPath.getParent());
            Files.writeString(mutantPath, mutatedCode);
            System.out.println("Mutant written to " + mutantPath);
        } catch (Exception e) {
            System.out.println("Error mutating code: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        if (!args[2].isEmpty()) {
            ArrayList<String> compileCommand = new ArrayList<>(Arrays.asList(args[2].split(" ")));
            int pathIndex = compileCommand.indexOf(sourcePath.toString());
            if (pathIndex != -1) {
                compileCommand.remove(pathIndex);
                compileCommand.add(pathIndex, mutantPath.toAbsolutePath().toString());
            }
            runCommand(compileCommand, "compile");
        }

        if (!args[3].isEmpty()) {
            ArrayList<String> runCommand = new ArrayList<>(Arrays.asList(args[3].split(" ")));
            int pathIndex = runCommand.indexOf(sourcePath.toString());
            if (pathIndex != -1) {
                runCommand.remove(pathIndex);
                runCommand.add(pathIndex, mutantPath.toAbsolutePath().toString());
            }
            runCommand(runCommand, "run");
        }

        System.out.println("Quitting");
    }
}
