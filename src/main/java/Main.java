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
import java.util.Iterator;  

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
            return;
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
        System.out.println("Setting up working directory");
        try {
            FileUtils.deleteDirectory(mutantPath.toFile());
            FileUtils.copyDirectory(projectPath.toFile(), mutantPath.toFile());
        } catch (IOException e) {
            System.err.println("Error copying " + sourcePath + " to " + mutantPath + ": " + e.getMessage());
            return;
        }
        System.out.println("Working directory created: " + mutantPath);

        // Run baseline tests (verify tests pass on original code)
        System.out.println("\nRunning baseline tests");
        String baselineOutput = runCommand(command2, mutantPath.toString());
         if (hasTestFailures(baselineOutput)) {
            System.err.println("ERROR: Tests fail on original code! Fix tests before running mutation testing.");
            System.err.println("Baseline output:\n" + baselineOutput);
            return;
        }
        System.out.println("Baseline tests pass");

        // scan for mutation points
        System.out.println("\nScanning for mutations...");
        Path sourceDir = mutantPath.resolve(sourcePath);
        List<MutationPoint> mutationPoints = scanForMutations(
                sourceDir,
                Mutator.MutatorType.valueOf(mutatorType)
        );
        System.out.println("✅ Found " + mutationPoints.size() + " mutation points");

        if (mutationPoints.isEmpty()) {
            System.out.println("No mutations found. Exiting.");
            return;
        }
                
        // mutate the files

        //old mutate all approach
        // try {
        //     String mutatedCode = Mutator.mutate(sourcePath, Mutator.MutatorType.valueOf(mutatorType));
        //     Files.createDirectories(mutantPath.getParent());
        //     Files.writeString(mutantPath, mutatedCode);
        // } catch (Exception e) {
        //     System.out.println("Error mutating code: " + e.getMessage());
        //     e.printStackTrace();
        // }
        // // run tests
        // String compileOutput;
        // String runOutput;
        // if (!command1.isEmpty()) {
        //     String compileCommand = command1.replace(sourcePath.toString(), mutantPath.toAbsolutePath().toString());
        //     compileOutput = runCommand(compileCommand, mutantPath.toString());
        // }
        // if (!command2.isEmpty()) {
        //     String runCommand = command2.replace(sourcePath.toString(), mutantPath.toAbsolutePath().toString());
        //     runOutput = runCommand(runCommand, mutantPath.toString());
        // }

        // Test each mutant ONE at a time
        System.out.println("\nTesting mutants");
        List<MutantResult> results = new ArrayList<>();
        int current = 1;

        for (MutationPoint point : mutationPoints) {
            System.out.printf("[%d/%d] Testing %s:%d:%d (%s → %s)... ",
                    current++,
                    mutationPoints.size(),  
                    point.getSourceFile().getName(),
                    point.getLineNumber(),
                    point.getColumnNumber(),
                    point.getOperator(),
                    point.getReplacement()
            );

            MutantResult result = testSingleMutant(point, mutantPath, command2);
            results.add(result);

            // Print result
            if (result.getStatus() == MutantResult.MutantStatus.KILLED) {
                System.out.println("KILLED");
            } else if (result.getStatus() == MutantResult.MutantStatus.SURVIVED) {
                System.out.println("SURVIVED");
            } else if (result.getStatus() == MutantResult.MutantStatus.COMPILE_ERROR) {
                System.out.println("COMPILE ERROR");
            } else {
                System.out.println("TIMEOUT OR ERROR");
            }
        }

        // generate report
        System.out.println("\nGenerating report");
        MutationReport report = new MutationReport(results);
        report.printReport();   

        System.out.println("\n Mutation testing complete");
    }

    //helper metehods below
 
    //Tests a single mutant by applying the mutation, running tests, and checking results
    private static MutantResult testSingleMutant(
            MutationPoint point,
            Path workingDir,
            String testCommand
    ) {
    File originalFile = point.getSourceFile();
        String originalContent = null;

        try {
            // Backup original file content
            originalContent = Files.readString(originalFile.toPath());

            // Apply the mutation
            String mutatedContent = applyMutation(originalContent, point);
            Files.writeString(originalFile.toPath(), mutatedContent);

            // Run tests on the mutated code
            String testOutput = runCommand(testCommand, workingDir.toString());

            // Check if tests failed
            boolean testsPass = !hasTestFailures(testOutput);

            // Determine status
            MutantResult.MutantStatus status;
            if (testsPass) {
                status = MutantResult.MutantStatus.SURVIVED;  // Tests didn't catch mutation
            } else {
                status = MutantResult.MutantStatus.KILLED;    // Tests caught mutation
            }

            return new MutantResult(point, status, testOutput, testsPass);

        } catch (IOException e) {
            // Compilation or file error
            return new MutantResult(
                    point,
                    MutantResult.MutantStatus.COMPILE_ERROR,
                    e.getMessage(),
                    false
            );
        } finally {
            // 6. ALWAYS restore original file
            if (originalContent != null) {
                try {
                    Files.writeString(originalFile.toPath(), originalContent);
                } catch (IOException e) {
                    System.err.println("ERROR: Failed to restore " + originalFile + ": " + e.getMessage());
                }
            }
        }
    }

    //Applies a single mutation to the source code at the specified location
    private static String applyMutation(String content, MutationPoint point) {
        String[] lines = content.split("\n", -1);  // -1 preserves trailing empty lines

        // Get the line to mutate (convert to 0-indexed)
        int lineIndex = point.getLineNumber() - 1;

        if (lineIndex < 0 || lineIndex >= lines.length) {
            System.err.println("Warning: Line number " + point.getLineNumber() + " out of bounds");
            return content;
        }

        String line = lines[lineIndex];

        // Check if column is valid
        if (point.getColumnNumber() < 0 || point.getColumnNumber() >= line.length()) {
            System.err.println("Warning: Column number " + point.getColumnNumber() + " out of bounds");
            return content;
        }

        // Replace character at specific column
        char[] chars = line.toCharArray();
        chars[point.getColumnNumber()] = point.getReplacement().charAt(0);
        lines[lineIndex] = new String(chars);

        return String.join("\n", lines);
    }

    //Checks if test output contains failure indicators
    private static boolean hasTestFailures(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }

        // Regex pattern to detect failures in test output
        // Matches: "2 failures", "Failures: 2", "2 failed", "2 Failures"
        String pattern = "[1-9]\\d* [Ff]ailure\\(?s?\\)?|[Ff]ailure\\(?s?\\)?: [1-9]\\d*|[1-9]\\d* [Ff]ailed";
        String[] split = output.split(pattern);

        return split.length > 1;
    }

    //Scans a directory for all mutation points
    private static List<MutationPoint> scanForMutations(
            Path sourceDir,
            Mutator.MutatorType type
    ) {
        List<MutationPoint> points = new ArrayList<>();

        try {
            // Iterate through all files in source directory
            for (Iterator<File> it = FileUtils.iterateFiles(sourceDir.toFile(), null, false);
                 it.hasNext(); ) {
                File file = it.next();

                // Find mutation points in this file
                List<MutationPoint> filePoints = findMutationsInFile(file, type);
                points.addAll(filePoints);
            }
        } catch (Exception e) {
            System.err.println("Error scanning for mutations: " + e.getMessage());
            e.printStackTrace();
        }

        return points;
    }

    //Finds all mutation points in a single file
    private static List<MutationPoint> findMutationsInFile(
            File file,
            Mutator.MutatorType type
    ) throws IOException {
        List<MutationPoint> points = new ArrayList<>();
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n", -1);

        // Scan each line for mutation opportunities
        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            // For PLUS_TO_MINUS: find all + operators
            if (type == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '+' && !isInStringOrComment(line, col)) {
                        // Avoid ++ (increment operator)
                        if (col + 1 < line.length() && line.charAt(col + 1) == '+') {
                            continue;  // Skip ++
                        }
                        if (col > 0 && line.charAt(col - 1) == '+') {
                            continue;  // Skip second + in ++
                        }

                        MutationPoint point = new MutationPoint(
                                file,
                                lineNum + 1,  // Convert to 1-indexed
                                col,
                                "+",
                                "-",
                                type
                        );
                        points.add(point);
                    }
                }
            }

            // For MINUS_TO_PLUS: find all - operators
            if (type == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '-' && !isInStringOrComment(line, col)) {
                        // Avoid -- (decrement operator)
                        if (col + 1 < line.length() && line.charAt(col + 1) == '-') {
                            continue;  // Skip --
                        }
                        if (col > 0 && line.charAt(col - 1) == '-') {
                            continue;  // Skip second - in --
                        }
                        // Avoid -> (arrow operator)
                        if (col + 1 < line.length() && line.charAt(col + 1) == '>') {
                            continue;  // Skip ->
                        }

                        MutationPoint point = new MutationPoint(
                                file,
                                lineNum + 1,  // Convert to 1-indexed
                                col,
                                "-",
                                "+",
                                type
                        );
                        points.add(point);
                    }
                }
            }
        }

        return points;
    }

    //Simple check to avoid mutating operators inside strings or comments

    private static boolean isInStringOrComment(String line, int position) {
        // Check if position is inside a string
        boolean inString = false;
        char stringDelimiter = '\0';

        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);

            // Check for escape sequences
            if (i > 0 && line.charAt(i - 1) == '\\') {
                continue;  // Skip escaped characters
            }

            // Toggle string state
            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                    stringDelimiter = '\0';
                }
            }
        }

        if (inString) {
            return true;
        }

        // Check if position is inside a line comment
        int commentStart = line.indexOf("//");
        if (commentStart >= 0 && commentStart < position) {
            return true;
        }

        return false;
    }
}
