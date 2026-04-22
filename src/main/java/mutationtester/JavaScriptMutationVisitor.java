package mutationtester;

import mutationtester.msl.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.util.*;

/**
 * Visitor that identifies and records mutation opportunities in JavaScript code
 */
public class JavaScriptMutationVisitor extends JavaScriptParserBaseVisitor<Void> {
    
    private List<Mutation> mutations = new ArrayList<>();
    private String sourceCode;
    
    public JavaScriptMutationVisitor(String sourceCode) {
        this.sourceCode = sourceCode;
    }
    
    /**
     * Visit additive expressions: + and -
     */
    @Override
    public Void visitAdditiveExpression(JavaScriptParser.AdditiveExpressionContext ctx) {
        // Check each operator in the expression
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                String operator = child.getText();
                Token token = ((TerminalNode) child).getSymbol();
                
                if (operator.equals("+")) {
                    // Create mutation: + to -
                    mutations.add(new Mutation(
                        "AOR_PLUS_TO_MINUS",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "+",
                        "-",
                        getSourceLine(token.getLine())
                    ));
                    
                    // Create mutation: + to *
                    mutations.add(new Mutation(
                        "AOR_PLUS_TO_MULTIPLY",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "+",
                        "*",
                        getSourceLine(token.getLine())
                    ));
                    
                    // Create mutation: + to /
                    mutations.add(new Mutation(
                        "AOR_PLUS_TO_DIVIDE",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "+",
                        "/",
                        getSourceLine(token.getLine())
                    ));
                    
                } else if (operator.equals("-")) {
                    // Create mutations for minus operator
                    mutations.add(new Mutation(
                        "AOR_MINUS_TO_PLUS",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "-",
                        "+",
                        getSourceLine(token.getLine())
                    ));
                    
                    mutations.add(new Mutation(
                        "AOR_MINUS_TO_MULTIPLY",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "-",
                        "*",
                        getSourceLine(token.getLine())
                    ));
                    
                    mutations.add(new Mutation(
                        "AOR_MINUS_TO_DIVIDE",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "-",
                        "/",
                        getSourceLine(token.getLine())
                    ));
                }
            }
        }
        
        // Continue visiting children
        return visitChildren(ctx);
    }
    
    /**
     * Visit multiplicative expressions: *, /, %
     */
    @Override
    public Void visitMultiplicativeExpression(JavaScriptParser.MultiplicativeExpressionContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                String operator = child.getText();
                Token token = ((TerminalNode) child).getSymbol();
                
                if (operator.equals("*")) {
                    mutations.add(new Mutation(
                        "AOR_MULTIPLY_TO_DIVIDE",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "*",
                        "/",
                        getSourceLine(token.getLine())
                    ));
                    
                    mutations.add(new Mutation(
                        "AOR_MULTIPLY_TO_PLUS",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "*",
                        "+",
                        getSourceLine(token.getLine())
                    ));
                    
                    mutations.add(new Mutation(
                        "AOR_MULTIPLY_TO_MINUS",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "*",
                        "-",
                        getSourceLine(token.getLine())
                    ));
                    
                } else if (operator.equals("/")) {
                    mutations.add(new Mutation(
                        "AOR_DIVIDE_TO_MULTIPLY",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "/",
                        "*",
                        getSourceLine(token.getLine())
                    ));
                    
                    mutations.add(new Mutation(
                        "AOR_DIVIDE_TO_PLUS",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "/",
                        "+",
                        getSourceLine(token.getLine())
                    ));
                    
                    mutations.add(new Mutation(
                        "AOR_DIVIDE_TO_MINUS",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "/",
                        "-",
                        getSourceLine(token.getLine())
                    ));
                    
                } else if (operator.equals("%")) {
                    mutations.add(new Mutation(
                        "AOR_MODULO_TO_MULTIPLY",
                        token.getLine(),
                        token.getCharPositionInLine(),
                        "%",
                        "*",
                        getSourceLine(token.getLine())
                    ));
                }
            }
        }
        
        return visitChildren(ctx);
    }
    
    /**
     * Helper to get source code line
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
}
