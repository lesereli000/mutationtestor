package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.JavaScriptParser;
import mutationtester.msl.MSLScannerAdapter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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
        if (name.startsWith("LCR_")) {
            JavaScriptLCRVisitor v = new JavaScriptLCRVisitor(file, type);
            v.visit(tree); return v.getMutations();
        }

        // RVM — Return Value Mutations (all variants)
        if (name.startsWith("RVM_")) {
            JavaScriptRVMVisitor v = new JavaScriptRVMVisitor(file, type);
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
            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                return ctx.getChildCount() == 3 && ctx.getChild(1).getText().equals(mslOperator);
            }
            @Override public ParseTree getLeftOperand(ParseTree ctx)  { return ctx.getChild(0); }
            @Override public ParseTree getRightOperand(ParseTree ctx) { return ctx.getChild(2); }
            @Override public ParseTree getOperatorToken(ParseTree ctx) {
                return ctx.getChildCount() >= 3 ? ctx.getChild(1) : null;
            }
            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                if (ctx instanceof TerminalNode tn) {
                    int type = tn.getSymbol().getType();
                    return type == JavaScriptLexer.DecimalLiteral
                            || type == JavaScriptLexer.HexIntegerLiteral
                            || type == JavaScriptLexer.OctalIntegerLiteral
                            || type == JavaScriptLexer.BinaryIntegerLiteral
                            || type == JavaScriptLexer.Identifier;
                }
                if (ctx.getChildCount() == 1) return isQuantity(ctx.getChild(0));
                return false;
            }
            @Override
            public String getSourceLine(ParseTree ctx) {
                int[] pos = getTokenPosition(ctx);
                int lineIndex = pos[0] - 1;
                if (lineIndex >= 0 && lineIndex < sourceLines.length) return sourceLines[lineIndex];
                return "";
            }
            @Override public String getNodeText(ParseTree ctx) { return ctx.getText(); }
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