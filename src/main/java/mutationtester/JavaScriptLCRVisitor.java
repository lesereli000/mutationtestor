package mutationtester;

import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Visitor for Logical Connector Replacement (LCR) mutations in JavaScript
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
        // Check for && operator
        if (ctx.And() != null && mutationType == Mutator.MutatorType.LCR_AND_TO_OR) {
            TerminalNode operator = ctx.And();
            int line = operator.getSymbol().getLine();
            int column = operator.getSymbol().getCharPositionInLine();
            
            mutations.add(new MutationPoint(
                     sourceFile,
                line,
                column,
                "&&",           // originalOp first
                "||",           // replacement second
                mutationType            // enum third  
            ));
        }
        
        return visitChildren(ctx);
    }
    
    @Override
    public Void visitLogicalOrExpression(JavaScriptParser.LogicalOrExpressionContext ctx) {
        // Check for || operator
        if (ctx.Or() != null && mutationType == Mutator.MutatorType.LCR_OR_TO_AND) {
            TerminalNode operator = ctx.Or();
            int line = operator.getSymbol().getLine();
            int column = operator.getSymbol().getCharPositionInLine();
            
            mutations.add(new MutationPoint(
                sourceFile,
                line,
                column,
                "&&",           // originalOp first
                "||",           // replacement second
                mutationType            // enum third    
            ));
        }
        
        return visitChildren(ctx);
    }
}