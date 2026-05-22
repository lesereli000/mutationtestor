package mutationtester.scanner;

import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PIT-style Increment mutations for JavaScript.
 *
 * Covers all four increment/decrement forms:
 *   i++  ->  i--   (AOR_INCREMENT_TO_DECREMENT)
 *   i--  ->  i++   (AOR_DECREMENT_TO_INCREMENT)
 *   ++i  ->  --i   (AOR_INCREMENT_TO_DECREMENT)
 *   --i  ->  ++i   (AOR_DECREMENT_TO_INCREMENT)
 */
public class JavaScriptIncrementVisitor extends JavaScriptParserBaseVisitor<Void> {

    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;

    public JavaScriptIncrementVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }

    public List<MutationPoint> getMutations() {
        return mutations;
    }

    @Override
    public Void visitPostIncrementExpression(JavaScriptParser.PostIncrementExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT) {
            TerminalNode op = ctx.PlusPlus();
            if (op != null) {
                mutations.add(new MutationPoint(sourceFile,
                        op.getSymbol().getLine(),
                        op.getSymbol().getCharPositionInLine(),
                        "++", "--", mutationType));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitPostDecreaseExpression(JavaScriptParser.PostDecreaseExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT) {
            TerminalNode op = ctx.MinusMinus();
            if (op != null) {
                mutations.add(new MutationPoint(sourceFile,
                        op.getSymbol().getLine(),
                        op.getSymbol().getCharPositionInLine(),
                        "--", "++", mutationType));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitPreIncrementExpression(JavaScriptParser.PreIncrementExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT) {
            TerminalNode op = ctx.PlusPlus();
            if (op != null) {
                mutations.add(new MutationPoint(sourceFile,
                        op.getSymbol().getLine(),
                        op.getSymbol().getCharPositionInLine(),
                        "++", "--", mutationType));
            }
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitPreDecreaseExpression(JavaScriptParser.PreDecreaseExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT) {
            TerminalNode op = ctx.MinusMinus();
            if (op != null) {
                mutations.add(new MutationPoint(sourceFile,
                        op.getSymbol().getLine(),
                        op.getSymbol().getCharPositionInLine(),
                        "--", "++", mutationType));
            }
        }
        return visitChildren(ctx);
    }
}