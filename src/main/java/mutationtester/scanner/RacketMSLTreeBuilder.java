package mutationtester.scanner;


import mutationtester.ISLParser;
import mutationtester.ISLBaseVisitor;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class RacketMSLTreeBuilder extends ISLBaseVisitor<Void> {
    private final List<MSLNode> fragments = new ArrayList<>();

    public List<MSLNode> getFragments() {
        return fragments;
    }

    @Override
    public Void visitExpr(ISLParser.ExprContext ctx) {
        if (ctx.getChildCount() >= 4 && ctx.getChild(0).getText().equals("(")) {
            ParseTree opChild = ctx.getChild(1);
            ParseTree leftChild = ctx.getChild(2);
            ParseTree rightChild = ctx.getChild(3);

            MSLNode leftNode = buildQuantityNode(leftChild);
            MSLNode rightNode = buildQuantityNode(rightChild);
            
            if (leftNode != null && rightNode != null) {
                Token opToken = getStartToken(opChild);
                if (opToken != null) {
                    fragments.add(new MSLNode(
                        "BinaryExpr", 
                        opToken.getText(), 
                        opToken.getLine(), 
                        opToken.getCharPositionInLine(), 
                        List.of(leftNode, rightNode)
                    ));
                }
            }
        }
        return super.visitExpr(ctx);
    }

    private MSLNode buildQuantityNode(ParseTree ctx) {
        if (ctx == null) return null;
        String text = ctx.getText();
        Token startToken = getStartToken(ctx);
        if (startToken != null) {
             return new MSLNode("Quantity", text, startToken.getLine(), startToken.getCharPositionInLine(), List.of());
        }
        return null;
    }

    private Token getStartToken(ParseTree ctx) {
        if (ctx instanceof org.antlr.v4.runtime.ParserRuleContext prc) {
            return prc.getStart();
        } else if (ctx instanceof TerminalNode tn) {
            return tn.getSymbol();
        }
        return null;
    }
}
