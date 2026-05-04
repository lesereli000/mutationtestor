package mutationtester.scanner;

import mutationtester.PythonLexer;
import mutationtester.PythonParser;
import mutationtester.msl.MSLNode;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PythonMSLTreeBuilderTest {

    private ParseTree parsePython(String code) {
        PythonLexer lexer = new PythonLexer(CharStreams.fromString(code));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);
        return parser.root();
    }

    @Test
    void testExtractBinaryExpression() {
        String code = "x = a + b";
        ParseTree tree = parsePython(code);
        
        PythonMSLTreeBuilder builder = new PythonMSLTreeBuilder();
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
