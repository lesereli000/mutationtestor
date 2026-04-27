package mutationtester;

import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Visitor for Arithmetic Operator Replacement (AOR) mutations in JavaScript
 */
public class JavaScriptAORVisitor extends JavaScriptParserBaseVisitor<Void> {
    
    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;
    
    public JavaScriptAORVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }
    
    public List<MutationPoint> getMutations() {
        return mutations;
    }
    
    @Override
    public Void visitAdditiveExpression(JavaScriptParser.AdditiveExpressionContext ctx) {
        TerminalNode operator = null;
        String originalOp = null;
        String replacement = null;
        
        // Check for + operator
        if (ctx.Plus() != null) {
            operator = ctx.Plus();
            originalOp = "+";
            
            if (mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                replacement = "-";
            }
        }
        // Check for - operator
        else if (ctx.Minus() != null) {
            operator = ctx.Minus();
            originalOp = "-";
            
            if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                replacement = "+";
            }
        }
        
        if (operator != null && replacement != null) {
            int line = operator.getSymbol().getLine();
            int column = operator.getSymbol().getCharPositionInLine();
            
            mutations.add(new MutationPoint(
                sourceFile,
                line,
                column,
                originalOp,
                replacement,
                mutationType
            ));
        }
        
        return visitChildren(ctx);
    }
    
    @Override
    public Void visitMultiplicativeExpression(JavaScriptParser.MultiplicativeExpressionContext ctx) {
        TerminalNode operator = null;
        String originalOp = null;
        String replacement = null;
        
        // Check for * operator
        if (ctx.Multiply() != null) {
            operator = ctx.Multiply();
            originalOp = "*";
            
            if (mutationType == Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE) {
                replacement = "/";
            }
        }
        // Check for / operator
        else if (ctx.Divide() != null) {
            operator = ctx.Divide();
            originalOp = "/";
            
            if (mutationType == Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY) {
                replacement = "*";
            }
        }
        // Check for % operator
        else if (ctx.Modulus() != null) {
            operator = ctx.Modulus();
            originalOp = "%";
            
            if (mutationType == Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY) {
                replacement = "*";
            }
        }
        
        if (operator != null && replacement != null) {
            int line = operator.getSymbol().getLine();
            int column = operator.getSymbol().getCharPositionInLine();
            
            mutations.add(new MutationPoint(
                sourceFile,
                line,
                column,
                originalOp,
                replacement,
                mutationType
            ));
        }
        
        return visitChildren(ctx);
    }
}