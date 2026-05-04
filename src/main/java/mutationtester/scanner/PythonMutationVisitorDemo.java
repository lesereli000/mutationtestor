package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.Mutation;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Demo program for the Python mutation scanner.
 *
 * This tool parses a Python source file and prints mutation points for one shared
 * MutatorType so the Python flow matches the rest of the project contract.
 *
 * Usage:
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" "-Dexec.args=path/to/file/filename.py AOR_PLUS_TO_MINUS" -q
 *   or
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" -q
 *   (Run from the root folder, not src)
 *
 * Examples:
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" "-Dexec.args=samples\\Demo.py AOR_PLUS_TO_MINUS" -q
 *   mvn compile exec:java "-Dexec.mainClass=PythonMutationVisitorDemo" "-Dexec.args=samples\\PytestProject\\src\\Add1.py AOR_PLUS_TO_MINUS" -q
 */
public class PythonMutationVisitorDemo {

    public static void main(String[] args) throws Exception {
        String testCode;
        File sourceFile;
        Mutator.MutatorType mutationType = args.length > 1
                ? Mutator.MutatorType.valueOf(args[1])
                : Mutator.MutatorType.AOR_PLUS_TO_MINUS;

        if (args.length > 0) {
            sourceFile = Path.of(args[0]).toFile();
            testCode = Files.readString(sourceFile.toPath());
        } else {
            sourceFile = new File("sample.py");
            testCode = """
                x: int = 5
                y: float = 2.0

                def add1(a: int) -> int:
                    return a + 1

                def calculate(x: int, y: float):
                    if x > y and x != 0:
                        return x * y + 10
                    elif x <= y or y == 0:
                        return x - y // 2
                    else:
                        return x
                """;
        }

        System.out.println("=== Source Code ===");
        System.out.println(testCode);
        System.out.println();

        CharStream input = CharStreams.fromString(testCode);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);

        ParseTree tree = parser.root();

        PythonMutationVisitor visitor = new PythonMutationVisitor(sourceFile, testCode, mutationType);
        visitor.visit(tree);

        List<MutationPoint> mutations = visitor.getMutationPoints();
        System.out.println("=== Found " + mutations.size() + " Mutation Points for " + mutationType + " ===");
        for (MutationPoint mutation : mutations) {
            System.out.println(mutation);
        }
    }
}
