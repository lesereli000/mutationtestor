package mutationtester.scanner;


import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class JavaScriptMSLTreeBuilder extends JavaScriptParserBaseVisitor<Void> {
    private final List<MSLNode> fragments = new ArrayList<>();

    public List<MSLNode> getFragments() {
        return fragments;
    }

    @Override
    public Void visitAdditiveExpression(JavaScriptParser.AdditiveExpressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitAdditiveExpression(ctx);
    }

    @Override
    public Void visitMultiplicativeExpression(JavaScriptParser.MultiplicativeExpressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitMultiplicativeExpression(ctx);
    }

    @Override
    public Void visitRelationalExpression(JavaScriptParser.RelationalExpressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitRelationalExpression(ctx);
    }

    @Override
    public Void visitEqualityExpression(JavaScriptParser.EqualityExpressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitEqualityExpression(ctx);
    }

    @Override
    public Void visitLogicalAndExpression(JavaScriptParser.LogicalAndExpressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitLogicalAndExpression(ctx);
    }

    @Override
    public Void visitLogicalOrExpression(JavaScriptParser.LogicalOrExpressionContext ctx) {
        if (ctx.getChildCount() == 3) extractBinary(ctx);
        return super.visitLogicalOrExpression(ctx);
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
}
