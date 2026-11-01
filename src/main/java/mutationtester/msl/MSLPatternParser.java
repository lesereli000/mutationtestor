package mutationtester.msl;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses one MSL pattern, the text after Replace:, With:, Delete:, Insert:,
 * Before:, After: or Except:, into an {@link MSLTree}.
 *
 * Patterns are written in a language-neutral, C-like syntax:
 * <pre>
 *   {quantity} + {quantity}            binary operators, with the usual precedence
 *   !{quantity}   -{quantity}          unary operators
 *   {quantity}++  {quantity}--         increment and decrement (++{quantity} is the same)
 *   {quantity} is null                 plain-English checks ("is null", "is not null")
 *   f({quantity}, 0)                   calls, literals (numbers, strings, true, false, null)
 *   if ({match}) {quantity} = 0        statements: if, while, for, assignment, { blocks }
 *   {match: {quantity} &lt;= 0}           in Except:, the mutated node, optionally constrained
 * </pre>
 * Parentheses only group. The tokenizer reads one character at a time and
 * tries the longest operator first, so >>> is never read as >> then >.
 */
public final class MSLPatternParser {

    /** The operators a pattern may use, longest first. */
    private static final String[] OPERATORS = {
        ">>>", "++", "--", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "//", "**",
        "+", "-", "*", "/", "%", "&", "|", "^", "<", ">", "!", "~", "=",
    };

    /** Binary operators from lowest to highest precedence. */
    private static final String[][] PRECEDENCE = {
        {"||"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!="}, {"<", "<=", ">", ">="},
        {"<<", ">>", ">>>"}, {"+", "-"}, {"*", "/", "//", "%"},
    };

    private enum Type { PLACEHOLDER, WORD, NUMBER, STRING, OP, PUNCT, END }

    private record Token(Type type, String text, String constraint) {
        boolean is(Type t, String s) {
            return type == t && text.equals(s);
        }
    }

    private final String source;
    private final List<Token> tokens;
    private int pos;

    private MSLPatternParser(String source) {
        this.source = source;
        this.tokens = tokenize(source);
    }

    /** Parses a pattern; throws IllegalArgumentException with a readable message if it isn't valid. */
    public static MSLTree parse(String pattern) {
        MSLPatternParser parser = new MSLPatternParser(pattern.strip());
        if (parser.peek().type == Type.END) {
            throw parser.error("empty pattern");
        }
        MSLTree tree = parser.statement();
        if (parser.peek().is(Type.PUNCT, ";")) parser.pos++;
        if (parser.peek().type != Type.END) {
            throw parser.error("unexpected \"" + parser.peek().text + "\"");
        }
        return tree;
    }

    // ── Statements ───────────────────────────────────────────────────────

    private MSLTree statement() {
        Token t = peek();
        if (t.is(Type.PUNCT, ";")) {
            // The empty statement: "With: ;" removes what Replace: matched.
            pos++;
            return new MSLNode(MSLNode.EMPTY, "");
        }
        if (t.is(Type.WORD, "if") || t.is(Type.WORD, "while")) {
            pos++;
            expect(Type.PUNCT, "(");
            MSLTree condition = expression();
            expect(Type.PUNCT, ")");
            return new MSLNode(t.text.equals("if") ? MSLNode.IF : MSLNode.WHILE, "", condition, statement());
        }
        if (t.is(Type.WORD, "for")) {
            pos++;
            expect(Type.PUNCT, "(");
            MSLTree init = optionalExpression(";");
            expect(Type.PUNCT, ";");
            MSLTree condition = optionalExpression(";");
            expect(Type.PUNCT, ";");
            MSLTree update = optionalExpression(")");
            expect(Type.PUNCT, ")");
            return new MSLNode(MSLNode.FOR, "", init, condition, update, statement());
        }
        if (t.is(Type.PUNCT, "{")) {
            pos++;
            List<MSLTree> statements = new ArrayList<>();
            while (!peek().is(Type.PUNCT, "}")) {
                if (peek().type == Type.END) throw error("missing \"}\"");
                statements.add(statement());
                if (peek().is(Type.PUNCT, ";")) pos++;
            }
            pos++;
            return new MSLNode(MSLNode.BLOCK, "", statements);
        }
        MSLTree expr = expression();
        if (peek().is(Type.OP, "=")) {
            pos++;
            expr = new MSLNode(MSLNode.ASSIGN, "=", expr, expression());
        }
        return expr;
    }

    private MSLTree optionalExpression(String terminator) {
        if (peek().is(Type.PUNCT, terminator)) {
            return new MSLNode(MSLNode.EMPTY, "");
        }
        MSLTree expr = expression();
        if (peek().is(Type.OP, "=")) {
            pos++;
            expr = new MSLNode(MSLNode.ASSIGN, "=", expr, expression());
        }
        return expr;
    }

    // ── Expressions ──────────────────────────────────────────────────────

    private MSLTree expression() {
        return binary(0);
    }

    private MSLTree binary(int level) {
        if (level == PRECEDENCE.length) {
            return power();
        }
        MSLTree left = binary(level + 1);
        while (true) {
            rejectWordOperator();
            String op = operatorAt(level);
            if (op == null) return left;
            pos++;
            left = new MSLNode(MSLNode.BINARY, op, left, binary(level + 1));
        }
    }

    private MSLTree power() {
        MSLTree base = unary();
        rejectWordOperator();
        if (peek().is(Type.OP, "**")) {
            pos++;
            return new MSLNode(MSLNode.BINARY, "**", base, power());
        }
        return base;
    }

    private String operatorAt(int level) {
        Token t = peek();
        if (t.type != Type.OP) return null;
        for (String op : PRECEDENCE[level]) {
            if (t.text.equals(op)) return op;
        }
        return null;
    }

    private MSLTree unary() {
        Token t = peek();
        if (t.type == Type.OP && (t.text.equals("!") || t.text.equals("-") || t.text.equals("~"))) {
            pos++;
            return new MSLNode(MSLNode.UNARY, t.text, unary());
        }
        if (t.type == Type.OP && (t.text.equals("++") || t.text.equals("--"))) {
            pos++;
            return new MSLNode(MSLNode.INCREMENT, t.text, primary());
        }
        return postfix(primary());
    }

    /** "x++" / "x--", and "x is null" / "x is not null". */
    private MSLTree postfix(MSLTree subject) {
        Token t = peek();
        if (t.type == Type.OP && (t.text.equals("++") || t.text.equals("--"))) {
            pos++;
            return new MSLNode(MSLNode.INCREMENT, t.text, subject);
        }
        if (!peek().is(Type.WORD, "is")) return subject;
        pos++;
        boolean negated = peek().is(Type.WORD, "not");
        if (negated) pos++;
        if (!peek().is(Type.WORD, "null")) {
            throw error("expected \"null\" after \"is" + (negated ? " not" : "") + "\"");
        }
        pos++;
        return new MSLNode(MSLNode.PREDICATE, negated ? "is not null" : "is null", subject);
    }

    private MSLTree primary() {
        Token t = peek();
        switch (t.type) {
            case PLACEHOLDER -> {
                pos++;
                MSLTree constraint = t.constraint == null ? null : parse(t.constraint);
                if (constraint != null && !t.text.equals(MSLPlaceholder.MATCH)) {
                    throw error("only {match} may have a pattern after ':'");
                }
                return new MSLPlaceholder(t.text, constraint);
            }
            case NUMBER, STRING -> {
                pos++;
                return new MSLNode(MSLNode.LITERAL, t.text);
            }
            case WORD -> {
                pos++;
                if (t.text.equals("true") || t.text.equals("false") || t.text.equals("null")) {
                    return new MSLNode(MSLNode.LITERAL, t.text);
                }
                if (!peek().is(Type.PUNCT, "(")) {
                    return new MSLNode(MSLNode.IDENTIFIER, t.text);
                }
                pos++;
                List<MSLTree> args = new ArrayList<>();
                if (!peek().is(Type.PUNCT, ")")) {
                    args.add(expression());
                    while (peek().is(Type.PUNCT, ",")) {
                        pos++;
                        args.add(expression());
                    }
                }
                expect(Type.PUNCT, ")");
                return new MSLNode(MSLNode.CALL, t.text, args);
            }
            case PUNCT -> {
                if (t.text.equals("(")) {
                    pos++;
                    MSLTree inner = expression();
                    expect(Type.PUNCT, ")");
                    return inner;
                }
                throw error("unexpected \"" + t.text + "\"");
            }
            case END -> throw error("pattern ends too early");
            default -> throw error("unexpected \"" + t.text + "\"");
        }
    }

    /** A word between two operands is a misspelled operator, e.g. LE for <=. */
    private void rejectWordOperator() {
        Token t = peek();
        if (t.type != Type.WORD || t.text.equals("is")) return;
        String canonical = switch (t.text.toUpperCase()) {
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
                ? " Supported operators: " + String.join(" ", OPERATORS)
                : " Use the canonical operator \"" + canonical + "\".";
        throw new IllegalArgumentException("Unsupported MSL operator \"" + t.text + "\"." + guidance);
    }

    // ── Tokens ───────────────────────────────────────────────────────────

    private Token peek() {
        return tokens.get(pos);
    }

    private void expect(Type type, String text) {
        if (!peek().is(type, text)) {
            throw error("expected \"" + text + "\"" + (peek().type == Type.END ? "" : " before \"" + peek().text + "\""));
        }
        pos++;
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("Invalid MSL pattern \"" + source + "\": " + message);
    }

    private static List<Token> tokenize(String s) {
        List<Token> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '{' && isPlaceholderStart(s, i)) {
                i = placeholder(s, i, out);
            } else if (isWordStart(c)) {
                int start = i;
                while (i < s.length() && (isWordPart(s.charAt(i)) || isDottedName(s, i))) i++;
                out.add(new Token(Type.WORD, s.substring(start, i), null));
            } else if (Character.isDigit(c)) {
                int start = i;
                while (i < s.length() && (Character.isLetterOrDigit(s.charAt(i)) || s.charAt(i) == '.')) i++;
                out.add(new Token(Type.NUMBER, s.substring(start, i), null));
            } else if (c == '"' || c == '\'') {
                int start = i++;
                while (i < s.length() && s.charAt(i) != c) {
                    if (s.charAt(i) == '\\') i++;
                    i++;
                }
                if (i >= s.length()) {
                    throw new IllegalArgumentException("Invalid MSL pattern \"" + s + "\": unterminated string");
                }
                i++;
                out.add(new Token(Type.STRING, s.substring(start, i), null));
            } else if ("(){},;".indexOf(c) >= 0) {
                out.add(new Token(Type.PUNCT, String.valueOf(c), null));
                i++;
            } else {
                String op = null;
                for (String candidate : OPERATORS) {
                    if (s.startsWith(candidate, i)) {
                        op = candidate;
                        break;
                    }
                }
                if (op == null) {
                    throw new IllegalArgumentException("Invalid MSL pattern \"" + s + "\": unexpected character '" + c + "'");
                }
                out.add(new Token(Type.OP, op, null));
                i += op.length();
            }
        }
        out.add(new Token(Type.END, "", null));
        return out;
    }

    /** {type} or {match: pattern}; returns the index after the closing brace. */
    private static int placeholder(String s, int open, List<Token> out) {
        int i = open + 1;
        while (i < s.length() && isWordPart(s.charAt(i))) i++;
        String type = s.substring(open + 1, i);
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        if (i < s.length() && s.charAt(i) == '}') {
            out.add(new Token(Type.PLACEHOLDER, type, null));
            return i + 1;
        }
        if (i >= s.length() || s.charAt(i) != ':') {
            throw new IllegalArgumentException("Invalid MSL pattern \"" + s + "\": placeholder {" + type + " is not closed");
        }
        int depth = 1;
        int start = ++i;
        while (i < s.length() && depth > 0) {
            char c = s.charAt(i);
            if (c == '"' || c == '\'') {
                i++;
                while (i < s.length() && s.charAt(i) != c) i++;
            } else if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            i++;
        }
        if (depth > 0) {
            throw new IllegalArgumentException("Invalid MSL pattern \"" + s + "\": placeholder {" + type + ": is not closed");
        }
        out.add(new Token(Type.PLACEHOLDER, type, s.substring(start, i - 1).strip()));
        return i;
    }

    /** "{name}" or "{name:"; any other '{' opens a block. */
    private static boolean isPlaceholderStart(String s, int open) {
        int i = open + 1;
        if (i >= s.length() || !isWordStart(s.charAt(i))) return false;
        while (i < s.length() && isWordPart(s.charAt(i))) i++;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i < s.length() && (s.charAt(i) == '}' || s.charAt(i) == ':');
    }

    private static boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /** A '.' inside a dotted name such as Math.floor. */
    private static boolean isDottedName(String s, int i) {
        return s.charAt(i) == '.' && i + 1 < s.length() && isWordStart(s.charAt(i + 1));
    }
}
