import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

public class JavaScriptMutationScanner {
    
    /**
     * Scan a JavaScript file for mutations using AST parsing
     */
    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        
        // Parse JavaScript using ANTLR (same as MyParser)
        CharStream input = CharStreams.fromString(sourceCode);
        JavaScriptLexer lexer = new JavaScriptLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        
        // Get parse tree starting from program() rule (same as MyParser)
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
         * Visit multiplicative expressions: *, /, %
         * (Add this if you extend mutation types beyond + and -)
         */
        @Override
        public Void visitMultiplicativeExpression(JavaScriptParser.MultiplicativeExpressionContext ctx) {
            // For now, just continue traversal
            // You can add * and / mutations here later
            return visitChildren(ctx);
        }
        
        /**
         * Visit relational expressions: <, >, <=, >=
         * (Add this if you implement ROR mutations)
         */
        @Override
        public Void visitRelationalExpression(JavaScriptParser.RelationalExpressionContext ctx) {
            // For now, just continue traversal
            // You can add relational operator mutations here later
            return visitChildren(ctx);
        }
        
        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }
    }
}