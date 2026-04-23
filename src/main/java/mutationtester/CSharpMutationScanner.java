package mutationtester;

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

public class CSharpMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        sourceCode = removeBom(sourceCode);

        if (isTypeSwapMutation(type)) {
            return scanTypeSwapMutations(file, sourceCode, type);
        }
        if (type == Mutator.MutatorType.REMOVE_INCREMENT) {
            return scanRemoveIncrementMutations(file, sourceCode);
        }
        if (type == Mutator.MutatorType.CONDITIONAL_TRUE || type == Mutator.MutatorType.CONDITIONAL_FALSE) {
            return scanConditionalMutations(file, sourceCode, type);
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

    private static List<MutationPoint> scanConditionalMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        String replacement = type == Mutator.MutatorType.CONDITIONAL_TRUE ? "true" : "false";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            addParenthesizedCondition(points, file, line, i + 1, type, replacement, "if");
            addParenthesizedCondition(points, file, line, i + 1, type, replacement, "while");
        }

        return points;
    }

    private static void addParenthesizedCondition(List<MutationPoint> points, File file, String line, int lineNumber,
                                                  Mutator.MutatorType type, String replacement, String keyword) {
        int searchFrom = 0;

        while (searchFrom < line.length()) {
            int keywordIndex = findKeywordOutsideStringOrComment(line, keyword, searchFrom);
            if (keywordIndex < 0) {
                return;
            }

            int openParen = line.indexOf('(', keywordIndex + keyword.length());
            if (openParen < 0 || isInStringOrComment(line, openParen)) {
                searchFrom = keywordIndex + keyword.length();
                continue;
            }

            int closeParen = findMatchingParen(line, openParen);
            if (closeParen < 0) {
                searchFrom = openParen + 1;
                continue;
            }

            String condition = line.substring(openParen + 1, closeParen).trim();
            int conditionColumn = line.indexOf(condition, openParen + 1);

            if (isValidConditionText(condition)) {
                points.add(new MutationPoint(file, lineNumber, conditionColumn, condition, replacement, type));
            }

            searchFrom = closeParen + 1;
        }
    }

    private static int findKeywordOutsideStringOrComment(String line, String keyword, int from) {
        int index = line.indexOf(keyword, from);

        while (index >= 0) {
            boolean beforeOk = index == 0 || !isIdentifierChar(line.charAt(index - 1));
            int afterIndex = index + keyword.length();
            boolean afterOk = afterIndex >= line.length() || !isIdentifierChar(line.charAt(afterIndex));

            if (beforeOk && afterOk && !isInStringOrComment(line, index)) {
                return index;
            }

            index = line.indexOf(keyword, index + keyword.length());
        }

        return -1;
    }

    private static int findMatchingParen(String line, int openParen) {
        int depth = 0;

        for (int i = openParen; i < line.length(); i++) {
            if (isInStringOrComment(line, i)) {
                continue;
            }

            char c = line.charAt(i);

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static int findQuestionMarkOutsideStringOrComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '?' && !isInStringOrComment(line, i)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean startsWithQuestionMark(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("? ");
    }

    private static boolean isValidConditionText(String condition) {
        if (condition == null || condition.isBlank()) {
            return false;
        }

        String trimmed = condition.trim();

        if (trimmed.equals("true") || trimmed.equals("false")) {
            return false;
        }

        if (trimmed.endsWith(";") || trimmed.endsWith("{") || trimmed.endsWith("}") || trimmed.endsWith("=>")) {
            return false;
        }

        if (trimmed.startsWith("?") || trimmed.startsWith(":")) {
            return false;
        }

        return true;
    }

    private static List<MutationPoint> scanRemoveIncrementMutations(File file, String sourceCode) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            for (int col = 0; col < line.length() - 1; col++) {
                if (isInStringOrComment(line, col)) {
                    continue;
                }

                String two = line.substring(col, col + 2);

                if (two.equals("++")) {
                    points.add(new MutationPoint(
                            file,
                            i + 1,
                            col,
                            "++",
                            "__INC_NOOP__",
                            Mutator.MutatorType.REMOVE_INCREMENT
                    ));
                    col++;
                } else if (two.equals("--")) {
                    points.add(new MutationPoint(
                            file,
                            i + 1,
                            col,
                            "--",
                            "__DEC_NOOP__",
                            Mutator.MutatorType.REMOVE_INCREMENT
                    ));
                    col++;
                }
            }
        }

        return points;
    }

    private static boolean isInStringOrComment(String line, int position) {
        boolean inString = false;
        char stringDelimiter = '\0';

        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);

            if (i > 0 && line.charAt(i - 1) == '\\') {
                continue;
            }

            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                    stringDelimiter = '\0';
                }
            }
        }

        if (inString) {
            return true;
        }

        int commentStart = line.indexOf("//");
        return commentStart >= 0 && commentStart < position;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
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

            String declaredType = matcher.group(2);
            String varName = matcher.group(3);
            String exprText = matcher.group(4).trim();

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
                    } else if (operator.equals("<") && mutationType == Mutator.MutatorType.ROR_LT_TO_LE) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "<",
                                "<=",
                                mutationType
                        ));
                    } else if (operator.equals("<=") && mutationType == Mutator.MutatorType.ROR_LE_TO_LT) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "<=",
                                "<",
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

        @Override
        public Void visitMultiplicative_expression(CSharpParser.Multiplicative_expressionContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("*") && mutationType == Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "*",
                                "/",
                                mutationType
                        ));
                    } else if (operator.equals("/") && mutationType == Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "/",
                                "*",
                                mutationType
                        ));
                    } else if (operator.equals("%") && mutationType == Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "%",
                                "*",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitAnd_expression(CSharpParser.And_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.BOR_AND_TO_OR) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("&")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "&",
                                "|",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitInclusive_or_expression(CSharpParser.Inclusive_or_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.BOR_OR_TO_AND) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("|")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "|",
                                "&",
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

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        sourceCode = removeBom(sourceCode);
        CharStream input = CharStreams.fromString(sourceCode);
        CSharpLexer lexer = new CSharpLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CSharpParser parser = new CSharpParser(tokens);
        ParseTree tree = parser.prog();

        return new MSLScannerAdapter() {
            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if (ctx.getChildCount() == 3) {
                    return ctx.getChild(1).getText().equals(mslOperator);
                }
                return false;
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                return ctx.getChild(0);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                return ctx.getChild(2);
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                if (ctx.getChildCount() >= 3) return ctx.getChild(1);
                return null;
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                if (ctx instanceof TerminalNode tn) {
                    Token symbol = tn.getSymbol();
                    int type = symbol.getType();
                    return type == CSharpLexer.Integer_Literal ||
                            type == CSharpLexer.Real_Literal ||
                            type == CSharpLexer.Simple_Identifier;
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