package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.Mutation;
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
 *   AOR_MULTIPLY_TO_DIVIDE : (* a b) → (/ a b)
 *   AOR_DIVIDE_TO_MULTIPLY : (/ a b) → (* a b)
 *   NEGATE_CONDITIONALS    : (if cond ...) → (if (not cond) ...)
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

        lexer.removeErrorListeners();
        lexer.addErrorListener(QuietAntlrErrorListener.INSTANCE);
        parser.removeErrorListeners();
        parser.addErrorListener(QuietAntlrErrorListener.INSTANCE);

        ParseTree tree = parser.program();


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

        /**
         * Visits every expression node.
         *
         * For operator mutations: checks if this expr is a paren application
         * '(' expr expr* ')' and if so inspects the head token.
         *
         * For and/or: ISL has explicit grammar rules '(' 'and' expr expr+ ')'
         * so the keyword is a direct terminal child at index 1.
         *
         * For NLT: scans all NUMBER tokens in the expression.
         */
        @Override
        public Void visitExpr(ISLParser.ExprContext ctx) {

            // Operato  r/keyword mutations
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
                        checkOperatorToken(headToken);
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
            return null;
        }

        /**
         * Checks a head-position token for operator mutation targets.
         */
        private void checkOperatorToken(Token token) {
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
                default -> { }
            }
        }

        /**
         * Checks a token for numeric literal mutation targets.
         *
         * NLT_INTEGER_TO_FLOAT : 5   → 5.0
         * NLT_FLOAT_TO_INTEGER : 5.0 → 5
         */
        private void checkNumberToken(Token token) {
            if (token.getType() != ISLLexer.NUMBER) return;

            String text = token.getText();
            int line    = token.getLine();
            int col     = token.getCharPositionInLine();

            // Skip rationals like 3/2 — not int or float
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
                // Racket function application: ( op arg1 arg2 )
                // Note: ISL grammar might have extra children for whitespaces depending on hidden channels,
                // but usually ANTLR tokens on the default channel are just the meaningful ones.
                // Based on visitor logic: ctx.getChild(1) is the head.
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
                // Racket numbers or names
                if (ctx instanceof TerminalNode tn) {
                    Token symbol = tn.getSymbol();
                    // We'd check ISLLexer.NUMBER etc if we had constants, 
                    // but we can check if it's numeric or alphanumeric
                    return text.matches("-?\\d+(\\.\\d+)?") || text.matches("[a-zA-Z][a-zA-Z0-9-]*");
                }
                if (ctx.getChildCount() == 1) return isQuantity(ctx.getChild(0));
                return false;
            }

            @Override
            public String getNodeText(ParseTree ctx) {
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
