package mutationtester.msl;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Matches a binary expression node (e.g., a + b) for a specific operator,
 * where both operands satisfy the given MSLExpression patterns.
 */
public class BinaryOpNode implements MSLExpression {

    private final String operator;
    private final MSLExpression left;
    private final MSLExpression right;

    public BinaryOpNode(String operator, MSLExpression left, MSLExpression right) {
        this.operator = operator;
        this.left = left;
        this.right = right;
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        if (!scanner.isBinaryExpressionFor(ctx, operator))
            return false;
        return left.matches(scanner, scanner.getLeftOperand(ctx))
                && right.matches(scanner, scanner.getRightOperand(ctx));
    }

    @Override
    public String getReplacementText(MSLScannerAdapter scanner, ParseTree ctx) {
        return scanner.getNodeText(ctx);
    }

    /** Returns the MSL operator string this node matches (e.g., "+", ">>"). */
    public String getOperator() {
        return operator;
    }
}
