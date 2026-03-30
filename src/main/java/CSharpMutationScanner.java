import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSharpMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        sourceCode = removeBom(sourceCode);

        if (isTypeSwapMutation(type)) {
            return scanTypeSwapMutations(file, sourceCode, type);
        }

        CharStream input = CharStreams.fromString(sourceCode);
        CSharpLexer lexer = new CSharpLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CSharpParser parser = new CSharpParser(tokens);

        ParseTree tree = parser.prog();

        CSharpMutationVisitor visitor = new CSharpMutationVisitor(file, type);
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    private static boolean isTypeSwapMutation(Mutator.MutatorType type) {
        return type == Mutator.MutatorType.TYPE_INT_TO_DOUBLE
                || type == Mutator.MutatorType.TYPE_INT_TO_FLOAT
                || type == Mutator.MutatorType.TYPE_DOUBLE_TO_INT
                || type == Mutator.MutatorType.TYPE_FLOAT_TO_INT;
    }

    private static String removeBom(String sourceCode) {
        if (sourceCode != null && !sourceCode.isEmpty() && sourceCode.charAt(0) == '\uFEFF') {
            return sourceCode.substring(1);
        }
        return sourceCode;
    }

    private static List<MutationPoint> scanTypeSwapMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);

        Pattern pattern = Pattern.compile(
                "^(\\s*)(int|double|float)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+?)(;\\s*)$"
        );

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = pattern.matcher(line);

            if (!matcher.find()) {
                continue;
            }

            String indent = matcher.group(1);
            String declaredType = matcher.group(2);
            String varName = matcher.group(3);
            String exprText = matcher.group(4).trim();
            String semicolonPart = matcher.group(5);

            String original = declaredType + " " + varName + " = " + exprText;
            String replacement = getTypeSwapReplacement(declaredType, varName, exprText, type);

            if (replacement == null || replacement.equals(original)) {
                continue;
            }

            int column = line.indexOf(original);
            if (column < 0) {
                continue;
            }

            points.add(new MutationPoint(
                    file,
                    i + 1,
                    column,
                    original,
                    replacement,
                    type
            ));
        }

        return points;
    }

    private static String getTypeSwapReplacement(String declaredType, String varName, String exprText, Mutator.MutatorType type) {
        switch (type) {
            case TYPE_INT_TO_DOUBLE:
                if (declaredType.equals("int")) {
                    return "double " + varName + " = " + convertIntExpressionToDouble(exprText);
                }
                break;

            case TYPE_INT_TO_FLOAT:
                if (declaredType.equals("int")) {
                    return "float " + varName + " = " + convertIntExpressionToFloat(exprText);
                }
                break;

            case TYPE_DOUBLE_TO_INT:
                if (declaredType.equals("double")) {
                    return "int " + varName + " = (int)(" + exprText + ")";
                }
                break;

            case TYPE_FLOAT_TO_INT:
                if (declaredType.equals("float")) {
                    return "int " + varName + " = (int)(" + exprText + ")";
                }
                break;

            default:
                break;
        }

        return null;
    }

    private static String convertIntExpressionToDouble(String exprText) {
        return exprText.replaceAll("(?<![\\w.])(\\d+)(?![\\w.])", "$1.0");
    }

    private static String convertIntExpressionToFloat(String exprText) {
        return exprText.replaceAll("(?<![\\w.])(\\d+)(?![\\w.])", "$1.0f");
    }

    private static class CSharpMutationVisitor extends CSharpParserBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints;

        public CSharpMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
            this.mutationPoints = new ArrayList<>();
        }

        @Override
        public Void visitAdditive_expression(CSharpParser.Additive_expressionContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("+") && mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "+",
                                "-",
                                mutationType
                        ));
                    } else if (operator.equals("-") && mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "-",
                                "+",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitRelational_expression(CSharpParser.Relational_expressionContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals(">") && mutationType == Mutator.MutatorType.ROR_GT_TO_GE) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                ">",
                                ">=",
                                mutationType
                        ));
                    } else if (operator.equals(">=") && mutationType == Mutator.MutatorType.ROR_GE_TO_GT) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                ">=",
                                ">",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitEquality_expression(CSharpParser.Equality_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.EQ_NEGATE) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("==")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "==",
                                "!=",
                                mutationType
                        ));
                    } else if (operator.equals("!=")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "!=",
                                "==",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitConditional_and_expression(CSharpParser.Conditional_and_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.LOGICAL_AND_TO_OR) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("&&")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "&&",
                                "||",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitConditional_or_expression(CSharpParser.Conditional_or_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.LOGICAL_OR_TO_AND) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("||")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "||",
                                "&&",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }
    }
}