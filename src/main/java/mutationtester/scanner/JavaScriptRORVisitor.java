package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PIT-style Relational Operator Replacement mutations for JavaScript
 */
public class JavaScriptRORVisitor extends JavaScriptParserBaseVisitor<Void> {
    
    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;
    
    public JavaScriptRORVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }
    
    public List<MutationPoint> getMutations() {
        return mutations;
    }
    
    @Override
    public Void visitRelationalExpression(JavaScriptParser.RelationalExpressionContext ctx) {
        // Find operator token in children
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                TerminalNode termNode = (TerminalNode) child;
                String operator = termNode.getText();
                String replacement = null;
                
                // Determine replacement based on operator and mutation type
                switch (operator) {
                    case ">":
                        if (mutationType == Mutator.MutatorType.ROR_GT_TO_LT) {
                            replacement = "<";
                        } else if (mutationType == Mutator.MutatorType.ROR_GT_TO_GTE) {
                            replacement = ">=";
                        }
                        break;
                        
                    case "<":
                        if (mutationType == Mutator.MutatorType.ROR_LT_TO_GT) {
                            replacement = ">";
                        } else if (mutationType == Mutator.MutatorType.ROR_LT_TO_LTE) {
                            replacement = "<=";
                        }
                        break;
                        
                    case ">=":
                        if (mutationType == Mutator.MutatorType.ROR_GTE_TO_GT) {
                            replacement = ">";
                        } else if (mutationType == Mutator.MutatorType.ROR_GTE_TO_LT) {
                            replacement = "<";
                        }
                        break;
                        
                    case "<=":
                        if (mutationType == Mutator.MutatorType.ROR_LTE_TO_LT) {
                            replacement = "<";
                        } else if (mutationType == Mutator.MutatorType.ROR_LTE_TO_GT) {
                            replacement = ">";
                        }
                        break;
                }
                
                if (replacement != null) {
                    int line = termNode.getSymbol().getLine();
                    int column = termNode.getSymbol().getCharPositionInLine();
                    
                    mutations.add(new MutationPoint(
                        sourceFile,
                        line,
                        column,
                        operator,
                        replacement,
                        mutationType
                    ));
                }
            }
        }
        
        return visitChildren(ctx);
    }
    
    @Override
    public Void visitEqualityExpression(JavaScriptParser.EqualityExpressionContext ctx) {
        // Find operator token in children
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                TerminalNode termNode = (TerminalNode) child;
                String operator = termNode.getText();
                String replacement = null;
                
                switch (operator) {
                    case "==":
                    case "===":  // Handle strict equality
                        if (mutationType == Mutator.MutatorType.ROR_EQ_TO_NE) {
                            replacement = operator.length() == 2 ? "!=" : "!==";
                        }
                        break;
                        
                    case "!=":
                    case "!==":  // Handle strict inequality
                        if (mutationType == Mutator.MutatorType.ROR_NE_TO_EQ) {
                            replacement = operator.length() == 2 ? "==" : "===";
                        }
                        break;
                }
                
                if (replacement != null) {
                    int line = termNode.getSymbol().getLine();
                    int column = termNode.getSymbol().getCharPositionInLine();
                    
                    mutations.add(new MutationPoint(
                        sourceFile,
                        line,
                        column,
                        operator,
                        replacement,
                        mutationType
                    ));
                }
            }
        }
        
        return visitChildren(ctx);
    }
}