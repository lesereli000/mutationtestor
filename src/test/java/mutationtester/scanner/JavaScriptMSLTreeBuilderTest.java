package mutationtester.scanner;

import mutationtester.JavaScriptLexer;
import mutationtester.JavaScriptParser;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JavaScriptMSLTreeBuilderTest {

    private ParseTree parseJS(String code) {
        JavaScriptLexer lexer = new JavaScriptLexer(CharStreams.fromString(code));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        JavaScriptParser parser = new JavaScriptParser(tokens);
        return parser.program();
    }

    @Test
    void testExtractBinaryExpression() {
        String code = "let x = a + b;";
        ParseTree tree = parseJS(code);
        
        JavaScriptMSLTreeBuilder builder = new JavaScriptMSLTreeBuilder();
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
}
