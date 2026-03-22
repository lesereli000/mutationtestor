import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Generates an "All Survivors" HTML page with filtering and search functionality
 * Allows users to click on a survivor to jump directly to that line in the source code
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
    
        SurvivorEntry(String file, int line, int column, String operator, String replacement, 
            String sourceCodeSnippet, String operatorName) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.operator = operator;
            this.replacement = replacement;
            this.sourceCodeSnippet = sourceCodeSnippet;
            this.operatorName = operatorName;  
        }
    }
    
    public static void generateAllSurvivorsPage(
            List<?> survivedMutants,
            String outputPath
    ) throws IOException {
        
        List<SurvivorEntry> survivors = convertToSurvivorEntries(survivedMutants);
        
        StringBuilder html = new StringBuilder();
        html.append(generateHeader());
        html.append(generateSearchAndFilters(survivors));
        html.append(generateSurvivorsTable(survivors));
        html.append(generateFooter());
        
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }
    }
    
    private static List<SurvivorEntry> convertToSurvivorEntries(List<?> mutants) {
        List<SurvivorEntry> entries = new ArrayList<>();
        
        // Try to extract data using reflection
        for (Object mutant : mutants) {
            try {
                if (mutant instanceof MutantResult result) {
                    MutationPoint point = result.getMutationPoint();

                    String file = point.getSourceFile().getName();
                    int line = point.getLineNumber();
                    int column = point.getColumnNumber();
                    String operator = point.getOperator();
                    String replacement = point.getReplacement();
                    String original = "return x " + operator + " y;";
                    String operatorName = getMutationOperatorName(operator, replacement);

                    entries.add(new SurvivorEntry(file, line, column, operator, replacement, original, operatorName));
                    continue;
                }

                String file = (String) mutant.getClass().getField("file").get(mutant);
                int line = (int) mutant.getClass().getField("line").get(mutant);
                int column = (int) mutant.getClass().getField("column").get(mutant);
                String operator = (String) mutant.getClass().getField("operator").get(mutant);
                String replacement = (String) mutant.getClass().getField("replacement").get(mutant);

                String original;
                try {
                    original = (String) mutant.getClass().getField("original").get(mutant);
                } catch (Exception e) {
                    original = "return x " + operator + " y;";
                }
            
                // Generate mutation operator name
                String operatorName = getMutationOperatorName(operator, replacement);
            
                entries.add(new SurvivorEntry(file, line, column, operator, replacement, original, operatorName));
            } catch (Exception e) {
                System.err.println("Error converting mutant: " + e.getMessage());
            }
        }
    
        return entries;
    }
    


    private static String getMutationOperatorName(String original, String mutated) {
        // Arithmetic Operator Replacement (AOR)
        if (original.equals("+") && mutated.equals("-")) return "AOR_PLUS_TO_MINUS";
        if (original.equals("-") && mutated.equals("+")) return "AOR_MINUS_TO_PLUS";
        if (original.equals("*") && mutated.equals("/")) return "AOR_MULTIPLY_TO_DIVIDE";
        if (original.equals("/") && mutated.equals("*")) return "AOR_DIVIDE_TO_MULTIPLY";
        if (original.equals("%") && mutated.equals("*")) return "AOR_MODULO_TO_MULTIPLY";
    
        // Relational Operator Replacement (ROR)
        if (original.equals(">") && mutated.equals(">=")) return "ROR_GT_TO_GE";
        if (original.equals(">") && mutated.equals("<")) return "ROR_GT_TO_LT";
        if (original.equals(">=") && mutated.equals(">")) return "ROR_GE_TO_GT";
        if (original.equals("<") && mutated.equals("<=")) return "ROR_LT_TO_LE";
        if (original.equals("<") && mutated.equals(">")) return "ROR_LT_TO_GT";
        if (original.equals("<=") && mutated.equals("<")) return "ROR_LE_TO_LT";
        if (original.equals("==") && mutated.equals("!=")) return "ROR_EQ_TO_NE";
        if (original.equals("!=") && mutated.equals("==")) return "ROR_NE_TO_EQ";
    
        // Logical Connector Replacement (LCR)
        if (original.equals("&&") && mutated.equals("||")) return "LCR_AND_TO_OR";
        if (original.equals("||") && mutated.equals("&&")) return "LCR_OR_TO_AND";
        if (original.equals("and") && mutated.equals("or")) return "LCR_AND_TO_OR";
        if (original.equals("or") && mutated.equals("and")) return "LCR_OR_TO_AND";
    
        // Bitwise Operator Replacement (BOR)
        if (original.equals("&") && mutated.equals("|")) return "BOR_AND_TO_OR";
        if (original.equals("|") && mutated.equals("&")) return "BOR_OR_TO_AND";
    
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
        sb.append("    <div>Total Survivors: <strong id=\"total-count\">").append(survivors.size()).append("</strong></div>\n");
        sb.append("    <div>Showing: <strong id=\"visible-count\">").append(survivors.size()).append("</strong></div>\n");
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
        sb.append("        <input type=\"text\" id=\"search-input\" placeholder=\"Search by file, operator name, or code...\">\n");
        sb.append("    </div>\n");
    
        sb.append("    <div class=\"filter-group\">\n");
        sb.append("        <label>File:</label>\n");
        sb.append("        <select id=\"file-filter\">\n");
        sb.append("            <option value=\"\">All Files</option>\n");
        for (String file : files.stream().sorted().toList()) {
            sb.append("            <option value=\"").append(escapeHtml(file)).append("\">").append(escapeHtml(file)).append("</option>\n");
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
        sb.append("        <div id=\"equivalent-stats\" class=\"equivalent-stats\">\n");
        sb.append("            <strong>0</strong> mutant(s) marked as equivalent\n");
        sb.append("        </div>\n");
        sb.append("        <label class=\"filter-checkbox\">\n");
        sb.append("            <input type=\"checkbox\" id=\"hide-equivalent\" onchange=\"toggleEquivalent()\">\n");
        sb.append("            Hide equivalent mutants\n");
        sb.append("        </label>\n");
        sb.append("    </div>\n");
        
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
            sb.append("onclick=\"window.location.href='pit-style-report.html#line-").append(s.line).append("'\">\n");
        
            sb.append("                <td>").append(i + 1).append("</td>\n");
            sb.append("                <td><span class=\"file-badge\">").append(escapeHtml(s.file)).append("</span></td>\n");
            sb.append("                <td class=\"location\">Line ").append(s.line).append(", Col ").append(s.column).append("</td>\n");
        
            sb.append("                <td><span class=\"mutation-badge\">")
            .append(escapeHtml(s.operatorName))
            .append("</span></td>\n");
        
            sb.append("                <td class=\"transformation\">")
            .append("<code>").append(escapeHtml(s.operator)).append("</code>")
            .append(" → ")
            .append("<code>").append(escapeHtml(s.replacement)).append("</code>")
            .append("</td>\n");
        
            sb.append("                <td><code class=\"code-snippet\">").append(escapeHtml(s.sourceCodeSnippet)).append("</code></td>\n");
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
        sb.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; padding: 20px; }\n");
        sb.append("        .container { max-width: 1800px; margin: 0 auto; background: white; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border-radius: 8px; }\n");
        sb.append("        .header { background: linear-gradient(135deg, #e74c3c 0%, #c0392b 100%); color: white; padding: 30px; border-radius: 8px 8px 0 0; }\n");
        sb.append("        .header h1 { font-size: 32px; margin-bottom: 10px; }\n");
        sb.append("        .header .subtitle { opacity: 0.9; font-size: 16px; }\n");
        sb.append("        .controls { padding: 20px 30px; background: #fafafa; border-bottom: 1px solid #e5e7eb; display: flex; gap: 15px; flex-wrap: wrap; align-items: center; }\n");
        sb.append("        .search-box { flex: 1; min-width: 300px; }\n");
        sb.append("        .search-box input { width: 100%; padding: 10px 15px; border: 2px solid #ddd; border-radius: 5px; font-size: 14px; }\n");
        sb.append("        .search-box input:focus { outline: none; border-color: #e74c3c; }\n");
        sb.append("        .filter-group { display: flex; gap: 10px; align-items: center; }\n");
        sb.append("        .filter-group label { font-weight: 500; color: #666; }\n");
        sb.append("        .filter-group select { padding: 8px 12px; border: 2px solid #ddd; border-radius: 5px; font-size: 14px; cursor: pointer; }\n");
        sb.append("        .stats { padding: 15px 30px; background: #fff3cd; display: flex; gap: 30px; font-size: 14px; }\n");
        sb.append("        .stats strong { color: #e74c3c; }\n");
        sb.append("        .table-container { padding: 30px; overflow-x: auto; }\n");
        sb.append("        .survivors-table { width: 100%; border-collapse: collapse; min-width: 1200px; }\n");
        sb.append("        .survivors-table thead { background: #f9fafb; position: sticky; top: 0; }\n");
        sb.append("        .survivors-table th { padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 2px solid #e5e7eb; }\n");
        sb.append("        .survivors-table td { padding: 12px; border-bottom: 1px solid #e5e7eb; }\n");
        sb.append("        .survivors-table tbody tr { cursor: pointer; transition: background 0.2s; }\n");
        sb.append("        .survivors-table tbody tr:hover { background: #fef3f2; }\n");
        sb.append("        .file-badge { display: inline-block; padding: 4px 10px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 13px; font-weight: 500; }\n");
        sb.append("        .location { font-family: 'Consolas', monospace; color: #666; font-size: 13px; }\n");
        sb.append("        .mutation-badge { padding: 6px 12px; background: #fee2e2; color: #dc2626; border-radius: 4px; font-family: 'Consolas', monospace; font-size: 12px; font-weight: 600; white-space: nowrap; }\n");
        sb.append("        .transformation { font-family: 'Consolas', monospace; font-size: 13px; color: #666; }\n");
        sb.append("        .transformation code { background: #f3f4f6; padding: 2px 6px; border-radius: 3px; color: #374151; }\n");
        sb.append("        .code-snippet { font-family: 'Consolas', monospace; background: #f9fafb; padding: 8px 12px; border-radius: 4px; font-size: 12px; color: #374151; max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
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
        
        sb.append("searchInput.addEventListener('input', filterRows);\n");
        sb.append("fileFilter.addEventListener('change', filterRows);\n");
        sb.append("operatorFilter.addEventListener('change', filterRows);\n");
        sb.append("</script>\n\n");
        
        // Mark as Equivalent JavaScript
        sb.append("<script>\n");
        sb.append("class EquivalentMutantManager {\n");
        sb.append("    constructor() {\n");
        sb.append("        this.storageKey = 'mutation_testing_equivalent_mutants';\n");
        sb.append("        this.equivalentMutants = this.load();\n");
        sb.append("        this.init();\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    load() {\n");
        sb.append("        try {\n");
        sb.append("            const data = localStorage.getItem(this.storageKey);\n");
        sb.append("            return data ? JSON.parse(data) : [];\n");
        sb.append("        } catch (e) {\n");
        sb.append("            console.error('Error loading:', e);\n");
        sb.append("            return [];\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    save() {\n");
        sb.append("        try {\n");
        sb.append("            localStorage.setItem(this.storageKey, JSON.stringify(this.equivalentMutants));\n");
        sb.append("            this.updateUI();\n");
        sb.append("        } catch (e) {\n");
        sb.append("            console.error('Error saving:', e);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    getMutantId(file, line, column, operator, replacement) {\n");
        sb.append("        return `${file}:${line}:${column}:${operator}->${replacement}`;\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    isEquivalent(mutantId) {\n");
        sb.append("        return this.equivalentMutants.some(m => m.id === mutantId);\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    markAsEquivalent(mutantId, file, line, column, operator, replacement) {\n");
        sb.append("        if (this.isEquivalent(mutantId)) return;\n");
        sb.append("        this.equivalentMutants.push({\n");
        sb.append("            id: mutantId,\n");
        sb.append("            file, line, column, operator, replacement,\n");
        sb.append("            markedAt: new Date().toISOString()\n");
        sb.append("        });\n");
        sb.append("        this.save();\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    unmarkAsEquivalent(mutantId) {\n");
        sb.append("        this.equivalentMutants = this.equivalentMutants.filter(m => m.id !== mutantId);\n");
        sb.append("        this.save();\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    updateUI() {\n");
        sb.append("        document.querySelectorAll('.mutant-row').forEach(row => {\n");
        sb.append("            const mutantId = row.dataset.mutantId;\n");
        sb.append("            const isEq = this.isEquivalent(mutantId);\n");
        sb.append("            \n");
        sb.append("            if (isEq) {\n");
        sb.append("                row.classList.add('equivalent');\n");
        sb.append("            } else {\n");
        sb.append("                row.classList.remove('equivalent');\n");
        sb.append("            }\n");
        sb.append("            \n");
        sb.append("            const btn = row.querySelector('.mark-equivalent-btn');\n");
        sb.append("            if (btn) {\n");
        sb.append("                btn.textContent = isEq ? '✓ Equivalent' : 'Mark as Equivalent';\n");
        sb.append("                btn.classList.toggle('marked', isEq);\n");
        sb.append("            }\n");
        sb.append("        });\n");
        sb.append("        \n");
        sb.append("        const statsEl = document.getElementById('equivalent-stats');\n");
        sb.append("        if (statsEl) {\n");
        sb.append("            statsEl.innerHTML = `<strong>${this.equivalentMutants.length}</strong> mutant(s) marked as equivalent`;\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    \n");
        sb.append("    init() {\n");
        sb.append("        document.querySelectorAll('.mutant-row').forEach(row => {\n");
        sb.append("            const file = row.dataset.file;\n");
        sb.append("            const line = row.dataset.line;\n");
        sb.append("            const column = row.dataset.column;\n");
        sb.append("            const operator = row.dataset.operator;\n");
        sb.append("            const replacement = row.dataset.replacement;\n");
        sb.append("            \n");
        sb.append("            const mutantId = this.getMutantId(file, line, column, operator, replacement);\n");
        sb.append("            row.dataset.mutantId = mutantId;\n");
        sb.append("            \n");
        sb.append("            const actionCell = row.querySelector('.action-cell');\n");
        sb.append("            if (actionCell) {\n");
        sb.append("                const btn = document.createElement('button');\n");
        sb.append("                btn.className = 'mark-equivalent-btn';\n");
        sb.append("                btn.textContent = this.isEquivalent(mutantId) ? '✓ Equivalent' : 'Mark as Equivalent';\n");
        sb.append("                if (this.isEquivalent(mutantId)) btn.classList.add('marked');\n");
        sb.append("                \n");
        sb.append("                btn.onclick = (e) => {\n");
        sb.append("                    e.stopPropagation();\n");
        sb.append("                    if (this.isEquivalent(mutantId)) {\n");
        sb.append("                        if (confirm('Remove \"equivalent\" marking from this mutant?')) {\n");
        sb.append("                            this.unmarkAsEquivalent(mutantId);\n");
        sb.append("                        }\n");
        sb.append("                    } else {\n");
        sb.append("                        this.markAsEquivalent(mutantId, file, line, column, operator, replacement);\n");
        sb.append("                    }\n");
        sb.append("                };\n");
        sb.append("                \n");
        sb.append("                actionCell.appendChild(btn);\n");
        sb.append("            }\n");
        sb.append("        });\n");
        sb.append("        \n");
        sb.append("        this.updateUI();\n");
        sb.append("    }\n");
        sb.append("}\n");
        sb.append("\n");
        sb.append("let equivalentManager;\n");
        sb.append("document.addEventListener('DOMContentLoaded', () => {\n");
        sb.append("    equivalentManager = new EquivalentMutantManager();\n");
        sb.append("});\n");
        sb.append("\n");
        sb.append("function toggleEquivalent() {\n");
        sb.append("    const hide = document.getElementById('hide-equivalent').checked;\n");
        sb.append("    document.querySelectorAll('.mutant-row.equivalent').forEach(row => {\n");
        sb.append("        row.style.display = hide ? 'none' : '';\n");
        sb.append("    });\n");
        sb.append("    \n");
        sb.append("    const visible = document.querySelectorAll('.mutant-row:not([style*=\"display: none\"])').length;\n");
        sb.append("    const countEl = document.getElementById('visible-count');\n");
        sb.append("    if (countEl) countEl.textContent = visible;\n");
        sb.append("}\n");
        sb.append("</script>\n");
        
        sb.append("</div>\n</body>\n</html>");
        return sb.toString();
    }
    
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}