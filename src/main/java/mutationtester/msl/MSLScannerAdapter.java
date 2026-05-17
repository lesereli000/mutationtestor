package mutationtester.msl;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Bridges MSLExpression nodes to a language-specific scanner/parser.
 * Each language scanner provides a static createAdapter(File) factory
 * that returns a concrete implementation.
 */
public interface MSLScannerAdapter {

    // ── Structural predicates ──────────────────────────────────────────────

    boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator);

    ParseTree getLeftOperand(ParseTree ctx);

    ParseTree getRightOperand(ParseTree ctx);

    ParseTree getOperatorToken(ParseTree ctx);

    // ── Semantic predicate ────────────────────────────────────────────────

    boolean isQuantity(ParseTree ctx);

    // ── Context access ────────────────────────────────────────────────────

    /**
     * Returns the full source line that contains the given parse tree node.
     * Used by ReplacementRule to check Except: patterns against the
     * surrounding context.
     *
     * Returns an empty string if the source line cannot be determined.
     */
    default String getSourceLine(ParseTree ctx) {
        return "";
    }

    // ── Optional language-specific helpers ────────────────────────────────

    default String getSingleArgumentText(ParseTree ctx, String functionName) {
        return null;
    }

    default boolean isInvocationStatement(ParseTree ctx) {
        return false;
    }

    // ── Utility ───────────────────────────────────────────────────────────

    String getNodeText(ParseTree ctx);

    int[] getTokenPosition(ParseTree ctx);

    ParseTree getRootContext();
}