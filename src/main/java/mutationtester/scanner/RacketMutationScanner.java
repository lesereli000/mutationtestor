package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.ISLBaseVisitor;
import mutationtester.ISLLexer;
import mutationtester.ISLParser;
import mutationtester.QuietAntlrErrorListener;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.CstNodes;
import mutationtester.msl.MSLScannerAdapter;

/**
 * Scans Racket (.rkt) source files for mutation points using the ISL ANTLR
 * grammar (racket-isl/ISL.g4) from the ANTLR grammars-v4 repository.
 * ISL (Intermediate Student Language) is a Racket teaching dialect documented
 * at https://docs.racket-lang.org/htdp-langs/intermediate-lam.html.
 * The grammar is sourced from the official ANTLR grammars-v4 repository and
 * adapted minimally to support standard #lang racket forms used in this project.
 * Its adapter (createAdapter) applies spec rules to Racket code. For the
 * PIT-style placeholders it reuses this scanner's parse-tree visitor, which
 * finds returned values (EMPTY_RETURNS), used call results
 * (NON_VOID_METHOD_CALLS), constructor calls (CONSTRUCTOR_CALLS) and the
 * equality and order tests of if/cond/when/unless (REMOVE_CONDITIONALS_*_IF).
 */
public class RacketMutationScanner {

    // -------------------------------------------------------------------------
    // Visitor
    // -------------------------------------------------------------------------

    private static class ISLMutationVisitor extends ISLBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final String sourceCode;
        private final List<MutationPoint> mutationPoints = new ArrayList<>();

        // Condition heads for REMOVE_CONDITIONALS_* are EQUALITY_TESTS and ORDER_TESTS
        // (outer class), shared with ScannerBackedRule's text fallback.

        ISLMutationVisitor(File sourceFile, Mutator.MutatorType mutationType, String sourceCode) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
            this.sourceCode = sourceCode;
        }

        /**
         * Visits every expression node.
         * For operator mutations: checks if this expr is a paren application
         * '(' expr expr* ')' and if so inspects the head token.
         * For and/or: ISL has explicit grammar rules '(' 'and' expr expr+ ')'
         * so the keyword is a direct terminal child at index 1.
         * For NLT: scans all NUMBER tokens in the expression.
         */
        @Override
        public Void visitExpr(ISLParser.ExprContext ctx) {
            if (isMacroDefinition(ctx)) return null;  // nothing inside a macro is mutated

            // Operator/keyword mutations
            if (ctx.getChildCount() >= 3) {
                ParseTree first = ctx.getChild(0);
                ParseTree second = ctx.getChild(1);

                // Check for '(' as the first child — paren application or special form
                if (first instanceof TerminalNode firstTn
                        && firstTn.getSymbol().getText().equals("(")) {

                    // second child is the head — could be a terminal (operator/keyword)
                    // or a nested expr that resolves to a single terminal
                    Token headToken = extractToken(second);
                    if (headToken != null) {
                        if (mutationType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                                || mutationType == Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF) {
                            handleRemoveConditionals(headToken.getText(), ctx);
                        }

                        if (mutationType == Mutator.MutatorType.NON_VOID_METHOD_CALLS) {
                            checkNonVoidCall(headToken, ctx);
                            markStatementExprs(headToken.getText(), ctx);
                        }

                        if (mutationType == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
                            checkConstructorCall(headToken, ctx);
                        }

                    }
                }
            }

            return visitChildren(ctx);
        }

        /**
         * Extracts a single terminal token from a parse tree node.
         * Handles both direct TerminalNodes and single-child expr nodes.
         */
        private Token extractToken(ParseTree node) {
            if (node instanceof TerminalNode tn) {
                return tn.getSymbol();
            }
            // Single-child expr that wraps a terminal
            if (node instanceof ISLParser.ExprContext exprCtx
                    && exprCtx.getChildCount() == 1
                    && exprCtx.getChild(0) instanceof TerminalNode tn) {
                return tn.getSymbol();
            }
            // name rule wrapping a NAME or SYMBOL token
            if (node instanceof ISLParser.NameContext nameCtx
                    && nameCtx.getChildCount() == 1
                    && nameCtx.getChild(0) instanceof TerminalNode tn) {
                return tn.getSymbol();
            }

            // Fallback: use the start token of any parser rule context
            if (node instanceof ParserRuleContext prc && prc.getStart() != null) {
                return prc.getStart();
            }

            return null;
        }

        /**
         * PIT-style "Remove Conditionals": replaces a whole condition with #t or #f.
         * EQUAL_* variants only fire on conditions that contain an equality check
         * (=, equal?, ...); ORDER_* variants only on conditions that contain an
         * order check (<, >, <=, >=, ...). Covers the condition of
         * (if c t f), each non-else clause test of (cond [c e] ...), and the
         * guard of (when c ...) / (unless c ...).
         */
        private void handleRemoveConditionals(String head, ISLParser.ExprContext ctx) {
            switch (head) {
                case "if", "when", "unless" -> {
                    if (ctx.getChildCount() >= 4 && ctx.getChild(2) instanceof ISLParser.ExprContext cond)
                        addRemoveConditionalPoint(cond);
                }
                case "cond" -> {
                    // ( cond [ test expr ] ... [ else expr ] ) — test follows each '['
                    for (int i = 2; i < ctx.getChildCount() - 1; i++) {
                        if (ctx.getChild(i) instanceof TerminalNode tn
                                && tn.getSymbol().getText().equals("[")
                                && ctx.getChild(i + 1) instanceof ISLParser.ExprContext test) {
                            addRemoveConditionalPoint(test);
                        }
                    }
                }
                default -> { }
            }
        }

        private void addRemoveConditionalPoint(ISLParser.ExprContext cond) {
            boolean equality = mutationType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                    || mutationType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE;
            if (!containsHead(cond, equality ? EQUALITY_TESTS : ORDER_TESTS)) return;

            // Main.applyMutation rewrites a single line, so a condition that spans
            // lines cannot be replaced safely; skip it rather than emit a broken mutant.
            if (cond.getStart() == null || cond.getStop() == null
                    || cond.getStart().getLine() != cond.getStop().getLine()) return;

            boolean toTrue = mutationType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                    || mutationType == Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF;
            String replacement = toTrue ? "#t" : "#f";
            String condText = extractSourceText(cond);
            int[] pos = getExprPosition(cond);
            String label = (equality ? "Remove conditionals (equal): " : "Remove conditionals (order): ")
                    + condText + " → " + replacement;
            mutationPoints.add(new MutationPoint(sourceFile, pos[0], pos[1], condText, replacement, mutationType, label));
        }

        /**
         * True if the expression, or any nested paren application inside it,
         * has a head token in {@code heads}. Handles conditions like
         * (not (= a b)) and (and (> a 0) (< a 10)).
         */
        private boolean containsHead(ParseTree node, java.util.Set<String> heads) {
            if (node instanceof ISLParser.ExprContext expr && expr.getChildCount() >= 3
                    && expr.getChild(0) instanceof TerminalNode open
                    && open.getSymbol().getText().equals("(")) {
                Token headTok = extractToken(expr.getChild(1));
                if (headTok != null && heads.contains(headTok.getText())) return true;
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                if (containsHead(node.getChild(i), heads)) return true;
            }
            return false;
        }

        private String extractSourceText(ParseTree ctx) {
            if (ctx instanceof ParserRuleContext prc
                    && prc.getStart() != null && prc.getStop() != null
                    && prc.getStart().getStartIndex() >= 0
                    && prc.getStop().getStopIndex() < sourceCode.length()) {
                return sourceCode.substring(
                        prc.getStart().getStartIndex(),
                        prc.getStop().getStopIndex() + 1);
            }
            return ctx.getText();
        }

        /**
         * Returns [line, col] for the start of a parse tree node.
         */
        private int[] getExprPosition(ParseTree ctx) {
            if (ctx instanceof TerminalNode tn) {
                Token s = tn.getSymbol();
                return new int[]{s.getLine(), s.getCharPositionInLine()};
            }
            if (ctx instanceof ParserRuleContext prc && prc.getStart() != null) {
                Token s = prc.getStart();
                return new int[]{s.getLine(), s.getCharPositionInLine()};
            }
            return new int[]{0, 0};
        }

        // -------------------------------------------------------------------------
        // RVM: Return Value Mutations — applied to the last expr of function bodies
        // -------------------------------------------------------------------------

        @Override
        public Void visitDefinition(ISLParser.DefinitionContext ctx) {
            // A function body's non-last exprs run only for their side effects
            if (mutationType == Mutator.MutatorType.NON_VOID_METHOD_CALLS) {
                markDiscarded(discardedBodyForms(ctx));
            }

            boolean emptyReturns = mutationType == Mutator.MutatorType.EMPTY_RETURNS;
            if (emptyReturns && ctx.getChildCount() >= 6) {
                // Function definition: '(' 'define' '(' name NAME* ')' expr+ ')'
                // Distinguish from value definition by checking child(2) == '('
                ParseTree thirdChild = ctx.getChild(2);
                if (thirdChild instanceof TerminalNode tn3 && tn3.getText().equals("(")) {
                    List<ISLParser.ExprContext> bodyExprs = ctx.expr();
                    if (!bodyExprs.isEmpty()) {
                        ISLParser.ExprContext body = bodyExprs.get(bodyExprs.size() - 1);
                        List<ISLParser.ExprContext> tails = new ArrayList<>();
                        collectTailExprs(body, tails);
                        tails.forEach(this::checkEmptyReturn);
                    }
                }
            }
            return visitChildren(ctx);
        }

        // -------------------------------------------------------------------------
        // EMPTY_RETURNS — PIT-style: replace a returned string/list with "" / '()
        // -------------------------------------------------------------------------

        // Calls whose result is a string
        private static final java.util.Set<String> STRING_BUILDERS = java.util.Set.of(
                "string-append", "substring", "format", "string", "make-string",
                "string-upcase", "string-downcase", "string-titlecase",
                "string-join", "string-trim", "~a",
                "number->string", "symbol->string", "list->string"
        );

        // Calls whose result is a list
        private static final java.util.Set<String> LIST_BUILDERS = java.util.Set.of(
                "list", "append", "reverse", "map", "filter", "sort", "build-list"
        );

        /**
         * Racket has no return statement: a function's result is whatever its tail
         * expressions evaluate to. Collects those, descending through the branches
         * of if and cond and into the body of let, let*, letrec, local and begin,
         * so each tail position is mutated like a separate return in PIT.
         */
        private void collectTailExprs(ISLParser.ExprContext expr, List<ISLParser.ExprContext> out) {
            Token head = (expr.getChildCount() >= 3
                    && expr.getChild(0) instanceof TerminalNode open
                    && open.getSymbol().getText().equals("("))
                    ? extractToken(expr.getChild(1)) : null;
            String headText = head == null ? "" : head.getText();
            List<ISLParser.ExprContext> subExprs = expr.expr();

            switch (headText) {
                case "if" -> {
                    // ( if cond then else )
                    if (subExprs.size() == 3) {
                        collectTailExprs(subExprs.get(1), out);
                        collectTailExprs(subExprs.get(2), out);
                        return;
                    }
                }
                case "cond" -> {
                    // ( cond [ test body ] ... [ else body ] ) — a clause body is the expr just before ']'
                    for (int i = 0; i < expr.getChildCount() - 1; i++) {
                        if (expr.getChild(i) instanceof ISLParser.ExprContext clauseBody
                                && expr.getChild(i + 1) instanceof TerminalNode close
                                && close.getSymbol().getText().equals("]")) {
                            collectTailExprs(clauseBody, out);
                        }
                    }
                    return;
                }
                case "let", "let*", "letrec", "local", "begin" -> {
                    // bindings/statements come first; the body is the last expr
                    if (!subExprs.isEmpty()) {
                        collectTailExprs(subExprs.get(subExprs.size() - 1), out);
                        return;
                    }
                }
                default -> { }
            }
            out.add(expr);
        }

        private void checkEmptyReturn(ISLParser.ExprContext returnExpr) {
            // Every returned value becomes the empty value of its inferred type.
            String replacement = typedEmptyValue(returnExpr);
            if (replacement == null) return;

            if (returnExpr.getStart() == null || returnExpr.getStop() == null) return;

            // A value spanning several lines is fine: Main.applyMutation replaces the
            // whole multi-line text
            int[] pos = getExprPosition(returnExpr);
            mutationPoints.add(new MutationPoint(sourceFile, pos[0], pos[1],
                    extractSourceText(returnExpr), replacement, mutationType));
        }

        /**
         * Returns the empty value for a returned string or list, or null when the
         * expression isn't one (or is already empty):
         *   "text"                     → ""
         *   (string-append ...) etc.   → ""
         *   '(a b c)                   → '()
         *   (list x ...), (map ...)    → '()
         */
        private String emptyReplacement(ISLParser.ExprContext ctx) {
            Token tok = getSingleExprToken(ctx);
            if (tok != null) {
                return tok.getType() == ISLLexer.STRING && !tok.getText().equals("\"\"") ? "\"\"" : null;
            }

            // '(a b c) parses as  ' quoted  with quoted = ( quoted* ); '() is a separate '()' token
            if (ctx.getChildCount() == 2
                    && ctx.getChild(0) instanceof TerminalNode quote
                    && quote.getSymbol().getText().equals("'")
                    && ctx.getChild(1) instanceof ISLParser.QuotedContext quoted
                    && quoted.getChildCount() > 2
                    && quoted.getChild(0) instanceof TerminalNode open
                    && open.getSymbol().getText().equals("(")) {
                return "'()";
            }

            if (ctx.getChildCount() >= 3
                    && ctx.getChild(0) instanceof TerminalNode open
                    && open.getSymbol().getText().equals("(")) {
                Token head = extractToken(ctx.getChild(1));
                if (head == null) return null;
                if (STRING_BUILDERS.contains(head.getText())) return "\"\"";
                // (list) with no arguments is already empty
                boolean hasArgs = ctx.getChildCount() > 3;
                if (LIST_BUILDERS.contains(head.getText()) && hasArgs) return "'()";
            }
            return null;
        }

        /**
         * The empty value for any returned expression, inferred from its form,
         * or null when it is already that value:
         *   strings and lists              → "" / '()   (as emptyReplacement)
         *   numbers, (+ ...), (sqr ...)    → 0
         *   #t, (< ...), (and ...), (p? x) → #f
         *   anything else                  → '()  (Racket's null, as in RVM_OBJECT_TO_NULL)
         */
        private String typedEmptyValue(ISLParser.ExprContext ctx) {
            String stringOrList = emptyReplacement(ctx);
            if (stringOrList != null) return stringOrList;

            Token tok = getSingleExprToken(ctx);
            if (tok != null) {
                String text = tok.getText();
                return switch (tok.getType()) {
                    case ISLLexer.STRING -> null;  // already ""
                    case ISLLexer.NUMBER -> isZeroLiteral(text) ? null : "0";
                    case ISLLexer.BOOLEAN -> text.equalsIgnoreCase("#f") || text.equals("#false") ? null : "#f";
                    default -> "'()";
                };
            }
            if (mutationtester.msl.CstNodes.compactText(ctx).equals("'()")) return null;

            if (ctx.getChildCount() >= 3
                    && ctx.getChild(0) instanceof TerminalNode open
                    && open.getSymbol().getText().equals("(")) {
                Token head = extractToken(ctx.getChild(1));
                if (head != null) {
                    String name = head.getText();
                    int type = head.getType();
                    if (type == ISLLexer.PLUS || type == ISLLexer.MINUS || type == ISLLexer.STAR
                            || type == ISLLexer.SLASH || NUMBER_CALLS.contains(name)) return "0";
                    if (type == ISLLexer.GT || type == ISLLexer.LT || type == ISLLexer.GEQ
                            || type == ISLLexer.LEQ || type == ISLLexer.EQ
                            || name.equals("and") || name.equals("or") || name.equals("not")
                            || name.endsWith("?")) return "#f";
                    // (list) with no arguments is already empty
                    if (LIST_BUILDERS.contains(name)) return null;
                }
            }
            return "'()";
        }

        private static boolean isZeroLiteral(String number) {
            try {
                return Double.parseDouble(number) == 0;
            } catch (NumberFormatException e) {
                return false;  // rationals like 1/2 are never zero
            }
        }

        // -------------------------------------------------------------------------
        // NON_VOID_METHOD_CALLS — PIT-style: replace a call whose result is used
        // with a default value for what it returns
        // -------------------------------------------------------------------------

        // Identifier heads that are macros/keywords rather than function calls.
        // (Grammar keywords like if/let/define and operators like + and < are
        // separate token types, so they never reach this check.)
        private static final java.util.Set<String> NON_CALL_HEADS = java.util.Set.of(
                "when", "unless", "quote", "not", "struct", "case", "match",
                "for", "do", "parameterize", "with-handlers"
        );

        // Calls made for their side effect — VML_VOID_METHOD_CALL's territory
        private static final java.util.Set<String> VOID_CALLS = java.util.Set.of(
                "display", "displayln", "print", "println", "printf", "write", "writeln",
                "newline", "fprintf", "eprintf", "void", "error", "raise", "exit", "sleep"
        );

        // Calls whose result is a number
        private static final java.util.Set<String> NUMBER_CALLS = java.util.Set.of(
                "add1", "sub1", "abs", "max", "min", "sqr", "sqrt", "expt", "exp", "log",
                "quotient", "remainder", "modulo", "floor", "ceiling", "round", "truncate",
                "gcd", "lcm", "length", "string-length", "vector-length", "random",
                "bitwise-and", "bitwise-ior", "bitwise-xor", "arithmetic-shift"
        );

        // Exprs whose value is discarded; filled in by the enclosing form before
        // its children are visited
        private final java.util.Set<ParseTree> statementExprs = new java.util.HashSet<>();

        private void checkNonVoidCall(Token head, ISLParser.ExprContext call) {
            if (head.getType() != ISLLexer.NAME || statementExprs.contains(call)) return;

            String name = head.getText();
            if (NON_CALL_HEADS.contains(name) || VOID_CALLS.contains(name) || name.endsWith("!")) return;

            // A call spanning several lines is fine: Main.applyMutation replaces the
            // whole multi-line text
            int[] pos = getExprPosition(call);
            mutationPoints.add(new MutationPoint(sourceFile, pos[0], pos[1],
                    extractSourceText(call), defaultValueForCall(name), mutationType,
                    "Non-void method call mutation"));
        }

        /**
         * Racket has no declared return types, so the default is inferred from the
         * callee's name, mirroring PIT's per-type defaults.
         */
        private String defaultValueForCall(String name) {
            if (name.endsWith("?")) return "#f";
            if (NUMBER_CALLS.contains(name)) return "0";
            if (STRING_BUILDERS.contains(name)) return "\"\"";
            // Lists, and anything whose result can't be told from the name:
            // '() is Racket's null (as in RVM_OBJECT_TO_NULL)
            return "'()";
        }

        /** Marks the sub-exprs of begin/begin0/when/unless whose values are discarded. */
        private void markStatementExprs(String head, ISLParser.ExprContext ctx) {
            markDiscarded(discardedForms(head, ctx));
            // ( struct name (field ...) ) — the field list is not a call
            if (head.equals("struct") || head.equals("define-struct")) statementExprs.addAll(ctx.expr());
        }

        /** Marks forms as discarded, and the value form of each discarded when/begin/let too. */
        private void markDiscarded(List<ISLParser.ExprContext> forms) {
            for (ISLParser.ExprContext form : forms) {
                statementExprs.add(form);
                if (form.getChildCount() < 3 || !form.getChild(0).getText().equals("(")) continue;
                Token head = extractToken(form.getChild(1));
                ISLParser.ExprContext value = head == null ? null : valueForm(head.getText(), form);
                if (value != null) markDiscarded(List.of(value));
            }
        }

        /**
         * The sub-forms of a form whose values are discarded, i.e. statements. Shared
         * by NON_VOID_METHOD_CALLS (which skips them) and VML_VOID_METHOD_CALL /
         * STATEMENT_MUTATION (which remove them):
         *   ( begin stmt... value )               ( begin0 value stmt... )
         *   ( when cond stmt... value )           ( unless cond stmt... value )
         *   ( let ( [x v] ... ) stmt... value )   ( lambda ( x ... ) stmt... value )
         */
        private List<ISLParser.ExprContext> discardedForms(String head, ISLParser.ExprContext ctx) {
            List<ISLParser.ExprContext> exprs = ctx.expr();
            return switch (head) {
                case "begin" -> exprs.subList(0, Math.max(0, exprs.size() - 1));
                case "begin0" -> exprs.subList(Math.min(1, exprs.size()), exprs.size());
                // parsed as an application, so exprs(0) is the head and exprs(1) the condition
                case "when", "unless" -> exprs.size() > 3 ? exprs.subList(2, exprs.size() - 1) : List.of();
                case "let", "let*", "letrec", "lambda", "λ" -> {
                    List<ISLParser.ExprContext> body = bodyAfterBindings(ctx);
                    yield body.subList(0, Math.max(0, body.size() - 1));
                }
                default -> List.of();
            };
        }

        /**
         * The form that gives a begin, begin0, when, unless or let its value (the
         * one discardedForms leaves out), or null for any other form.
         */
        private ISLParser.ExprContext valueForm(String head, ISLParser.ExprContext ctx) {
            List<ISLParser.ExprContext> exprs = ctx.expr();
            List<ISLParser.ExprContext> body = switch (head) {
                case "begin", "begin0" -> exprs;
                case "when", "unless" -> exprs.size() > 2 ? exprs.subList(2, exprs.size()) : List.of();
                case "let", "let*", "letrec" -> bodyAfterBindings(ctx);
                default -> List.of();
            };
            if (body.isEmpty()) return null;
            return head.equals("begin0") ? body.get(0) : body.get(body.size() - 1);
        }

        /** The discarded forms of a function body: ( define ( f x ... ) stmt... value ). */
        private List<ISLParser.ExprContext> discardedBodyForms(ISLParser.DefinitionContext ctx) {
            if (ctx.getChildCount() < 6 || !(ctx.getChild(2) instanceof TerminalNode paren)
                    || !paren.getText().equals("(")) {
                return List.of();
            }
            List<ISLParser.ExprContext> body = ctx.expr();
            return body.subList(0, Math.max(0, body.size() - 1));
        }

        /**
         * The body exprs of a let or lambda, which the grammar lists flat after the
         * bindings or parameters: everything after the ')' that closes them.
         */
        private List<ISLParser.ExprContext> bodyAfterBindings(ISLParser.ExprContext ctx) {
            List<ISLParser.ExprContext> body = new ArrayList<>();
            if (!(ctx.getChild(2) instanceof TerminalNode open)) return body;
            // (lambda () ...) and (let () ...) have a single '()' token instead
            boolean inBody = open.getText().equals("()");
            if (!inBody && !open.getText().equals("(")) return body;
            for (int i = 3; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (!inBody) {
                    inBody = child instanceof TerminalNode t && t.getText().equals(")");
                } else if (child instanceof ISLParser.ExprContext expr) {
                    body.add(expr);
                }
            }
            return body;
        }

        // -------------------------------------------------------------------------
        // CONSTRUCTOR_CALLS — PIT-style: replace a constructor call (new Foo()) with
        // null. Racket's equivalent of `new` is a struct constructor call.
        // -------------------------------------------------------------------------

        // Constructor names of the structs declared in this file or a file it requires
        private final java.util.Set<String> structConstructors = new java.util.HashSet<>();

        /**
         * Records the constructors bound by struct declarations:
         *   (struct posn (x y))         → posn
         *   (define-struct posn (x y))  → make-posn
         */
        void collectStructConstructors(ParseTree node) {
            if (node instanceof ISLParser.DefinitionContext def
                    && def.getChild(1).getText().equals("define-struct")) {
                structConstructors.add("make-" + def.name(0).getText());
            }
            // (struct ...) isn't a grammar keyword, so it parses as an application:
            // exprs(0) is the head, exprs(1) the struct name
            if (node instanceof ISLParser.ExprContext expr && expr.expr().size() >= 3) {
                Token head = extractToken(expr.expr(0));
                Token name = extractToken(expr.expr(1));
                if (head != null && head.getText().equals("struct")
                        && name != null && name.getType() == ISLLexer.NAME) {
                    structConstructors.add(name.getText());
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) {
                collectStructConstructors(node.getChild(i));
            }
        }

        private void checkConstructorCall(Token head, ISLParser.ExprContext call) {
            if (head.getType() != ISLLexer.NAME) return;

            // A struct declared here or in a required file, or a built-in like make-hash. Other
            // make-... names are ordinary functions (make-report, make-counter).
            String name = head.getText();
            boolean isConstructor = structConstructors.contains(name) || isBuiltInConstructor(name);
            if (!isConstructor) return;

            // A call spanning several lines is fine: Main.applyMutation replaces the
            // whole multi-line text. '() is Racket's null (as in RVM_OBJECT_TO_NULL)
            int[] pos = getExprPosition(call);
            mutationPoints.add(new MutationPoint(sourceFile, pos[0], pos[1],
                    extractSourceText(call), "'()", mutationType,
                    "Constructor call mutation"));
        }

        // -------------------------------------------------------------------------
        // REMOVE_LOGICAL_NEGATION — PIT-style: drop the negation (!p → p).
        // Racket negates with the not function, so (not p) → p.
        // -------------------------------------------------------------------------

        private Token getSingleExprToken(ISLParser.ExprContext ctx) {
            if (ctx.getChildCount() == 1 && ctx.getChild(0) instanceof TerminalNode tn)
                return tn.getSymbol();
            return null;
        }

        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }

    }
    /** Racket equality tests that MSL's == (and the inner form of !=) stand for. */
    private static final List<String> EQUALITY_FUNCTIONS = List.of("=", "equal?", "eqv?", "eq?", "string=?", "char=?");

    /**
     * Forms that define macros. Their patterns ((when-positive n body ...)) and
     * templates aren't ordinary expressions, so nothing inside them is mutated.
     */
    private static final java.util.Set<String> MACRO_DEFINITION_FORMS = java.util.Set.of(
            "define-syntax-rule", "define-syntax", "define-syntaxes",
            "let-syntax", "letrec-syntax", "define-simple-macro");

    /** Functions whose call is an equality test (PIT REMOVE_CONDITIONALS_EQUAL_*). */
    private static final java.util.Set<String> EQUALITY_TESTS = java.util.Set.of(
            "=", "eq?", "eqv?", "equal?", "zero?", "null?", "empty?",
            "string=?", "string-ci=?", "char=?", "char-ci=?");

    /** Functions whose call is an order test (PIT REMOVE_CONDITIONALS_ORDER_*). */
    private static final java.util.Set<String> ORDER_TESTS = java.util.Set.of(
            "<", ">", "<=", ">=", "positive?", "negative?",
            "string<?", "string>?", "string<=?", "string>=?",
            "string-ci<?", "string-ci>?", "string-ci<=?", "string-ci>=?",
            "char<?", "char>?", "char<=?", "char>=?",
            "char-ci<?", "char-ci>?", "char-ci<=?", "char-ci>=?");

    /** True for define-syntax-rule and the other macro-defining form names above. */
    public static boolean isMacroDefinitionForm(String head) {
        return MACRO_DEFINITION_FORMS.contains(head);
    }

    /** "-12": a minus sign and digits. */
    private static boolean isNegativeInteger(String text) {
        return text.startsWith("-") && MSLScannerAdapter.isIntegerText(text, true);
    }

    /**
     * Built-in functions that create a new object, Racket's nearest thing to `new`.
     * make-posn is built into the HtDP teaching languages.
     */
    private static final java.util.Set<String> BUILT_IN_CONSTRUCTORS = java.util.Set.of(
            "make-hash", "make-hasheq", "make-hasheqv",
            "make-weak-hash", "make-weak-hasheq", "make-weak-hasheqv",
            "make-immutable-hash", "make-immutable-hasheq", "make-immutable-hasheqv",
            "make-vector", "make-string", "make-bytes", "make-list",
            "make-posn", "make-parameter", "make-channel", "make-semaphore", "make-custodian");

    /** True for a built-in constructor such as make-hash (CONSTRUCTOR_CALLS). */
    public static boolean isBuiltInConstructor(String name) {
        return BUILT_IN_CONSTRUCTORS.contains(name);
    }

    // -------------------------------------------------------------------------
    // Structs declared in required files (CONSTRUCTOR_CALLS)
    // -------------------------------------------------------------------------

    /**
     * Constructors of the structs declared in the local files this one requires,
     * so (posn 1 2) counts as a constructor call when (struct posn ...) lives in
     * another file. Handles (require "shapes.rkt") and (file "..."), wrapped in
     * only-in, except-in, prefix-in, rename-in or combine-in, and follows those
     * files' own requires, since a module can re-export what it requires.
     * Collection paths like racket/list aren't local files and are skipped.
     */
    public static Set<String> requiredStructConstructors(File file) {
        Path path = file.toPath().toAbsolutePath().normalize();
        Set<Path> open = new HashSet<>(List.of(path));
        Set<String> names = new HashSet<>();
        for (Object spec : requireSpecs(readForms(path))) {
            names.addAll(constructorsFromSpec(spec, path.getParent(), open));
        }
        return names;
    }

    // A string literal in readForms' output; atoms are plain Strings
    private record Str(String value) {}

    /** The names one require spec brings in that are struct constructors. */
    private static Set<String> constructorsFromSpec(Object spec, Path dir, Set<Path> open) {
        if (spec instanceof Str s) return constructorsInFile(dir.resolve(s.value()), open);
        if (!(spec instanceof List<?> form) || form.size() < 2 || !(form.get(0) instanceof String head)) {
            return Set.of();
        }
        Set<String> names = new HashSet<>();
        switch (head) {
            case "file" -> names.addAll(constructorsFromSpec(form.get(1), dir, open));
            case "combine-in" -> {
                for (Object sub : form.subList(1, form.size())) names.addAll(constructorsFromSpec(sub, dir, open));
            }
            case "prefix-in" -> {
                if (form.size() >= 3 && form.get(1) instanceof String prefix) {
                    for (String name : constructorsFromSpec(form.get(2), dir, open)) names.add(prefix + name);
                }
            }
            case "only-in" -> {
                // (only-in spec id [old-id new-id] ...) keeps just the listed names
                Set<String> base = constructorsFromSpec(form.get(1), dir, open);
                for (Object item : form.subList(2, form.size())) {
                    if (item instanceof String id && base.contains(id)) names.add(id);
                    String[] pair = renamePair(item);
                    if (pair != null && base.contains(pair[0])) names.add(pair[1]);
                }
            }
            case "except-in" -> {
                names.addAll(constructorsFromSpec(form.get(1), dir, open));
                names.removeAll(form.subList(2, form.size()));
            }
            case "rename-in" -> {
                names.addAll(constructorsFromSpec(form.get(1), dir, open));
                for (Object item : form.subList(2, form.size())) {
                    String[] pair = renamePair(item);
                    if (pair != null && names.remove(pair[0])) names.add(pair[1]);
                }
            }
            default -> { }   // for-syntax, planet and other specs
        }
        return names;
    }

    /** [old-id new-id] in only-in or rename-in, or null. */
    private static String[] renamePair(Object item) {
        if (item instanceof List<?> pair && pair.size() == 2
                && pair.get(0) instanceof String from && pair.get(1) instanceof String to) {
            return new String[] { from, to };
        }
        return null;
    }

    /** Struct constructors a required file declares or re-exports from its own requires. */
    private static Set<String> constructorsInFile(Path path, Set<Path> open) {
        path = path.normalize();
        // open is the chain of files being read, so a require cycle stops here
        if (!Files.isRegularFile(path) || !open.add(path)) return Set.of();
        try {
            List<Object> forms = readForms(path);
            Set<String> names = new HashSet<>();
            collectDeclaredStructs(forms, names);
            for (Object spec : requireSpecs(forms)) {
                names.addAll(constructorsFromSpec(spec, path.getParent(), open));
            }
            return names;
        } finally {
            open.remove(path);
        }
    }

    /** (struct posn ...) binds posn, (define-struct posn ...) binds make-posn. */
    private static void collectDeclaredStructs(Object node, Set<String> names) {
        if (!(node instanceof List<?> form)) return;
        if (form.size() >= 3 && form.get(0) instanceof String head && form.get(1) instanceof String name) {
            if (head.equals("struct")) names.add(name);
            if (head.equals("define-struct")) names.add("make-" + name);
        }
        for (Object child : form) collectDeclaredStructs(child, names);
    }

    /** The specs of every (require ...) form, including those in submodules. */
    private static List<Object> requireSpecs(Object node) {
        List<Object> specs = new ArrayList<>();
        if (node instanceof List<?> form) {
            if (!form.isEmpty() && "require".equals(form.get(0))) {
                specs.addAll(form.subList(1, form.size()));
            } else {
                for (Object child : form) specs.addAll(requireSpecs(child));
            }
        }
        return specs;
    }

    /**
     * Reads a file as s-expressions: a List per form, a String per atom and a Str
     * per string literal, without comments. Enough to find requires and struct
     * declarations; an unreadable file reads as no forms.
     */
    private static List<Object> readForms(Path path) {
        String src;
        try {
            src = Files.readString(path);
        } catch (IOException e) {
            return List.of();
        }
        List<Object> top = new ArrayList<>();
        java.util.Deque<List<Object>> stack = new java.util.ArrayDeque<>();
        stack.push(top);
        int i = 0;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == ';') {
                while (i < n && src.charAt(i) != '\n') i++;
            } else if (src.startsWith("#|", i)) {
                int end = src.indexOf("|#", i + 2);
                i = end < 0 ? n : end + 2;
            } else if (c == '(' || c == '[' || c == '{') {
                List<Object> list = new ArrayList<>();
                stack.peek().add(list);
                stack.push(list);
                i++;
            } else if (c == ')' || c == ']' || c == '}') {
                if (stack.size() > 1) stack.pop();
                i++;
            } else if (c == '"') {
                StringBuilder value = new StringBuilder();
                i++;
                while (i < n && src.charAt(i) != '"') {
                    if (src.charAt(i) == '\\' && i + 1 < n) i++;
                    value.append(src.charAt(i++));
                }
                i++;
                stack.peek().add(new Str(value.toString()));
            } else {
                int start = i;
                // A character literal like #\( isn't a paren
                if (src.startsWith("#\\", i)) i = Math.min(i + 3, n);
                while (i < n && !Character.isWhitespace(src.charAt(i)) && "()[]{}\";".indexOf(src.charAt(i)) < 0) i++;
                stack.peek().add(src.substring(start, i));
            }
        }
        return top;
    }

    /**
     * What the scanner's own visitor finds for one of EMPTY_RETURNS,
     * NON_VOID_METHOD_CALLS, CONSTRUCTOR_CALLS, REMOVE_CONDITIONALS_EQUAL_IF or
     * REMOVE_CONDITIONALS_ORDER_IF, the kinds the adapter's placeholders use.
     */
    public static List<MutationPoint> visitorPoints(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        ISLParser parser = new ISLParser(new CommonTokenStream(new ISLLexer(CstNodes.utf16(sourceCode))));
        parser.removeErrorListeners();
        parser.addErrorListener(QuietAntlrErrorListener.INSTANCE);
        return visitorPoints(file, sourceCode, parser.program(), type);
    }

    /** The same, for a tree the adapter has already parsed. */
    static List<MutationPoint> visitorPoints(File file, String sourceCode, ParseTree tree, Mutator.MutatorType type) {
        ISLMutationVisitor visitor = new ISLMutationVisitor(file, type, sourceCode);
        if (type == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
            // Structs may be declared after their first use, or in a required file.
            visitor.collectStructConstructors(tree);
            visitor.structConstructors.addAll(requiredStructConstructors(file));
        }
        visitor.visit(tree);
        return visitor.getMutationPoints();
    }

    /** True for (define-syntax-rule ...) and the other forms above. */
    static boolean isMacroDefinition(ParseTree ctx) {
        return ctx instanceof ISLParser.ExprContext
                && ctx.getChildCount() >= 3
                && ctx.getChild(0).getText().equals("(")
                && isMacroDefinitionForm(ctx.getChild(1).getText());
    }

    /** Forms that hold other forms; a discarded one discards its own value form instead. */
    private static final java.util.Set<String> VALUE_FORMS = java.util.Set.of(
            "begin", "begin0", "let", "let*", "letrec", "lambda", "λ", "if", "cond");

    /** Exprs whose value is discarded, as the visitor finds them for NON_VOID_METHOD_CALLS. */
    static java.util.Set<ParseTree> discardedExprs(File file, String sourceCode, ParseTree tree) {
        ISLMutationVisitor visitor = new ISLMutationVisitor(file, Mutator.MutatorType.NON_VOID_METHOD_CALLS, sourceCode);
        visitor.visit(tree);
        return visitor.statementExprs;
    }

    /** Forms that define or declare something; removing them isn't a call removal. */
    private static final java.util.Set<String> NON_STATEMENT_FORMS = java.util.Set.of(
            "define", "define-values", "define-syntax", "define-struct", "struct",
            "require", "provide", "module", "module+", "set!");

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = CstNodes.utf16(sourceCode);
        ISLLexer lexer = new ISLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ISLParser parser = new ISLParser(tokens);
        ParseTree tree = parser.program();

        return new MSLScannerAdapter() {
            // Racket spells most MSL operators as a prefix function name. Shifts
            // and inequality have no single name, so they get special handling:
            //   a << b   is (arithmetic-shift a b)
            //   a >> b   is (arithmetic-shift a (- b))
            //   a != b   is (not (= a b))
            //   a // b   is (quotient a b) or (floor (/ a b))
            //   a >>> b  is (arithmetic-shift (bitwise-and a #xFFFFFFFF) (- b)):
            //            Racket integers are unbounded, so an unsigned shift
            //            masks the value to the word size first.
            // The inner / of (floor (/ a b)) and the bitwise-and mask of >>> are
            // part of those operators, so the / and & rules don't match them.

            @Override
            public boolean skipsSubtree(ParseTree ctx) {
                return isMacroDefinition(ctx);
            }

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                return switch (mslOperator) {
                    case "<<" -> isShift(ctx) && !isNegatedAmount(ctx.getChild(3));
                    case ">>" -> isShift(ctx) && isNegatedAmount(ctx.getChild(3)) && !isUnsignedShift(ctx);
                    case ">>>" -> isUnsignedShift(ctx);
                    case "!=" -> negatedEquality(ctx) != null;
                    case "//" -> (isApplication(ctx, 2) && head(ctx).equals("quotient")) || floorDivision(ctx) != null;
                    case "/" -> isApplication(ctx, 2) && head(ctx).equals("/") && !isInsideFloorDivision(ctx);
                    case "bitwise-and" -> isApplication(ctx, 2) && head(ctx).equals("bitwise-and")
                            && !isMaskOfUnsignedShift(ctx);
                    default -> isApplication(ctx, 2) && head(ctx).equals(mslOperator);
                };
            }

            /** The (/ a b) inside (floor (/ a b)), or null. */
            private ParseTree floorDivision(ParseTree ctx) {
                if (!isApplication(ctx, 1) || ctx.getChildCount() != 4 || !head(ctx).equals("floor")) return null;
                ParseTree inner = unwrapSingle(ctx.getChild(2));
                return isApplication(inner, 2) && inner.getChildCount() == 5 && head(inner).equals("/") ? inner : null;
            }

            private boolean isInsideFloorDivision(ParseTree division) {
                ParseTree p = division.getParent();
                while (p != null && !isApplication(p, 0)) p = p.getParent();
                return p != null && floorDivision(p) == division;
            }

            /** (arithmetic-shift (bitwise-and x MASK) (- n)) */
            private boolean isUnsignedShift(ParseTree ctx) {
                if (!isShift(ctx) || !isNegatedAmount(ctx.getChild(3))) return false;
                ParseTree value = unwrapSingle(ctx.getChild(2));
                return isApplication(value, 2) && value.getChildCount() == 5 && head(value).equals("bitwise-and")
                        && (isMask(value.getChild(3)) || isMask(value.getChild(2)));
            }

            private boolean isMask(ParseTree node) {
                return MSLScannerAdapter.isUnsignedShiftMask(getNodeText(node));
            }

            private boolean isMaskOfUnsignedShift(ParseTree and) {
                ParseTree p = and.getParent();
                while (p != null && !isApplication(p, 0)) p = p.getParent();
                return p != null && isUnsignedShift(p) && unwrapSingle(p.getChild(2)) == and;
            }

            private ParseTree unwrapSingle(ParseTree node) {
                while (node != null && !isApplication(node, 0) && node.getChildCount() == 1) node = node.getChild(0);
                return node;
            }

            @Override
            public List<String> getOperatorAliases(String canonicalOperator) {
                return switch (canonicalOperator) {
                    case "%" -> List.of("modulo", "remainder");
                    case "//" -> List.of("//");
                    case "**" -> List.of("expt");
                    case "&" -> List.of("bitwise-and");  // the mask of a >>> is excluded
                    case "|" -> List.of("bitwise-ior");
                    case "^" -> List.of("bitwise-xor");
                    case "==" -> EQUALITY_FUNCTIONS;
                    case "&&" -> List.of("and");
                    case "||" -> List.of("or");
                    default -> List.of(canonicalOperator);
                };
            }

            @Override
            public String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
                return switch (canonicalOperator) {
                    case "%" -> "modulo";
                    case "//" -> "quotient";
                    case "**" -> "expt";
                    case "&" -> "bitwise-and";
                    case "|" -> "bitwise-ior";
                    case "^" -> "bitwise-xor";
                    case "==" -> "=";
                    case "&&" -> "and";
                    case "||" -> "or";
                    default -> canonicalOperator;
                };
            }

            @Override
            public OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
                int[] pos = getTokenPosition(ctx);
                String text = getNodeText(ctx);
                ParseTree inner = "!=".equals(fromOperator) ? negatedEquality(ctx) : null;

                // (= a b) <-> (not (= a b)): wrap or unwrap, keeping the original form.
                if (inner != null && "==".equals(toOperator)) {
                    return new OperatorEdit(pos[0], pos[1], text, getNodeText(inner));
                }
                if ("==".equals(fromOperator) && "!=".equals(toOperator)) {
                    return new OperatorEdit(pos[0], pos[1], text, "(not " + text + ")");
                }

                // Shift direction flips by negating the shift amount. A >>> keeps
                // its mask, as (x & 0xFFFFFFFF) >> n -> (x & 0xFFFFFFFF) << n does in Python.
                boolean fromShift = "<<".equals(fromOperator) || ">>".equals(fromOperator) || ">>>".equals(fromOperator);
                boolean toShift = "<<".equals(toOperator) || ">>".equals(toOperator) || ">>>".equals(toOperator);
                boolean flipsDirection = ("<<".equals(fromOperator) && ">>".equals(toOperator))
                        || (!"<<".equals(fromOperator) && fromShift && "<<".equals(toOperator));
                if (flipsDirection) {
                    ParseTree amount = ctx.getChild(3);
                    int[] amountPos = getTokenPosition(amount);
                    String amountText = getNodeText(amount);
                    String flipped = "<<".equals(fromOperator) ? "(- " + amountText + ")" : unnegate(amount);
                    return new OperatorEdit(amountPos[0], amountPos[1], amountText, flipped);
                }

                // Any other change into or out of a special form rebuilds the call.
                boolean fromFloor = "//".equals(fromOperator) && floorDivision(ctx) != null;
                if (inner != null || fromShift || toShift || fromFloor
                        || "!=".equals(toOperator) || "//".equals(toOperator)) {
                    List<String> operands = operandTexts(ctx, fromOperator, inner);
                    if (">>>".equals(fromOperator) && ">>".equals(toOperator)) {
                        // Unsigned -> signed shift drops the mask.
                        ParseTree masked = unwrapSingle(ctx.getChild(2));
                        ParseTree value = isMask(masked.getChild(3)) ? masked.getChild(2) : masked.getChild(3);
                        operands = List.of(getNodeText(value), operands.get(1));
                    }
                    String rebuilt = buildCall(toOperator, operands);
                    return rebuilt == null ? null : new OperatorEdit(pos[0], pos[1], text, rebuilt);
                }

                // Plain prefix functions: swap the function name.
                String replacement = toLanguageOperator(toOperator, ctx);
                if (replacement == null) return null;
                ParseTree headNode = ctx.getChild(1);
                int[] headPos = getTokenPosition(headNode);
                return new OperatorEdit(headPos[0], headPos[1], getNodeText(headNode), replacement);
            }

            /**
             * The operands as MSL sees them: a and b for (- b) shifts, (floor (/ a b))
             * and (not (= a b)) alike.
             */
            private List<String> operandTexts(ParseTree ctx, String fromOperator, ParseTree negatedInner) {
                if (negatedInner != null) return argumentTexts(negatedInner);
                ParseTree floor = "//".equals(fromOperator) ? floorDivision(ctx) : null;
                if (floor != null) return argumentTexts(floor);
                if (">>".equals(fromOperator) || ">>>".equals(fromOperator)) {
                    return List.of(getNodeText(ctx.getChild(2)), unnegate(ctx.getChild(3)));
                }
                return argumentTexts(ctx);
            }

            private String buildCall(String canonicalOperator, List<String> args) {
                String joined = String.join(" ", args);
                switch (canonicalOperator) {
                    case "!=":
                        return "(not (= " + joined + "))";
                    case "//":
                        return args.size() == 2 ? "(floor (/ " + joined + "))" : null;
                    case ">>>":
                        return args.size() == 2
                                ? "(arithmetic-shift (bitwise-and " + args.get(0) + " #xFFFFFFFF) (- " + args.get(1) + "))"
                                : null;
                    case "<<":
                        return args.size() == 2 ? "(arithmetic-shift " + joined + ")" : null;
                    case ">>":
                        return args.size() == 2
                                ? "(arithmetic-shift " + args.get(0) + " (- " + args.get(1) + "))"
                                : null;
                    default:
                        String name = toLanguageOperator(canonicalOperator, null);
                        return name == null ? null : "(" + name + " " + joined + ")";
                }
            }

            /** (f a b ...) with at least minArgs arguments. */
            private boolean isApplication(ParseTree ctx, int minArgs) {
                if (!(ctx instanceof ISLParser.ExprContext) || ctx.getChildCount() < minArgs + 3) {
                    return false;
                }
                String open = ctx.getChild(0).getText();
                String close = ctx.getChild(ctx.getChildCount() - 1).getText();
                return (open.equals("(") && close.equals(")")) || (open.equals("[") && close.equals("]"));
            }

            private String head(ParseTree ctx) {
                return ctx.getChild(1).getText();
            }

            private boolean isShift(ParseTree ctx) {
                return isApplication(ctx, 2) && ctx.getChildCount() == 5
                        && head(ctx).equals("arithmetic-shift");
            }

            /** A shift amount written as (- n) or a negative literal shifts right. */
            private boolean isNegatedAmount(ParseTree amount) {
                if (isNegativeInteger(amount.getText())) return true;
                return isApplication(amount, 1) && amount.getChildCount() == 4 && head(amount).equals("-");
            }

            private String unnegate(ParseTree amount) {
                String text = getNodeText(amount);
                if (isNegativeInteger(text)) return text.substring(1);
                return getNodeText(amount.getChild(2));
            }

            /** For (not (= a b)) returns the inner (= a b); otherwise null. */
            private ParseTree negatedEquality(ParseTree ctx) {
                if (!isApplication(ctx, 1) || ctx.getChildCount() != 4 || !head(ctx).equals("not")) {
                    return null;
                }
                ParseTree inner = ctx.getChild(2);
                while (inner != null && !isApplication(inner, 0) && inner.getChildCount() == 1) {
                    inner = inner.getChild(0);
                }
                return isApplication(inner, 2) && EQUALITY_FUNCTIONS.contains(head(inner)) ? inner : null;
            }

            private List<String> argumentTexts(ParseTree call) {
                List<String> args = new ArrayList<>();
                for (int i = 2; i < call.getChildCount() - 1; i++) {
                    args.add(getNodeText(call.getChild(i)));
                }
                return args;
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                return operandSource(ctx).getChild(2);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                return operandSource(ctx).getChild(3);
            }

            /** The call whose arguments are the operands: the inner call of (not (= ..)) or (floor (/ ..)). */
            private ParseTree operandSource(ParseTree ctx) {
                ParseTree inner = negatedEquality(ctx);
                if (inner == null) inner = floorDivision(ctx);
                return inner != null ? inner : ctx;
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                return ctx.getChild(1);
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                String text = getNodeText(ctx).strip();
                // Any expression except bracket or quote punctuation.
                return !text.isEmpty() && !java.util.Set.of("(", ")", "[", "]", "'").contains(text);
            }

            @Override
            public boolean isStringLiteral(ParseTree ctx) {
                return ctx != null && MSLScannerAdapter.isStringLiteral(getNodeText(ctx).strip(), false);
            }

            @Override
            public String getNodeText(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null
                            && start.getStartIndex() >= 0
                            && stop.getStopIndex() >= start.getStartIndex()
                            && stop.getStopIndex() < sourceCode.length()) {
                        return sourceCode.substring(start.getStartIndex(), stop.getStopIndex() + 1);
                    }
                }
                return ctx.getText();
            }

            /**
             * A Racket "statement" is a call evaluated only for its effect: one in
             * a body (define, lambda, let, when, unless, begin) that is not the
             * body's last form, since the last form is the return value.
             * Definitions are never removed.
             */
            // MSL's "s is null" is a null-or-empty check. With #f as "no string",
            // Racket writes it (or (not s) (string=? s "")) or a variant.
            @Override
            public ParseTree nullCheckSubject(ParseTree ctx) {
                if (isApplication(ctx, 1) && ctx.getChildCount() == 4 && head(ctx).equals("string.IsNullOrEmpty")) {
                    return ctx.getChild(2);
                }
                if (!(ctx instanceof ISLParser.ExprContext) || ctx.getChildCount() != 5
                        || !ctx.getChild(1).getText().equals("or")) {
                    return null;
                }
                ParseTree not = ctx.getChild(2);
                if (!isApplication(not, 1) || not.getChildCount() != 4 || !head(not).equals("not")) return null;
                ParseTree subject = not.getChild(2);
                TerminalNode token = CstNodes.singleToken(subject);
                if (token == null || token.getSymbol().getType() == ISLLexer.STRING) return null;
                String s = token.getText();
                ParseTree check = ctx.getChild(3);
                if (!isApplication(check, 1)) return null;
                String name = head(check);
                if ((name.equals("string=?") || name.equals("equal?")) && check.getChildCount() == 5) {
                    return check.getChild(2).getText().equals(s) && check.getChild(3).getText().equals("\"\"")
                            ? subject : null;
                }
                if (name.equals("zero?") && check.getChildCount() == 4) {
                    ParseTree length = check.getChild(2);
                    return isApplication(length, 1) && length.getChildCount() == 4
                            && head(length).equals("string-length") && length.getChild(2).getText().equals(s)
                            ? subject : null;
                }
                return null;
            }

            /** Operators are prefix calls: (- a b), (not (= a b)), (arithmetic-shift a b). */
            @Override
            public String renderBinary(mutationtester.msl.MSLNode node, String left, String right, ParseTree ctx) {
                if (node.value().equals("!=") && node.child(1) instanceof mutationtester.msl.MSLNode r
                        && r.kind().equals(mutationtester.msl.MSLNode.LITERAL)
                        && (r.value().equals("null") || r.value().equals("\"\""))) {
                    return MSLScannerAdapter.super.renderBinary(node, left, right, ctx);
                }
                return buildCall(node.value(), List.of(left, right));
            }

            /** Racket forms need no terminator. */
            @Override
            public String renderStatement(mutationtester.msl.MSLTree tree, mutationtester.msl.MSLBindings.Cursor bindings,
                                          ParseTree ctx) {
                return render(tree, bindings, ctx);
            }

            @Override
            public String renderCall(String function, List<String> arguments, ParseTree ctx) {
                return arguments.isEmpty() ? "(" + function + ")" : "(" + function + " " + String.join(" ", arguments) + ")";
            }

            @Override
            public String renderUnary(String operator, String operand, ParseTree ctx) {
                return switch (operator) {
                    case "!" -> "(not " + operand + ")";
                    case "-" -> "(- " + operand + ")";
                    case "~" -> "(bitwise-not " + operand + ")";
                    default -> null;
                };
            }

            /** Racket forms: (set! x v), (if c a b), (begin ...), (void); no loops, if needs an else. */
            @Override
            public String renderStatementNode(mutationtester.msl.MSLNode node,
                                              mutationtester.msl.MSLBindings.Cursor bindings, ParseTree ctx) {
                List<String> parts = new ArrayList<>();
                for (mutationtester.msl.MSLTree child : node.children()) {
                    String part = render(child, bindings, ctx);
                    if (part == null) return null;
                    parts.add(part);
                }
                return switch (node.kind()) {
                    case mutationtester.msl.MSLNode.ASSIGN -> "(set! " + parts.get(0) + " " + parts.get(1) + ")";
                    case mutationtester.msl.MSLNode.IF -> parts.size() == 3
                            ? "(if " + parts.get(0) + " " + parts.get(1) + " " + parts.get(2) + ")" : null;
                    case mutationtester.msl.MSLNode.BLOCK -> parts.isEmpty() ? "(void)" : "(begin " + String.join(" ", parts) + ")";
                    case mutationtester.msl.MSLNode.EMPTY -> getEmptyStatement(ctx);
                    // A function's result is its last form, so return v is just v.
                    case mutationtester.msl.MSLNode.RETURN -> parts.isEmpty() ? null : parts.get(0);
                    case mutationtester.msl.MSLNode.THROW -> "(raise " + parts.get(0) + ")";
                    case mutationtester.msl.MSLNode.DECLARE -> isLetBindingValue(ctx) ? parts.get(1)
                            : "(define " + parts.get(0) + " " + parts.get(1) + ")";
                    case mutationtester.msl.MSLNode.WHEN, mutationtester.msl.MSLNode.UNLESS ->
                            "(" + node.kind() + " " + parts.get(0) + " " + parts.get(1) + ")";
                    case mutationtester.msl.MSLNode.FOR_EACH -> "(for ((" + parts.get(0) + " " + parts.get(1) + ")) " + parts.get(2) + ")";
                    default -> null;
                };
            }

            /** (f a b): its arguments, when f is the named function. */
            @Override
            public List<ParseTree> callArguments(ParseTree ctx, String functionName) {
                if (!isApplication(ctx, 0) || !ctx.getChild(0).getText().equals("(") || !head(ctx).equals(functionName)) {
                    return null;
                }
                List<ParseTree> args = new ArrayList<>();
                for (int i = 2; i < ctx.getChildCount() - 1; i++) args.add(ctx.getChild(i));
                return args;
            }

            @Override
            public String renderLiteral(String canonical, ParseTree ctx) {
                return switch (canonical) {
                    case "true" -> "#t";
                    case "false" -> "#f";
                    // '() is Racket's null, as in the other Racket null-value mutators.
                    case "null" -> "'()";
                    default -> canonical;
                };
            }

            // ── Forms, for Except: trees ─────────────────────────────────

            /**
             * (if c v x) is the expression form of "if (c) x = v": the value is
             * v when c holds and x otherwise, so an Except: such as
             * if ({match}) {quantity} = 0 also covers the clamp (if (<= n 0) 0 n).
             * (set! x v) is a plain assignment.
             */
            @Override
            public List<ParseTree> matchNode(mutationtester.msl.MSLNode pattern, ParseTree ctx) {
                switch (pattern.kind()) {
                    case mutationtester.msl.MSLNode.IF -> {
                        return matchIf(pattern, ctx, false);
                    }
                    case mutationtester.msl.MSLNode.ASSIGN -> {
                        return isApplication(ctx, 2) && head(ctx).equals("set!") && ctx.getChildCount() == 5
                                ? List.of(ctx.getChild(2), ctx.getChild(3)) : null;
                    }
                    case mutationtester.msl.MSLNode.UNARY -> {
                        String function = switch (pattern.value()) {
                            case "!" -> "not";
                            case "-" -> "-";
                            case "~" -> "bitwise-not";
                            default -> null;
                        };
                        List<ParseTree> args = function == null ? null : callArguments(ctx, function);
                        return args != null && args.size() == 1 ? args : null;
                    }
                    case mutationtester.msl.MSLNode.CONDITIONAL -> {
                        return isApplication(ctx, 3) && head(ctx).equals("if") && ctx.getChildCount() == 6
                                ? List.of(ctx.getChild(2), ctx.getChild(3), ctx.getChild(4)) : null;
                    }
                    case mutationtester.msl.MSLNode.INDEX -> {
                        if (!isApplication(ctx, 2) || ctx.getChildCount() != 5) return null;
                        String h = head(ctx);
                        return h.equals("list-ref") || h.equals("vector-ref") || h.equals("string-ref")
                                ? List.of(ctx.getChild(2), ctx.getChild(3)) : null;
                    }
                    case mutationtester.msl.MSLNode.RETURN -> {
                        return isFunctionResult(ctx) ? List.of(ctx) : null;
                    }
                    case mutationtester.msl.MSLNode.THROW -> {
                        if (!isApplication(ctx, 1)) return null;
                        String h = head(ctx);
                        return h.equals("raise") || h.equals("error") ? List.of(ctx.getChild(2)) : null;
                    }
                    case mutationtester.msl.MSLNode.DECLARE -> {
                        // a [name value] binding of let, let* or letrec, matched at its value
                        if (isLetBindingValue(ctx)) {
                            ParseTree p = ctx.getParent();
                            for (int i = 0; i < p.getChildCount(); i++) {
                                if (p.getChild(i) == ctx) return List.of(p.getChild(i - 1), ctx);
                            }
                        }
                        return ctx instanceof ISLParser.DefinitionContext d && d.getChildCount() == 5
                                && d.getChild(1).getText().equals("define") && !d.getChild(2).getText().equals("(")
                                ? List.of(d.getChild(2), d.getChild(3)) : null;
                    }
                    case mutationtester.msl.MSLNode.FOR_EACH -> {
                        // (for ([x xs]) body)
                        ParseTree[] binding = singleBinding(ctx, "for");
                        return binding == null ? null : List.of(binding[0], binding[1], ctx.getChild(3));
                    }
                    case mutationtester.msl.MSLNode.TRY -> {
                        // (with-handlers ([predicate handler]) body)
                        ParseTree[] binding = singleBinding(ctx, "with-handlers");
                        return binding == null ? null : java.util.Arrays.asList(ctx.getChild(3), binding[1], null);
                    }
                    case mutationtester.msl.MSLNode.COLLECTION -> {
                        return isApplication(ctx, 0) && (head(ctx).equals("list") || head(ctx).equals("vector"))
                                ? callParts(ctx).subList(1, ctx.getChildCount() - 2) : null;
                    }
                    case mutationtester.msl.MSLNode.OBJECT -> {
                        if (!isApplication(ctx, 0) || !head(ctx).equals("hash") || (ctx.getChildCount() - 3) % 2 != 0) return null;
                        List<ParseTree> entries = new ArrayList<>();
                        for (int i = 2; i + 1 < ctx.getChildCount() - 1; i += 2) {
                            entries.add(mutationtester.msl.CstNodes.span(List.of(ctx.getChild(i), ctx.getChild(i + 1))));
                        }
                        return entries;
                    }
                    case mutationtester.msl.MSLNode.ENTRY -> {
                        return mutationtester.msl.CstNodes.isSpan(ctx) && ctx.getChildCount() == 2
                                ? List.of(ctx.getChild(0), ctx.getChild(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.LAMBDA -> {
                        if (!isApplication(ctx, 1) || !(head(ctx).equals("lambda") || head(ctx).equals("\u03BB"))) return null;
                        List<ParseTree> parts = parameterNames(ctx, 2);
                        // One body form: ( lambda ( names... ) body ), or ( lambda () body )
                        int oneBody = ctx.getChild(2).getText().equals("()") ? 5 : (parts == null ? 0 : parts.size()) + 6;
                        if (parts == null || ctx.getChildCount() != oneBody) return null;
                        ParseTree body = ctx.getChild(ctx.getChildCount() - 2);
                        parts.add(body);
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.FUNCTION -> {
                        if (!(ctx instanceof ISLParser.DefinitionContext d) || !d.getChild(1).getText().equals("define")
                                || !d.getChild(2).getText().equals("(")) {
                            return null;
                        }
                        List<ParseTree> params = parameterNames(d, 2);
                        if (params == null || params.isEmpty()) return null;
                        List<ParseTree> parts = new ArrayList<>(params);
                        int bodyStart = 2 + params.size() + 2;
                        List<ParseTree> body = new ArrayList<>();
                        for (int i = bodyStart; i < d.getChildCount() - 1; i++) body.add(d.getChild(i));
                        if (body.isEmpty()) return null;
                        parts.add(mutationtester.msl.CstNodes.span(body));
                        return parts;
                    }
                    case mutationtester.msl.MSLNode.PARAMETER -> {
                        return isParameterName(ctx) ? List.of(ctx) : null;
                    }
                    case mutationtester.msl.MSLNode.TYPE_CHECK -> {
                        return isApplication(ctx, 1) && ctx.getChildCount() == 4 && head(ctx).endsWith("?")
                                ? List.of(ctx.getChild(2), ctx.getChild(1)) : null;
                    }
                    case mutationtester.msl.MSLNode.ARM -> {
                        // a (pattern value) clause of (match v clause ...)
                        ParseTree p = ctx.getParent();
                        return ctx.getChildCount() == 4 && isApplication(p, 1) && head(p).equals("match")
                                && p.getChild(2) != ctx ? List.of(ctx.getChild(1), ctx.getChild(2)) : null;
                    }
                    case mutationtester.msl.MSLNode.WHEN, mutationtester.msl.MSLNode.UNLESS -> {
                        return isApplication(ctx, 2) && ctx.getChildCount() == 5 && head(ctx).equals(pattern.kind())
                                ? List.of(ctx.getChild(2), ctx.getChild(3)) : null;
                    }
                    default -> {
                        return MSLScannerAdapter.super.matchNode(pattern, ctx);
                    }
                }
            }

            /** In Except: only, (if c v x) also reads as "if (c) x = v", covering clamps. */
            @Override
            public List<ParseTree> matchNodeInExcept(mutationtester.msl.MSLNode pattern, ParseTree ctx) {
                if (pattern.kind().equals(mutationtester.msl.MSLNode.ASSIGN)) {
                    ParseTree parent = ctx.getParent();
                    if (isApplication(parent, 2) && head(parent).equals("if") && parent.getChildCount() == 6
                            && parent.getChild(3) == ctx) {
                        return List.of(parent.getChild(4), ctx);
                    }
                }
                return MSLScannerAdapter.super.matchNodeInExcept(pattern, ctx);
            }

            /** (f a b): [f, a, b]. */
            @Override
            public List<ParseTree> callParts(ParseTree ctx) {
                if (!isApplication(ctx, 0) || !ctx.getChild(0).getText().equals("(")) return null;
                List<ParseTree> parts = new ArrayList<>();
                for (int i = 1; i < ctx.getChildCount() - 1; i++) parts.add(ctx.getChild(i));
                return parts;
            }

            @Override
            public String joinArguments(List<ParseTree> arguments, ParseTree ctx) {
                List<String> texts = new ArrayList<>();
                for (ParseTree argument : arguments) texts.add(boundText(argument));
                return String.join(" ", texts);
            }

            @Override
            public String renderConditional(String condition, String then, String otherwise, ParseTree ctx) {
                return "(if " + condition + " " + then + " " + otherwise + ")";
            }

            /** (list-ref l i), or the original's own -ref form. */
            @Override
            public String renderIndex(String collection, String index, ParseTree ctx) {
                String h = isApplication(ctx, 2) ? head(ctx) : "";
                String form = h.equals("vector-ref") || h.equals("string-ref") ? h : "list-ref";
                return "(" + form + " " + collection + " " + index + ")";
            }

            @Override
            public String renderMember(String object, String member, ParseTree ctx) {
                return null;
            }

            @Override
            public String renderMemberCall(String object, String method, List<String> arguments, ParseTree ctx) {
                return null;
            }

            // ── Literals and type names ──────────────────────────────────────

            @Override
            public mutationtester.msl.ValueKind valueKind(ParseTree ctx) {
                return Literals.valueKind(Literals.Lang.RACKET, ctx);
            }

            @Override
            public String convert(ParseTree node, String fromType, String toType, String toCondition, ParseTree ctx) {
                mutationtester.msl.ValueKind from = valueKind(node);
                if (from == null) return null;
                return Literals.convert(Literals.Lang.RACKET, getNodeText(node).strip(), from, toType, toCondition);
            }

            // ── Statements ────────────────────────────────────────────────

            /** (if c a b) always has an else; (when c s) is Racket's if without one. */
            @Override
            public List<ParseTree> ifParts(ParseTree ctx) {
                if (isApplication(ctx, 3) && head(ctx).equals("if") && ctx.getChildCount() == 6) {
                    return java.util.Arrays.asList(ctx.getChild(2), ctx.getChild(3), ctx.getChild(4));
                }
                if (isApplication(ctx, 2) && head(ctx).equals("when") && ctx.getChildCount() == 5) {
                    return java.util.Arrays.asList(ctx.getChild(2), ctx.getChild(3), null);
                }
                return null;
            }

            /** The value a function returns: the last form of a define or lambda body. */
            private boolean isFunctionResult(ParseTree ctx) {
                ParseTree parent = ctx.getParent();
                if (parent == null || parent.getChildCount() < 4 || parent.getChild(parent.getChildCount() - 2) != ctx) {
                    return false;
                }
                if (parent instanceof ISLParser.DefinitionContext d) {
                    return d.getChild(1).getText().equals("define") && d.getChild(2).getText().equals("(");
                }
                String h = isApplication(parent, 1) ? head(parent) : "";
                return h.equals("lambda") || h.equals("\u03BB");
            }

            /** [name, value] of the one binding in (form ([name value]) body). */
            private ParseTree[] singleBinding(ParseTree ctx, String form) {
                if (!isApplication(ctx, 2) || ctx.getChildCount() != 5 || !head(ctx).equals(form)) return null;
                ParseTree bindings = ctx.getChild(2);
                if (bindings.getChildCount() != 3) return null;
                ParseTree binding = bindings.getChild(1);
                return binding.getChildCount() == 4 ? new ParseTree[]{binding.getChild(1), binding.getChild(2)} : null;
            }

            @Override
            public boolean isBlock(ParseTree ctx) {
                // (begin ...), or a function's body forms
                if (isApplication(ctx, 1) && head(ctx).equals("begin") || mutationtester.msl.CstNodes.isSpan(ctx)) return true;
                // the one body form of (define (f ...) body)
                return ctx.getParent() instanceof ISLParser.DefinitionContext d && d.getChild(2).getText().equals("(")
                        && d.getChildCount() >= 5 && d.getChild(d.getChildCount() - 2) == ctx
                        && d.getChild(d.getChildCount() - 3).getText().equals(")");
            }

            @Override
            public String emptyBlock(ParseTree ctx) {
                return "(void)";
            }

            // ── Collections, functions and types ────────────────────────────

            /** The names in the "(...)" at {@code open}: (define (f a b) ...) or (lambda (a b) ...). */
            private List<ParseTree> parameterNames(ParseTree form, int open) {
                if (form.getChildCount() <= open) return null;
                if (form.getChild(open).getText().equals("()")) return new ArrayList<>();
                if (!form.getChild(open).getText().equals("(")) return null;
                List<ParseTree> names = new ArrayList<>();
                for (int i = open + 1; i < form.getChildCount() && !form.getChild(i).getText().equals(")"); i++) {
                    names.add(form.getChild(i));
                }
                return names;
            }

            /** A parameter name in a (define (f a b) ...) header (not f) or a lambda's list. */
            private boolean isParameterName(ParseTree ctx) {
                ParseTree p = ctx.getParent();
                if (p == null) return false;
                if (ctx instanceof ISLParser.NameContext && p.getParent() instanceof ISLParser.DefinitionContext) return false;
                ParseTree form = p instanceof ISLParser.NameContext ? p.getParent() : p;
                if (!(ctx instanceof TerminalNode) || form == null) return false;
                if (form instanceof ISLParser.DefinitionContext d && d.getChild(2).getText().equals("(")) {
                    List<ParseTree> names = parameterNames(d, 2);
                    return names != null && names.indexOf(ctx) > 0;
                }
                if (isApplication(form, 1) && (head(form).equals("lambda") || head(form).equals("\u03BB"))) {
                    List<ParseTree> names = parameterNames(form, 2);
                    return names != null && names.contains(ctx);
                }
                return false;
            }

            @Override
            public boolean isLambda(ParseTree ctx) {
                return isApplication(ctx, 1) && (head(ctx).equals("lambda") || head(ctx).equals("\u03BB"));
            }

            /** A predicate's name, the "type" of (number? x). */
            @Override
            public boolean isTypeName(ParseTree ctx) {
                ParseTree p = ctx.getParent();
                return p != null && isApplication(p, 1) && p.getChildCount() == 4 && p.getChild(1) == ctx
                        && ctx.getText().endsWith("?");
            }

            @Override
            public String emptyCollection(ParseTree ctx) {
                return isApplication(ctx, 0) && head(ctx).equals("hash") ? "(hash)" : "'()";
            }

            @Override
            public String renderCollection(List<String> elements, ParseTree ctx) {
                return elements.isEmpty() ? "'()" : "(list " + String.join(" ", elements) + ")";
            }

            @Override
            public String renderObject(List<String> entries, ParseTree ctx) {
                return entries.isEmpty() ? "(hash)" : "(hash " + String.join(" ", entries) + ")";
            }

            @Override
            public String renderEntry(String key, String value, ParseTree ctx) {
                return key + " " + value;
            }

            @Override
            public String renderLambda(List<String> parameters, String body, ParseTree ctx) {
                return "(lambda (" + String.join(" ", parameters) + ") " + body + ")";
            }

            @Override
            public String renderTypeCheck(String value, String type, ParseTree ctx) {
                return "(" + type + " " + value + ")";
            }

            // ── Remaining statements ────────────────────────────────────────

            @Override
            public String renderArm(String pattern, String value, ParseTree ctx) {
                return "(" + pattern + " " + value + ")";
            }

            // ── Language-only ───────────────────────────────────────────────

            /** The value of a [name value] binding in (let ([name value] ...) body). */
            private boolean isLetBindingValue(ParseTree ctx) {
                ParseTree p = ctx.getParent();
                if (p == null || p.getChildCount() < 4) return false;
                String form = p.getChild(1).getText();
                if (!form.equals("let") && !form.equals("let*") && !form.equals("letrec")) return false;
                for (int i = 2; i + 1 < p.getChildCount(); i++) {
                    if (p.getChild(i) == ctx) {
                        return p.getChild(i - 2).getText().equals("[") && p.getChild(i + 1).getText().equals("]");
                    }
                }
                return false;
            }

            // ── Increments ───────────────────────────────────────────────

            /** (add1 x) and (sub1 x), Racket's increment and decrement. */
            @Override
            public ParseTree incrementOperand(String operator, ParseTree ctx) {
                String function = operator.equals("++") ? "add1" : operator.equals("--") ? "sub1" : null;
                List<ParseTree> args = function == null ? null : callArguments(ctx, function);
                return args != null && args.size() == 1 ? args.get(0) : null;
            }

            @Override
            public String renderIncrement(String operator, String operand, ParseTree ctx) {
                return "(" + (operator.equals("++") ? "add1" : "sub1") + " " + operand + ")";
            }

            @Override
            public java.util.Set<String> supportedKinds() {
                return java.util.Set.of("binary", "predicate", "literal", "identifier", "call", "if", "assign",
                        "increment", "unary", "conditional", "index", "return", "declare", "for_each", "throw", "try",
                        "collection", "object", "entry", "lambda", "function", "parameter", "type_check", "arm", "when", "unless");
            }

            @Override
            public java.util.Set<String> supportedPlaceholders() {
                return java.util.Set.of("quantity", "match", "statement", "invocation_statement",
                        "return_value", "constructor_call", "non_void_method_call",
                        "equality_condition", "order_condition",
                        "number", "integer", "float", "based_integer", "rational", "string", "char", "boolean",
                        "condition", "name", "arguments", "block", "contains", "collection", "lambda", "type");
            }

            // ── PIT-style placeholders ───────────────────────────────────
            //
            // The scanner's own parse-tree visitor already finds Racket's
            // returned values, constructor calls, used call results and
            // conditions; a node fits a placeholder when the visitor would
            // mutate it. Single-line forms it leaves out are added below.

            private final java.util.Map<Mutator.MutatorType, java.util.Map<String, MutationPoint>> found =
                    new java.util.EnumMap<>(Mutator.MutatorType.class);

            private MutationPoint visitorPoint(Mutator.MutatorType type, ParseTree ctx) {
                java.util.Map<String, MutationPoint> points = found.computeIfAbsent(type, t -> {
                    java.util.Map<String, MutationPoint> byPosition = new java.util.HashMap<>();
                    for (MutationPoint p : visitorPoints(file, sourceCode, tree, t)) {
                        byPosition.putIfAbsent(p.getLineNumber() + ":" + p.getColumnNumber() + ":" + p.getOperator(), p);
                    }
                    return byPosition;
                });
                int[] pos = getTokenPosition(ctx);
                return points.get(pos[0] + ":" + pos[1] + ":" + getNodeText(ctx));
            }

            private Mutator.MutatorType visitorType(String placeholder) {
                return switch (placeholder) {
                    case "return_value" -> Mutator.MutatorType.EMPTY_RETURNS;
                    case "constructor_call" -> Mutator.MutatorType.CONSTRUCTOR_CALLS;
                    case "non_void_method_call" -> Mutator.MutatorType.NON_VOID_METHOD_CALLS;
                    case "equality_condition" -> Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF;
                    case "order_condition" -> Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF;
                    default -> null;
                };
            }

            @Override
            public boolean matchesPlaceholder(String type, ParseTree ctx) {
                if (type.equals("statement")) return ctx instanceof ISLParser.ExprContext;
                Mutator.MutatorType visitor = visitorType(type);
                if (visitor == null) return MSLScannerAdapter.super.matchesPlaceholder(type, ctx);
                if (!(ctx instanceof ISLParser.ExprContext)) return false;
                if (visitorPoint(visitor, ctx) != null) return true;
                return switch (type) {
                    case "constructor_call" -> isConstructorForm(ctx);
                    case "non_void_method_call" -> isDefinedCallValue(ctx);
                    case "equality_condition" -> isTest(ctx, EQUALITY_TESTS);
                    case "order_condition" -> isTest(ctx, ORDER_TESTS);
                    default -> false;
                };
            }

            /**
             * The visitor's replacement where it mutates the node; otherwise the
             * spec's own replacement ('() for constructors, #f for call results,
             * #t / #f for conditions).
             */
            @Override
            public OperatorEdit replace(ParseTree ctx, mutationtester.msl.MSLTree from, mutationtester.msl.MSLTree to,
                                        mutationtester.msl.MSLBindings bindings) {
                if (from instanceof mutationtester.msl.MSLPlaceholder p && visitorType(p.type()) != null) {
                    MutationPoint point = visitorPoint(visitorType(p.type()), ctx);
                    String replacement = point != null && !p.type().endsWith("_condition") ? point.getReplacement()
                            : render(to, bindings.cursor(), ctx);
                    if (replacement == null) return null;
                    int[] pos = getTokenPosition(ctx);
                    return new OperatorEdit(pos[0], pos[1], getNodeText(ctx), replacement);
                }
                return MSLScannerAdapter.super.replace(ctx, from, to, bindings);
            }

            @Override
            public String renderValue(String placeholderType, ParseTree ctx) {
                return placeholderType.equals("default_value") ? "#f" : null;
            }

            private boolean oneLine(ParseTree ctx) {
                Token start = CstNodes.start(ctx);
                Token stop = CstNodes.stop(ctx);
                return start != null && stop != null && start.getLine() == stop.getLine();
            }

            private java.util.Set<String> structNames;

            /**
             * (new thing% ...), a built-in like (make-hash), or a constructor of a struct declared
             * here or in a required file. Other make-... names are ordinary functions.
             */
            private boolean isConstructorForm(ParseTree ctx) {
                if (!isApplication(ctx, 0) || !oneLine(ctx) || !ctx.getChild(0).getText().equals("(")) return false;
                if (structNames == null) {
                    structNames = new java.util.HashSet<>(requiredStructConstructors(file));
                    collectStructNames(tree);
                }
                String name = head(ctx);
                return name.equals("new") || isBuiltInConstructor(name) || structNames.contains(name);
            }

            /** (struct posn ...) binds posn, (define-struct posn ...) binds make-posn. */
            private void collectStructNames(ParseTree node) {
                if (node instanceof ISLParser.DefinitionContext def && def.getChild(1).getText().equals("define-struct")
                        && !def.name().isEmpty()) {
                    structNames.add("make-" + def.name(0).getText());
                }
                if (isApplication(node, 1) && head(node).equals("struct")) {
                    structNames.add(node.getChild(2).getText());
                }
                if (isApplication(node, 1) && head(node).equals("define-struct")) {
                    structNames.add("make-" + node.getChild(2).getText());
                }
                for (int i = 0; i < node.getChildCount(); i++) collectStructNames(node.getChild(i));
            }

            /** The call in a one-line (define name (f ...)), unless f is a special form. */
            private boolean isDefinedCallValue(ParseTree ctx) {
                ParseTree def = ctx.getParent();
                if (!(def instanceof ISLParser.DefinitionContext) || def.getChildCount() != 5
                        || !def.getChild(1).getText().equals("define") || def.getChild(3) != ctx
                        || !oneLine(def) || !isApplication(ctx, 0) || !ctx.getChild(0).getText().equals("(")) {
                    return false;
                }
                return !java.util.Set.of("and", "or", "if", "cond", "case", "when", "unless", "let", "let*", "letrec",
                        "lambda", "λ", "begin", "quote", "quasiquote", "match", "for", "for/list", "do").contains(head(ctx));
            }

            /** A one-line test of if / when / unless or of a cond clause whose function is in tests. */
            private boolean isTest(ParseTree ctx, java.util.Set<String> tests) {
                if (!isApplication(ctx, 0) || !oneLine(ctx) || !ctx.getChild(0).getText().equals("(")
                        || !tests.contains(head(ctx))) {
                    return false;
                }
                ParseTree parent = ctx.getParent();
                if (isApplication(parent, 1) && parent.getChild(2) == ctx) {
                    String form = head(parent);
                    return form.equals("if") || form.equals("when") || form.equals("unless");
                }
                // [test body ...] in a cond: the test directly follows the '['.
                for (int i = 1; parent != null && i < parent.getChildCount(); i++) {
                    if (parent.getChild(i) == ctx) return parent.getChild(i - 1).getText().equals("[");
                }
                return false;
            }

            @Override
            public String notNullCheck(String subject) {
                return "(not (eq? " + subject + " #f))";
            }

            @Override
            public String notEmptyStringCheck(String subject) {
                return "(not (equal? " + subject + " \"\"))";
            }

            private java.util.Set<ParseTree> discarded;

            /**
             * A form whose value is thrown away: one before the last in a body (begin, when,
             * unless, lambda, let or define), or a call the visitor finds discarded, such as
             * one after begin0's first form or the value form of a discarded when.
             */
            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                if (!isApplication(ctx, 0) || NON_STATEMENT_FORMS.contains(head(ctx))) {
                    return false;
                }
                ParseTree parent = ctx.getParent();
                if (parent == null || !isApplicationOrDefinition(parent)) {
                    return false;
                }
                int index = indexInParent(ctx);
                int bodyStart = bodyStartIndex(parent);
                int lastForm = parent.getChildCount() - 2;
                if (bodyStart > 0 && index >= bodyStart && index < lastForm) return true;
                if (ISLMutationVisitor.NON_CALL_HEADS.contains(head(ctx)) || VALUE_FORMS.contains(head(ctx))
                        || head(parent).equals("struct") || head(parent).equals("define-struct")) {
                    return false;   // not a call (a struct's field list isn't either)
                }
                if (discarded == null) discarded = discardedExprs(file, sourceCode, tree);
                return discarded.contains(ctx);
            }

            @Override
            public String getEmptyStatement(ParseTree statement) {
                return "(void)";
            }

            private boolean isApplicationOrDefinition(ParseTree ctx) {
                return (ctx instanceof ISLParser.ExprContext || ctx instanceof ISLParser.DefinitionContext)
                        && ctx.getChildCount() >= 3 && ctx.getChild(0).getText().equals("(");
            }

            private int indexInParent(ParseTree ctx) {
                ParseTree parent = ctx.getParent();
                for (int i = 0; i < parent.getChildCount(); i++) {
                    if (parent.getChild(i) == ctx) return i;
                }
                return -1;
            }

            /** Index of the first body form, or -1 if the form has no body. */
            private int bodyStartIndex(ParseTree form) {
                String head = form.getChild(1).getText();
                switch (head) {
                    case "begin":
                        return 2;
                    case "when", "unless":
                        return 3;
                    case "lambda", "λ", "let", "let*", "letrec":
                        // The grammar lists the parameters or bindings flat, so
                        // [x (f a)] puts (f a) right after let: the body starts
                        // after the ')' that closes them. (lambda () ...) has a single '()' token.
                        if (form.getChild(2) instanceof TerminalNode empty && empty.getText().equals("()")) {
                            return 3;
                        }
                        if (form.getChild(2) instanceof TerminalNode open && open.getText().equals("(")) {
                            for (int i = 3; i < form.getChildCount(); i++) {
                                if (form.getChild(i) instanceof TerminalNode t && t.getText().equals(")")) {
                                    return i + 1;
                                }
                            }
                            return -1;
                        }
                        // Not parsed as that form (e.g. a named let): the
                        // bindings are one sub-form, after the name if any.
                        boolean named = head.equals("let") && !form.getChild(2).getText().startsWith("(")
                                && !form.getChild(2).getText().startsWith("[");
                        return named ? 4 : 3;
                    case "define":
                        // (define (f x) body...) - a grammar definition lists the
                        // header tokens flat, so the body starts after its ')'.
                        if (form.getChild(2) instanceof TerminalNode open && open.getText().equals("(")) {
                            for (int i = 3; i < form.getChildCount(); i++) {
                                if (form.getChild(i).getText().equals(")")) return i + 1;
                            }
                            return -1;
                        }
                        return form.getChild(2).getText().startsWith("(") ? 3 : -1;
                    default:
                        return -1;
                }
            }

            @Override
            public int[] getTokenPosition(ParseTree ctx) {
                if (ctx instanceof TerminalNode tn) {
                    Token s = tn.getSymbol();
                    return new int[]{s.getLine(), s.getCharPositionInLine()};
                }
                if (ctx instanceof ParserRuleContext prc) {
                    Token s = prc.getStart();
                    return new int[]{s.getLine(), s.getCharPositionInLine()};
                }
                return new int[]{0, 0};
            }

            @Override
            public ParseTree getRootContext() {
                return tree;
            }
        };
    }
}
