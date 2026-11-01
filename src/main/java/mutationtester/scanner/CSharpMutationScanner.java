package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import mutationtester.CSharpLexer;
import mutationtester.CSharpParser;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.CstNodes;
import mutationtester.msl.MSLScannerAdapter;

public class CSharpMutationScanner {

    private static int findMatchingParen(String line, int openParen) {
        int depth = 0;

        for (int i = openParen; i < line.length(); i++) {
            if (isInStringOrComment(line, i)) {
                continue;
            }

            char c = line.charAt(i);

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static String getEmptyReturnReplacement(String value, String returnType) {
        if (returnType == null || returnType.isBlank() || returnType.equals("void")) {
            return null;
        }

        String normalizedType = returnType.replace("?", "").trim();
        String emptyValue = defaultValueForType(normalizedType, true);
        if (emptyValue == null || value.equals(emptyValue)) {
            return null;
        }

        if (normalizedType.equals("bool") && (value.equals("true") || value.equals("false"))) {
            return emptyValue;
        }
        if (isNumericType(normalizedType) && !isZeroLiteral(value)) {
            return emptyValue;
        }
        if (normalizedType.equals("string") && !value.equals("\"\"")) {
            return emptyValue;
        }
        if (isEmptyCollectionReturnType(normalizedType) && !isAlreadyEmptyCollection(value)) {
            return emptyValue;
        }

        return null;
    }

    private static String stripOuterParentheses(String expression) {
        String value = expression;
        while (value.startsWith("(") && value.endsWith(")") && findMatchingParen(value, 0) == value.length() - 1) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    /** [+-]0[.0][fFdDmM]: zero written with any number of zeros. */
    private static boolean isZeroLiteral(String value) {
        int i = value.startsWith("+") || value.startsWith("-") ? 1 : 0;
        int end = value.length();
        if (end > i && "fFdDmM".indexOf(value.charAt(end - 1)) >= 0) end--;
        int zeros = 0;
        while (i < end && value.charAt(i) == '0') { i++; zeros++; }
        if (zeros == 0) return false;
        if (i < end && value.charAt(i) == '.') {
            i++;
            int fraction = 0;
            while (i < end && value.charAt(i) == '0') { i++; fraction++; }
            if (fraction == 0) return false;
        }
        return i == end;
    }

    private static String defaultValueForType(String type, boolean emptyReturn) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalized = type.replace("?", "").trim();
        if (normalized.equals("void")) {
            return null;
        }

        if (normalized.equals("bool")) {
            return "false";
        }
        if (isNumericType(normalized)) {
            return normalized.equals("float") ? "0.0f"
                    : normalized.equals("double") ? "0.0"
                    : normalized.equals("decimal") ? "0.0m"
                    : normalized.equals("char") ? "'\\0'"
                    : "0";
        }
        if (normalized.equals("string")) {
            return emptyReturn ? "\"\"" : "null";
        }
        if (normalized.endsWith("[]")) {
            String elementType = normalized.substring(0, normalized.length() - 2);
            return emptyReturn ? "System.Array.Empty<" + elementType + ">()" : "null";
        }
        if (isListType(normalized)) {
            return emptyReturn ? "new " + concreteGenericCollectionType(normalized, "List") : "null";
        }
        if (isSetType(normalized)) {
            return emptyReturn ? "new " + concreteGenericCollectionType(normalized, "HashSet") : "null";
        }
        if (isEnumerableType(normalized)) {
            String elementType = genericArgument(normalized);
            return emptyReturn && elementType != null ? "System.Linq.Enumerable.Empty<" + elementType + ">()" : "null";
        }

        return emptyReturn ? null : "null";
    }

    private static boolean isNumericType(String type) {
        return type.equals("byte")
                || type.equals("sbyte")
                || type.equals("short")
                || type.equals("ushort")
                || type.equals("int")
                || type.equals("uint")
                || type.equals("long")
                || type.equals("ulong")
                || type.equals("float")
                || type.equals("double")
                || type.equals("decimal")
                || type.equals("char");
    }

    private static boolean isEmptyCollectionReturnType(String type) {
        return type.endsWith("[]") || isListType(type) || isSetType(type) || isEnumerableType(type);
    }

    private static boolean isAlreadyEmptyCollection(String value) {
        if (value.startsWith("System.Array.Empty<") || value.startsWith("System.Linq.Enumerable.Empty<")) {
            return true;
        }
        // new List<T>() / new HashSet<T>(), optionally namespace-qualified
        if (!value.startsWith("new") || value.length() < 4 || !Character.isWhitespace(value.charAt(3))) return false;
        String rest = value.substring(3).strip();
        if (!rest.endsWith(")")) return false;
        rest = rest.substring(0, rest.length() - 1).strip();
        if (!rest.endsWith("(")) return false;
        rest = rest.substring(0, rest.length() - 1).strip();
        return isGenericOf(rest, "List", "HashSet");
    }

    /** "Name<...>" or "System.Collections.Generic.Name<...>" for one of the names. */
    private static boolean isGenericOf(String type, String... names) {
        String t = type.startsWith("System.Collections.Generic.")
                ? type.substring("System.Collections.Generic.".length()) : type;
        for (String name : names) {
            if (t.startsWith(name + "<") && t.endsWith(">") && t.indexOf('\n') < 0) return true;
        }
        return false;
    }

    private static boolean isListType(String type) {
        return isGenericOf(type, "IList", "List", "IReadOnlyList", "ICollection", "Collection");
    }

    private static boolean isSetType(String type) {
        return isGenericOf(type, "ISet", "HashSet", "IReadOnlySet", "Set");
    }

    private static boolean isEnumerableType(String type) {
        return isGenericOf(type, "IEnumerable");
    }

    private static String concreteGenericCollectionType(String type, String concreteName) {
        String argument = genericArgument(type);
        return "System.Collections.Generic." + concreteName + "<" + (argument == null ? "object" : argument) + ">()";
    }

    private static String genericArgument(String type) {
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open < 0 || close <= open) {
            return null;
        }
        return type.substring(open + 1, close).trim();
    }

    private static boolean isInStringOrComment(String line, int position) {
        boolean inString = false;
        char stringDelimiter = '\0';

        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);

            if (i > 0 && line.charAt(i - 1) == '\\') {
                continue;
            }

            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                    stringDelimiter = '\0';
                }
            }
        }

        if (inString) {
            return true;
        }

        int commentStart = line.indexOf("//");
        return commentStart >= 0 && commentStart < position;
    }

    private static String removeBom(String sourceCode) {
        if (sourceCode != null && !sourceCode.isEmpty() && sourceCode.charAt(0) == '\uFEFF') {
            return sourceCode.substring(1);
        }
        return sourceCode;
    }

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        sourceCode = removeBom(sourceCode);
        final String sourceText = sourceCode;
        CharStream input = CstNodes.utf16(sourceCode);
        CSharpLexer lexer = new CSharpLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CSharpParser parser = new CSharpParser(tokens);
        ParseTree tree = parser.prog();

        return new MSLScannerAdapter() {
            // C# has no // or ** operator. The calls that compute them stand in:
            // Math.Pow(a, b) is a ** b and Math.Floor(a / b) is a // b (whose
            // inner "/" is part of that operator, not a separate division).

            private boolean isCall(ParseTree ctx) {
                return ctx instanceof CSharpParser.Invocation_expressionContext
                        && ctx.getChildCount() >= 3
                        && ctx.getChild(1).getText().equals("(")
                        && ctx.getChild(ctx.getChildCount() - 1).getText().equals(")");
            }

            private boolean isMathCall(ParseTree ctx, String method) {
                if (!isCall(ctx)) return false;
                String callee = CstNodes.compactText(ctx.getChild(0));
                return callee.equals("Math." + method) || callee.equals("System.Math." + method);
            }

            /** The plain expressions passed to a call (no named, ref or out arguments). */
            private List<ParseTree> callArguments(ParseTree call) {
                List<ParseTree> args = new ArrayList<>();
                if (call.getChildCount() != 4) return args;
                ParseTree list = call.getChild(2);
                for (int i = 0; i < list.getChildCount(); i++) {
                    if (list.getChild(i) instanceof CSharpParser.ArgumentContext arg) {
                        if (arg.argument_name() != null || arg.argument_value().expression() == null) return List.of();
                        args.add(arg.argument_value().expression());
                    }
                }
                return args;
            }

            /** Descends through single-child wrappers and parentheses. */
            private ParseTree unwrap(ParseTree expr) {
                while (true) {
                    if (expr.getChildCount() == 1) {
                        expr = expr.getChild(0);
                    } else if (expr instanceof CSharpParser.Parenthesized_expressionContext paren) {
                        expr = paren.expression();
                    } else {
                        return expr;
                    }
                }
            }

            /** [left, right] when ctx is Math.Pow(a, b) (for **) or Math.Floor(a / b) (for //). */
            private ParseTree[] callOperands(ParseTree ctx, String mslOperator) {
                if ("**".equals(mslOperator) && isMathCall(ctx, "Pow")) {
                    List<ParseTree> args = callArguments(ctx);
                    return args.size() == 2 ? new ParseTree[]{args.get(0), args.get(1)} : null;
                }
                ParseTree division = "//".equals(mslOperator) ? floorDivision(ctx) : null;
                return division == null ? null
                        : new ParseTree[]{division.getChild(0), division.getChild(2)};
            }

            private ParseTree[] anyCallOperands(ParseTree ctx) {
                ParseTree[] operands = callOperands(ctx, "**");
                return operands != null ? operands : callOperands(ctx, "//");
            }

            /** The a / b inside Math.Floor(a / b), or null. */
            private ParseTree floorDivision(ParseTree ctx) {
                if (!isMathCall(ctx, "Floor")) return null;
                List<ParseTree> args = callArguments(ctx);
                if (args.size() != 1) return null;
                ParseTree inner = unwrap(args.get(0));
                return inner instanceof CSharpParser.Multiplicative_expressionContext
                        && inner.getChildCount() == 3 && inner.getChild(1).getText().equals("/") ? inner : null;
            }

            private boolean isInsideFloorDivision(ParseTree division) {
                // The grammar re-parents an invocation's arguments, so their parent
                // may be the expression wrapping the call rather than the call.
                for (ParseTree p = division.getParent(); p != null; p = p.getParent()) {
                    ParseTree call = isCall(p) ? p
                            : p.getChildCount() > 0 && isCall(p.getChild(0)) ? p.getChild(0) : null;
                    if (call != null) return floorDivision(call) == division;
                }
                return false;
            }

            // The grammar builds invocation nodes after the fact, without their
            // own start/stop tokens, so a call's position and text come from its
            // callee's first token and its closing ')'.

            private Token callStart(ParseTree call) {
                return ((ParserRuleContext) call.getChild(0)).getStart();
            }

            private String callText(ParseTree call) {
                int stop = ((TerminalNode) call.getChild(call.getChildCount() - 1)).getSymbol().getStopIndex();
                return sourceText.substring(callStart(call).getStartIndex(), stop + 1);
            }

            // C# also compares values with a.Equals(b) / Equals(a, b), the
            // counterpart of Java's a.equals(b) and Racket's equal?;
            // !a.Equals(b) is the matching !=.
            private static final Set<String> STATIC_EQUALS =
                    Set.of("Equals", "object.Equals", "Object.Equals", "string.Equals", "String.Equals");

            /** {left text, right text} for a.Equals(b) or Equals(a, b), else null. */
            private String[] equalsOperands(ParseTree ctx) {
                if (!isCall(ctx)) return null;
                String callee = CstNodes.compactText(ctx.getChild(0));
                List<ParseTree> args = callArguments(ctx);
                if (args.size() == 2 && STATIC_EQUALS.contains(callee)) {
                    return new String[]{getNodeText(args.get(0)), getNodeText(args.get(1))};
                }
                if (args.size() == 1 && callee.endsWith(".Equals") && !STATIC_EQUALS.contains(callee)) {
                    String receiver = getNodeText(ctx.getChild(0)).strip();
                    return new String[]{receiver.substring(0, receiver.length() - ".Equals".length()).strip(),
                            getNodeText(args.get(0))};
                }
                return null;
            }

            /** The Equals call inside !a.Equals(b), else null. */
            private ParseTree negatedEquals(ParseTree ctx) {
                if (!(ctx instanceof CSharpParser.Unary_expressionContext) || ctx.getChildCount() != 2
                        || !ctx.getChild(0).getText().equals("!")) {
                    return null;
                }
                ParseTree inner = unwrap(ctx.getChild(1));
                return equalsOperands(inner) != null ? inner : null;
            }

            /** Operand nodes of an Equals call or its negation: receiver (or first arg) and last arg. */
            private ParseTree[] equalsOperandNodes(ParseTree ctx) {
                ParseTree inner = negatedEquals(ctx);
                ParseTree call = inner != null ? inner : ctx;
                if (equalsOperands(call) == null) return null;
                List<ParseTree> args = callArguments(call);
                ParseTree left = args.size() == 2 ? args.get(0) : call.getChild(0);
                return new ParseTree[]{left, args.get(args.size() - 1)};
            }

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if ("**".equals(mslOperator) || "//".equals(mslOperator)) {
                    return callOperands(ctx, mslOperator) != null;
                }
                if ("==".equals(mslOperator) && equalsOperands(ctx) != null) return true;
                if ("!=".equals(mslOperator) && negatedEquals(ctx) != null) return true;
                if ("/".equals(mslOperator) && isInsideFloorDivision(ctx)) {
                    return false;
                }
                if ("*".equals(mslOperator) && isAmbiguousMultiplyExpression(ctx)) {
                    return true;
                }
                if (ctx.getChildCount() >= 3) {
                    for (int i = 1; i < ctx.getChildCount() - 1; i++) {
                        if (ctx.getChild(i).getText().equals(mslOperator)) {
                            return true;
                        }
                    }
                }
                return false;
            }

            @Override
            public List<String> getOperatorAliases(String canonicalOperator) {
                return List.of(canonicalOperator);
            }

            @Override
            public String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
                // // and ** are calls (see callOperands). An operator can't become
                // one: Math.Pow and Math.Floor return double, which an int
                // context won't accept.
                return switch (canonicalOperator) {
                    case "//", "**" -> null;
                    default -> canonicalOperator;
                };
            }

            @Override
            public OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
                ParseTree negated = "!=".equals(fromOperator) ? negatedEquals(ctx) : null;
                ParseTree equalsCall = negated != null ? negated
                        : "==".equals(fromOperator) && equalsOperands(ctx) != null ? ctx : null;
                if (equalsCall != null) {
                    // a.Equals(b) <-> !a.Equals(b); the call node's own text is unreliable.
                    Token start = negated != null ? ((ParserRuleContext) ctx).getStart() : callStart(equalsCall);
                    int stop = negated != null
                            ? ((ParserRuleContext) ctx).getStop().getStopIndex()
                            : ((TerminalNode) equalsCall.getChild(equalsCall.getChildCount() - 1)).getSymbol().getStopIndex();
                    String text = sourceText.substring(start.getStartIndex(), stop + 1);
                    String replacement;
                    if (negated == null && "!=".equals(toOperator)) {
                        replacement = "!" + text;
                    } else if (negated != null && "==".equals(toOperator)) {
                        replacement = callText(negated);
                    } else {
                        String op = toLanguageOperator(toOperator, ctx);
                        if (op == null) return null;
                        String[] operands = equalsOperands(equalsCall);
                        replacement = MSLScannerAdapter.infix(operands[0], op, operands[1]);
                    }
                    return new OperatorEdit(start.getLine(), start.getCharPositionInLine(), text, replacement);
                }
                ParseTree[] call = callOperands(ctx, fromOperator);
                if (call == null) {
                    return MSLScannerAdapter.super.createOperatorEdit(ctx, fromOperator, toOperator);
                }
                // Math.Pow(a, b) -> (a * b)
                String op = toLanguageOperator(toOperator, ctx);
                if (op == null) return null;
                Token start = callStart(ctx);
                return new OperatorEdit(start.getLine(), start.getCharPositionInLine(), callText(ctx),
                        MSLScannerAdapter.infix(getNodeText(call[0]), op, getNodeText(call[1])));
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                ParseTree[] call = anyCallOperands(ctx);
                if (call == null) call = equalsOperandNodes(ctx);
                if (call != null) {
                    return call[0];
                }
                ParseTree syntheticLeft = getAmbiguousMultiplyLeftOperand(ctx);
                if (syntheticLeft != null) {
                    return syntheticLeft;
                }
                return ctx.getChild(0);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                ParseTree[] call = anyCallOperands(ctx);
                if (call == null) call = equalsOperandNodes(ctx);
                if (call != null) {
                    return call[1];
                }
                ParseTree syntheticRight = getAmbiguousMultiplyRightOperand(ctx);
                if (syntheticRight != null) {
                    return syntheticRight;
                }
                return ctx.getChild(ctx.getChildCount() - 1);
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                ParseTree syntheticOperator = getAmbiguousMultiplyOperatorToken(ctx);
                if (syntheticOperator != null) {
                    return syntheticOperator;
                }
                if (ctx.getChildCount() >= 3) {
                    for (int i = 1; i < ctx.getChildCount() - 1; i++) {
                        String text = ctx.getChild(i).getText();
                        if ("+".equals(text) || "-".equals(text) || "*".equals(text)
                                || "/".equals(text) || "%".equals(text)
                                || "&".equals(text) || "|".equals(text) || "^".equals(text)
                                || "<<".equals(text) || ">>".equals(text) || ">>>".equals(text)
                                || "<".equals(text) || ">".equals(text) || "<=".equals(text) || ">=".equals(text)
                                || "==".equals(text) || "!=".equals(text)
                                || "&&".equals(text) || "||".equals(text)) {
                            return ctx.getChild(i);
                        }
                    }
                    return ctx.getChild(1);
                }
                return null;
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                return MSLScannerAdapter.isQuantityText(getNodeText(ctx), ctx instanceof TerminalNode);
            }

            @Override
            public boolean isStringLiteral(ParseTree ctx) {
                return ctx != null && MSLScannerAdapter.isStringLiteral(getNodeText(ctx).strip(), false);
            }

            // MSL's "s is null" is a null-or-empty check: string.IsNullOrEmpty(s),
            // or the check written out (s == null || s == "" and variants).
            @Override
            public ParseTree nullCheckSubject(ParseTree ctx) {
                if (isCall(ctx)) {
                    String callee = CstNodes.compactText(ctx.getChild(0));
                    List<ParseTree> args = callArguments(ctx);
                    return (callee.equals("string.IsNullOrEmpty") || callee.equals("String.IsNullOrEmpty"))
                            && args.size() == 1 ? args.get(0) : null;
                }
                if (!(ctx instanceof CSharpParser.Conditional_or_expressionContext or) || or.getChildCount() != 3) {
                    return null;
                }
                ParseTree isNull = unwrap(or.getChild(0));
                if (!isEqualityTest(isNull) || !isNull.getChild(2).getText().equals("null")
                        || !CstNodes.isDottedName(isNull.getChild(0))) {
                    return null;
                }
                ParseTree subject = isNull.getChild(0);
                String s = subject.getText();
                ParseTree isEmpty = unwrap(or.getChild(2));
                if (!isEqualityTest(isEmpty)) return null;
                String left = isEmpty.getChild(0).getText();
                String right = isEmpty.getChild(2).getText();
                boolean empty = left.equals(s)
                        && (right.equals("\"\"") || right.equals("string.Empty") || right.equals("String.Empty"));
                boolean zeroLength = left.equals(s + ".Length") && right.equals("0");
                return empty || zeroLength ? subject : null;
            }

            @Override
            public List<ParseTree> callArguments(ParseTree ctx, String functionName) {
                return isCall(ctx) && CstNodes.compactText(ctx.getChild(0)).equals(functionName)
                        ? callArguments(ctx) : null;
            }

            private boolean isEqualityTest(ParseTree node) {
                return node instanceof CSharpParser.Equality_expressionContext && node.getChildCount() == 3
                        && node.getChild(1).getText().equals("==");
            }

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                // Only real statements: "delegate void Notify();" also looks like a call.
                if (!(ctx instanceof CSharpParser.Expression_statementContext prc)) {
                    return false;
                }
                Token start = prc.getStart();
                Token stop = prc.getStop();
                if (start == null || stop == null || start.getLine() != stop.getLine()) {
                    return false;
                }
                // [await] name(.name|<T>)*( ... ) ; - a statement that is a call.
                List<Token> t = CstNodes.tokens(ctx);
                if (t.size() < 4 || !t.get(t.size() - 1).getText().equals(";")
                        || !t.get(t.size() - 2).getText().equals(")")) {
                    return false;
                }
                String first = t.get(0).getText();
                if (first.startsWith("return") || first.startsWith("yield")) return false;
                int i = first.equals("await") ? 1 : 0;
                if (i >= t.size() || t.get(i).getText().isEmpty()) return false;
                char initial = t.get(i).getText().charAt(0);
                if (!Character.isLetter(initial) && initial != '_') return false;
                for (; i < t.size(); i++) {
                    String text = t.get(i).getText();
                    if (text.equals("(")) return true;
                    for (int c = 0; c < text.length(); c++) {
                        char ch = text.charAt(c);
                        if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '.' && ch != '<' && ch != '>') {
                            return false;
                        }
                    }
                }
                return false;
            }

            private boolean isCSharpIdentifier(String text) {
                String name = text.startsWith("@") ? text.substring(1) : text;
                return CstNodes.isName(name) && name.indexOf('$') < 0;
            }

            // ── Statements, for Except: trees ────────────────────────────

            @Override
            public List<ParseTree> matchNode(mutationtester.msl.MSLNode pattern, ParseTree ctx) {
                switch (pattern.kind()) {
                    case mutationtester.msl.MSLNode.IF -> {
                        ParseTree s = statementBody(ctx);
                        return s instanceof CSharpParser.If_statementContext i
                                ? List.of(i.boolean_expression(), i.embedded_statement(0)) : null;
                    }
                    case mutationtester.msl.MSLNode.WHILE -> {
                        ParseTree s = statementBody(ctx);
                        return s instanceof CSharpParser.While_statementContext w
                                ? List.of(w.boolean_expression(), w.embedded_statement()) : null;
                    }
                    case mutationtester.msl.MSLNode.FOR -> {
                        ParseTree s = statementBody(ctx);
                        if (!(s instanceof CSharpParser.For_statementContext f)) return null;
                        return java.util.Arrays.asList(f.for_initializer(), f.for_condition(), f.for_iterator(),
                                f.embedded_statement());
                    }
                    case mutationtester.msl.MSLNode.ASSIGN -> {
                        ParseTree s = statementBody(ctx);
                        ParseTree expr = s instanceof CSharpParser.Expression_statementContext e
                                ? e.statement_expression() : ctx;
                        ParseTree assign = unwrap(expr);
                        if (assign instanceof CSharpParser.AssignmentContext a
                                && a.assignment_operator().getText().equals("=")) {
                            return List.of(a.unary_expression(), a.expression());
                        }
                        return null;
                    }
                    case mutationtester.msl.MSLNode.BLOCK -> {
                        ParseTree s = statementBody(ctx);
                        if (!(s instanceof CSharpParser.BlockContext b) || b.statement_list() == null
                                || b.statement_list().statement().size() < pattern.children().size()) {
                            return null;
                        }
                        return new ArrayList<>(b.statement_list().statement().subList(0, pattern.children().size()));
                    }
                    default -> {
                        return MSLScannerAdapter.super.matchNode(pattern, ctx);
                    }
                }
            }

            /** The specific statement inside statement / embedded_statement wrappers. */
            private ParseTree statementBody(ParseTree ctx) {
                ParseTree p = ctx;
                while ((p instanceof CSharpParser.StatementContext || p instanceof CSharpParser.Embedded_statementContext
                        || p instanceof CSharpParser.Selection_statementContext
                        || p instanceof CSharpParser.Iteration_statementContext)
                        && p.getChildCount() == 1) {
                    p = p.getChild(0);
                }
                return p;
            }

            // ── Increments ───────────────────────────────────────────────

            @Override
            public ParseTree incrementOperand(String operator, ParseTree ctx) {
                boolean post = operator.equals("++") ? ctx instanceof CSharpParser.Post_increment_expressionContext
                        : operator.equals("--") && ctx instanceof CSharpParser.Post_decrement_expressionContext;
                boolean pre = operator.equals("++") ? ctx instanceof CSharpParser.Pre_increment_expressionContext
                        : operator.equals("--") && ctx instanceof CSharpParser.Pre_decrement_expressionContext;
                // x++ is [operand, '++'] and ++x is ['++', operand], whether the
                // parser built the node or CSharpParserBase inserted it.
                if (ctx.getChildCount() != 2) return null;
                if (post) return ctx.getChild(0);
                if (pre) return ctx.getChild(1);
                return null;
            }

            @Override
            public ParseTree expressionStatement(ParseTree ctx) {
                return ctx.getParent() instanceof CSharpParser.Statement_expressionContext se
                        && se.getParent() instanceof CSharpParser.Expression_statementContext statement
                        ? statement : null;
            }

            @Override
            public boolean discardsValue(ParseTree ctx) {
                // for (i = 0, j++; ...; i++, j--) lists statement expressions too
                return expressionStatement(ctx) != null
                        || ctx.getParent() instanceof CSharpParser.Statement_expressionContext se
                            && se.getParent() instanceof CSharpParser.Statement_expression_listContext;
            }

            @Override
            public Set<String> supportedKinds() {
                return Set.of("binary", "predicate", "literal", "identifier", "call", "if", "while", "for", "assign", "block",
                        "increment");
            }

            @Override
            public Set<String> supportedPlaceholders() {
                return Set.of("quantity", "match", "statement", "invocation_statement",
                        "return_value", "constructor_call", "non_void_method_call",
                        "equality_condition", "order_condition");
            }

            // ── PIT-style placeholders ───────────────────────────────────

            @Override
            public boolean matchesPlaceholder(String type, ParseTree ctx) {
                return switch (type) {
                    case "statement" -> ctx instanceof CSharpParser.StatementContext
                            || ctx instanceof CSharpParser.Embedded_statementContext;
                    case "return_value" -> returnedExpression(ctx) && renderValue("empty_value", ctx) != null;
                    case "constructor_call" -> isConstructorCall(ctx);
                    case "non_void_method_call" -> nonVoidCallType(ctx) != null;
                    case "equality_condition" -> isCondition(ctx) && hasEqualityOperator(ctx);
                    case "order_condition" -> isCondition(ctx) && hasOrderOperator(ctx);
                    default -> MSLScannerAdapter.super.matchesPlaceholder(type, ctx);
                };
            }

            private boolean singleLine(ParseTree ctx) {
                Token start = CstNodes.start(ctx);
                Token stop = CstNodes.stop(ctx);
                if (start == null && isCall(ctx) && ctx.getChild(0) instanceof ParserRuleContext) {
                    // An invocation node has no tokens of its own: callee to ')'.
                    start = callStart(ctx);
                    stop = ((TerminalNode) ctx.getChild(ctx.getChildCount() - 1)).getSymbol();
                }
                return start != null && stop != null && start.getLine() == stop.getLine();
            }

            /** The value of a one-line "return x;" or "yield return x;". */
            private boolean returnedExpression(ParseTree ctx) {
                return ctx instanceof CSharpParser.ExpressionContext
                        && (ctx.getParent() instanceof CSharpParser.Return_statementContext
                            || isYieldedValue(ctx))
                        && singleLine(ctx.getParent());
            }

            private boolean isYieldedValue(ParseTree ctx) {
                return ctx.getParent() instanceof CSharpParser.Yield_statementContext y && y.expression() == ctx;
            }

            /** new T(...) for a reference type T. */
            private boolean isConstructorCall(ParseTree ctx) {
                if (!(ctx instanceof CSharpParser.Object_creation_expressionContext creation)
                        || creation.getChildCount() < 4 || !creation.getChild(2).getText().equals("(")
                        || isValueType(creation.type().getText())) {
                    return false;
                }
                Token start = creation.getStart();
                Token close = ((TerminalNode) creation.getChild(creation.argument_list() == null ? 3 : 4)).getSymbol();
                return start.getLine() == close.getLine();
            }

            private boolean isValueType(String type) {
                return Set.of("bool", "byte", "sbyte", "short", "ushort", "int", "uint", "long", "ulong",
                        "float", "double", "decimal", "char").contains(type.replace("?", ""));
            }

            /** The condition of an if, while or do-while, or of a ?: expression. */
            private boolean isCondition(ParseTree ctx) {
                ParseTree parent = ctx.getParent();
                if (ctx instanceof CSharpParser.Boolean_expressionContext) {
                    return parent instanceof CSharpParser.If_statementContext
                            || parent instanceof CSharpParser.While_statementContext
                            || parent instanceof CSharpParser.Do_statementContext;
                }
                return parent instanceof CSharpParser.Conditional_expressionContext c
                        && c.getChildCount() == 5 && c.getChild(0) == ctx && !ctx.getText().equals("true")
                        && !ctx.getText().equals("false");
            }

            private boolean hasEqualityOperator(ParseTree ctx) {
                for (Token t : CstNodes.tokens(ctx)) {
                    if (t.getText().equals("==") || t.getText().equals("!=")) return true;
                }
                return false;
            }

            private boolean hasOrderOperator(ParseTree ctx) {
                for (Token t : CstNodes.tokens(ctx)) {
                    String text = t.getText();
                    if (text.equals("<") || text.equals(">") || text.equals("<=") || text.equals(">=")
                            || text.equals("=>")) {
                        return true;
                    }
                }
                return false;
            }

            /**
             * The declared type a call's result goes into: the method's return
             * type for "return f(x);", or the variable's type for "T v = f(x);".
             * Null when the call isn't one of those, or the type is var or void.
             */
            private String nonVoidCallType(ParseTree ctx) {
                // "await f(x)" is replaced as a whole, like the call itself.
                ParseTree call = ctx instanceof CSharpParser.Await_expressionContext await
                        ? unwrap(await.unary_expression()) : ctx;
                if (!isCall(call) || !singleLine(ctx)) return null;
                String callee = CstNodes.compactText(call.getChild(0));
                if (callee.startsWith("new") || !isSimpleCallee(callee)) return null;
                ParseTree value = ctx;
                while (value.getParent() != null && value.getParent().getChildCount() == 1) value = value.getParent();
                // A declaration whose whole value is the call gets the type's default.
                String typed = value instanceof CSharpParser.Local_variable_initializerContext
                        || value instanceof CSharpParser.Variable_initializerContext ? "native:" : "assigned:";
                if (value.getParent() instanceof CSharpParser.Return_statementContext) {
                    // return f(x); - the call must be the whole value.
                    String type = precedingReturnType(ctx);
                    return type == null || type.equals("void") ? null : "native:" + type;
                }
                // Otherwise the call that starts an assigned value: T v = f(x) ?? y; v = f(x);
                // new T { P = f(x) }; new { p = f(x) }.
                value = ctx;
                while (value.getParent() != null && value.getParent().getChild(0) == value
                        && !(value instanceof CSharpParser.Local_variable_initializerContext)
                        && !(value instanceof CSharpParser.Variable_initializerContext)) {
                    value = value.getParent();
                }
                ParseTree parent = value instanceof CSharpParser.Local_variable_initializerContext ? value : value.getParent();
                if (parent instanceof CSharpParser.Member_initializerContext init && init.initializer_value() == value
                        || parent instanceof CSharpParser.Member_declaratorContext member && member.expression() == value) {
                    return "assigned:";
                }
                if (parent instanceof CSharpParser.Local_variable_initializerContext init
                        && init.getParent() instanceof CSharpParser.Explicitly_typed_local_variable_declaratorContext declarator
                        && declarator.getParent() != null
                        && declarator.getParent().getParent() instanceof CSharpParser.Explicitly_typed_local_variable_declarationContext decl) {
                    return typed + decl.type().getText();
                }
                if (value instanceof CSharpParser.Variable_initializerContext init
                        && init.getParent() instanceof CSharpParser.Variable_declaratorContext declarator
                        && declarator.getParent() != null
                        && declarator.getParent().getParent() instanceof CSharpParser.Field_declarationContext field) {
                    return typed + field.type().getText();
                }
                if (parent instanceof CSharpParser.AssignmentContext assign
                        && assign.assignment_operator().getText().equals("=") && assign.expression() == value) {
                    // v = f(x): the type v was declared with, when it was; "object" (null) otherwise.
                    String type = declaredType(assign.unary_expression());
                    return "assigned:" + (type == null || type.equals("var") ? "" : type);
                }
                return null;
            }

            /** A callee of names, dots and type arguments: Foo, a.b.Get, Repo<T>.Find. */
            private boolean isSimpleCallee(String callee) {
                if (callee.isEmpty() || (!Character.isLetter(callee.charAt(0)) && callee.charAt(0) != '_')) return false;
                for (int i = 0; i < callee.length(); i++) {
                    char c = callee.charAt(i);
                    if (!Character.isLetterOrDigit(c) && "_$.<>".indexOf(c) < 0) return false;
                }
                return true;
            }

            /** The declared type of the variable, field or parameter a target names, from its nearest earlier declaration. */
            private String declaredType(ParseTree target) {
                List<Token> tokens = CstNodes.tokens(target);
                if (tokens.isEmpty()) return null;
                String name = tokens.get(tokens.size() - 1).getText();
                int before = tokens.get(0).getTokenIndex();
                String type = null;
                int nearest = -1;
                for (Object[] declaration : declarations()) {
                    int index = (Integer) declaration[0];
                    if (index < before && index > nearest && declaration[1].equals(name)) {
                        nearest = index;
                        type = (String) declaration[2];
                    }
                }
                return type;
            }

            private List<Object[]> declarations;

            /** {token index, name, type} for each typed local, field and parameter. */
            private List<Object[]> declarations() {
                if (declarations == null) {
                    declarations = new ArrayList<>();
                    collectDeclarations(tree);
                }
                return declarations;
            }

            private void collectDeclarations(ParseTree node) {
                if (node instanceof CSharpParser.Explicitly_typed_local_variable_declarationContext local) {
                    for (CSharpParser.Explicitly_typed_local_variable_declaratorContext d
                            : local.explicitly_typed_local_variable_declarators().explicitly_typed_local_variable_declarator()) {
                        addDeclaration(d.identifier(), local.type().getText());
                    }
                } else if (node instanceof CSharpParser.Field_declarationContext field) {
                    for (CSharpParser.Variable_declaratorContext d : field.variable_declarators().variable_declarator()) {
                        addDeclaration(d.identifier(), field.type().getText());
                    }
                } else if (node instanceof CSharpParser.Fixed_parameterContext param && param.type() != null) {
                    addDeclaration(param.identifier(), param.type().getText());
                }
                for (int i = 0; i < node.getChildCount(); i++) collectDeclarations(node.getChild(i));
            }

            private void addDeclaration(CSharpParser.IdentifierContext id, String type) {
                if (id != null && id.getStart() != null) {
                    declarations.add(new Object[]{id.getStart().getTokenIndex(), id.getText(), type});
                }
            }

            /** The return type of the nearest method declared before ctx, or null. */
            private String precedingReturnType(ParseTree ctx) {
                Token at = CstNodes.start(ctx);
                if (at == null && isCall(ctx) && ctx.getChild(0) instanceof ParserRuleContext) at = callStart(ctx);
                if (at == null) return null;
                String type = null;
                int nearest = -1;
                for (CSharpParser.Method_declarationContext m : methods()) {
                    int index = m.getStart().getTokenIndex();
                    if (index < at.getTokenIndex() && index > nearest && m.return_type() != null) {
                        nearest = index;
                        type = m.return_type().getText();
                    }
                }
                return type;
            }

            private List<CSharpParser.Method_declarationContext> methods;

            private List<CSharpParser.Method_declarationContext> methods() {
                if (methods == null) {
                    methods = new ArrayList<>();
                    collectMethods(tree);
                }
                return methods;
            }

            private void collectMethods(ParseTree node) {
                if (node instanceof CSharpParser.Method_declarationContext m && m.getStart() != null) methods.add(m);
                for (int i = 0; i < node.getChildCount(); i++) collectMethods(node.getChild(i));
            }

            @Override
            public String renderLiteral(String canonical, ParseTree ctx) {
                if (canonical.equals("null") && ctx instanceof CSharpParser.Object_creation_expressionContext creation) {
                    // "var x = null;" doesn't compile; default(T) keeps the type.
                    ParseTree p = creation;
                    while (p.getParent() != null && p.getParent().getChildCount() == 1) p = p.getParent();
                    if (p.getParent() instanceof CSharpParser.Implicitly_typed_local_variable_declaratorContext) {
                        return "default(" + creation.type().getText() + ")";
                    }
                }
                return canonical;
            }

            @Override
            public String renderValue(String placeholderType, ParseTree ctx) {
                switch (placeholderType) {
                    case "empty_value" -> {
                        String type = precedingReturnType(ctx);
                        String value = stripOuterParentheses(getNodeText(ctx).strip());
                        // "yield return x" yields one element, so the method's declared
                        // (IEnumerable) return type doesn't describe x; its default does.
                        String typed = type == null || isYieldedValue(ctx) ? null : getEmptyReturnReplacement(value, type);
                        if (typed != null) return typed;
                        if (type == null) return TypedEmptyValues.forExpression(getNodeText(ctx), "null", "false");
                        return TypedEmptyValues.forType(type, true);
                    }
                    case "default_value" -> {
                        // A returned call or a typed local's value gets the type's default;
                        // other assigned values get its empty value (null when unknown).
                        String kind = nonVoidCallType(ctx);
                        if (kind == null) return null;
                        if (kind.startsWith("native:")) return defaultValueForType(kind.substring("native:".length()), false);
                        String type = kind.substring("assigned:".length());
                        if (type.isEmpty()) return "null";
                        String value = TypedEmptyValues.forType(type, true);
                        return value == null ? null
                                : value.equals("\"\"") || value.equals("Optional.empty()") ? "default" : value;
                    }
                    default -> {
                        return null;
                    }
                }
            }

            /** An object creation with an initializer is replaced only up to its ')'. */
            @Override
            public OperatorEdit replace(ParseTree ctx, mutationtester.msl.MSLTree from, mutationtester.msl.MSLTree to,
                                        mutationtester.msl.MSLBindings bindings) {
                if (from instanceof mutationtester.msl.MSLPlaceholder p && p.type().equals("constructor_call")
                        && ctx instanceof CSharpParser.Object_creation_expressionContext creation) {
                    String replacement = render(to, bindings.cursor(), ctx);
                    if (replacement == null) return null;
                    Token start = creation.getStart();
                    Token close = ((TerminalNode) creation.getChild(creation.argument_list() == null ? 3 : 4)).getSymbol();
                    return new OperatorEdit(start.getLine(), start.getCharPositionInLine(),
                            sourceText.substring(start.getStartIndex(), close.getStopIndex() + 1), replacement);
                }
                return MSLScannerAdapter.super.replace(ctx, from, to, bindings);
            }

            @Override
            public String getNodeText(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null
                            && start.getStartIndex() >= 0
                            && stop.getStopIndex() >= start.getStartIndex()
                            && stop.getStopIndex() < sourceText.length()) {
                        return sourceText.substring(start.getStartIndex(), stop.getStopIndex() + 1);
                    }
                    // Invocation nodes are built without their own start/stop tokens.
                    if (start == null && isCall(ctx) && ctx.getChild(0) instanceof ParserRuleContext) {
                        return callText(ctx);
                    }
                }
                return ctx.getText();
            }

            @Override
            public int[] getTokenPosition(ParseTree ctx) {
                if (ctx instanceof TerminalNode tn) {
                    Token s = tn.getSymbol();
                    return new int[]{s.getLine(), s.getCharPositionInLine()};
                }
                if (ctx instanceof ParserRuleContext prc) {
                    Token s = prc.getStart();
                    if (s != null) {
                        return new int[]{s.getLine(), s.getCharPositionInLine()};
                    }
                    for (int i = 0; i < prc.getChildCount(); i++) {
                        int[] childPos = getTokenPosition(prc.getChild(i));
                        if (childPos[0] != 0 || childPos[1] != 0) {
                            return childPos;
                        }
                    }
                }
                return new int[]{0, 0};
            }

            @Override
            public ParseTree getRootContext() {
                return tree;
            }

            private boolean isAmbiguousMultiplyExpression(ParseTree ctx) {
                return getAmbiguousMultiplyLeftOperand(ctx) != null;
            }

            private ParseTree getAmbiguousMultiplyLeftOperand(ParseTree ctx) {
                if (!(ctx instanceof CSharpParser.Declaration_expressionContext) || ctx.getChildCount() != 2) {
                    return null;
                }

                String leftText = compactNodeText(ctx.getChild(0));
                String rightText = compactNodeText(ctx.getChild(1));
                if (!leftText.endsWith("*") || !isCSharpIdentifier(rightText)) {
                    return null;
                }

                String leftOperand = leftText.substring(0, leftText.length() - 1);
                if (!isCSharpIdentifier(leftOperand)) {
                    return null;
                }

                Token baseToken = getStartToken(ctx.getChild(0));
                if (baseToken == null) {
                    return null;
                }

                return createSyntheticTerminal(leftOperand, baseToken, 0);
            }

            private ParseTree getAmbiguousMultiplyRightOperand(ParseTree ctx) {
                if (!isAmbiguousMultiplyExpression(ctx)) {
                    return null;
                }
                return ctx.getChild(1);
            }

            private ParseTree getAmbiguousMultiplyOperatorToken(ParseTree ctx) {
                if (!(ctx instanceof CSharpParser.Declaration_expressionContext) || ctx.getChildCount() != 2) {
                    return null;
                }

                String originalLeftText = getNodeText(ctx.getChild(0));
                String compactLeftText = compactNodeText(ctx.getChild(0));
                String rightText = compactNodeText(ctx.getChild(1));
                if (!compactLeftText.endsWith("*") || !isCSharpIdentifier(rightText)) {
                    return null;
                }

                String leftOperand = compactLeftText.substring(0, compactLeftText.length() - 1);
                if (!isCSharpIdentifier(leftOperand)) {
                    return null;
                }

                Token baseToken = getStartToken(ctx.getChild(0));
                if (baseToken == null) {
                    return null;
                }

                int operatorOffset = originalLeftText.lastIndexOf('*');
                if (operatorOffset < 0) {
                    operatorOffset = leftOperand.length();
                }
                return createSyntheticTerminal("*", baseToken, operatorOffset);
            }

            private Token getStartToken(ParseTree ctx) {
                if (ctx instanceof TerminalNode tn) {
                    return tn.getSymbol();
                }
                if (ctx instanceof ParserRuleContext prc) {
                    return prc.getStart();
                }
                return null;
            }

            private ParseTree createSyntheticTerminal(String text, Token baseToken, int offset) {
                CommonToken token = new CommonToken(0, text);
                token.setLine(baseToken.getLine());
                token.setCharPositionInLine(baseToken.getCharPositionInLine() + Math.max(offset, 0));
                return new TerminalNodeImpl(token);
            }

            private String compactNodeText(ParseTree ctx) {
                return CstNodes.compactText(ctx);
            }
        };
    }

}
