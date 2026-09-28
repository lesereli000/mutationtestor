package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.CSharpMutationScanner;
import mutationtester.scanner.JavaMutationScanner;
import mutationtester.scanner.JavaScriptMutationScanner;
import mutationtester.scanner.PythonMutationScanner;
import mutationtester.scanner.RacketMutationScanner;

/** Verifies that one shared mutator name works in every supported language. */
class UniversalSharedMutationTests {

    private static final List<Mutator.MutatorType> SHARED_TYPES = List.of(
            Mutator.MutatorType.ROR_LT_TO_LE,
            Mutator.MutatorType.ROR_LE_TO_LT,
            Mutator.MutatorType.LOGICAL_AND_TO_OR,
            Mutator.MutatorType.LOGICAL_OR_TO_AND,
            Mutator.MutatorType.BOR_AND_TO_OR);

    @TempDir
    Path tempDir;

    private final Map<String, File> sources = new LinkedHashMap<>();

    @BeforeEach
    void createFixtures() throws Exception {
        sources.put("JavaScript", write("shared.js",
                "function shared(a, b, x, y) { return [a < b, a <= b, a && b, a || b, x & y]; }"));
        sources.put("Java", write("Shared.java", """
                class Shared {
                    boolean lt(int a, int b) { return a < b; }
                    boolean le(int a, int b) { return a <= b; }
                    boolean and(boolean a, boolean b) { return a && b; }
                    boolean or(boolean a, boolean b) { return a || b; }
                    int bits(int x, int y) { return x & y; }
                }
                """));
        sources.put("Python", write("shared.py", """
                def shared(a, b, x, y):
                    return (a < b, a <= b, a and b, a or b, x & y)
                """));
        sources.put("C#", write("Shared.cs", """
                class Shared {
                    bool Lt(int a, int b) { return a < b; }
                    bool Le(int a, int b) { return a <= b; }
                    bool And(bool a, bool b) { return a && b; }
                    bool Or(bool a, bool b) { return a || b; }
                    int Bits(int x, int y) { return x & y; }
                }
                """));
        sources.put("Racket", write("shared.rkt", """
                #lang racket
                (define (shared a b x y)
                  (list (< a b) (<= a b) (and a b) (or a b) (bitwise-and x y)))
                """));
    }

    @Test
    void fiveSharedMutationTypesWorkAcrossAllFiveLanguages() throws Exception {
        System.out.println("=== UNIVERSAL MUTATION SUPPORT MATRIX ===");
        for (Map.Entry<String, File> language : sources.entrySet()) {
            System.out.println(language.getKey() + ":");
            for (Mutator.MutatorType type : SHARED_TYPES) {
                List<MutationPoint> points = scan(language.getValue(), type);
                assertEquals(1, points.size(), language.getKey() + " / " + type);
                System.out.println("  [PASS] " + type + ": "
                        + points.get(0).getOperator() + " -> " + points.get(0).getReplacement());
            }
        }
    }

    private File write(String name, String content) throws Exception {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path.toFile();
    }

    private List<MutationPoint> scan(File file, Mutator.MutatorType type) throws Exception {
        String name = file.getName();
        if (name.endsWith(".js")) return JavaScriptMutationScanner.scanFile(file, type);
        if (name.endsWith(".java")) return JavaMutationScanner.scanFile(file, type);
        if (name.endsWith(".py")) return PythonMutationScanner.scanFile(file, type);
        if (name.endsWith(".cs")) return CSharpMutationScanner.scanFile(file, type);
        return RacketMutationScanner.scanFile(file, type);
    }
}
