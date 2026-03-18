import org.antlr.v4.gui.TreeViewer;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class MyParser {

    public MyParser(String filePath){
        try {
            printAST(Files.readString(Path.of(filePath)), filePath.split("\\.")[1]);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void printAST(String code, String fileExt){
        if(fileExt.equals("java")) {
            JavaLexer lexer = new JavaLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            System.out.println("Lexer finished");

            JavaParser parser = new JavaParser(tokens);
            ParseTree tree = parser.compilationUnit();
            System.out.println("Parser finished");

            // 4. Use a visitor or listener to process the parse tree
            // (You would implement your own custom visitor/listener here to perform actions)
            System.out.println(tree.toStringTree(parser));

            TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
            viewer.open();
        }
        if(fileExt.equals("py")) {
            PythonLexer lexer = new PythonLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            System.out.println("Lexer finished");

            PythonParser parser = new PythonParser(tokens);
            ParseTree tree = parser.root();
            System.out.println("Parser finished");

            // 4. Use a visitor or listener to process the parse tree
            // (You would implement your own custom visitor/listener here to perform actions)
            System.out.println(tree.toStringTree(parser));

            TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
            viewer.open();
        }
        if(fileExt.equals("js")) {
            JavaScriptLexer lexer = new JavaScriptLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            System.out.println("Lexer finished");

            JavaScriptParser parser = new JavaScriptParser(tokens);
            ParseTree tree = parser.program();
            System.out.println("Parser finished");

            // 4. Use a visitor or listener to process the parse tree
            // (You would implement your own custom visitor/listener here to perform actions)
            System.out.println(tree.toStringTree(parser));

            TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
            viewer.open();
        }
        if(fileExt.equals("cs")) {
            CSharpLexer lexer = new CSharpLexer(CharStreams.fromString(code));
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            System.out.println("Lexer finished");

            CSharpParser parser = new CSharpParser(tokens);
            ParseTree tree = parser.prog();
            System.out.println("Parser finished");

            // 4. Use a visitor or listener to process the parse tree
            // (You would implement your own custom visitor/listener here to perform actions)
            System.out.println(tree.toStringTree(parser));

            TreeViewer viewer = new TreeViewer(Arrays.asList(parser.getRuleNames()), tree);
            viewer.open();
        }
    }

}
