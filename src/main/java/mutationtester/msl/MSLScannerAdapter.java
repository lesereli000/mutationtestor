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
            case MSLNode.CALL -> pattern.value().isEmpty() ? callParts(ctx) : callArguments(ctx, pattern.value());
            case MSLNode.INCREMENT -> {
                ParseTree operand = incrementOperand(pattern.value(), ctx);
                yield operand == null ? null : List.of(operand);
            }
            default -> null;
        };
    }

    /** matchNode inside an Except: tree, for idioms that only count there. */
    default List<ParseTree> matchNodeInExcept(MSLNode pattern, ParseTree ctx) {
        // An if (c) s exception covers ifs with an else too, as it always has.
        if (pattern.kind().equals(MSLNode.IF) && ifParts(ctx) != null) return matchIf(pattern, ctx, true);
        return matchNode(pattern, ctx);
    }

    /** [condition, then, else or null] when ctx is an if statement, else null. */
    default List<ParseTree> ifParts(ParseTree ctx) {
        return null;
    }

    /** if (c) s matches ifs without an else (any if when lenient); if (c) s else s needs one. */
    default List<ParseTree> matchIf(MSLNode pattern, ParseTree ctx, boolean lenient) {
        List<ParseTree> parts = ifParts(ctx);
        if (parts == null) return null;
        if (pattern.children().size() == 2) {
            return parts.get(2) == null || lenient ? List.of(parts.get(0), parts.get(1)) : null;
        }
        return parts.get(2) != null ? parts : null;
    }

    /** True when ctx meets a word constraint such as literal or int64. */
    default boolean satisfies(String condition, String type, ParseTree ctx) {
        if (!isSingleValue(ctx)) return false;
        String text = getNodeText(ctx).strip();
        ValueKind kind = valueKind(ctx);
        return switch (condition) {
            case "literal" -> kind != null ? !kind.typeName() : isLiteralText(text, ctx);
            case "type" -> kind != null && kind.typeName();
            case "null" -> text.equals(renderLiteral("null", ctx));
            case "boolean" -> kind != null ? kind.type().equals("boolean")
                    : text.equals(renderLiteral("true", ctx)) || text.equals(renderLiteral("false", ctx));
            case "string" -> kind != null ? kind.type().equals("string") : isStringLiteral(ctx);
            case "number" -> kind != null ? kind.isNumber() && !kind.typeName() : CstNodes.isNumberText(text);
            case "integer", "float", "char", "bytes" -> kind != null && kind.fits(condition);
            case "collection" -> false;
            // Size words: int8 ... uint64, bigint, float32, float64, decimal
            default -> kind != null && condition.equals(kind.size());
        };
    }

    /** True when the single-token text is a literal: a number, string, boolean or null. */
    default boolean isLiteralText(String text, ParseTree ctx) {
        return CstNodes.isNumberText(text) || isStringLiteral(ctx)
                || text.equals(renderLiteral("true", ctx)) || text.equals(renderLiteral("false", ctx))
                || text.equals(renderLiteral("null", ctx));
    }

    /** {@code node} written as another type or size (42 as {float} is 42.0); null if impossible. */
    default String convert(ParseTree node, String fromType, String toType, String toCondition, ParseTree ctx) {
        return null;
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

    /** [callee, arguments...] when ctx is a call, else null. */
    default List<ParseTree> callParts(ParseTree ctx) {
        return null;
    }

    /**
     * True when {@code ctx} fits a placeholder type. Adapters add the types
     * their language supports; an unknown type matches nothing. Number, text
     * and boolean placeholders are answered by {@link #valueKind}, {condition}
     * by the adapter's own if, while, for and conditional matching, and
     * {name} by the token type.
     */
    default boolean matchesPlaceholder(String type, ParseTree ctx) {
        return switch (type) {
            case MSLPlaceholder.QUANTITY -> isQuantity(ctx);
            case "invocation_statement" -> isInvocationStatement(ctx);
            case "number", "integer", "float", "exponential", "based_integer", "rational", "complex",
                 "string", "char", "bytes", "boolean" -> {
                ValueKind kind = valueKind(ctx);
                yield kind != null && kind.fits(type);
            }
            case "condition" -> isConditionPosition(ctx);
            case "block" -> isBlock(ctx);
            case "collection" -> matchNode(new MSLNode(MSLNode.COLLECTION, "", new MSLPlaceholder(MSLPlaceholder.ARGUMENTS)), ctx) != null;
            case "lambda" -> isLambda(ctx);
            case "type" -> isTypeName(ctx);
            case "field" -> isField(ctx);
            case "name" -> isName(ctx);
            default -> false;
        };
    }

    /** The kind of literal or numeric type name ctx is, or null. */
    default ValueKind valueKind(ParseTree ctx) {
        return null;
    }

    /** The slots that hold a condition: [kind, number of children, condition position]. */
    Object[][] CONDITION_SLOTS = {
        {MSLNode.IF, 2, 0}, {MSLNode.IF, 3, 0}, {MSLNode.WHILE, 2, 0}, {MSLNode.FOR, 4, 1},
        {MSLNode.CONDITIONAL, 3, 0}, {"do_while", 2, 1}, {MSLNode.COMPREHENSION, 4, 3}, {MSLNode.WHERE, 1, 0},
        {MSLNode.WHEN, 2, 0}, {MSLNode.UNLESS, 2, 0},
    };

    /** True when ctx is the condition of an if, loop or conditional expression. */
    default boolean isConditionPosition(ParseTree ctx) {
        ParseTree p = ctx.getParent();
        for (int depth = 0; p != null && depth < 6; depth++, p = p.getParent()) {
            for (Object[] slot : CONDITION_SLOTS) {
                MSLTree[] holes = new MSLTree[(Integer) slot[1]];
                java.util.Arrays.fill(holes, new MSLPlaceholder(MSLPlaceholder.QUANTITY));
                List<ParseTree> children = matchNode(new MSLNode((String) slot[0], "", holes), p);
                int at = (Integer) slot[2];
                if (children != null && children.size() > at && children.get(at) != null
                        && isSameNode(children.get(at), ctx)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** True when ctx is a lambda / anonymous function. */
    default boolean isLambda(ParseTree ctx) {
        return false;
    }

    /** True when ctx is a type name (in a declaration, cast, type check or annotation). */
    default boolean isTypeName(ParseTree ctx) {
        return false;
    }

    /** True when ctx is a field (member variable) declaration. */
    default boolean isField(ParseTree ctx) {
        return false;
    }

    /** True when ctx is a whole block of statements: { ... }, an indented suite, (begin ...). */
    default boolean isBlock(ParseTree ctx) {
        return false;
    }

    /** True when ctx is one identifier token (through single-child wrappers). */
    default boolean isName(ParseTree ctx) {
        ParseTree p = ctx;
        while (p != null && !(p instanceof TerminalNode) && p.getChildCount() == 1) p = p.getChild(0);
        return p instanceof TerminalNode token && CstNodes.isIdentifierToken(token);
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
        text = placeLines(text, ctx);
        String original = getNodeText(ctx);
        // A statement's text can end with its line break (Python); keep it, so
        // the next line stays on its own line.
        String trailing = original.substring(original.stripTrailing().length());
        // return x; written as return 0 keeps its ';'.
        if (to instanceof MSLNode node && SIMPLE_STATEMENT_KINDS.contains(node.kind())
                && original.stripTrailing().endsWith(";") && !text.stripTrailing().endsWith(";")) {
            text = text.stripTrailing() + ";";
        }
        if (!trailing.isEmpty() && !text.endsWith(trailing)) text = text.stripTrailing() + trailing;
        int[] pos = getTokenPosition(ctx);
        return new OperatorEdit(pos[0], pos[1], original, text);
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
                    || !a.type().equals(b.type()) || !java.util.Objects.equals(a.name(), b.name())) {
                return false;
            }
        }
        return true;
    }

    /** Removes a list item (switch arm, annotation) and its trailing comma. */
    default OperatorEdit deleteItem(ParseTree ctx) {
        ParseTree parent = ctx.getParent();
        ParseTree last = ctx;
        if (parent != null) {
            for (int i = 0; i + 1 < parent.getChildCount(); i++) {
                if (parent.getChild(i) == ctx && parent.getChild(i + 1).getText().equals(",")) {
                    last = parent.getChild(i + 1);
                }
            }
        }
        ParseTree item = last == ctx ? ctx : CstNodes.span(List.of(ctx, last));
        int[] pos = getTokenPosition(ctx);
        return new OperatorEdit(pos[0], pos[1], getNodeText(item), "");
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
        statement = placeLines(statement, anchor);
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
            return renderPlaceholder(p, bindings, ctx);
        }
        MSLNode node = (MSLNode) tree;
        if (STATEMENT_KINDS.contains(node.kind())) {
            return renderStatementNode(node, bindings, ctx);
        }
        List<String> parts = new ArrayList<>();
        for (MSLTree child : node.children()) {
            // A slice's missing bound is written as nothing.
            String part = child instanceof MSLNode n && n.kind().equals(MSLNode.EMPTY) ? "" : render(child, bindings, ctx);
            if (part == null) return null;
            parts.add(part);
        }
        if (node.kind().equals(MSLNode.SLICE)) return renderSlice(parts.get(0), parts.get(1), parts.get(2), ctx);
        return renderExpression(node, parts, ctx);
    }

    /** Kinds written by {@link #renderStatementNode}, whose children may be statements. */
    Set<String> STATEMENT_KINDS = Set.of(MSLNode.ASSIGN, MSLNode.COMPOUND_ASSIGN, MSLNode.IF, MSLNode.WHILE,
            MSLNode.FOR, MSLNode.BLOCK, MSLNode.EMPTY, MSLNode.RETURN, MSLNode.BREAK, MSLNode.CONTINUE,
            MSLNode.DECLARE, MSLNode.FOR_EACH, MSLNode.DO_WHILE, MSLNode.CASE, MSLNode.DEFAULT_CASE, MSLNode.THROW,
            MSLNode.TRY, MSLNode.ASSERT, MSLNode.WITH, MSLNode.YIELD, MSLNode.LABELED, MSLNode.DELETE,
            MSLNode.ANNOTATION, MSLNode.WHEN, MSLNode.UNLESS, MSLNode.WHERE);

    /** Statement kinds written as one simple statement, which take the language's terminator. */
    Set<String> SIMPLE_STATEMENT_KINDS = Set.of(MSLNode.ASSIGN, MSLNode.COMPOUND_ASSIGN, MSLNode.RETURN,
            MSLNode.BREAK, MSLNode.CONTINUE, MSLNode.DECLARE, MSLNode.THROW, MSLNode.ASSERT, MSLNode.YIELD,
            MSLNode.DELETE);

    /** A placeholder's text: what it matched, converted within its family if needed, or an output value. */
    default String renderPlaceholder(MSLPlaceholder p, MSLBindings.Cursor bindings, ParseTree ctx) {
        java.util.Set<String> family = MSLPlaceholder.familyOf(p.type());
        if (family != null) {
            MSLBindings.Binding bound = p.isNamed() ? bindings.named(p.name()) : bindings.nextInFamily(family);
            if (bound == null) return null;
            if (bound.type().equals(p.type()) && p.condition() == null) return boundText(bound.node());
            return convert(bound.node(), bound.type(), p.type(), p.condition(), ctx);
        }
        if (p.type().equals("empty_string")) return "\"\"";
        if (p.type().equals("empty_block")) return emptyBlock(ctx);
        if (p.type().equals("empty_collection")) return emptyCollection(ctx);
        MSLBindings.Binding bound = p.isNamed() ? bindings.named(p.name()) : bindings.nextBinding(p.type());
        if (bound == null) return p.isNamed() ? null : renderValue(p.type(), ctx);
        return bound.nodes() != null ? joinArguments(bound.nodes(), ctx) : boundText(bound.node());
    }

    /** The text of a variadic {arguments} match: the arguments with the language's separator. */
    default String joinArguments(List<ParseTree> arguments, ParseTree ctx) {
        List<String> texts = new ArrayList<>();
        for (ParseTree argument : arguments) texts.add(boundText(argument));
        return String.join(", ", texts);
    }

    /** An empty collection of the kind ctx is: [], {}, new int[0], '(). */
    default String emptyCollection(ParseTree ctx) {
        return "[]";
    }

    /** A block with no statements: {} by default; pass in Python, (void) in Racket. */
    default String emptyBlock(ParseTree ctx) {
        return "{}";
    }

    /** The text a matched node contributes when written into a With: or Insert: tree. */
    default String boundText(ParseTree node) {
        return getNodeText(node);
    }

    /** Writes an expression node whose children are already written. */
    default String renderExpression(MSLNode node, List<String> parts, ParseTree ctx) {
        return switch (node.kind()) {
            case MSLNode.LITERAL -> renderLiteral(node.value(), ctx);
            case MSLNode.IDENTIFIER -> node.value();
            case MSLNode.BINARY -> renderBinary(node, parts.get(0), parts.get(1), ctx);
            case MSLNode.UNARY -> renderUnary(node.value(), parts.get(0), ctx);
            case MSLNode.INCREMENT -> renderIncrement(node.value(), parts.get(0), ctx);
            case MSLNode.CALL -> node.value().isEmpty()
                    ? renderCall(parts.get(0), parts.subList(1, parts.size()), ctx)
                    : renderCall(node.value(), parts, ctx);
            case MSLNode.CONDITIONAL -> renderConditional(parts.get(0), parts.get(1), parts.get(2), ctx);
            case MSLNode.COLLECTION -> renderCollection(parts, ctx);
            case MSLNode.MEMBERSHIP -> renderMembership(node.value(), parts.get(0), parts.get(1), ctx);
            case MSLNode.TUPLE -> renderTuple(parts, ctx);
            case MSLNode.AWAIT -> renderAwait(parts.get(0), ctx);
            case MSLNode.COMPREHENSION -> renderComprehension(parts, ctx);
            case MSLNode.SPREAD -> renderSpread(parts.get(0), ctx);
            case MSLNode.INTERPOLATION -> renderInterpolation(parts, ctx);
            case MSLNode.ARM -> renderArm(parts.get(0), parts.get(1), ctx);
            case MSLNode.OBJECT -> renderObject(parts, ctx);
            case MSLNode.ENTRY -> renderEntry(parts.get(0), parts.get(1), ctx);
            case MSLNode.LAMBDA -> renderLambda(parts.subList(0, parts.size() - 1), parts.get(parts.size() - 1), ctx);
            case MSLNode.PARAMETER -> renderParameter(parts.get(0), parts.size() > 1 ? parts.get(1) : null, ctx);
            case MSLNode.FIELD -> renderField(parts.get(0), parts.size() > 1 ? parts.get(1) : null, ctx);
            case MSLNode.TYPE_CHECK -> renderTypeCheck(parts.get(0), parts.get(1), ctx);
            case MSLNode.CAST -> renderCast(parts.get(0), parts.get(1), ctx);
            case MSLNode.SAFE_MEMBER -> renderSafeMember(parts.get(0), parts.get(1), ctx);
            case MSLNode.INITIALIZED_NEW -> renderInitializedNew(parts.get(0), parts.subList(1, parts.size()), ctx);
            case MSLNode.INDEX -> renderIndex(parts.get(0), parts.get(1), ctx);
            case MSLNode.MEMBER -> renderMember(parts.get(0), parts.get(1), ctx);
            case MSLNode.MEMBER_CALL -> renderMemberCall(parts.get(0), parts.get(1), parts.subList(2, parts.size()), ctx);
            default -> null;
        };
    }

    /** {@code a in b} / {@code a not in b}; null where the language has no membership operator. */
    default String renderMembership(String operator, String item, String container, ParseTree ctx) {
        return null;
    }

    /** {@code await a}. */
    default String renderAwait(String value, ParseTree ctx) {
        return "await " + value;
    }

    /** {@code [e for x in xs if c]}; null where the language has no comprehensions. */
    default String renderComprehension(List<String> parts, ParseTree ctx) {
        return null;
    }

    /** {@code (a, b)}. */
    default String renderTuple(List<String> elements, ParseTree ctx) {
        return "(" + String.join(", ", elements) + ")";
    }

    /** {@code ...a}; null where the language has no spread. */
    default String renderSpread(String value, ParseTree ctx) {
        return null;
    }

    /** The original interpolated string with its holes written anew; null when it can't be. */
    default String renderInterpolation(List<String> holes, ParseTree ctx) {
        return null;
    }

    /** A switch-expression arm; null where the language has none. */
    default String renderArm(String pattern, String value, ParseTree ctx) {
        return null;
    }

    /** {@code [a, b]}. */
    default String renderCollection(List<String> elements, ParseTree ctx) {
        return "[" + String.join(", ", elements) + "]";
    }

    /** {@code { k: v }}. */
    default String renderObject(List<String> entries, ParseTree ctx) {
        return entries.isEmpty() ? "{}" : "{ " + String.join(", ", entries) + " }";
    }

    /** {@code k: v}, one entry of an object literal. */
    default String renderEntry(String key, String value, ParseTree ctx) {
        return key + ": " + value;
    }

    /** {@code (a, b) => body}; null when the language has no lambdas it can write here. */
    default String renderLambda(List<String> parameters, String body, ParseTree ctx) {
        return "(" + String.join(", ", parameters) + ") => " + body;
    }

    /** A function parameter, with its default value or without one. */
    default String renderParameter(String name, String defaultValue, ParseTree ctx) {
        return defaultValue == null ? name : name + " = " + defaultValue;
    }

    /** A field declaration, with its initial value or without one. */
    default String renderField(String name, String value, ParseTree ctx) {
        return value == null ? name : name + " = " + value;
    }

    /** {@code a instanceof T}. */
    default String renderTypeCheck(String value, String type, ParseTree ctx) {
        return value + " instanceof " + type;
    }

    /** {@code (T) a}; null when the language has no casts. */
    default String renderCast(String type, String value, ParseTree ctx) {
        return null;
    }

    /** {@code a?.name}; null when the language has no null-safe access. */
    default String renderSafeMember(String object, String member, ParseTree ctx) {
        return null;
    }

    /** {@code new T { a, b }}; null when the language has no object initializers. */
    default String renderInitializedNew(String type, List<String> initializers, ParseTree ctx) {
        return null;
    }

    /** {@code a[i:j]}; null where the language has no slices. */
    default String renderSlice(String collection, String from, String to, ParseTree ctx) {
        return null;
    }

    /** {@code (c ? a : b)}. */
    default String renderConditional(String condition, String then, String otherwise, ParseTree ctx) {
        return "(" + condition + " ? " + then + " : " + otherwise + ")";
    }

    /** {@code a[i]}. */
    default String renderIndex(String collection, String index, ParseTree ctx) {
        return collection + "[" + index + "]";
    }

    /** {@code obj.name}. */
    default String renderMember(String object, String member, ParseTree ctx) {
        return object + "." + member;
    }

    /** {@code obj.name(args)}. */
    default String renderMemberCall(String object, String method, List<String> arguments, ParseTree ctx) {
        return object + "." + method + "(" + String.join(", ", arguments) + ")";
    }

    /** {@code !x}, {@code -x}, {@code ~x}. */
    default String renderUnary(String operator, String operand, ParseTree ctx) {
        return operator + operand;
    }

    /** {@code f(a, b)}. */
    default String renderCall(String function, List<String> arguments, ParseTree ctx) {
        return function + "(" + String.join(", ", arguments) + ")";
    }

    /** Writes a statement node, C-like by default; multi-line text is indented relative to its first line. */
    default String renderStatementNode(MSLNode node, MSLBindings.Cursor bindings, ParseTree ctx) {
        switch (node.kind()) {
            case MSLNode.ASSIGN, MSLNode.COMPOUND_ASSIGN -> {
                String target = render(node.child(0), bindings, ctx);
                String value = render(node.child(1), bindings, ctx);
                return target == null || value == null ? null : target + " " + node.value() + " " + value;
            }
            case MSLNode.IF -> {
                String condition = render(node.child(0), bindings, ctx);
                String then = renderStatement(node.child(1), bindings, ctx);
                if (condition == null || then == null) return null;
                if (node.children().size() < 3) return "if (" + condition + ") " + then;
                String otherwise = renderStatement(node.child(2), bindings, ctx);
                return otherwise == null ? null : "if (" + condition + ") " + then + " else " + otherwise;
            }
            case MSLNode.WHILE -> {
                String condition = render(node.child(0), bindings, ctx);
                String body = renderStatement(node.child(1), bindings, ctx);
                return condition == null || body == null ? null : "while (" + condition + ") " + body;
            }
            case MSLNode.FOR -> {
                List<String> parts = new ArrayList<>();
                for (int i = 0; i < 3; i++) {
                    if (node.child(i) instanceof MSLNode part && part.kind().equals(MSLNode.EMPTY)) {
                        parts.add("");
                        continue;
                    }
                    String text = render(node.child(i), bindings, ctx);
                    if (text == null) return null;
                    parts.add(text);
                }
                String body = renderStatement(node.child(3), bindings, ctx);
                return body == null ? null : "for (" + String.join("; ", parts) + ") " + body;
            }
            case MSLNode.BLOCK -> {
                List<String> statements = new ArrayList<>();
                for (MSLTree child : node.children()) {
                    String text = renderStatement(child, bindings, ctx);
                    if (text == null) return null;
                    statements.add(text);
                }
                return statements.isEmpty() ? "{}" : "{ " + String.join(" ", statements) + " }";
            }
            // ";" in a With: means the statement is removed.
            case MSLNode.EMPTY -> {
                return getEmptyStatement(ctx);
            }
            case MSLNode.RETURN -> {
                if (node.children().isEmpty()) return "return";
                String value = render(node.child(0), bindings, ctx);
                return value == null ? null : "return " + value;
            }
            case MSLNode.BREAK, MSLNode.CONTINUE -> {
                if (node.children().isEmpty()) return node.kind();
                String label = render(node.child(0), bindings, ctx);
                return label == null ? null : node.kind() + " " + label;
            }
            case MSLNode.THROW -> {
                String value = render(node.child(0), bindings, ctx);
                return value == null ? null : renderThrow(value, ctx);
            }
            case MSLNode.DECLARE -> {
                String name = render(node.child(0), bindings, ctx);
                String value = render(node.child(1), bindings, ctx);
                return name == null || value == null ? null : renderDeclaration(name, value, ctx);
            }
            case MSLNode.FOR_EACH -> {
                String name = render(node.child(0), bindings, ctx);
                String collection = render(node.child(1), bindings, ctx);
                String body = renderStatement(node.child(2), bindings, ctx);
                return name == null || collection == null || body == null ? null
                        : renderForEach(name, collection, body, ctx);
            }
            case MSLNode.DO_WHILE -> {
                String body = renderStatement(node.child(0), bindings, ctx);
                String condition = render(node.child(1), bindings, ctx);
                return body == null || condition == null ? null : "do " + body + " while (" + condition + ");";
            }
            case MSLNode.CASE -> {
                String value = render(node.child(0), bindings, ctx);
                String body = renderStatement(node.child(1), bindings, ctx);
                return value == null || body == null ? null : "case " + value + ": " + body;
            }
            case MSLNode.DEFAULT_CASE -> {
                String body = renderStatement(node.child(0), bindings, ctx);
                return body == null ? null : "default: " + body;
            }
            case MSLNode.ASSERT, MSLNode.DELETE, MSLNode.ANNOTATION -> {
                String value = render(node.child(0), bindings, ctx);
                return value == null ? null : renderKeywordStatement(node.kind(), value, ctx);
            }
            case MSLNode.YIELD -> {
                String value = node.children().isEmpty() ? null : render(node.child(0), bindings, ctx);
                return node.children().isEmpty() || value != null ? renderYield(value, ctx) : null;
            }
            case MSLNode.LABELED -> {
                String label = render(node.child(0), bindings, ctx);
                String statement = renderStatement(node.child(1), bindings, ctx);
                return label == null || statement == null ? null : label + ": " + statement;
            }
            default -> {
                // try and with can't be written in general: a catch needs its
                // exception type, a resource its declaration.
                return null;
            }
        }
    }

    /** assert, delete or an annotation; null where the language has none. */
    default String renderKeywordStatement(String kind, String value, ParseTree ctx) {
        return null;
    }

    /** {@code yield v}, or a bare {@code yield} when {@code value} is null. */
    default String renderYield(String value, ParseTree ctx) {
        return value == null ? "yield" : "yield " + value;
    }

    /** {@code throw x}. */
    default String renderThrow(String value, ParseTree ctx) {
        return "throw " + value;
    }

    /** {@code var x = v}, or the language's declaration keyword. */
    default String renderDeclaration(String name, String value, ParseTree ctx) {
        return "var " + name + " = " + value;
    }

    /** A for-each loop over a collection. */
    default String renderForEach(String name, String collection, String body, ParseTree ctx) {
        return null;
    }

    /** Indents later lines of written text to ctx's column (Python); unchanged by default. */
    default String placeLines(String text, ParseTree ctx) {
        return text;
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
        return MSLPlaceholder.OUTPUT_TYPES.contains(type);
    }

    /**
     * The value of an output placeholder such as {empty_value} or
     * {default_value} for the matched node ctx, or null when there is none.
     */
    default String renderValue(String placeholderType, ParseTree ctx) {
        return null;
    }

    /** Writes a tree as a complete statement, adding ';' to simple statements by default. */
    default String renderStatement(MSLTree tree, MSLBindings.Cursor bindings, ParseTree ctx) {
        String text = render(tree, bindings, ctx);
        if (text == null) return null;
        if (tree instanceof MSLNode node && STATEMENT_KINDS.contains(node.kind())
                && !SIMPLE_STATEMENT_KINDS.contains(node.kind())) {
            return text;
        }
        String trimmed = text.stripTrailing();
        return trimmed.endsWith(";") || trimmed.endsWith("}") ? text : text + ";";
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
