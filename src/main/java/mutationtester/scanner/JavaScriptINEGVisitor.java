package mutationtester.scanner;

import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PIT-style Invert Negatives (INEG) mutations for JavaScript.
 *
 * Removes the unary minus operator, effectively inverting a negative:
 *   -i        ->  i
 *   -x.value  ->  x.value
 *   -(a + b)  ->  (a + b)
 */
public class JavaScriptINEGVisitor extends JavaScriptParserBaseVisitor<Void> {

    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;

    public JavaScriptINEGVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }

    public List<MutationPoint> getMutations() {
        return mutations;
    }

    /**
     * Matches unary minus expressions like -i, -x, -(a+b).
     * The first child is always the '-' terminal.
     * The mutation removes the minus sign: original="-", replacement="".
     */
    @Override
    public Void visitUnaryMinusExpression(JavaScriptParser.UnaryMinusExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.INEG_INVERT_NEGATIVE) {
            if (ctx.getChildCount() >= 1) {
                ParseTree firstChild = ctx.getChild(0);
                if (firstChild instanceof TerminalNode tn && tn.getText().equals("-")) {
                    mutations.add(new MutationPoint(
                            sourceFile,
                            tn.getSymbol().getLine(),
                            tn.getSymbol().getCharPositionInLine(),
                            "-",
                            "",
                            mutationType));
                }
            }
        }
        return visitChildren(ctx);
    }
}