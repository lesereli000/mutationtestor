package mutationtester.msl;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Matches a binary expression node (e.g., a + b) for a specific operator,
 * where both operands satisfy the given MSLExpression patterns.
 */
public class BinaryOpNode implements MSLExpression {

    /** Operators that never apply to strings, so a string-literal operand means concatenation. */
    private static final java.util.Set<String> ARITHMETIC_OPERATORS = java.util.Set.of(
            "+", "-", "*", "/", "%", "//", "**", "&", "|", "^", "<<", ">>", ">>>");

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
        boolean opMatch = false;
        for (String alias : scanner.getOperatorAliases(operator)) {
            if (scanner.isBinaryExpressionFor(ctx, alias)) {
                opMatch = true;
                break;
            }
        }
        if (!opMatch) return false;
        ParseTree leftOperand = scanner.getLeftOperand(ctx);
        ParseTree rightOperand = scanner.getRightOperand(ctx);
        if (ARITHMETIC_OPERATORS.contains(operator)
                && (isStringValued(scanner, leftOperand, 0) || isStringValued(scanner, rightOperand, 0))) {
            // String concatenation ("a" + b, or x + " " + y), not arithmetic.
            return false;
        }
        return left.matches(scanner, leftOperand)
                && right.matches(scanner, rightOperand);
    }

    /** A string literal, or a + whose operands include one: "a" + b + c is a string. */
    private static boolean isStringValued(MSLScannerAdapter scanner, ParseTree operand, int depth) {
        if (operand == null || depth > 50) return false;
        if (scanner.isStringLiteral(operand)) return true;
        return scanner.isBinaryExpressionFor(operand, "+")
                && (isStringValued(scanner, scanner.getLeftOperand(operand), depth + 1)
                    || isStringValued(scanner, scanner.getRightOperand(operand), depth + 1));
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
