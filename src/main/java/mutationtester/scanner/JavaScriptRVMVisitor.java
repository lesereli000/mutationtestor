package mutationtester.scanner;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PIT-style Return Value Mutations for JavaScript
 */
public class JavaScriptRVMVisitor extends JavaScriptParserBaseVisitor<Void> {
    
    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;
    
    public JavaScriptRVMVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }
    
    public List<MutationPoint> getMutations() {
        return mutations;
    }
    
    @Override
    public Void visitReturnStatement(JavaScriptParser.ReturnStatementContext ctx) {
        // Check what's being returned
        if (ctx.expressionSequence() != null) {
            String returnedValue = ctx.expressionSequence().getText();
            
            String replacement = null;
            String original = returnedValue;
            
            // Boolean mutations
            if (returnedValue.equals("true") && 
                mutationType == Mutator.MutatorType.RVM_TRUE_TO_FALSE) {
                replacement = "false";
            } else if (returnedValue.equals("false") && 
                       mutationType == Mutator.MutatorType.RVM_FALSE_TO_TRUE) {
                replacement = "true";
            }
            // Numeric mutations
            else if (returnedValue.equals("0") && 
                     mutationType == Mutator.MutatorType.RVM_ZERO_TO_ONE) {
                replacement = "1";
            } else if (returnedValue.matches("^[1-9]\\d*$") && 
                       mutationType == Mutator.MutatorType.RVM_NONZERO_TO_ZERO) {
                replacement = "0";
            }
            
            if (replacement != null) {
                int line = ctx.expressionSequence().getStart().getLine();
                int column = ctx.expressionSequence().getStart().getCharPositionInLine();
                
                mutations.add(new MutationPoint(
                    sourceFile,
                    line,
                    column,
                    original,
                    replacement,    
                    mutationType
                ));
            }
        }
        
        return visitChildren(ctx);
    }
}