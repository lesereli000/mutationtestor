package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.MSLScannerAdapter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                Mutator.MutatorType.INEG_INVERT_NEGATIVE,
                Mutator.MutatorType.NLT_INTEGER_TO_FLOAT,
                Mutator.MutatorType.NLT_FLOAT_TO_INTEGER,
                Mutator.MutatorType.RVM_TRUE_TO_FALSE,
                Mutator.MutatorType.RVM_FALSE_TO_TRUE,
                Mutator.MutatorType.RVM_ZERO_TO_ONE,
                Mutator.MutatorType.RVM_NONZERO_TO_ZERO,
                Mutator.MutatorType.RVM_OBJECT_TO_NULL,
                Mutator.MutatorType.RVM_PRIMITIVE_TO_ZERO
        );
    }

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
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

        private static final java.util.Set<String> SPECIAL_FORMS = java.util.Set.of(
                "if", "and", "or", "cond", "let", "let*", "letrec", "lambda", "λ",
                "begin", "begin0", "define", "define-struct", "provide", "require",
                "local", "shared", "recur", "delay", "set!", "time", "module+"
        );

        // Heads that make a condition an "equality" check (PIT REMOVE_CONDITIONALS_EQUAL_*).
        // Names containing =, < or > (string=?, char<?, ...) are not single NAME
        // tokens in the ISL lexer, so they can't be matched here.
        private static final java.util.Set<String> EQUALITY_OPS = java.util.Set.of(
                "=", "eq?", "eqv?", "equal?", "zero?", "null?", "empty?"
        );

        // Heads that make a condition an "order" check (PIT REMOVE_CONDITIONALS_ORDER_*)
        private static final java.util.Set<String> ORDER_OPS = java.util.Set.of(
                "<", ">", "<=", ">=", "positive?", "negative?"
        );

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
            if (!containsHead(cond, equality ? EQUALITY_OPS : ORDER_OPS)) return;

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
            if (isRVMMutation() && ctx.getChildCount() >= 6) {
                // Function definition: '(' 'define' '(' name NAME* ')' expr+ ')'
                // Distinguish from value definition by checking child(2) == '('
                ParseTree thirdChild = ctx.getChild(2);
                if (thirdChild instanceof TerminalNode tn3 && tn3.getText().equals("(")) {
                    List<ISLParser.ExprContext> bodyExprs = ctx.expr();
                    if (!bodyExprs.isEmpty()) {
                        checkReturnValueMutation(bodyExprs.get(bodyExprs.size() - 1));
                    }
                }
            }
            return visitChildren(ctx);
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
    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = CharStreams.fromString(sourceCode);
        ISLLexer lexer = new ISLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ISLParser parser = new ISLParser(tokens);
        ParseTree tree = parser.program();

        return new MSLScannerAdapter() {
            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if (ctx instanceof ISLParser.ExprContext && ctx.getChildCount() >= 4) {
                    if (ctx.getChild(0).getText().equals("(")) {
                        return ctx.getChild(1).getText().equals(mslOperator);
                    }
                }
                return false;
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                return ctx.getChild(2);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                return ctx.getChild(3);
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                return ctx.getChild(1);
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                String text = ctx.getText();

                if (ctx instanceof TerminalNode tn) {
                    Token symbol = tn.getSymbol();
                    return text.matches("-?\\d+(\\.\\d+)?") || text.matches("[a-zA-Z][a-zA-Z0-9-]*");
                }
                if (ctx.getChildCount() == 1) return isQuantity(ctx.getChild(0));
                return false;
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

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null && start.getLine() != stop.getLine())
                        return false;
                }
                String text = getNodeText(ctx);
                if (text == null || text.isBlank()) return false;
                if (!text.startsWith("(") || !text.endsWith(")")) return false;
                String inner = text.substring(1, text.length() - 1).trim();
                if (inner.isEmpty()) return false;
                int spaceIdx = inner.indexOf(' ');
                String head = spaceIdx < 0 ? inner : inner.substring(0, spaceIdx);
                return head.matches("[a-zA-Z_$%&!~^][a-zA-Z0-9_\\-?!.~^]*");
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