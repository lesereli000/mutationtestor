package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.PythonLexer;
import mutationtester.PythonParser;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.CstNodes;
import mutationtester.msl.MSLBindings;
import mutationtester.msl.MSLNode;
import mutationtester.msl.MSLPlaceholder;
import mutationtester.msl.MSLScannerAdapter;
import mutationtester.msl.MSLTree;

public class PythonMutationScanner {

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = CstNodes.utf16(sourceCode);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);
        ParseTree tree = parser.root();
        Map<org.antlr.v4.runtime.CharStream, Integer> snippetFileOffset = new IdentityHashMap<>();
        ParseTree root = treeWithFStringFields(sourceCode, tokens, tree, snippetFileOffset);

        return new MSLScannerAdapter() {

            // Python has no >>> (its ints are unbounded); x >>> n is written as
            // (x & 0xFFFFFFFF) >> n. That shift is matched as >>>, and its mask
            // and shift are part of it rather than separate & and >> operators.

            /** For (x & MASK) >> n returns the x & MASK node, else null. */
            private ParseTree unsignedShiftMask(ParseTree ctx) {
                if (!isBinaryOp(ctx, ">>")) return null;
                ParseTree masked = unwrapParens(ctx.getChild(0));
                if (isBinaryOp(masked, "&")
                        && (MSLScannerAdapter.isUnsignedShiftMask(masked.getChild(2).getText())
                            || MSLScannerAdapter.isUnsignedShiftMask(masked.getChild(0).getText()))) {
                    return masked;
                }
                return null;
            }

            private boolean isBinaryOp(ParseTree ctx, String op) {
                return ctx instanceof PythonParser.ExprContext expr && expr.getChildCount() == 3
                        && expr.op != null && expr.op.getText().equals(op);
            }

            private ParseTree unwrapParens(ParseTree expr) {
                while (true) {
                    if (expr.getChildCount() == 1) {
                        expr = expr.getChild(0);
                    } else if (expr.getChildCount() == 3 && expr.getChild(0).getText().equals("(")
                            && expr.getChild(2).getText().equals(")")) {
                        expr = expr.getChild(1);
                    } else {
                        return expr;
                    }
                }
            }

            private boolean isMaskOfUnsignedShift(ParseTree and) {
                ParseTree p = and.getParent();
                for (int depth = 0; p != null && depth < 12; depth++, p = p.getParent()) {
                    if (unsignedShiftMask(p) == and) return true;
                }
                return false;
            }

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                // Handle ExprContext (arithmetic, bitwise)
                // (unary forms such as -x also carry an op, so require two operands)
                if (ctx instanceof PythonParser.ExprContext) {
                    return switch (mslOperator) {
                        case ">>>" -> unsignedShiftMask(ctx) != null;
                        case ">>" -> isBinaryOp(ctx, ">>") && unsignedShiftMask(ctx) == null;
                        case "&" -> isBinaryOp(ctx, "&") && !isMaskOfUnsignedShift(ctx);
                        default -> isBinaryOp(ctx, mslOperator);
                    };
                }
                // Handle ComparisonContext
                if (ctx instanceof PythonParser.ComparisonContext) {
                    return mslOperator.equals(comparisonOperator(ctx));
                }
                // Handle logical_test
                if (ctx instanceof PythonParser.Logical_testContext logCtx) {
                    return logCtx.op != null && logCtx.op.getText().equals(mslOperator);
                }
                return false;
            }

            /**
             * The operator of a single comparison ("<", "is", "is not"), or null.
             * "is not" and "not in" are two tokens.
             */
            private String comparisonOperator(ParseTree ctx) {
                if (!(ctx instanceof PythonParser.ComparisonContext)) return null;
                if (ctx.getChildCount() == 3) return ctx.getChild(1).getText();
                if (ctx.getChildCount() == 4) return ctx.getChild(1).getText() + " " + ctx.getChild(2).getText();
                return null;
            }

            @Override
            public List<String> getOperatorAliases(String canonicalOperator) {
                // is / is not compare identity, as == / != do for references
                // in Java and C# (s is None <-> s == null).
                return switch (canonicalOperator) {
                    case "&&" -> List.of("and");
                    case "||" -> List.of("or");
                    case "==" -> List.of("==", "is");
                    case "!=" -> List.of("!=", "is not");
                    default -> List.of(canonicalOperator);
                };
            }

            @Override
            public String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
                String source = comparisonOperator(ctx);
                boolean identity = "is".equals(source) || "is not".equals(source);
                return switch (canonicalOperator) {
                    case "&&" -> "and";
                    case "||" -> "or";
                    // Keep the identity form: s is None becomes s is not None.
                    case "==" -> identity ? "is" : "==";
                    case "!=" -> identity ? "is not" : "!=";
                    // Written as (x & 0xFFFFFFFF) >> n; see createOperatorEdit.
                    case ">>>" -> null;
                    default -> canonicalOperator;
                };
            }

            @Override
            public OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
                if (ctx instanceof PythonParser.ComparisonContext && ctx.getChildCount() == 4) {
                    // "is not" spans two tokens; replace both.
                    String op = toLanguageOperator(toOperator, ctx);
                    if (op == null) return null;
                    Token first = ((TerminalNode) ctx.getChild(1)).getSymbol();
                    Token last = ((TerminalNode) ctx.getChild(2)).getSymbol();
                    return new OperatorEdit(first.getLine(), first.getCharPositionInLine(),
                            sourceCode.substring(first.getStartIndex(), last.getStopIndex() + 1), op);
                }
                boolean fromUnsigned = ">>>".equals(fromOperator);
                if (">>>".equals(toOperator) || (fromUnsigned && ">>".equals(toOperator))) {
                    // Into or out of the masked form: rebuild the expression.
                    String left = getNodeText(ctx.getChild(0));
                    String right = getNodeText(ctx.getChild(2));
                    ParseTree masked = fromUnsigned ? unsignedShiftMask(ctx) : null;
                    // The mask may sit on either side of &; keep the other operand.
                    ParseTree value = masked == null ? null
                            : MSLScannerAdapter.isUnsignedShiftMask(masked.getChild(2).getText())
                                    ? masked.getChild(0) : masked.getChild(2);
                    String replacement = fromUnsigned
                            ? getNodeText(value) + " >> " + right
                            : "(" + left + " & 0xFFFFFFFF) >> " + right;
                    int[] pos = getTokenPosition(ctx);
                    return new OperatorEdit(pos[0], pos[1], getNodeText(ctx), replacement);
                }
                // Otherwise the default token swap: (x & 0xFFFFFFFF) >> n becomes
                // (x & 0xFFFFFFFF) << n.
                return MSLScannerAdapter.super.createOperatorEdit(ctx, fromOperator, toOperator);
            }

            // MSL's "s is null" is a null-or-empty check; Python writes it as
            // s is None or s == "" (or len(s) == 0).
            @Override
            public ParseTree nullCheckSubject(ParseTree ctx) {
                if (!(ctx instanceof PythonParser.Logical_testContext or) || or.op == null
                        || or.op.getType() != PythonLexer.OR) {
                    return null;
                }
                ParseTree subject = comparedWithNone(CstNodes.unwrap(or.getChild(0)));
                if (subject == null) return null;
                String s = subject.getText();
                ParseTree right = CstNodes.unwrap(or.getChild(2));
                if (!isComparison(right, "==")) return null;
                String left = right.getChild(0).getText();
                String value = right.getChild(2).getText();
                boolean emptyString = left.equals(s) && (value.equals("\"\"") || value.equals("''"));
                boolean zeroLength = left.equals("len(" + s + ")") && value.equals("0");
                return emptyString || zeroLength ? subject : null;
            }

            /** s for "s is None" where s is a name or dotted path, else null. */
            private ParseTree comparedWithNone(ParseTree node) {
                if (!(node instanceof PythonParser.ComparisonContext c) || c.getChildCount() != 3
                        || !(c.getChild(1) instanceof TerminalNode is) || is.getSymbol().getType() != PythonLexer.IS
                        || !c.getChild(2).getText().equals("None")
                        || !CstNodes.isDottedName(c.getChild(0))) {
                    return null;
                }
                return c.getChild(0);
            }

            private boolean isComparison(ParseTree node, String op) {
                return node instanceof PythonParser.ComparisonContext && node.getChildCount() == 3
                        && node.getChild(1).getText().equals(op);
            }

            @Override
            public String notNullCheck(String subject) {
                return "(" + subject + " is not None)";
            }

            /** Python statements end at the line, without a ';'. */
            @Override
            public String renderStatement(MSLTree tree, MSLBindings.Cursor bindings, ParseTree ctx) {
                if (tree instanceof MSLNode node && node.kind().equals(MSLNode.ASSIGN)) {
                    String target = render(node.child(0), bindings, ctx);
                    String value = render(node.child(1), bindings, ctx);
                    return target == null || value == null ? null : target + " = " + value;
                }
                return render(tree, bindings, ctx);
            }

            @Override
            public String renderLiteral(String canonical, ParseTree ctx) {
                return switch (canonical) {
                    case "true" -> "True";
                    case "false" -> "False";
                    case "null" -> "None";
                    default -> canonical;
                };
            }

            // ── Statements, for Except: trees ────────────────────────────

            @Override
            public List<ParseTree> matchNode(MSLNode pattern, ParseTree ctx) {
                switch (pattern.kind()) {
                    case MSLNode.IF -> {
                        if (ctx instanceof PythonParser.If_stmtContext s) return List.of(s.cond, s.suite());
                        if (ctx instanceof PythonParser.Elif_clauseContext s) return List.of(s.test(), s.suite());
                        return null;
                    }
                    case MSLNode.WHILE -> {
                        return ctx instanceof PythonParser.While_stmtContext s ? List.of(s.test(), s.suite()) : null;
                    }
                    case MSLNode.ASSIGN -> {
                        PythonParser.Expr_stmtContext stmt = singleExpressionStatement(ctx);
                        if (stmt == null || stmt.assign_part() == null) return null;
                        PythonParser.Assign_partContext assign = stmt.assign_part();
                        if (assign.ASSIGN().size() != 1 || assign.testlist_star_expr().size() != 1) return null;
                        return List.of(stmt.testlist_star_expr(), assign.testlist_star_expr(0));
                    }
                    case MSLNode.BLOCK -> {
                        // An indented suite is Python's block; its statements are its stmt children.
                        if (!(ctx instanceof PythonParser.SuiteContext suite) || suite.INDENT() == null
                                || suite.stmt().size() < pattern.children().size()) {
                            return null;
                        }
                        return new ArrayList<>(suite.stmt().subList(0, pattern.children().size()));
                    }
                    default -> {
                        return MSLScannerAdapter.super.matchNode(pattern, ctx);
                    }
                }
            }

            /**
             * The expression statement when ctx is one written on its own: a
             * small statement, a one-statement line, or a body on the same
             * line as its "if x:" (an indented body is a block).
             */
            private PythonParser.Expr_stmtContext singleExpressionStatement(ParseTree ctx) {
                ParseTree p = ctx;
                if (p instanceof PythonParser.SuiteContext suite && suite.simple_stmt() != null) p = suite.simple_stmt();
                if (p instanceof PythonParser.StmtContext stmt && stmt.simple_stmt() != null) p = stmt.simple_stmt();
                if (p instanceof PythonParser.Simple_stmtContext simple && simple.small_stmt().size() == 1) {
                    p = simple.small_stmt(0);
                }
                return p instanceof PythonParser.Expr_stmtContext e ? e : null;
            }

            // ── Increments ───────────────────────────────────────────────

            /** x += 1 and x -= 1, Python's increment and decrement. */
            @Override
            public ParseTree incrementOperand(String operator, ParseTree ctx) {
                if (!(ctx instanceof PythonParser.Expr_stmtContext stmt) || stmt.assign_part() == null
                        || stmt.assign_part().op == null || stmt.assign_part().testlist() == null) {
                    return null;
                }
                int expected = operator.equals("++") ? PythonParser.ADD_ASSIGN
                        : operator.equals("--") ? PythonParser.SUB_ASSIGN : -1;
                return stmt.assign_part().op.getType() == expected
                        && stmt.assign_part().testlist().getText().equals("1") ? stmt.testlist_star_expr() : null;
            }

            @Override
            public String renderIncrement(String operator, String operand, ParseTree ctx) {
                return operand + (operator.equals("++") ? " += 1" : " -= 1");
            }

            @Override
            public ParseTree expressionStatement(ParseTree ctx) {
                // x += 1 is a statement itself; foo() is an expression inside one.
                if (ctx instanceof PythonParser.Expr_stmtContext stmt) return stmt;
                ParseTree p = ctx;
                while (p.getParent() != null && p.getParent().getChildCount() == 1) p = p.getParent();
                return p.getParent() instanceof PythonParser.Expr_stmtContext stmt && stmt.assign_part() == null
                        ? stmt : null;
            }

            @Override
            public java.util.Set<String> supportedKinds() {
                return java.util.Set.of("binary", "predicate", "literal", "identifier", "call",
                        "if", "while", "assign", "block", "increment");
            }

            @Override
            public java.util.Set<String> supportedPlaceholders() {
                return java.util.Set.of("quantity", "match", "statement", "invocation_statement",
                        "return_value", "constructor_call", "non_void_method_call",
                        "equality_condition", "order_condition");
            }

            // ── PIT-style placeholders ───────────────────────────────────

            @Override
            public boolean matchesPlaceholder(String type, ParseTree ctx) {
                return switch (type) {
                    case "statement" -> ctx instanceof PythonParser.StmtContext || ctx instanceof PythonParser.SuiteContext
                            || ctx instanceof PythonParser.Simple_stmtContext || ctx instanceof PythonParser.Compound_stmtContext;
                    case "return_value" -> ctx instanceof PythonParser.Return_stmtContext r && r.testlist() != null
                            && isMainSource(r);
                    case "constructor_call" -> constructorCallEnd(ctx) != null;
                    case "non_void_method_call" -> nonVoidCallEnd(ctx) != null;
                    case "equality_condition" -> isCondition(ctx) && CstNodes.anyTokenContains(ctx, "==", "!=");
                    case "order_condition" -> isCondition(ctx) && CstNodes.anyTokenContains(ctx, "<", ">");
                    default -> MSLScannerAdapter.super.matchesPlaceholder(type, ctx);
                };
            }

            /** The test of an if, elif or while. */
            private boolean isCondition(ParseTree ctx) {
                ParseTree parent = ctx.getParent();
                return ctx instanceof PythonParser.TestContext && isMainSource(ctx)
                        && ((parent instanceof PythonParser.If_stmtContext s && s.cond == ctx)
                            || (parent instanceof PythonParser.Elif_clauseContext e && e.test() == ctx)
                            || (parent instanceof PythonParser.While_stmtContext w && w.test() == ctx));
            }

            /** Nodes of the file itself, not of an f-string field parsed on its own. */
            private boolean isMainSource(ParseTree ctx) {
                Token start = CstNodes.start(ctx);
                return start != null && snippetFileOffset.get(start.getInputStream()) == null;
            }

            /**
             * A call to a capitalised name, the Python convention for a class,
             * with any module prefix: Thing(), threading.Lock(). Not a
             * decorator, and not on a class header line. Returns the call's
             * closing parenthesis, or null.
             */
            private Token constructorCallEnd(ParseTree ctx) {
                List<TerminalNode> callee = calleeNames(ctx);
                if (callee == null) return null;
                String name = callee.get(callee.size() - 1).getText();
                if (name.isEmpty() || name.charAt(0) < 'A' || name.charAt(0) > 'Z') return null;
                PythonParser.ClassdefContext classdef = CstNodes.ancestor(ctx, PythonParser.ClassdefContext.class);
                if (classdef != null && classdef.CLASS().getSymbol().getLine() == callee.get(0).getSymbol().getLine()) {
                    return null;
                }
                return callArgumentsEnd((PythonParser.ExprContext) ctx);
            }

            /**
             * The leading names of an "atom trailer*" expression whose first
             * call is on a dotted name (f(...), a.b.f(...)); null otherwise.
             */
            private List<TerminalNode> calleeNames(ParseTree ctx) {
                if (!(ctx instanceof PythonParser.ExprContext expr) || expr.atom() == null || !isMainSource(ctx)) {
                    return null;
                }
                TerminalNode first = CstNodes.singleToken(expr.atom());
                if (first == null || !CstNodes.isName(first.getText())) return null;
                List<TerminalNode> names = new ArrayList<>(List.of(first));
                for (PythonParser.TrailerContext trailer : expr.trailer()) {
                    boolean call = trailer.arguments() != null && trailer.arguments().OPEN_PAREN() != null;
                    if (trailer.arguments() != null && !call) return null;   // a[i]
                    if (trailer.DOT() != null) {
                        TerminalNode name = CstNodes.singleToken(trailer.name());
                        if (name == null) return null;
                        names.add(name);
                    }
                    // a.f(...) or f(...): the call ends the callee.
                    if (call) return names;
                }
                return null;
            }

            /** f(a, b) or m.f(a, b) as a whole expression: its plain arguments. */
            @Override
            public List<ParseTree> callArguments(ParseTree ctx, String functionName) {
                List<TerminalNode> callee = calleeNames(ctx);
                if (callee == null) return null;
                StringBuilder name = new StringBuilder();
                for (TerminalNode part : callee) name.append(name.length() == 0 ? "" : ".").append(part.getText());
                PythonParser.ExprContext expr = (PythonParser.ExprContext) ctx;
                PythonParser.ArgumentsContext call = expr.trailer(expr.trailer().size() - 1).arguments();
                if (!name.toString().equals(functionName) || call == null || call.OPEN_PAREN() == null
                        || expr.AWAIT() != null) {
                    return null;
                }
                // The call must be the expression's last trailer.
                int calls = 0;
                for (PythonParser.TrailerContext trailer : expr.trailer()) {
                    if (trailer.arguments() != null) calls++;
                }
                if (calls != 1) return null;
                List<ParseTree> args = new ArrayList<>();
                if (call.arglist() != null) {
                    for (PythonParser.ArgumentContext arg : call.arglist().argument()) {
                        if (arg.getChildCount() != 1) return null;   // keyword, *args or a generator
                        args.add(arg.test(0));
                    }
                }
                return args;
            }

            /** The ')' of the call made by the first call trailer of expr. */
            private Token callArgumentsEnd(PythonParser.ExprContext expr) {
                for (PythonParser.TrailerContext trailer : expr.trailer()) {
                    if (trailer.arguments() != null && trailer.arguments().OPEN_PAREN() != null) {
                        return trailer.arguments().CLOSE_PAREN().getSymbol();
                    }
                }
                return null;
            }

            /**
             * A call whose result is used: the value of an assignment, keyword
             * argument or default (v = f(x)), or what a return statement starts
             * with (return f(x) + 1). Returns the end of the replaced call.
             */
            private Token nonVoidCallEnd(ParseTree ctx) {
                List<TerminalNode> callee = calleeNames(ctx);
                if (callee == null) return null;
                Token first = callee.get(0).getSymbol();
                Token previous = previousToken(first);
                if (previous != null && previous.getType() == PythonLexer.ASSIGN && previous.getLine() == first.getLine()) {
                    // The call itself, to its matching ')' on the same line.
                    Token end = callArgumentsEnd((PythonParser.ExprContext) ctx);
                    return end != null && end.getLine() == first.getLine() ? end : null;
                }
                char initial = first.getText().charAt(0);
                if (previous != null && previous.getType() == PythonLexer.RETURN
                        && (Character.isLowerCase(initial) || initial == '_')) {
                    // The callee must touch its '(' and the call runs to the line's last ')'.
                    Token open = nextToken(callee.get(callee.size() - 1).getSymbol());
                    if (open == null || open.getType() != PythonLexer.OPEN_PAREN
                            || open.getStartIndex() != callee.get(callee.size() - 1).getSymbol().getStopIndex() + 1) {
                        return null;
                    }
                    Token last = null;
                    for (int i = open.getTokenIndex(); i < tokens.size(); i++) {
                        Token t = tokens.get(i);
                        if (t.getLine() != first.getLine() || t.getType() == PythonLexer.SEMI_COLON
                                || t.getType() == Token.EOF) break;
                        if (t.getType() == PythonLexer.CLOSE_PAREN) last = t;
                    }
                    return last;
                }
                return null;
            }

            private Token previousToken(Token t) {
                for (int i = t.getTokenIndex() - 1; i >= 0; i--) {
                    Token p = tokens.get(i);
                    if (p.getChannel() == Token.DEFAULT_CHANNEL) return p;
                }
                return null;
            }

            private Token nextToken(Token t) {
                for (int i = t.getTokenIndex() + 1; i < tokens.size(); i++) {
                    Token n = tokens.get(i);
                    if (n.getChannel() == Token.DEFAULT_CHANNEL) return n;
                }
                return null;
            }

            /**
             * A call or a return value is replaced from where it starts, which
             * for a call is its callee rather than an "await" before it, to the
             * end found when it matched.
             */
            @Override
            public OperatorEdit replace(ParseTree ctx, MSLTree from, MSLTree to, MSLBindings bindings) {
                if (from instanceof MSLPlaceholder p) {
                    Token start = null;
                    Token end = null;
                    String replacement = null;
                    switch (p.type()) {
                        case "constructor_call", "non_void_method_call" -> {
                            start = calleeNames(ctx).get(0).getSymbol();
                            end = p.type().equals("constructor_call") ? constructorCallEnd(ctx) : nonVoidCallEnd(ctx);
                            replacement = render(to, bindings.cursor(), ctx);
                        }
                        case "return_value" -> {
                            Object[] value = emptyReturn((PythonParser.Return_stmtContext) ctx);
                            start = (Token) value[0];
                            end = (Token) value[1];
                            replacement = (String) value[2];
                        }
                        default -> {
                        }
                    }
                    if (start != null) {
                        if (end == null || replacement == null) return null;
                        return new OperatorEdit(start.getLine(), start.getCharPositionInLine(),
                                sourceCode.substring(start.getStartIndex(), end.getStopIndex() + 1), replacement);
                    }
                }
                return MSLScannerAdapter.super.replace(ctx, from, to, bindings);
            }

            /**
             * {start, end, replacement} for a return's empty value. A returned
             * string, list, dict or tuple display that starts the value becomes
             * "", [], {} or (); anything else is judged as a whole: False for
             * booleans, 0 for numbers, "" for strings, otherwise None.
             */
            private Object[] emptyReturn(PythonParser.Return_stmtContext ret) {
                List<Token> value = CstNodes.tokens(ret.testlist());
                Token first = value.get(0);
                Token last = value.get(value.size() - 1);
                String text = first.getText();
                if (first.getType() == PythonLexer.STRING && (text.startsWith("\"") || text.startsWith("'"))
                        && !text.equals("\"\"") && !text.equals("''")) {
                    return new Object[]{first, first, "\"\""};
                }
                int open = first.getType() == PythonLexer.OPEN_BRACKET ? PythonLexer.CLOSE_BRACKET
                        : first.getType() == PythonLexer.OPEN_BRACE ? PythonLexer.CLOSE_BRACE : -1;
                if (open >= 0) {
                    // To the first closing bracket on the line, around something.
                    for (int i = 1; i < value.size(); i++) {
                        Token t = value.get(i);
                        if (t.getLine() != first.getLine()) break;
                        if (t.getType() == open) {
                            if (i == 1) break;
                            return new Object[]{first, t, open == PythonLexer.CLOSE_BRACKET ? "[]" : "{}"};
                        }
                    }
                }
                if (first.getType() == PythonLexer.OPEN_PAREN) {
                    // A one-line tuple (a, b) without nested parentheses.
                    boolean comma = false;
                    for (int i = 1; i < value.size(); i++) {
                        Token t = value.get(i);
                        if (t.getLine() != first.getLine() || t.getType() == PythonLexer.OPEN_PAREN) break;
                        if (t.getType() == PythonLexer.COMMA) comma = true;
                        if (t.getType() == PythonLexer.CLOSE_PAREN) {
                            if (comma) return new Object[]{first, t, "()"};
                            break;
                        }
                    }
                }
                String whole = sourceCode.substring(first.getStartIndex(), last.getStopIndex() + 1);
                String replacement = TypedEmptyValues.forExpression(whole, "None", "False");
                return new Object[]{first, last, replacement};
            }

            @Override
            public String getEmptyStatement(ParseTree statement) {
                return "pass";
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                return ctx.getChild(0);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                return ctx.getChild(ctx.getChildCount() - 1);
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                if (ctx.getChildCount() >= 3) return ctx.getChild(1);
                return null;
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                return MSLScannerAdapter.isQuantityText(getNodeText(ctx), ctx instanceof TerminalNode);
            }

            @Override
            public boolean isStringLiteral(ParseTree ctx) {
                return ctx != null && MSLScannerAdapter.isStringLiteral(getNodeText(ctx).strip(), true);
            }

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                if (!(ctx instanceof PythonParser.Expr_stmtContext exprStmt)) {
                    return false;
                }
                // Assignments are not invocation statements
                if (exprStmt.assign_part() != null) {
                    return false;
                }
                // Skip multi-line calls
                if (exprStmt.getStart() != null && exprStmt.getStop() != null
                        && exprStmt.getStart().getLine() != exprStmt.getStop().getLine()) {
                    return false;
                }
                // [await] name(.name)* ( ... ) - it starts with a call and ends with ')'.
                List<Token> t = CstNodes.tokens(ctx);
                int i = !t.isEmpty() && t.get(0).getType() == PythonLexer.AWAIT ? 1 : 0;
                if (i >= t.size() || !CstNodes.isName(t.get(i).getText())) return false;
                i++;
                while (i + 1 < t.size() && t.get(i).getType() == PythonLexer.DOT && CstNodes.isName(t.get(i + 1).getText())) {
                    i += 2;
                }
                return i < t.size() && t.get(i).getType() == PythonLexer.OPEN_PAREN
                        && t.get(t.size() - 1).getType() == PythonLexer.CLOSE_PAREN;
            }

            @Override
            public String renderValue(String placeholderType, ParseTree ctx) {
                return placeholderType.equals("default_value") ? "None" : null;
            }

            @Override
            public String getNodeText(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null
                            && start.getStartIndex() >= 0
                            && stop.getStopIndex() >= start.getStartIndex()) {
                        Integer snippetOffset = snippetFileOffset.get(start.getInputStream());
                        if (snippetOffset != null) {
                            int from = snippetOffset + start.getStartIndex();
                            int to = snippetOffset + stop.getStopIndex() + 1;
                            if (from >= 0 && to <= sourceCode.length() && to >= from) {
                                return sourceCode.substring(from, to);
                            }
                        } else if (stop.getStopIndex() < sourceCode.length()) {
                            return sourceCode.substring(start.getStartIndex(), stop.getStopIndex() + 1);
                        }
                    }
                }
                return ctx.getText();
            }

            @Override
            public int[] getTokenPosition(ParseTree ctx) {
                Token s = null;
                if (ctx instanceof TerminalNode tn) {
                    s = tn.getSymbol();
                } else if (ctx instanceof ParserRuleContext prc) {
                    s = prc.getStart();
                }
                if (s == null) {
                    return new int[]{0, 0};
                }
                Integer snippetOffset = snippetFileOffset.get(s.getInputStream());
                if (snippetOffset != null) {
                    return lineAndColumn(sourceCode, snippetOffset + s.getStartIndex());
                }
                return new int[]{s.getLine(), s.getCharPositionInLine()};
            }

            @Override
            public ParseTree getRootContext() {
                return root;
            }
        };
    }

    /**
     * f-string bodies are a single STRING token. Parse each {@code {expr}} so
     * shared MSL rules can mutate the executed expression, with positions
     * mapped back into the original file.
     */
    private static ParseTree treeWithFStringFields(
            String sourceCode,
            CommonTokenStream tokens,
            ParseTree mainTree,
            Map<org.antlr.v4.runtime.CharStream, Integer> snippetFileOffset) {
        List<ParseTree> extras = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (token.getType() != PythonLexer.STRING) {
                continue;
            }
            for (PythonFStringInterpolations.Field field
                    : PythonFStringInterpolations.fieldsInToken(token.getText(), token.getStartIndex())) {
                ParseTree snippet = parsePythonSnippet(field.expression(), field.fileOffset(), snippetFileOffset);
                if (snippet != null) {
                    extras.add(snippet);
                }
            }
        }
        if (extras.isEmpty()) {
            return mainTree;
        }
        ParserRuleContext composite = new ParserRuleContext();
        attachChild(composite, mainTree);
        for (ParseTree extra : extras) {
            attachChild(composite, extra);
        }
        return composite;
    }

    private static ParseTree parsePythonSnippet(
            String expression,
            int fileOffset,
            Map<org.antlr.v4.runtime.CharStream, Integer> snippetFileOffset) {
        try {
            CharStream chars = CstNodes.utf16(expression);
            PythonLexer lexer = new PythonLexer(chars);
            lexer.removeErrorListeners();
            CommonTokenStream snippetTokens = new CommonTokenStream(lexer);
            PythonParser snippetParser = new PythonParser(snippetTokens);
            snippetParser.removeErrorListeners();
            ParseTree tree = snippetParser.eval_input();
            if (snippetParser.getNumberOfSyntaxErrors() > 0 || tree == null) {
                return null;
            }
            snippetFileOffset.put(chars, fileOffset);
            return tree;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void attachChild(ParserRuleContext parent, ParseTree child) {
        if (child instanceof ParserRuleContext rule) {
            parent.addChild(rule);
        }
    }

    private static int[] lineAndColumn(String source, int offset) {
        int line = 1;
        int column = 0;
        int n = Math.min(offset, source.length());
        for (int i = 0; i < n; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                column = 0;
            } else {
                column++;
            }
        }
        return new int[]{line, column};
    }
}
