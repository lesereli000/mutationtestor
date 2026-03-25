import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class JavaMutationScanner {
    /**
     * Scan a Java file for mutations using AST parsing
     */
    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());

        // Parse Java using ANTLR (same as MyParser)
        CharStream input = CharStreams.fromString(sourceCode);
        JavaLexer lexer = new JavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);

        // Get parse tree starting from program() rule (same as MyParser)
        ParseTree tree = parser.compilationUnit();
        TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
        viewer.open();

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
            // Walk through all children of this expression
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                // Check if this child is a terminal node (operator)
                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    switch(operator) {
                        // AOR_PLUS_TO_MINUS: mutate + to -
                        case "+":
                            if (mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                                addMutation(token, "+", "-");
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

//                        case "<":
//                            if (mutationType == Mutator.MutatorType.ROR_LT_TO_LE) {
//                                addMutation(token, "<", "<=");
//                            }
//                            break;
//
//                        case "<=":
//                            if (mutationType == Mutator.MutatorType.ROR_LE_TO_LT) {
//                                addMutation(token, "<=", "<");
//                            }
//                            break;

                        default:
                            System.out.println("Unrecognized operator: " + operator);
                            break;

                    }
                }
            }

            // Continue visiting children
            return visitChildren(ctx);
        }

        private void addMutation(Token token, String operator, String replacement) {
            MutationPoint point = new MutationPoint(
                    sourceFile,
                    token.getLine(),
                    token.getCharPositionInLine(),
                    operator,
                    replacement,
                    mutationType
            );
            mutationPoints.add(point);
        }

        public List<MutationPoint> getMutationPoints() {
//            System.out.println(mutationPoints);
            return mutationPoints;
        }
    }
}
