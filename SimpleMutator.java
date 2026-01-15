import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleMutator {

    private static final long MAX_CMD_TIME = 30000;

    public static String mutatePlusToMinus(Path inputPath) throws IOException {
        String code = Files.readString(inputPath);
        String mutatedCode = code.replace("+", "-");
        return mutatedCode;
    }

    private static void runCommand(List<String> command, String processName){
        // Prepare and start the process
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            builder.directory(null);
            process = builder.start();
        } catch (IOException e) {
            System.out.println("Error compiling code: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Wait for the process to run
        try {
            boolean timeout = !process.waitFor(MAX_CMD_TIME, TimeUnit.MILLISECONDS);

            if (timeout) {
                process.destroyForcibly();
                System.out.println("Process timed out.");
            } else {
                process.destroy();
                System.out.println("Process " + processName + " ran successfully");
            }
        } catch (InterruptedException e) {
            System.out.println("Interrupted while waiting for process to finish");
            e.printStackTrace();
        }
    }

    public static void main(String[] args){

        // Check args and create IO Paths
        if (args.length < 3) {
            System.out.println("Usage: java SimpleMutator <input_file> \"command to compile code\" \"command to run the code\"");
            return;
        }
        Path sourcePath = Path.of(args[0]);
        Path mutantPath = Path.of("output/".concat(sourcePath.getFileName().toString()));
//        System.out.println(mutantPath.toAbsolutePath());
//        System.out.println(sourcePath.toAbsolutePath());

        // Mutate the code and write it to output file
        try {
            String mutatedCode = mutatePlusToMinus(sourcePath);
            Files.writeString(mutantPath, mutatedCode);
            System.out.println("Mutant written to " + mutantPath);
        } catch (IOException e) {
            System.out.println("Error mutating code: " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Prepare the command to compile mutated code
        ArrayList<String> compile_command = new ArrayList<>(Arrays.asList(args[1].split(" ")));
        int pathIndex = compile_command.indexOf(sourcePath.toString());
        compile_command.remove(pathIndex);
        compile_command.add(pathIndex, mutantPath.toAbsolutePath().toString());
//        System.out.println(compile_command);
        runCommand(compile_command, "compile");

        // Prepare the command to run mutated code
        List<String> run_command = Arrays.asList(args[2].split(" "));
//        System.out.println(run_command);
        runCommand(run_command, "run");

        // Done
        System.out.println("Quitting");
    }
}
