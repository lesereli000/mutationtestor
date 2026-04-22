package mutationtester;

import mutationtester.msl.*;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import org.antlr.v4.gui.Trees;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import javax.swing.JFrame;

/**
 * GUI viewer for Python parse trees using ANTLR's built-in tree visualization.
 *
 * This tool parses a Python source file and displays its parse tree in a GUI window.
 * It also prints the source code and the parse tree (S-expression) to the console.
 *
 * Usage:
 *   mvn compile exec:java "-Dexec.mainClass=PythonParseTreeViewer" "-Dexec.args=path/to/file/filename.py" -q
 *   or
 *   mvn compile exec:java "-Dexec.mainClass=PythonParseTreeViewer" -q
 *   (Run from the root folder, not src)
 *
 * Examples:
 *   mvn compile exec:java "-Dexec.mainClass=PythonParseTreeViewer" "-Dexec.args=samples\\Demo.py" -q
 *   mvn compile exec:java "-Dexec.mainClass=PythonParseTreeViewer" "-Dexec.args=samples\\PytestProject\\src\\Add1.py" -q
 *
 * If no file is provided, a built-in sample Python code is used.
 *
 * Output:
 *   - Prints the source code.
 *   - Prints the parse tree (S-expression).
 *   - Launches a GUI window showing the parse tree.
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
