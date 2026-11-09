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
                return render(tree, bindings, ctx);
            }

            @Override
            public String renderUnary(String operator, String operand, ParseTree ctx) {
                return operator.equals("!") ? "not " + operand : operator + operand;
            }

            /** Python statements: colon and indented body, pass for empty, no C-style for. */
            @Override
            public String renderStatementNode(MSLNode node, MSLBindings.Cursor bindings, ParseTree ctx) {
                switch (node.kind()) {
                    case MSLNode.IF, MSLNode.WHILE -> {
                        String condition = render(node.child(0), bindings, ctx);
                        String body = suite(node.child(1), bindings, ctx);
                        if (condition == null || body == null) return null;
                        String head = (node.kind().equals(MSLNode.IF) ? "if " : "while ") + condition + ":" + body;
                        if (node.children().size() < 3) return head;
                        String otherwise = suite(node.child(2), bindings, ctx);
                        return otherwise == null ? null : head + "\nelse:" + otherwise;
                    }
                    case MSLNode.BLOCK -> {
                        List<String> statements = new ArrayList<>();
                        for (MSLTree child : node.children()) {
                            String text = renderStatement(child, bindings, ctx);
                            if (text == null) return null;
                            statements.add(text);
                        }
                        return statements.isEmpty() ? "pass" : String.join("\n", statements);
                    }
                    case MSLNode.FOR, MSLNode.DO_WHILE, MSLNode.CASE, MSLNode.DEFAULT_CASE -> {
                        return null;
                    }
                    default -> {
                        return MSLScannerAdapter.super.renderStatementNode(node, bindings, ctx);
                    }
                }
            }

            /** ":" then the statement on its own lines, indented one level. */
            private String suite(MSLTree statement, MSLBindings.Cursor bindings, ParseTree ctx) {
                String text = renderStatement(statement, bindings, ctx);
                return text == null ? null : "\n    " + text.replace("\n", "\n    ");
            }

            /** Matched text with its own indentation removed, for re-indenting. */
            @Override
            public String boundText(ParseTree node) {
                String text = getNodeText(node);
                if (text.indexOf('\n') < 0) return text;
                List<String> lines = mutationtester.msl.CstNodes.lines(text.strip());
                int indent = Integer.MAX_VALUE;
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    if (line.isBlank()) continue;
                    int n = 0;
                    while (n < line.length() && line.charAt(n) == ' ') n++;
                    indent = Math.min(indent, n);
                }
                // The first line starts at the node's column; later lines are
                // indented relative to that.
                int base = Math.min(indent, Math.max(getTokenPosition(node)[1], 0));
                StringBuilder out = new StringBuilder(lines.get(0).strip());
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i);
                    out.append('\n').append(line.length() >= base ? line.substring(base) : line.strip());
                }
                return out.toString();
            }

            @Override
            public String placeLines(String text, ParseTree ctx) {
                return text.replace("\n", "\n" + getIndentation(ctx));
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
                        return matchIf(pattern, ctx, false);
                    }
                    case MSLNode.WHILE -> {
                        if (!(ctx instanceof PythonParser.While_stmtContext s)) return null;
                        if (pattern.children().size() == 3) {
                            return s.else_clause() == null ? null : List.of(s.test(), s.suite(), s.else_clause().suite());
                        }
                        return List.of(s.test(), s.suite());
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
                    case MSLNode.UNARY -> {
                        if (pattern.value().equals("!")) {
                            return ctx instanceof PythonParser.Logical_testContext t && t.NOT() != null
                                    && t.logical_test().size() == 1 ? List.of(t.logical_test(0)) : null;
                        }
                        if (!(ctx instanceof PythonParser.ExprContext e) || e.op == null || e.expr().size() != 1
                                || e.getChildCount() != 2) {
                            return null;
                        }
                        String op = e.op.getType() == PythonParser.MINUS ? "-" : e.op.getType() == PythonParser.NOT_OP ? "~" : "";
                        return op.equals(pattern.value()) ? List.of(e.expr(0)) : null;
                    }
                    case MSLNode.CONDITIONAL -> {
                        return ctx instanceof PythonParser.TestContext t && t.IF() != null
                                ? List.of(t.logical_test(1), t.logical_test(0), t.test()) : null;
                    }
                    case MSLNode.COMPOUND_ASSIGN -> {
                        PythonParser.Expr_stmtContext stmt = singleExpressionStatement(ctx);
                        if (stmt == null || stmt.assign_part() == null || stmt.assign_part().op == null
                                || stmt.assign_part().testlist() == null
                                || !stmt.assign_part().op.getText().equals(pattern.value())) {
                            return null;
                        }
                        return List.of(stmt.testlist_star_expr(), stmt.assign_part().testlist());
                    }
                    case MSLNode.INDEX -> {
                        PythonParser.TrailerContext last = lastTrailer(ctx);
                        if (last == null || last.arguments() == null || last.arguments().OPEN_BRACKET() == null
                                || last.arguments().subscriptlist().subscript().size() != 1) {
                            return null;
                        }
                        PythonParser.SubscriptContext sub = last.arguments().subscriptlist().subscript(0);
                        if (sub.getChildCount() != 1 || sub.test().size() != 1) return null;
                        return List.of(receiver(ctx), sub.test(0));
                    }
                    case MSLNode.MEMBER -> {
                        PythonParser.TrailerContext last = lastTrailer(ctx);
                        return last != null && last.DOT() != null && last.arguments() == null
                                ? List.of(receiver(ctx), last.name()) : null;
                    }
                    case MSLNode.MEMBER_CALL -> {
                        PythonParser.TrailerContext last = lastTrailer(ctx);
                        if (last == null || last.DOT() == null || last.arguments() == null
                                || last.arguments().OPEN_PAREN() == null) {
                            return null;
                        }
                        List<ParseTree> parts = new ArrayList<>(List.of(receiver(ctx), last.name()));
                        parts.addAll(callArgumentList(last.arguments()));
                        return parts;
                    }
                    case MSLNode.RETURN -> {
                        if (!(smallStatement(ctx) instanceof PythonParser.Return_stmtContext r)) return null;
                        return r.testlist() == null ? List.of() : List.of(r.testlist());
                    }
                    case MSLNode.BREAK -> {
                        return smallStatement(ctx) instanceof PythonParser.Break_stmtContext ? List.of() : null;
                    }
                    case MSLNode.CONTINUE -> {
                        return smallStatement(ctx) instanceof PythonParser.Continue_stmtContext ? List.of() : null;
                    }
                    case MSLNode.THROW -> {
                        return smallStatement(ctx) instanceof PythonParser.Raise_stmtContext r && !r.test().isEmpty()
                                ? List.of(r.test(0)) : null;
                    }
                    case MSLNode.FOR_EACH -> {
                        if (!(ctx instanceof PythonParser.For_stmtContext f)) return null;
                        if (pattern.children().size() == 4) {
                            return f.else_clause() == null ? null
                                    : List.of(f.exprlist(), f.testlist(), f.suite(), f.else_clause().suite());
                        }
                        return List.of(f.exprlist(), f.testlist(), f.suite());
                    }
                    case MSLNode.TRY -> {
                        if (!(ctx instanceof PythonParser.Try_stmtContext t)) return null;
                        return java.util.Arrays.asList(t.suite(),
                                t.except_clause().isEmpty() ? null : t.except_clause(0).suite(),
                                t.finally_clause() == null ? null : t.finally_clause().suite());
                    }
                    case MSLNode.DECLARE -> {
                        // A first assignment is a Python variable's declaration.
                        PythonParser.Expr_stmtContext stmt = singleExpressionStatement(ctx);
                        if (stmt == null || stmt.assign_part() == null) return null;
                        PythonParser.Assign_partContext assign = stmt.assign_part();
                        if (assign.ASSIGN().size() != 1 || assign.testlist_star_expr().size() != 1
                                || !mutationtester.msl.CstNodes.isName(stmt.testlist_star_expr().getText())) {
                            return null;
                        }
                        return List.of(stmt.testlist_star_expr(), assign.testlist_star_expr(0));
                    }
                    case MSLNode.COLLECTION -> {
                        if (!(ctx instanceof PythonParser.AtomContext a) || a.OPEN_BRACKET() == null) return null;
                        return a.testlist_comp() == null ? new ArrayList<>() : listElements(a.testlist_comp());
                    }
                    case MSLNode.OBJECT -> {
                        if (!(ctx instanceof PythonParser.AtomContext a) || a.OPEN_BRACE() == null) return null;
                        if (a.dictorsetmaker() == null) return new ArrayList<>();
                        return dictEntries(a.dictorsetmaker());
                    }
                    case MSLNode.ENTRY -> {
                        return mutationtester.msl.CstNodes.isSpan(ctx) && ctx.getChildCount() == 3
                                && ctx.getChild(1).getText().equals(":") ? List.of(ctx.getChild(0), ctx.getChild(2)) : null;
                    }
                    case MSLNode.LAMBDA -> {
                        if (!(ctx instanceof PythonParser.TestContext t) || t.LAMBDA() == null) return null;
                        List<ParseTree> parts = new ArrayList<>();
                        if (t.varargslist() != null) {
                            for (PythonParser.Vardef_parametersContext list : t.varargslist().vardef_parameters()) {
                                parts.addAll(list.vardef_parameter());
                            }
                        }
                        parts.add(t.test());
                        return parts;
                    }
                    case MSLNode.FUNCTION -> {
                        if (!(ctx instanceof PythonParser.FuncdefContext f)) return null;
                        List<ParseTree> parts = new ArrayList<>(List.of(f.name()));
                        if (f.typedargslist() != null) {
                            for (PythonParser.Def_parametersContext list : f.typedargslist().def_parameters()) {
                                parts.addAll(list.def_parameter());
                            }
                        }
                        parts.add(f.suite());
                        return parts;
                    }
                    case MSLNode.PARAMETER -> {
                        if (ctx instanceof PythonParser.Def_parameterContext d && d.named_parameter() != null) {
                            return d.test() == null ? List.of(d.named_parameter().name())
                                    : List.of(d.named_parameter().name(), d.test());
                        }
                        if (ctx instanceof PythonParser.Vardef_parameterContext v && v.name() != null) {
                            return v.test() == null ? List.of(v.name()) : List.of(v.name(), v.test());
                        }
                        return null;
                    }
                    case MSLNode.TYPE_CHECK -> {
                        List<ParseTree> args = callArguments(ctx, "isinstance");
                        return args != null && args.size() == 2 ? args : null;
                    }
                    case MSLNode.FIELD -> {
                        // An assignment directly in a class body.
                        PythonParser.Expr_stmtContext stmt = singleExpressionStatement(ctx);
                        if (stmt == null || stmt.assign_part() == null || !isClassLevel(stmt)) return null;
                        PythonParser.Assign_partContext assign = stmt.assign_part();
                        if (assign.ASSIGN().size() != 1 || assign.testlist_star_expr().size() != 1) return null;
                        return List.of(stmt.testlist_star_expr(), assign.testlist_star_expr(0));
                    }
                    case MSLNode.ASSERT -> {
                        return smallStatement(ctx) instanceof PythonParser.Assert_stmtContext a ? List.of(a.test(0)) : null;
                    }
                    case MSLNode.WITH -> {
                        return ctx instanceof PythonParser.With_stmtContext w && w.with_item().size() == 1
                                ? List.of(w.with_item(0), w.suite()) : null;
                    }
                    case MSLNode.YIELD -> {
                        ParseTree s = smallStatement(ctx);
                        PythonParser.Yield_exprContext y = s instanceof PythonParser.Yield_stmtContext ys ? ys.yield_expr()
                                : ctx instanceof PythonParser.Yield_exprContext ye ? ye : null;
                        if (y == null) return null;
                        return y.yield_arg() == null ? List.of() : List.of(y.yield_arg());
                    }
                    case MSLNode.MEMBERSHIP -> {
                        if (!(ctx instanceof PythonParser.ComparisonContext c) || c.IN() == null || c.comparison().size() != 2) return null;
                        boolean negated = c.optional != null;
                        return negated == pattern.value().equals("not in") ? List.of(c.comparison(0), c.comparison(1)) : null;
                    }
                    case MSLNode.SLICE -> {
                        PythonParser.TrailerContext last = lastTrailer(ctx);
                        if (last == null || last.arguments() == null || last.arguments().OPEN_BRACKET() == null
                                || last.arguments().subscriptlist().subscript().size() != 1) {
                            return null;
                        }
                        PythonParser.SubscriptContext sub = last.arguments().subscriptlist().subscript(0);
                        if (sub.COLON() == null || sub.sliceop() != null) return null;
                        ParseTree from = null;
                        ParseTree to = null;
                        boolean afterColon = false;
                        for (int i = 0; i < sub.getChildCount(); i++) {
                            if (sub.getChild(i) == sub.COLON()) afterColon = true;
                            else if (afterColon) to = sub.getChild(i);
                            else from = sub.getChild(i);
                        }
                        return java.util.Arrays.asList(receiver(ctx), from, to);
                    }
                    case MSLNode.TUPLE -> {
                        // a, b on either side of an assignment
                        if (!(ctx instanceof PythonParser.Testlist_star_exprContext || ctx instanceof PythonParser.TestlistContext)
                                || ctx.getChildCount() < 3) {
                            return null;
                        }
                        List<ParseTree> elements = new ArrayList<>();
                        for (int i = 0; i < ctx.getChildCount(); i++) {
                            if (!(ctx.getChild(i) instanceof TerminalNode)) elements.add(ctx.getChild(i));
                        }
                        return elements;
                    }
                    case MSLNode.SPREAD -> {
                        if (ctx instanceof PythonParser.Star_exprContext s) return List.of(s.expr());
                        return ctx instanceof PythonParser.ArgumentContext a && a.STAR() != null ? List.of(a.test(0)) : null;
                    }
                    case MSLNode.DELETE -> {
                        return smallStatement(ctx) instanceof PythonParser.Del_stmtContext d ? List.of(d.exprlist()) : null;
                    }
                    case MSLNode.ANNOTATION -> {
                        return ctx instanceof PythonParser.DecoratorContext d && d.arglist() == null && d.OPEN_PAREN() == null
                                ? List.of(d.dotted_name()) : null;
                    }
                    case MSLNode.AWAIT -> {
                        if (!(ctx instanceof PythonParser.ExprContext e) || e.AWAIT() == null) return null;
                        List<ParseTree> parts = new ArrayList<>(List.of(e.atom()));
                        parts.addAll(e.trailer());
                        return List.of(mutationtester.msl.CstNodes.span(parts));
                    }
                    case MSLNode.COMPREHENSION -> {
                        if (!(ctx instanceof PythonParser.AtomContext a) || a.OPEN_BRACKET() == null || a.testlist_comp() == null
                                || a.testlist_comp().comp_for() == null) {
                            return null;
                        }
                        PythonParser.Comp_forContext loop = a.testlist_comp().comp_for();
                        List<ParseTree> parts = new ArrayList<>(List.of(a.testlist_comp().getChild(0), loop.exprlist(),
                                loop.logical_test()));
                        PythonParser.Comp_iterContext rest = loop.comp_iter();
                        if (rest != null && (rest.comp_for() != null || rest.comp_iter() != null)) return null;
                        if (rest != null) parts.add(rest.test());
                        return parts;
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

            /** The last trailer of atom trailer*, for indexing, member access and calls. */
            private PythonParser.TrailerContext lastTrailer(ParseTree ctx) {
                if (!(ctx instanceof PythonParser.ExprContext e) || e.atom() == null || e.trailer().isEmpty()
                        || e.AWAIT() != null) {
                    return null;
                }
                return e.trailer(e.trailer().size() - 1);
            }

            /** Everything before the last trailer: a.b in a.b.c(x), as one node. */
            private ParseTree receiver(ParseTree ctx) {
                PythonParser.ExprContext e = (PythonParser.ExprContext) ctx;
                List<ParseTree> parts = new ArrayList<>(List.of(e.atom()));
                parts.addAll(e.trailer().subList(0, e.trailer().size() - 1));
                return mutationtester.msl.CstNodes.span(parts);
            }

            private List<ParseTree> callArgumentList(PythonParser.ArgumentsContext call) {
                return call.arglist() == null ? List.of() : new ArrayList<>(call.arglist().argument());
            }

            /** f(a, b): [f, a, b], when the call is the last trailer. */
            @Override
            public List<ParseTree> callParts(ParseTree ctx) {
                PythonParser.TrailerContext last = lastTrailer(ctx);
                if (last == null || last.DOT() != null || last.arguments() == null
                        || last.arguments().OPEN_PAREN() == null) {
                    return null;
                }
                List<ParseTree> parts = new ArrayList<>(List.of(receiver(ctx)));
                parts.addAll(callArgumentList(last.arguments()));
                return parts;
            }

            @Override
            public String renderConditional(String condition, String then, String otherwise, ParseTree ctx) {
                return "(" + then + " if " + condition + " else " + otherwise + ")";
            }

            // ── Literals and type names ──────────────────────────────────────

            @Override
            public mutationtester.msl.ValueKind valueKind(ParseTree ctx) {
                return Literals.valueKind(Literals.Lang.PYTHON, ctx);
            }

            @Override
            public String convert(ParseTree node, String fromType, String toType, String toCondition, ParseTree ctx) {
                mutationtester.msl.ValueKind from = valueKind(node);
                if (from == null) return null;
                return Literals.convert(Literals.Lang.PYTHON, getNodeText(node).strip(), from, toType, toCondition);
            }

            // ── Statements ────────────────────────────────────────────────

            /** [condition, body, else]; an elif's else is the next elif or the else suite. */
            @Override
            public List<ParseTree> ifParts(ParseTree ctx) {
                if (ctx instanceof PythonParser.If_stmtContext s) {
                    ParseTree otherwise = !s.elif_clause().isEmpty() ? s.elif_clause(0)
                            : s.else_clause() == null ? null : s.else_clause().suite();
                    return java.util.Arrays.asList(s.cond, s.suite(), otherwise);
                }
                if (ctx instanceof PythonParser.Elif_clauseContext e && e.getParent() instanceof PythonParser.If_stmtContext s) {
                    int i = s.elif_clause().indexOf(e);
                    ParseTree otherwise = i + 1 < s.elif_clause().size() ? s.elif_clause(i + 1)
                            : s.else_clause() == null ? null : s.else_clause().suite();
                    return java.util.Arrays.asList(e.test(), e.suite(), otherwise);
                }
                return null;
            }

            /** The small statement (return, break, raise, ...) a statement line holds, or ctx. */
            private ParseTree smallStatement(ParseTree ctx) {
                ParseTree p = ctx;
                if (p instanceof PythonParser.StmtContext stmt && stmt.simple_stmt() != null) p = stmt.simple_stmt();
                if (p instanceof PythonParser.Simple_stmtContext simple && simple.small_stmt().size() == 1) {
                    p = simple.small_stmt(0);
                }
                return p;
            }

            @Override
            public boolean isBlock(ParseTree ctx) {
                return ctx instanceof PythonParser.SuiteContext suite && suite.INDENT() != null;
            }

            @Override
            public String emptyBlock(ParseTree ctx) {
                return "pass";
            }

            @Override
            public String renderThrow(String value, ParseTree ctx) {
                return "raise " + value;
            }

            @Override
            public String renderDeclaration(String name, String value, ParseTree ctx) {
                return name + " = " + value;
            }

            @Override
            public String renderForEach(String name, String collection, String body, ParseTree ctx) {
                return "for " + name + " in " + collection + ":\n    " + body.replace("\n", "\n    ");
            }

            // ── Collections, functions and types ────────────────────────────

            /** [1, 2]: its elements, unless it is a comprehension. */
            private List<ParseTree> listElements(PythonParser.Testlist_compContext list) {
                if (list.comp_for() != null) return null;
                List<ParseTree> elements = new ArrayList<>();
                for (int i = 0; i < list.getChildCount(); i++) {
                    if (!(list.getChild(i) instanceof TerminalNode)) elements.add(list.getChild(i));
                }
                return elements;
            }

            /** {k: v, ...}: each k: v as one node. */
            private List<ParseTree> dictEntries(PythonParser.DictorsetmakerContext dict) {
                List<ParseTree> entries = new ArrayList<>();
                for (int i = 0; i + 2 < dict.getChildCount(); i++) {
                    if (dict.getChild(i + 1).getText().equals(":")) {
                        entries.add(mutationtester.msl.CstNodes.span(List.of(dict.getChild(i), dict.getChild(i + 1), dict.getChild(i + 2))));
                        i += 2;
                    } else if (!(dict.getChild(i) instanceof TerminalNode)) {
                        return null;   // a set, a comprehension or **unpacking
                    }
                }
                return entries;
            }

            private boolean isClassLevel(ParseTree stmt) {
                ParseTree p = stmt.getParent();
                for (int depth = 0; p != null && depth < 5; depth++, p = p.getParent()) {
                    if (p instanceof PythonParser.ClassdefContext) return true;
                    if (p instanceof PythonParser.FuncdefContext) return false;
                }
                return false;
            }

            @Override
            public boolean isLambda(ParseTree ctx) {
                return ctx instanceof PythonParser.TestContext t && t.LAMBDA() != null;
            }

            /** A type in an annotation, or the class isinstance tests for. */
            @Override
            public boolean isTypeName(ParseTree ctx) {
                if (!isName(ctx)) return false;
                ParseTree child = ctx;
                for (ParseTree p = ctx.getParent(); p != null; child = p, p = p.getParent()) {
                    if (p instanceof PythonParser.Named_parameterContext n) return n.test() == child;
                    if (p instanceof PythonParser.FuncdefContext f) return f.test() == child;
                    if (p instanceof PythonParser.StmtContext) return false;
                    if (p instanceof PythonParser.ArglistContext) {
                        // arglist < arguments < trailer < expr: the isinstance(...) call
                        ParseTree call = p;
                        while (call != null && !(call instanceof PythonParser.ExprContext)) call = call.getParent();
                        List<ParseTree> isinstance = callArguments(call, "isinstance");
                        return isinstance != null && isinstance.size() == 2 && isSameNode(isinstance.get(1), ctx);
                    }
                }
                return false;
            }

            @Override
            public boolean isField(ParseTree ctx) {
                return ctx instanceof PythonParser.Expr_stmtContext stmt && stmt.assign_part() != null && isClassLevel(stmt);
            }

            @Override
            public String emptyCollection(ParseTree ctx) {
                return ctx instanceof PythonParser.AtomContext a && a.OPEN_BRACE() != null ? "{}" : "[]";
            }

            @Override
            public String renderObject(List<String> entries, ParseTree ctx) {
                return "{" + String.join(", ", entries) + "}";
            }

            @Override
            public String renderLambda(List<String> parameters, String body, ParseTree ctx) {
                return parameters.isEmpty() ? "lambda: " + body : "lambda " + String.join(", ", parameters) + ": " + body;
            }

            /** Keeps the annotation: x: int=5. */
            @Override
            public String renderParameter(String name, String defaultValue, ParseTree ctx) {
                String annotation = ctx instanceof PythonParser.Def_parameterContext d && d.named_parameter() != null
                        && d.named_parameter().test() != null ? ": " + getNodeText(d.named_parameter().test()) : "";
                return name + annotation + (defaultValue == null ? "" : "=" + defaultValue);
            }

            @Override
            public String renderTypeCheck(String value, String type, ParseTree ctx) {
                return "isinstance(" + value + ", " + type + ")";
            }

            // ── Remaining statements ────────────────────────────────────────

            @Override
            public String renderKeywordStatement(String kind, String value, ParseTree ctx) {
                return switch (kind) {
                    case "assert" -> "assert " + value;
                    case "delete" -> "del " + value;
                    case "annotation" -> "@" + value;
                    default -> null;
                };
            }

            @Override
            public String renderMembership(String operator, String item, String container, ParseTree ctx) {
                return item + " " + operator + " " + container;
            }

            @Override
            public String renderTuple(List<String> elements, ParseTree ctx) {
                return String.join(", ", elements);
            }

            @Override
            public String renderSpread(String value, ParseTree ctx) {
                return "*" + value;
            }

            @Override
            public String renderSlice(String collection, String from, String to, ParseTree ctx) {
                return collection + "[" + from + ":" + to + "]";
            }

            // ── Language-only ───────────────────────────────────────────────

            @Override
            public String renderComprehension(List<String> parts, ParseTree ctx) {
                return "[" + parts.get(0) + " for " + parts.get(1) + " in " + parts.get(2)
                        + (parts.size() > 3 ? " if " + parts.get(3) : "") + "]";
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
                while (!(p instanceof PythonParser.Expr_stmtContext) && p.getParent() != null
                        && p.getParent().getChildCount() == 1) {
                    p = p.getParent();
                }
                return p instanceof PythonParser.Expr_stmtContext stmt && stmt.assign_part() == null ? stmt : null;
            }

            @Override
            public java.util.Set<String> supportedKinds() {
                return java.util.Set.of("binary", "predicate", "literal", "identifier", "call",
                        "if", "while", "assign", "block", "increment", "return", "break", "continue", "declare", "for_each",
                        "throw", "try",
                        "unary", "conditional", "compound_assign", "index", "member", "member_call",
                        "collection", "object", "entry", "lambda", "function", "parameter", "type_check", "field",
                        "assert", "with", "yield", "membership", "slice", "tuple", "spread", "delete", "annotation", "await",
                        "comprehension");
            }

            @Override
            public java.util.Set<String> supportedPlaceholders() {
                return java.util.Set.of("quantity", "match", "statement", "invocation_statement",
                        "return_value", "constructor_call", "non_void_method_call",
                        "equality_condition", "order_condition",
                        "number", "integer", "float", "exponential", "based_integer", "complex", "string", "bytes",
                        "boolean", "condition", "name", "arguments", "block", "contains", "collection", "lambda", "type", "field");
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
