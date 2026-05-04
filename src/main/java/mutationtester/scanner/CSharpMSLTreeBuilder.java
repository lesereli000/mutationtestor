package mutationtester.scanner;

import mutationtester.CSharpLexer;
import mutationtester.CSharpParser;
import mutationtester.CSharpParserBaseVisitor;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class CSharpMSLTreeBuilder extends CSharpParserBaseVisitor<Void> {
    private final List<MSLNode> fragments = new ArrayList<>();

    public List<MSLNode> getFragments() {
        return fragments;
    }

    @Override
    public Void visitAdditive_expression(CSharpParser.Additive_expressionContext ctx) {
        if (ctx.getChildCount() == 3 && !containsStringLiteral(ctx)) {
            extractBinary(ctx);
        }
        return super.visitAdditive_expression(ctx);
    }

    @Override
    public Void visitMultiplicative_expression(CSharpParser.Multiplicative_expressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitMultiplicative_expression(ctx);
    }

    @Override
    public Void visitRelational_expression(CSharpParser.Relational_expressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitRelational_expression(ctx);
    }

    @Override
    public Void visitEquality_expression(CSharpParser.Equality_expressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitEquality_expression(ctx);
    }

    @Override
    public Void visitConditional_and_expression(CSharpParser.Conditional_and_expressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitConditional_and_expression(ctx);
    }

    @Override
    public Void visitConditional_or_expression(CSharpParser.Conditional_or_expressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitConditional_or_expression(ctx);
    }

    private void extractBinary(org.antlr.v4.runtime.ParserRuleContext ctx) {
        ParseTree leftChild = ctx.getChild(0);
        ParseTree rightChild = ctx.getChild(2);
        ParseTree opChild = ctx.getChild(1);

        MSLNode leftNode = buildQuantityNode(leftChild);
        MSLNode rightNode = buildQuantityNode(rightChild);
        
        if (leftNode != null && rightNode != null && opChild instanceof TerminalNode tn) {
            Token opToken = tn.getSymbol();
            fragments.add(new MSLNode(
                "BinaryExpr", 
                opToken.getText(), 
                opToken.getLine(), 
                opToken.getCharPositionInLine(), 
                List.of(leftNode, rightNode)
            ));
        }
    }

    private MSLNode buildQuantityNode(ParseTree ctx) {
        if (ctx == null) return null;
        String text = ctx.getText();
        Token startToken = null;
        if (ctx instanceof org.antlr.v4.runtime.ParserRuleContext prc) {
            startToken = prc.getStart();
        } else if (ctx instanceof TerminalNode tn) {
            startToken = tn.getSymbol();
        }
        if (startToken != null) {
             return new MSLNode("Quantity", text, startToken.getLine(), startToken.getCharPositionInLine(), List.of());
        }
        return null;
    }

    private boolean containsStringLiteral(ParseTree tree) {
        if (tree instanceof TerminalNode terminalNode) {
            int tokenType = terminalNode.getSymbol().getType();
            return tokenType == CSharpLexer.String_Literal || tokenType == CSharpLexer.Interpolated_Regular_String_Start || tokenType == CSharpLexer.Interpolated_Verbatim_String_Start;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            if (containsStringLiteral(tree.getChild(i))) return true;
        }
        return false;
    }

    @Override
    public Void visitExpression_statement(CSharpParser.Expression_statementContext ctx) {
        if (ctx.statement_expression() != null && ctx.statement_expression().getText().contains("(")) {
            Token startToken = ctx.getStart();
            int line = startToken != null ? startToken.getLine() : 0;
            int col = startToken != null ? startToken.getCharPositionInLine() : 0;
            fragments.add(new MSLNode(
                "InvocationStatement",
                ctx.getText(),
                line,
                col,
                List.of()
            ));
        }
        return super.visitExpression_statement(ctx);
    }

    @Override
    public Void visitInvocation_expression(CSharpParser.Invocation_expressionContext ctx) {
        String text = ctx.getText();
        if (text.startsWith("string.IsNullOrEmpty(")) {
            String argText = "";
            if (ctx.argument_list() != null) {
                argText = ctx.argument_list().getText();
            }
            MSLNode argNode = new MSLNode("Quantity", argText, 0, 0, List.of());
            Token startToken = ctx.getStart();
            int line = startToken != null ? startToken.getLine() : 0;
            int col = startToken != null ? startToken.getCharPositionInLine() : 0;
            fragments.add(new MSLNode(
                "StringCall",
                "string.IsNullOrEmpty",
                line,
                col,
                List.of(argNode)
            ));
        }
        return super.visitInvocation_expression(ctx);
    }
}
