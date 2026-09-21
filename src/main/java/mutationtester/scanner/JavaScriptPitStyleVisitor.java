package mutationtester.scanner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/** Conservative AST-backed PIT-style mutations specific to JavaScript. */
public class JavaScriptPitStyleVisitor extends JavaScriptParserBaseVisitor<Void> {

    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;
    private final String sourceCode;

    public JavaScriptPitStyleVisitor(File sourceFile, Mutator.MutatorType mutationType,
                                     String sourceCode) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
        this.sourceCode = sourceCode;
    }

    public List<MutationPoint> getMutations() {
        return mutations;
    }

    @Override
    public Void visitIfStatement(JavaScriptParser.IfStatementContext ctx) {
        if (mutationType == Mutator.MutatorType.CONDITIONAL_TRUE
                || mutationType == Mutator.MutatorType.CONDITIONAL_FALSE) {
            addWholeNode(ctx.expressionSequence(),
                    mutationType == Mutator.MutatorType.CONDITIONAL_TRUE ? "true" : "false");
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitTernaryExpression(JavaScriptParser.TernaryExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.CONDITIONAL_TRUE
                || mutationType == Mutator.MutatorType.CONDITIONAL_FALSE) {
            addWholeNode(ctx.singleExpression(0),
                    mutationType == Mutator.MutatorType.CONDITIONAL_TRUE ? "true" : "false");
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitNotExpression(JavaScriptParser.NotExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.REMOVE_LOGICAL_NEGATION) {
            ParseTree token = ctx.getChild(0);
            addToken(token, "");
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitNewExpression(JavaScriptParser.NewExpressionContext ctx) {
        if (mutationType == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
            addWholeNode(ctx, "null");
            return null; // Do not also mutate calls nested inside this constructor.
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitReturnStatement(JavaScriptParser.ReturnStatementContext ctx) {
        if (mutationType == Mutator.MutatorType.NON_VOID_METHOD_CALLS
                && ctx.expressionSequence() != null
                && ctx.expressionSequence().singleExpression().size() == 1
                && ctx.expressionSequence().singleExpression(0)
                    instanceof JavaScriptParser.ArgumentsExpressionContext) {
            addWholeNode(ctx.expressionSequence().singleExpression(0), "null");
            return null;
        }
        return visitChildren(ctx);
    }

    @Override
    public Void visitEqualityExpression(JavaScriptParser.EqualityExpressionContext ctx) {
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (!(child instanceof TerminalNode)) continue;
            String operator = child.getText();
            if (mutationType == Mutator.MutatorType.JS_STRICT_EQ_TO_LOOSE_EQ
                    && operator.equals("===")) {
                addToken(child, "==");
            } else if (mutationType == Mutator.MutatorType.JS_STRICT_NE_TO_LOOSE_NE
                    && operator.equals("!==")) {
                addToken(child, "!=");
            }
        }
        return visitChildren(ctx);
    }

    private void addWholeNode(ParserRuleContext ctx, String replacement) {
        if (ctx == null || ctx.getStart() == null || ctx.getStop() == null) return;
        Token start = ctx.getStart();
        Token stop = ctx.getStop();
        if (start.getLine() != stop.getLine()) return; // Main currently applies line-local edits.
        String original = sourceCode.substring(start.getStartIndex(), stop.getStopIndex() + 1);
        mutations.add(new MutationPoint(sourceFile,
                start.getLine(), start.getCharPositionInLine(), original, replacement, mutationType));
    }

    private void addToken(ParseTree node, String replacement) {
        if (!(node instanceof TerminalNode terminal)) return;
        Token token = terminal.getSymbol();
        mutations.add(new MutationPoint(sourceFile,
                token.getLine(), token.getCharPositionInLine(), token.getText(), replacement, mutationType));
    }
}
