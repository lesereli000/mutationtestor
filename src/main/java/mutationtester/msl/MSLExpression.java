package mutationtester.msl;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Represents a pattern that can be matched against a parse-tree node.
 * Implementations include QuantityNode, BinaryOpNode, etc.
 */
public interface MSLExpression {

    /**
     * Returns true if {@code ctx} matches this expression pattern,
     * using the given adapter to query language-specific structural facts.
     */
    boolean matches(MSLScannerAdapter scanner, ParseTree ctx);

    /**
     * Returns the source text of the matched node (used for reporting).
     */
    String getReplacementText(MSLScannerAdapter scanner, ParseTree ctx);
}
