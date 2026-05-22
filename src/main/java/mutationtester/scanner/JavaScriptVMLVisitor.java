package mutationtester.scanner;

import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PIT-style Void Method Call (VML) mutations for JavaScript.
 *
 * Removes standalone method/function calls that appear as expression
 * statements — i.e., calls whose return value is discarded.
 *
 * Example:
 *   console.log("hello");  →  ;   (call removed, empty statement remains)
 *   notify(user);          →  ;
 */
public class JavaScriptVMLVisitor extends JavaScriptParserBaseVisitor<Void> {

    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;

    public JavaScriptVMLVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }

    public List<MutationPoint> getMutations() {
        return mutations;
    }

    /**
     * Visit expression statements — these are lines where a call result
     * is discarded, e.g.  foo();  or  obj.method(x);
     *
     * We only mutate when the expression is a direct call (not an
     * assignment like  x = foo();  which would be a different context).
     */
   @Override
    public Void visitExpressionStatement(JavaScriptParser.ExpressionStatementContext ctx) {
        if (mutationType != Mutator.MutatorType.VML_VOID_METHOD_CALL) {
            return visitChildren(ctx);
        }

        if (ctx.expressionSequence() == null) {
            return visitChildren(ctx);
        }

        for (JavaScriptParser.SingleExpressionContext expr
                : ctx.expressionSequence().singleExpression()) {

            String text = expr.getText();

            // A standalone call ends with ) and is not an assignment
            // e.g. foo(), obj.method(x), console.log("hi")
            if (text.endsWith(")") && text.contains("(")
                    && !text.contains("=")) {

                int line = expr.getStart().getLine();
                int col  = expr.getStart().getCharPositionInLine();

                mutations.add(new MutationPoint(
                        sourceFile, line, col,
                        text, "", mutationType));
            }
        }

        return visitChildren(ctx);
    }
}