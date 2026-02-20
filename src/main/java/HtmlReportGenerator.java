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

        StringBuilder index = new StringBuilder();
        index.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                .append("<title>Mutation Report</title>")
                .append("<style>")
                .append("body{font-family:Arial,sans-serif;background:#f5f5f5;padding:20px}")
                .append(".card{background:#fff;border-radius:10px;padding:18px;margin-bottom:16px;box-shadow:0 2px 10px rgba(0,0,0,.08)}")
                .append("table{width:100%;border-collapse:collapse}")
                .append("th,td{padding:10px;border-bottom:1px solid #eee;text-align:left}")
                .append("a{color:#4338ca;text-decoration:none}")
                .append(".big{font-size:26px;font-weight:700}")
                .append(".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(180px,1fr));gap:12px}")
                .append("</style></head><body>");

        index.append("<div class=\"card\"><h2>Mutation Testing Report</h2>")
                .append("<div class=\"grid\">")
                .append("<div><div>Mutation Score</div><div class=\"big\">").append(String.format("%.1f%%", score)).append("</div></div>")
                .append("<div><div>Total Mutants</div><div class=\"big\">").append(total).append("</div></div>")
                .append("<div><div>Killed</div><div class=\"big\">").append(killed).append("</div></div>")
                .append("<div><div>Survived</div><div class=\"big\">").append(survived).append("</div></div>")
                .append("</div></div>");

        index.append("<div class=\"card\"><h3>Files</h3><table><thead><tr>")
                .append("<th>File</th><th>Total</th><th>Killed</th><th>Survived</th><th>Score</th>")
                .append("</tr></thead><tbody>");

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

            index.append("<tr>")
                    .append("<td><a href=\"").append(outName).append("\">").append(escapeHtml(displayName)).append("</a></td>")
                    .append("<td>").append(ft).append("</td>")
                    .append("<td>").append(fk).append("</td>")
                    .append("<td>").append(fs).append("</td>")
                    .append("<td>").append(String.format("%.1f%%", fscore)).append("</td>")
                    .append("</tr>");
        }

        index.append("</tbody></table></div>");
        index.append("</body></html>");

        Files.writeString(reportDir.resolve("index.html"), index.toString());
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