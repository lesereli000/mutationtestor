package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.PythonLexer;
import mutationtester.PythonParser;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.MSLScannerAdapter;

public class PythonMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        if (type == Mutator.MutatorType.CONDITIONAL_TRUE
                || type == Mutator.MutatorType.CONDITIONAL_FALSE
                || type == Mutator.MutatorType.REMOVE_LOGICAL_NEGATION
                || type == Mutator.MutatorType.EMPTY_RETURNS
                || type == Mutator.MutatorType.VML_VOID_METHOD_CALL
                || type == Mutator.MutatorType.NON_VOID_METHOD_CALLS
                || type == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
            return UniversalPitMutationScanner.scan(file, type);
        }
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
                Mutator.MutatorType.AOR_FLOOR_DIV_TO_DIV,
                Mutator.MutatorType.AOR_POWER_TO_MULTIPLY,
                Mutator.MutatorType.INVERT_NEGS,
                Mutator.MutatorType.BOR_AND_TO_OR,
                Mutator.MutatorType.BOR_OR_TO_AND,
                Mutator.MutatorType.ROR_LT_TO_LE,
                Mutator.MutatorType.ROR_LE_TO_LT,
                Mutator.MutatorType.ROR_GT_TO_GE,
                Mutator.MutatorType.ROR_GE_TO_GT,
                Mutator.MutatorType.EQ_NEGATE,
                Mutator.MutatorType.ROR_GT_TO_LT,
                Mutator.MutatorType.ROR_LT_TO_GT,
                Mutator.MutatorType.ROR_GTE_TO_LT,
                Mutator.MutatorType.ROR_LTE_TO_GT,
                Mutator.MutatorType.LOGICAL_AND_TO_OR,
                Mutator.MutatorType.LOGICAL_OR_TO_AND,
                Mutator.MutatorType.TYPE_INT_TO_FLOAT,
                Mutator.MutatorType.TYPE_FLOAT_TO_INT,
                Mutator.MutatorType.CONDITIONAL_TRUE,
                Mutator.MutatorType.CONDITIONAL_FALSE,
                Mutator.MutatorType.REMOVE_LOGICAL_NEGATION,
                Mutator.MutatorType.EMPTY_RETURNS,
                Mutator.MutatorType.VML_VOID_METHOD_CALL,
                Mutator.MutatorType.NON_VOID_METHOD_CALLS,
                Mutator.MutatorType.CONSTRUCTOR_CALLS
        );
    }

    private static boolean supportsMutation(Mutator.MutatorType type) {
        return type == Mutator.MutatorType.AOR_PLUS_TO_MINUS
                || type == Mutator.MutatorType.AOR_MINUS_TO_PLUS
                || type == Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE
                || type == Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY
                || type == Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY
                || type == Mutator.MutatorType.AOR_FLOOR_DIV_TO_DIV
                || type == Mutator.MutatorType.AOR_POWER_TO_MULTIPLY
                || type == Mutator.MutatorType.INVERT_NEGS
                || type == Mutator.MutatorType.BOR_AND_TO_OR
                || type == Mutator.MutatorType.BOR_OR_TO_AND
                || type == Mutator.MutatorType.ROR_LT_TO_LE
                || type == Mutator.MutatorType.ROR_LE_TO_LT
                || type == Mutator.MutatorType.ROR_GT_TO_GE
                || type == Mutator.MutatorType.ROR_GE_TO_GT
                || type == Mutator.MutatorType.EQ_NEGATE
                || type == Mutator.MutatorType.ROR_GT_TO_LT
                || type == Mutator.MutatorType.ROR_LT_TO_GT
                || type == Mutator.MutatorType.ROR_GTE_TO_LT
                || type == Mutator.MutatorType.ROR_LTE_TO_GT
                || type == Mutator.MutatorType.LOGICAL_AND_TO_OR
                || type == Mutator.MutatorType.LOGICAL_OR_TO_AND
                || type == Mutator.MutatorType.TYPE_INT_TO_FLOAT
                || type == Mutator.MutatorType.TYPE_FLOAT_TO_INT;
    }
    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        // Pre-split for O(1) line lookups in getSourceLine()
        // ANTLR line numbers are 1-based; index 0 = line 1
        final String[] sourceLines = sourceCode.split("\n", -1);
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

            // Python has no >>> (its ints are unbounded); x >>> n is written as
            // (x & 0xFFFFFFFF) >> n. That shift is matched as >>>, and its mask
            // and shift are part of it rather than separate & and >> operators.

            /** For (x & MASK) >> n returns the x & MASK node, else null. */
            private ParseTree unsignedShiftMask(ParseTree ctx) {
                if (!isBinaryOp(ctx, ">>")) return null;
                ParseTree masked = unwrapParens(ctx.getChild(0));
                if (isBinaryOp(masked, "&")
                        && (MSLScannerAdapter.isUnsignedShiftMask(masked.getChild(2).getText())
                            || MSLScannerAdapter.isUnsignedShiftMask(masked.getChild(0).getText()))) {
                    return masked;
                }
                return null;
            }

            private boolean isBinaryOp(ParseTree ctx, String op) {
                return ctx instanceof PythonParser.ExprContext expr && expr.getChildCount() == 3
                        && expr.op != null && expr.op.getText().equals(op);
            }

            private ParseTree unwrapParens(ParseTree expr) {
                while (true) {
                    if (expr.getChildCount() == 1) {
                        expr = expr.getChild(0);
                    } else if (expr.getChildCount() == 3 && expr.getChild(0).getText().equals("(")
                            && expr.getChild(2).getText().equals(")")) {
                        expr = expr.getChild(1);
                    } else {
                        return expr;
                    }
                }
            }

            private boolean isMaskOfUnsignedShift(ParseTree and) {
                ParseTree p = and.getParent();
                for (int depth = 0; p != null && depth < 12; depth++, p = p.getParent()) {
                    if (unsignedShiftMask(p) == and) return true;
                }
                return false;
            }

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                // Handle ExprContext (arithmetic, bitwise)
                // (unary forms such as -x also carry an op, so require two operands)
                if (ctx instanceof PythonParser.ExprContext) {
                    return switch (mslOperator) {
                        case ">>>" -> unsignedShiftMask(ctx) != null;
                        case ">>" -> isBinaryOp(ctx, ">>") && unsignedShiftMask(ctx) == null;
                        case "&" -> isBinaryOp(ctx, "&") && !isMaskOfUnsignedShift(ctx);
                        default -> isBinaryOp(ctx, mslOperator);
                    };
                }
                // Handle ComparisonContext
                if (ctx instanceof PythonParser.ComparisonContext) {
                    return mslOperator.equals(comparisonOperator(ctx));
                }
                // Handle logical_test
                if (ctx instanceof PythonParser.Logical_testContext logCtx) {
                    return logCtx.op != null && logCtx.op.getText().equals(mslOperator);
                }
                return false;
            }

            /**
             * The operator of a single comparison ("<", "is", "is not"), or null.
             * "is not" and "not in" are two tokens.
             */
            private String comparisonOperator(ParseTree ctx) {
                if (!(ctx instanceof PythonParser.ComparisonContext)) return null;
                if (ctx.getChildCount() == 3) return ctx.getChild(1).getText();
                if (ctx.getChildCount() == 4) return ctx.getChild(1).getText() + " " + ctx.getChild(2).getText();
                return null;
            }

            @Override
            public List<String> getOperatorAliases(String canonicalOperator) {
                // is / is not compare identity, as == / != do for references
                // in Java and C# (s is None <-> s == null).
                return switch (canonicalOperator) {
                    case "&&" -> List.of("and");
                    case "||" -> List.of("or");
                    case "==" -> List.of("==", "is");
                    case "!=" -> List.of("!=", "is not");
                    default -> List.of(canonicalOperator);
                };
            }

            @Override
            public String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
                String source = comparisonOperator(ctx);
                boolean identity = "is".equals(source) || "is not".equals(source);
                return switch (canonicalOperator) {
                    case "&&" -> "and";
                    case "||" -> "or";
                    // Keep the identity form: s is None becomes s is not None.
                    case "==" -> identity ? "is" : "==";
                    case "!=" -> identity ? "is not" : "!=";
                    // Written as (x & 0xFFFFFFFF) >> n; see createOperatorEdit.
                    case ">>>" -> null;
                    default -> canonicalOperator;
                };
            }

            @Override
            public OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
                if (ctx instanceof PythonParser.ComparisonContext && ctx.getChildCount() == 4) {
                    // "is not" spans two tokens; replace both.
                    String op = toLanguageOperator(toOperator, ctx);
                    if (op == null) return null;
                    Token first = ((TerminalNode) ctx.getChild(1)).getSymbol();
                    Token last = ((TerminalNode) ctx.getChild(2)).getSymbol();
                    return new OperatorEdit(first.getLine(), first.getCharPositionInLine(),
                            sourceCode.substring(first.getStartIndex(), last.getStopIndex() + 1), op);
                }
                boolean fromUnsigned = ">>>".equals(fromOperator);
                if (">>>".equals(toOperator) || (fromUnsigned && ">>".equals(toOperator))) {
                    // Into or out of the masked form: rebuild the expression.
                    String left = getNodeText(ctx.getChild(0));
                    String right = getNodeText(ctx.getChild(2));
                    ParseTree masked = fromUnsigned ? unsignedShiftMask(ctx) : null;
                    // The mask may sit on either side of &; keep the other operand.
                    ParseTree value = masked == null ? null
                            : MSLScannerAdapter.isUnsignedShiftMask(masked.getChild(2).getText())
                                    ? masked.getChild(0) : masked.getChild(2);
                    String replacement = fromUnsigned
                            ? getNodeText(value) + " >> " + right
                            : "(" + left + " & 0xFFFFFFFF) >> " + right;
                    int[] pos = getTokenPosition(ctx);
                    return new OperatorEdit(pos[0], pos[1], getNodeText(ctx), replacement);
                }
                // Otherwise the default token swap: (x & 0xFFFFFFFF) >> n becomes
                // (x & 0xFFFFFFFF) << n.
                return MSLScannerAdapter.super.createOperatorEdit(ctx, fromOperator, toOperator);
            }

            // Python has no IsNullOrEmpty; s is None or s == "" is the check.
            private static final List<Pattern> NULL_OR_EMPTY_IDIOMS = List.of(
                    Pattern.compile("([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)\\s+is\\s+None\\s+or\\s+\\1\\s*==\\s*(?:\"\"|'')"),
                    Pattern.compile("([A-Za-z_]\\w*(?:\\.[A-Za-z_]\\w*)*)\\s+is\\s+None\\s+or\\s+len\\(\\1\\)\\s*==\\s*0"));

            @Override
            public String getNullOrEmptySubject(ParseTree ctx) {
                return MSLScannerAdapter.matchNullOrEmptySubject(getNodeText(ctx), NULL_OR_EMPTY_IDIOMS);
            }

            @Override
            public String notNullCheck(String subject) {
                return "(" + subject + " is not None)";
            }

            @Override
            public String getEmptyStatement(ParseTree statement) {
                return "pass";
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
                return MSLScannerAdapter.isQuantityText(getNodeText(ctx), ctx instanceof TerminalNode);
            }

            @Override
            public boolean isStringLiteral(ParseTree ctx) {
                return ctx != null && MSLScannerAdapter.isStringLiteral(getNodeText(ctx).strip(), true);
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

            /**
             * Returns the full source line containing this node.
             * ReplacementRule uses this to evaluate Except: context patterns.
             */
            @Override
            public String getSourceLine(ParseTree ctx) {
                int[] pos = getTokenPosition(ctx);
                int lineIndex = pos[0] - 1; // convert 1-based ANTLR line to 0-based array index
                if (lineIndex >= 0 && lineIndex < sourceLines.length) {
                    return sourceLines[lineIndex];
                }
                return "";
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
