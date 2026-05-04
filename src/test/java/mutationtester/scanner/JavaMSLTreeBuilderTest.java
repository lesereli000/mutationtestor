package mutationtester.scanner;

import mutationtester.JavaLexer;
import mutationtester.JavaParser;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaMSLTreeBuilderTest {

    private ParseTree parseJava(String code) {
        JavaLexer lexer = new JavaLexer(CharStreams.fromString(code));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaParser parser = new JavaParser(tokens);
        return parser.compilationUnit();
    }

    @Test
    void testExtractBinaryExpression() {
        String code = "class Test { int x = a + b; }";
        ParseTree tree = parseJava(code);
        
        JavaMSLTreeBuilder builder = new JavaMSLTreeBuilder();
        builder.visit(tree);
        
        List<MSLNode> fragments = builder.getFragments();
        
        // Should extract the BinaryExpr
        assertEquals(1, fragments.size());
        
        MSLNode root = fragments.get(0);
        assertEquals("BinaryExpr", root.getType());
        assertEquals("+", root.getText());
        
        assertEquals(2, root.getChildren().size());
        
        MSLNode left = root.getChildren().get(0);
        assertEquals("Quantity", left.getType());
        assertEquals("a", left.getText());
        
        MSLNode right = root.getChildren().get(1);
        assertEquals("Quantity", right.getType());
        assertEquals("b", right.getText());
    }

    @Test
    void testNaturalFilteringIgnoresStrings() {
        String code = "class Test { String s = \"Hello\" + \"World\"; }";
        ParseTree tree = parseJava(code);
        
        JavaMSLTreeBuilder builder = new JavaMSLTreeBuilder();
        builder.visit(tree);
        
        List<MSLNode> fragments = builder.getFragments();
        
        // Since we ignore strings, the fragments list should be empty
        // Or if it extracts the binary expr, the children shouldn't be quantities,
        // but our implementation of JavaMSLTreeBuilder should naturally filter this entirely.
        assertTrue(fragments.isEmpty(), "String concatenation should be naturally filtered out");
    }
}
