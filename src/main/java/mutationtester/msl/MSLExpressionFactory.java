package mutationtester.msl;

import mutationtester.*;
/**
 * Parses an MSL pattern string into an MSLExpression tree.
 *
 * Supported patterns (case-sensitive, whitespace-normalised):
 *   {quantity}                       → QuantityNode
 *   {quantity} <op> {quantity}       → BinaryOpNode("<op>", QuantityNode, QuantityNode)
 *
 * Where <op> is any non-empty, non-brace token (e.g. +, -, *, /, %, &, |, ^, <<, >>, >>>).
 *
 * Throws IllegalArgumentException for unrecognised patterns.
 */
public class MSLExpressionFactory {

    private MSLExpressionFactory() {}

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
            return new BinaryOpNode(op, new QuantityNode(), new QuantityNode());
        }

        throw new IllegalArgumentException(
            "Unrecognised MSL pattern: \"" + pattern + "\". " +
            "Expected \"{quantity}\" or \"{quantity} <op> {quantity}\".");
    }
}
