import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HtmlReportGenerator {

    public static void generate(List<MutantResult> results, Path reportDir) throws IOException {
        Files.createDirectories(reportDir);

        Map<String, List<MutantResult>> byFile = new LinkedHashMap<>();
        for (MutantResult r : results) {
            String path = r.getMutationPoint().getSourceFile().getPath();
            byFile.computeIfAbsent(path, k -> new ArrayList<>()).add(r);
        }

        int total = results.size();
        int killed = (int) results.stream().filter(r -> r.getStatus() == MutantResult.MutantStatus.KILLED).count();
        int survived = (int) results.stream().filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED).count();
        double score = total == 0 ? 0.0 : ((double) killed / total) * 100.0;

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Mutation Testing Report</title>\n");
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
        html.append("        .files-section { padding: 30px; }\n");
        html.append("        .files-section h2 { margin-bottom: 20px; color: #1f2937; }\n");
        html.append("        .file-table { width: 100%; border-collapse: collapse; }\n");
        html.append("        .file-table th { background: #f9fafb; padding: 12px; text-align: left; border-bottom: 2px solid #e5e7eb; }\n");
        html.append("        .file-table td { padding: 12px; border-bottom: 1px solid #e5e7eb; }\n");
        html.append("        .file-table tr:hover { background: #f9fafb; cursor: pointer; }\n");
        html.append("        a { color: inherit; text-decoration: none; }\n");
        html.append("        .file-badge { display: inline-block; padding: 4px 8px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 13px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");

        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>Mutation Testing Report</h1>\n");
        html.append("            <p>Interactive Report</p>\n");
        html.append("        </div>\n");

        html.append("        <div class=\"summary\">\n");
        html.append(String.format(
                "            <div class=\"stat-card\">\n" +
                        "                <div class=\"label\">Mutation Score</div>\n" +
                        "                <div class=\"value mutation-score\">%.1f%%</div>\n" +
                        "            </div>\n", score));
        html.append(String.format(
                "            <div class=\"stat-card\">\n" +
                        "                <div class=\"label\">Total Mutants</div>\n" +
                        "                <div class=\"value\">%d</div>\n" +
                        "            </div>\n", total));
        html.append(String.format(
                "            <div class=\"stat-card\">\n" +
                        "                <div class=\"label\">Killed</div>\n" +
                        "                <div class=\"value killed\">%d</div>\n" +
                        "            </div>\n", killed));
        html.append(String.format(
                "            <div class=\"stat-card\">\n" +
                        "                <div class=\"label\">Survived</div>\n" +
                        "                <div class=\"value survived\">%d</div>\n" +
                        "            </div>\n", survived));
        html.append("        </div>\n");

        html.append("        <div class=\"files-section\">\n");
        html.append("            <h2>Files</h2>\n");
        html.append("            <table class=\"file-table\">\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>#</th>\n");
        html.append("                        <th>File</th>\n");
        html.append("                        <th>Total</th>\n");
        html.append("                        <th>Killed</th>\n");
        html.append("                        <th>Survived</th>\n");
        html.append("                        <th>Score</th>\n");
        html.append("                    </tr>\n");
        html.append("                </thead>\n");
        html.append("                <tbody>\n");

        int i = 0;
        for (Map.Entry<String, List<MutantResult>> e : byFile.entrySet()) {
            String sourceFilePath = e.getKey();
            List<MutantResult> fileResults = e.getValue();

            int ft = fileResults.size();
            int fk = (int) fileResults.stream().filter(r -> r.getStatus() == MutantResult.MutantStatus.KILLED).count();
            int fs = (int) fileResults.stream().filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED).count();
            double fscore = ft == 0 ? 0.0 : ((double) fk / ft) * 100.0;

            String outName = "file-" + (++i) + ".html";

            PitStyleReportGenerator.generatePitStyleReport(
                    sourceFilePath,
                    reportDir.resolve(outName).toString(),
                    fileResults,
                    ft,
                    fk,
                    fs,
                    fscore
            );

            String displayName = Path.of(sourceFilePath).getFileName().toString();

            html.append("                    <tr>\n");
            html.append("                        <td><a href=\"").append(outName).append("\">").append(i).append("</a></td>\n");
            html.append("                        <td><a href=\"").append(outName).append("\"><span class=\"file-badge\">")
                    .append(escapeHtml(displayName)).append("</span></a></td>\n");
            html.append("                        <td><a href=\"").append(outName).append("\">").append(ft).append("</a></td>\n");
            html.append("                        <td><a href=\"").append(outName).append("\">").append(fk).append("</a></td>\n");
            html.append("                        <td><a href=\"").append(outName).append("\">").append(fs).append("</a></td>\n");
            html.append("                        <td><a href=\"").append(outName).append("\">")
                    .append(String.format("%.1f%%", fscore)).append("</a></td>\n");
            html.append("                    </tr>\n");
        }

        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");

        html.append("    </div>\n");
        html.append("</body>\n</html>");

        Files.writeString(reportDir.resolve("index.html"), html.toString());
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
