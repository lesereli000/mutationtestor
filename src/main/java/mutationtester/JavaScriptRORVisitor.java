package mutationtester;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        // Loop through all children to find operators
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            // Check if this is a terminal node (operator)
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
                        if (mutationType == Mutator.MutatorType.ROR_GT_TO_LT) {
                            replacement = "<";
                        }
                        break;
                        
                    case "<=":
                        if (mutationType == Mutator.MutatorType.ROR_LT_TO_GT) {
                            replacement = ">";
                        }
                        break;
                }
                
                // Create mutation if we found a replacement
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
        // Loop through all children to find operators
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                TerminalNode termNode = (TerminalNode) child;
                String operator = termNode.getText();
                
                String replacement = null;
                
                switch (operator) {
                    case "==":
                    case "===":  // Also handle strict equality
                        if (mutationType == Mutator.MutatorType.ROR_EQ_TO_NE) {
                            replacement = "!=";
                        }
                        break;
                        
                    case "!=":
                    case "!==":  // Also handle strict inequality
                        if (mutationType == Mutator.MutatorType.ROR_NE_TO_EQ) {
                            replacement = "==";
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
