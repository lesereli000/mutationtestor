package mutationtester.msl;

import java.util.List;

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
     * Returns language-local spellings that should be treated as aliases
     * for a canonical MSL operator (for example && -> and in Python).
     */
    default List<String> getOperatorAliases(String canonicalOperator) {
        return List.of(canonicalOperator);
    }

    /**
     * Translates a canonical MSL operator into the source-language token/text
     * that should be written when applying the mutation. Returns null when the
     * operator has no equivalent in this language, so no mutant is produced.
     */
    default String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
        return canonicalOperator;
    }

    /**
     * A source edit: replace {@code original}, which starts at
     * ({@code line}, {@code column}), with {@code replacement}.
     * {@code original} may span several lines.
     */
    record OperatorEdit(int line, int column, String original, String replacement) {}

    /**
     * Builds the edit that turns the binary expression {@code ctx}, matched for
     * {@code fromOperator}, into one using {@code toOperator}. Returns null when
     * the mutation cannot be expressed in this language.
     *
     * The default swaps the operator token. Languages whose operators are not a
     * single swappable token (e.g. Racket's (not (= a b))) override this.
     */
    default OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
        String replacement = toLanguageOperator(toOperator, ctx);
        if (replacement == null) {
            return null;
        }
        ParseTree opToken = getOperatorToken(ctx);
        int[] pos = getTokenPosition(opToken);
        String original = opToken != null ? getNodeText(opToken) : fromOperator;
        return new OperatorEdit(pos[0], pos[1], original, replacement);
    }

    ParseTree getLeftOperand(ParseTree ctx);

    ParseTree getRightOperand(ParseTree ctx);

    ParseTree getOperatorToken(ParseTree ctx);

    // ── Semantic predicate ────────────────────────────────────────────────

    boolean isQuantity(ParseTree ctx);

    /**
     * The {quantity} test shared by every language: an operand may be any
     * expression. A single-token operand must be a literal or an identifier,
     * which keeps punctuation tokens out.
     */
    static boolean isQuantityText(String text, boolean isSingleToken) {
        if (text == null) return false;
        String t = text.strip();
        if (t.isEmpty()) return false;
        return !isSingleToken || t.matches("[\\w$@.]+|'(?:\\\\.|[^'\\\\])*'|\"(?:\\\\.|[^\"\\\\])*\"");
    }

    /**
     * True when the operand is a string literal. Arithmetic and bitwise rules
     * skip these ("a" + b would become "a" - b, which doesn't compile), while
     * comparisons such as s == "" are still mutated.
     */
    default boolean isStringLiteral(ParseTree ctx) {
        return false;
    }

    /**
     * True for an all-ones 32- or 64-bit mask (0xFFFFFFFF, #xFFFFFFFF,
     * 4294967295, ...). Languages without >>> write x >>> n as a shift of
     * x masked to the word size, e.g. Python's (x & 0xFFFFFFFF) >> n.
     */
    static boolean isUnsignedShiftMask(String text) {
        return text.strip().matches("(?:0[xX]|#[xX])(?:[fF]{8}|[fF]{16})|4294967295|18446744073709551615");
    }

    /** "(left op right)", used when an idiom such as Math.pow(a, b) becomes a plain operator. */
    static String infix(String left, String operator, String right) {
        return "(" + left + " " + operator + " " + right + ")";
    }

    /** True when text is one string literal, optionally prefixed (f"..", $"..", @".."). */
    static boolean isStringLiteral(String text, boolean singleQuotedStrings) {
        String quotes = singleQuotedStrings ? "\"'`" : "\"`";
        int i = 0;
        while (i < text.length() && i < 3 && "$@fFrRbBuU".indexOf(text.charAt(i)) >= 0) i++;
        if (i >= text.length() || quotes.indexOf(text.charAt(i)) < 0) return false;
        char quote = text.charAt(i);
        return text.length() - i >= 2 && text.charAt(text.length() - 1) == quote;
    }

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

    /**
     * Returns language-local spellings that should be treated as aliases
     * for a canonical function name used in specs.
     */
    default List<String> getFunctionAliases(String canonicalFunctionName) {
        return List.of(canonicalFunctionName);
    }

    /**
     * For a null-or-empty string check written without a library call (e.g.
     * Python's s is None or s == ""), returns the checked expression s;
     * otherwise null. Lets string.IsNullOrEmpty rules apply in every language.
     */
    default String getNullOrEmptySubject(ParseTree ctx) {
        return null;
    }

    /** Regex for the subject of a C-family null-or-empty check: a name or dotted path. */
    String NULL_CHECK_SUBJECT = "([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)";

    /** Matches text against null-or-empty idioms whose group 1 is the subject. */
    static String matchNullOrEmptySubject(String text, List<java.util.regex.Pattern> idioms) {
        if (text == null || text.length() > 300) return null;
        String t = text.strip();
        for (java.util.regex.Pattern idiom : idioms) {
            java.util.regex.Matcher m = idiom.matcher(t);
            if (m.matches()) return m.group(1);
        }
        return null;
    }

    /** "x is not null", meaning the same as C#'s x != null. */
    default String notNullCheck(String subject) {
        return "(" + subject + " != null)";
    }

    /** "x is not the empty string", by value, meaning the same as C#'s x != "". */
    default String notEmptyStringCheck(String subject) {
        return "(" + subject + " != \"\")";
    }

    default boolean isInvocationStatement(ParseTree ctx) {
        return false;
    }

    /** A statement that does nothing, used in place of a removed call. */
    default String getEmptyStatement(ParseTree statement) {
        return ";";
    }

    // ── Utility ───────────────────────────────────────────────────────────

    String getNodeText(ParseTree ctx);

    int[] getTokenPosition(ParseTree ctx);

    ParseTree getRootContext();
}