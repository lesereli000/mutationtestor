import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.*;
import java.util.*;

/**
 * Demo program to test PythonMutationVisitor
 * Run: mvn exec:java -Dexec.mainClass="PythonMutationVisitorDemo" -Dexec.args="path/to/file.py"
 */
public class PythonMutationVisitorDemo {
    
    public static void main(String[] args) throws Exception {
        String testCode;
        
        if (args.length > 0) {
            // Read from file
            testCode = Files.readString(Path.of(args[0]));
        } else {
            // Use sample code
            testCode = """
                def add1(a):
                    return a + 1
                
                def calculate(x, y):
                    if x > y and x != 0:
                        return x * y + 10
                    elif x <= y or y == 0:
                        return x - y // 2
                    else:
                        return x ** 2
                """;
        }
        
        System.out.println("=== Source Code ===");
        System.out.println(testCode);
        System.out.println();
        
        // Parse the code
        CharStream input = CharStreams.fromString(testCode);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);
        
        // Get the parse tree
        ParseTree tree = parser.root();
        
        // Create and run the visitor
        PythonMutationVisitor visitor = new PythonMutationVisitor(testCode);
        visitor.visit(tree);
        
        // Print results
        List<Mutation> mutations = visitor.getMutations();
        System.out.println("=== Found " + mutations.size() + " Mutation Opportunities ===");
        for (Mutation m : mutations) {
            System.out.println(m);
        }
    }
}
