package mutationtester.scanner;

import mutationtester.CSharpLexer;
import mutationtester.CSharpParser;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CSharpMSLTreeBuilderTest {

    private ParseTree parseCSharp(String code) {
        CSharpLexer lexer = new CSharpLexer(CharStreams.fromString(code));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CSharpParser parser = new CSharpParser(tokens);
        return parser.prog();
    }

    @Test
    void testExtractBinaryExpression() {
        String code = "class Test { int x = a + b; }";
        ParseTree tree = parseCSharp(code);
        
        CSharpMSLTreeBuilder builder = new CSharpMSLTreeBuilder();
        builder.visit(tree);
        
        List<MSLNode> fragments = builder.getFragments();
        
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
    void testExtractStringCall() {
        String code = "class Test { void run() { string.IsNullOrEmpty(name); } }";
        ParseTree tree = parseCSharp(code);
        
        CSharpMSLTreeBuilder builder = new CSharpMSLTreeBuilder();
        builder.visit(tree);
        
        List<MSLNode> fragments = builder.getFragments();
        
        // C# scanner currently captures InvocationStatement and StringCall for this
        assertTrue(fragments.stream().anyMatch(n -> n.getType().equals("StringCall")));
        
        MSLNode root = fragments.stream().filter(n -> n.getType().equals("StringCall")).findFirst().get();
        assertEquals("string.IsNullOrEmpty", root.getText());
        
        assertEquals(1, root.getChildren().size());
        
        MSLNode arg = root.getChildren().get(0);
        assertEquals("Quantity", arg.getType());
        assertEquals("name", arg.getText());
    }
}
