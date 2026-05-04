package mutationtester.msl;

import java.util.List;

/**
 * Parses an MSL pattern string into an MSLNode Dummy Pattern tree.
 *
 * Supported patterns (case-sensitive, whitespace-normalised):
 * {quantity} → Quantity Wildcard Node
 * {quantity} <op> {quantity} → BinaryExpr Exact Node(Wildcard, Wildcard)
 */
public class MSLPatternFactory {

    private MSLPatternFactory() {
    }

    private static int captureCounter = 0;

    public static MSLNode parse(String pattern) {
        String trimmed = pattern.strip();

        // Single quantity
        if (trimmed.equals("{quantity}")) {
            return new MSLNode("Quantity", "quant_" + (++captureCounter));
        }

        // Binary: {quantity} <op> {quantity}
        String[] parts = trimmed.split("\\s+");
        if (parts.length == 3
                && parts[0].equals("{quantity}")
                && parts[2].equals("{quantity}")) {
            String op = parts[1];
            MSLNode left = new MSLNode("Quantity", "quant_" + (++captureCounter));
            MSLNode right = new MSLNode("Quantity", "quant_" + (++captureCounter));
            return new MSLNode("BinaryExpr", op, List.of(left, right));
        }

        throw new IllegalArgumentException(
                "Unrecognised MSL pattern: \"" + pattern + "\". " +
                        "Expected \"{quantity}\" or \"{quantity} <op> {quantity}\".");
    }
}
