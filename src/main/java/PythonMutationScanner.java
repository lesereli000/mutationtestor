import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class PythonMutationScanner {

    /**
     * Scan a Python file for mutations using AST parsing
     */
    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());

        // Parse Python using ANTLR
        CharStream input = CharStreams.fromString(sourceCode);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);

        // Get parse tree starting from root() rule
        ParseTree tree = parser.root();

        // Create visitor based on mutation type
        PythonMutationVisitor visitor = new PythonMutationVisitor(file, type);
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    /**
     * Visitor that walks the Python AST and finds mutation points.
     * Ported from the standalone PythonMutationVisitor, adapted to produce
     * MutationPoint objects and filter by the requested MutatorType.
     */
    private static class PythonMutationVisitor extends PythonParserBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints;

        public PythonMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
            this.mutationPoints = new ArrayList<>();
        }

        /**
         * Detect int/float type annotations after a COLON (:).
         * This catches BOTH variable annotations (x: int = 5)
         * and function parameter annotations (def func(x: int)).
         */
        @Override
        public Void visitTest(PythonParser.TestContext ctx) {
            if (mutationType != Mutator.MutatorType.TYPE_INT_TO_FLOAT
                    && mutationType != Mutator.MutatorType.TYPE_FLOAT_TO_INT) {
                return visitChildren(ctx);
            }

            ParseTree parent = ctx.getParent();
            if (parent != null) {
                for (int i = 0; i < parent.getChildCount() - 1; i++) {
                    ParseTree child = parent.getChild(i);
                    if (child instanceof TerminalNode) {
                        Token token = ((TerminalNode) child).getSymbol();
                        if (token.getType() == PythonLexer.COLON) {
                            String type = ctx.getText().trim();

                            if (type.equals("int") && mutationType == Mutator.MutatorType.TYPE_INT_TO_FLOAT) {
                                Token typeToken = ctx.getStart();
                                addMutation(typeToken, "int", "float");
                            } else if (type.equals("float") && mutationType == Mutator.MutatorType.TYPE_FLOAT_TO_INT) {
                                Token typeToken = ctx.getStart();
                                addMutation(typeToken, "float", "int");
                            }
                            break;
                        }
                    }
                }
            }
            return visitChildren(ctx);
        }

        /**
         * Catch return annotations (-> int / -> float) inside funcdef.
         */
        @Override
        public Void visitFuncdef(PythonParser.FuncdefContext ctx) {
            if (mutationType != Mutator.MutatorType.TYPE_INT_TO_FLOAT
                    && mutationType != Mutator.MutatorType.TYPE_FLOAT_TO_INT) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount() - 1; i++) {
                ParseTree child = ctx.getChild(i);
                if (child instanceof TerminalNode) {
                    Token token = ((TerminalNode) child).getSymbol();
                    if (token.getType() == PythonLexer.ARROW) {
                        ParseTree typeNode = ctx.getChild(i + 1);
                        String returnType = typeNode.getText().trim();

                        Token typeToken = (typeNode instanceof ParserRuleContext)
                                ? ((ParserRuleContext) typeNode).getStart()
                                : token;

                        if (returnType.equals("int") && mutationType == Mutator.MutatorType.TYPE_INT_TO_FLOAT) {
                            addMutation(typeToken, "int", "float");
                        } else if (returnType.equals("float") && mutationType == Mutator.MutatorType.TYPE_FLOAT_TO_INT) {
                            addMutation(typeToken, "float", "int");
                        }
                        break;
                    }
                }
            }
            return visitChildren(ctx);
        }

        /**
         * Visit expressions to find arithmetic operators: +, -, *, /, //, %, **
         */
        @Override
        public Void visitExpr(PythonParser.ExprContext ctx) {
            if (ctx.op != null) {
                Token opToken = ctx.op;
                String operator = opToken.getText();

                switch (operator) {
                    case "+":
                        if (mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                            addMutation(opToken, "+", "-");
                        }
                        break;

                    case "-":
                        if (mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                            addMutation(opToken, "-", "+");
                        }
                        break;

                    case "*":
                        // No direct MutatorType for * mutations in the enum currently
                        break;

                    case "/":
                        // No direct MutatorType for / mutations in the enum currently
                        break;

                    case "//":
                        // Python integer division - no direct MutatorType currently
                        break;

                    case "%":
                        // No direct MutatorType for % mutations currently
                        break;

                    case "**":
                        // Python power - no direct MutatorType currently
                        break;
                }
            }

            return visitChildren(ctx);
        }

        /**
         * Visit logical_test to find logical operators: and, or
         */
        @Override
        public Void visitLogical_test(PythonParser.Logical_testContext ctx) {
            if (ctx.op != null) {
                Token opToken = ctx.op;
                String operator = opToken.getText();

                switch (operator) {
                    case "and":
                        if (mutationType == Mutator.MutatorType.LOGICAL_AND_TO_OR) {
                            addMutation(opToken, "and", "or");
                        }
                        break;

                    case "or":
                        if (mutationType == Mutator.MutatorType.LOGICAL_OR_TO_AND) {
                            addMutation(opToken, "or", "and");
                        }
                        break;
                }
            }

            return visitChildren(ctx);
        }

        /**
         * Visit comparison expressions to find relational operators
         */
        @Override
        public Void visitComparison(PythonParser.ComparisonContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    Token token = ((TerminalNode) child).getSymbol();
                    String operator = token.getText();

                    switch (operator) {
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

                        case "==":
                            if (mutationType == Mutator.MutatorType.EQ_NEGATE) {
                                addMutation(token, "==", "!=");
                            }
                            break;

                        case "!=":
                            if (mutationType == Mutator.MutatorType.EQ_NEGATE) {
                                addMutation(token, "!=", "==");
                            }
                            break;
                    }
                }
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

        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }
    }
}
