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
        ">>>=", ">>>", "<<=", ">>=", "**=", "//=", "=>", "?.", "??", "===", "!==", "++", "--", "<<", ">>", "<=", ">=", "==", "!=", "&&", "||", "//", "**",
        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=",
        "+", "-", "*", "/", "%", "&", "|", "^", "<", ">", "!", "~", "=", "?", ":",
    };

    /** Binary operators from lowest to highest precedence. */
    private static final String[][] PRECEDENCE = {
        {"??"}, {"||"}, {"&&"}, {"|"}, {"^"}, {"&"}, {"==", "!=", "===", "!=="}, {"<", "<=", ">", ">="},
        {"<<", ">>", ">>>"}, {"+", "-"}, {"*", "/", "//", "%"},
    };

    private enum Type { PLACEHOLDER, WORD, NUMBER, STRING, TEMPLATE, OP, PUNCT, END }

    private record Token(Type type, String text, String constraint, String name) {
        Token(Type type, String text, String constraint) {
            this(type, text, constraint, null);
        }

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
            MSLTree then = statement();
            if (peek().is(Type.WORD, "else")) {
                // if ... else, or a loop's else (Python's while ... else)
                pos++;
                return new MSLNode(t.text.equals("if") ? MSLNode.IF : MSLNode.WHILE, "", condition, then, statement());
            }
            return new MSLNode(t.text.equals("if") ? MSLNode.IF : MSLNode.WHILE, "", condition, then);
        }
        if (t.is(Type.PUNCT, "@")) {
            pos++;
            return new MSLNode(MSLNode.ANNOTATION, "", primary());
        }
        if ((t.is(Type.WORD, "when") || t.is(Type.WORD, "unless")) && tokens.get(pos + 1).is(Type.PUNCT, "(")) {
            pos += 2;
            MSLTree condition = expression();
            expect(Type.PUNCT, ")");
            return new MSLNode(t.text.equals("when") ? MSLNode.WHEN : MSLNode.UNLESS, "", condition, statement());
        }
        if (t.is(Type.WORD, "where")) {
            pos++;
            return new MSLNode(MSLNode.WHERE, "", expression());
        }
        if (t.is(Type.WORD, "assert") || t.is(Type.WORD, "delete")) {
            pos++;
            return new MSLNode(t.text.equals("assert") ? MSLNode.ASSERT : MSLNode.DELETE, "", expression());
        }
        if (t.is(Type.WORD, "yield")) {
            pos++;
            if (endsStatement(peek()) || peek().is(Type.WORD, "break")) {
                if (peek().is(Type.WORD, "break")) pos++;
                return new MSLNode(MSLNode.YIELD, "");
            }
            return new MSLNode(MSLNode.YIELD, "", expression());
        }
        if (t.is(Type.WORD, "with") && tokens.get(pos + 1).is(Type.PUNCT, "(")) {
            pos += 2;
            MSLTree resource = expression();
            expect(Type.PUNCT, ")");
            return new MSLNode(MSLNode.WITH, "", resource, statement());
        }
        if ((t.type == Type.PLACEHOLDER || t.type == Type.WORD) && tokens.get(pos + 1).is(Type.OP, ":")
                && !t.is(Type.WORD, "case") && !t.is(Type.WORD, "default")) {
            MSLTree label = primary();
            pos++;
            return new MSLNode(MSLNode.LABELED, "", label, statement());
        }
        if (t.is(Type.PUNCT, "{") && isObjectAhead()) {
            return expression();
        }
        if (t.is(Type.WORD, "function")) {
            pos++;
            List<MSLTree> parts = new ArrayList<>(List.of(primary()));
            expect(Type.PUNCT, "(");
            parts.addAll(parameterList());
            parts.add(statement());
            return new MSLNode(MSLNode.FUNCTION, "", parts);
        }
        if (t.is(Type.WORD, "parameter") || t.is(Type.WORD, "field")) {
            pos++;
            String kind = t.text.equals("parameter") ? MSLNode.PARAMETER : MSLNode.FIELD;
            MSLTree name = primary();
            if (!peek().is(Type.OP, "=")) return new MSLNode(kind, "", name);
            pos++;
            return new MSLNode(kind, "", name, expression());
        }
        if (t.is(Type.WORD, "return")) {
            pos++;
            if (endsStatement(peek())) return new MSLNode(MSLNode.RETURN, "");
            return new MSLNode(MSLNode.RETURN, "", expression());
        }
        if (t.is(Type.WORD, "break") || t.is(Type.WORD, "continue")) {
            pos++;
            String kind = t.text.equals("break") ? MSLNode.BREAK : MSLNode.CONTINUE;
            if (endsStatement(peek())) return new MSLNode(kind, "");
            return new MSLNode(kind, "", primary());
        }
        if (t.is(Type.WORD, "var")) {
            pos++;
            MSLTree name = primary();
            expect(Type.OP, "=");
            return new MSLNode(MSLNode.DECLARE, "", name, expression());
        }
        if (t.is(Type.WORD, "do")) {
            pos++;
            MSLTree body = statement();
            if (!peek().is(Type.WORD, "while")) throw error("expected \"while\" after a do body");
            pos++;
            expect(Type.PUNCT, "(");
            MSLTree condition = expression();
            expect(Type.PUNCT, ")");
            return new MSLNode(MSLNode.DO_WHILE, "", body, condition);
        }
        if (t.is(Type.WORD, "case")) {
            pos++;
            MSLTree value = expression();
            if (peek().is(Type.OP, "=>")) {
                pos++;
                return new MSLNode(MSLNode.ARM, "", value, expression());
            }
            expect(Type.OP, ":");
            return new MSLNode(MSLNode.CASE, "", value, statement());
        }
        if (t.is(Type.WORD, "default") && tokens.get(pos + 1).is(Type.OP, ":")) {
            pos += 2;
            return new MSLNode(MSLNode.DEFAULT_CASE, "", statement());
        }
        if (t.is(Type.WORD, "throw")) {
            pos++;
            return new MSLNode(MSLNode.THROW, "", expression());
        }
        if (t.is(Type.WORD, "try")) {
            pos++;
            MSLTree body = statement();
            MSLTree handler = new MSLNode(MSLNode.EMPTY, "");
            MSLTree last = new MSLNode(MSLNode.EMPTY, "");
            if (peek().is(Type.WORD, "catch")) {
                pos++;
                handler = statement();
            }
            if (peek().is(Type.WORD, "finally")) {
                pos++;
                last = statement();
            }
            return new MSLNode(MSLNode.TRY, "", body, handler, last);
        }
        if (t.is(Type.WORD, "for") && pos + 3 < tokens.size() && tokens.get(pos + 1).is(Type.PUNCT, "(")
                && tokens.get(pos + 3).is(Type.WORD, "in")) {
            pos += 2;
            MSLTree name = primary();
            pos++;
            MSLTree collection = expression();
            expect(Type.PUNCT, ")");
            MSLTree body = statement();
            if (peek().is(Type.WORD, "else")) {
                pos++;
                return new MSLNode(MSLNode.FOR_EACH, "", name, collection, body, statement());
            }
            return new MSLNode(MSLNode.FOR_EACH, "", name, collection, body);
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
        return assignment(expression());
    }

    /** True when the "{" at pos opens an object literal ({ k: v } or { {arguments} }), not a block. */
    private boolean isObjectAhead() {
        if (tokens.get(pos + 1).type == Type.PLACEHOLDER && tokens.get(pos + 1).text.equals(MSLPlaceholder.ARGUMENTS)
                && tokens.get(pos + 2).is(Type.PUNCT, "}")) {
            return true;
        }
        int depth = 0;
        for (int i = pos; i < tokens.size(); i++) {
            Token t = tokens.get(i);
            if (t.is(Type.PUNCT, "{") || t.is(Type.PUNCT, "(") || t.is(Type.PUNCT, "[")) depth++;
            else if (t.is(Type.PUNCT, "}") || t.is(Type.PUNCT, ")") || t.is(Type.PUNCT, "]")) {
                if (--depth == 0) return false;
            } else if (depth == 1 && (t.is(Type.OP, ":") || t.is(Type.PUNCT, ";"))) {
                return t.is(Type.OP, ":");
            }
        }
        return false;
    }

    /** Function parameters after the "(": names, defaults (x = 1) and {arguments}, up to ")". */
    private List<MSLTree> parameterList() {
        List<MSLTree> params = new ArrayList<>();
        while (!peek().is(Type.PUNCT, ")")) {
            MSLTree name = primary();
            if (peek().is(Type.OP, "=")) {
                pos++;
                params.add(new MSLNode(MSLNode.PARAMETER, "", name, expression()));
            } else {
                params.add(name);
            }
            if (!peek().is(Type.PUNCT, ",")) break;
            pos++;
        }
        expect(Type.PUNCT, ")");
        return params;
    }

    /** True at the end of a statement: ";", "}", "else", or the end of the pattern. */
    private static boolean endsStatement(Token t) {
        return t.type == Type.END || t.is(Type.PUNCT, ";") || t.is(Type.PUNCT, "}") || t.is(Type.WORD, "else");
    }

    private MSLTree optionalExpression(String terminator) {
        if (peek().is(Type.PUNCT, terminator)) {
            return new MSLNode(MSLNode.EMPTY, "");
        }
        return assignment(expression());
    }

    // ── Expressions ──────────────────────────────────────────────────────

    /** Compound assignment operators: {@code a += b}. */
    private static final java.util.Set<String> COMPOUND = java.util.Set.of(
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", ">>>=", "**=", "//=");

    private MSLTree expression() {
        MSLTree condition = binary(0);
        if (!peek().is(Type.OP, "?")) return condition;
        pos++;
        MSLTree then = expression();
        expect(Type.OP, ":");
        return new MSLNode(MSLNode.CONDITIONAL, "", condition, then, expression());
    }

    /** An assignment or compound assignment after {@code target}, or {@code target} itself. */
    private MSLTree assignment(MSLTree target) {
        Token t = peek();
        if (t.is(Type.OP, "=")) {
            pos++;
            return new MSLNode(MSLNode.ASSIGN, "=", target, expression());
        }
        if (t.type == Type.OP && COMPOUND.contains(t.text)) {
            pos++;
            return new MSLNode(MSLNode.COMPOUND_ASSIGN, t.text, target, expression());
        }
        return target;
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
        if (t.is(Type.OP, "...")) {
            pos++;
            return new MSLNode(MSLNode.SPREAD, "", unary());
        }
        if (t.is(Type.WORD, "await")) {
            pos++;
            return new MSLNode(MSLNode.AWAIT, "", unary());
        }
        return postfix(primary());
    }

    /** Postfix forms: a[i], a[i:j], a.b, a.b(...), a?.b, {name}(...), x++, is null, in, is a. */
    private MSLTree postfix(MSLTree subject) {
        while (true) {
            Token next = peek();
            if (next.is(Type.PUNCT, "[")) {
                pos++;
                MSLTree from = peek().is(Type.OP, ":") ? new MSLNode(MSLNode.EMPTY, "") : expression();
                if (peek().is(Type.OP, ":")) {
                    pos++;
                    MSLTree to = peek().is(Type.PUNCT, "]") ? new MSLNode(MSLNode.EMPTY, "") : expression();
                    expect(Type.PUNCT, "]");
                    subject = new MSLNode(MSLNode.SLICE, "", subject, from, to);
                    continue;
                }
                expect(Type.PUNCT, "]");
                subject = new MSLNode(MSLNode.INDEX, "", subject, from);
            } else if (next.is(Type.PUNCT, ".")) {
                pos++;
                Token name = peek();
                MSLTree member;
                if (name.type == Type.WORD) {
                    pos++;
                    member = new MSLNode(MSLNode.IDENTIFIER, name.text);
                } else if (name.type == Type.PLACEHOLDER) {
                    member = primary();
                } else {
                    throw error("expected a member name after \".\"");
                }
                if (peek().is(Type.PUNCT, "(")) {
                    pos++;
                    List<MSLTree> parts = new ArrayList<>(List.of(subject, member));
                    parts.addAll(arguments());
                    subject = new MSLNode(MSLNode.MEMBER_CALL, "", parts);
                } else {
                    subject = new MSLNode(MSLNode.MEMBER, "", subject, member);
                }
            } else if (next.is(Type.OP, "?.")) {
                pos++;
                Token name = peek();
                MSLTree member;
                if (name.type == Type.WORD) {
                    pos++;
                    member = new MSLNode(MSLNode.IDENTIFIER, name.text);
                } else {
                    member = primary();
                }
                subject = new MSLNode(MSLNode.SAFE_MEMBER, "", subject, member);
            } else if (next.is(Type.PUNCT, "(") && subject instanceof MSLPlaceholder) {
                pos++;
                List<MSLTree> parts = new ArrayList<>(List.of(subject));
                parts.addAll(arguments());
                subject = new MSLNode(MSLNode.CALL, "", parts);
            } else {
                break;
            }
        }
        Token t = peek();
        if (t.type == Type.OP && (t.text.equals("++") || t.text.equals("--"))) {
            pos++;
            return new MSLNode(MSLNode.INCREMENT, t.text, subject);
        }
        if (peek().is(Type.WORD, "in")) {
            pos++;
            return new MSLNode(MSLNode.MEMBERSHIP, "in", subject, unary());
        }
        if (peek().is(Type.WORD, "not") && tokens.get(pos + 1).is(Type.WORD, "in")) {
            pos += 2;
            return new MSLNode(MSLNode.MEMBERSHIP, "not in", subject, unary());
        }
        if (!peek().is(Type.WORD, "is")) return subject;
        pos++;
        if (peek().is(Type.WORD, "a") || peek().is(Type.WORD, "an")) {
            pos++;
            return new MSLNode(MSLNode.TYPE_CHECK, "", subject, primary());
        }
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
                if (t.name != null && t.text.equals(MSLPlaceholder.MATCH)) {
                    throw error("{match} can't be named");
                }
                if (t.constraint == null) return new MSLPlaceholder(t.text, t.name, null, null);
                // A single constraint word ({integer: int64}), or a pattern.
                if (MSLPlaceholder.CONDITIONS.contains(t.constraint)) {
                    if (t.text.equals(MSLPlaceholder.MATCH)) {
                        throw error("{match} takes a pattern, not \"" + t.constraint + "\"");
                    }
                    return new MSLPlaceholder(t.text, t.name, null, t.constraint);
                }
                if (isWord(t.constraint) && !t.text.equals(MSLPlaceholder.MATCH)) {
                    throw error("unknown constraint \"" + t.constraint + "\"; constraints are a pattern or one of "
                            + String.join(", ", new java.util.TreeSet<>(MSLPlaceholder.CONDITIONS)));
                }
                return new MSLPlaceholder(t.text, t.name, parse(t.constraint), null);
            }
            case TEMPLATE -> {
                pos++;
                List<MSLTree> holes = new ArrayList<>();
                if (!t.constraint.isEmpty()) {
                    int start = 0;
                    for (int i = 0; i <= t.constraint.length(); i++) {
                        if (i == t.constraint.length() || t.constraint.charAt(i) == '\u0000') {
                            holes.add(parse(t.constraint.substring(start, i)));
                            start = i + 1;
                        }
                    }
                }
                return new MSLNode(MSLNode.INTERPOLATION, "", holes);
            }
            case NUMBER, STRING -> {
                pos++;
                return new MSLNode(MSLNode.LITERAL, t.text);
            }
            case WORD -> {
                pos++;
                if (t.text.equals("new") && peek().type != Type.END && tokens.get(pos + 1).is(Type.PUNCT, "{")) {
                    List<MSLTree> parts = new ArrayList<>(List.of(primary()));
                    pos++;
                    while (!peek().is(Type.PUNCT, "}")) {
                        parts.add(assignment(expression()));
                        if (!peek().is(Type.PUNCT, ",")) break;
                        pos++;
                    }
                    expect(Type.PUNCT, "}");
                    return new MSLNode(MSLNode.INITIALIZED_NEW, "", parts);
                }
                if (t.text.equals("true") || t.text.equals("false") || t.text.equals("null")) {
                    return new MSLNode(MSLNode.LITERAL, t.text);
                }
                if (!peek().is(Type.PUNCT, "(")) {
                    return new MSLNode(MSLNode.IDENTIFIER, t.text);
                }
                pos++;
                return postfix(new MSLNode(MSLNode.CALL, t.text, arguments()));
            }
            case PUNCT -> {
                if (t.text.equals("(")) {
                    pos++;
                    // (T) a is a cast, (a, b) => body a lambda, (a) a group.
                    if (peek().type == Type.PLACEHOLDER && peek().text.equals("type")
                            && tokens.get(pos + 1).is(Type.PUNCT, ")")) {
                        MSLTree type = primary();
                        pos++;
                        return new MSLNode(MSLNode.CAST, "", type, unary());
                    }
                    List<MSLTree> items = new ArrayList<>();
                    while (!peek().is(Type.PUNCT, ")")) {
                        items.add(expression());
                        if (!peek().is(Type.PUNCT, ",")) break;
                        pos++;
                    }
                    expect(Type.PUNCT, ")");
                    if (peek().is(Type.OP, "=>")) {
                        pos++;
                        items.add(expression());
                        return new MSLNode(MSLNode.LAMBDA, "", items);
                    }
                    return items.size() == 1 ? items.get(0) : new MSLNode(MSLNode.TUPLE, "", items);
                }
                if (t.text.equals("[")) {
                    pos++;
                    List<MSLTree> elements = new ArrayList<>();
                    // [e for x in xs if c]
                    if (!peek().is(Type.PUNCT, "]")) {
                        int mark = pos;
                        MSLTree element = expression();
                        if (peek().is(Type.WORD, "for")) {
                            pos++;
                            MSLTree name = primary();
                            if (!peek().is(Type.WORD, "in")) throw error("expected \"in\" in a comprehension");
                            pos++;
                            MSLTree iterable = expression();
                            List<MSLTree> parts = new ArrayList<>(List.of(element, name, iterable));
                            if (peek().is(Type.WORD, "if")) {
                                pos++;
                                parts.add(expression());
                            }
                            expect(Type.PUNCT, "]");
                            return new MSLNode(MSLNode.COMPREHENSION, "", parts);
                        }
                        pos = mark;
                    }
                    while (!peek().is(Type.PUNCT, "]")) {
                        elements.add(expression());
                        if (!peek().is(Type.PUNCT, ",")) break;
                        pos++;
                    }
                    expect(Type.PUNCT, "]");
                    return new MSLNode(MSLNode.COLLECTION, "", elements);
                }
                if (t.text.equals("{")) {
                    pos++;
                    List<MSLTree> entries = new ArrayList<>();
                    while (!peek().is(Type.PUNCT, "}")) {
                        MSLTree key = expression();
                        if (peek().is(Type.OP, ":")) {
                            pos++;
                            entries.add(new MSLNode(MSLNode.ENTRY, "", key, expression()));
                        } else {
                            entries.add(key);
                        }
                        if (!peek().is(Type.PUNCT, ",")) break;
                        pos++;
                    }
                    expect(Type.PUNCT, "}");
                    return new MSLNode(MSLNode.OBJECT, "", entries);
                }
                throw error("unexpected \"" + t.text + "\"");
            }
            case END -> throw error("pattern ends too early");
            default -> throw error("unexpected \"" + t.text + "\"");
        }
    }

    /** Call arguments after the "(": a, b, ... up to and including ")". */
    private List<MSLTree> arguments() {
        List<MSLTree> args = new ArrayList<>();
        if (!peek().is(Type.PUNCT, ")")) {
            args.add(expression());
            while (peek().is(Type.PUNCT, ",")) {
                pos++;
                args.add(expression());
            }
        }
        expect(Type.PUNCT, ")");
        return args;
    }

    /** A word between two operands is a misspelled operator, e.g. LE for <=. */
    private void rejectWordOperator() {
        Token t = peek();
        // "is null", and the words that continue a statement: if (c) s else s,
        // do s while (c), try s catch s finally s.
        if (t.type != Type.WORD || t.text.equals("is") || t.text.equals("a") || t.text.equals("in") || t.text.equals("not")
                || t.text.equals("for") || t.text.equals("if")
                || t.text.equals("else") || t.text.equals("while")
                || t.text.equals("catch") || t.text.equals("finally")) {
            return;
        }
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
            } else if (s.startsWith("...", i)) {
                out.add(new Token(Type.OP, "...", null));
                i += 3;
            } else if (c == '`') {
                i = template(s, i, out);
            } else if ("(){}[],;.@".indexOf(c) >= 0) {
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

    /** `...${a}...`: one TEMPLATE token holding the holes' text, NUL-separated. */
    private static int template(String s, int open, List<Token> out) {
        StringBuilder holes = new StringBuilder();
        int i = open + 1;
        int count = 0;
        while (i < s.length() && s.charAt(i) != '`') {
            if (s.startsWith("${", i)) {
                int depth = 1;
                int start = i + 2;
                i = start;
                while (i < s.length() && depth > 0) {
                    if (s.charAt(i) == '{') depth++;
                    else if (s.charAt(i) == '}') depth--;
                    i++;
                }
                if (depth > 0) throw new IllegalArgumentException("Invalid MSL pattern \"" + s + "\": unclosed ${ in a template");
                if (count++ > 0) holes.append('\u0000');
                holes.append(s, start, i - 1);
            } else {
                i++;
            }
        }
        if (i >= s.length()) throw new IllegalArgumentException("Invalid MSL pattern \"" + s + "\": unterminated template");
        out.add(new Token(Type.TEMPLATE, "`", holes.toString()));
        return i + 1;
    }

    /** {type}, {type name}, {type: c} or {type name: c}; returns the index after the "}". */
    private static int placeholder(String s, int open, List<Token> out) {
        int i = open + 1;
        while (i < s.length() && isWordPart(s.charAt(i))) i++;
        String type = s.substring(open + 1, i);
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        String name = null;
        if (i < s.length() && isWordStart(s.charAt(i))) {
            int start = i;
            while (i < s.length() && isWordPart(s.charAt(i))) i++;
            name = s.substring(start, i);
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }
        if (i < s.length() && s.charAt(i) == '}') {
            out.add(new Token(Type.PLACEHOLDER, type, null, name));
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
        out.add(new Token(Type.PLACEHOLDER, type, s.substring(start, i - 1).strip(), name));
        return i;
    }

    /** True when the '{' starts a placeholder rather than a block. */
    private static boolean isPlaceholderStart(String s, int open) {
        int i = open + 1;
        if (i >= s.length() || !isWordStart(s.charAt(i))) return false;
        int start = i;
        while (i < s.length() && isWordPart(s.charAt(i))) i++;
        String type = s.substring(start, i);
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        if (i < s.length() && (s.charAt(i) == '}' || s.charAt(i) == ':')) return true;
        if (!MSLPlaceholder.isKnownType(type) || i >= s.length() || !isWordStart(s.charAt(i))) return false;
        while (i < s.length() && isWordPart(s.charAt(i))) i++;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i < s.length() && (s.charAt(i) == '}' || s.charAt(i) == ':');
    }

    private static boolean isWord(String text) {
        if (text.isEmpty() || !isWordStart(text.charAt(0))) return false;
        for (int i = 1; i < text.length(); i++) {
            if (!isWordPart(text.charAt(i))) return false;
        }
        return true;
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
