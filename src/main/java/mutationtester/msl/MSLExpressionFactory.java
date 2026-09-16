package mutationtester.msl;

import java.util.Set;

/**
 * Parses an MSL pattern string into an MSLExpression tree.
 *
 * Supported patterns (case-sensitive, whitespace-normalised):
 * {quantity} → QuantityNode
 * {quantity} <op> {quantity} → BinaryOpNode("<op>", QuantityNode, QuantityNode)
 *
 * Where <op> is one of MSL's canonical, language-independent operator symbols.
 * Parser token names and word aliases (for example LE, LEQ, or leq) are
 * deliberately rejected so that the same specification has one spelling in
 * every supported language.
 *
 * Throws IllegalArgumentException for unrecognised patterns.
 */
public class MSLExpressionFactory {

    private static final Set<String> CANONICAL_OPERATORS = Set.of(
            "+", "-", "*", "/", "%", "//", "**",
            "<", "<=", ">", ">=", "==", "!=",
            "&&", "||", "&", "|", "^", "<<", ">>", ">>>");

    private MSLExpressionFactory() {
    }

    public static MSLExpression parse(String pattern) {
        String trimmed = pattern.strip();

        // Single quantity
        if (trimmed.equals("{quantity}")) {
            return new QuantityNode();
        }

        // Binary: {quantity} <op> {quantity}
        // Split on whitespace; tokens must be exactly 3
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 3
                && parts[0].equals("{quantity}")
                && parts[2].equals("{quantity}")) {
            String op = parts[1];
            validateCanonicalOperator(op);
            return new BinaryOpNode(op, new QuantityNode(), new QuantityNode());
        }

        throw new IllegalArgumentException(
                "Unrecognised MSL pattern: \"" + pattern + "\". " +
                        "Expected \"{quantity}\" or \"{quantity} <op> {quantity}\".");
    }

    private static void validateCanonicalOperator(String operator) {
        if (CANONICAL_OPERATORS.contains(operator)) {
            return;
        }

        String canonical = switch (operator.toUpperCase()) {
            case "LT" -> "<";
            case "LE", "LEQ", "LTE" -> "<=";
            case "GT" -> ">";
            case "GE", "GEQ", "GTE" -> ">=";
            case "EQ" -> "==";
            case "NE", "NEQ" -> "!=";
            case "AND" -> "&&";
            case "OR" -> "||";
            default -> null;
        };

        String guidance = canonical == null
                ? " Supported operators: " + CANONICAL_OPERATORS
                : " Use the canonical operator \"" + canonical + "\".";
        throw new IllegalArgumentException(
                "Unsupported MSL operator \"" + operator + "\"." + guidance);
    }
}
