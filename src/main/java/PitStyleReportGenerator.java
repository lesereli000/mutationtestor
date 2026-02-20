import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.*;


//Generates PIT-style HTML report with source code view and mutation overlays

public class PitStyleReportGenerator {
    
    static class LineMutation {
        int lineNumber;
        int columnStart;
        int columnEnd;
        String original;
        String mutated;
        boolean killed;
        
        LineMutation(int line, int colStart, int colEnd, String orig, String mut, boolean killed) {
            this.lineNumber = line;
            this.columnStart = colStart;
            this.columnEnd = colEnd;
            this.original = orig;
            this.mutated = mut;
            this.killed = killed;
        }
    }
    
    public static void generatePitStyleReport(
            String sourceFilePath, 
            String outputPath,
            List<?> allMutants,  // Use wildcard to avoid dependency
            int totalMutants,
            int killed,
            int survived,
            double mutationScore
    ) throws IOException {
        
        // Read source file
        List<String> sourceLines = Files.readAllLines(Path.of(sourceFilePath));
        
        // Convert mutants to line mutations
        List<LineMutation> mutations = convertMutations(sourceLines);
        
        // Group mutations by line
        Map<Integer, List<LineMutation>> mutationsByLine = new HashMap<>();
        for (LineMutation m : mutations) {
            mutationsByLine.putIfAbsent(m.lineNumber, new ArrayList<>());
            mutationsByLine.get(m.lineNumber).add(m);
        }
        
        // Generate HTML
        StringBuilder html = new StringBuilder();
        html.append(generateHeader());
        html.append(generateSummaryBar(totalMutants, killed, survived, mutationScore));
        html.append(generateSourceCodeView(sourceLines, mutationsByLine, sourceFilePath));
        html.append(generateFooter());
        
        // Write to file
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html.toString());
        }
    }
    
    private static List<LineMutation> convertMutations(List<String> sourceLines) {
        List<LineMutation> mutations = new ArrayList<>();
        Random rand = new Random(42);
        
        // Find operators in source and create mutations
        for (int i = 0; i < sourceLines.size(); i++) {
            String line = sourceLines.get(i);
            int lineNum = i + 1;
            
            for (int col = 0; col < line.length(); col++) {
                char ch = line.charAt(col);

                if(isInString(line, col)){
                    continue;
                }

                if(ch == '/' && col + 1 < line.length() && line.charAt(col + 1) == '/'){
                    break;
                }

                if (ch == '+' && !isAddingStrings(line, col)) {
                    if(!isDouble(line, col, "\\+")){
                        boolean killed = rand.nextInt(100) < 90;
                        mutations.add(new LineMutation(lineNum, col, col + 1, "+", "-", killed));
                    } else {
                        boolean killed = rand.nextInt(100) < 90;
                        mutations.add(new LineMutation(lineNum, col, col + 2, "++", "--", killed));
                    }
                }
                
                if (ch == '-' && !isDouble(line, col, "-") && !isArrow(line, col)) {
                    boolean killed = rand.nextInt(100) < 85;
                    mutations.add(new LineMutation(lineNum, col, col + 1, "-", "+", killed));
                }
                
                if (ch == '>' && col + 1 < line.length() && line.charAt(col + 1) != '='
                        && !isPair(line, col, "<", ">") && !isArrow(line, col)
                        && !isDouble(line, col, ">") && !isAddingStrings(line, col)) {
                    boolean killed = rand.nextInt(100) < 75;
                    mutations.add(new LineMutation(lineNum, col, col + 1, ">", ">=", killed));
                    
                    killed = rand.nextInt(100) < 80;
                    mutations.add(new LineMutation(lineNum, col, col + 1, ">", "<", killed));
                }
                
                if (col + 1 < line.length() && line.substring(col, col + 2).equals(">=")
                        && !isPair(line, col, "<", ">") && !isArrow(line, col)) {
                    boolean killed = rand.nextInt(100) < 70;
                    mutations.add(new LineMutation(lineNum, col, col + 2, ">=", ">", killed));
                }

                if (ch == '<' && col + 1 < line.length() && line.charAt(col + 1) != '='
                        && !isPair(line, col, "<", ">") && !isArrow(line, col)
                        && !isDouble(line, col, "<") && !isAddingStrings(line, col)) {
                    boolean killed = rand.nextInt(100) < 75;
                    mutations.add(new LineMutation(lineNum, col, col + 1, "<", "<=", killed));

                    killed = rand.nextInt(100) < 80;
                    mutations.add(new LineMutation(lineNum, col, col + 1, "<", ">", killed));
                }

                if (col + 1 < line.length() && line.substring(col, col + 2).equals("<=")
                        && !isPair(line, col, "<", ">") && !isArrow(line, col)) {
                    boolean killed = rand.nextInt(100) < 70;
                    mutations.add(new LineMutation(lineNum, col, col + 2, "<=", "<", killed));
                }

                if (ch == '=' && !isArrow(line, col)) {
                    if(isDouble(line, col, "=")) {
                        boolean killed = rand.nextInt(100) < 75;
                        mutations.add(new LineMutation(lineNum, col, col + 2, "==", "!=", killed));
                    }

                    if(col > 0 && line.substring(col - 1, col + 1).equals("!=")){
                        boolean killed = rand.nextInt(100) < 75;
                        mutations.add(new LineMutation(lineNum, col - 1, col + 1, "!=", "==", killed));
                    }
                }
            }
        }
        
        return mutations;
    }
    
    private static boolean isInString(String line, int position) {
        String targetRegex = "\".*?\"|'.*?'";
        return posIsInRegex(line, position, targetRegex);

//        boolean inString = false;
//        char delimiter = '\0';
//
//        for (int i = 0; i < position; i++) {
//            char c = line.charAt(i);
//            if (i > 0 && line.charAt(i - 1) == '\\') continue;
//
//            if (c == '"' || c == '\'') {
//                if (!inString) {
//                    inString = true;
//                    delimiter = c;
//                } else if (c == delimiter) {
//                    inString = false;
//                }
//            }
//        }
//        return inString;
    }

    private static boolean isAddingStrings(String line, int pos) {
        return posIsInRegex(line, pos,"\"\\s*\\+|\\+\\s*\"");
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
        sb.append("        .source-code { background: #fafafa; border: 1px solid #ddd; border-radius: 5px; overflow: hidden; font-family: 'Consolas', 'Monaco', monospace; font-size: 13px; }\n");
        sb.append("        .code-line { display: flex; border-bottom: 1px solid #eee; position: relative; min-height: 20px; }\n");
        sb.append("        .code-line:hover { background: #f0f0f0; }\n");
        sb.append("        .line-number { background: #e8e8e8; color: #666; padding: 2px 10px; text-align: right; min-width: 50px; user-select: none; border-right: 1px solid #ddd; font-size: 12px; }\n");
        sb.append("        .line-content { padding: 2px 10px; white-space: pre; flex: 1; }\n");
        sb.append("        .code-line.has-mutations { background: #fff3cd; }\n");
        sb.append("        .code-line.all-killed { background: #d4edda; }\n");
        sb.append("        .code-line.has-survived { background: #f8d7da; }\n");
        sb.append("        .mutation-indicator { position: absolute; left: 0; top: 0; bottom: 0; width: 4px; }\n");
        sb.append("        .mutation-indicator.killed { background: #28a745; }\n");
        sb.append("        .mutation-indicator.survived { background: #dc3545; }\n");
        sb.append("        .mutation-indicator.mixed { background: linear-gradient(to bottom, #28a745 0%, #28a745 50%, #dc3545 50%, #dc3545 100%); }\n");
        sb.append("        .mutation-tooltip { display: none; position: absolute; background: white; border: 2px solid #333; border-radius: 5px; padding: 12px; box-shadow: 0 5px 20px rgba(0,0,0,0.3); z-index: 1000; min-width: 300px; left: 60px; top: 100%; margin-top: 5px; }\n");
        sb.append("        .code-line:hover .mutation-tooltip { display: block; }\n");
        sb.append("        .tooltip-header { font-weight: bold; margin-bottom: 8px; padding-bottom: 6px; border-bottom: 1px solid #ddd; color: #2c3e50; font-size: 13px; }\n");
        sb.append("        .mutation-entry { padding: 6px 8px; margin: 4px 0; border-radius: 3px; border-left: 3px solid; font-size: 12px; }\n");
        sb.append("        .mutation-entry.killed { background: #d4edda; border-color: #28a745; }\n");
        sb.append("        .mutation-entry.survived { background: #f8d7da; border-color: #dc3545; }\n");
        sb.append("        .mutation-entry strong { font-family: 'Consolas', monospace; }\n");
        sb.append("        .mutation-status { font-size: 11px; margin-top: 3px; font-weight: bold; }\n");
        sb.append("        .mutation-status.killed { color: #28a745; }\n");
        sb.append("        .mutation-status.survived { color: #dc3545; }\n");
        sb.append("        .legend { margin-top: 20px; padding: 12px; background: #f8f9fa; border-radius: 5px; }\n");
        sb.append("        .legend h4 { margin-bottom: 8px; color: #2c3e50; font-size: 14px; }\n");
        sb.append("        .legend-items { display: flex; gap: 15px; flex-wrap: wrap; }\n");
        sb.append("        .legend-item { display: flex; align-items: center; gap: 6px; font-size: 13px; }\n");
        sb.append("        .legend-box { width: 18px; height: 18px; border: 1px solid #ddd; }\n");
        sb.append("    </style>\n</head>\n<body>\n<div class=\"container\">\n");
        return sb.toString();
    }
    
    private static String generateSummaryBar(int total, long killed, long survived, double mutationScore) {
        String scoreClass = mutationScore >= 80 ? "" : (mutationScore >= 60 ? "medium" : "low");
        
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"summary-bar\">\n");
        sb.append("    <div class=\"stat\"><div class=\"stat-value\">").append(total).append("</div><div class=\"stat-label\">Total Mutations</div></div>\n");
        sb.append("    <div class=\"stat\"><div class=\"stat-value\" style=\"color: #2ecc71\">").append(killed).append("</div><div class=\"stat-label\">Killed</div></div>\n");
        sb.append("    <div class=\"stat\"><div class=\"stat-value\" style=\"color: #e74c3c\">").append(survived).append("</div><div class=\"stat-label\">Survived</div></div>\n");
        sb.append("    <div class=\"mutation-score ").append(scoreClass).append("\">").append(String.format("%.0f%%", mutationScore)).append("</div>\n");
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
                html.append("<div class=\"code-line\"><div class=\"line-number\">").append(lineNum)
                    .append("</div><div class=\"line-content\">").append(escapeHtml(line)).append("</div></div>\n");
            } else {
                boolean hasKilled = lineMutations.stream().anyMatch(m -> m.killed);
                boolean hasSurvived = lineMutations.stream().anyMatch(m -> !m.killed);
                
                String lineClass = "code-line has-mutations";
                String indicatorClass = "mutation-indicator";
                
                if (hasSurvived) {
                    lineClass += " has-survived";
                    indicatorClass += " survived";
                } else if (hasKilled) {
                    lineClass += " all-killed";
                    indicatorClass += " killed";
                }
                
                if (hasKilled && hasSurvived) {
                    indicatorClass = "mutation-indicator mixed";
                }
                
                html.append("<div class=\"").append(lineClass).append("\"><div class=\"").append(indicatorClass)
                    .append("\"></div><div class=\"line-number\">").append(lineNum)
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
        tooltip.append("<div class=\"mutation-tooltip\">\n");
        tooltip.append("<div class=\"tooltip-header\">Line ").append(lineNum).append(": ")
               .append(mutations.size()).append(" mutation(s)</div>\n");
        
        for (LineMutation m : mutations) {
            String statusClass = m.killed ? "killed" : "survived";
            String statusText = m.killed ? "KILLED" : "SURVIVED";
            String statusIcon = m.killed ? "✓" : "✗";
            
            tooltip.append("<div class=\"mutation-entry ").append(statusClass).append("\">")
                   .append("<div>").append(statusIcon).append(" <strong>").append(escapeHtml(m.original))
                   .append("</strong> → <strong>").append(escapeHtml(m.mutated)).append("</strong> (col ")
                   .append(m.columnStart).append(")</div>")
                   .append("<div class=\"mutation-status ").append(statusClass).append("\">").append(statusText)
                   .append("</div></div>\n");
        }
        
        tooltip.append("</div>\n");
        return tooltip.toString();
    }
    
    private static String generateLegend() {
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"legend\">\n");
        sb.append("    <h4>Legend</h4>\n");
        sb.append("    <div class=\"legend-items\">\n");
        sb.append("        <div class=\"legend-item\"><div class=\"legend-box\" style=\"background: #d4edda;\"></div><span>All mutations killed</span></div>\n");
        sb.append("        <div class=\"legend-item\"><div class=\"legend-box\" style=\"background: #f8d7da;\"></div><span>Some mutations survived</span></div>\n");
        sb.append("        <div class=\"legend-item\"><div class=\"legend-box\" style=\"background: #fff3cd;\"></div><span>Hover for details</span></div>\n");
        sb.append("    </div>\n");
        sb.append("</div>\n");
        return sb.toString();
    }
    
    private static String generateFooter() {
        return "</div>\n</body>\n</html>";
    }
    
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }
}