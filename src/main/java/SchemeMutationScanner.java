import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.gui.TreeViewer;
import java.util.Arrays;


public class SchemeMutationScanner {

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());

        CharStream input = CharStreams.fromString(sourceCode);
        SchemeLexer lexer = new SchemeLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SchemeParser parser = new SchemeParser(tokens);

        ParseTree tree = parser.program();

        // ANTLR tree viewer — mirrors JavaMutationScanner
        TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
        viewer.open();

        RacketMutationVisitor visitor = new RacketMutationVisitor(file, type);
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    // -------------------------------------------------------------------------
    // Visitor
    // -------------------------------------------------------------------------

    private static class RacketMutationVisitor extends SchemeParserBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints = new ArrayList<>();

        RacketMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
        }

        // -- List visitors ----------------------------------------------------

        @Override
        public Void visitParenList(SchemeParser.ParenListContext ctx) {
            checkHead(ctx.form());
            return visitChildren(ctx);
        }

        @Override
        public Void visitBracketList(SchemeParser.BracketListContext ctx) {
            checkHead(ctx.form());
            return visitChildren(ctx);
        }

        // -- Atom visitor (for numeric literal mutations) ----------------------

        /**
         * Visits every atom in the tree. Used for NLT mutations where we need
         * to flag numeric literals regardless of their position.
         */
        @Override
        public Void visitAtom(SchemeParser.AtomContext ctx) {
            if (mutationType == Mutator.MutatorType.NLT_INTEGER_TO_FLOAT
                    || mutationType == Mutator.MutatorType.NLT_FLOAT_TO_INTEGER) {
                checkNumericLiteral(ctx);
            }
            return visitChildren(ctx);
        }

        // -- Head operator check ----------------------------------------------

        /**
         * Inspects the first form of a list to find operator mutation targets.
         */
        private void checkHead(List<SchemeParser.FormContext> forms) {
            if (forms == null || forms.isEmpty()) return;

            SchemeParser.FormContext head = forms.get(0);
            if (head.atom() == null) return;

            Token token = getAtomToken(head.atom());
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
                case EQ_NEGATE -> {
                    // Racket numeric equality is `=`.
                    // Flagged here; mutator is responsible for wrapping as (not (= ...))
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
                default -> { }
            }
        }

        // -- Numeric literal check --------------------------------------------

        /**
         * Checks an atom for numeric literal mutation targets.
         * Applies to all numeric literals regardless of position.
         *
         * NLT_INTEGER_TO_FLOAT : 5 → 5.0
         * NLT_FLOAT_TO_INTEGER : 5.0 → 5
         */
        private void checkNumericLiteral(SchemeParser.AtomContext ctx) {
            Token token = getAtomToken(ctx);
            if (token == null) return;

            if (token.getType() != SchemeLexer.NUMBER) return;

            String text = token.getText();
            int line    = token.getLine();
            int col     = token.getCharPositionInLine();

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

        // -- Helper -----------------------------------------------------------

        private Token getAtomToken(SchemeParser.AtomContext ctx) {
            if (ctx.getChildCount() == 0) return null;
            ParseTree child = ctx.getChild(0);
            if (child instanceof TerminalNode terminal) {
                return terminal.getSymbol();
            }
            return null;
        }

        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }
    }
}
