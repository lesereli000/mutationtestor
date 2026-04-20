import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.*;
import java.util.*;

/**
 * Generates interactive AST visualizations for mutation testing reports
 */
public class ASTVisualizationGenerator {
    
    public static void generateJavaScriptAST(
            File sourceFile,
            List<MutationPoint> mutations,
            String outputPath) throws IOException {
        
        // Parse the file to get AST
        CharStream input = CharStreams.fromPath(sourceFile.toPath());
        JavaScriptLexer lexer = new JavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        
        ParseTree tree = parser.program();
        
        // Convert to JSON
        String astJson = convertTreeToJSON(tree, mutations, sourceFile);
        
        // Generate HTML
        String html = generateASTHTML(sourceFile.getName(), astJson, mutations);
        
        // Write to file
        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(html);
        }
    }
    
    /**
     * Convert parse tree to JSON format for D3.js visualization
     */
    private static String convertTreeToJSON(
            ParseTree tree, 
            List<MutationPoint> mutations,
            File sourceFile) {
        
        StringBuilder json = new StringBuilder();
        buildJSONNode(tree, 0, json, mutations, sourceFile, 0);
        return json.toString();
    }
    
    private static int buildJSONNode(
            ParseTree node,
            int depth,
            StringBuilder json,
            List<MutationPoint> mutations,
            File sourceFile,
            int nodeId) {
        
        json.append("{\n");
        json.append("  \"id\": ").append(nodeId).append(",\n");
        json.append("  \"name\": \"").append(escapeJSON(getNodeName(node))).append("\",\n");
        json.append("  \"type\": \"").append(getNodeType(node)).append("\",\n");
        
        // Check if this node is a mutation point
        MutationInfo mutationInfo = findMutationAtNode(node, mutations, sourceFile);
        if (mutationInfo != null) {
            json.append("  \"isMutation\": true,\n");
            json.append("  \"mutationStatus\": \"").append(mutationInfo.status).append("\",\n");
            json.append("  \"mutationOperator\": \"").append(escapeJSON(mutationInfo.operator)).append("\",\n");
            json.append("  \"mutationReplacement\": \"").append(escapeJSON(mutationInfo.replacement)).append("\",\n");
        } else {
            json.append("  \"isMutation\": false,\n");
        }
        
        // Add position info
        if (node instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) node;
            json.append("  \"line\": ").append(ctx.getStart().getLine()).append(",\n");
            json.append("  \"column\": ").append(ctx.getStart().getCharPositionInLine()).append(",\n");
        }
        
        // Process children
        int childCount = node.getChildCount();
        if (childCount > 0) {
            json.append("  \"children\": [\n");
            int currentNodeId = nodeId + 1;
            for (int i = 0; i < childCount; i++) {
                currentNodeId = buildJSONNode(node.getChild(i), depth + 1, json, mutations, sourceFile, currentNodeId);
                if (i < childCount - 1) {
                    json.append(",\n");
                }
            }
            json.append("  ]\n");
        } else {
            json.append("  \"children\": []\n");
        }
        
        json.append("}");
        
        return nodeId + 1;
    }
    
    private static class MutationInfo {
        String status;  // "killed", "survived", "equivalent"
        String operator;
        String replacement;
        
        MutationInfo(String status, String operator, String replacement) {
            this.status = status;
            this.operator = operator;
            this.replacement = replacement;
        }
    }
    
    private static MutationInfo findMutationAtNode(
            ParseTree node,
            List<MutationPoint> mutations,
            File sourceFile) {
        
        if (!(node instanceof ParserRuleContext)) {
            return null;
        }
        
        ParserRuleContext ctx = (ParserRuleContext) node;
        int line = ctx.getStart().getLine();
        int column = ctx.getStart().getCharPositionInLine();
        
        for (MutationPoint mutation : mutations) {
            if (mutation.getSourceFile().equals(sourceFile) &&
                mutation.getLineNumber() == line &&
                mutation.getColumnNumber() == column) {
                
                // Determine status (you'll need to pass this info in)
                String status = "survived";  // Default - you should get this from MutantResult
                
                return new MutationInfo(
                    status,
                    mutation.getOperator(),
                    mutation.getReplacement()
                );
            }
        }
        
        return null;
    }
    
    private static String getNodeName(ParseTree node) {
        if (node instanceof TerminalNode) {
            return node.getText();
        } else if (node instanceof ParserRuleContext) {
            ParserRuleContext ctx = (ParserRuleContext) node;
            return ctx.getClass().getSimpleName().replace("Context", "");
        }
        return "Unknown";
    }
    
    private static String getNodeType(ParseTree node) {
        if (node instanceof TerminalNode) {
            return "terminal";
        } else {
            return "rule";
        }
    }
    
    private static String escapeJSON(String str) {
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
    
    /**
     * Generate the HTML page with D3.js visualization
     */
    private static String generateASTHTML(String filename, String astJson, List<MutationPoint> mutations) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <title>AST Visualization - ").append(filename).append("</title>\n");
        html.append("    <script src=\"https://d3js.org/d3.v7.min.js\"></script>\n");
        html.append("    <style>\n");
        html.append(getASTStyles());
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"header\">\n");
        html.append("        <h1>Abstract Syntax Tree</h1>\n");
        html.append("        <div class=\"subtitle\">").append(filename).append("</div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"legend\">\n");
        html.append("        <div class=\"legend-item\"><span class=\"color-box killed\"></span> Killed Mutation</div>\n");
        html.append("        <div class=\"legend-item\"><span class=\"color-box survived\"></span> Survived Mutation</div>\n");
        html.append("        <div class=\"legend-item\"><span class=\"color-box equivalent\"></span> Equivalent Mutation</div>\n");
        html.append("        <div class=\"legend-item\"><span class=\"color-box normal\"></span> Normal Node</div>\n");
        html.append("    </div>\n");
        html.append("    <div class=\"controls\">\n");
        html.append("        <button onclick=\"expandAll()\">Expand All</button>\n");
        html.append("        <button onclick=\"collapseAll()\">Collapse All</button>\n");
        html.append("        <button onclick=\"resetZoom()\">Reset Zoom</button>\n");
        html.append("    </div>\n");
        html.append("    <div id=\"ast-container\"></div>\n");
        html.append("    <script>\n");
        html.append("        const astData = ").append(astJson).append(";\n");
        html.append(getASTJavaScript());
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");
        
        return html.toString();
    }
    
    private static String getASTStyles() {
        return """
            * { margin: 0; padding: 0; box-sizing: border-box; }
            body { 
                font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; 
                background: #f5f5f5; 
            }
            .header {
                background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                color: white;
                padding: 30px;
                text-align: center;
            }
            .header h1 { font-size: 32px; margin-bottom: 10px; }
            .header .subtitle { opacity: 0.9; font-size: 16px; }
            .legend {
                display: flex;
                justify-content: center;
                gap: 20px;
                padding: 20px;
                background: white;
                border-bottom: 1px solid #e5e7eb;
            }
            .legend-item {
                display: flex;
                align-items: center;
                gap: 8px;
                font-size: 14px;
            }
            .color-box {
                width: 20px;
                height: 20px;
                border-radius: 4px;
                border: 2px solid #ccc;
            }
            .color-box.killed { background: #4ade80; }
            .color-box.survived { background: #f87171; }
            .color-box.equivalent { background: #fbbf24; }
            .color-box.normal { background: #e5e7eb; }
            .controls {
                padding: 15px;
                text-align: center;
                background: white;
                border-bottom: 1px solid #e5e7eb;
            }
            .controls button {
                padding: 8px 16px;
                margin: 0 5px;
                border: 2px solid #667eea;
                background: white;
                color: #667eea;
                border-radius: 5px;
                cursor: pointer;
                font-size: 14px;
                font-weight: 500;
            }
            .controls button:hover {
                background: #667eea;
                color: white;
            }
            #ast-container {
                width: 100%;
                height: calc(100vh - 250px);
                background: white;
                overflow: hidden;
            }
            .node circle {
                cursor: pointer;
                stroke-width: 2px;
            }
            .node.mutation-killed circle {
                fill: #4ade80;
                stroke: #22c55e;
            }
            .node.mutation-survived circle {
                fill: #f87171;
                stroke: #ef4444;
            }
            .node.mutation-equivalent circle {
                fill: #fbbf24;
                stroke: #f59e0b;
            }
            .node.normal circle {
                fill: #e5e7eb;
                stroke: #9ca3af;
            }
            .node text {
                font-size: 12px;
                font-family: 'Consolas', monospace;
            }
            .link {
                fill: none;
                stroke: #9ca3af;
                stroke-width: 2px;
            }
            .tooltip {
                position: absolute;
                padding: 10px;
                background: rgba(0, 0, 0, 0.9);
                color: white;
                border-radius: 5px;
                pointer-events: none;
                font-size: 12px;
                max-width: 300px;
            }
            """;
    }
    
    private static String getASTJavaScript() {
        return """
            // Set up dimensions
            const width = document.getElementById('ast-container').clientWidth;
            const height = document.getElementById('ast-container').clientHeight;
            
            // Create SVG
            const svg = d3.select('#ast-container')
                .append('svg')
                .attr('width', width)
                .attr('height', height);
            
            const g = svg.append('g')
                .attr('transform', `translate(40, 20)`);
            
            // Create tree layout
            const treeLayout = d3.tree()
                .size([height - 40, width - 200]);
            
            // Create hierarchy
            const root = d3.hierarchy(astData);
            
            // Initially collapse deep nodes
            root.descendants().forEach((d, i) => {
                if (d.depth > 2) {
                    d._children = d.children;
                    d.children = null;
                }
            });
            
            // Compute tree layout
            treeLayout(root);
            
            // Create tooltip
            const tooltip = d3.select('body')
                .append('div')
                .attr('class', 'tooltip')
                .style('opacity', 0);
            
            // Draw tree
            update(root);
            
            function update(source) {
                const nodes = root.descendants();
                const links = root.links();
                
                // Update node positions
                treeLayout(root);
                
                // Update links
                const link = g.selectAll('.link')
                    .data(links, d => d.target.data.id);
                
                link.enter()
                    .append('path')
                    .attr('class', 'link')
                    .attr('d', d3.linkHorizontal()
                        .x(d => d.y)
                        .y(d => d.x));
                
                link.exit().remove();
                
                // Update nodes
                const node = g.selectAll('.node')
                    .data(nodes, d => d.data.id);
                
                const nodeEnter = node.enter()
                    .append('g')
                    .attr('class', d => {
                        if (d.data.isMutation) {
                            return 'node mutation-' + d.data.mutationStatus;
                        }
                        return 'node normal';
                    })
                    .attr('transform', d => `translate(${d.y},${d.x})`)
                    .on('click', (event, d) => toggle(d))
                    .on('mouseover', (event, d) => showTooltip(event, d))
                    .on('mouseout', () => hideTooltip());
                
                nodeEnter.append('circle')
                    .attr('r', d => d.data.isMutation ? 8 : 5);
                
                nodeEnter.append('text')
                    .attr('dy', -10)
                    .attr('text-anchor', 'middle')
                    .text(d => d.data.name.length > 20 ? d.data.name.substring(0, 20) + '...' : d.data.name);
                
                node.exit().remove();
            }
            
            function toggle(d) {
                if (d.children) {
                    d._children = d.children;
                    d.children = null;
                } else {
                    d.children = d._children;
                    d._children = null;
                }
                update(d);
            }
            
            function showTooltip(event, d) {
                let content = `<strong>${d.data.name}</strong><br>`;
                content += `Type: ${d.data.type}<br>`;
                if (d.data.line) {
                    content += `Line: ${d.data.line}, Column: ${d.data.column}<br>`;
                }
                if (d.data.isMutation) {
                    content += `<br><strong>Mutation:</strong><br>`;
                    content += `${d.data.mutationOperator} → ${d.data.mutationReplacement}<br>`;
                    content += `Status: ${d.data.mutationStatus.toUpperCase()}`;
                }
                
                tooltip.html(content)
                    .style('left', (event.pageX + 10) + 'px')
                    .style('top', (event.pageY - 10) + 'px')
                    .style('opacity', 1);
            }
            
            function hideTooltip() {
                tooltip.style('opacity', 0);
            }
            
            function expandAll() {
                root.descendants().forEach(d => {
                    if (d._children) {
                        d.children = d._children;
                        d._children = null;
                    }
                });
                update(root);
            }
            
            function collapseAll() {
                root.descendants().forEach(d => {
                    if (d.children && d.depth > 1) {
                        d._children = d.children;
                        d.children = null;
                    }
                });
                update(root);
            }
            
            function resetZoom() {
                g.attr('transform', 'translate(40, 20)');
            }
            
            // Enable zoom
            const zoom = d3.zoom()
                .scaleExtent([0.1, 3])
                .on('zoom', (event) => {
                    g.attr('transform', event.transform);
                });
            
            svg.call(zoom);
            """;
    }
}