import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;
import java.nio.file.*;
import java.util.*;

/**
 * Demo program for the PythonMutationVisitor.
 *
 * This tool parses a Python source file and prints all detected mutation opportunities
 * (arithmetic, logical, and comparison operator mutations) to the console.
 *
 * Usage:
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" "-Dexec.args=path/to/file/filename.py" -q
 *   or
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" -q
 *   (Run from the root folder, not src)
 *
 * Examples:
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" "-Dexec.args=samples\\Demo.py" -q
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" "-Dexec.args=samples\\PytestProject\\src\\Add1.py" -q
 *
 * If no file is provided, a built-in sample Python code is used.
 *
 * Output:
 *   - Prints the source code.
 *   - Prints the number and details of mutation opportunities found.
 */

public class PythonMutationVisitorDemo {
    
    public static void main(String[] args) throws Exception {
        String testCode;
        
        if (args.length > 0) {
            // Read from file
            testCode = Files.readString(Path.of(args[0]));
        } else {
            // Use sample code with type annotations
            testCode = """
                x: int = 5
                y: float = 2.0
                z = 3  # untyped, should not mutate

                def add1(a: int) -> int:
                    return a + 1

                def add2(b: float) -> float:
                    return b + 1.0

                def calculate(x: int, y: float):
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
        System.out.println("\nNotice: There will be duplicate mutation entries for function definitions due to visitTest and visitFuncDef both processing the same function. Currently we won't do anything about this for the time being, but there will be fixes via post-processing to remove duplicates in the future.\n");
        System.out.println("\nNote: Only annotated int/float variables and parameters are mutated for type swaps. Untyped variables are skipped.\n");
    }
}
