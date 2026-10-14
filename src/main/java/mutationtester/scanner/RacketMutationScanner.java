package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import mutationtester.msl.MSLScannerAdapter;

/**
 * Scans Racket (.rkt) source files for mutation points using the ISL ANTLR
 * grammar (racket-isl/ISL.g4) from the ANTLR grammars-v4 repository.
 * ISL (Intermediate Student Language) is a Racket teaching dialect documented
 * at https://docs.racket-lang.org/htdp-langs/intermediate-lam.html.
 * The grammar is sourced from the official ANTLR grammars-v4 repository and
 * adapted minimally to support standard #lang racket forms used in this project.
 * Supported mutation types:
 *   AOR_PLUS_TO_MINUS            : (+ a b) → (- a b)
 *   AOR_MINUS_TO_PLUS            : (- a b) → (+ a b)
 *   AOR_MULTIPLY_TO_DIVIDE       : (* a b) → (/ a b)
 *   AOR_DIVIDE_TO_MULTIPLY       : (/ a b) → (* a b)
 *   AOR_MODULO_TO_MULTIPLY       : (modulo a b) or (remainder a b) → (* a b)
 *   AOR_INCREMENT_TO_DECREMENT   : (add1 x) → (sub1 x)
 *   AOR_DECREMENT_TO_INCREMENT   : (sub1 x) → (add1 x)
 *   BOR_AND_TO_OR                : (bitwise-and x y) → (bitwise-ior x y)
 *   BOR_OR_TO_AND                : (bitwise-ior x y) → (bitwise-and x y)
 *   ROR_GT_TO_GE                 : (> a b) → (>= a b)
 *   ROR_GE_TO_GT                 : (>= a b) → (> a b)
 *   ROR_LT_TO_LE                 : (< a b) → (<= a b)
 *   ROR_LE_TO_LT                 : (<= a b) → (< a b)
 *   EQ_NEGATE                    : (= a b) → (not (= a b))
 *   LOGICAL_AND_TO_OR            : (and p q) → (or p q)
 *   LOGICAL_OR_TO_AND            : (or p q) → (and p q)
 *   CONDITIONAL_TRUE             : (if cond t f) → (if #t t f)
 *   CONDITIONAL_FALSE            : (if cond t f) → (if #f t f)
 *   NEGATE_CONDITIONALS          : (if cond ...) → (if (not cond) ...)
 *   REMOVE_CONDITIONALS_EQUAL_IF   : equality condition in if/cond/when/unless → #t
 *   REMOVE_CONDITIONALS_EQUAL_ELSE : equality condition in if/cond/when/unless → #f
 *   REMOVE_CONDITIONALS_ORDER_IF   : order condition in if/cond/when/unless → #t
 *   REMOVE_CONDITIONALS_ORDER_ELSE : order condition in if/cond/when/unless → #f
 *   STATEMENT_MUTATION           : (stmt ...) in begin → (void)
 *   VML_VOID_METHOD_CALL         : (stmt ...) in begin → removed entirely
 *   INEG_INVERT_NEGATIVE         : (- x) → x
 *   NLT_INTEGER_TO_FLOAT         : 5 → 5.0
 *   NLT_FLOAT_TO_INTEGER         : 5.0 → 5
 *   RVM_TRUE_TO_FALSE            : function returning #t → #f
 *   RVM_FALSE_TO_TRUE            : function returning #f → #t
 *   RVM_ZERO_TO_ONE              : function returning 0 → 1
 *   RVM_NONZERO_TO_ZERO          : function returning non-zero integer → 0
 *   RVM_OBJECT_TO_NULL           : function returning expression/identifier → '()
 *   RVM_PRIMITIVE_TO_ZERO        : function returning numeric literal → 0
 *   EMPTY_RETURNS                : function returning a string/list → "" / '()
 *   NON_VOID_METHOD_CALLS        : (f x) whose result is used → #f / 0 / "" / '()
 *   CONSTRUCTOR_CALLS            : (make-posn 1 2) or (posn 1 2) for a declared struct → '()
 *   REMOVE_LOGICAL_NEGATION      : (not p) → p
 *   JS_STRICT_EQ_TO_LOOSE_EQ     : (eq? a b) or (eqv? a b) → (equal? a b)
 */
public class RacketMutationScanner {

    // Matches `<operand> OP <operand>` inside an infix Except: template so it
    // can be rewritten to Racket's prefix form `(OP <operand> <operand>)`.
    // Operand alternatives: {quantity}, numeric literal, or identifier.
    // Multi-char comparison/equality operators come first so they win over
    // their single-char prefixes.
    private static final Pattern INFIX_OPERATOR_SEGMENT = Pattern.compile(
            "(\\{quantity\\}|-?\\d+(?:\\.\\d+)?|[A-Za-z_][A-Za-z0-9_]*)" +
                    "\\s*(<=|>=|==|!=|&&|\\|\\||<<|>>|\\+|-|\\*|/|<|>)\\s*" +
                    "(\\{quantity\\}|-?\\d+(?:\\.\\d+)?|[A-Za-z_][A-Za-z0-9_]*)"
    );

    // Rewrites the C-style guarded-assignment clamp form
    //   if (COND) {quantity} = VALUE
    // into Racket's if-expression form
    //   (if COND VALUE
    // so that the shared spec's Except clause suppresses Racket's
    // (if (<= n 0) 0 n) clamp pattern.
    private static final Pattern IF_ASSIGN_CLAMP = Pattern.compile(
            "if\\s*\\(\\s*(.+?)\\s*\\)\\s*\\{quantity\\}\\s*=\\s*(\\S+)"
    );

    // -------------------------------------------------------------------------
    // Supported types
    // -------------------------------------------------------------------------

    public static List<Mutator.MutatorType> getSupportedTypes() {
        return List.of(
                Mutator.MutatorType.AOR_PLUS_TO_MINUS,
                Mutator.MutatorType.AOR_MINUS_TO_PLUS,
                Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE,
                Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY,
                Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY,
                Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT,
                Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT,
                Mutator.MutatorType.BOR_AND_TO_OR,
                Mutator.MutatorType.BOR_OR_TO_AND,
                Mutator.MutatorType.ROR_GT_TO_GE,
                Mutator.MutatorType.ROR_GE_TO_GT,
                Mutator.MutatorType.ROR_LT_TO_LE,
                Mutator.MutatorType.ROR_LE_TO_LT,
                Mutator.MutatorType.EQ_NEGATE,
                Mutator.MutatorType.LOGICAL_AND_TO_OR,
                Mutator.MutatorType.LOGICAL_OR_TO_AND,
                Mutator.MutatorType.CONDITIONAL_TRUE,
                Mutator.MutatorType.CONDITIONAL_FALSE,
                Mutator.MutatorType.NEGATE_CONDITIONALS,
                Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF,
                Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE,
                Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF,
                Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_ELSE,
                Mutator.MutatorType.STATEMENT_MUTATION,
                Mutator.MutatorType.VML_VOID_METHOD_CALL,
                Mutator.MutatorType.REMOVE_LOGICAL_NEGATION,
                Mutator.MutatorType.JS_STRICT_EQ_TO_LOOSE_EQ,
                Mutator.MutatorType.EMPTY_RETURNS,
                Mutator.MutatorType.NON_VOID_METHOD_CALLS,
                Mutator.MutatorType.CONSTRUCTOR_CALLS,
                Mutator.MutatorType.INEG_INVERT_NEGATIVE,
                Mutator.MutatorType.NLT_INTEGER_TO_FLOAT,
                Mutator.MutatorType.NLT_FLOAT_TO_INTEGER,
                Mutator.MutatorType.RVM_TRUE_TO_FALSE,
                Mutator.MutatorType.RVM_FALSE_TO_TRUE,
                Mutator.MutatorType.RVM_ZERO_TO_ONE,
                Mutator.MutatorType.RVM_NONZERO_TO_ZERO,
                Mutator.MutatorType.RVM_OBJECT_TO_NULL,
                Mutator.MutatorType.RVM_PRIMITIVE_TO_ZERO,
                Mutator.MutatorType.EMPTY_RETURNS,
                Mutator.MutatorType.NON_VOID_METHOD_CALLS
        );
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        return scan(file, type, false);
    }

    /**
     * EMPTY_RETURNS for every returned value, not just strings and lists: each
     * tail value becomes the empty value of its inferred type (0, "", '(), #f,
     * or '() when the type can't be told). This is MSL's
     * {return_value} → {empty_value}, which Java and C# apply to every return
     * using the declared type.
     */
    public static List<MutationPoint> scanTypedEmptyReturns(File file) throws IOException {
        return scan(file, Mutator.MutatorType.EMPTY_RETURNS, true);
    }

    private static List<MutationPoint> scan(File file, Mutator.MutatorType type, boolean typedEmptyValues)
            throws IOException {
        String sourceCode = Files.readString(file.toPath());

        CharStream input = CharStreams.fromString(sourceCode);
        ISLLexer lexer = new ISLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ISLParser parser = new ISLParser(tokens);

        lexer.removeErrorListeners();
        lexer.addErrorListener(QuietAntlrErrorListener.INSTANCE);
        parser.removeErrorListeners();
        parser.addErrorListener(QuietAntlrErrorListener.INSTANCE);

        ParseTree tree = parser.program();


        ISLMutationVisitor visitor = new ISLMutationVisitor(file, type, sourceCode);
        visitor.typedEmptyValues = typedEmptyValues;
        if (type == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
            // Structs may be defined after their first use, so collect them up front
            visitor.collectStructConstructors(tree);
        }
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    // -------------------------------------------------------------------------
    // Visitor
    // -------------------------------------------------------------------------

    private static class ISLMutationVisitor extends ISLBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final String sourceCode;
        private final List<MutationPoint> mutationPoints = new ArrayList<>();

        // EMPTY_RETURNS: mutate every returned value by its inferred type
        // (see scanTypedEmptyReturns), not only strings and lists
        private boolean typedEmptyValues;

        private static final java.util.Set<String> SPECIAL_FORMS = java.util.Set.of(
                "if", "and", "or", "cond", "let", "let*", "letrec", "lambda", "λ",
                "begin", "begin0", "define", "define-struct", "provide", "require",
                "local", "shared", "recur", "delay", "set!", "time", "module+"
        );

        // Condition heads for REMOVE_CONDITIONALS_* are EQUALITY_TESTS and ORDER_TESTS
        // (outer class). Names containing =, < or > (string=?, char<?, ...) are not
        // single NAME tokens in the ISL lexer, so only the plain ones match here;
        // ScannerBackedRule's text fallback finds the rest for spec rules.

        private boolean isStatementHead(String text) {
            if (SPECIAL_FORMS.contains(text)) return false;
            return text.matches("[a-zA-Z_$%&!~^][a-zA-Z0-9_\\-?!.~^]*");
        }

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
                        checkOperatorToken(headToken, ctx);

                        if (mutationType == Mutator.MutatorType.NON_VOID_METHOD_CALLS) {
                            checkNonVoidCall(headToken, ctx);
                            markStatementExprs(headToken.getText(), ctx);
                        }

                        if (mutationType == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
                            checkConstructorCall(headToken, ctx);
                        }

                        if (mutationType == Mutator.MutatorType.REMOVE_LOGICAL_NEGATION) {
                            checkLogicalNegation(headToken, ctx);
                        }

                        // STATEMENT_MUTATION / VML_VOID_METHOD_CALL: flag non-last exprs in begin/begin0
                        if (mutationType == Mutator.MutatorType.STATEMENT_MUTATION
                                || mutationType == Mutator.MutatorType.VML_VOID_METHOD_CALL) {
                            String headText = headToken.getText();
                            if (headText.equals("begin") || headText.equals("begin0")) {
                                handleBeginStatements(ctx);
                            }
                        }
                    }
                }
            }

            return visitChildren(ctx);
        }

        private void handleBeginStatements(ISLParser.ExprContext ctx) {
            // ( begin expr+ ) — children: (, begin, expr..., )
            // last child is ')'; second-to-last is the return-value expr; rest are statements
            int childCount = ctx.getChildCount();
            for (int i = 2; i <= childCount - 3; i++) {
                ParseTree child = ctx.getChild(i);
                if (!(child instanceof ISLParser.ExprContext stmtCtx)) continue;
                if (stmtCtx.getChildCount() < 3) continue;
                if (!(stmtCtx.getChild(0) instanceof TerminalNode tn)
                        || !tn.getSymbol().getText().equals("(")) continue;
                Token stmtHead = extractToken(stmtCtx.getChild(1));
                if (stmtHead == null || !isStatementHead(stmtHead.getText())) continue;
                String stmtText = extractSourceText(stmtCtx);
                Token startTok = stmtCtx.getStart();
                String replacement = (mutationType == Mutator.MutatorType.VML_VOID_METHOD_CALL) ? "" : "(void)";
                String label = (mutationType == Mutator.MutatorType.VML_VOID_METHOD_CALL) ? "Void method call" : "Statement mutation";
                mutationPoints.add(new MutationPoint(
                        sourceFile,
                        startTok.getLine(),
                        startTok.getCharPositionInLine(),
                        stmtText, replacement,
                        mutationType,
                        label
                ));
            }
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
         * Checks a head-position token for operator mutation targets.
         */
        private void checkOperatorToken(Token token, ISLParser.ExprContext ctx) {
            String text = token.getText();
            int line    = token.getLine();
            int col     = token.getCharPositionInLine();

            switch (mutationType) {
                case AOR_PLUS_TO_MINUS -> {
                    if (text.equals("+"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "+", "-", mutationType));
                }
                case AOR_MINUS_TO_PLUS -> {
                    if (text.equals("-"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "-", "+", mutationType));
                }
                case ROR_GT_TO_GE -> {
                    if (text.equals(">"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, ">", ">=", mutationType));
                }
                case ROR_GE_TO_GT -> {
                    if (text.equals(">="))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, ">=", ">", mutationType));
                }
                case ROR_LT_TO_LE -> {
                    if (text.equals("<"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "<", "<=", mutationType));
                }
                case ROR_LE_TO_LT -> {
                    if (text.equals("<="))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "<=", "<", mutationType));
                }
                case EQ_NEGATE -> {
                    if (text.equals("="))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "=", "not =", mutationType));
                }
                case LOGICAL_AND_TO_OR -> {
                    if (text.equals("and"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "and", "or", mutationType));
                }
                case LOGICAL_OR_TO_AND -> {
                    if (text.equals("or"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "or", "and", mutationType));
                }
                case JS_STRICT_EQ_TO_LOOSE_EQ -> {
                    // eq? (identity) and eqv? are Racket's strict checks; equal? is the
                    // loose, structural one, like === → == in JavaScript
                    if (text.equals("eq?") || text.equals("eqv?"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, text, "equal?", mutationType));
                }
                case AOR_MULTIPLY_TO_DIVIDE -> {
                    if (text.equals("*"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "*", "/", mutationType));
                }
                case AOR_DIVIDE_TO_MULTIPLY -> {
                    if (text.equals("/"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "/", "*", mutationType));
                }
                case NEGATE_CONDITIONALS -> {
                    if (text.equals("if"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "if", "(not if)", mutationType));
                }
                case AOR_MODULO_TO_MULTIPLY -> {
                    if (text.equals("modulo") || text.equals("remainder"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, text, "*", mutationType));
                }
                case AOR_INCREMENT_TO_DECREMENT -> {
                    if (text.equals("add1"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "add1", "sub1", mutationType));
                }
                case AOR_DECREMENT_TO_INCREMENT -> {
                    if (text.equals("sub1"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "sub1", "add1", mutationType));
                }
                case INEG_INVERT_NEGATIVE -> {
                    // Unary minus: (- arg) has exactly 4 children: '(' '-' arg ')'
                    if (text.equals("-") && ctx.getChildCount() == 4) {
                        ParseTree argExpr = ctx.getChild(2);
                        String argText = extractSourceText(argExpr);
                        String fullExpr = extractSourceText(ctx);
                        int[] exprPos = getExprPosition(ctx);
                        mutationPoints.add(new MutationPoint(
                                sourceFile, exprPos[0], exprPos[1],
                                fullExpr, argText, mutationType));
                    }
                }
                case BOR_AND_TO_OR -> {
                    if (text.equals("bitwise-and"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "bitwise-and", "bitwise-ior", mutationType));
                }
                case BOR_OR_TO_AND -> {
                    if (text.equals("bitwise-ior"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "bitwise-ior", "bitwise-and", mutationType));
                }
                case REMOVE_CONDITIONALS_EQUAL_IF, REMOVE_CONDITIONALS_EQUAL_ELSE,
                     REMOVE_CONDITIONALS_ORDER_IF, REMOVE_CONDITIONALS_ORDER_ELSE -> {
                    handleRemoveConditionals(text, ctx);
                }
                case CONDITIONAL_TRUE, CONDITIONAL_FALSE -> {
                    // ISL: (if cond then else) — child(2) is the condition
                    if (text.equals("if") && ctx.getChildCount() >= 6) {
                        ParseTree condExpr = ctx.getChild(2);
                        String condText = extractSourceText(condExpr);
                        int[] pos = getExprPosition(condExpr);
                        String replacement = (mutationType == Mutator.MutatorType.CONDITIONAL_TRUE) ? "#t" : "#f";
                        mutationPoints.add(new MutationPoint(sourceFile, pos[0], pos[1], condText, replacement, mutationType));
                    }
                }

                default -> { }
            }
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

        /**
         * Checks a token for numeric literal mutation targets.
         * NLT_INTEGER_TO_FLOAT : 5   → 5.0
         * NLT_FLOAT_TO_INTEGER : 5.0 → 5
         */
        private void checkNumberToken(Token token) {
            if (token.getType() != ISLLexer.NUMBER) return;

            String text = token.getText();
            int line    = token.getLine();
            int col     = token.getCharPositionInLine();

            if (text.contains("/")) return;

            boolean isFloat   = text.contains(".");
            boolean isInteger = !isFloat;

            if (mutationType == Mutator.MutatorType.NLT_INTEGER_TO_FLOAT && isInteger) {
                mutationPoints.add(new MutationPoint(sourceFile, line, col, text, text + ".0", mutationType));
            } else if (mutationType == Mutator.MutatorType.NLT_FLOAT_TO_INTEGER && isFloat) {
                int dotIndex = text.indexOf('.');
                String intPart = dotIndex > 0 ? text.substring(0, dotIndex) : text;
                mutationPoints.add(new MutationPoint(sourceFile, line, col, text, intPart, mutationType));
            }
        }

        // -------------------------------------------------------------------------
        // RVM: Return Value Mutations — applied to the last expr of function bodies
        // -------------------------------------------------------------------------

        @Override
        public Void visitDefinition(ISLParser.DefinitionContext ctx) {
            // A function body's non-last exprs run only for their side effects
            if (mutationType == Mutator.MutatorType.NON_VOID_METHOD_CALLS
                    && ctx.getChildCount() >= 6
                    && ctx.getChild(2) instanceof TerminalNode paren
                    && paren.getText().equals("(")) {
                List<ISLParser.ExprContext> bodyExprs = ctx.expr();
                statementExprs.addAll(bodyExprs.subList(0, Math.max(0, bodyExprs.size() - 1)));
            }

            boolean emptyReturns = mutationType == Mutator.MutatorType.EMPTY_RETURNS;
            if ((isRVMMutation() || emptyReturns) && ctx.getChildCount() >= 6) {
                // Function definition: '(' 'define' '(' name NAME* ')' expr+ ')'
                // Distinguish from value definition by checking child(2) == '('
                ParseTree thirdChild = ctx.getChild(2);
                if (thirdChild instanceof TerminalNode tn3 && tn3.getText().equals("(")) {
                    List<ISLParser.ExprContext> bodyExprs = ctx.expr();
                    if (!bodyExprs.isEmpty()) {
                        ISLParser.ExprContext body = bodyExprs.get(bodyExprs.size() - 1);
                        if (emptyReturns) {
                            List<ISLParser.ExprContext> tails = new ArrayList<>();
                            collectTailExprs(body, tails);
                            tails.forEach(this::checkEmptyReturn);
                        } else {
                            checkReturnValueMutation(body);
                        }
                    }
                }
            }
            return visitChildren(ctx);
        }

        // -------------------------------------------------------------------------
        // EMPTY_RETURNS — PIT-style: replace a returned string/list with "" / '()
        // -------------------------------------------------------------------------

        // Calls whose result is a string. Names containing <, > or = (number->string,
        // list->string) are not single NAME tokens in the ISL lexer, so they're omitted.
        private static final java.util.Set<String> STRING_BUILDERS = java.util.Set.of(
                "string-append", "substring", "format", "string", "make-string",
                "string-upcase", "string-downcase", "string-titlecase",
                "string-join", "string-trim", "~a"
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
            String replacement = typedEmptyValues ? typedEmptyValue(returnExpr) : emptyReplacement(returnExpr);
            if (replacement == null) return;

            // Main.applyMutation rewrites a single line; skip values that span lines.
            if (returnExpr.getStart() == null || returnExpr.getStop() == null
                    || returnExpr.getStart().getLine() != returnExpr.getStop().getLine()) return;

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
            if (extractSourceText(ctx).replaceAll("\\s", "").equals("'()")) return null;

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

            // Main.applyMutation rewrites a single line; skip calls that span lines.
            if (call.getStart().getLine() != call.getStop().getLine()) return;

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
            List<ISLParser.ExprContext> exprs = ctx.expr();
            switch (head) {
                // ( begin stmt... value )
                case "begin" -> statementExprs.addAll(exprs.subList(0, Math.max(0, exprs.size() - 1)));
                // ( begin0 value stmt... )
                case "begin0" -> statementExprs.addAll(exprs.subList(Math.min(1, exprs.size()), exprs.size()));
                // ( when cond stmt... value ) — parsed as an application, so exprs(0) is the head
                case "when", "unless" -> {
                    if (exprs.size() > 3) statementExprs.addAll(exprs.subList(2, exprs.size() - 1));
                }
                // ( struct name (field ...) ) — the field list is not a call
                case "struct", "define-struct" -> statementExprs.addAll(exprs);
                // ( let ( [x v] ... ) stmt... value ) and ( lambda ( x ... ) stmt... value )
                case "let", "let*", "letrec", "lambda", "λ" -> {
                    List<ISLParser.ExprContext> body = bodyAfterBindings(ctx);
                    statementExprs.addAll(body.subList(0, Math.max(0, body.size() - 1)));
                }
                default -> { }
            }
        }

        /**
         * The body exprs of a let or lambda, which the grammar lists flat after the
         * bindings or parameters: everything after the ')' that closes them.
         */
        private List<ISLParser.ExprContext> bodyAfterBindings(ISLParser.ExprContext ctx) {
            List<ISLParser.ExprContext> body = new ArrayList<>();
            if (!(ctx.getChild(2) instanceof TerminalNode open) || !open.getText().equals("(")) return body;
            boolean inBody = false;
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

        // Constructor names of the structs declared in this file
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

            // make-NAME is Racket's naming convention for constructors, which also
            // covers structs declared in other modules and built-ins like make-hash
            String name = head.getText();
            boolean isConstructor = structConstructors.contains(name)
                    || (name.startsWith("make-") && name.length() > "make-".length());
            if (!isConstructor) return;

            // Main.applyMutation rewrites a single line; skip calls that span lines.
            if (call.getStart().getLine() != call.getStop().getLine()) return;

            // '() is Racket's null (as in RVM_OBJECT_TO_NULL)
            int[] pos = getExprPosition(call);
            mutationPoints.add(new MutationPoint(sourceFile, pos[0], pos[1],
                    extractSourceText(call), "'()", mutationType,
                    "Constructor call mutation"));
        }

        // -------------------------------------------------------------------------
        // REMOVE_LOGICAL_NEGATION — PIT-style: drop the negation (!p → p).
        // Racket negates with the not function, so (not p) → p.
        // -------------------------------------------------------------------------

        private void checkLogicalNegation(Token head, ISLParser.ExprContext call) {
            // (not p) parses as an application: exprs(0) is the head, exprs(1) the operand
            if (head.getType() != ISLLexer.NAME || !head.getText().equals("not")
                    || call.expr().size() != 2) return;

            // Main.applyMutation rewrites a single line; skip negations that span lines.
            if (call.getStart().getLine() != call.getStop().getLine()) return;

            ISLParser.ExprContext operand = call.expr(1);
            int[] pos = getExprPosition(call);
            mutationPoints.add(new MutationPoint(sourceFile, pos[0], pos[1],
                    extractSourceText(call), extractSourceText(operand), mutationType,
                    "Remove logical negation"));
        }

        private boolean isRVMMutation() {
            return mutationType == Mutator.MutatorType.RVM_TRUE_TO_FALSE
                    || mutationType == Mutator.MutatorType.RVM_FALSE_TO_TRUE
                    || mutationType == Mutator.MutatorType.RVM_ZERO_TO_ONE
                    || mutationType == Mutator.MutatorType.RVM_NONZERO_TO_ZERO
                    || mutationType == Mutator.MutatorType.RVM_OBJECT_TO_NULL
                    || mutationType == Mutator.MutatorType.RVM_PRIMITIVE_TO_ZERO;
        }

        private void checkReturnValueMutation(ISLParser.ExprContext returnExpr) {
            String exprText = extractSourceText(returnExpr);
            int[] pos = getExprPosition(returnExpr);
            int line = pos[0], col = pos[1];

            switch (mutationType) {
                case RVM_TRUE_TO_FALSE -> {
                    if (isBooleanToken(returnExpr, "#t", "#true", "#T"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, exprText, "#f", mutationType));
                }
                case RVM_FALSE_TO_TRUE -> {
                    if (isBooleanToken(returnExpr, "#f", "#false", "#F"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, exprText, "#t", mutationType));
                }
                case RVM_ZERO_TO_ONE -> {
                    if (isLiteralNumber(returnExpr, "0"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, exprText, "1", mutationType));
                }
                case RVM_NONZERO_TO_ZERO -> {
                    if (isNonZeroIntegerLiteral(returnExpr))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, exprText, "0", mutationType));
                }
                case RVM_OBJECT_TO_NULL -> {
                    if (isObjectReturn(returnExpr))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, exprText, "'()", mutationType));
                }
                case RVM_PRIMITIVE_TO_ZERO -> {
                    if (isNumericLiteralNonZero(returnExpr))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, exprText, "0", mutationType));
                }
                default -> { }
            }
        }

        private Token getSingleExprToken(ISLParser.ExprContext ctx) {
            if (ctx.getChildCount() == 1 && ctx.getChild(0) instanceof TerminalNode tn)
                return tn.getSymbol();
            return null;
        }

        private boolean isBooleanToken(ISLParser.ExprContext ctx, String... variants) {
            Token tok = getSingleExprToken(ctx);
            if (tok == null || tok.getType() != ISLLexer.BOOLEAN) return false;
            String text = tok.getText();
            for (String v : variants) { if (text.equals(v)) return true; }
            return false;
        }

        private boolean isLiteralNumber(ISLParser.ExprContext ctx, String value) {
            Token tok = getSingleExprToken(ctx);
            return tok != null && tok.getType() == ISLLexer.NUMBER && tok.getText().equals(value);
        }

        private boolean isNonZeroIntegerLiteral(ISLParser.ExprContext ctx) {
            Token tok = getSingleExprToken(ctx);
            if (tok == null || tok.getType() != ISLLexer.NUMBER) return false;
            String text = tok.getText();
            if (text.contains(".") || text.contains("/")) return false;
            try { return Long.parseLong(text) != 0; } catch (NumberFormatException e) { return false; }
        }

        private boolean isNumericLiteralNonZero(ISLParser.ExprContext ctx) {
            Token tok = getSingleExprToken(ctx);
            if (tok == null || tok.getType() != ISLLexer.NUMBER) return false;
            return !tok.getText().equals("0");
        }

        private boolean isObjectReturn(ISLParser.ExprContext ctx) {
            // Exclude: number, boolean, string literals
            Token tok = getSingleExprToken(ctx);
            if (tok != null) {
                int type = tok.getType();
                if (type == ISLLexer.NUMBER || type == ISLLexer.BOOLEAN || type == ISLLexer.STRING)
                    return false;
            }
            // Include: identifiers (NAME/SYMBOL) resolved via extractToken fallback
            Token resolved = extractToken(ctx);
            if (resolved != null) {
                int type = resolved.getType();
                if (type == ISLLexer.NAME || type == ISLLexer.SYMBOL)
                    return true;
            }
            // Include: paren expressions like (cons x y), (list ...), (add1 x), etc.
            if (ctx.getChildCount() >= 3) {
                ParseTree first = ctx.getChild(0);
                if (first instanceof TerminalNode tn && tn.getText().equals("("))
                    return true;
            }
            return false;
        }

        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }

        @Override
        public Void visitTerminal(TerminalNode node) {
            if (mutationType == Mutator.MutatorType.NLT_INTEGER_TO_FLOAT
                    || mutationType == Mutator.MutatorType.NLT_FLOAT_TO_INTEGER) {
                checkNumberToken(node.getSymbol());
            }
            return null;
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

    /** True if a call to this function is an equality test. */
    public static boolean isEqualityTest(String head) {
        return EQUALITY_TESTS.contains(head);
    }

    /** True if a call to this function is an order test. */
    public static boolean isOrderTest(String head) {
        return ORDER_TESTS.contains(head);
    }

    /** True for define-syntax-rule and the other macro-defining form names above. */
    public static boolean isMacroDefinitionForm(String head) {
        return MACRO_DEFINITION_FORMS.contains(head);
    }

    /** True for (define-syntax-rule ...) and the other forms above. */
    static boolean isMacroDefinition(ParseTree ctx) {
        return ctx instanceof ISLParser.ExprContext
                && ctx.getChildCount() >= 3
                && ctx.getChild(0).getText().equals("(")
                && isMacroDefinitionForm(ctx.getChild(1).getText());
    }

    /** Forms that define or declare something; removing them isn't a call removal. */
    private static final java.util.Set<String> NON_STATEMENT_FORMS = java.util.Set.of(
            "define", "define-values", "define-syntax", "define-struct", "struct",
            "require", "provide", "module", "module+", "set!");

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = CharStreams.fromString(sourceCode);
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
                if (amount.getText().matches("-\\d+")) return true;
                return isApplication(amount, 1) && amount.getChildCount() == 4 && head(amount).equals("-");
            }

            private String unnegate(ParseTree amount) {
                String text = getNodeText(amount);
                if (text.matches("-\\d+")) return text.substring(1);
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
                return !text.isEmpty() && !text.matches("[()\\[\\]']");
            }

            @Override
            public boolean isStringLiteral(ParseTree ctx) {
                return ctx != null && MSLScannerAdapter.isStringLiteral(getNodeText(ctx).strip(), false);
            }

            @Override
            public String translateExceptTemplate(String infixTemplate) {
                // First fold the C-style guarded-assignment clamp form into
                // Racket's (if cond value ...) form, then rewrite operator
                // subexpressions into prefix.
                String t = rewriteAll(IF_ASSIGN_CLAMP, infixTemplate,
                        m -> "(if " + m.group(1) + " " + m.group(2));
                t = rewriteAll(INFIX_OPERATOR_SEGMENT, t,
                        m -> "(" + m.group(2) + " " + m.group(1) + " " + m.group(3) + ")");
                return t;
            }

            private String rewriteAll(Pattern p, String input,
                                      java.util.function.Function<Matcher, String> fn) {
                Matcher m = p.matcher(input);
                StringBuilder sb = new StringBuilder();
                while (m.find()) {
                    m.appendReplacement(sb, Matcher.quoteReplacement(fn.apply(m)));
                }
                m.appendTail(sb);
                return sb.toString();
            }

            @Override
            public String getSourceLine(ParseTree ctx) {
                String[] lines = sourceCode.split("\n", -1);
                int[] pos = getTokenPosition(ctx);
                int idx = pos[0] - 1;   // ANTLR lines are 1-based
                return (idx >= 0 && idx < lines.length) ? lines[idx] : "";
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
            // Racket has no IsNullOrEmpty; with #f as "no string" the check is
            // (or (not s) (string=? s "")) or a variant.
            private static final List<Pattern> NULL_OR_EMPTY_IDIOMS = List.of(
                    Pattern.compile("\\(or\\s+\\(not\\s+([^\\s()\\[\\]\"]+)\\)\\s+\\((?:string=\\?|equal\\?)\\s+\\1\\s+\"\"\\)\\)"),
                    Pattern.compile("\\(or\\s+\\(not\\s+([^\\s()\\[\\]\"]+)\\)\\s+\\(zero\\?\\s+\\(string-length\\s+\\1\\)\\)\\)"));

            @Override
            public String getNullOrEmptySubject(ParseTree ctx) {
                return MSLScannerAdapter.matchNullOrEmptySubject(getNodeText(ctx), NULL_OR_EMPTY_IDIOMS);
            }

            @Override
            public String notNullCheck(String subject) {
                return "(not (eq? " + subject + " #f))";
            }

            @Override
            public String notEmptyStringCheck(String subject) {
                return "(not (equal? " + subject + " \"\"))";
            }

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
                return bodyStart > 0 && index >= bodyStart && index < lastForm;
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
                        // after the ')' that closes them.
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
            public String getSingleArgumentText(ParseTree ctx, String functionName) {
                String text = getNodeText(ctx);
                String prefix = "(" + functionName;
                if (!text.startsWith(prefix) || !text.endsWith(")")) return null;
                String inner = text.substring(prefix.length(), text.length() - 1).trim();
                if (inner.isEmpty() || hasMultipleTopLevelArgs(inner)) return null;
                return inner;
            }

            private boolean hasMultipleTopLevelArgs(String args) {
                int depth = 0;
                boolean seenArg = false;
                boolean inWhitespace = false;
                for (int i = 0; i < args.length(); i++) {
                    char c = args.charAt(i);
                    if (Character.isWhitespace(c)) {
                        if (depth == 0 && seenArg) inWhitespace = true;
                    } else {
                        if (c == '(' || c == '[') depth++;
                        else if (c == ')' || c == ']') depth--;
                        if (depth == 0) {
                            if (inWhitespace && seenArg) return true;
                            seenArg = true;
                            inWhitespace = false;
                        }
                    }
                }
                return false;
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
