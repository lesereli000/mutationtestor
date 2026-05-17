package mutationtester.report;

import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;
import mutationtester.equivalent.EquivalentMutantStore;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PitStyleReportGenerator {

    static class LineMutation {
        int lineNumber;
        int columnStart;
        int columnEnd;
        String original;
        String mutated;
        MutantResult.MutantStatus status;
        String mutantId;
        Set<String> candidateMutantIds;
        String customLabel;

        LineMutation(int line, int colStart, int colEnd, String orig, String mut, MutantResult.MutantStatus status, String mutantId,
                     Set<String> candidateMutantIds, String customLabel) {
            this.lineNumber = line;
            this.columnStart = colStart;
            this.columnEnd = colEnd;
            this.original = orig;
            this.mutated = mut;
            this.status = status;
            this.mutantId = mutantId;
            this.candidateMutantIds = candidateMutantIds;
            this.customLabel = customLabel;
        }
    }

    public static void generatePitStyleReport(
            String sourceFilePath,
            String outputPath,
            List<?> allMutants,
            int totalMutants,
            int killed,
            int survived,
            int noCoverage,
            double mutationScore
    ) throws IOException {

        List<String> sourceLines = Files.readAllLines(Path.of(sourceFilePath));
        List<LineMutation> mutations = convertMutations(allMutants);

        Map<Integer, List<LineMutation>> mutationsByLine = new HashMap<>();
        for (LineMutation m : mutations) {
            mutationsByLine.putIfAbsent(m.lineNumber, new ArrayList<>());
            mutationsByLine.get(m.lineNumber).add(m);
        }

        StringBuilder html = new StringBuilder();
        html.append(generateHeader());
        html.append(generateSummaryBar(totalMutants, killed, survived, noCoverage, mutationScore));
        html.append(generateSourceCodeView(sourceLines, mutationsByLine, sourceFilePath));
        html.append(generateFooter());

        try (FileWriter writer = new FileWriter(outputPath, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
    }

    private static List<LineMutation> convertMutations(List<?> allMutants) {
        List<LineMutation> mutations = new ArrayList<>();

        for (Object mutant : allMutants) {
            try {
                if (mutant instanceof MutantResult result) {
                    MutationPoint point = result.getMutationPoint();
                    mutations.add(new LineMutation(
                            point.getLineNumber(),
                            point.getColumnNumber(),
                            point.getColumnNumber() + point.getOperator().length(),
                            point.getOperator(),
                            point.getReplacement(),
                            result.getStatus(),
                            EquivalentMutantStore.buildMutantId(result),
                            EquivalentMutantStore.buildCandidateMutantIds(result),
                            point.getCustomLabel()
                    ));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return mutations;
    }

    private static boolean isInString(String line, int position) {
        String targetRegex = "\".*?\"|'.*?'";
        return posIsInRegex(line, position, targetRegex);
    }

    private static boolean isAddingStrings(String line, int pos) {
        return posIsInRegex(line, pos, "\"\\s*\\+|\\+\\s*\"");
    }

    private static boolean isDouble(String line, int pos, String symbol) {
        String targetRegex = symbol.concat(symbol);
        return posIsInRegex(line, pos, targetRegex);
    }

    private static boolean isArrow(String line, int pos) {
        return posIsInRegex(line, pos, "<[-_]+|[-_=]+>|<==+");
    }

    private static boolean isPair(String line, int pos, String open, String close) {
        String targetRegex = open.concat("(.*,?\\s*)*").concat(close);
        return posIsInRegex(line, pos, targetRegex);
    }

    private static boolean posIsInRegex(String line, int pos, String regex) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(line);

        while (matcher.find()) {
            if (pos >= matcher.start() && pos < matcher.end()) {
                return true;
            }
        }

        return false;
    }

    private static String generateHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <title>Mutation Coverage Report</title>\n");
        sb.append("    <style>\n");
        sb.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append("        body { font-family: Arial, sans-serif; background: #f5f5f5; padding: 20px; }\n");
        sb.append("        .container { max-width: 1400px; margin: 0 auto; background: white; box-shadow: 0 2px 10px rgba(0,0,0,0.1); }\n");
        sb.append("        .summary-bar { background: #2c3e50; color: white; padding: 20px 30px; display: flex; justify-content: space-between; align-items: center; }\n");
        sb.append("        .stat { text-align: center; }\n");
        sb.append("        .stat-value { font-size: 28px; font-weight: bold; margin-bottom: 5px; }\n");
        sb.append("        .stat-label { font-size: 12px; opacity: 0.8; }\n");
        sb.append("        .mutation-score { background: #27ae60; padding: 10px 20px; border-radius: 5px; font-size: 32px; font-weight: bold; }\n");
        sb.append("        .mutation-score.medium { background: #f39c12; }\n");
        sb.append("        .mutation-score.low { background: #e74c3c; }\n");
        sb.append("        .source-view { padding: 30px; }\n");
        sb.append("        .source-header { margin-bottom: 20px; padding-bottom: 10px; border-bottom: 2px solid #ecf0f1; }\n");
        sb.append("        .source-header h2 { color: #2c3e50; font-size: 24px; }\n");
        sb.append("        .source-code { background: #fafafa; border: 1px solid #ddd; border-radius: 5px; overflow: visible; font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; }\n");
        sb.append("        .code-line { display: flex; flex-wrap: wrap; border-bottom: 1px solid #eee; position: relative; min-height: 20px; }\n");
        sb.append("        .code-line:hover .mutation-tooltip { display: block; }\n");
        sb.append("        .line-number { background: #e8e8e8; color: #666; padding: 2px 10px; text-align: right; min-width: 50px; user-select: none; border-right: 1px solid #ddd; font-size: 12px; align-self: stretch; }\n");
        sb.append("        .line-content { padding: 2px 10px; white-space: pre; flex: 1; min-width: 0; }\n");
        sb.append("        .code-line.has-mutations { background: #fff3cd; }\n");
        sb.append("        .code-line.all-killed { background: #d4edda; }\n");
        sb.append("        .code-line.has-survived { background: #f8d7da; }\n");
        sb.append("        .code-line.has-no-coverage { background: #fff7ed; }\n");
        sb.append("        .mutation-indicator { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; }\n");
        sb.append("        .mutation-indicator.killed { background: #28a745; }\n");
        sb.append("        .mutation-indicator.survived { background: #dc3545; }\n");
        sb.append("        .mutation-indicator.no-coverage { background: #f59e0b; }\n");
        sb.append("        .mutation-indicator.mixed { background: linear-gradient(to bottom, #28a745 0%, #28a745 33%, #dc3545 33%, #dc3545 66%, #f59e0b 66%, #f59e0b 100%); }\n");
        sb.append("        .mutation-tooltip { display: none; position: static; background: white; border: 2px solid #333; border-radius: 5px; padding: 12px; box-shadow: 0 5px 20px rgba(0,0,0,0.2); width: calc(100% - 60px); margin: 6px 0 8px 60px; z-index: 1; }\n");
        sb.append("        .code-line:hover .mutation-tooltip { display: block; }\n");
        sb.append("        .tooltip-header { font-weight: bold; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px solid #ddd; color: #2c3e50; font-size: 13px; }\n");
        sb.append("        .mutation-entry { padding: 6px 8px; margin: 4px 0; border-radius: 3px; border-left: 3px solid; font-size: 12px; }\n");
        sb.append("        .mutation-entry.killed { background: #d4edda; border-color: #28a745; }\n");
        sb.append("        .mutation-entry.survived { background: #f8d7da; border-color: #dc3545; }\n");
        sb.append("        .mutation-entry.no-coverage { background: #fff7ed; border-color: #f59e0b; }\n");
        sb.append("        .mutation-entry strong { font-family: 'Consolas', monospace; }\n");
        sb.append("        .mutation-status { font-size: 11px; margin-top: 3px; font-weight: bold; }\n");
        sb.append("        .mutation-status.killed { color: #28a745; }\n");
        sb.append("        .mutation-status.survived { color: #dc3545; }\n");
        sb.append("        .mutation-status.no-coverage { color: #f59e0b; }\n");
        sb.append("        .legend { margin-top: 20px; padding: 12px; background: #f8f9fa; border-radius: 5px; }\n");
        sb.append("        .legend h4 { margin-bottom: 8px; color: #2c3e50; font-size: 14px; }\n");
        sb.append("        .legend-items { display: flex; gap: 15px; flex-wrap: wrap; }\n");
        sb.append("        .legend-item { display: flex; align-items: center; gap: 6px; font-size: 13px; }\n");
        sb.append("        .legend-box { width: 18px; height: 18px; border: 1px solid #ddd; }\n");
        sb.append("    </style>\n</head>\n<body>\n<div class=\"container\">\n");
        return sb.toString();
    }

    private static String generateSummaryBar(int total, long killed, long survived, long noCoverage, double mutationScore) {
        String scoreClass = mutationScore >= 80 ? "" : (mutationScore >= 60 ? "medium" : "low");

        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"summary-bar\">\n");
        sb.append("    <div class=\"stat\"><div id=\"top-total-count\" class=\"stat-value\">").append(total).append("</div><div class=\"stat-label\">Total Mutations</div></div>\n");
        sb.append("    <div class=\"stat\"><div id=\"top-killed-count\" class=\"stat-value\" style=\"color: #2ecc71\">").append(killed).append("</div><div class=\"stat-label\">Killed</div></div>\n");
        sb.append("    <div class=\"stat\"><div id=\"top-survived-count\" class=\"stat-value\" style=\"color: #e74c3c\">").append(survived).append("</div><div class=\"stat-label\">Survived</div></div>\n");
        sb.append("    <div class=\"stat\"><div id=\"top-no-coverage-count\" class=\"stat-value\" style=\"color: #f59e0b\">").append(noCoverage).append("</div><div class=\"stat-label\">No Coverage</div></div>\n");
        sb.append("    <div id=\"top-mutation-score\" class=\"mutation-score ").append(scoreClass).append("\">").append(String.format("%.0f%%", mutationScore)).append("</div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private static String generateSourceCodeView(List<String> sourceLines, Map<Integer, List<LineMutation>> mutationsByLine, String filename) {
        StringBuilder html = new StringBuilder();

        String displayName = filename.substring(Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\')) + 1);

        html.append("<div class=\"source-view\">\n");
        html.append("    <div class=\"source-header\"><h2>").append(escapeHtml(displayName)).append("</h2></div>\n");
        html.append("    <div class=\"source-code\">\n");

        for (int i = 0; i < sourceLines.size(); i++) {
            int lineNum = i + 1;
            String line = sourceLines.get(i);
            List<LineMutation> lineMutations = mutationsByLine.get(lineNum);

            if (lineMutations == null || lineMutations.isEmpty()) {
                html.append("<div class=\"code-line\" data-line-number=\"").append(lineNum).append("\"><div class=\"line-number\">")
                        .append(lineNum).append("</div><div class=\"line-content\">").append(escapeHtml(line)).append("</div></div>\n");
            } else {
                boolean hasKilled = lineMutations.stream().anyMatch(m -> m.status == MutantResult.MutantStatus.KILLED);
                boolean hasSurvived = lineMutations.stream().anyMatch(m -> m.status == MutantResult.MutantStatus.SURVIVED);
                boolean hasNoCoverage = lineMutations.stream().anyMatch(m -> m.status == MutantResult.MutantStatus.NO_COVERAGE);

                String lineClass = "code-line has-mutations";
                String indicatorClass = "mutation-indicator";

                if (hasSurvived) {
                    lineClass += " has-survived";
                    indicatorClass += " survived";
                } else if (hasNoCoverage) {
                    lineClass += " has-no-coverage";
                    indicatorClass += " no-coverage";
                } else if (hasKilled) {
                    lineClass += " all-killed";
                    indicatorClass += " killed";
                }

                if ((hasKilled && hasSurvived) || (hasKilled && hasNoCoverage) || (hasSurvived && hasNoCoverage)) {
                    indicatorClass = "mutation-indicator mixed";
                }

                html.append("<div class=\"").append(lineClass).append("\" id=\"line-").append(lineNum)
                        .append("\" data-line-number=\"").append(lineNum).append("\"><div class=\"")
                        .append(indicatorClass).append("\"></div><div class=\"line-number\">").append(lineNum)
                        .append("</div><div class=\"line-content\">").append(escapeHtml(line)).append("</div>")
                        .append(generateTooltip(lineNum, lineMutations))
                        .append("</div>\n");
            }
        }

        html.append("    </div>\n");
        html.append(generateLegend());
        html.append("</div>\n");

        return html.toString();
    }

    private static String generateTooltip(int lineNum, List<LineMutation> mutations) {
        StringBuilder tooltip = new StringBuilder();
        tooltip.append("<div class=\"mutation-tooltip\" data-line-number=\"").append(lineNum).append("\">\n");
        tooltip.append("<div class=\"tooltip-header\">Line ").append(lineNum).append(": ")
                .append(mutations.size()).append(" mutation(s)</div>\n");

        for (LineMutation m : mutations) {
            String statusClass = statusClass(m.status);
            String statusText = statusText(m.status);
            String statusIcon = statusIcon(m.status);
            String operatorName = m.customLabel != null ? m.customLabel : getMutationOperatorName(m.original, m.mutated);

            tooltip.append("<div class=\"mutation-entry ").append(statusClass)
                    .append("\" data-mutant-id=\"").append(escapeHtml(m.mutantId))
                    .append("\" data-mutant-ids=\"").append(escapeHtml(toJsonArray(m.candidateMutantIds))).append("\">")
                    .append("<div>").append(statusIcon).append(" ")
                    .append("<strong>").append(escapeHtml(operatorName)).append("</strong>")
                    .append(" (").append(escapeHtml(m.original)).append(" -> ").append(escapeHtml(m.mutated)).append(")")
                    .append(" at column ").append(m.columnStart)
                    .append("</div>")
                    .append("<div class=\"mutation-status ").append(statusClass).append("\">")
                    .append(statusText)
                    .append("</div></div>\n");
        }

        tooltip.append("</div>\n");
        return tooltip.toString();
    }

    private static String statusClass(MutantResult.MutantStatus status) {
        return switch (status) {
            case KILLED -> "killed";
            case NO_COVERAGE -> "no-coverage";
            default -> "survived";
        };
    }

    private static String statusText(MutantResult.MutantStatus status) {
        return switch (status) {
            case KILLED -> "KILLED";
            case NO_COVERAGE -> "NO COVERAGE";
            default -> "SURVIVED";
        };
    }

    private static String statusIcon(MutantResult.MutantStatus status) {
        return switch (status) {
            case KILLED -> "&#10003;";
            case NO_COVERAGE -> "!";
            default -> "&#10007;";
        };
    }

    private static String getMutationOperatorName(String original, String mutated) {
        if ("+".equals(original) && "-".equals(mutated)) return "AOR_PLUS_TO_MINUS";
        if ("-".equals(original) && "+".equals(mutated)) return "AOR_MINUS_TO_PLUS";
        if ("*".equals(original) && "/".equals(mutated)) return "AOR_MULTIPLY_TO_DIVIDE";
        if ("/".equals(original) && "*".equals(mutated)) return "AOR_DIVIDE_TO_MULTIPLY";
        if ("%".equals(original) && "*".equals(mutated)) return "AOR_MODULO_TO_MULTIPLY";
        if ("++".equals(original) && "--".equals(mutated)) return "AOR_INCREMENT_TO_DECREMENT";
        if ("--".equals(original) && "++".equals(mutated)) return "AOR_DECREMENT_TO_INCREMENT";

        if (">".equals(original) && ">=".equals(mutated)) return "ROR_GT_TO_GE";
        if (">".equals(original) && "<".equals(mutated)) return "ROR_GT_TO_LT";
        if (">".equals(original) && "<=".equals(mutated)) return "ROR_GT_TO_LE";
        if (">".equals(original) && "==".equals(mutated)) return "ROR_GT_TO_EQ";
        if (">".equals(original) && "!=".equals(mutated)) return "ROR_GT_TO_NE";

        if (">=".equals(original) && ">".equals(mutated)) return "ROR_GE_TO_GT";
        if (">=".equals(original) && "<".equals(mutated)) return "ROR_GE_TO_LT";
        if (">=".equals(original) && "<=".equals(mutated)) return "ROR_GE_TO_LE";

        if ("<".equals(original) && "<=".equals(mutated)) return "ROR_LT_TO_LE";
        if ("<".equals(original) && ">".equals(mutated)) return "ROR_LT_TO_GT";
        if ("<".equals(original) && ">=".equals(mutated)) return "ROR_LT_TO_GE";

        if ("<=".equals(original) && "<".equals(mutated)) return "ROR_LE_TO_LT";
        if ("<=".equals(original) && ">".equals(mutated)) return "ROR_LE_TO_GT";
        if ("<=".equals(original) && ">=".equals(mutated)) return "ROR_LE_TO_GE";

        if ("==".equals(original) && "!=".equals(mutated)) return "ROR_EQ_TO_NE";
        if ("!=".equals(original) && "==".equals(mutated)) return "ROR_NE_TO_EQ";

        if ("&&".equals(original) && "||".equals(mutated)) return "LCR_AND_TO_OR";
        if ("||".equals(original) && "&&".equals(mutated)) return "LCR_OR_TO_AND";
        if ("and".equals(original) && "or".equals(mutated)) return "LCR_AND_TO_OR";
        if ("or".equals(original) && "and".equals(mutated)) return "LCR_OR_TO_AND";

        if ("!".equals(original)) return "UOI_NEGATE";
        if ("not".equals(original)) return "UOI_NEGATE";

        if ("&".equals(original) && "|".equals(mutated)) return "BOR_AND_TO_OR";
        if ("|".equals(original) && "&".equals(mutated)) return "BOR_OR_TO_AND";
        if ("^".equals(original) && "&".equals(mutated)) return "BOR_XOR_TO_AND";
        if ("<<".equals(original) && ">>".equals(mutated)) return "BOR_LSHIFT_TO_RSHIFT";
        if (">>".equals(original) && "<<".equals(mutated)) return "BOR_RSHIFT_TO_LSHIFT";

        // Return Value Mutations
        if ("true".equals(original) && "false".equals(mutated))  return "RVM_TRUE_TO_FALSE";
        if ("false".equals(original) && "true".equals(mutated))  return "RVM_FALSE_TO_TRUE";
        if ("0".equals(original) && "1".equals(mutated))          return "RVM_ZERO_TO_ONE";
        if ("1".equals(original) && "0".equals(mutated))          return "RVM_NONZERO_TO_ZERO";

        return "MUTATION_" + sanitizeOperatorName(original) + "_TO_" + sanitizeOperatorName(mutated);
    }

    private static String sanitizeOperatorName(String value) {
        if (value == null || value.isEmpty()) {
            return "UNKNOWN";
        }
        String sanitized = value.toUpperCase().replaceAll("[^A-Z0-9]+", "_");
        sanitized = sanitized.replaceAll("^_+", "").replaceAll("_+$", "");
        return sanitized.isEmpty() ? "UNKNOWN" : sanitized;
    }

    private static String generateLegend() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"legend\">\n");
        sb.append("    <h4>Legend</h4>\n");
        sb.append("    <div class=\"legend-items\">\n");
        sb.append("        <div class=\"legend-item\"><div class=\"legend-box\" style=\"background: #d4edda;\"></div><span>All mutations killed</span></div>\n");
        sb.append("        <div class=\"legend-item\"><div class=\"legend-box\" style=\"background: #f8d7da;\"></div><span>Some mutations survived</span></div>\n");
        sb.append("        <div class=\"legend-item\"><div class=\"legend-box\" style=\"background: #fff7ed;\"></div><span>No coverage</span></div>\n");
        sb.append("        <div class=\"legend-item\"><div class=\"legend-box\" style=\"background: #fff3cd;\"></div><span>Hover for details</span></div>\n");
        sb.append("    </div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }

    private static String generateFooter() {
        StringBuilder sb = new StringBuilder();

        sb.append("<script>\n");
        sb.append("async function applyEquivalentFilteringToFilePage() {\n");
        sb.append("    try {\n");
        sb.append("        const res = await fetch('http://localhost:8081/api/equivalent-mutants');\n");
        sb.append("        if (!res.ok) throw new Error('HTTP ' + res.status);\n");
        sb.append("        const data = await res.json();\n");
        sb.append("        const hiddenIds = new Set(data.hiddenMutantIds || []);\n");
        sb.append("\n");
        sb.append("        function getMutantIds(entry) {\n");
        sb.append("            try {\n");
        sb.append("                const ids = JSON.parse(entry.dataset.mutantIds || '[]');\n");
        sb.append("                return ids.length ? ids : [entry.dataset.mutantId].filter(Boolean);\n");
        sb.append("            } catch (e) {\n");
        sb.append("                return [entry.dataset.mutantId].filter(Boolean);\n");
        sb.append("            }\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        document.querySelectorAll('.mutation-entry[data-mutant-id]').forEach(entry => {\n");
        sb.append("            if (getMutantIds(entry).some(id => hiddenIds.has(id))) {\n");
        sb.append("                entry.style.display = 'none';\n");
        sb.append("            }\n");
        sb.append("        });\n");
        sb.append("\n");
        sb.append("        let visibleTotalCount = 0;\n");
        sb.append("        let visibleKilledCount = 0;\n");
        sb.append("        let visibleSurvivedCount = 0;\n");
        sb.append("        let visibleNoCoverageCount = 0;\n");
        sb.append("\n");
        sb.append("        document.querySelectorAll('.mutation-tooltip[data-line-number]').forEach(tooltip => {\n");
        sb.append("            const lineNumber = tooltip.dataset.lineNumber;\n");
        sb.append("            const row = document.querySelector('.code-line[data-line-number=\"' + lineNumber + '\"]');\n");
        sb.append("            const indicator = row ? row.querySelector('.mutation-indicator') : null;\n");
        sb.append("            const visibleEntries = tooltip.querySelectorAll('.mutation-entry:not([style*=\"display: none\"])');\n");
        sb.append("            const visibleSurvivedEntries = tooltip.querySelectorAll('.mutation-entry.survived:not([style*=\"display: none\"])');\n");
        sb.append("            const visibleKilledEntries = tooltip.querySelectorAll('.mutation-entry.killed:not([style*=\"display: none\"])');\n");
        sb.append("            const visibleNoCoverageEntries = tooltip.querySelectorAll('.mutation-entry.no-coverage:not([style*=\"display: none\"])');\n");
        sb.append("\n");
        sb.append("            if (visibleEntries.length === 0) {\n");
        sb.append("                tooltip.style.display = 'none';\n");
        sb.append("                if (row) {\n");
        sb.append("                    row.classList.remove('has-mutations');\n");
        sb.append("                    row.classList.remove('has-survived');\n");
        sb.append("                    row.classList.remove('has-no-coverage');\n");
        sb.append("                    row.classList.remove('all-killed');\n");
        sb.append("                }\n");
        sb.append("                if (indicator) {\n");
        sb.append("                    indicator.style.display = 'none';\n");
        sb.append("                }\n");
        sb.append("            } else {\n");
        sb.append("                tooltip.style.display = '';\n");
        sb.append("                if (row) {\n");
        sb.append("                    row.classList.add('has-mutations');\n");
        sb.append("                    row.classList.remove('has-survived');\n");
        sb.append("                    row.classList.remove('has-no-coverage');\n");
        sb.append("                    row.classList.remove('all-killed');\n");
        sb.append("                    if (visibleSurvivedEntries.length > 0) {\n");
        sb.append("                        row.classList.add('has-survived');\n");
        sb.append("                    } else if (visibleNoCoverageEntries.length > 0) {\n");
        sb.append("                        row.classList.add('has-no-coverage');\n");
        sb.append("                    } else if (visibleKilledEntries.length > 0) {\n");
        sb.append("                        row.classList.add('all-killed');\n");
        sb.append("                    }\n");
        sb.append("                }\n");
        sb.append("                if (indicator) {\n");
        sb.append("                    indicator.style.display = '';\n");
        sb.append("                    indicator.className = 'mutation-indicator';\n");
        sb.append("                    const statusTypeCount = (visibleSurvivedEntries.length > 0 ? 1 : 0) + (visibleKilledEntries.length > 0 ? 1 : 0) + (visibleNoCoverageEntries.length > 0 ? 1 : 0);\n");
        sb.append("                    if (statusTypeCount > 1) {\n");
        sb.append("                        indicator.classList.add('mixed');\n");
        sb.append("                    } else if (visibleSurvivedEntries.length > 0) {\n");
        sb.append("                        indicator.classList.add('survived');\n");
        sb.append("                    } else if (visibleNoCoverageEntries.length > 0) {\n");
        sb.append("                        indicator.classList.add('no-coverage');\n");
        sb.append("                    } else if (visibleKilledEntries.length > 0) {\n");
        sb.append("                        indicator.classList.add('killed');\n");
        sb.append("                    }\n");
        sb.append("                }\n");
        sb.append("            }\n");
        sb.append("\n");
        sb.append("            visibleTotalCount += visibleEntries.length;\n");
        sb.append("            visibleKilledCount += visibleKilledEntries.length;\n");
        sb.append("            visibleSurvivedCount += visibleSurvivedEntries.length;\n");
        sb.append("            visibleNoCoverageCount += visibleNoCoverageEntries.length;\n");
        sb.append("        });\n");
        sb.append("\n");
        sb.append("        const totalEl = document.getElementById('top-total-count');\n");
        sb.append("        if (totalEl) totalEl.textContent = visibleTotalCount;\n");
        sb.append("        const killedEl = document.getElementById('top-killed-count');\n");
        sb.append("        if (killedEl) killedEl.textContent = visibleKilledCount;\n");
        sb.append("        const survivedEl = document.getElementById('top-survived-count');\n");
        sb.append("        if (survivedEl) survivedEl.textContent = visibleSurvivedCount;\n");
        sb.append("        const noCoverageEl = document.getElementById('top-no-coverage-count');\n");
        sb.append("        if (noCoverageEl) noCoverageEl.textContent = visibleNoCoverageCount;\n");
        sb.append("        const scoreEl = document.getElementById('top-mutation-score');\n");
        sb.append("        if (scoreEl) scoreEl.textContent = (visibleTotalCount > 0 ? (visibleKilledCount * 100.0 / visibleTotalCount) : 0).toFixed(0) + '%';\n");
        sb.append("    } catch (e) {\n");
        sb.append("        console.error('Failed to apply equivalent filtering on file page:', e);\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("document.addEventListener('DOMContentLoaded', applyEquivalentFilteringToFilePage);\n");
        sb.append("</script>\n");

        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String toJsonArray(Set<String> values) {
        StringBuilder json = new StringBuilder("[");
        int index = 0;
        for (String value : values) {
            if (index++ > 0) {
                json.append(",");
            }
            json.append("\"").append(escapeJson(value)).append("\"");
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
}
