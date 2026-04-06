import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.io.FileWriter;

public class MockCommandLineMutationTester {
    static class MockSurvivedMutant {
        public String file;
        public int line;
        public int column;
        public String original;
        public String mutated;
        public String operator;
        public String replacement;
        MockSurvivedMutant(String file, int line, int column, String original, String mutated, String operator, String replacement) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.original = original;
            this.mutated = mutated;
            this.operator = operator;
            this.replacement = replacement;
        }
    }

    static String fileExt = "";

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java MockCommandLineMutationTester <project directory> <config file>");
            return;
        }

        System.out.println("------------------------------------------\n");

        Random rand = new Random();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        Path configPath = Path.of(args[1]);
        if(configPath.equals(Path.of("proper.config"))){
            System.out.println("Found Config File: " + args[1]);
        } else {
            System.out.println("Config file " + args[1] + " not found.");
            return;
        }

        System.out.println("\n------------------------------------------\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        Path projectPath = Path.of(args[0]);
        if(projectPath.equals(Path.of("./samples/MavenProject"))) {
            int numLangs = 1;
            String[] langsPresent = {"Java"};
            fileExt = ".java";

            System.out.println("Found Project directory: " + args[0]);
            System.out.println(numLangs + " Language(s) detected: " + Arrays.toString(langsPresent));

        } else if(projectPath.equals(Path.of("./samples/PytestProject"))){
            int numLangs = 1;
            String[] langsPresent = {"Python"};
            fileExt = ".py";

            System.out.println("Found Project directory: " + args[0]);
            System.out.println(numLangs + " Languages detected: " + Arrays.toString(langsPresent));

        } else if(projectPath.equals(Path.of("./samples/RacketProject"))) {
            int numLangs = 1;
            String[] langsPresent = {"Racket Scheme"};
            fileExt = ".rkt";

            System.out.println("Found Project directory: " + args[0]);
            System.out.println(numLangs + " Languages detected: " + Arrays.toString(langsPresent));

        } else if(projectPath.equals(Path.of("./samples/RacketCalculator/calculator"))) {
            int numLangs = 1;
            String[] langsPresent = {"Racket Scheme"};
            fileExt = ".rkt";

            System.out.println("Found Project directory: " + args[0]);
            System.out.println(numLangs + " Languages detected: " + Arrays.toString(langsPresent));

        } else if(projectPath.equals(Path.of("./samples/JSProject"))){
            int numLangs = 1;
            String[] langsPresent = {"JavaScript"};
            fileExt = ".js";

            System.out.println("Found Project directory: " + args[0]);
            System.out.println(numLangs + " Languages detected: " + Arrays.toString(langsPresent));

        } else if(projectPath.equals(Path.of("./samples/CSProject"))) {
            int numLangs = 1;
            String[] langsPresent = {"C#"};
            fileExt = ".cs";

            System.out.println("Found Project directory: " + args[0]);
            System.out.println(numLangs + " Languages detected: " + Arrays.toString(langsPresent));

        } else {
            System.out.println("Project directory " + args[0] + " not found.");
            return;
        }

        System.out.println("\n------------------------------------------\n");
        System.out.println("Discovering mutants...");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        int mutantsMade = rand.nextInt(1500, 2000);
        System.out.println("Found " + mutantsMade + " mutants");
        System.out.println("\n------------------------------------------\n");
        System.out.println("Starting to run mutants...");

        int completion = 0;
        while(completion < 100){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            int inc = rand.nextInt(15, 25);
            if(completion + inc > 100){
                break;
            }
            completion += inc;

            System.out.println("Running mutants: " + completion + "%");
        }
        System.out.println("Finished running tests");

        System.out.println("\n------------------------------------------\n");

        System.out.println("Generating Report ... \n");

        int buildFailures = rand.nextInt(500);
        System.out.printf("Discovered %d mutants.\n", mutantsMade);
        System.out.printf("Only %d mutants could be built and ran successfully.\n", mutantsMade - buildFailures);

        int survived = rand.nextInt(200, 500);
        int killed = mutantsMade - survived - buildFailures;
        int totalLines = rand.nextInt(3000, 7000);
        int mutatedLines = totalLines - rand.nextInt(300, 700);

        // Generate survived mutants data
        List<MockSurvivedMutant> survivedMutants = generateMockSurvivedMutants(survived, rand);

        // Print console report
        printConsoleReport(mutantsMade, buildFailures, killed, survived, totalLines, mutatedLines, survivedMutants);

        // Generate HTML report
        try {
            generateHTMLReport(mutantsMade, buildFailures, killed, survived, totalLines, mutatedLines, survivedMutants, rand);
            System.out.println("\n Interactive HTML report generated: mutation-report-demo.html");
            System.out.println("   Open this file in your browser to view mutations with hover tooltips!");
        } catch (IOException e) {
            System.err.println("Error generating HTML report: " + e.getMessage());
        }

        // Generate PIT-style source code view with hover tooltips for mutations
        try {
            double mutationScore = ((double) killed / (killed + survived)) * 100.0;
            PitStyleReportGenerator.generatePitStyleReport(
                "samples/prototypeSample" + fileExt,
                "./output/report/pit-style-report.html",
                survivedMutants,
                mutantsMade - buildFailures,
                killed,
                survived,
                mutationScore
            );
            System.out.println("\nPIT-style source code report generated: pit-style-report.html");
            System.out.println("   Open this file to view source code with HOVER TOOLTIPS!");
        } catch (IOException e) {
            System.err.println("Error generating PIT-style report: " + e.getMessage());
        }

        // Generate All Survivors page with filtering
        try {
            AllSurvivorsPageGenerator.generateAllSurvivorsPage(
                    survivedMutants,
                    "./output/report/all-survivors.html"
            );
            System.out.println("\nAll Survivors page generated: all-survivors.html");
            System.out.println("   Click any survivor to jump to its location in the source code!");
        } catch (IOException e) {
            System.err.println("Error generating All Survivors page: " + e.getMessage());
        }

        System.out.println("\n------------------------------------------\n");
    }
    



    static String[] files = {"Add1.java", "Add2.java", "Main.java", "Calculator.java", "Utils.java"};

    private static List<MockSurvivedMutant> generateMockSurvivedMutants(int count, Random rand) {
        List<MockSurvivedMutant> mutants = new ArrayList<>();
        String[] operators = {"+", "-", "*", "/", ">", "<", ">=", "<=", "==", "!="};
        String[] replacements = {"-", "+", "/", "*", ">=", "<=", ">", "<", "!=", "=="};
        
        for(int i = 0; i < count; i++){
            String file = files[rand.nextInt(files.length)];
            int line = rand.nextInt(10, 200);
            int column = rand.nextInt(10, 80);
            int opIdx = rand.nextInt(operators.length);
            String operator = operators[opIdx];
            String replacement = replacements[opIdx];
            String original = String.format("return x %s y;", operator);
            String mutated = String.format("return x %s y;", replacement);

            mutants.add(new MockSurvivedMutant(file, line, column, original, mutated, operator, replacement));
        }
        
        return mutants;
    }
    private static void printConsoleReport(int mutantsMade, int buildFailures, int killed, int survived, int totalLines, int mutatedLines, List<MockSurvivedMutant> survivedMutants) {
        System.out.printf("Discovered %d mutants.\n", mutantsMade);
        System.out.printf("Only %d mutants could be built and ran successfully.\n", mutantsMade - buildFailures);

        System.out.println();
        System.out.printf("Mutants Killed: %d\n", killed);
        System.out.printf("Surviving Mutants: %d\n", survived);

        double mutationScore = ((double) killed / (killed + survived)) * 100;
        System.out.printf("\nMutation Score: %.2f%%\n", mutationScore);
        System.out.printf("Mutation Coverage: %.2f%%\n", ((float) mutatedLines / totalLines) * 100);
        System.out.printf("Mutation Survival Rate: %.2f%%\n", ((float) survived / mutantsMade) * 100);
        System.out.printf("Mutation Density: %.2f\n", ((float) mutantsMade / totalLines));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("SURVIVED MUTANTS SUMMARY");
        System.out.println("=".repeat(80));
        
        // For large numbers of survivors, show condensed summary
        if (survived > 20) {
            System.out.printf("Total Survived: %d (showing first 20, see HTML report for complete list)\n\n", survived);
            for(int i = 0; i < Math.min(20, survivedMutants.size()); i++){
                MockSurvivedMutant m = survivedMutants.get(i);
                System.out.printf("[%d] %s:%d:%d - %s To %s\n", 
                    i + 1, m.file, m.line, m.column, m.operator, m.replacement);
            }
            System.out.println("\n  " + (survived - 20) + " more survived mutants not shown.");
            System.out.println("   Open mutation-report-demo.html to vie    w all survivors with interactive tooltips!");
        } else {
            System.out.println("Mutants that survived:\n");
            for (MockSurvivedMutant m : survivedMutants) {
                System.out.println("--------------------------");
                System.out.printf("Location: %s, line %d, column %d\n", m.file, m.line, m.column);
                System.out.printf("Mutation: %s To %s\n", m.operator, m.replacement);
                System.out.printf("Original: %s\n", m.original);
                System.out.printf("Mutated:  %s\n", m.mutated);
            }
        }
        System.out.println("=".repeat(80));
    }

    private static void generateHTMLReport(int mutantsMade, int buildFailures, int killed, int survived,
                                          int totalLines, int mutatedLines, List<MockSurvivedMutant> survivedMutants, Random rand)
                                          throws IOException {
        StringBuilder html = new StringBuilder();
        
        double mutationScore = ((double) killed / (killed + survived)) * 100;
        
        // HTML header and styles
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Mutation Testing Report - Demo</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; padding: 20px; }\n");
        html.append("        .container { max-width: 1400px; margin: 0 auto; background: white; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border-radius: 8px; }\n");
        html.append("        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 8px 8px 0 0; }\n");
        html.append("        .header h1 { font-size: 32px; margin-bottom: 10px; }\n");
        html.append("        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; background: #fafafa; }\n");
        html.append("        .stat-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); text-align: center; }\n");
        html.append("        .stat-card .value { font-size: 36px; font-weight: bold; margin: 10px 0; }\n");
        html.append("        .stat-card .label { color: #666; font-size: 14px; }\n");
        html.append("        .mutation-score { color: #667eea; }\n");
        html.append("        .killed { color: #10b981; }\n");
        html.append("        .survived { color: #ef4444; }\n");
        html.append("        .survivors-section { padding: 30px; }\n");
        html.append("        .survivors-section h2 { margin-bottom: 20px; color: #1f2937; }\n");
        html.append("        .survivor-table { width: 100%; border-collapse: collapse; }\n");
        html.append("        .survivor-table th { background: #f9fafb; padding: 12px; text-align: left; border-bottom: 2px solid #e5e7eb; }\n");
        html.append("        .survivor-table td { padding: 12px; border-bottom: 1px solid #e5e7eb; }\n");
        html.append("        .survivor-table tr:hover { background: #f9fafb; cursor: pointer; }\n");
        html.append("        .mutation-badge { display: inline-block; padding: 4px 8px; background: #fee2e2; color: #dc2626; border-radius: 4px; font-family: monospace; font-size: 13px; }\n");
        html.append("        .file-badge { display: inline-block; padding: 4px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 13px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");
        
        // Container
        html.append("    <div class=\"container\">\n");
        
        // Header
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>Mutation Testing Report</h1>\n");
        html.append("            <p>Interactive Demo - Prototype Report</p>\n");
        html.append("        </div>\n");
        
        // Summary cards
        html.append("        <div class=\"summary\">\n");
        html.append(String.format("            <div class=\"stat-card\">\n" +
                                  "                <div class=\"label\">Mutation Score</div>\n" +
                                  "                <div class=\"value mutation-score\">%.1f%%</div>\n" +
                                  "            </div>\n", mutationScore));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                                  "                <div class=\"label\">Total Mutants</div>\n" +
                                  "                <div class=\"value\">%d</div>\n" +
                                  "            </div>\n", mutantsMade));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                                  "                <div class=\"label\">Killed</div>\n" +
                                  "                <div class=\"value killed\">%d</div>\n" +
                                  "            </div>\n", killed));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                                  "                <div class=\"label\">Survived</div>\n" +
                                  "                <div class=\"value survived\">%d</div>\n" +
                                  "            </div>\n", survived));
        html.append("        </div>\n");
        

        // Survivors section with link to all survivors page
        html.append("        <div class=\"survivors-section\">\n");
        html.append("            <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;\">\n");
        html.append("                <h2>Survived Mutants By File</h2>\n");
        html.append("                <a href=\"all-survivors.html\" style=\"padding: 10px 20px; background: #e74c3c; color: white; text-decoration: none; border-radius: 5px; font-weight: 500;\">View All Survivors →</a>\n");
        html.append("            </div>\n");
        // Survivors table
        html.append("        <div class=\"survivors-section\">\n");
        html.append("            <h2>Survived Mutants By File</h2>\n");
        html.append("            <table class=\"survivor-table\">\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");  
        html.append("                        <th>#</th>\n");
        html.append("                        <th>File</th>\n");
        html.append("                        <th>Mutants</th>\n");
        html.append("                        <th>Killed</th>\n");
        html.append("                        <th>Survivors</th>\n");
        html.append("                        <th>Score</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");
        
        for (int i = 0; i < files.length; i++) {
            MockSurvivedMutant m = survivedMutants.get(i);
            int survivedInFile = rand.nextInt(50, 150);
            if(survived < survivedInFile){
                survivedInFile = survived;
            }
            survived -= survivedInFile;
            int killedInFile = rand.nextInt(150, 300);
            if(killed < killedInFile){
                killedInFile = killed;
            }
            killed -= killedInFile;

            html.append("                    <tr>\n");
            html.append(String.format("                        <td><a href=\"pit-style-report.html\">%d</a></td>\n", i + 1));
            html.append(String.format("                        <td><a href=\"pit-style-report.html\"><span class=\"file-badge\">%s</span></a></td>\n", files[i]));
            html.append(String.format("                        <td><a href=\"pit-style-report.html\">%d</a></td>\n", killedInFile+survivedInFile));
            html.append(String.format("                        <td><a href=\"pit-style-report.html\">%d</a></td>\n", killedInFile));
            html.append(String.format("                        <td><a href=\"pit-style-report.html\">%d</a></td>\n", survivedInFile));
            html.append(String.format("                        <td><a href=\"pit-style-report.html\">%.1f%%</a></td>\n", ((double) killedInFile / (killedInFile + survivedInFile)) * 100));
            html.append("                    </tr>\n");

            generateHTMLReportByFile(killedInFile + survivedInFile, buildFailures, killedInFile, survivedInFile, totalLines, mutatedLines, survivedMutants.stream().filter(mu -> mu.file.equals(files[0])).toList(), rand, files[i]);
        }
        
        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        
        // Footer
        html.append("    </div>\n");
        html.append("</body>\n</html>");
        
        // Write to file
        try (FileWriter writer = new FileWriter("./output/report/mutation-report-demo.html")) {
            writer.write(html.toString());
        }
    }

    private static void generateHTMLReportByFile(int mutantsMade, int buildFailures, int killed, int survived,
                                           int totalLines, int mutatedLines, List<MockSurvivedMutant> survivedMutants, Random rand, String file)
            throws IOException {
        StringBuilder html = new StringBuilder();

        double mutationScore = ((double) killed / (killed + survived)) * 100;

        // HTML header and styles
        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Mutation Testing Report - Demo</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; padding: 20px; }\n");
        html.append("        .container { max-width: 1400px; margin: 0 auto; background: white; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border-radius: 8px; }\n");
        html.append("        .header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; border-radius: 8px 8px 0 0; }\n");
        html.append("        .header h1 { font-size: 32px; margin-bottom: 10px; }\n");
        html.append("        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; background: #fafafa; }\n");
        html.append("        .stat-card { background: white; padding: 20px; border-radius: 8px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); text-align: center; }\n");
        html.append("        .stat-card .value { font-size: 36px; font-weight: bold; margin: 10px 0; }\n");
        html.append("        .stat-card .label { color: #666; font-size: 14px; }\n");
        html.append("        .mutation-score { color: #667eea; }\n");
        html.append("        .killed { color: #10b981; }\n");
        html.append("        .survived { color: #ef4444; }\n");
        html.append("        .survivors-section { padding: 30px; }\n");
        html.append("        .survivors-section h2 { margin-bottom: 20px; color: #1f2937; }\n");
        html.append("        .survivor-table { width: 100%; border-collapse: collapse; }\n");
        html.append("        .survivor-table th { background: #f9fafb; padding: 12px; text-align: left; border-bottom: 2px solid #e5e7eb; }\n");
        html.append("        .survivor-table td { padding: 12px; border-bottom: 1px solid #e5e7eb; }\n");
        html.append("        .survivor-table td tr:hover { background: #f9fafb; cursor: pointer; }\n");
        html.append("        .mutation-badge { display: inline-block; padding: 4px 8px; background: #fee2e2; color: #dc2626; border-radius: 4px; font-family: monospace; font-size: 13px; }\n");
        html.append("        .file-badge { display: inline-block; padding: 4px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 13px; }\n");
        html.append("        p { padding: 30px; font-family: monospace; }\n");
        html.append("        .killed p { background-color: #10b981; opacity: 0.3; }\n");
        html.append("        .survived p { background-color: #ef4444; opacity: 0.3; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");

        // Container
        html.append("    <div class=\"container\">\n");

        // Header
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>Mutation Testing Report</h1>\n");
        html.append("            <p>Interactive Demo - Prototype Report</p>\n");
        html.append("        </div>\n");

        // Summary cards
        html.append("        <div class=\"summary\">\n");
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Mutation Score</div>\n" +
                "                <div class=\"value mutation-score\">%.1f%%</div>\n" +
                "            </div>\n", mutationScore));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Total Mutants</div>\n" +
                "                <div class=\"value\">%d</div>\n" +
                "            </div>\n", mutantsMade));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Killed</div>\n" +
                "                <div class=\"value killed\">%d</div>\n" +
                "            </div>\n", killed));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Survived</div>\n" +
                "                <div class=\"value survived\">%d</div>\n" +
                "            </div>\n", survived));
        html.append("        </div>\n");

        // Body
        try (BufferedReader reader = new BufferedReader(new FileReader("./samples/prototypeSample" + fileExt))) {

            String line;
            int lineNum = 1;
            boolean isKilled = rand.nextInt(mutantsMade) > killed;

            while ((line = reader.readLine()) != null) {
                html.append(String.format("<p id=\"%d\" class=\"%s\">%s</p>\n", lineNum, isKilled ? "killed" : "survived", line));
                lineNum++;
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        // Footer
        html.append("    </div>\n");
        html.append("</body>\n</html>");

        // Write to file
        try {
            FileUtils.createParentDirectories(Path.of("./output/report/allFiles/").toFile());
            FileWriter writer = new FileWriter(String.format("./output/report/allFiles/%s.html", file.substring(0, file.indexOf("."))));
            writer.write(html.toString());
        } catch (Exception e) {
            System.out.println("Error creating individual reports " + e.getMessage());
        }
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}