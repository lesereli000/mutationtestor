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
            "&&", "||", "&", "|", "^", "<<", ">>", ">>>",
            "+=", "-=");

    private static final Set<String> CANONICAL_UPDATES = Set.of("++", "--");

    private MSLExpressionFactory() {
    }

    public static MSLExpression parse(String pattern) {
        String trimmed = pattern.strip();

        // Single quantity
        if (trimmed.equals("{quantity}")) {
            return new QuantityNode();
        }

        String[] parts = trimmed.split("\\s+");

        // Postfix update: {quantity} ++   /   {quantity} --
        if (parts.length == 2
                && parts[0].equals("{quantity}")
                && CANONICAL_UPDATES.contains(parts[1])) {
            return new UpdateNode(parts[1], new QuantityNode());
        }

        // Binary: {quantity} <op> {quantity}
        if (parts.length == 3
                && parts[0].equals("{quantity}")
                && parts[2].equals("{quantity}")) {
            String op = parts[1];
            if (CANONICAL_UPDATES.contains(op)) {
                throw new IllegalArgumentException(
                        "Unsupported MSL operator \"" + op + "\" in \"" + pattern + "\"."
                                + " Use the canonical postfix form \"{quantity} " + op + "\".");
            }
            validateCanonicalOperator(op);
            return new BinaryOpNode(op, new QuantityNode(), new QuantityNode());
        }

        throw new IllegalArgumentException(
                "Unrecognised MSL pattern: \"" + pattern + "\". " +
                        "Expected \"{quantity}\", \"{quantity} ++\", \"{quantity} --\","
                        + " or \"{quantity} <op> {quantity}\".");
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
            case "INC", "PLUSPLUS", "ADD1" -> "++";
            case "DEC", "MINUSMINUS", "SUB1" -> "--";
            default -> null;
        };

        String guidance = canonical == null
                ? " Supported operators: " + CANONICAL_OPERATORS
                : " Use the canonical operator \"" + canonical + "\".";
        throw new IllegalArgumentException(
                "Unsupported MSL operator \"" + operator + "\"." + guidance);
    }
}
