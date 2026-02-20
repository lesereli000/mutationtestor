import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import org.apache.commons.io.FileUtils;

public class Main {
    private static final long MAX_CMD_TIME = 60000;
    private static final String WORKING_DIR = "output/";
    private static final String REPORT_DIR = WORKING_DIR + "report/";

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
            Future<Integer> future = executor.submit(() -> process.waitFor());
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

        } catch (Exception e) {
            process.destroyForcibly();
            System.out.println("Interrupted while waiting for process " + commandList.get(0) + " to finish");
            e.printStackTrace();
            return "";
        }
    }

    public static void main(String[] args) {

        if (args.length != 2) {
            System.out.println("Usage:");
            System.out.println("  java -jar <jar> <project_directory> <config_file>");
            return;
        }

        String projectDir = args[0];
        String configFile = args[1];

        Path cfg = Path.of(configFile);
        if (!Files.exists(cfg)) {
            System.out.println("Config file not found: " + configFile);
            return;
        }

        Map<String, String> config = readSimpleConfig(cfg);

        String sourceDir = config.getOrDefault("sourceDir", "src");
        String mutator = config.getOrDefault("mutator", "AOR_PLUS_TO_MINUS");
        String compile = config.getOrDefault("compile", "");
        String test = config.getOrDefault("test", "");

        mutationTest(projectDir, sourceDir, mutator, compile, test);

        System.out.println("Quitting");
    }

    private static java.util.Map<String, String> readSimpleConfig(java.nio.file.Path cfg) {
        java.util.Map<String, String> map = new java.util.HashMap<>();
        try {
            java.util.List<String> lines = java.nio.file.Files.readAllLines(cfg);
            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) continue;

                int eq = line.indexOf('=');
                if (eq < 0) continue;

                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();

                if (!key.isEmpty()) map.put(key, val);
            }
        } catch (java.io.IOException e) {
            System.out.println("Failed to read config: " + e.getMessage());
        }
        return map;
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



        // run baseline tests
        System.out.println("\nRunning baseline tests");
        String baselineOutput = runCommand(command2, mutantPath.toString());
        if (hasTestFailures(baselineOutput)) {
            System.err.println("ERROR: Tests fail on original code! Fix tests before running mutation testing.");
            System.err.println(baselineOutput);
            return;
        }
        System.out.println("Baseline tests pass");

        // scan for mutation points
        System.out.println("\nScanning for mutations...");
        Path sourceDir = mutantPath.resolve(sourcePath);
        List<MutationPoint> mutationPoints = scanForMutations(sourceDir, Mutator.MutatorType.valueOf(mutatorType));
        System.out.println("✅ Found " + mutationPoints.size() + " mutation points");

        if (mutationPoints.isEmpty()) {
            System.out.println("No mutations found. Exiting.");
            return;
        }

        // test mutants
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

        try {
            HtmlReportGenerator.generate(results, Path.of(REPORT_DIR));
            System.out.println("\nHTML report generated: " + REPORT_DIR + "index.html");
        } catch (IOException e) {
            System.err.println("Error generating HTML report: " + e.getMessage());
        }

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

            // Apply mutation
            String mutatedContent = applyMutation(originalContent, point);
            Files.writeString(originalFile.toPath(), mutatedContent);

            // Run tests
            String testOutput = runCommand(testCommand, workingDir.toString());

            // Check results
            boolean testsPass = !hasTestFailures(testOutput);

            MutantResult.MutantStatus status;
            if (testsPass) status = MutantResult.MutantStatus.SURVIVED;
            else status = MutantResult.MutantStatus.KILLED;

            return new MutantResult(point, status, testOutput, testsPass);

        } catch (IOException e) {
            return new MutantResult(point, MutantResult.MutantStatus.COMPILE_ERROR, e.getMessage(), false);
        } finally {
            // Restore original file
            if (originalContent != null) {
                try {
                    Files.writeString(originalFile.toPath(), originalContent);
                } catch (IOException e) {
                    System.err.println("ERROR: Failed to restore " + originalFile + ": " + e.getMessage());
                }
            }
        }
    }

    private static String applyMutation(String content, MutationPoint point) {
        String[] lines = content.split("\n", -1);
        int lineIndex = point.getLineNumber() - 1;

        if (lineIndex < 0 || lineIndex >= lines.length) return content;

        String line = lines[lineIndex];
        int col = point.getColumnNumber();
        if (col < 0 || col >= line.length()) return content;

        String op = point.getOperator();
        String repl = point.getReplacement();

        if (op == null || repl == null || op.isEmpty()) return content;

        if (col + op.length() > line.length()) return content;
        if (!line.substring(col, col + op.length()).equals(op)) return content;

        String mutatedLine = line.substring(0, col) + repl + line.substring(col + op.length());
        lines[lineIndex] = mutatedLine;

        return String.join("\n", lines);
    }

    private static boolean hasTestFailures(String output) {
        if (output == null || output.isEmpty()) return false;

        if (output.contains("\nFAIL") || output.startsWith("FAIL")) return true;
        if (output.contains("Test Suites:") && output.contains("failed")) return true;
        if (output.contains("Tests:") && output.contains("failed")) return true;
        if (output.contains("npm ERR!")) return true;

        return false;
    }

    private static List<MutationPoint> scanForMutations(Path sourceDir, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();

        try {
            for (Iterator<File> it = FileUtils.iterateFiles(sourceDir.toFile(), null, true); it.hasNext(); ) {
                File file = it.next();
                points.addAll(findMutationsInFile(file, type));
            }
        } catch (Exception e) {
            System.err.println("Error scanning for mutations: " + e.getMessage());
            e.printStackTrace();
        }

        return points;
    }

    private static List<MutationPoint> findMutationsInFile(File file, Mutator.MutatorType type) throws IOException {
        List<MutationPoint> points = new ArrayList<>();
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n", -1);

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            if (type == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '+' && !isInStringOrComment(line, col)) {
                        if (col + 1 < line.length() && line.charAt(col + 1) == '+') continue;
                        if (col > 0 && line.charAt(col - 1) == '+') continue;

                        points.add(new MutationPoint(file, lineNum + 1, col, "+", "-", type));
                    }
                }
            }

            if (type == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '-' && !isInStringOrComment(line, col)) {
                        if (col + 1 < line.length() && line.charAt(col + 1) == '-') continue;
                        if (col > 0 && line.charAt(col - 1) == '-') continue;

                        points.add(new MutationPoint(file, lineNum + 1, col, "-", "+", type));
                    }
                }
            }
        }

        return points;
    }

    private static boolean isInStringOrComment(String line, int position) {
        boolean inString = false;
        char stringDelimiter = '\0';

        // Check if position is inside a string
        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);
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