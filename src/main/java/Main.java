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

import org.apache.commons.io.FileUtils;

public class Main {
    private static final long MAX_CMD_TIME = 60000;
    private static final String OUTPUT_ROOT = "output";
    private static final String WORKING_DIR_PREFIX = "workdir-";

    static class Config {
        String sourceCodeDirectory;
        String mutatorType;
        String compileCommand;
        String testCommand;
        String mutationStrategy;  // AST or DICTIONARY, or AUTO
        String testMode;          // BROWSER, CLI, or AUTO
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

        } catch (InterruptedException e) {
            process.destroyForcibly();
            System.out.println("Interrupted while waiting for process " + commandList.get(0) + " to finish");
            e.printStackTrace();
            return "";
        }
    }

    public static void main(String[] args) {

        // These are for testing purposes because the GUI tree view does not open in
        // test mode
        // MyParser py = new MyParser("samples/PytestProject/src/Add1.py");
        // MyParser java = new MyParser("samples/MavenProject/src/main/java/Add1.java");
        // MyParser js = new MyParser("samples/JSProject/src/Add1.js");
        // MyParser cs = new MyParser("samples/CSProject/src/CSProject/Add1.cs");

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

        if ("ALL".equalsIgnoreCase(config.mutatorType)) {
            for (Mutator.MutatorType type : PythonMutationScanner.getSupportedTypes()) {
                mutationTest(
                        projectDirectory,
                        config.sourceCodeDirectory,
                        type.name(),
                        config.compileCommand,
                        config.testCommand,
                        config.mutationStrategy);
            }
        } else {
            mutationTest(
                    projectDirectory,
                    config.sourceCodeDirectory,
                    config.mutatorType,
                    config.compileCommand,
                    config.testCommand,
                    config.mutationStrategy);
        }

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

        // Read mutation strategy (defaults to AUTO)
        config.mutationStrategy = values.getOrDefault("mutation_strategy", "AUTO");
        config.testMode = values.getOrDefault("test_mode", "AUTO");

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

    private static void mutationTest(String projectDir, String sourceCode, String mutatorType, String command1,
        String command2, String mutationStrategy) {
        Path projectPath = Path.of(projectDir);
        Path sourcePath = Path.of(sourceCode);
        Path outputRoot = Path.of(OUTPUT_ROOT);
        Path mutantPath = outputRoot.resolve(WORKING_DIR_PREFIX + System.currentTimeMillis());

        System.out.println("Setting up working directory");
        try {
            FileUtils.forceMkdir(outputRoot.toFile());
            cleanupWorkDirectories(outputRoot, null);
            FileUtils.copyDirectory(projectPath.toFile(), mutantPath.toFile());
        } catch (IOException e) {
            System.err.println("Error copying " + sourcePath + " to " + mutantPath + ": " + e.getMessage());
            return;
        }
        System.out.println("Working directory created: " + mutantPath);

        try {

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
        System.out.println("Mutation strategy: " + mutationStrategy); 

        Path sourceDir = mutantPath.resolve(sourcePath);
        List<MutationPoint> mutationPoints = scanForMutations(
                sourceDir,
                Mutator.MutatorType.valueOf(mutatorType),
                mutationStrategy);
        System.out.println("Found " + mutationPoints.size() + " mutation points");

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
                    point.getReplacement());

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
            System.out.println("HTML report generated at: output/report/mutation-report-demo.html");
        } catch (IOException e) {
            System.err.println("Failed to generate HTML report: " + e.getMessage());
        }

        System.out.println("\n Mutation testing complete");
        } finally {
            cleanupWorkDirectories(outputRoot, mutantPath);
        }
    }

    private static void cleanupWorkDirectories(Path outputRoot, Path activeWorkdir) {
        File outputDirectory = outputRoot.toFile();
        File[] children = outputDirectory.listFiles();

        if (children == null) {
            return;
        }

        String activePath = activeWorkdir != null ? activeWorkdir.toAbsolutePath().normalize().toString() : null;
        for (File child : children) {
            if (!child.isDirectory() || !child.getName().startsWith(WORKING_DIR_PREFIX)) {
                continue;
            }

            String childPath = child.toPath().toAbsolutePath().normalize().toString();
            if (activePath != null && activePath.equals(childPath)) {
                continue;
            }

            try {
                FileUtils.deleteDirectory(child);
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temporary work directory " + child + ": " + e.getMessage());
            }
        }

        if (activeWorkdir != null) {
            try {
                FileUtils.deleteDirectory(activeWorkdir.toFile());
            } catch (IOException e) {
                System.err.println("Warning: Failed to delete temporary work directory " + activeWorkdir + ": " + e.getMessage());
            }
        }
    }

    private static MutantResult testSingleMutant(
            MutationPoint point,
            Path workingDir,
            String compileCommand,
            String testCommand) {
        File originalFile = point.getSourceFile();
        String originalContent = null;

        try {
            originalContent = Files.readString(originalFile.toPath());

            String mutatedContent = applyMutation(originalContent, point);
            Files.writeString(originalFile.toPath(), mutatedContent);

            String compileOutput = "";
            if (!compileCommand.isEmpty()) {
                compileOutput = runCommand(compileCommand, workingDir.toString());
                if (hasTestFailures(compileOutput)) {
                    return new MutantResult(
                            point,
                            MutantResult.MutantStatus.COMPILE_ERROR,
                            compileOutput,
                            false);
                }
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
                    false);
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

        if (point.getColumnNumber() < 0 || point.getColumnNumber() > line.length()) {
            System.err.println("Warning: Column number " + point.getColumnNumber() + " out of bounds");
            return content;
        }

        if (point.getType() == Mutator.MutatorType.REMOVE_INCREMENT) {
            String newLine = mutateRemoveIncrementLine(line, point);
            lines[lineIndex] = newLine;
            return String.join("\n", lines);
        }

        // Racket EQ_NEGATE: wrap (= x y) as (not (= x y)) instead of
        // naively replacing just the "=" token, which produces broken syntax.
        if (point.getType() == Mutator.MutatorType.EQ_NEGATE
                && point.getSourceFile().getName().endsWith(".rkt")) {
            int parenCol = point.getColumnNumber() - 1;
            if (parenCol >= 0 && parenCol < line.length() && line.charAt(parenCol) == '(') {
                int depth = 0;
                int closeIdx = -1;
                for (int i = parenCol; i < line.length(); i++) {
                    if (line.charAt(i) == '(') depth++;
                    else if (line.charAt(i) == ')') {
                        depth--;
                        if (depth == 0) { closeIdx = i; break; }
                    }
                }
                if (closeIdx != -1) {
                    String inner = line.substring(parenCol, closeIdx + 1); // e.g. "(= x y)"
                    String mutated = "(not " + inner + ")";               // "(not (= x y))"
                    String newLine = line.substring(0, parenCol) + mutated + line.substring(closeIdx + 1);
                    lines[lineIndex] = newLine;
                    return String.join("\n", lines);
                }
            }
        }

        String newLine = line.substring(0, point.getColumnNumber()) +
                point.getReplacement() +
                line.substring(point.getColumnNumber() + point.getOperator().length());

        lines[lineIndex] = newLine;

        return String.join("\n", lines);
    }

    private static String mutateRemoveIncrementLine(String line, MutationPoint point) {
        int col = point.getColumnNumber();
        String op = point.getOperator();

        int identStart;
        int identEnd;

        if (op.equals("++") || op.equals("--")) {
            boolean postfix = col > 0 && isIdentifierChar(line.charAt(col - 1));

            if (postfix) {
                identEnd = col;
                identStart = identEnd - 1;
                while (identStart >= 0 && isIdentifierChar(line.charAt(identStart))) {
                    identStart--;
                }
                identStart++;

                String varName = line.substring(identStart, identEnd);
                String replacement = op.equals("++") ? varName + " += 0" : varName + " -= 0";

                return line.substring(0, identStart) +
                        replacement +
                        line.substring(col + 2);
            } else {
                identStart = col + 2;
                while (identStart < line.length() && Character.isWhitespace(line.charAt(identStart))) {
                    identStart++;
                }

                identEnd = identStart;
                while (identEnd < line.length() && isIdentifierChar(line.charAt(identEnd))) {
                    identEnd++;
                }

                String varName = line.substring(identStart, identEnd);
                String replacement = op.equals("++") ? varName + " += 0" : varName + " -= 0";

                return line.substring(0, col) +
                        replacement +
                        line.substring(identEnd);
            }
        }

        return line;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    static boolean hasTestFailures(String output) {
        if (output == null || output.isEmpty()) {
            return false;
        }



        String lower = output.toLowerCase();

        // Check for explicit success indicators first
        if (lower.contains("all tests passed") || 
            lower.contains("status: pass") ||
            lower.contains("success rate: 100.0%")) {
            return false;  // Tests passed!
        }
        
        // Then check for failure patterns
        return lower.matches("(?s).*\\b[1-9]\\d*\\s+failure(s)?\\b.*")
                || lower.matches("(?s).*\\bfailure(s)?\\s*:\\s*[1-9]\\d*\\b.*")
                || lower.matches("(?s).*\\b[1-9]\\d*\\s+failed\\b.*")
                || lower.matches("(?s).*\\bfailed\\s*:\\s*[1-9]\\d*\\b.*")
                || lower.matches("(?s).*\\b[1-9]\\d*\\s+failing\\b.*")
                || lower.contains("0 tests passed, ")   // rackunit: "0 tests passed, N failed"
                || lower.contains("tests failed")       // rackunit summary variant
                || lower.contains("\nfailure\n")        // rackunit per-failure block header (lowercased)
                || lower.contains("check-equal? failed")
            || lower.contains("not recognized as an internal or external command")
            || lower.contains("commandnotfoundexception")
            || lower.contains("no module named pytest")
                || lower.contains("build failed")
                || lower.contains("build failure")
                || lower.contains("test run failed")
                || lower.contains("type checker:")
                || lower.contains("type mismatch");
    }

    private static List<MutationPoint> scanForMutations(
            Path sourceDir,
            Mutator.MutatorType type,
            String mutationStrategy) {
        List<MutationPoint> points = new ArrayList<>();

        try {
            for (Iterator<File> it = FileUtils.iterateFiles(sourceDir.toFile(), null, false); it.hasNext();) {
                File file = it.next();

                List<MutationPoint> filePoints = findMutationsInFile(file, type, mutationStrategy);
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
            Mutator.MutatorType type,
            String mutationStrategy) throws IOException {

        String fileName = file.getName();
        boolean useAST = shouldUseAST(fileName, type, mutationStrategy);
        if (useAST) {
            // AST APPROACH - Use ANTLR parsers
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

            if (file.getName().endsWith(".py")) {
                System.out.println("  [AST] Scanning " + file.getName() + " using ANTLR Python parser");
                return PythonMutationScanner.scanFile(file, type);
            }

            if (file.getName().endsWith(".rkt")) {
                System.out.println("  [AST] Scanning " + file.getName() + " using ANTLR Scheme parser");
                return RacketMutationScanner.scanFile(file, type);
            }

            if (file.getName().endsWith(".py")) {
                System.out.println("  [AST] Scanning " + file.getName() + " using ANTLR Python parser");
                return PythonMutationScanner.scanFile(file, type);
            }
        }
        // DICTIONARY APPROACH - Use regex pattern matching
        System.out.println("  [Dictionary] Scanning " + fileName + " using regex");
        return scanWithDictionary(file, type);
    }

     /**
     * Determines whether to use AST or Dictionary approach
     */
    private static boolean shouldUseAST(String fileName, Mutator.MutatorType type, String strategy) {
        // If explicitly set, use that
        if (strategy.equals("AST")) {
            return true;
        }
        if (strategy.equals("DICTIONARY")) {
            return false;
        }
        
        // AUTO mode - use AST whenever an ANTLR scanner exists for the file type
        if (strategy.equals("AUTO")) {
            return fileName.endsWith(".js") || 
                   fileName.endsWith(".cs") || 
                   fileName.endsWith(".java") ||
                   fileName.endsWith(".py") ||
                   fileName.endsWith(".rkt");
        }
        
        return false;
    }


    /**
     * Dictionary-based mutation scanning using regex patterns
     * This is the original approach - works for AOR, ROR, LOR mutations
     */
    private static List<MutationPoint> scanWithDictionary(File file, Mutator.MutatorType type) throws IOException {
        List<MutationPoint> points = new ArrayList<>();
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n", -1);

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            // AOR: Arithmetic Operator Replacement
            if (type == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '+' && !isInStringOrComment(line, col)) {
                        // Skip ++ increment operators
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
                                type);
                        points.add(point);
                    }
                }
            }

            if (type == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == '-' && !isInStringOrComment(line, col)) {
                        // Skip -- decrement operators
                        if (col + 1 < line.length() && line.charAt(col + 1) == '-') {
                            continue;
                        }
                        if (col > 0 && line.charAt(col - 1) == '-') {
                            continue;
                        }
                        // Skip -> (arrow operator in some languages)
                        if (col + 1 < line.length() && line.charAt(col + 1) == '>') {
                            continue;
                        }

                        MutationPoint point = new MutationPoint(
                                file,
                                lineNum + 1,
                                col,
                                "-",
                                "+",
                                type);
                        points.add(point);
                    }
                }
            }

            // Add more dictionary-based mutations here as needed
            // ROR: Relational Operator Replacement
            // LOR: Logical Operator Replacement
            // etc.
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