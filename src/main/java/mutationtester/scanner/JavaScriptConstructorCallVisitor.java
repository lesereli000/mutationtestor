package mutationtester.scanner;

import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * S03: replace {@code new Foo(...)} used as a value with {@code null}.
 * Ordinary calls are not mutated.
 */
public class JavaScriptConstructorCallVisitor extends JavaScriptParserBaseVisitor<Void> {

    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;
    private final String sourceCode;

    public JavaScriptConstructorCallVisitor(File sourceFile, Mutator.MutatorType mutationType, String sourceCode) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
        this.sourceCode = sourceCode;
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
        if (!(expression instanceof JavaScriptParser.NewExpressionContext creation)) {
            return;
        }
        mutations.add(new MutationPoint(
                sourceFile,
                creation.getStart().getLine(),
                creation.getStart().getCharPositionInLine(),
                sourceSpan(creation),
                "null",
                mutationType));
    }

    private String sourceSpan(ParserRuleContext ctx) {
        Token start = ctx.getStart();
        Token stop = ctx.getStop();
        if (start != null && stop != null
                && start.getStartIndex() >= 0
                && stop.getStopIndex() >= start.getStartIndex()
                && stop.getStopIndex() < sourceCode.length()) {
            return sourceCode.substring(start.getStartIndex(), stop.getStopIndex() + 1);
        }
        return ctx.getText();
    }
}
