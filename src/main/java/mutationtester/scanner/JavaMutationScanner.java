package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.Mutation;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import mutationtester.msl.MSLScannerAdapter;
import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class JavaMutationScanner {
    /**
     * Scan a Java file for mutations using AST parsing
     */
    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        return scanFile(file, type, false);
    }

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type, boolean showViewer)
            throws IOException {
        if (type == Mutator.MutatorType.CONDITIONAL_TRUE
                || type == Mutator.MutatorType.CONDITIONAL_FALSE
                || type == Mutator.MutatorType.REMOVE_LOGICAL_NEGATION
                || type == Mutator.MutatorType.EMPTY_RETURNS
                || type == Mutator.MutatorType.VML_VOID_METHOD_CALL
                || type == Mutator.MutatorType.NON_VOID_METHOD_CALLS
                || type == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
            return UniversalPitMutationScanner.scan(file, type);
        }
        String sourceCode = Files.readString(file.toPath());

        // Parse Java using ANTLR (same as MyParser)
        CharStream input = CharStreams.fromString(sourceCode);
        JavaLexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);

        // Get parse tree starting from program() rule (same as MyParser)
        ParseTree tree = parser.compilationUnit();

        if (showViewer) {
            TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
            viewer.open();
        }

        // Create visitor based on mutation type
        JavaMutationScanner.JavaMutationVisitor visitor = new JavaMutationScanner.JavaMutationVisitor(file, type);
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    /**
     * Visitor that walks the Java AST and finds mutation points
     */
    private static class JavaMutationVisitor extends JavaParserBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints;

        public JavaMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
            this.mutationPoints = new ArrayList<>();
        }

        /**
         * Visit binary operator expressions (+, -, &&, ||, *, /, etc.)
         */
        @Override
        public Void visitBinaryOperatorExpression(JavaParser.BinaryOperatorExpressionContext ctx) {
            // If there aren't exactly 3 children, it's not a simple binary operator (e.g.,
            // >> or >>>)
            if (ctx.getChildCount() != 3) {
                return super.visitBinaryOperatorExpression(ctx);
            }

            // Walk through all children of this expression
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                // Check if this child is a terminal node (operator)
                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    switch (operator) {
                        // AOR_PLUS_TO_MINUS: mutate + to -
                        case "+":
                            if (mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                                if (!containsStringLiteral(ctx)) {
                                    addMutation(token, "+", "-");
                                }
                            }
                            break;

                        // AOR_MINUS_TO_PLUS: mutate - to +
                        case "-":
                            if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                                addMutation(token, "-", "+");
                            }
                            break;

                        case "*":
                            if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                                addMutation(token, "*", "/");
                            }
                            break;

                        case "/":
                            if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                                addMutation(token, "/", "*");
                            }
                            break;

                        case "&&":
                            if (mutationType == Mutator.MutatorType.LOGICAL_AND_TO_OR) {
                                addMutation(token, "&&", "||");
                            }
                            break;

                        case "||":
                            if (mutationType == Mutator.MutatorType.LOGICAL_OR_TO_AND) {
                                addMutation(token, "||", "&&");
                            }
                            break;

                        case ">":
                            if (mutationType == Mutator.MutatorType.ROR_GT_TO_GE) {
                                addMutation(token, ">", ">=");
                            }
                            break;

                        case ">=":
                            if (mutationType == Mutator.MutatorType.ROR_GE_TO_GT) {
                                addMutation(token, ">=", ">");
                            }
                            break;

                        case "<":
                            if (mutationType == Mutator.MutatorType.ROR_LT_TO_LE) {
                                addMutation(token, "<", "<=");
                            }
                            break;

                        case "<=":
                            if (mutationType == Mutator.MutatorType.ROR_LE_TO_LT) {
                                addMutation(token, "<=", "<");
                            }
                            break;

                        case "&":
                            if (mutationType == Mutator.MutatorType.BOR_AND_TO_OR) {
                                addMutation(token, "&", "|");
                            }
                            break;

                        default:
                            System.out.println("Unrecognized operator: " + operator);
                            break;

                    }
                }
            }

            // Continue visiting children
            return visitChildren(ctx);
        }

        /**
         * Visit primitive types (int, long, double, float, etc.)
         */
        @Override
        public Void visitPrimitiveType(JavaParser.PrimitiveTypeContext ctx) {
            String typeText = ctx.getText();
            Token token = ctx.getStart();

            // Mapping of "from" type to "to" type based on mutator
            if (mutationType == Mutator.MutatorType.TYPE_INT_TO_LONG && typeText.equals("int")) {
                addMutation(token, "int", "long");
            } else if (mutationType == Mutator.MutatorType.TYPE_LONG_TO_INT && typeText.equals("long")) {
                addMutation(token, "long", "int");
            } else if (mutationType == Mutator.MutatorType.TYPE_INT_TO_DOUBLE && typeText.equals("int")) {
                addMutation(token, "int", "double");
            } else if (mutationType == Mutator.MutatorType.TYPE_INT_TO_FLOAT && typeText.equals("int")) {
                addMutation(token, "int", "float");
            } else if (mutationType == Mutator.MutatorType.TYPE_DOUBLE_TO_INT && typeText.equals("double")) {
                addMutation(token, "double", "int");
            } else if (mutationType == Mutator.MutatorType.TYPE_FLOAT_TO_INT && typeText.equals("float")) {
                addMutation(token, "float", "int");
            }

            return visitChildren(ctx);
        }

        private void addMutation(Token token, String operator, String replacement) {
            MutationPoint point = new MutationPoint(
                    sourceFile,
                    token.getLine(),
                    token.getCharPositionInLine(),
                    operator,
                    replacement,
                    mutationType);
            mutationPoints.add(point);
        }

        private boolean containsStringLiteral(ParseTree tree) {
            if (tree instanceof TerminalNode terminalNode) {
                int tokenType = terminalNode.getSymbol().getType();
                return tokenType == JavaLexer.STRING_LITERAL || tokenType == JavaLexer.TEXT_BLOCK;
            }

            for (int i = 0; i < tree.getChildCount(); i++) {
                if (containsStringLiteral(tree.getChild(i))) {
                    return true;
                }
            }
            return false;
        }

        public List<MutationPoint> getMutationPoints() {
            // System.out.println(mutationPoints);
            return mutationPoints;
        }
    }
    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        // Pre-split for O(1) line lookups in getSourceLine()
        // ANTLR line numbers are 1-based; index 0 = line 1
        final String[] sourceLines = sourceCode.split("\n", -1);
        CharStream input = CharStreams.fromString(sourceCode);
        JavaLexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        ParseTree tree = parser.compilationUnit();

        return new MSLScannerAdapter() {
            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if (!(ctx instanceof JavaParser.BinaryOperatorExpressionContext binCtx)) return false;
                
                // Case 1: Operator is a single token (bop is set)
                if (binCtx.bop != null) {
                    return binCtx.bop.getText().equals(mslOperator);
                }
                
                // Case 2: Operator is multiple tokens (e.g., >>, >>>, <<)
                // We reconstruct the operator text from the middle children
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i < binCtx.getChildCount() - 1; i++) {
                    sb.append(binCtx.getChild(i).getText());
                }
                return sb.toString().equals(mslOperator);
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                return ctx.getChild(0);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                // Right operand is always the last child
                return ctx.getChild(ctx.getChildCount() - 1);
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                // For multi-token operators, we return the first token as the "point"
                if (ctx.getChildCount() >= 3) {
                    return ctx.getChild(1);
                }
                return null;
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                
                // If it's a TerminalNode, check if it's a literal or identifier
                if (ctx instanceof TerminalNode tn) {
                    Token symbol = tn.getSymbol();
                    int type = symbol.getType();
                    return type == JavaLexer.DECIMAL_LITERAL || 
                           type == JavaLexer.FLOAT_LITERAL ||
                           type == JavaLexer.HEX_LITERAL ||
                           type == JavaLexer.OCT_LITERAL ||
                           type == JavaLexer.BINARY_LITERAL ||
                           type == JavaLexer.IDENTIFIER;
                }
                
                // Handle wrapper contexts: if it has 1 child, recurse
                if (ctx instanceof ParserRuleContext prc) {
                    if (prc.getChildCount() == 1) return isQuantity(prc.getChild(0));
                    
                    // ( expression )
                    if (prc.getChildCount() == 3 && 
                        prc.getChild(0).getText().equals("(") && 
                        prc.getChild(2).getText().equals(")")) {
                        return true; // Parenthesized expression is a quantity
                    }
                }
                
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
            public String getSourceLine(ParseTree ctx) {
                ParseTree p = ctx;
                while (p != null && !(p instanceof mutationtester.JavaParser.StatementContext)) {
                    p = p.getParent();
                }
                if (p != null) {
                    org.antlr.v4.runtime.ParserRuleContext prc = (org.antlr.v4.runtime.ParserRuleContext) p;
                    if (prc.getStart() != null && prc.getStop() != null) {
                        int start = prc.getStart().getStartIndex();
                        int stop = prc.getStop().getStopIndex();
                        if (start >= 0 && stop >= start) {
                            return prc.getStart().getInputStream().getText(new org.antlr.v4.runtime.misc.Interval(start, stop));
                        }
                    }
                }
                
                int[] pos = getTokenPosition(ctx);
                int lineIndex = pos[0] - 1; // ANTLR line numbers are 1-based
                if (lineIndex >= 0 && lineIndex < sourceLines.length) {
                    return sourceLines[lineIndex];
                }
                return "";
            }

            @Override
            public ParseTree getRootContext() {
                return tree;
            }
        };
    }
}
