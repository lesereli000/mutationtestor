package mutationtester.scanner;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PIT-style Logical Connector Replacement mutations for JavaScript
 */
public class JavaScriptLCRVisitor extends JavaScriptParserBaseVisitor<Void> {
    
    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;
    
    public JavaScriptLCRVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }
    
    public List<MutationPoint> getMutations() {
        return mutations;
    }
    
    @Override
    public Void visitLogicalAndExpression(JavaScriptParser.LogicalAndExpressionContext ctx) {
        // Find && operator
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                TerminalNode termNode = (TerminalNode) child;
                String operator = termNode.getText();
                
                if (operator.equals("&&") && mutationType == Mutator.MutatorType.LCR_AND_TO_OR) {
                    int line = termNode.getSymbol().getLine();
                    int column = termNode.getSymbol().getCharPositionInLine();
                    
                    mutations.add(new MutationPoint(
                        sourceFile,
                        line,
                        column,
                        "&&",
                        "||",
                        mutationType
                    ));
                }
            }
        }
        
        return visitChildren(ctx);
    }
    
    @Override
    public Void visitLogicalORExpression(JavaScriptParser.LogicalORExpressionContext ctx) {
        // Find || operator
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            
            if (child instanceof TerminalNode) {
                TerminalNode termNode = (TerminalNode) child;
                String operator = termNode.getText();
                
                if (operator.equals("||") && mutationType == Mutator.MutatorType.LCR_OR_TO_AND) {
                    int line = termNode.getSymbol().getLine();
                    int column = termNode.getSymbol().getCharPositionInLine();
                    
                    mutations.add(new MutationPoint(
                        sourceFile,
                        line,
                        column,
                        "||",
                        "&&",
                        mutationType
                    ));
                }
            }
        }
        
        return visitChildren(ctx);
    }
}