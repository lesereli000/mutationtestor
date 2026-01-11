import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SimpleMutator {

    public static void mutatePlusToMinus(String inputFile, String outputFile) throws IOException {
        String code = Files.readString(Path.of(inputFile));
        String mutatedCode = code.replace("+", "-");
        Files.writeString(Path.of(outputFile), mutatedCode);
        System.out.println("Mutant written to " + outputFile);
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: java SimpleMutator <input_file> <output_file>");
            return;
        }

        mutatePlusToMinus(args[0], args[1]);
    }
}
