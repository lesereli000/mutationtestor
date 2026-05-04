package mutationtester.scanner;


import mutationtester.PythonParser;
import mutationtester.PythonParserBaseVisitor;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class PythonMSLTreeBuilder extends PythonParserBaseVisitor<Void> {
    private final List<MSLNode> fragments = new ArrayList<>();

    public List<MSLNode> getFragments() {
        return fragments;
    }

    @Override
    public Void visitExpr(PythonParser.ExprContext ctx) {
        if (ctx.op != null && ctx.getChildCount() == 3) {
            addBinaryFragment(ctx.getChild(0), ctx.op, ctx.getChild(2));
        }
        return super.visitExpr(ctx);
    }

    @Override
    public Void visitComparison(PythonParser.ComparisonContext ctx) {
        if (ctx.getChildCount() == 3) {
            ParseTree opChild = ctx.getChild(1);
            if (opChild instanceof org.antlr.v4.runtime.ParserRuleContext opCtx) {
                addBinaryFragment(ctx.getChild(0), opCtx.getStart(), ctx.getChild(2));
            } else if (opChild instanceof TerminalNode tn) {
                addBinaryFragment(ctx.getChild(0), tn.getSymbol(), ctx.getChild(2));
            }
        }
        return super.visitComparison(ctx);
    }

    @Override
    public Void visitLogical_test(PythonParser.Logical_testContext ctx) {
        if (ctx.op != null && ctx.getChildCount() == 3) {
            addBinaryFragment(ctx.getChild(0), ctx.op, ctx.getChild(2));
        }
        return super.visitLogical_test(ctx);
    }

    private void addBinaryFragment(ParseTree leftChild, Token opToken, ParseTree rightChild) {
        MSLNode leftNode = buildQuantityNode(leftChild);
        MSLNode rightNode = buildQuantityNode(rightChild);
        
        if (leftNode != null && rightNode != null && opToken != null) {
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
