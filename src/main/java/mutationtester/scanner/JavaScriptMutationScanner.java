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
import java.util.ArrayList;
import java.util.List;

public class JavaScriptMutationScanner {
    
    /**
     * Scan a JavaScript file for mutations using AST parsing
     */
    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        
        // Parse JavaScript using ANTLR
        CharStream input = CharStreams.fromString(sourceCode);
        JavaScriptLexer lexer = new JavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        
        // Get parse tree starting from program() rule
        ParseTree tree = parser.program();
        
        // Create visitor based on mutation type
        JavaScriptMutationVisitor visitor = new JavaScriptMutationVisitor(file, type);
        visitor.visit(tree);
        
        return visitor.getMutationPoints();
    }
    
    /**
     * Visitor that walks the JavaScript AST and finds mutation points
     */
    private static class JavaScriptMutationVisitor extends JavaScriptParserBaseVisitor<Void> {
        
        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints;
        
        public JavaScriptMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
            this.mutationPoints = new ArrayList<>();
        }
        
        /**
         * Visit additive expressions: + and -
         */
        @Override
        public Void visitAdditiveExpression(JavaScriptParser.AdditiveExpressionContext ctx) {
            // Walk through all children of this expression
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);
                
                // Check if this child is a terminal node (operator)
                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();
                    
                    // AOR_PLUS_TO_MINUS: mutate + to -
                    if (operator.equals("+") && mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                        MutationPoint point = new MutationPoint(
                            sourceFile,
                            token.getLine(),
                            token.getCharPositionInLine(),
                            "+",
                            "-",
                            mutationType
                        );
                        mutationPoints.add(point);
                    }
                    
                    // AOR_MINUS_TO_PLUS: mutate - to +
                    else if (operator.equals("-") && mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                        MutationPoint point = new MutationPoint(
                            sourceFile,
                            token.getLine(),
                            token.getCharPositionInLine(),
                            "-",
                            "+",
                            mutationType
                        );
                        mutationPoints.add(point);
                    }
                }
            }
            
            // Continue visiting children
            return visitChildren(ctx);
        }
        
        /**
         * NEW: Visit variable declarations to find numeric literals
         */
        @Override
        public Void visitVariableDeclaration(JavaScriptParser.VariableDeclarationContext ctx) {
            // Check if this is a variable initialization with a value
            if (ctx.assignable() != null && ctx.singleExpression() != null) {
                JavaScriptParser.SingleExpressionContext expr = ctx.singleExpression();
                
                // Check if the expression is a numeric literal
                if (isNumericLiteral(expr)) {
                    String literalText = expr.getText();
                    Token token = expr.start;
                    
                    // Create mutation for this numeric literal
                    createNumericLiteralMutation(literalText, token);
                }
            }
            
            return visitChildren(ctx);
        }
        
        /**
         * Check if an expression is a numeric literal
         */
        private boolean isNumericLiteral(JavaScriptParser.SingleExpressionContext ctx) {
            // Check if this is a LiteralExpression
            if (ctx instanceof JavaScriptParser.LiteralExpressionContext) {
                JavaScriptParser.LiteralExpressionContext litCtx = 
                    (JavaScriptParser.LiteralExpressionContext) ctx;
                
                // Check if it has a numeric literal
                if (litCtx.literal() != null && litCtx.literal().numericLiteral() != null) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * Create a mutation point for a numeric literal
         */
        private void createNumericLiteralMutation(String literalText, Token token) {
            // Determine what type of numeric literal this is
            NumericLiteralType type = getNumericLiteralType(literalText);
            
            // Create appropriate mutations based on current mutation type
            String replacement = getReplacementLiteral(literalText, type);
            
            if (replacement != null) {
                mutationPoints.add(new MutationPoint(
                    sourceFile,
                    token.getLine(),
                    token.getCharPositionInLine(),
                    literalText,
                    replacement,
                    mutationType
                ));
            }
        }
        
        /**
         * Determine the type of numeric literal
         */
        private NumericLiteralType getNumericLiteralType(String literal) {
            // Check for exponential notation (contains 'e' or 'E')
            if (literal.toLowerCase().contains("e")) {
                return NumericLiteralType.EXPONENTIAL;
            }
            
            // Check for float (contains decimal point)
            if (literal.contains(".")) {
                return NumericLiteralType.FLOAT;
            }
            
            // Otherwise it's an integer
            return NumericLiteralType.INTEGER;
        }
        
        /**
         * Get the replacement literal based on mutation type
         */
        private String getReplacementLiteral(String original, NumericLiteralType type) {
            switch (type) {
                case INTEGER:
                    if (mutationType == Mutator.MutatorType.NLT_INTEGER_TO_FLOAT) {
                        return original + ".0";  // 42 → 42.0
                    }
                    if (mutationType == Mutator.MutatorType.NLT_INTEGER_TO_EXPONENTIAL) {
                        return convertIntegerToExponential(original);  // 42 → 4.2e1
                    }
                    break;
                    
                case FLOAT:
                    if (mutationType == Mutator.MutatorType.NLT_FLOAT_TO_INTEGER) {
                        // 3.14 → 3 (truncate decimal part)
                        int dotIndex = original.indexOf('.');
                        if (dotIndex > 0) {
                            return original.substring(0, dotIndex);
                        }
                    }
                    if (mutationType == Mutator.MutatorType.NLT_FLOAT_TO_EXPONENTIAL) {
                        return original + "e0";  // 3.14 → 3.14e0
                    }
                    break;
                    
                case EXPONENTIAL:
                    if (mutationType == Mutator.MutatorType.NLT_EXPONENTIAL_TO_INTEGER) {
                        return convertExponentialToInteger(original);  // 1e10 → 10000000000
                    }
                    if (mutationType == Mutator.MutatorType.NLT_EXPONENTIAL_TO_FLOAT) {
                        return convertExponentialToInteger(original) + ".0";  // 1e10 → 10000000000.0
                    }
                    break;
            }
            return null;
        }
        
        /**
         * Convert integer to exponential notation
         */
        private String convertIntegerToExponential(String integer) {
            try {
                long value = Long.parseLong(integer);
                if (value == 0) return "0e0";
                
                // Convert to exponential form
                int exponent = 0;
                double mantissa = Math.abs(value);
                
                while (mantissa >= 10) {
                    mantissa /= 10;
                    exponent++;
                }
                
                String sign = value < 0 ? "-" : "";
                return String.format("%s%.1fe%d", sign, mantissa, exponent);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        /**
         * Convert exponential to integer
         */
        private String convertExponentialToInteger(String exponential) {
            try {
                double value = Double.parseDouble(exponential);
                return String.valueOf((long) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        /**
         * Visit multiplicative expressions: *, /, %
         */
        @Override
        public Void visitMultiplicativeExpression(JavaScriptParser.MultiplicativeExpressionContext ctx) {
            return visitChildren(ctx);
        }
        
        /**
         * Visit relational expressions: <, >, <=, >=
         */
        @Override
        public Void visitRelationalExpression(JavaScriptParser.RelationalExpressionContext ctx) {
            return visitChildren(ctx);
        }
        
        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }
    }
    
    /**
     * Enum to classify numeric literal types
     */
    private enum NumericLiteralType {
        INTEGER,
        FLOAT,
        EXPONENTIAL
    }
    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        CharStream input = CharStreams.fromString(sourceCode);
        JavaScriptLexer lexer = new JavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        ParseTree tree = parser.program();

        return new MSLScannerAdapter() {
            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                // In JavaScript grammar, many binary expressions follow the same pattern:
                // leftExpr op rightExpr
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
                    return type == JavaScriptLexer.DecimalLiteral || 
                           type == JavaScriptLexer.HexIntegerLiteral ||
                           type == JavaScriptLexer.OctalIntegerLiteral ||
                           type == JavaScriptLexer.BinaryIntegerLiteral ||
                           type == JavaScriptLexer.Identifier;
                }
                // Check if it's a literal or primary expression
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
