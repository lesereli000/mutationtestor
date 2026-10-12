package mutationtester.report;

import mutationtester.model.MutantResult;
import mutationtester.equivalent.EquivalentMutantStore;
import mutationtester.equivalent.EquivalentsBranch;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;

public class HtmlReportGenerator {

    public static void generateHtmlReport(MutationReport report, List<MutantResult> results) throws IOException {
        generateHtmlReport(report, results, EquivalentsPageData.empty());
    }

    public static void generateHtmlReport(MutationReport report, List<MutantResult> results,
            EquivalentsPageData equivalents) throws IOException {
        FileUtils.createParentDirectories(Path.of("./output/report/mutation-report-demo.html").toFile());
        FileUtils.deleteDirectory(Path.of("./output/report/allFiles").toFile());
        FileUtils.forceMkdir(Path.of("./output/report/allFiles").toFile());

        // Counts start from every mutant; the page script hides equivalents on load.
        MutationReport visibleReport = new MutationReport(results);

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
            int noCoverage = (int) fileResults.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.NO_COVERAGE)
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
                    noCoverage,
                    score,
                    equivalents
            );
        }

        AllSurvivorsPageGenerator.generateAllSurvivorsPage(
                survivedMutants,
                "./output/report/all-survivors.html",
                equivalents
        );

        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        html.append("    <title>Mutation Testing Report - Demo</title>\n");
        html.append("    <style>\n");
        html.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        html.append("        body { font-family: Arial, sans-serif; background-color: #f8fafc; padding: 20px; }\n");
        html.append("        .container { max-width: 1600px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); overflow: hidden; }\n");
        html.append("        .header { background: linear-gradient(135deg, #1e293b 0%, #334155 100%); color: white; padding: 30px; }\n");
        html.append("        .header h1 { font-size: 32px; margin-bottom: 8px; }\n");
        html.append("        .summary { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 20px; padding: 30px; background: #f8fafc; border-bottom: 1px solid #e2e8f0; }\n");
        html.append("        .stat-card { min-width: 0; background: white; padding: 20px 14px; border-radius: 8px; border: 1px solid #e2e8f0; text-align: center; }\n");
        html.append("        .stat-card .label { color: #64748b; font-size: 14px; margin-bottom: 8px; text-transform: uppercase; letter-spacing: 0.5px; white-space: nowrap; }\n");
        html.append("        .stat-card .value { font-size: 36px; font-weight: bold; color: #1e293b; }\n");
        html.append("        @media (max-width: 1100px) { .summary { grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); } .stat-card .label { white-space: normal; } }\n");
        html.append("        .mutation-score { color: #10b981; }\n");
        html.append("        .killed { color: #10b981; }\n");
        html.append("        .survived { color: #ef4444; }\n");
        html.append("        .no-coverage { color: #f59e0b; }\n");
        html.append("        .equivalent { color: #8e44ad; }\n");
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
        html.append("        .store-note { margin-top: 8px; color: #64748b; font-size: 13px; }\n");
        html.append("    </style>\n");
        html.append("</head>\n<body>\n");

        html.append("    <div class=\"container\">\n");
        html.append("        <div class=\"header\">\n");
        html.append("            <h1>Mutation Testing Report</h1>\n");
        html.append("            <div class=\"store-note\">Equivalent mutants: ")
                .append(escapeHtml(describeSource(equivalents)))
                .append("</div>\n");
        html.append("        </div>\n");

        html.append("        <div class=\"summary\">\n");
        int totalMutants = visibleReport.getTotalMutants();
        int killedCount = visibleReport.getKilled();
        int survivedCount = visibleReport.getSurvived();
        int noCoverageCount = visibleReport.getNoCoverage();
        double initialScore = totalMutants > 0 ? (killedCount * 100.0 / totalMutants) : 0.0;

        html.append(String.format("            <div class=\"stat-card\"><div class=\"label\">Mutation Score</div><div id=\"top-mutation-score\" class=\"value mutation-score\">%.1f%%</div></div>\n", initialScore));
        html.append(String.format("            <div class=\"stat-card\"><div class=\"label\">Total Mutants</div><div id=\"top-total-count\" class=\"value\">%d</div></div>\n", totalMutants));
        html.append(String.format("            <div class=\"stat-card\"><div class=\"label\">Killed</div><div id=\"top-killed-count\" class=\"value killed\">%d</div></div>\n", killedCount));
        html.append(String.format("            <div class=\"stat-card\"><div class=\"label\">Survived</div><div id=\"top-survived-count\" class=\"value survived\">%d</div></div>\n", survivedCount));
        html.append(String.format("            <div class=\"stat-card\"><div class=\"label\">No Coverage</div><div id=\"top-no-coverage-count\" class=\"value no-coverage\">%d</div></div>\n", noCoverageCount));
        html.append("            <div class=\"stat-card\"><div class=\"label\">Equivalent</div><div id=\"top-equivalent-count\" class=\"value equivalent\">0</div></div>\n");
        html.append("        </div>\n");

        html.append("        <div class=\"survivors-section\">\n");
        html.append("            <div style=\"display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;\">\n");
        html.append("                <h2>Survived Mutants By File</h2>\n");
        html.append("                <a href=\"all-survivors.html\" class=\"view-all\">View All Survivors</a>\n");
        html.append("            </div>\n");

        html.append("            <table class=\"survivor-table\">\n");
        html.append("                <thead>\n");
        html.append("                    <tr>\n");
        html.append("                        <th>#</th>\n");
        html.append("                        <th>File</th>\n");
        html.append("                        <th>Total</th>\n");
        html.append("                        <th>Killed</th>\n");
        html.append("                        <th>Survivors</th>\n");
        html.append("                        <th>No Coverage</th>\n");
        html.append("                        <th>Score</th>\n");
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
            int noCoverage = (int) fileResults.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.NO_COVERAGE)
                    .count();
            double score = total > 0 ? ((double) killed / total) * 100.0 : 0.0;

            String link = "allFiles/" + stripExtension(file) + ".html";

            html.append(String.format(
                    "                    <tr class=\"summary-row\" data-file=\"%s\" data-total=\"%d\" data-killed=\"%d\" data-survived=\"%d\" data-no-coverage=\"%d\" data-killed-ids=\"%s\" data-survived-ids=\"%s\" data-no-coverage-ids=\"%s\">\n",
                    escapeHtml(file),
                    total,
                    killed,
                    survived,
                    noCoverage,
                    escapeHtml(toJsonArray(mutantIdsForStatus(fileResults, MutantResult.MutantStatus.KILLED))),
                    escapeHtml(toJsonArray(mutantIdsForStatus(fileResults, MutantResult.MutantStatus.SURVIVED))),
                    escapeHtml(toJsonArray(mutantIdsForStatus(fileResults, MutantResult.MutantStatus.NO_COVERAGE)))
            ));
            html.append(String.format("                        <td><a href=\"%s\">%d</a></td>\n", link, index++));
            html.append(String.format("                        <td><a href=\"%s\"><span class=\"file-badge\">%s</span></a></td>\n", link, escapeHtml(file)));
            html.append(String.format("                        <td class=\"total-cell\"><a href=\"%s\">%d</a></td>\n", link, total));
            html.append(String.format("                        <td class=\"killed-cell\"><a href=\"%s\">%d</a></td>\n", link, killed));
            html.append(String.format("                        <td class=\"survived-cell\"><a href=\"%s\">%d</a></td>\n", link, survived));
            html.append(String.format("                        <td class=\"no-coverage-cell\"><a href=\"%s\">%d</a></td>\n", link, noCoverage));
            html.append(String.format("                        <td class=\"score-cell\"><a href=\"%s\">%.1f%%</a></td>\n", link, score));
            html.append("                    </tr>\n");
        }

        html.append("                </tbody>\n");
        html.append("            </table>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");

        html.append(equivalents.toDataScript());
        html.append(EquivalentsPageData.sharedScript());
        html.append("<script>\n");
        html.append("function parseIds(row, name) {\n");
        html.append("    try { return JSON.parse(row.dataset[name] || '[]'); } catch (e) { return []; }\n");
        html.append("}\n");
        html.append("function hiddenCount(ids, hiddenIds) {\n");
        html.append("    return ids.filter(idGroup => idGroup.some(id => hiddenIds.has(id))).length;\n");
        html.append("}\n");
        html.append("\n");
        html.append("async function applyEquivalentFilteringToSummary() {\n");
        html.append("    try {\n");
        html.append("        const hiddenIds = await loadHiddenMutantIds();\n");
        html.append("\n");
        html.append("        let visibleSurvivedTotal = 0;\n");
        html.append("        let visibleTotal = 0;\n");
        html.append("        let visibleKilledTotal = 0;\n");
        html.append("        let visibleNoCoverageTotal = 0;\n");
        html.append("        let equivalentTotal = 0;\n");
        html.append("\n");
        html.append("        document.querySelectorAll('.summary-row').forEach(row => {\n");
        html.append("            const totalCount = parseInt(row.dataset.total || '0', 10);\n");
        html.append("            const killedCount = parseInt(row.dataset.killed || '0', 10);\n");
        html.append("            const survivedCount = parseInt(row.dataset.survived || '0', 10);\n");
        html.append("            const noCoverageCount = parseInt(row.dataset.noCoverage || '0', 10);\n");
        html.append("\n");
        html.append("            const hiddenKilled = hiddenCount(parseIds(row, 'killedIds'), hiddenIds);\n");
        html.append("            const hiddenSurvived = hiddenCount(parseIds(row, 'survivedIds'), hiddenIds);\n");
        html.append("            const hiddenNoCoverage = hiddenCount(parseIds(row, 'noCoverageIds'), hiddenIds);\n");
        html.append("            const hiddenForThisFile = hiddenKilled + hiddenSurvived + hiddenNoCoverage;\n");
        html.append("\n");
        html.append("            const visibleTotalForThisFile = Math.max(0, totalCount - hiddenForThisFile);\n");
        html.append("            const visibleKilledForThisFile = Math.max(0, killedCount - hiddenKilled);\n");
        html.append("            const visibleSurvivedForThisFile = Math.max(0, survivedCount - hiddenSurvived);\n");
        html.append("            const visibleNoCoverageForThisFile = Math.max(0, noCoverageCount - hiddenNoCoverage);\n");
        html.append("            equivalentTotal += hiddenForThisFile;\n");
        html.append("\n");
        html.append("            const totalCell = row.querySelector('.total-cell a');\n");
        html.append("            const killedCell = row.querySelector('.killed-cell a');\n");
        html.append("            const survivedCell = row.querySelector('.survived-cell a');\n");
        html.append("            const noCoverageCell = row.querySelector('.no-coverage-cell a');\n");
        html.append("            const scoreCell = row.querySelector('.score-cell a');\n");
        html.append("            if (totalCell) totalCell.textContent = visibleTotalForThisFile;\n");
        html.append("            if (killedCell) killedCell.textContent = visibleKilledForThisFile;\n");
        html.append("            if (survivedCell) survivedCell.textContent = visibleSurvivedForThisFile;\n");
        html.append("            if (noCoverageCell) noCoverageCell.textContent = visibleNoCoverageForThisFile;\n");
        html.append("            if (scoreCell) scoreCell.textContent = (visibleTotalForThisFile > 0 ? (visibleKilledForThisFile * 100.0 / visibleTotalForThisFile) : 0).toFixed(1) + '%';\n");
        html.append("\n");
        html.append("            if (totalCount > 0 && visibleTotalForThisFile === 0) {\n");
        html.append("                row.style.display = 'none';\n");
        html.append("            } else {\n");
        html.append("                row.style.display = '';\n");
        html.append("            }\n");
        html.append("\n");
        html.append("            visibleTotal += visibleTotalForThisFile;\n");
        html.append("            visibleKilledTotal += visibleKilledForThisFile;\n");
        html.append("            visibleSurvivedTotal += visibleSurvivedForThisFile;\n");
        html.append("            visibleNoCoverageTotal += visibleNoCoverageForThisFile;\n");
        html.append("        });\n");
        html.append("\n");
        html.append("        const totalEl = document.getElementById('top-total-count');\n");
        html.append("        const killedEl = document.getElementById('top-killed-count');\n");
        html.append("        const survivedEl = document.getElementById('top-survived-count');\n");
        html.append("        const noCoverageEl = document.getElementById('top-no-coverage-count');\n");
        html.append("        const equivalentEl = document.getElementById('top-equivalent-count');\n");
        html.append("        const scoreEl = document.getElementById('top-mutation-score');\n");
        html.append("\n");
        html.append("        const score = visibleTotal > 0 ? (visibleKilledTotal * 100.0 / visibleTotal) : 0;\n");
        html.append("\n");
        html.append("        if (totalEl) totalEl.textContent = visibleTotal;\n");
        html.append("        if (killedEl) killedEl.textContent = visibleKilledTotal;\n");
        html.append("        if (survivedEl) survivedEl.textContent = visibleSurvivedTotal;\n");
        html.append("        if (noCoverageEl) noCoverageEl.textContent = visibleNoCoverageTotal;\n");
        html.append("        if (equivalentEl) equivalentEl.textContent = equivalentTotal;\n");
        html.append("        if (scoreEl) scoreEl.textContent = score.toFixed(1) + '%';\n");
        html.append("    } catch (e) {\n");
        html.append("        console.error('Failed to apply equivalent filtering on summary page:', e);\n");
        html.append("    }\n");
        html.append("}\n");
        html.append("document.addEventListener('DOMContentLoaded', applyEquivalentFilteringToSummary);\n");
        html.append("</script>\n");

        html.append("</body>\n</html>");

       try (FileWriter writer = new FileWriter("./output/report/mutation-report-demo.html", java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
    }

    private static String describeSource(EquivalentsPageData equivalents) {
        int count = equivalents.hiddenIds().size();
        return switch (equivalents.source()) {
            case "branch" -> count + " from the " + EquivalentsBranch.BRANCH + " branch";
            case "local" -> count + " from " + EquivalentMutantStore.getStorePath();
            default -> equivalents.repoUrl() != null
                    ? "none yet (marks you save are kept on the " + EquivalentsBranch.BRANCH + " branch)"
                    : "local API server or " + EquivalentMutantStore.getStorePath();
        };
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
    }

    private static List<Set<String>> mutantIdsForStatus(List<MutantResult> results, MutantResult.MutantStatus status) {
        return results.stream()
                .filter(result -> result.getStatus() == status)
                .map(EquivalentMutantStore::buildCandidateMutantIds)
                .toList();
    }

    private static String toJsonArray(List<Set<String>> idGroups) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < idGroups.size(); i++) {
            if (i > 0) {
                json.append(",");
            }
            json.append("[");
            int index = 0;
            for (String id : idGroups.get(i)) {
                if (index++ > 0) {
                    json.append(",");
                }
                json.append("\"").append(escapeJson(id)).append("\"");
            }
            json.append("]");
        }
        json.append("]");
        return json.toString();
    }

    private static String escapeJson(String value) {
        return (value == null ? "" : value)
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
