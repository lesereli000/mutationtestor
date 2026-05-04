package mutationtester.scanner;

import mutationtester.JavaLexer;
import mutationtester.JavaParser;
import mutationtester.JavaParserBaseVisitor;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.List;

public class JavaMSLTreeBuilder extends JavaParserBaseVisitor<Void> {

    private final List<MSLNode> fragments = new ArrayList<>();

    public List<MSLNode> getFragments() {
        return fragments;
    }

    @Override
    public Void visitBinaryOperatorExpression(JavaParser.BinaryOperatorExpressionContext ctx) {
        if (ctx.getChildCount() != 3) {
            return super.visitBinaryOperatorExpression(ctx);
        }

        // Natural filter: Check if any child contains a string literal
        if (containsStringLiteral(ctx)) {
            return super.visitBinaryOperatorExpression(ctx); // Skip making an MSLNode
        }

        // Extract left and right operands as Quantity nodes (simplified for Phase 2 proof of concept)
        ParseTree leftChild = ctx.getChild(0);
        ParseTree rightChild = ctx.getChild(2);
        
        MSLNode leftNode = buildQuantityNode(leftChild);
        MSLNode rightNode = buildQuantityNode(rightChild);
        
        if (leftNode == null || rightNode == null) {
             return super.visitBinaryOperatorExpression(ctx);
        }

        // Find the operator token
        ParseTree opChild = ctx.getChild(1);
        if (opChild instanceof TerminalNode tn) {
            Token opToken = tn.getSymbol();
            String operatorText = opChild.getText();
            
            MSLNode binaryNode = new MSLNode(
                "BinaryExpr", 
                operatorText, 
                opToken.getLine(), 
                opToken.getCharPositionInLine(), 
                List.of(leftNode, rightNode)
            );
            
            fragments.add(binaryNode);
        }

        return super.visitBinaryOperatorExpression(ctx);
    }
    
    private MSLNode buildQuantityNode(ParseTree ctx) {
        // Simplified generic quantity extraction
        String text = ctx.getText();
        
        // Find the start token of this expression
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
            return tokenType == JavaLexer.STRING_LITERAL || tokenType == JavaLexer.TEXT_BLOCK;
        }

        for (int i = 0; i < tree.getChildCount(); i++) {
            if (containsStringLiteral(tree.getChild(i))) {
                return true;
            }
        }
        return false;
    }
}
