package mutationtester.msl;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Matches a prefix/postfix update ({@code ++} / {@code --}) or the
 * language equivalent (Racket {@code add1}/{@code sub1}).
 *
 * Spec spelling is always {@code {quantity} ++} or {@code {quantity} --}.
 * Adapters match both prefix and postfix source forms to that one spelling.
 */
public class UpdateNode implements MSLExpression {

    private final String operator;
    private final MSLExpression operand;

    public UpdateNode(String operator, MSLExpression operand) {
        this.operator = operator;
        this.operand = operand;
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        return scanner.isUpdateExpressionFor(ctx, operator);
    }

    @Override
    public String getReplacementText(MSLScannerAdapter scanner, ParseTree ctx) {
        return scanner.getNodeText(ctx);
    }

    /** Canonical MSL operator: "++" or "--". */
    public String getOperator() {
        return operator;
    }

    public MSLExpression getOperand() {
        return operand;
    }
}
