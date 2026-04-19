import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

public class PythonMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        if (!supportsMutation(type)) {
            return Collections.emptyList();
        }

        String sourceCode = Files.readString(file.toPath());

        CharStream input = CharStreams.fromString(sourceCode);
        PythonLexer lexer = new PythonLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        PythonParser parser = new PythonParser(tokens);

        ParseTree tree = parser.root();

        PythonMutationVisitor visitor = new PythonMutationVisitor(file, sourceCode, type);
        visitor.visit(tree);
        return visitor.getMutationPoints();
    }

    public static List<Mutator.MutatorType> getSupportedTypes() {
        return List.of(
                Mutator.MutatorType.AOR_PLUS_TO_MINUS,
                Mutator.MutatorType.AOR_MINUS_TO_PLUS,
                Mutator.MutatorType.ROR_LT_TO_LE,
                Mutator.MutatorType.ROR_LE_TO_LT,
                Mutator.MutatorType.ROR_GT_TO_GE,
                Mutator.MutatorType.ROR_GE_TO_GT,
                Mutator.MutatorType.EQ_NEGATE,
                Mutator.MutatorType.LOGICAL_AND_TO_OR,
                Mutator.MutatorType.LOGICAL_OR_TO_AND,
                Mutator.MutatorType.TYPE_INT_TO_FLOAT,
                Mutator.MutatorType.TYPE_FLOAT_TO_INT
        );
    }

    private static boolean supportsMutation(Mutator.MutatorType type) {
        return type == Mutator.MutatorType.AOR_PLUS_TO_MINUS
                || type == Mutator.MutatorType.AOR_MINUS_TO_PLUS
                || type == Mutator.MutatorType.ROR_LT_TO_LE
                || type == Mutator.MutatorType.ROR_LE_TO_LT
                || type == Mutator.MutatorType.ROR_GT_TO_GE
                || type == Mutator.MutatorType.ROR_GE_TO_GT
                || type == Mutator.MutatorType.EQ_NEGATE
                || type == Mutator.MutatorType.LOGICAL_AND_TO_OR
                || type == Mutator.MutatorType.LOGICAL_OR_TO_AND
                || type == Mutator.MutatorType.TYPE_INT_TO_FLOAT
                || type == Mutator.MutatorType.TYPE_FLOAT_TO_INT;
    }
}