package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.JavaScriptAORVisitor;
import mutationtester.JavaScriptLexer;
import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.MSLScannerAdapter;

public class JavaScriptMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = CharStreams.fromString(sourceCode);
        JavaScriptLexer lexer = new JavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        ParseTree tree = parser.program();

        String name = type.name();

        // ROR — Relational Operator Replacement
        if (name.startsWith("ROR_")) {
            JavaScriptRORVisitor v = new JavaScriptRORVisitor(file, type);
            v.visit(tree); return v.getMutations();
        }

        // LCR — Logical Connector Replacement
        if (name.startsWith("LCR_")
                || type == Mutator.MutatorType.LOGICAL_AND_TO_OR
                || type == Mutator.MutatorType.LOGICAL_OR_TO_AND) {
            JavaScriptLCRVisitor v = new JavaScriptLCRVisitor(file, type);
            v.visit(tree); return v.getMutations();
        }

        // RVM — Return Value Mutations (all variants)
        if (name.startsWith("RVM_")) {
            JavaScriptRVMVisitor v = new JavaScriptRVMVisitor(file, type, sourceCode);
            v.visit(tree); return v.getMutations();
        }

        // AOR — Arithmetic Operator Replacement (including ++ / --)
        if (name.startsWith("AOR_")) {
            // Increment/decrement are a subset of AOR
            if (type == Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT
                    || type == Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT) {
                JavaScriptIncrementVisitor v = new JavaScriptIncrementVisitor(file, type);
                v.visit(tree); return v.getMutations();
            }
            JavaScriptAORVisitor v = new JavaScriptAORVisitor(file, type);
            v.visit(tree); return v.getMutations();
        }

        // VML — Void Method Call removal
        if (name.startsWith("VML_")) {
            JavaScriptVMLVisitor v = new JavaScriptVMLVisitor(file, type);
            v.visit(tree); return v.getMutations();
        }

        // INEG — Invert Negatives
        if (name.startsWith("INEG_")) {
            JavaScriptINEGVisitor v = new JavaScriptINEGVisitor(file, type);
            v.visit(tree); return v.getMutations();
        }

        // Additional PIT-style mutations for JavaScript.
        if (type == Mutator.MutatorType.CONDITIONAL_TRUE
                || type == Mutator.MutatorType.CONDITIONAL_FALSE
                || type == Mutator.MutatorType.CONSTRUCTOR_CALLS
                || type == Mutator.MutatorType.NON_VOID_METHOD_CALLS
                || type == Mutator.MutatorType.REMOVE_LOGICAL_NEGATION
                || type == Mutator.MutatorType.JS_STRICT_EQ_TO_LOOSE_EQ
                || type == Mutator.MutatorType.JS_STRICT_NE_TO_LOOSE_NE) {
            JavaScriptPitStyleVisitor v = new JavaScriptPitStyleVisitor(file, type, sourceCode);
            v.visit(tree); return v.getMutations();
        }

        if (type == Mutator.MutatorType.BOR_AND_TO_OR) {
            JavaScriptPitStyleVisitor v = new JavaScriptPitStyleVisitor(file, type, sourceCode);
            v.visit(tree); return v.getMutations();
        }

        if (type == Mutator.MutatorType.EMPTY_RETURNS) {
            JavaScriptRVMVisitor v = new JavaScriptRVMVisitor(file, type, sourceCode);
            v.visit(tree); return v.getMutations();
        }

        // NLT — Numeric Literal Transformations
        NLTVisitor visitor = new NLTVisitor(file, type);
        visitor.visit(tree);
        return visitor.getMutationPoints();
    }

    // ── NLT visitor ──────────────────────────────────────────────────────────

    private static class NLTVisitor extends JavaScriptParserBaseVisitor<Void> {
        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints = new ArrayList<>();

        NLTVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
        }

        List<MutationPoint> getMutationPoints() { return mutationPoints; }

        @Override
        public Void visitVariableDeclaration(JavaScriptParser.VariableDeclarationContext ctx) {
            if (ctx.assignable() != null && ctx.singleExpression() != null) {
                JavaScriptParser.SingleExpressionContext expr = ctx.singleExpression();
                if (isNumericLiteral(expr)) createNumericLiteralMutation(expr.getText(), expr.start);
            }
            return visitChildren(ctx);
        }

        private boolean isNumericLiteral(JavaScriptParser.SingleExpressionContext ctx) {
            if (ctx instanceof JavaScriptParser.LiteralExpressionContext litCtx)
                return litCtx.literal() != null && litCtx.literal().numericLiteral() != null;
            return false;
        }

        private void createNumericLiteralMutation(String literalText, Token token) {
            NumericLiteralType kind = classify(literalText);
            String replacement = getReplacement(literalText, kind);
            if (replacement != null)
                mutationPoints.add(new MutationPoint(sourceFile, token.getLine(),
                        token.getCharPositionInLine(), literalText, replacement, mutationType));
        }

        private NumericLiteralType classify(String literal) {
            if (literal.toLowerCase().contains("e")) return NumericLiteralType.EXPONENTIAL;
            if (literal.contains("."))                return NumericLiteralType.FLOAT;
            return NumericLiteralType.INTEGER;
        }

        private String getReplacement(String original, NumericLiteralType kind) {
            return switch (kind) {
                case INTEGER -> switch (mutationType) {
                    case NLT_INTEGER_TO_FLOAT       -> original + ".0";
                    case NLT_INTEGER_TO_EXPONENTIAL -> toExponential(original);
                    default -> null;
                };
                case FLOAT -> switch (mutationType) {
                    case NLT_FLOAT_TO_INTEGER -> { int d = original.indexOf('.'); yield d > 0 ? original.substring(0, d) : null; }
                    case NLT_FLOAT_TO_EXPONENTIAL -> original + "e0";
                    default -> null;
                };
                case EXPONENTIAL -> switch (mutationType) {
                    case NLT_EXPONENTIAL_TO_INTEGER -> toInteger(original);
                    case NLT_EXPONENTIAL_TO_FLOAT   -> toInteger(original) + ".0";
                    default -> null;
                };
            };
        }

        private String toExponential(String integer) {
            try {
                long value = Long.parseLong(integer);
                if (value == 0) return "0e0";
                int exp = 0; double mantissa = Math.abs(value);
                while (mantissa >= 10) { mantissa /= 10; exp++; }
                return String.format("%s%.1fe%d", value < 0 ? "-" : "", mantissa, exp);
            } catch (NumberFormatException e) { return null; }
        }

        private String toInteger(String exp) {
            try { return String.valueOf((long) Double.parseDouble(exp)); }
            catch (NumberFormatException e) { return null; }
        }
    }

    private enum NumericLiteralType { INTEGER, FLOAT, EXPONENTIAL }

    // ── MSL adapter ──────────────────────────────────────────────────────────

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        final String[] sourceLines = sourceCode.split("\n", -1);

        CharStream input = CharStreams.fromString(sourceCode);
        JavaScriptLexer lexer = new JavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        ParseTree tree = parser.program();

        return new MSLScannerAdapter() {
            private final java.util.regex.Pattern invocationStatement = java.util.regex.Pattern.compile(
                    "^(await)?[A-Za-z_$][A-Za-z0-9_$\\.\\[\\]]*\\(.*\\);$",
                    java.util.regex.Pattern.DOTALL
            );

            // JavaScript has no // operator; Math.floor(a / b) is how floor
            // division is written, so it stands in for a // b. Its inner "/" is
            // part of that operator, not a separate division to mutate.

            /** The a / b inside Math.floor(a / b), or null if ctx isn't that idiom. */
            private ParseTree floorDivision(ParseTree ctx) {
                if (!(ctx instanceof JavaScriptParser.ArgumentsExpressionContext call)
                        || !call.singleExpression().getText().equals("Math.floor")
                        || call.arguments().argument().size() != 1) {
                    return null;
                }
                JavaScriptParser.ArgumentContext arg = call.arguments().argument(0);
                if (arg.Ellipsis() != null || arg.singleExpression() == null) return null;
                ParseTree inner = stripParens(arg.singleExpression());
                return inner instanceof JavaScriptParser.MultiplicativeExpressionContext
                        && inner.getChild(1).getText().equals("/") ? inner : null;
            }

            private ParseTree stripParens(ParseTree expr) {
                while (expr instanceof JavaScriptParser.ParenthesizedExpressionContext paren
                        && paren.expressionSequence().singleExpression().size() == 1) {
                    expr = paren.expressionSequence().singleExpression(0);
                }
                return expr;
            }

            private boolean isInsideFloorDivision(ParseTree division) {
                ParseTree p = division.getParent();
                while (p instanceof JavaScriptParser.ParenthesizedExpressionContext
                        || p instanceof JavaScriptParser.ExpressionSequenceContext) {
                    p = p.getParent();
                }
                if (!(p instanceof JavaScriptParser.ArgumentContext) || p.getParent() == null) return false;
                ParseTree call = p.getParent().getParent();
                return call != null && floorDivision(call) == division;
            }

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if (mslOperator.equals("//")) return floorDivision(ctx) != null;
                return ctx.getChildCount() == 3 && ctx.getChild(1).getText().equals(mslOperator)
                        && !(mslOperator.equals("/") && isInsideFloorDivision(ctx));
            }

            @Override
            public OperatorEdit createOperatorEdit(ParseTree ctx, String fromOperator, String toOperator) {
                ParseTree floor = floorDivision(ctx);
                boolean fromFloor = fromOperator.equals("//") && floor != null;
                if (!fromFloor && !toOperator.equals("//")) {
                    return MSLScannerAdapter.super.createOperatorEdit(ctx, fromOperator, toOperator);
                }
                ParseTree binary = fromFloor ? floor : ctx;
                String left = getNodeText(binary.getChild(0));
                String right = getNodeText(binary.getChild(2));
                String replacement = toOperator.equals("//")
                        ? "Math.floor(" + left + " / " + right + ")"
                        : MSLScannerAdapter.infix(left, toLanguageOperator(toOperator, binary), right);
                int[] pos = getTokenPosition(ctx);
                return new OperatorEdit(pos[0], pos[1], getNodeText(ctx), replacement);
            }
            @Override
            public List<String> getOperatorAliases(String canonicalOperator) {
                // Equality covers both the loose and strict spellings.
                return switch (canonicalOperator) {
                    case "==" -> List.of("==", "===");
                    case "!=" -> List.of("!=", "!==");
                    default -> List.of(canonicalOperator);
                };
            }
            @Override
            public String toLanguageOperator(String canonicalOperator, ParseTree ctx) {
                return switch (canonicalOperator) {
                    // Written as Math.floor(a / b); see createOperatorEdit.
                    case "//" -> null;
                    // Keep strictness: === becomes !== rather than !=.
                    case "==", "!=" -> isStrictEquality(ctx) ? canonicalOperator + "=" : canonicalOperator;
                    default -> canonicalOperator;
                };
            }
            private boolean isStrictEquality(ParseTree ctx) {
                if (ctx == null || ctx.getChildCount() != 3) return false;
                String op = ctx.getChild(1).getText();
                return op.equals("===") || op.equals("!==");
            }
            @Override public ParseTree getLeftOperand(ParseTree ctx) {
                ParseTree floor = floorDivision(ctx);
                return (floor != null ? floor : ctx).getChild(0);
            }
            @Override public ParseTree getRightOperand(ParseTree ctx) {
                ParseTree floor = floorDivision(ctx);
                return (floor != null ? floor : ctx).getChild(2);
            }
            @Override public ParseTree getOperatorToken(ParseTree ctx) {
                return ctx.getChildCount() >= 3 ? ctx.getChild(1) : null;
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
            public String getSourceLine(ParseTree ctx) {
                int[] pos = getTokenPosition(ctx);
                int lineIndex = pos[0] - 1;
                if (lineIndex >= 0 && lineIndex < sourceLines.length) return sourceLines[lineIndex];
                return "";
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

            // JavaScript has no IsNullOrEmpty; s == null || s === "" is the check.
            private static final List<java.util.regex.Pattern> NULL_OR_EMPTY_IDIOMS = List.of(
                    java.util.regex.Pattern.compile(MSLScannerAdapter.NULL_CHECK_SUBJECT + "\\s*===?\\s*(?:null|undefined)\\s*\\|\\|\\s*\\1\\s*===?\\s*(?:\"\"|'')"),
                    java.util.regex.Pattern.compile(MSLScannerAdapter.NULL_CHECK_SUBJECT + "\\s*===?\\s*(?:null|undefined)\\s*\\|\\|\\s*\\1\\.length\\s*===?\\s*0"));

            @Override
            public String getNullOrEmptySubject(ParseTree ctx) {
                return MSLScannerAdapter.matchNullOrEmptySubject(getNodeText(ctx), NULL_OR_EMPTY_IDIOMS);
            }

            @Override
            public String notEmptyStringCheck(String subject) {
                return "(" + subject + " !== \"\")";
            }

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null && start.getLine() != stop.getLine()) {
                        return false;
                    }
                }
                String text = getNodeText(ctx);
                if (text == null || text.isBlank()) {
                    return false;
                }
                String compact = text.replaceAll("\\s+", "");
                if (compact.startsWith("return") || compact.startsWith("throw")) {
                    return false;
                }
                return invocationStatement.matcher(compact).matches();
            }

            @Override
            public String getSingleArgumentText(ParseTree ctx, String functionName) {
                String text = getNodeText(ctx);
                if (text == null) return null;
                String compact = text.replaceAll("\\s+", "");
                String prefix = functionName.replaceAll("\\s+", "") + "(";
                if (!compact.startsWith(prefix) || !compact.endsWith(")")) {
                    return null;
                }
                String inner = compact.substring(prefix.length(), compact.length() - 1).trim();
                if (inner.isEmpty() || hasTopLevelComma(inner)) {
                    return null;
                }
                return inner;
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
            @Override public ParseTree getRootContext() { return tree; }
        };
    }
}
