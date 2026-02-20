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
        
        SurvivorEntry(String file, int line, int column, String operator, String replacement, String sourceCodeSnippet) {
            this.file = file;
            this.line = line;
            this.column = column;
            this.operator = operator;
            this.replacement = replacement;
            this.sourceCodeSnippet = sourceCodeSnippet;
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
                String file = (String) mutant.getClass().getField("file").get(mutant);
                int line = (int) mutant.getClass().getField("line").get(mutant);
                int column = (int) mutant.getClass().getField("column").get(mutant);
                String operator = (String) mutant.getClass().getField("operator").get(mutant);
                String replacement = (String) mutant.getClass().getField("replacement").get(mutant);
                String original = "";
                try {
                    original = (String) mutant.getClass().getField("original").get(mutant);
                } catch (Exception e) {
                    original = "return x " + operator + " y;";
                }
                
                entries.add(new SurvivorEntry(file, line, column, operator, replacement, original));
            } catch (Exception e) {
                System.err.println("Error converting mutant: " + e.getMessage());
            }
        }
        
        return entries;
    }
    
    private static String generateHeader() {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n<html>\n<head>\n");
        sb.append("    <meta charset=\"UTF-8\">\n");
        sb.append("    <title>All Surviving Mutants</title>\n");
        sb.append("    <style>\n");
        sb.append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n");
        sb.append("        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background: #f5f5f5; padding: 20px; }\n");
        sb.append("        .container { max-width: 1600px; margin: 0 auto; background: white; box-shadow: 0 2px 10px rgba(0,0,0,0.1); border-radius: 8px; }\n");
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
        sb.append("        .table-container { padding: 30px; }\n");
        sb.append("        .survivors-table { width: 100%; border-collapse: collapse; }\n");
        sb.append("        .survivors-table thead { background: #f9fafb; position: sticky; top: 0; }\n");
        sb.append("        .survivors-table th { padding: 12px; text-align: left; font-weight: 600; color: #374151; border-bottom: 2px solid #e5e7eb; }\n");
        sb.append("        .survivors-table td { padding: 12px; border-bottom: 1px solid #e5e7eb; }\n");
        sb.append("        .survivors-table tbody tr { cursor: pointer; transition: background 0.2s; }\n");
        sb.append("        .survivors-table tbody tr:hover { background: #fef3f2; }\n");
        sb.append("        .file-badge { display: inline-block; padding: 4px 10px; background: #e0e7ff; color: #4338ca; border-radius: 4px; font-size: 13px; font-weight: 500; }\n");
        sb.append("        .location { font-family: 'Consolas', monospace; color: #666; font-size: 13px; }\n");
        sb.append("        .mutation-badge { padding: 4px 8px; background: #fee2e2; color: #dc2626; border-radius: 4px; font-family: monospace; font-size: 13px; font-weight: 600; }\n");
        sb.append("        .code-snippet { font-family: 'Consolas', monospace; background: #f9fafb; padding: 8px 12px; border-radius: 4px; font-size: 12px; color: #374151; max-width: 400px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }\n");
        sb.append("        .no-results { text-align: center; padding: 60px 20px; color: #9ca3af; }\n");
        sb.append("        .no-results-icon { font-size: 48px; margin-bottom: 15px; }\n");
        sb.append("        .action-link { color: #e74c3c; text-decoration: none; font-weight: 500; }\n");
        sb.append("        .action-link:hover { text-decoration: underline; }\n");
        sb.append("        .hidden { display: none !important; }\n");
        sb.append("    </style>\n");
        sb.append("</head>\n<body>\n");
        sb.append("<div class=\"container\">\n");
        return sb.toString();
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
        
        // Get unique files and operators for filters
        Set<String> files = new HashSet<>();
        Set<String> operators = new HashSet<>();
        for (SurvivorEntry s : survivors) {
            files.add(s.file);
            operators.add(s.operator);
        }
        
        // Search and filters
        sb.append("<div class=\"controls\">\n");
        sb.append("    <div class=\"search-box\">\n");
        sb.append("        <input type=\"text\" id=\"search-input\" placeholder=\"Search by file, line, operator, or code snippet...\">\n");
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
        sb.append("        <label>Operator:</label>\n");
        sb.append("        <select id=\"operator-filter\">\n");
        sb.append("            <option value=\"\">All Operators</option>\n");
        for (String op : operators.stream().sorted().toList()) {
            sb.append("            <option value=\"").append(escapeHtml(op)).append("\">").append(escapeHtml(op)).append("</option>\n");
        }
        sb.append("        </select>\n");
        sb.append("    </div>\n");
        sb.append("</div>\n");
        
        return sb.toString();
    }
    
    private static String generateSurvivorsTable(List<SurvivorEntry> survivors) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("<div class=\"table-container\">\n");
        sb.append("    <table class=\"survivors-table\">\n");
        sb.append("        <thead>\n");
        sb.append("            <tr>\n");
        sb.append("                <th>#</th>\n");
        sb.append("                <th>File</th>\n");
        sb.append("                <th>Location</th>\n");
        sb.append("                <th>Mutation</th>\n");
        sb.append("                <th>Code Snippet</th>\n");
        sb.append("                <th>Action</th>\n");
        sb.append("            </tr>\n");
        sb.append("        </thead>\n");
        sb.append("        <tbody id=\"survivors-tbody\">\n");
        
        for (int i = 0; i < survivors.size(); i++) {
            SurvivorEntry s = survivors.get(i);
            
            sb.append("            <tr class=\"survivor-row\" ");
            sb.append("data-file=\"").append(escapeHtml(s.file)).append("\" ");
            sb.append("data-line=\"").append(s.line).append("\" ");
            sb.append("data-operator=\"").append(escapeHtml(s.operator)).append("\" ");
            sb.append("onclick=\"window.location.href='pit-style-report.html#line-").append(s.line).append("'\">\n");
            
            sb.append("                <td>").append(i + 1).append("</td>\n");
            sb.append("                <td><span class=\"file-badge\">").append(escapeHtml(s.file)).append("</span></td>\n");
            sb.append("                <td class=\"location\">Line ").append(s.line).append(", Col ").append(s.column).append("</td>\n");
            sb.append("                <td><span class=\"mutation-badge\">").append(escapeHtml(s.operator)).append(" -> ").append(escapeHtml(s.replacement)).append("</span></td>\n");
            sb.append("                <td><code class=\"code-snippet\">").append(escapeHtml(s.sourceCodeSnippet)).append("</code></td>\n");
            sb.append("                <td><a href=\"pit-style-report.html#line-").append(s.line).append("\" class=\"action-link\">Jump to Source →</a></td>\n");
            sb.append("            </tr>\n");
        }
        
        sb.append("        </tbody>\n");
        sb.append("    </table>\n");
        
        // No results message
        sb.append("    <div id=\"no-results\" class=\"no-results hidden\">\n");
        sb.append("        <div class=\"no-results-icon\">Search</div>\n");
        sb.append("        <h3>No survivors found</h3>\n");
        sb.append("        <p>Try adjusting your search or filter criteria</p>\n");
        sb.append("    </div>\n");
        
        sb.append("</div>\n");
        
        return sb.toString();
    }
    
    private static String generateFooter() {
        StringBuilder sb = new StringBuilder();
        
        // Add JavaScript for filtering and search
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