import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;

import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.CharStreams;

import org.apache.commons.io.FileUtils;

import javax.swing.*;

public class Main {
    private static final long MAX_CMD_TIME = 60000;
    private static final String WORKING_DIR = "output/";

    static class Config {
        String sourceCodeDirectory;
        String mutatorType;
        String compileCommand;
        String testCommand;
    }

    static String runCommand(String command, String directory) {
        Process process;
        List<String> commandList = new ArrayList<>(Arrays.asList(command.split(" ")));
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }

            File workingDir = (directory != null) ? new File(directory) : null;
            pb.directory(workingDir);
            pb.redirectErrorStream(true);
            process = pb.start();

        } catch (IOException e) {
            System.out.println("Error running command " + command + ": " + e.getMessage());
            e.printStackTrace();
            return "";
        }

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

        // These are for testing purposes because the GUI tree view does not open in test mode
//        MyParser py = new MyParser("samples/PytestProject/src/Add1.py");
//        MyParser java = new MyParser("samples/MavenProject/src/main/java/Add1.java");
//        MyParser js = new MyParser("samples/JSProject/src/Add1.js");
//        MyParser cs = new MyParser("samples/CSProject/src/CSProject/Add1.cs");

        if (args.length != 2) {
            System.out.println("Usage: java -jar MutationTester.jar <project_directory> <config_file>");
            return;
        }

        String projectDirectory = args[0];
        String configFile = args[1];

        Config config;
        try {
            config = readConfig(configFile);
        } catch (IOException e) {
            System.out.println("Error reading config file " + configFile + ": " + e.getMessage());
            return;
        }

        mutationTest(
                projectDirectory,
                config.sourceCodeDirectory,
                config.mutatorType,
                config.compileCommand,
                config.testCommand
        );

        System.out.println("Quitting");
    }

    private static Config readConfig(String configFile) throws IOException {
        Path configPath = Path.of(configFile);

        if (!Files.exists(configPath)) {
            throw new IOException("Config file not found");
        }

        Map<String, String> values = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(configPath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                int idx = line.indexOf('=');
                if (idx < 0) {
                    continue;
                }

                String key = line.substring(0, idx).trim();
                String value = line.substring(idx + 1).trim();
                values.put(key, value);
            }
        }

        Config config = new Config();
        config.sourceCodeDirectory = values.get("source_code_directory");
        config.mutatorType = values.get("mutator_type");
        config.compileCommand = values.getOrDefault("compile_command", "");
        config.testCommand = values.get("test_command");

        if (config.sourceCodeDirectory == null || config.sourceCodeDirectory.isEmpty()) {
            throw new IOException("Missing source_code_directory in config file");
        }

        if (config.mutatorType == null || config.mutatorType.isEmpty()) {
            throw new IOException("Missing mutator_type in config file");
        }

        if (config.testCommand == null || config.testCommand.isEmpty()) {
            throw new IOException("Missing test_command in config file");
        }

        return config;
    }

    private static void mutationTest(String projectDir, String sourceCode, String mutatorType, String command1, String command2) {
        Path projectPath = Path.of(projectDir);
        Path sourcePath = Path.of(sourceCode);
        Path mutantPath = Path.of(WORKING_DIR);

        System.out.println("Setting up working directory");
        try {
            FileUtils.deleteDirectory(mutantPath.toFile());
            FileUtils.copyDirectory(projectPath.toFile(), mutantPath.toFile());
        } catch (IOException e) {
            System.err.println("Error copying " + sourcePath + " to " + mutantPath + ": " + e.getMessage());
            return;
        }
        System.out.println("Working directory created: " + mutantPath);

        System.out.println("\nRunning baseline tests");
        if (!command1.isEmpty()) {
            runCommand(command1, mutantPath.toString());
        }
        String baselineOutput = runCommand(command2, mutantPath.toString());
        if (hasTestFailures(baselineOutput)) {
            System.err.println("ERROR: Tests fail on original code! Fix tests before running mutation testing.");
            System.err.println("Baseline output:\n" + baselineOutput);
            return;
        }
        System.out.println("Baseline tests pass");

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

            MutantResult result = testSingleMutant(point, mutantPath, command1, command2);
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

        System.out.println("\nGenerating report");
        MutationReport report = new MutationReport(results);
        report.printReport();

        try {
            HtmlReportGenerator.generateHtmlReport(report, results);
            System.out.println("HTML report generated at: output/report/mutation-report.html");
        } catch (IOException e) {
            System.err.println("Failed to generate HTML report: " + e.getMessage());
        }

        System.out.println("\n Mutation testing complete");
    }

    private static MutantResult testSingleMutant(
            MutationPoint point,
            Path workingDir,
            String compileCommand,
            String testCommand
    ) {
        File originalFile = point.getSourceFile();
        String originalContent = null;

        try {
            originalContent = Files.readString(originalFile.toPath());

            String mutatedContent = applyMutation(originalContent, point);
            Files.writeString(originalFile.toPath(), mutatedContent);

            if (!compileCommand.isEmpty()) {
                String compileOutput = runCommand(compileCommand, workingDir.toString());
            }

            String testOutput = runCommand(testCommand, workingDir.toString());

            boolean testsPass = !hasTestFailures(testOutput);

            MutantResult.MutantStatus status;
            if (testsPass) {
                status = MutantResult.MutantStatus.SURVIVED;
            } else {
                status = MutantResult.MutantStatus.KILLED;
            }

            return new MutantResult(point, status, testOutput, testsPass);

        } catch (IOException e) {
            return new MutantResult(
                    point,
                    MutantResult.MutantStatus.COMPILE_ERROR,
                    e.getMessage(),
                    false
            );
        } finally {
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

        if (lineIndex < 0 || lineIndex >= lines.length) {
            System.err.println("Warning: Line number " + point.getLineNumber() + " out of bounds");
            return content;
        }

        String line = lines[lineIndex];

        if (point.getColumnNumber() < 0 || point.getColumnNumber() >= line.length()) {
            System.err.println("Warning: Column number " + point.getColumnNumber() + " out of bounds");
            return content;
        }

        char[] chars = line.toCharArray();
        chars[point.getColumnNumber()] = point.getReplacement().charAt(0);
        lines[lineIndex] = new String(chars);

        return String.join("\n", lines);
    }

    private static boolean hasTestFailures(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }

        String lower = output.toLowerCase();

        return lower.matches("(?s).*\\b[1-9]\\d*\\s+failure(s)?\\b.*")
                || lower.matches("(?s).*\\bfailure(s)?\\s*:\\s*[1-9]\\d*\\b.*")
                || lower.matches("(?s).*\\b[1-9]\\d*\\s+failed\\b.*")
                || lower.matches("(?s).*\\bfailed\\s*:\\s*[1-9]\\d*\\b.*")
                || lower.matches("(?s).*\\b[1-9]\\d*\\s+failing\\b.*")
                || lower.contains("build failed")
                || lower.contains("test run failed");
    }

    private static List<MutationPoint> scanForMutations(
            Path sourceDir,
            Mutator.MutatorType type
    ) {
        List<MutationPoint> points = new ArrayList<>();

        try {
            for (Iterator<File> it = FileUtils.iterateFiles(sourceDir.toFile(), null, false); it.hasNext();) {
                File file = it.next();

                List<MutationPoint> filePoints = findMutationsInFile(file, type);
                points.addAll(filePoints);
            }
        } catch (Exception e) {
            System.err.println("Error scanning for mutations: " + e.getMessage());
            e.printStackTrace();
        }

        return points;
    }

    private static List<MutationPoint> findMutationsInFile(
            File file,
            Mutator.MutatorType type
    ) throws IOException {
        // Check if this is a JavaScript file - use AST approach
        if (file.getName().endsWith(".js")) {
            System.out.println("  [AST] Scanning " + file.getName() + " using ANTLR JavaScript parser");
            return JavaScriptMutationScanner.scanFile(file, type);
        }

        if (file.getName().endsWith(".cs")) {
            System.out.println("  [AST] Scanning " + file.getName() + " using ANTLR C# parser");
            return CSharpMutationScanner.scanFile(file, type);
        }

        if (file.getName().endsWith(".java")) {
            System.out.println("  [AST] Scanning " + file.getName() + " using ANTLR Java parser");
            return JavaMutationScanner.scanFile(file, type);
        }

        // For other files (.java, .py, .cs) keep using existing dictionary approach
        System.out.println("  [Dictionary] Scanning " + file.getName() + " using regex");
    
        List<MutationPoint> points = new ArrayList<>();
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n", -1);
 

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            if (type == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '+' && !isInStringOrComment(line, col)) {
                        if (col + 1 < line.length() && line.charAt(col + 1) == '+') {
                            continue;
                        }
                        if (col > 0 && line.charAt(col - 1) == '+') {
                            continue;
                        }

                        MutationPoint point = new MutationPoint(
                                file,
                                lineNum + 1,
                                col,
                                "+",
                                "-",
                                type
                        );
                        points.add(point);
                    }
                }
            }

            if (type == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '-' && !isInStringOrComment(line, col)) {
                        if (col + 1 < line.length() && line.charAt(col + 1) == '-') {
                            continue;
                        }
                        if (col > 0 && line.charAt(col - 1) == '-') {
                            continue;
                        }
                        if (col + 1 < line.length() && line.charAt(col + 1) == '>') {
                            continue;
                        }

                        MutationPoint point = new MutationPoint(
                                file,
                                lineNum + 1,
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

    private static boolean isInStringOrComment(String line, int position) {
        boolean inString = false;
        char stringDelimiter = '\0';

        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);

            if (i > 0 && line.charAt(i - 1) == '\\') {
                continue;
            }

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

        int commentStart = line.indexOf("//");
        if (commentStart >= 0 && commentStart < position) {
            return true;
        }
        return false;
    }
}