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
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class PythonMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        if (!supportsMutation(type)) {
            return Collections.emptyList();
        }

        String sourceCode = Files.readString(file.toPath());

        CharStream input = CharStreams.fromString(sourceCode);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);

        ParseTree tree = parser.root();

        PythonMutationVisitor visitor = new PythonMutationVisitor(file, sourceCode, type);
        visitor.visit(tree);
        return visitor.getMutationPoints();
    }

    public static List<Mutator.MutatorType> getSupportedTypes() {
        return List.of(
                Mutator.MutatorType.AOR_PLUS_TO_MINUS,
                Mutator.MutatorType.AOR_MINUS_TO_PLUS,
                Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE,
                Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY,
                Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY,
                Mutator.MutatorType.BOR_AND_TO_OR,
                Mutator.MutatorType.BOR_OR_TO_AND,
                Mutator.MutatorType.ROR_LT_TO_LE,
                Mutator.MutatorType.ROR_LE_TO_LT,
                Mutator.MutatorType.ROR_GT_TO_GE,
                Mutator.MutatorType.ROR_GE_TO_GT,
                Mutator.MutatorType.EQ_NEGATE,
                Mutator.MutatorType.LOGICAL_AND_TO_OR,
                Mutator.MutatorType.LOGICAL_OR_TO_AND,
                Mutator.MutatorType.TYPE_INT_TO_FLOAT,
                Mutator.MutatorType.TYPE_FLOAT_TO_INT
        );
    }

    private static boolean supportsMutation(Mutator.MutatorType type) {
        return type == Mutator.MutatorType.AOR_PLUS_TO_MINUS
                || type == Mutator.MutatorType.AOR_MINUS_TO_PLUS
                || type == Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE
                || type == Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY
                || type == Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY
                || type == Mutator.MutatorType.BOR_AND_TO_OR
                || type == Mutator.MutatorType.BOR_OR_TO_AND
                || type == Mutator.MutatorType.ROR_LT_TO_LE
                || type == Mutator.MutatorType.ROR_LE_TO_LT
                || type == Mutator.MutatorType.ROR_GT_TO_GE
                || type == Mutator.MutatorType.ROR_GE_TO_GT
                || type == Mutator.MutatorType.EQ_NEGATE
                || type == Mutator.MutatorType.LOGICAL_AND_TO_OR
                || type == Mutator.MutatorType.LOGICAL_OR_TO_AND
                || type == Mutator.MutatorType.TYPE_INT_TO_FLOAT
                || type == Mutator.MutatorType.TYPE_FLOAT_TO_INT;
    }
    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = CharStreams.fromString(sourceCode);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);
        ParseTree tree = parser.root();

        return new MSLScannerAdapter() {
            private final Pattern invocationPattern = Pattern.compile(
                    "(await\\s+)?[A-Za-z_][A-Za-z0-9_.]*\\s*\\(.*\\)$",
                    Pattern.DOTALL
            );

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                // Handle ExprContext (arithmetic, bitwise)
                if (ctx instanceof PythonParser.ExprContext exprCtx) {
                    return exprCtx.op != null && exprCtx.op.getText().equals(mslOperator);
                }
                // Handle ComparisonContext
                if (ctx instanceof PythonParser.ComparisonContext compCtx) {
                    // Comparison can have multiple operators, but for MSL we likely want simple ones
                    if (compCtx.getChildCount() == 3) {
                        return compCtx.getChild(1).getText().equals(mslOperator);
                    }
                }
                // Handle logical_test
                if (ctx instanceof PythonParser.Logical_testContext logCtx) {
                    return logCtx.op != null && logCtx.op.getText().equals(mslOperator);
                }
                return false;
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                return ctx.getChild(0);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                return ctx.getChild(ctx.getChildCount() - 1);
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
                    return type == PythonLexer.DECIMAL_INTEGER ||
                           type == PythonLexer.FLOAT_NUMBER ||
                           type == PythonLexer.OCT_INTEGER ||
                           type == PythonLexer.HEX_INTEGER ||
                           type == PythonLexer.BIN_INTEGER ||
                           type == PythonLexer.NAME;
                }
                if (ctx.getChildCount() == 1) return isQuantity(ctx.getChild(0));
                return false;
            }

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                if (!(ctx instanceof PythonParser.Expr_stmtContext exprStmt)) {
                    return false;
                }
                // Assignments are not invocation statements
                if (exprStmt.assign_part() != null) {
                    return false;
                }
                // Skip multi-line calls
                if (exprStmt.getStart() != null && exprStmt.getStop() != null
                        && exprStmt.getStart().getLine() != exprStmt.getStop().getLine()) {
                    return false;
                }
                String text = getNodeText(ctx);
                if (text == null || text.isBlank()) {
                    return false;
                }
                return invocationPattern.matcher(text.strip()).matches();
            }

            @Override
            public String getSingleArgumentText(ParseTree ctx, String functionName) {
                String text = getNodeText(ctx);
                if (text == null) return null;
                String prefix = functionName + "(";
                if (!text.startsWith(prefix) || !text.endsWith(")")) {
                    return null;
                }
                String inner = text.substring(prefix.length(), text.length() - 1).trim();
                if (inner.isEmpty() || hasTopLevelComma(inner)) {
                    return null;
                }
                return inner;
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
            public int[] getTokenPosition(ParseTree ctx) {
                if (ctx instanceof TerminalNode tn) {
                    Token s = tn.getSymbol();
                    return new int[]{s.getLine(), s.getCharPositionInLine()};
                }
                if (ctx instanceof ParserRuleContext prc) {
                    Token s = prc.getStart();
                    if (s != null) {
                        return new int[]{s.getLine(), s.getCharPositionInLine()};
                    }
                }
                return new int[]{0, 0};
            }

            @Override
            public ParseTree getRootContext() {
                return tree;
            }

            private boolean hasTopLevelComma(String text) {
                int parenDepth = 0;
                int bracketDepth = 0;
                int braceDepth = 0;
                boolean inString = false;
                char stringDelimiter = '\0';

                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    if (i > 0 && text.charAt(i - 1) == '\\') {
                        continue;
                    }
                    if (inString) {
                        if (c == stringDelimiter) {
                            inString = false;
                            stringDelimiter = '\0';
                        }
                        continue;
                    }
                    if (c == '"' || c == '\'') {
                        inString = true;
                        stringDelimiter = c;
                        continue;
                    }
                    switch (c) {
                        case '(' -> parenDepth++;
                        case ')' -> parenDepth--;
                        case '[' -> bracketDepth++;
                        case ']' -> bracketDepth--;
                        case '{' -> braceDepth++;
                        case '}' -> braceDepth--;
                        case ',' -> {
                            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                                return true;
                            }
                        }
                        default -> {
                        }
                    }
                }
                return false;
            }
        };
    }
}
