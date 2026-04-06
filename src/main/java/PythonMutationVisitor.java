import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

/**
 * Visitor that identifies and records mutation opportunities in Python code.
 * Finds arithmetic, comparison, and logical operators for mutation testing,
 * PLUS the new variable-type-swap mutation (int <-> float) using only
 * the tokens and rules present in the project's PythonParser.g4.
 */
public class PythonMutationVisitor extends PythonParserBaseVisitor<Void> {
    
    private List<Mutation> mutations = new ArrayList<>();
    private String sourceCode;
    // Track variable and parameter annotations: name -> type
    private Map<String, String> variableTypes = new HashMap<>();
    
    public PythonMutationVisitor(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    /**
     * Detect int/float type annotations after a COLON (:).
     * This catches BOTH variable annotations (x: int = 5)
     * and function parameter annotations (def func(x: int)).
     * Uses token traversal because the grammar does NOT generate
     * AnnassignContext or TfpdefContext.
     */
    @Override
    public Void visitTest(PythonParser.TestContext ctx) {
        ParseTree parent = ctx.getParent();
        if (parent != null) {
            for (int i = 0; i < parent.getChildCount() - 1; i++) {
                ParseTree child = parent.getChild(i);
                if (child instanceof TerminalNode) {
                    Token token = ((TerminalNode) child).getSymbol();
                    if (token.getType() == PythonLexer.COLON) {
                        String type = ctx.getText().trim();

                        if (type.equals("int") || type.equals("float")) {
                            // The name is the child immediately before the colon
                            String name = (i > 0) ? parent.getChild(i - 1).getText() : "unknown";

                            Token typeToken = ctx.getStart();
                            int line = typeToken.getLine();
                            int column = typeToken.getCharPositionInLine();
                            String srcLine = getSourceLine(line);

                            variableTypes.put(name, type);

                            if (type.equals("int")) {
                                mutations.add(new Mutation("TYPE_INT_TO_FLOAT", line, column, "int", "float", srcLine));
                            } else {
                                mutations.add(new Mutation("TYPE_FLOAT_TO_INT", line, column, "float", "int", srcLine));
                            }
                        }
                        break;
                    }
                }
            }
        }
        return visitChildren(ctx);
    }

    /**
     * Catch return annotations (-> int / -> float) inside funcdef.
     * The grammar defines funcdef with an ARROW token followed by a test.
     * Function name comes from the 'name' rule.
     */
    @Override
    public Void visitFuncdef(PythonParser.FuncdefContext ctx) {
        for (int i = 0; i < ctx.getChildCount() - 1; i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode) {
                Token token = ((TerminalNode) child).getSymbol();
                if (token.getType() == PythonLexer.ARROW) {
                    ParseTree typeNode = ctx.getChild(i + 1);
                    String returnType = typeNode.getText().trim();

                    if (returnType.equals("int") || returnType.equals("float")) {
                        String funcName = (ctx.name() != null) ? ctx.name().getText() : "unknown";

                        // Safe cast: typeNode after ARROW is always a ParserRuleContext (TestContext)
                        Token typeToken = (typeNode instanceof ParserRuleContext)
                                ? ((ParserRuleContext) typeNode).getStart()
                                : token;   // fallback (should never be hit)

                        int line = typeToken.getLine();
                        int column = typeToken.getCharPositionInLine();
                        String srcLine = getSourceLine(line);

                        if (returnType.equals("int")) {
                            mutations.add(new Mutation("TYPE_INT_TO_FLOAT", line, column, "int", "float", srcLine));
                        } else {
                            mutations.add(new Mutation("TYPE_FLOAT_TO_INT", line, column, "float", "int", srcLine));
                        }
                    }
                    break;
                }
            }
        }
        return visitChildren(ctx);
    }
    
    /**
     * Visit expressions to find arithmetic operators: +, -, *, /, //, %, **
     */
    @Override
    public Void visitExpr(PythonParser.ExprContext ctx) {
        // Check if this expression has an operator
        if (ctx.op != null) {
            Token opToken = ctx.op;
            String operator = opToken.getText();
            int line = opToken.getLine();
            int column = opToken.getCharPositionInLine();
            String srcLine = getSourceLine(line);
            
            switch (operator) {
                case "+":
                    mutations.add(new Mutation("AOR_PLUS_TO_MINUS", line, column, "+", "-", srcLine));
                    mutations.add(new Mutation("AOR_PLUS_TO_MULTIPLY", line, column, "+", "*", srcLine));
                    mutations.add(new Mutation("AOR_PLUS_TO_DIVIDE", line, column, "+", "/", srcLine));
                    break;
                    
                case "-":
                    mutations.add(new Mutation("AOR_MINUS_TO_PLUS", line, column, "-", "+", srcLine));
                    mutations.add(new Mutation("AOR_MINUS_TO_MULTIPLY", line, column, "-", "*", srcLine));
                    mutations.add(new Mutation("AOR_MINUS_TO_DIVIDE", line, column, "-", "/", srcLine));
                    break;
                    
                case "*":
                    mutations.add(new Mutation("AOR_MULTIPLY_TO_DIVIDE", line, column, "*", "/", srcLine));
                    mutations.add(new Mutation("AOR_MULTIPLY_TO_PLUS", line, column, "*", "+", srcLine));
                    mutations.add(new Mutation("AOR_MULTIPLY_TO_MINUS", line, column, "*", "-", srcLine));
                    break;
                    
                case "/":
                    mutations.add(new Mutation("AOR_DIVIDE_TO_MULTIPLY", line, column, "/", "*", srcLine));
                    mutations.add(new Mutation("AOR_DIVIDE_TO_PLUS", line, column, "/", "+", srcLine));
                    mutations.add(new Mutation("AOR_DIVIDE_TO_MINUS", line, column, "/", "-", srcLine));
                    break;
                    
                case "//":  // Integer division (Python specific)
                    mutations.add(new Mutation("AOR_INTDIV_TO_DIVIDE", line, column, "//", "/", srcLine));
                    mutations.add(new Mutation("AOR_INTDIV_TO_MULTIPLY", line, column, "//", "*", srcLine));
                    break;
                    
                case "%":
                    mutations.add(new Mutation("AOR_MODULO_TO_MULTIPLY", line, column, "%", "*", srcLine));
                    mutations.add(new Mutation("AOR_MODULO_TO_DIVIDE", line, column, "%", "/", srcLine));
                    break;
                    
                case "**":  // Power (Python specific)
                    mutations.add(new Mutation("AOR_POWER_TO_MULTIPLY", line, column, "**", "*", srcLine));
                    mutations.add(new Mutation("AOR_POWER_TO_DIVIDE", line, column, "**", "/", srcLine));
                    break;
            }
        }
        
        // Continue visiting children
        return visitChildren(ctx);
    }
    
    /**
     * Visit logical_test to find logical operators: and, or
     */
    @Override
    public Void visitLogical_test(PythonParser.Logical_testContext ctx) {
        if (ctx.op != null) {
            Token opToken = ctx.op;
            String operator = opToken.getText();
            int line = opToken.getLine();
            int column = opToken.getCharPositionInLine();
            String srcLine = getSourceLine(line);
            
            switch (operator) {
                case "and":
                    mutations.add(new Mutation("LCR_AND_TO_OR", line, column, "and", "or", srcLine));
                    break;
                    
                case "or":
                    mutations.add(new Mutation("LCR_OR_TO_AND", line, column, "or", "and", srcLine));
                    break;
            }
        }
        
        return visitChildren(ctx);
    }
    
    /**
     * Visit comparison expressions to find relational operators: <, >, ==, >=, <=, !=
     */
    @Override
    public Void visitComparison(PythonParser.ComparisonContext ctx) {
        // Comparison has multiple children: expr (comp_op expr)*
        // We need to iterate through and find comparison operators
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                Token token = ((TerminalNode) child).getSymbol();
                String operator = token.getText();
                int line = token.getLine();
                int column = token.getCharPositionInLine();
                String srcLine = getSourceLine(line);
                
                switch (operator) {
                    case "<":
                        mutations.add(new Mutation("ROR_LT_TO_LE", line, column, "<", "<=", srcLine));
                        mutations.add(new Mutation("ROR_LT_TO_GT", line, column, "<", ">", srcLine));
                        mutations.add(new Mutation("ROR_LT_TO_EQ", line, column, "<", "==", srcLine));
                        break;
                        
                    case ">":
                        mutations.add(new Mutation("ROR_GT_TO_GE", line, column, ">", ">=", srcLine));
                        mutations.add(new Mutation("ROR_GT_TO_LT", line, column, ">", "<", srcLine));
                        mutations.add(new Mutation("ROR_GT_TO_EQ", line, column, ">", "==", srcLine));
                        break;
                        
                    case "==":
                        mutations.add(new Mutation("ROR_EQ_TO_NE", line, column, "==", "!=", srcLine));
                        mutations.add(new Mutation("ROR_EQ_TO_LT", line, column, "==", "<", srcLine));
                        mutations.add(new Mutation("ROR_EQ_TO_GT", line, column, "==", ">", srcLine));
                        break;
                        
                    case "!=":
                        mutations.add(new Mutation("ROR_NE_TO_EQ", line, column, "!=", "==", srcLine));
                        break;
                        
                    case ">=":
                        mutations.add(new Mutation("ROR_GE_TO_GT", line, column, ">=", ">", srcLine));
                        mutations.add(new Mutation("ROR_GE_TO_LT", line, column, ">=", "<", srcLine));
                        mutations.add(new Mutation("ROR_GE_TO_EQ", line, column, ">=", "==", srcLine));
                        break;
                        
                    case "<=":
                        mutations.add(new Mutation("ROR_LE_TO_LT", line, column, "<=", "<", srcLine));
                        mutations.add(new Mutation("ROR_LE_TO_GT", line, column, "<=", ">", srcLine));
                        mutations.add(new Mutation("ROR_LE_TO_EQ", line, column, "<=", "==", srcLine));
                        break;
                }
            }
        }
        
        return visitChildren(ctx);
    }
    
    /**
     * Helper to get source code line by line number
     */
    private String getSourceLine(int lineNumber) {
        String[] lines = sourceCode.split("\n");
        if (lineNumber > 0 && lineNumber <= lines.length) {
            return lines[lineNumber - 1].trim();
        }
        return "";
    }
    
    /**
     * Get all detected mutations
     */
    public List<Mutation> getMutations() {
        return mutations;
    }
    
    /**
     * Clear all mutations (useful for reusing visitor)
     */
    public void clearMutations() {
        mutations.clear();
    }
}