import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class SimpleMutator {

    private static final long MAX_CMD_TIME = 30000;
    private static final String WOKRING_DIR = "output/";

    //Mutation Types 
    private enum MutatorType {
        AOR_PLUS_TO_MINUS,
        // AOR_MINUS_TO_PLUS,
        // ROR_LT_TO_LE,
        // ROR_LE_TO_LT,
        // ROR_GT_TO_GE,
        // ROR_GE_TO_GT,
        // EQ_NEGATE,          // switch between == and !=
        // LOGICAL_AND_TO_OR,  // from && to ||
        // LOGICAL_OR_TO_AND,  // from || to &&
        // INC_TO_DEC,         // from ++ to --
        // DEC_TO_INC          // from -- to ++
    }

    private enum State {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING_DOUBLE,
        STRING_SINGLE
    }


    public static String mutatePlusToMinus(Path inputPath) throws IOException {
        String code = Files.readString(inputPath);
        String name = inputPath.getFileName().toString().toLowerCase();

        if (name.endsWith(".rkt")) {
            return RacketMutator.mutatePlusToMinus(code);
        }
        return replacePlusWithMinusOutsideStringsAndComments(code);
    }

    private static String replacePlusWithMinusOutsideStringsAndComments(String code) {
        StringBuilder out = new StringBuilder(code.length());
        State state = State.CODE;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';

            switch (state) {
                case CODE -> {
                    // comments
                    if (c == '/' && next == '/') {
                        out.append(c).append(next);
                        i++;
                        state = State.LINE_COMMENT;
                        continue;
                    }
                    if (c == '/' && next == '*') {
                        out.append(c).append(next);
                        i++;
                        state = State.BLOCK_COMMENT;
                        continue;
                    }

                    // strings
                    if (c == '"') {
                        out.append(c);
                        state = State.STRING_DOUBLE;
                        continue;
                    }
                    if (c == '\'') {
                        out.append(c);
                        state = State.STRING_SINGLE;
                        continue;
                    }

                    // mutate only code
                    if (c == '+') {
                        out.append(shouldMutatePlus(code, i) ? '-' : '+');
                    } else {
                        out.append(c);
                    }
                }

                case LINE_COMMENT -> {
                    out.append(c);
                    if (c == '\n') state = State.CODE;
                }

                case BLOCK_COMMENT -> {
                    out.append(c);
                    if (c == '*' && next == '/') {
                        out.append(next);
                        i++;
                        state = State.CODE;
                    }
                }

                case STRING_DOUBLE -> {
                    out.append(c);
                    // preserve escapes inside string
                    if (c == '\\' && next != '\0') {
                        out.append(next);
                        i++;
                        continue;
                    }
                    if (c == '"') state = State.CODE;
                }

                case STRING_SINGLE -> {
                    out.append(c);
                    // preserve escapes inside string or char
                    if (c == '\\' && next != '\0') {
                        out.append(next);
                        i++;
                        continue;
                    }
                    if (c == '\'') state = State.CODE;
                }
            }
        }

        return out.toString();
    }

    private static boolean shouldMutatePlus(String code, int plusIndex) {
        char next = (plusIndex + 1 < code.length()) ? code.charAt(plusIndex + 1) : '\0';
        if (next == '+' || next == '=') return false;

        int left = plusIndex - 1;
        while (left >= 0 && Character.isWhitespace(code.charAt(left))) left--;
        if (left < 0) return false;

        if (code.charAt(left) == '+') return false;

        int right = plusIndex + 1;
        while (right < code.length() && Character.isWhitespace(code.charAt(right))) right++;
        if (right >= code.length()) return false;

        char l = code.charAt(left);
        char r = code.charAt(right);

        if (l == '"' || r == '"') return false;
        if (l == '\'' || r == '\'') return false;

        if (!isLikelyArithmeticLeftOperand(l)) return false;
        if (!isLikelyArithmeticRightOperand(r)) return false;

        return true;
    }

    private static boolean isLikelyArithmeticLeftOperand(char c) {
        return Character.isJavaIdentifierPart(c) || Character.isDigit(c) || c == ']' || c == '.';
    }

    private static boolean isLikelyArithmeticRightOperand(char c) {
        return Character.isJavaIdentifierPart(c) || Character.isDigit(c) || c == '(' || c == '.';
    }

    private static void runCommand(List<String> command, String processName){
        // Prepare and start the process
        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.inheritIO();
            process = builder.start();
        } catch (IOException e) {
            System.out.println("Error running command " + processName + ": " + e.getMessage());
            e.printStackTrace();
            return;
        }

        // Wait for the process to run
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

    public static void main(String[] args){

        // Check args and create IO Paths
        if (args.length < 3) {
            System.out.println("Usage: java SimpleMutator <input_file> \"command to compile code\" \"command to run the code\"");
            return;
        }
        Path sourcePath = Path.of(args[0]);

        Path mutantPath = Path.of(WOKRING_DIR.concat(sourcePath.getFileName().toString()));
        
        // System.out.println(mutantPath.toAbsolutePath());
        // System.out.println(sourcePath.toAbsolutePath());

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
        if(!args[1].isEmpty()) {
            ArrayList<String> compile_command = new ArrayList<>(Arrays.asList(args[1].split(" ")));
            int pathIndex = compile_command.indexOf(sourcePath.toString());
            if (pathIndex != -1) {
                compile_command.remove(pathIndex);
                compile_command.add(pathIndex, mutantPath.toAbsolutePath().toString());
            }
            System.out.println(compile_command);
            runCommand(compile_command, "compile");
        }

        // Prepare the command to run mutated code
        if(!args[2].isEmpty()) {
            ArrayList<String> run_command = new ArrayList<>(Arrays.asList(args[2].split(" ")));
            int pathIndex = run_command.indexOf(sourcePath.toString());
            if (pathIndex != -1) {
                run_command.remove(pathIndex);
                run_command.add(pathIndex, mutantPath.toAbsolutePath().toString());
            }
            System.out.println(run_command);
            runCommand(run_command, "run");
        }

        // Done
        System.out.println("Quitting");
    }
}
