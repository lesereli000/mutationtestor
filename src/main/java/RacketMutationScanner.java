import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.gui.TreeViewer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Scans Racket (.rkt) source files for mutation points using the ISL ANTLR
 * grammar (racket-isl/ISL.g4) from the ANTLR grammars-v4 repository.
 *
 * ISL (Intermediate Student Language) is a Racket teaching dialect documented
 * at https://docs.racket-lang.org/htdp-langs/intermediate-lam.html.
 * The grammar is sourced from the official ANTLR grammars-v4 repository and
 * adapted minimally to support standard #lang racket forms used in this project.
 *
 * Supported mutation types:
 *   AOR_PLUS_TO_MINUS      : (+ a b) → (- a b)
 *   AOR_MINUS_TO_PLUS      : (- a b) → (+ a b)
 *   ROR_GT_TO_GE           : (> a b) → (>= a b)
 *   ROR_GE_TO_GT           : (>= a b) → (> a b)
 *   ROR_LT_TO_LE           : (< a b) → (<= a b)
 *   ROR_LE_TO_LT           : (<= a b) → (< a b)
 *   EQ_NEGATE              : (= a b) → flagged at = token
 *   LOGICAL_AND_TO_OR      : (and p q) → (or p q)
 *   LOGICAL_OR_TO_AND      : (or p q) → (and p q)
 *   NLT_INTEGER_TO_FLOAT   : 5 → 5.0
 *   NLT_FLOAT_TO_INTEGER   : 5.0 → 5
 */
public class RacketMutationScanner {

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------


    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());

        CharStream input = CharStreams.fromString(sourceCode);
        ISLLexer lexer = new ISLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        ISLParser parser = new ISLParser(tokens);

        ParseTree tree = parser.program();

        // ANTLR tree viewer — mirrors JavaMutationScanner
        TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
        viewer.open();

        ISLMutationVisitor visitor = new ISLMutationVisitor(file, type);
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    // -------------------------------------------------------------------------
    // Visitor
    // -------------------------------------------------------------------------

    private static class ISLMutationVisitor extends ISLBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints = new ArrayList<>();

        ISLMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
        }

        // -- Expression visitor -----------------------------------------------

        /**
         * Visits every expression in the ISL grammar.
         * The ISL grammar rule for function application is:
         *   '(' expr expr* ')'
         * where the first expr is the operator in head position.
         *
         * For operator mutations we check if the head expr is a bare name
         * or operator token matching a mutation target.
         *
         * For NLT mutations we check NUMBER tokens anywhere in the expression.
         */
        @Override
        public Void visitExpr(ISLParser.ExprContext ctx) {
            // NLT: check every NUMBER token in this expression
            if (mutationType == Mutator.MutatorType.NLT_INTEGER_TO_FLOAT
                    || mutationType == Mutator.MutatorType.NLT_FLOAT_TO_INTEGER) {
                checkNumberTokens(ctx);
            }

            // Operator mutations: look for function application ( expr expr* )
            // The grammar rule '(' expr expr* ')' is the last expr alternative
            // Check if this expr is a paren application by looking for LPAREN
            if (ctx.getChildCount() >= 3) {
                ParseTree first = ctx.getChild(0);
                if (first instanceof TerminalNode &&
                        ((TerminalNode) first).getSymbol().getText().equals("(")) {
                    // Head is the first expr inside the parens
                    ParseTree head = ctx.getChild(1);
                    checkHead(head);
                }
            }

            // Also check and/or/if/cond which are separate grammar rules
            checkSpecialForms(ctx);

            return visitChildren(ctx);
        }

        // -- Special form checks ----------------------------------------------

        /**
         * ISL has explicit grammar rules for and/or/if/cond rather than
         * treating them as plain function applications. Check them here.
         */
        private void checkSpecialForms(ISLParser.ExprContext ctx) {
            if (ctx.getChildCount() < 2) return;

            ParseTree second = ctx.getChild(1);
            if (!(second instanceof TerminalNode)) return;

            String keyword = second.getText();
            Token token = ((TerminalNode) second).getSymbol();
            int line = token.getLine();
            int col  = token.getCharPositionInLine();

            switch (mutationType) {
                case LOGICAL_AND_TO_OR -> {
                    if (keyword.equals("and"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "and", "or", mutationType));
                }
                case LOGICAL_OR_TO_AND -> {
                    if (keyword.equals("or"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "or", "and", mutationType));
                }
                default -> { }
            }
        }

        // -- Head operator check ----------------------------------------------

        /**
         * Checks the head position of a function application for operator
         * mutation targets. In ISL/Racket, the head is always the operator:
         *   (+ a b)  → head is +
         *   (> x 0)  → head is >
         */
        private void checkHead(ParseTree head) {
            Token token = null;

            if (head instanceof TerminalNode) {
                token = ((TerminalNode) head).getSymbol();
            } else if (head instanceof ISLParser.ExprContext exprCtx
                    && exprCtx.getChildCount() == 1
                    && exprCtx.getChild(0) instanceof TerminalNode tn) {
                token = tn.getSymbol();
            }

            if (token == null) return;

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
                default -> { }
            }
        }

        // -- Numeric literal check --------------------------------------------

        /**
         * Scans all terminal tokens in an expression context for NUMBER tokens.
         * Applies to all numeric literals regardless of position.
         *
         * NLT_INTEGER_TO_FLOAT : 5   → 5.0
         * NLT_FLOAT_TO_INTEGER : 5.0 → 5
         */
        private void checkNumberTokens(ISLParser.ExprContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                if (!(child instanceof TerminalNode terminal)) continue;

                Token token = terminal.getSymbol();
                if (token.getType() != ISLLexer.NUMBER) continue;

                String text = token.getText();
                int line    = token.getLine();
                int col     = token.getCharPositionInLine();

                boolean isFloat   = text.contains(".");
                boolean isInteger = !isFloat && !text.contains("/");

                if (mutationType == Mutator.MutatorType.NLT_INTEGER_TO_FLOAT && isInteger) {
                    mutationPoints.add(new MutationPoint(sourceFile, line, col, text, text + ".0", mutationType));
                } else if (mutationType == Mutator.MutatorType.NLT_FLOAT_TO_INTEGER && isFloat) {
                    int dotIndex = text.indexOf('.');
                    String intPart = dotIndex > 0 ? text.substring(0, dotIndex) : text;
                    mutationPoints.add(new MutationPoint(sourceFile, line, col, text, intPart, mutationType));
                }
            }
        }

        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }
    }
}