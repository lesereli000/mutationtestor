import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.gui.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.JFrame;

/**
 * GUI viewer for Python parse trees using ANTLR's built-in tree visualization.
 * Usage: java PythonParseTreeViewer <python-file>
 */
public class PythonParseTreeViewer {
    
    public static void main(String[] args) throws Exception {
        String code;
        String title;
        
        if (args.length > 0) {
            // Read from file
            code = new String(Files.readAllBytes(Paths.get(args[0])));
            title = "Parse Tree: " + args[0];
        } else {
            // Use sample code
            code = "def add1(a):\n    return a + 1\n\ndef calculate(x, y):\n    if x > y:\n        return x * y\n    else:\n        return x - y\n";
            title = "Parse Tree: Sample Python Code";
        }
        
        System.out.println("=== Source Code ===");
        System.out.println(code);
        System.out.println();
        
        // Parse the code
        CharStream input = CharStreams.fromString(code);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);
        
        // Get the parse tree starting from the 'file_input' rule
        ParseTree tree = parser.file_input();
        
        // Print text representation to console
        System.out.println("=== Parse Tree (S-expression) ===");
        System.out.println(tree.toStringTree(parser));
        System.out.println();
        
        // Show GUI window
        System.out.println("=== Launching GUI Window ===");
        JFrame frame = (JFrame) Trees.inspect(tree, parser).get();
        frame.setTitle(title);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    }
}
