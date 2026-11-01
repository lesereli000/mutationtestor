package mutationtester.msl;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Interprets MSL trees against one language's CST. Each language scanner
 * provides a static createAdapter(File) factory that returns an implementation.
 *
 * The shared {@link MSLMatcher} walks a pattern tree and asks the adapter one
 * node at a time: does this CST node have the pattern node's kind and value,
 * and which CST children line up with the pattern's children
 * ({@link #matchNode}); does this CST node fit a placeholder type
 * ({@link #matchesPlaceholder}). Rules then ask the adapter to write the
 * result in its language ({@link #replace}, {@link #delete}, {@link #insert}).
 *
 * Adapters work only from the CST: node types, tokens and children. Language
 * idioms (Python's "and" for &&, Java's a.equals(b) for ==) live here, never in
 * spec files.
 */
public interface MSLScannerAdapter {

    /**
     * A source edit: replace {@code original}, which starts at
     * ({@code line}, {@code column}), with {@code replacement}.
     * {@code original} may span several lines, and is empty for an insertion.
     */
    record OperatorEdit(int line, int column, String original, String replacement) {}

    // ── Tree matching ─────────────────────────────────────────────────────

    /**
     * The CST nodes lined up with {@code pattern}'s children when {@code ctx} is
     * the construct the pattern node describes, or null when it isn't. A null
     * entry stands for a missing part (a for loop with no update).
     *
     * The default handles the expression kinds shared by every language;
     * adapters override it for statements and their own idioms, and return
     * null for kinds they don't know, so the rule simply doesn't apply.
     */
    default List<ParseTree> matchNode(MSLNode pattern, ParseTree ctx) {
        return switch (pattern.kind()) {
            case MSLNode.BINARY -> matchBinary(pattern.value(), ctx);
            case MSLNode.PREDICATE -> {
                ParseTree subject = "is null".equals(pattern.value()) ? nullCheckSubject(ctx) : null;
                yield subject == null ? null : List.of(subject);
            }
            case MSLNode.LITERAL -> {
                String spelled = renderLiteral(pattern.value(), ctx);
                yield spelled != null && isSingleValue(ctx) && getNodeText(ctx).strip().equals(spelled)
                        ? List.of() : null;
            }
            case MSLNode.IDENTIFIER -> isSingleValue(ctx) && getNodeText(ctx).strip().equals(pattern.value())
                    ? List.of() : null;
            case MSLNode.CALL -> callArguments(ctx, pattern.value());
            case MSLNode.INCREMENT -> {
                ParseTree operand = incrementOperand(pattern.value(), ctx);
                yield operand == null ? null : List.of(operand);
            }
            default -> null;
        };
    }

    /**
     * The variable when ctx adds one to it ("++") or subtracts one ("--"), in
     * whatever form the language writes that (x++, ++x, x += 1, (add1 x)),
     * else null.
     */
    default ParseTree incrementOperand(String operator, ParseTree ctx) {
        return null;
    }

    /**
     * The statement when ctx is the whole expression of an expression
     * statement (x++; or foo();), else null. Delete: rules on expression
     * patterns remove that statement, and are skipped elsewhere.
     */
    default ParseTree expressionStatement(ParseTree ctx) {
        return null;
    }

    /**
     * True when ctx's value is thrown away: an expression statement, or a
     * for loop's update. Replacing such a node with just one of its operands
     * (x++ with x) would only delete it, and doesn't compile in Java or C#.
     */
    default boolean discardsValue(ParseTree ctx) {
        return expressionStatement(ctx) != null;
    }

    /**
     * The argument nodes when ctx is a call to the named function (a name or
     * dotted path such as Math.max), else null.
     */
    default List<ParseTree> callArguments(ParseTree ctx, String functionName) {
        return null;
    }

    /**
     * True when {@code ctx} fits a placeholder type. Adapters add the types
     * their language supports; an unknown type matches nothing.
     */
    default boolean matchesPlaceholder(String type, ParseTree ctx) {
        return switch (type) {
            case MSLPlaceholder.QUANTITY -> isQuantity(ctx);
            case "invocation_statement" -> isInvocationStatement(ctx);
            default -> false;
        };
    }

    /**
     * True when the CST node in an Except: tree's {match} position is the
     * mutated node, allowing for wrapper nodes with a single child.
     */
    default boolean isSameNode(ParseTree position, ParseTree mutated) {
        for (ParseTree p = position; p != null; p = p.getChildCount() == 1 ? p.getChild(0) : null) {
            if (p == mutated) return true;
        }
        return false;
    }

    /** True when ctx is one token or a chain of single-child wrappers around one. */
    default boolean isSingleValue(ParseTree ctx) {
        ParseTree p = ctx;
        while (p != null && p.getChildCount() == 1) p = p.getChild(0);
        return p instanceof TerminalNode;
    }

    /** Operator kinds and placeholder types this adapter understands, for reporting rules it can't apply. */
    default Set<String> supportedKinds() {
        return Set.of(MSLNode.BINARY, MSLNode.PREDICATE, MSLNode.LITERAL, MSLNode.IDENTIFIER, MSLNode.CALL);
    }

    default Set<String> supportedPlaceholders() {
        return Set.of(MSLPlaceholder.QUANTITY, MSLPlaceholder.MATCH, "invocation_statement");
    }

    /** True when every kind and placeholder in the tree is one this adapter understands. */
    default boolean supports(MSLTree tree) {
        if (tree instanceof MSLPlaceholder p) {
            return (supportedPlaceholders().contains(p.type()) || isOutputPlaceholder(p.type()))
                    && (p.constraint() == null || supports(p.constraint()));
        }
        MSLNode node = (MSLNode) tree;
        if (!supportedKinds().contains(node.kind()) && !node.kind().equals(MSLNode.EMPTY)) return false;
        for (MSLTree child : node.children()) {
            if (!supports(child)) return false;
        }
        return true;
    }

    // ── Binary operators ──────────────────────────────────────────────────

    /** [left, right] when ctx is a binary expression for the canonical operator, else null. */
    default List<ParseTree> matchBinary(String operator, ParseTree ctx) {
        boolean opMatch = false;
        for (String alias : getOperatorAliases(operator)) {
            if (isBinaryExpressionFor(ctx, alias)) {
                opMatch = true;
                break;
            }
        }
        if (!opMatch) return null;
        ParseTree left = getLeftOperand(ctx);
        ParseTree right = getRightOperand(ctx);
        if (ARITHMETIC_OPERATORS.contains(operator)
                && (isStringValued(left, 0) || isStringValued(right, 0))) {
            // String concatenation ("a" + b, or x + " " + y), not arithmetic.
            return null;
        }
        List<ParseTree> operands = new ArrayList<>(2);
        operands.add(left);
        operands.add(right);
        return operands;
    }

    /** Operators that never apply to strings, so a string-literal operand means concatenation. */
    Set<String> ARITHMETIC_OPERATORS = Set.of(
            "+", "-", "*", "/", "%", "//", "**", "&", "|", "^", "<<", ">>", ">>>");

    /** A string literal, or a + whose operands include one: "a" + b + c is a string. */
    private boolean isStringValued(ParseTree operand, int depth) {
        if (operand == null || depth > 50) return false;
        if (isStringLiteral(operand)) return true;
        return isBinaryExpressionFor(operand, "+")
                && (isStringValued(getLeftOperand(operand), depth + 1)
                    || isStringValued(getRightOperand(operand), depth + 1));
    }

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

    // ── Placeholders ──────────────────────────────────────────────────────

    /** True when ctx can stand for {quantity}: any expression, or a single literal or name token. */
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
        return !isSingleToken || isNameOrNumber(t) || isQuotedLiteral(t, '\'') || isQuotedLiteral(t, '"');
    }

    /** Letters, digits, '_', '$', '@' and '.', the characters of names and numbers. */
    private static boolean isNameOrNumber(String t) {
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$' && c != '@' && c != '.') return false;
        }
        return true;
    }

    /** 'x' or "x" with no unescaped quote inside. */
    private static boolean isQuotedLiteral(String t, char quote) {
        if (t.length() < 2 || t.charAt(0) != quote || t.charAt(t.length() - 1) != quote) return false;
        for (int i = 1; i < t.length() - 1; i++) {
            char c = t.charAt(i);
            if (c == '\\') i++;
            else if (c == quote) return false;
        }
        // The closing quote must not be escaped.
        int backslashes = 0;
        for (int i = t.length() - 2; i > 0 && t.charAt(i) == '\\'; i--) backslashes++;
        return backslashes % 2 == 0;
    }

    /**
     * True when the operand is a string literal. Arithmetic and bitwise rules
     * skip these ("a" + b would become "a" - b, which doesn't compile), while
     * comparisons such as s == "" are still mutated.
     */
    default boolean isStringLiteral(ParseTree ctx) {
        return false;
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

    /** The all-ones 32- and 64-bit masks, as Java, Python and Racket write them. */
    Set<String> UNSIGNED_SHIFT_MASKS = Set.of(
            "0xffffffff", "0xffffffffffffffff", "#xffffffff", "#xffffffffffffffff",
            "4294967295", "18446744073709551615");

    /**
     * True for an all-ones 32- or 64-bit mask (0xFFFFFFFF, #xFFFFFFFF,
     * 4294967295, ...). Languages without >>> write x >>> n as a shift of
     * x masked to the word size, e.g. Python's (x & 0xFFFFFFFF) >> n.
     */
    static boolean isUnsignedShiftMask(String text) {
        return UNSIGNED_SHIFT_MASKS.contains(text.strip().toLowerCase());
    }

    /** True when text is an optional '-' followed by one or more digits. */
    static boolean isIntegerText(String text, boolean allowNegative) {
        int i = allowNegative && text.startsWith("-") ? 1 : 0;
        if (i >= text.length()) return false;
        for (; i < text.length(); i++) {
            if (!Character.isDigit(text.charAt(i))) return false;
        }
        return true;
    }

    /**
     * The checked expression when ctx is a null-or-empty check, the meaning of
     * MSL's "{quantity} is null": string.IsNullOrEmpty(s) in C#, s is None or
     * s == "" in Python, and so on. Null otherwise.
     */
    default ParseTree nullCheckSubject(ParseTree ctx) {
        return null;
    }

    default boolean isInvocationStatement(ParseTree ctx) {
        return false;
    }

    /**
     * Returns true if no rule should mutate anything inside this node, such as
     * a Racket macro definition, whose patterns and templates aren't ordinary
     * expressions. Default: every node can be mutated.
     */
    default boolean skipsSubtree(ParseTree ctx) {
        return false;
    }

    // ── Rewriting ─────────────────────────────────────────────────────────

    /**
     * The edit that replaces the node {@code from} matched with {@code to}.
     * An operator swap keeps the operands' text and changes only the operator
     * (via {@link #createOperatorEdit}); anything else replaces the matched
     * node's text with the rendered {@code to} tree.
     */
    default OperatorEdit replace(ParseTree ctx, MSLTree from, MSLTree to, MSLBindings bindings) {
        if (isOperatorSwap(from, to)) {
            return createOperatorEdit(ctx, ((MSLNode) from).value(), ((MSLNode) to).value());
        }
        String text = render(to, bindings.cursor(), ctx);
        if (text == null) return null;
        int[] pos = getTokenPosition(ctx);
        return new OperatorEdit(pos[0], pos[1], getNodeText(ctx), text);
    }

    /** Both trees are the same kind of operator over the same placeholders, in the same order. */
    private static boolean isOperatorSwap(MSLTree from, MSLTree to) {
        if (!(from instanceof MSLNode f) || !(to instanceof MSLNode t)
                || !f.kind().equals(t.kind()) || !f.kind().equals(MSLNode.BINARY)
                || f.children().size() != t.children().size()) {
            return false;
        }
        for (int i = 0; i < f.children().size(); i++) {
            if (!(f.child(i) instanceof MSLPlaceholder a) || !(t.child(i) instanceof MSLPlaceholder b)
                    || !a.type().equals(b.type())) {
                return false;
            }
        }
        return true;
    }

    /** Removes the matched statement, leaving the language's empty statement. */
    default OperatorEdit delete(ParseTree ctx, MSLBindings bindings) {
        int[] pos = getTokenPosition(ctx);
        return new OperatorEdit(pos[0], pos[1], getNodeText(ctx), getEmptyStatement(ctx));
    }

    /**
     * Inserts {@code tree} as a new statement on its own line before or after
     * the anchor statement, at the anchor's indentation.
     */
    default OperatorEdit insert(ParseTree anchor, MSLTree tree, boolean before, MSLBindings bindings) {
        String statement = renderStatement(tree, bindings.cursor(), anchor);
        if (statement == null) return null;
        String indent = getIndentation(anchor);
        if (before) {
            int[] pos = getTokenPosition(anchor);
            return new OperatorEdit(pos[0], pos[1], "", statement + "\n" + indent);
        }
        int[] end = getEndPosition(anchor);
        return new OperatorEdit(end[0], end[1], "", "\n" + indent + statement);
    }

    /**
     * Writes a tree in this language. Placeholders take the text of the node
     * they matched; output placeholders such as {empty_value} ask
     * {@link #renderValue}. Returns null when the tree can't be written.
     */
    default String render(MSLTree tree, MSLBindings.Cursor bindings, ParseTree ctx) {
        if (tree instanceof MSLPlaceholder p) {
            ParseTree bound = bindings.next(p.type());
            return bound != null ? getNodeText(bound) : renderValue(p.type(), ctx);
        }
        MSLNode node = (MSLNode) tree;
        List<String> parts = new ArrayList<>();
        for (MSLTree child : node.children()) {
            String part = render(child, bindings, ctx);
            if (part == null) return null;
            parts.add(part);
        }
        return switch (node.kind()) {
            case MSLNode.LITERAL -> renderLiteral(node.value(), ctx);
            case MSLNode.IDENTIFIER -> node.value();
            case MSLNode.BINARY -> renderBinary(node, parts.get(0), parts.get(1), ctx);
            case MSLNode.UNARY -> node.value() + parts.get(0);
            case MSLNode.INCREMENT -> renderIncrement(node.value(), parts.get(0), ctx);
            case MSLNode.CALL -> node.value() + "(" + String.join(", ", parts) + ")";
            // ";" in a With: means the statement is removed.
            case MSLNode.EMPTY -> getEmptyStatement(ctx);
            default -> null;
        };
    }

    /**
     * Writes a binary operator. {@code x != null} and {@code x != ""} use the
     * language's own null and empty-string checks.
     */
    default String renderBinary(MSLNode node, String left, String right, ParseTree ctx) {
        if (node.value().equals("!=") && node.child(1) instanceof MSLNode r && r.kind().equals(MSLNode.LITERAL)) {
            if (r.value().equals("null")) return notNullCheck(left);
            if (r.value().equals("\"\"")) return notEmptyStringCheck(left);
        }
        String op = toLanguageOperator(node.value(), null);
        return op == null ? null : MSLScannerAdapter.infix(left, op, right);
    }

    /**
     * Writes an increment or decrement of {@code operand}. The default is
     * C-like, keeping the prefix or postfix form of the node it replaces.
     */
    default String renderIncrement(String operator, String operand, ParseTree ctx) {
        String text = ctx == null ? "" : getNodeText(ctx).strip();
        boolean prefix = text.startsWith("++") || text.startsWith("--");
        return prefix ? operator + operand : operand + operator;
    }

    /** "x is not null", meaning the same as C#'s x != null. */
    default String notNullCheck(String subject) {
        return "(" + subject + " != null)";
    }

    /** "x is not the empty string", by value, meaning the same as C#'s x != "". */
    default String notEmptyStringCheck(String subject) {
        return "(" + subject + " != \"\")";
    }

    /** A canonical literal (true, false, null, numbers, strings) as this language writes it at ctx. */
    default String renderLiteral(String canonical, ParseTree ctx) {
        return canonical;
    }

    /** True for placeholder types whose value the adapter computes rather than reads from a match. */
    default boolean isOutputPlaceholder(String type) {
        return type.equals("empty_value") || type.equals("default_value");
    }

    /**
     * The value of an output placeholder such as {empty_value} or
     * {default_value} for the matched node ctx, or null when there is none.
     */
    default String renderValue(String placeholderType, ParseTree ctx) {
        return null;
    }

    /** Writes a tree as a complete statement, for Insert:. Null when it can't be written. */
    default String renderStatement(MSLTree tree, MSLBindings.Cursor bindings, ParseTree ctx) {
        if (tree instanceof MSLNode node && node.kind().equals(MSLNode.ASSIGN)) {
            String target = render(node.child(0), bindings, ctx);
            String value = render(node.child(1), bindings, ctx);
            return target == null || value == null ? null : target + " = " + value + ";";
        }
        String expression = render(tree, bindings, ctx);
        return expression == null ? null : expression + ";";
    }

    /** "(left op right)", used when an idiom such as Math.pow(a, b) becomes a plain operator. */
    static String infix(String left, String operator, String right) {
        return "(" + left + " " + operator + " " + right + ")";
    }

    /** A statement that does nothing, used in place of a removed call. */
    default String getEmptyStatement(ParseTree statement) {
        return ";";
    }

    // ── Utility ───────────────────────────────────────────────────────────

    String getNodeText(ParseTree ctx);

    int[] getTokenPosition(ParseTree ctx);

    /** Line and column just after the node's last character. */
    default int[] getEndPosition(ParseTree ctx) {
        Token stop = ctx instanceof TerminalNode tn ? tn.getSymbol()
                : ctx instanceof ParserRuleContext prc ? prc.getStop() : null;
        if (stop == null) return getTokenPosition(ctx);
        String text = stop.getText();
        int lastNewline = text.lastIndexOf('\n');
        if (lastNewline < 0) {
            return new int[]{stop.getLine(), stop.getCharPositionInLine() + text.length()};
        }
        int lines = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') lines++;
        return new int[]{stop.getLine() + lines, text.length() - lastNewline - 1};
    }

    /** The whitespace that starts the node's first line. */
    default String getIndentation(ParseTree ctx) {
        int[] pos = getTokenPosition(ctx);
        return " ".repeat(Math.max(pos[1], 0));
    }

    ParseTree getRootContext();
}
