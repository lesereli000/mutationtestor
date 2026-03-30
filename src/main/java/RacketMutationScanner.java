import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.gui.TreeViewer;
import java.util.Arrays;


public class RacketMutationScanner {

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());

        // Build ANTLR parse tree
        CharStream input = CharStreams.fromString(sourceCode);
        SchemeLexer lexer = new SchemeLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        SchemeParser parser = new SchemeParser(tokens);

        ParseTree tree = parser.program();

// ANTLR tree viewer — same as JavaMutationScanner
        TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
        viewer.open();

        // Walk the tree with the mutation visitor
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

        /**
         * Visit every paren list: ( form* )
         * Checks whether the first child is an operator atom that matches
         * the requested mutation type.
         */
        @Override
        public Void visitParenList(SchemeParser.ParenListContext ctx) {
            checkHead(ctx.form());
            return visitChildren(ctx);
        }

        /**
         * Visit every bracket list: [ form* ]
         * Same logic as paren lists — Racket allows [] as an alternative
         * to () in certain contexts (e.g. let bindings).
         */
        @Override
        public Void visitBracketList(SchemeParser.BracketListContext ctx) {
            checkHead(ctx.form());
            return visitChildren(ctx);
        }

        /**
         * Inspects the first form in a list to see if it is an operator atom
         * matching the requested mutation type. If so, records a MutationPoint
         * at the token's source position.
         */
        private void checkHead(List<SchemeParser.FormContext> forms) {
            if (forms == null || forms.isEmpty()) return;

            SchemeParser.FormContext head = forms.get(0);

            // Head must be a bare atom (not a nested list or quoted form)
            if (head.atom() == null) return;

            SchemeParser.AtomContext atomCtx = head.atom();
            Token token = getAtomToken(atomCtx);
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
                    // Not implemented for Racket: negating = requires structural
                    // wrapping (not (= ...)) rather than a simple token swap.
                }
                case LOGICAL_AND_TO_OR -> {
                    // In Racket, logical and is the identifier "and"
                    if (text.equals("and"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "and", "or", mutationType));
                }
                case LOGICAL_OR_TO_AND -> {
                    // In Racket, logical or is the identifier "or"
                    if (text.equals("or"))
                        mutationPoints.add(new MutationPoint(sourceFile, line, col, "or", "and", mutationType));
                }
            }
        }

        /**
         * Extracts the single terminal token from an atom context.
         * An atom rule matches exactly one token, so we take child 0.
         */
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
