package mutationtester.scanner;

import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * S04: replace a valued method/function call with {@code null}.
 * Standalone {@code foo();} and {@code new Foo()} are not mutated.
 */
public class JavaScriptNonVoidCallVisitor extends JavaScriptParserBaseVisitor<Void> {

    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;

    public JavaScriptNonVoidCallVisitor(File sourceFile, Mutator.MutatorType mutationType) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
    }

    public List<MutationPoint> getMutations() {
        return mutations;
    }

    @Override
    public Void visitReturnStatement(JavaScriptParser.ReturnStatementContext ctx) {
        if (ctx.expressionSequence() != null
                && ctx.expressionSequence().singleExpression().size() == 1) {
            maybeMutate(ctx.expressionSequence().singleExpression(0));
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitVariableDeclaration(JavaScriptParser.VariableDeclarationContext ctx) {
        maybeMutate(ctx.singleExpression());
        return visitChildren(ctx);
    }

    @Override
    public Void visitAssignmentExpression(JavaScriptParser.AssignmentExpressionContext ctx) {
        if (ctx.singleExpression().size() >= 2) {
            maybeMutate(ctx.singleExpression(1));
        }
        return visitChildren(ctx);
    }

    private void maybeMutate(JavaScriptParser.SingleExpressionContext expression) {
        if (!(expression instanceof JavaScriptParser.ArgumentsExpressionContext call)) {
            return;
        }
        mutations.add(new MutationPoint(
                sourceFile,
                call.getStart().getLine(),
                call.getStart().getCharPositionInLine(),
                call.getText(),
                "null",
                mutationType));
    }
}
