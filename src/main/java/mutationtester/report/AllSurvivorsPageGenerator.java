package mutationtester.report;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mutationtester.equivalent.EquivalentMutantStore;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;

/**
 * Generates an "All Survivors" HTML page with filtering and search
 * functionality
 * Allows users to click on a survivor to jump directly to that line in the
 * source code
 */
public class AllSurvivorsPageGenerator {

    static class SurvivorEntry {
        String file;
        int line;
        int column;
        String operator;
        String replacement;
        String sourceCodeSnippet;
        String operatorName;
        String targetLink;
        String mutantId;
        Set<String> candidateMutantIds;

        SurvivorEntry(
                String file,
                int line,
                int column,
                String operator,
                String replacement,
                String sourceCodeSnippet,
                String operatorName,
                String targetLink,
                String mutantId,
                Set<String> candidateMutantIds) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.operator = operator;
            this.replacement = replacement;
            this.sourceCodeSnippet = sourceCodeSnippet;
            this.operatorName = operatorName;
            this.targetLink = targetLink;
            this.mutantId = mutantId;
            this.candidateMutantIds = candidateMutantIds;
        }
    }

    public static void generateAllSurvivorsPage(
            List<?> survivedMutants,
            String outputPath) throws IOException {
        generateAllSurvivorsPage(survivedMutants, outputPath, EquivalentsPageData.empty());
    }

    public static void generateAllSurvivorsPage(
            List<?> survivedMutants,
            String outputPath,
            EquivalentsPageData equivalents) throws IOException {

        List<SurvivorEntry> survivors = convertToSurvivorEntries(survivedMutants);

        StringBuilder html = new StringBuilder();
        html.append(generateHeader());
        html.append(generateSearchAndFilters(survivors));
        html.append(generateSurvivorsTable(survivors));
        html.append(equivalents.toDataScript());
        html.append(EquivalentsPageData.sharedScript());
        html.append(generateFooter());

        try (FileWriter writer = new FileWriter(outputPath, StandardCharsets.UTF_8)) {
            writer.write(html.toString());
        }
    }

    private static List<SurvivorEntry> convertToSurvivorEntries(List<?> mutants) {
        List<SurvivorEntry> entries = new ArrayList<>();

        for (Object mutant : mutants) {
            try {
                if (mutant instanceof MutantResult result) {
                    MutationPoint point = result.getMutationPoint();

                    String file = point.getSourceFile().getName();
                    int line = point.getLineNumber();
                    int column = point.getColumnNumber();
                    String operator = point.getOperator();
                    String replacement = point.getReplacement();
                    String sourceSnippet = readSourceLine(point.getSourceFile().toPath(), line);
                        String operatorName = normalizeMutationLabel(
                            point.getCustomLabel() != null ? point.getCustomLabel()
                                : getMutationOperatorName(operator, replacement));
                    String targetLink = "allFiles/" + stripExtension(file) + ".html#line-" + line;
                    String mutantId = EquivalentMutantStore.buildMutantId(result);
                    Set<String> candidateMutantIds = EquivalentMutantStore.buildCandidateMutantIds(result);

                    entries.add(new SurvivorEntry(
                            file,
                            line,
                            column,
                            operator,
                            replacement,
                            sourceSnippet,
                            operatorName,
                            targetLink,
                            mutantId,
                            candidateMutantIds));
                    continue;
                }

                String file = (String) mutant.getClass().getField("file").get(mutant);
                int line = (int) mutant.getClass().getField("line").get(mutant);
                int column = (int) mutant.getClass().getField("column").get(mutant);
                String operator = (String) mutant.getClass().getField("operator").get(mutant);
                String replacement = (String) mutant.getClass().getField("replacement").get(mutant);
                String customLabel = null;
                try {
                    customLabel = (String) mutant.getClass().getField("customLabel").get(mutant);
                } catch (Exception e) {
                }
                String operatorName = normalizeMutationLabel(
                    customLabel != null ? customLabel
                        : getMutationOperatorName(operator, replacement));
                String targetLink = "allFiles/" + stripExtension(file) + ".html#line-" + line;
                String mutantId = file + ":" + line + ":" + column + ":UNKNOWN:" + operator + ":" + replacement;

                String sourceSnippet;
                try {
                    sourceSnippet = (String) mutant.getClass().getField("original").get(mutant);
                } catch (Exception e) {
                    sourceSnippet = "";
                }

                entries.add(new SurvivorEntry(
                        file,
                        line,
                        column,
                        operator,
                        replacement,
                        sourceSnippet,
                        operatorName,
                        targetLink,
                        mutantId,
                        Set.of(mutantId)));
            } catch (Exception e) {
                System.err.println("Error converting mutant: " + e.getMessage());
            }
        }

        return entries;
    }

    private static String readSourceLine(java.nio.file.Path path, int lineNumber) {
        try {
            List<String> lines = java.nio.file.Files.readAllLines(path);
            if (lineNumber >= 1 && lineNumber <= lines.size()) {
                return lines.get(lineNumber - 1).trim();
            }
        } catch (Exception e) {
        }
        return "";
    }

    private static String normalizeMutationLabel(String label) {
        if (label == null) {
            return null;
        }
        return label.replace(" ? ", " -> ").replace(" → ", " -> ");
    }

    private static String getMutationOperatorName(String original, String mutated) {
        // Arithmetic Operator Replacement (AOR)
        if (original.equals("+") && mutated.equals("-"))
            return "AOR_PLUS_TO_MINUS";
        if (original.equals("-") && mutated.equals("+"))
            return "AOR_MINUS_TO_PLUS";
        if (original.equals("*") && mutated.equals("/"))
            return "AOR_MULTIPLY_TO_DIVIDE";
        if (original.equals("/") && mutated.equals("*"))
            return "AOR_DIVIDE_TO_MULTIPLY";
        if (original.equals("%") && mutated.equals("*"))
            return "AOR_MODULO_TO_MULTIPLY";

        // Relational Operator Replacement (ROR)
        if (original.equals(">") && mutated.equals(">="))
            return "ROR_GT_TO_GE";
        if (original.equals(">") && mutated.equals("<"))
            return "ROR_GT_TO_LT";
        if (original.equals(">=") && mutated.equals(">"))
            return "ROR_GE_TO_GT";
        if (original.equals("<") && mutated.equals("<="))
            return "ROR_LT_TO_LE";
        if (original.equals("<") && mutated.equals(">"))
            return "ROR_LT_TO_GT";
        if (original.equals("<=") && mutated.equals("<"))
            return "ROR_LE_TO_LT";
        if (original.equals("==") && mutated.equals("!="))
            return "ROR_EQ_TO_NE";
        if (original.equals("!=") && mutated.equals("=="))
            return "ROR_NE_TO_EQ";

        // Logical Connector Replacement (LCR)
        if (original.equals("&&") && mutated.equals("||"))
            return "LCR_AND_TO_OR";
        if (original.equals("||") && mutated.equals("&&"))
            return "LCR_OR_TO_AND";
        if (original.equals("and") && mutated.equals("or"))
            return "LCR_AND_TO_OR";
        if (original.equals("or") && mutated.equals("and"))
            return "LCR_OR_TO_AND";

        // Bitwise Operator Replacement (BOR)
        if (original.equals("&") && mutated.equals("|"))
            return "BOR_AND_TO_OR";
        if (original.equals("|") && mutated.equals("&"))
            return "BOR_OR_TO_AND";

        // Default
        return "MUTATION_" + original.toUpperCase().replaceAll("[^A-Z0-9]", "") +
                "_TO_" + mutated.toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private static String generateSearchAndFilters(List<SurvivorEntry> survivors) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("    <h1>All Surviving Mutants</h1>\n");
        sb.append("    <div class=\"subtitle\">Click any mutant to jump to its location in the source code</div>\n");
        sb.append("</div>\n");

        // Stats bar
        sb.append("<div class=\"stats\">\n");
        sb.append("    <div>Total Survivors: <strong id=\"total-count\">").append(survivors.size())
                .append("</strong></div>\n");
        sb.append("    <div>Showing: <strong id=\"visible-count\">").append(survivors.size())
                .append("</strong></div>\n");
        sb.append("</div>\n");

        // Get unique files and operator names for filters
        Set<String> files = new HashSet<>();
        Set<String> operatorNames = new HashSet<>();
        for (SurvivorEntry s : survivors) {
            files.add(s.file);
            operatorNames.add(s.operatorName);
        }

        // Search and filters
        sb.append("<div class=\"controls\">\n");
        sb.append("    <div class=\"search-box\">\n");
        sb.append(
                "        <input type=\"text\" id=\"search-input\" placeholder=\"Search by file, operator name, or code...\">\n");
        sb.append("    </div>\n");

        sb.append("    <div class=\"filter-group\">\n");
        sb.append("        <label>File:</label>\n");
        sb.append("        <select id=\"file-filter\">\n");
        sb.append("            <option value=\"\">All Files</option>\n");
        for (String file : files.stream().sorted().toList()) {
            sb.append("            <option value=\"").append(escapeHtml(file)).append("\">").append(escapeHtml(file))
                    .append("</option>\n");
        }
        sb.append("        </select>\n");
        sb.append("    </div>\n");

        sb.append("    <div class=\"filter-group\">\n");
        sb.append("        <label>Mutation Operator:</label>\n");
        sb.append("        <select id=\"operator-filter\">\n");
        sb.append("            <option value=\"\">All Operators</option>\n");
        for (String opName : operatorNames.stream().sorted().toList()) {
            sb.append("            <option value=\"").append(escapeHtml(opName)).append("\">")
                    .append(escapeHtml(opName)).append("</option>\n");
        }
        sb.append("        </select>\n");
        sb.append("    </div>\n");
        sb.append("</div>\n");

        return sb.toString();
    }

    private static String generateSurvivorsTable(List<SurvivorEntry> survivors) {
        StringBuilder sb = new StringBuilder();

        sb.append("<div class=\"table-container\">\n");
        // Control panel
        sb.append("    <div class=\"equivalent-panel\">\n");
        sb.append("        <div>\n");
        sb.append("            <div id=\"equivalent-stats\" class=\"equivalent-stats\">\n");
        sb.append("                <strong>0</strong> mutant(s) marked as equivalent\n");
        sb.append("            </div>\n");
        sb.append("            <div id=\"equivalent-changes\" class=\"equivalent-changes hidden\"></div>\n");
        sb.append("        </div>\n");
        sb.append("        <div class=\"equivalent-actions\">\n");
        sb.append("            <button type=\"button\" id=\"save-equivalents\" class=\"save-btn hidden\">Save to GitHub</button>\n");
        sb.append("            <button type=\"button\" id=\"discard-equivalents\" class=\"discard-btn hidden\">Discard changes</button>\n");
        sb.append("            <a id=\"equivalent-history\" class=\"history-link hidden\" target=\"_blank\" rel=\"noopener\">History</a>\n");
        sb.append("            <label class=\"filter-checkbox\">\n");
        sb.append(
                "                <input type=\"checkbox\" id=\"hide-equivalent\" checked onchange=\"toggleEquivalent()\">\n");
        sb.append("                Hide equivalent mutants\n");
        sb.append("            </label>\n");
        sb.append("        </div>\n");
        sb.append("    </div>\n");
        sb.append("    <div id=\"equivalent-save-status\" class=\"save-status hidden\"></div>\n");

        sb.append("    <table class=\"survivors-table\">\n");
        sb.append("        <thead>\n");
        sb.append("            <tr>\n");
        sb.append("                <th>#</th>\n");
        sb.append("                <th>File</th>\n");
        sb.append("                <th>Location</th>\n");
        sb.append("                <th>Mutation Operator</th>\n");
        sb.append("                <th>Transformation</th>\n");
        sb.append("                <th>Code Snippet</th>\n");
        sb.append("                <th>Action</th>\n");
        sb.append("            </tr>\n");
        sb.append("        </thead>\n");
        sb.append("        <tbody id=\"survivors-tbody\">\n");

        for (int i = 0; i < survivors.size(); i++) {
            SurvivorEntry s = survivors.get(i);

            sb.append("            <tr class=\"mutant-row survivor-row\" ");
            sb.append("data-file=\"").append(escapeHtml(s.file)).append("\" ");
            sb.append("data-line=\"").append(s.line).append("\" ");
            sb.append("data-column=\"").append(s.column).append("\" ");
            sb.append("data-operator=\"").append(escapeHtml(s.operatorName)).append("\" ");
            sb.append("data-replacement=\"").append(escapeHtml(s.replacement)).append("\" ");
            sb.append("data-mutant-id=\"").append(escapeHtml(s.mutantId)).append("\" ");
            sb.append("data-mutant-ids=\"").append(escapeHtml(toJsonArray(s.candidateMutantIds))).append("\" ");
            sb.append("onclick=\"window.location.href='").append(escapeHtml(s.targetLink)).append("'\">\n");

            sb.append("                <td>").append(i + 1).append("</td>\n");
            sb.append("                <td><span class=\"file-badge\">").append(escapeHtml(s.file))
                    .append("</span></td>\n");
            sb.append("                <td class=\"location\">Line ").append(s.line).append(", Col ").append(s.column)
                    .append("</td>\n");

            sb.append("                <td><span class=\"mutation-badge\">")
                    .append(escapeHtml(s.operatorName))
                    .append("</span></td>\n");

            sb.append("                <td class=\"transformation\">")
                    .append("<code>").append(escapeHtml(s.operator)).append("</code>")
                    .append(" -> ")
                    .append("<code>").append(escapeHtml(s.replacement)).append("</code>")
                    .append("</td>\n");

            sb.append("                <td><code class=\"code-snippet\">").append(escapeHtml(s.sourceCodeSnippet))
                    .append("</code></td>\n");
            sb.append("                <td class=\"action-cell\"></td>\n");
            sb.append("            </tr>\n");
        }

        sb.append("        </tbody>\n");
        sb.append("    </table>\n");

        sb.append("    <div id=\"no-results\" class=\"no-results hidden\">\n");
        sb.append("        <div class=\"no-results-icon\">&#128269;</div>\n");
        sb.append("        <h3>No survivors found</h3>\n");
        sb.append("        <p>Try adjusting your search or filter criteria</p>\n");
        sb.append("    </div>\n");

        sb.append("</div>\n");

        return sb.toString();
    }

    private static String generateHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <title>All Surviving Mutants</title>\n");
        sb.append("    <style>\n");
        sb.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append(
                "        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; padding: 20px; }\n");
        sb.append(
                "        .container { max-width: 1800px; margin: 0 auto; background: white; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border-radius: 8px; }\n");
        sb.append(
                "        .header { background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%); color: white; padding: 30px; border-radius: 8px 8px 0 0; }\n");
        sb.append("        .header h1 { font-size: 32px; margin-bottom: 10px; }\n");
        sb.append("        .header .subtitle { opacity: 0.9; font-size: 16px; }\n");
        sb.append(
                "        .controls { padding: 20px 30px; background: #fafafa; border-bottom: 1px solid #e5e7eb; display: flex; gap: 15px; flex-wrap: wrap; align-items: center; }\n");
        sb.append("        .search-box { flex: 1; min-width: 300px; }\n");
        sb.append(
                "        .search-box input { width: 100%; padding: 10px 15px; border: 2px solid #ddd; border-radius: 5px; font-size: 14px; }\n");
        sb.append("        .search-box input:focus { outline: none; border-color: #e74c3c; }\n");
        sb.append("        .filter-group { display: flex; gap: 10px; align-items: center; }\n");
        sb.append("        .filter-group label { font-weight: 500; color: #666; }\n");
        sb.append(
                "        .filter-group select { padding: 8px 12px; border: 2px solid #ddd; border-radius: 5px; font-size: 14px; cursor: pointer; }\n");
        sb.append(
                "        .stats { padding: 15px 30px; background: #fff3cd; display: flex; gap: 30px; font-size: 14px; }\n");
        sb.append("        .stats strong { color: #e74c3c; }\n");
        sb.append("        .table-container { padding: 30px; overflow-x: auto; }\n");
        sb.append("        .survivors-table { width: 100%; border-collapse: collapse; min-width: 1200px; }\n");
        sb.append("        .survivors-table thead { background: #f9fafb; position: sticky; top: 0; }\n");
        sb.append(
                "        .survivors-table th { padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 2px solid #e5e7eb; }\n");
        sb.append("        .survivors-table td { padding: 12px; border-bottom: 1px solid #e5e7eb; }\n");
        sb.append("        .survivors-table tbody tr { cursor: pointer; transition: background 0.2s; }\n");
        sb.append("        .survivors-table tbody tr:hover { background: #fef3f2; }\n");
        sb.append(
                "        .file-badge { display: inline-block; padding: 4px 10px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 13px; font-weight: 500; }\n");
        sb.append("        .location { font-family: 'Consolas', monospace; color: #666; font-size: 13px; }\n");
        sb.append(
                "        .mutation-badge { padding: 6px 12px; background: #fee2e2; color: #dc2626; border-radius: 4px; font-family: 'Consolas', monospace; font-size: 12px; font-weight: 600; white-space: nowrap; }\n");
        sb.append("        .transformation { font-family: 'Consolas', monospace; font-size: 13px; color: #666; }\n");
        sb.append(
                "        .transformation code { background: #f3f4f6; padding: 2px 6px; border-radius: 3px; color: #374151; }\n");
        sb.append(
                "        .code-snippet { font-family: 'Consolas', monospace; background: #f9fafb; padding: 8px 12px; border-radius: 4px; font-size: 12px; color: #374151; max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
        sb.append("        .no-results { text-align: center; padding: 60px 20px; color: #9ca3af; }\n");
        sb.append("        .no-results-icon { font-size: 48px; margin-bottom: 15px; }\n");
        sb.append("        .action-link { color: #e74c3c; text-decoration: none; font-weight: 500; }\n");
        sb.append("        .action-link:hover { text-decoration: underline; }\n");
        sb.append("        .hidden { display: none !important; }\n");
        sb.append("        .mark-equivalent-btn {\n");
        sb.append("            padding: 6px 12px;\n");
        sb.append("            border: 2px solid #94a3b8;\n");
        sb.append("            background: white;\n");
        sb.append("            color: #475569;\n");
        sb.append("            border-radius: 4px;\n");
        sb.append("            cursor: pointer;\n");
        sb.append("            font-size: 12px;\n");
        sb.append("            font-weight: 500;\n");
        sb.append("            transition: all 0.2s;\n");
        sb.append("        }\n");
        sb.append("        .mark-equivalent-btn:hover {\n");
        sb.append("            background: #f1f5f9;\n");
        sb.append("            border-color: #64748b;\n");
        sb.append("        }\n");
        sb.append("        .mark-equivalent-btn.marked {\n");
        sb.append("            background: #dbeafe;\n");
        sb.append("            border-color: #3b82f6;\n");
        sb.append("            color: #1e40af;\n");
        sb.append("        }\n");
        sb.append("        .mutant-row.equivalent {\n");
        sb.append("            background: #f8fafc !important;\n");
        sb.append("            opacity: 0.6;\n");
        sb.append("        }\n");
        sb.append("        .mutant-row.equivalent:hover {\n");
        sb.append("            background: #f1f5f9 !important;\n");
        sb.append("            opacity: 0.8;\n");
        sb.append("        }\n");
        sb.append("        .mutant-row.equivalent td {\n");
        sb.append("            text-decoration: line-through;\n");
        sb.append("            color: #94a3b8;\n");
        sb.append("        }\n");
        sb.append("        .mutant-row.equivalent .mark-equivalent-btn {\n");
        sb.append("            text-decoration: none;\n");
        sb.append("        }\n");
        sb.append("        .equivalent-panel {\n");
        sb.append("            background: #f0f9ff;\n");
        sb.append("            border: 1px solid #bae6fd;\n");
        sb.append("            padding: 12px 20px;\n");
        sb.append("            margin: 15px 0;\n");
        sb.append("            border-radius: 6px;\n");
        sb.append("            display: flex;\n");
        sb.append("            justify-content: space-between;\n");
        sb.append("            align-items: center;\n");
        sb.append("        }\n");
        sb.append("        .equivalent-stats {\n");
        sb.append("            font-size: 14px;\n");
        sb.append("            color: #0369a1;\n");
        sb.append("        }\n");
        sb.append("        .filter-checkbox {\n");
        sb.append("            display: flex;\n");
        sb.append("            align-items: center;\n");
        sb.append("            gap: 6px;\n");
        sb.append("            font-size: 14px;\n");
        sb.append("            cursor: pointer;\n");
        sb.append("        }\n");
        sb.append("        .equivalent-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }\n");
        sb.append("        .equivalent-changes { font-size: 13px; color: #b45309; margin-top: 4px; }\n");
        sb.append("        .save-btn { padding: 8px 14px; border: none; background: #2563eb; color: white; border-radius: 4px; cursor: pointer; font-weight: 600; }\n");
        sb.append("        .save-btn:hover { background: #1d4ed8; }\n");
        sb.append("        .discard-btn { padding: 8px 14px; border: 1px solid #cbd5e1; background: white; color: #475569; border-radius: 4px; cursor: pointer; }\n");
        sb.append("        .history-link { font-size: 14px; color: #2563eb; }\n");
        sb.append("        .save-status { background: #ecfdf5; border: 1px solid #a7f3d0; color: #065f46; padding: 12px 20px; margin: 0 0 15px; border-radius: 6px; font-size: 14px; line-height: 1.5; }\n");
        sb.append("        .save-status a { color: #047857; font-weight: 600; margin-right: 12px; }\n");
        sb.append("        .mutant-row.unsaved .mark-equivalent-btn { border-style: dashed; }\n");
        sb.append("    </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div class=\"container\">\n");
        return sb.toString();
    }

    private static String generateFooter() {
        StringBuilder sb = new StringBuilder();

        // Search and filter JavaScript
        sb.append("<script>\n");
        sb.append("const searchInput = document.getElementById('search-input');\n");
        sb.append("const fileFilter = document.getElementById('file-filter');\n");
        sb.append("const operatorFilter = document.getElementById('operator-filter');\n");
        sb.append("const rows = document.querySelectorAll('.survivor-row');\n");
        sb.append("const visibleCount = document.getElementById('visible-count');\n");
        sb.append("const noResults = document.getElementById('no-results');\n\n");

        sb.append("function filterRows() {\n");
        sb.append("    const searchTerm = searchInput.value.toLowerCase();\n");
        sb.append("    const fileValue = fileFilter.value;\n");
        sb.append("    const operatorValue = operatorFilter.value;\n");
        sb.append("    let visibleRowCount = 0;\n\n");

        sb.append("    rows.forEach(row => {\n");
        sb.append("        const file = row.dataset.file;\n");
        sb.append("        const line = row.dataset.line;\n");
        sb.append("        const operator = row.dataset.operator;\n");
        sb.append("        const text = row.textContent.toLowerCase();\n\n");

        sb.append("        const matchesSearch = text.includes(searchTerm);\n");
        sb.append("        const matchesFile = !fileValue || file === fileValue;\n");
        sb.append("        const matchesOperator = !operatorValue || operator === operatorValue;\n\n");

        sb.append("        if (matchesSearch && matchesFile && matchesOperator) {\n");
        sb.append("            row.classList.remove('hidden');\n");
        sb.append("            visibleRowCount++;\n");
        sb.append("        } else {\n");
        sb.append("            row.classList.add('hidden');\n");
        sb.append("        }\n");
        sb.append("    });\n\n");

        sb.append("    visibleCount.textContent = visibleRowCount;\n");
        sb.append("    noResults.classList.toggle('hidden', visibleRowCount > 0);\n");
        sb.append("}\n\n");

        sb.append("searchInput.addEventListener('input', () => toggleEquivalent());\n");
        sb.append("fileFilter.addEventListener('change', () => toggleEquivalent());\n");
        sb.append("operatorFilter.addEventListener('change', () => toggleEquivalent());\n");
        sb.append("</script>\n\n");

        // Mark as Equivalent JavaScript
        sb.append("<script>\n");
        sb.append(EQUIVALENT_MANAGER_SCRIPT);

        sb.append("function toggleEquivalent() {\n");
        sb.append("    const hide = document.getElementById('hide-equivalent').checked;\n");
        sb.append("    const searchTerm = document.getElementById('search-input').value.toLowerCase();\n");
        sb.append("    const fileValue = document.getElementById('file-filter').value;\n");
        sb.append("    const operatorValue = document.getElementById('operator-filter').value;\n");
        sb.append("    let visible = 0;\n");
        sb.append("\n");
        sb.append("    document.querySelectorAll('.mutant-row').forEach(row => {\n");
        sb.append("        const isEquivalent = row.classList.contains('equivalent');\n");
        sb.append("        const file = row.dataset.file || '';\n");
        sb.append("        const operator = row.dataset.operator || '';\n");
        sb.append("        const text = row.textContent.toLowerCase();\n");
        sb.append("\n");
        sb.append("        const matchesSearch = text.includes(searchTerm);\n");
        sb.append("        const matchesFile = !fileValue || file === fileValue;\n");
        sb.append("        const matchesOperator = !operatorValue || operator === operatorValue;\n");
        sb.append("        const passesFilters = matchesSearch && matchesFile && matchesOperator;\n");
        sb.append("\n");
        sb.append("        if (!passesFilters) {\n");
        sb.append("            row.style.display = 'none';\n");
        sb.append("            return;\n");
        sb.append("        }\n");
        sb.append("\n");
        sb.append("        if (hide && isEquivalent) {\n");
        sb.append("            row.style.display = 'none';\n");
        sb.append("        } else {\n");
        sb.append("            row.style.display = '';\n");
        sb.append("            visible++;\n");
        sb.append("        }\n");
        sb.append("    });\n");
        sb.append("\n");
        sb.append("    const countEl = document.getElementById('visible-count');\n");
        sb.append("    if (countEl) countEl.textContent = visible;\n");
        sb.append("\n");
        sb.append("    const noResults = document.getElementById('no-results');\n");
        sb.append("    if (noResults) noResults.classList.toggle('hidden', visible > 0);\n");
        sb.append("}\n");
        sb.append("</script>\n");

        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }

    private static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0) {
            return fileName;
        }
        return fileName.substring(0, dot);
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

    /**
     * Marking equivalent mutants. Three ways to save, picked when the page loads:
     *   github   - report from GitHub Actions: changes are kept in the page and
     *              "Save to GitHub" opens GitHub's new-file page prefilled with a
     *              pending/ batch on the mutation-data branch (one Commit click).
     *   server   - local report with the API server running: saved immediately,
     *              as before.
     *   download - local report, no server: changes are kept in the page and
     *              downloaded as equivalent-mutants.json.
     * Links longer than GitHub accepts are split into several batch files.
     */
    static final String EQUIVALENT_MANAGER_SCRIPT = """
            const MAX_EQUIVALENT_URL_LENGTH = 7000;

            function describeEquivalentBatch(batch) {
                const parts = [];
                if (batch.add.length) parts.push(batch.add.length + ' added');
                if (batch.remove.length) parts.push(batch.remove.length + ' removed');
                return parts.join(', ');
            }

            function equivalentBatchUrl(data, fileName, batch, partCount, partIndex) {
                const body = JSON.stringify({ add: batch.add, remove: batch.remove }, null, 2) + '\\n';
                const message = 'Mark equivalent mutants: ' + describeEquivalentBatch(batch)
                    + (partCount > 1 ? ' (part ' + partIndex + ' of ' + partCount + ')' : '');
                return data.repoUrl + '/new/' + encodeURIComponent(data.branch) + '/' + encodeURIComponent(data.pendingDir)
                    + '?filename=' + encodeURIComponent(fileName)
                    + '&value=' + encodeURIComponent(body)
                    + '&message=' + encodeURIComponent(message);
            }

            function buildEquivalentBatches(adds, removes, data) {
                const stamp = new Date().toISOString().replace(/[:.]/g, '-');
                const suffix = Math.random().toString(36).slice(2, 6);
                const items = removes.map(id => ['remove', id]).concat(adds.map(id => ['add', id]));
                const groups = [];
                let current = { add: [], remove: [] };
                for (const [op, id] of items) {
                    const trial = { add: current.add.slice(), remove: current.remove.slice() };
                    trial[op].push(id);
                    const length = equivalentBatchUrl(data, stamp + '-' + suffix + '-p00.json', trial, 99, 99).length;
                    if (length > MAX_EQUIVALENT_URL_LENGTH && current.add.length + current.remove.length > 0) {
                        groups.push(current);
                        current = { add: [], remove: [] };
                    }
                    current[op].push(id);
                }
                if (current.add.length + current.remove.length > 0) groups.push(current);
                return groups.map((group, i) => {
                    const name = groups.length === 1
                        ? stamp + '-' + suffix + '.json'
                        : stamp + '-' + suffix + '-p' + String(i + 1).padStart(2, '0') + '.json';
                    return { url: equivalentBatchUrl(data, name, group, groups.length, i + 1), batch: group };
                });
            }

            class EquivalentMutantManager {
                constructor() {
                    this.data = readEquivalentsData();
                    this.applied = new Set();
                    this.pendingAdd = new Set();
                    this.pendingRemove = new Set();
                    this.mode = 'download';
                    this.initPromise = this.init();
                }

                getMutantIds(row) {
                    try {
                        const ids = JSON.parse(row.dataset.mutantIds || '[]');
                        return ids.length ? ids : [row.dataset.mutantId].filter(Boolean);
                    } catch (e) {
                        return [row.dataset.mutantId].filter(Boolean);
                    }
                }

                appliedId(row) {
                    return this.getMutantIds(row).find(id => this.applied.has(id)) || null;
                }

                state(row) {
                    const appliedId = this.appliedId(row);
                    if (appliedId) return this.pendingRemove.has(appliedId) ? 'removing' : 'marked';
                    return this.pendingAdd.has(row.dataset.mutantId) ? 'adding' : 'unmarked';
                }

                changeCount() {
                    return this.pendingAdd.size + this.pendingRemove.size;
                }

                async init() {
                    if (this.data.repoUrl) {
                        this.mode = 'github';
                        this.applied = new Set(this.data.hiddenMutantIds || []);
                    } else {
                        const fromServer = usesLocalServer(this.data) ? await fetchLocalServerIds() : null;
                        this.mode = fromServer ? 'server' : 'download';
                        this.applied = fromServer || new Set(this.data.hiddenMutantIds || []);
                    }

                    document.querySelectorAll('.mutant-row').forEach(row => {
                        const actionCell = row.querySelector('.action-cell');
                        if (actionCell && !actionCell.querySelector('.mark-equivalent-btn')) {
                            const btn = document.createElement('button');
                            btn.type = 'button';
                            btn.className = 'mark-equivalent-btn';
                            btn.onclick = async (e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                await this.toggle(row);
                            };
                            actionCell.appendChild(btn);
                        }
                    });

                    document.getElementById('save-equivalents').onclick = () => this.save();
                    document.getElementById('discard-equivalents').onclick = () => this.discard();
                    const history = document.getElementById('equivalent-history');
                    if (this.mode === 'github' && history) {
                        history.href = this.data.repoUrl + '/commits/' + encodeURIComponent(this.data.branch) + '/' + this.data.file;
                        history.classList.remove('hidden');
                    }
                    this.updateUI();
                }

                async toggle(row) {
                    const id = row.dataset.mutantId;
                    if (!id) return;
                    const appliedId = this.appliedId(row);
                    if (this.mode === 'server') {
                        await this.serverRequest(appliedId ? 'DELETE' : 'POST', appliedId || id);
                        return;
                    }
                    if (appliedId) {
                        if (this.pendingRemove.has(appliedId)) this.pendingRemove.delete(appliedId);
                        else this.pendingRemove.add(appliedId);
                    } else if (this.pendingAdd.has(id)) {
                        this.pendingAdd.delete(id);
                    } else {
                        this.pendingAdd.add(id);
                    }
                    this.updateUI();
                }

                async serverRequest(method, id) {
                    try {
                        const res = await fetch('http://localhost:8081/api/equivalent-mutants', {
                            method,
                            headers: { 'Content-Type': 'application/json' },
                            body: JSON.stringify({ id })
                        });
                        if (!res.ok) throw new Error('HTTP ' + res.status);
                        if (method === 'POST') this.applied.add(id);
                        else this.applied.delete(id);
                    } catch (e) {
                        // The server went away: keep the change here and offer a download instead.
                        this.mode = 'download';
                        if (method === 'POST') this.pendingAdd.add(id);
                        else this.pendingRemove.add(id);
                        this.showStatus([document.createTextNode(
                            'The local API server stopped responding. Your change is kept in this page; use Download to save it.')]);
                    }
                    this.updateUI();
                }

                updateUI() {
                    const labels = {
                        marked: '\\u2713 Equivalent',
                        adding: '\\u2713 Equivalent (unsaved)',
                        removing: 'Mark as Equivalent (unsaved)',
                        unmarked: 'Mark as Equivalent'
                    };
                    let count = 0;
                    document.querySelectorAll('.mutant-row').forEach(row => {
                        const state = this.state(row);
                        const isEq = state === 'marked' || state === 'adding';
                        row.classList.toggle('equivalent', isEq);
                        row.classList.toggle('unsaved', state === 'adding' || state === 'removing');
                        if (isEq) count++;
                        const btn = row.querySelector('.mark-equivalent-btn');
                        if (btn) {
                            btn.textContent = labels[state];
                            btn.classList.toggle('marked', isEq);
                        }
                    });

                    const statsEl = document.getElementById('equivalent-stats');
                    if (statsEl) statsEl.innerHTML = `<strong>${count}</strong> mutant(s) marked as equivalent`;

                    const changes = this.changeCount();
                    const changesEl = document.getElementById('equivalent-changes');
                    changesEl.textContent = changes + ' unsaved change' + (changes === 1 ? '' : 's');
                    changesEl.classList.toggle('hidden', changes === 0);

                    const saveBtn = document.getElementById('save-equivalents');
                    saveBtn.textContent = this.mode === 'github' ? 'Save to GitHub' : 'Download equivalent-mutants.json';
                    saveBtn.classList.toggle('hidden', changes === 0 || this.mode === 'server');
                    document.getElementById('discard-equivalents').classList.toggle('hidden', changes === 0);
                    toggleEquivalent();
                }

                discard() {
                    this.pendingAdd.clear();
                    this.pendingRemove.clear();
                    this.hideStatus();
                    this.updateUI();
                }

                save() {
                    if (this.changeCount() === 0) return;
                    if (this.mode === 'github') this.saveToGitHub();
                    else this.download();
                }

                commitPending() {
                    this.pendingRemove.forEach(id => this.applied.delete(id));
                    this.pendingAdd.forEach(id => this.applied.add(id));
                    this.pendingAdd.clear();
                    this.pendingRemove.clear();
                }

                saveToGitHub() {
                    const parts = buildEquivalentBatches([...this.pendingAdd], [...this.pendingRemove], this.data);
                    if (parts.length === 1) window.open(parts[0].url, '_blank', 'noopener');

                    const nodes = [];
                    if (parts.length === 1) {
                        nodes.push(document.createTextNode('GitHub opened in a new tab with your changes. Click '));
                        const strong = document.createElement('strong');
                        strong.textContent = 'Commit changes';
                        nodes.push(strong);
                        nodes.push(document.createTextNode(' there; the next workflow run applies them. No tab? '));
                    } else {
                        nodes.push(document.createTextNode('Your changes are split into ' + parts.length
                            + ' files because GitHub limits link length. Open each one and click Commit changes; the next workflow run applies them. '));
                    }
                    parts.forEach((part, i) => {
                        const link = document.createElement('a');
                        link.href = part.url;
                        link.target = '_blank';
                        link.rel = 'noopener';
                        link.textContent = parts.length === 1 ? 'Open it here' : 'Part ' + (i + 1) + ' (' + describeEquivalentBatch(part.batch) + ')';
                        nodes.push(link);
                    });
                    this.showStatus(nodes);
                    this.commitPending();
                    this.updateUI();
                }

                download() {
                    const ids = [...this.applied].filter(id => !this.pendingRemove.has(id));
                    this.pendingAdd.forEach(id => { if (!ids.includes(id)) ids.push(id); });
                    const blob = new Blob([JSON.stringify({ hiddenMutantIds: ids }, null, 2) + '\\n'], { type: 'application/json' });
                    const link = document.createElement('a');
                    link.href = URL.createObjectURL(blob);
                    link.download = 'equivalent-mutants.json';
                    document.body.appendChild(link);
                    link.click();
                    link.remove();
                    setTimeout(() => URL.revokeObjectURL(link.href), 1000);

                    this.showStatus([document.createTextNode(this.data.source === 'branch'
                        ? 'Downloaded equivalent-mutants.json. This project keeps its list on the ' + this.data.branch
                            + ' branch, so commit it there as ' + this.data.file + ' to apply it.'
                        : 'Downloaded equivalent-mutants.json. Put it in output/report/ where you run the tool (or start the API server) and re-run to apply it.')]);
                    this.commitPending();
                    this.updateUI();
                }

                showStatus(nodes) {
                    const el = document.getElementById('equivalent-save-status');
                    el.replaceChildren(...nodes);
                    el.classList.remove('hidden');
                }

                hideStatus() {
                    document.getElementById('equivalent-save-status').classList.add('hidden');
                }
            }

            let equivalentManager;
            document.addEventListener('DOMContentLoaded', async () => {
                equivalentManager = new EquivalentMutantManager();
                await equivalentManager.initPromise;
                toggleEquivalent();
            });

            """;
}
