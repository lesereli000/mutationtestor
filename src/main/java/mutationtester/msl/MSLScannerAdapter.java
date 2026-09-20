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

    /**
     * True when {@code ctx} is a prefix/postfix update for the canonical MSL
     * operator {@code ++} or {@code --}. Languages without those tokens map
     * the equivalent form (Racket {@code add1}/{@code sub1}). Default: false.
     */
    default boolean isUpdateExpressionFor(ParseTree ctx, String mslOperator) {
        return false;
    }

    /**
     * Maps a canonical MSL operator to the token that appears in this
     * language's source (identity for Java/JS/C#/Python; {@code ++}→{@code add1}
     * on Racket). Used when writing the MutationPoint so apply/restore splice
     * the real source token.
     */
    default String translateOperator(String mslOperator) {
        return mslOperator;
    }

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

    /**
     * Translates an Except: template from the shared infix spec into the
     * source-language form used by this adapter. Default: identity (templates
     * are already infix; matches JS/Python/C#). Languages with non-infix
     * operator syntax (e.g. Racket prefix) override to rewrite the
     * `{quantity} OP {quantity}` segments.
     */
    default String translateExceptTemplate(String infixTemplate) {
        return infixTemplate;
    }

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