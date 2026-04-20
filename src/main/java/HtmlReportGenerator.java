import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

public class HtmlReportGenerator {

    public static void generateHtmlReport(MutationReport report, List<MutantResult> results) throws IOException {
        FileUtils.createParentDirectories(Path.of("./output/report/mutation-report-demo.html").toFile());
        FileUtils.forceMkdir(Path.of("./output/report/allFiles").toFile());

        List<MutantResult> survivedMutants = results.stream()
                .filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED)
                .toList();

        Map<String, List<MutantResult>> byFile = new LinkedHashMap<>();
        for (MutantResult result : results) {
            String fileName = result.getMutationPoint().getSourceFile().getName();
            byFile.putIfAbsent(fileName, new ArrayList<>());
            byFile.get(fileName).add(result);
        }

        for (Map.Entry<String, List<MutantResult>> entry : byFile.entrySet()) {
            String fileName = entry.getKey();
            List<MutantResult> fileResults = entry.getValue();

            int total = fileResults.size();
            int killed = (int) fileResults.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.KILLED)
                    .count();
            int survived = (int) fileResults.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED)
                    .count();

            double score = total > 0 ? ((double) killed / total) * 100.0 : 0.0;

            String outputFile = "./output/report/allFiles/" + stripExtension(fileName) + ".html";

            PitStyleReportGenerator.generatePitStyleReport(
                    fileResults.get(0).getMutationPoint().getSourceFile().getPath(),
                    outputFile,
                    fileResults,
                    total,
                    killed,
                    survived,
                    score
            );
        }

        AllSurvivorsPageGenerator.generateAllSurvivorsPage(
                survivedMutants,
                "./output/report/all-survivors.html"
        );

        // Generate AST visualizations
        generateASTVisualizations(results, "./output/report");

        StringBuilder html = new StringBuilder();
        double mutationScore = report.getMutationScore();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Mutation Testing Report - Demo</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: Arial, sans-serif; background-color: #f8fafc; padding: 20px; }\n");
        html.append("        .container { max-width: 1200px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); overflow: hidden; }\n");
        html.append("        .header { background: linear-gradient(135deg, #1e293b 0%, #334155 100%); color: white; padding: 30px; }\n");
        html.append("        .header h1 { font-size: 32px; margin-bottom: 8px; }\n");
        html.append("        .header p { opacity: 0.9; font-size: 16px; }\n");
        html.append("        .summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); gap: 20px; padding: 30px; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }\n");
        html.append("        .stat-card { background: white; padding: 20px; border-radius: 8px; border: 1px solid #e2e8f0; text-align: center; }\n");
        html.append("        .stat-card .label { color: #64748b; font-size: 14px; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; }\n");
        html.append("        .stat-card .value { font-size: 36px; font-weight: bold; color: #1e293b; }\n");
        html.append("        .mutation-score { color: #10b981; }\n");
        html.append("        .killed { color: #10b981; }\n");
        html.append("        .survived { color: #ef4444; }\n");
        html.append("        .survivors-section { padding: 30px; }\n");
        html.append("        .survivors-section h2 { font-size: 24px; color: #1e293b; margin-bottom: 20px; }\n");
        html.append("        .survivor-table { width: 100%; border-collapse: collapse; margin-top: 20px; }\n");
        html.append("        .survivor-table th { background: #f1f5f9; padding: 12px; text-align: left; font-weight: 600; color: #475569; border-bottom: 2px solid #cbd5e1; }\n");
        html.append("        .survivor-table td { padding: 12px; border-bottom: 1px solid #e2e8f0; }\n");
        html.append("        .survivor-table tr:hover { background: #f8fafc; }\n");
        html.append("        .survivor-table a { color: #2563eb; text-decoration: none; }\n");
        html.append("        .survivor-table a:hover { text-decoration: underline; }\n");
        html.append("        .file-badge { display: inline-block; padding: 4px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 13px; }\n");
        html.append("        .view-all { padding: 10px 20px; background: #e74c3c; color: white; text-decoration: none; border-radius: 5px; font-weight: 500; }\n");
        html.append("        .view-all:hover { background: #c0392b; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");

        html.append("    <div class=\"container\">\n");

        html.append("        <div class=\"header\">\n");
        html.append("            <h1>Mutation Testing Report</h1>\n");
        html.append("        </div>\n");

        html.append("        <div class=\"summary\">\n");
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Mutation Score</div>\n" +
                "                <div class=\"value mutation-score\">%.1f%%</div>\n" +
                "            </div>\n", mutationScore));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Total Mutants</div>\n" +
                "                <div class=\"value\">%d</div>\n" +
                "            </div>\n", report.getTotalMutants()));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Killed</div>\n" +
                "                <div class=\"value killed\">%d</div>\n" +
                "            </div>\n", report.getKilled()));
        html.append(String.format("            <div class=\"stat-card\">\n" +
                "                <div class=\"label\">Survived</div>\n" +
                "                <div class=\"value survived\">%d</div>\n" +
                "            </div>\n", report.getSurvived()));
        html.append("        </div>\n");

        html.append("        <div class=\"survivors-section\">\n");
        html.append("            <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;\">\n");
        html.append("                <h2>Survived Mutants By File</h2>\n");
        html.append("                <a href=\"all-survivors.html\" class=\"view-all\">View All Survivors →</a>\n");
        html.append("            </div>\n");

        html.append("            <table class=\"survivor-table\">\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>#</th>\n");
        html.append("                        <th>File</th>\n");
        html.append("                        <th>Total</th>\n");
        html.append("                        <th>Killed</th>\n");
        html.append("                        <th>Survivors</th>\n");
        html.append("                        <th>Score</th>\n");
        html.append("                        <th>Actions</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");

        int index = 1;
        for (Map.Entry<String, List<MutantResult>> entry : byFile.entrySet()) {
            String file = entry.getKey();
            List<MutantResult> fileResults = entry.getValue();

            int total = fileResults.size();
            int killed = (int) fileResults.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.KILLED)
                    .count();
            int survived = (int) fileResults.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED)
                    .count();
            double score = total > 0 ? ((double) killed / total) * 100.0 : 0.0;

            String link = "allFiles/" + stripExtension(file) + ".html";

            html.append("                    <tr>\n");
            html.append(String.format("                        <td><a href=\"%s\">%d</a></td>\n", link, index++));
            html.append(String.format("                        <td><a href=\"%s\"><span class=\"file-badge\">%s</span></a></td>\n", link, escapeHtml(file)));
            html.append(String.format("                        <td><a href=\"%s\">%d</a></td>\n", link, total));
            html.append(String.format("                        <td><a href=\"%s\">%d</a></td>\n", link, killed));
            html.append(String.format("                        <td><a href=\"%s\">%d</a></td>\n", link, survived));
            html.append(String.format("                        <td><a href=\"%s\">%.1f%%</a></td>\n", link, score));

            // Actions column
            html.append("                        <td>\n");
            html.append(String.format("                            <a href=\"%s\" style=\"color: #2563eb; text-decoration: none;\">View Details</a>\n", link));
            
            if (file.endsWith(".js")) {
                html.append("                            <span style=\"margin: 0 5px;\">|</span>\n");
                String astLink = "ast-" + stripExtension(file) + ".html";
                html.append(String.format("                            <a href=\"%s\" style=\"color: #10b981; text-decoration: none;\" target=\"_blank\">View AST</a>\n", astLink));
            }

            html.append("                        </td>\n");
            html.append("                    </tr>\n");
        }

        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");

        html.append("    </div>\n");
        html.append("</body>\n</html>");

        try (FileWriter writer = new FileWriter("./output/report/mutation-report-demo.html")) {
            writer.write(html.toString());
        }
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
        private static void generateASTVisualizations(
                List<MutantResult> results,
                String outputDir) throws IOException {
        
        // Group mutations by file
        Map<File, List<MutationPoint>> mutationsByFile = new HashMap<>();
        
        for (MutantResult result : results) {
                File file = result.getMutationPoint().getSourceFile();
                mutationsByFile.putIfAbsent(file, new ArrayList<>());
                mutationsByFile.get(file).add(result.getMutationPoint());
        }
        
        // Generate AST for each file
        for (Map.Entry<File, List<MutationPoint>> entry : mutationsByFile.entrySet()) {
                File sourceFile = entry.getKey();
                List<MutationPoint> mutations = entry.getValue();
                
                // Only generate for JavaScript files
                if (sourceFile.getName().endsWith(".js")) {
                String outputPath = outputDir + "/ast-" + 
                                sourceFile.getName().replace(".js", ".html");
                
                ASTVisualizationGenerator.generateJavaScriptAST(
                        sourceFile, mutations, outputPath
                );
                }
        }
        }
}