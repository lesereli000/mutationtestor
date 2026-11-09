package mutationtester;

import static mutationtester.SpecFixture.set;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;

/**
 * specs/TypeMutations.spec: the ten number-type conversions of the old TYPE_*
 * operators, each checked by its exact mutants in Java and C#.
 */
class TypeMutationSpecTests {

    @TempDir
    Path dir;

    private static final String SPEC = "specs/TypeMutations.spec";

    private static final String JAVA = """
            class T {
              double scale(int count, long total, float ratio) {
                int n = 3;
                long big = 7L;
                float f = 1.5f;
                double d = 2.5;
                return d;
              }
            }
            """;

    /** Each conversion and its mutants, the same in Java and C# for this code. */
    private static final Map<String, Set<String>> EXPECTED = new TreeMap<>(Map.of(
            "int -> double", set("2: int -> double", "3: int -> double"),
            "double -> int", set("2: double -> int", "6: double -> int", "6: 2.5 -> 2"),
            "int -> float", set("2: int -> float", "3: int -> float"),
            "float -> int", set("2: float -> int", "5: float -> int", "5: 1.5f -> 1"),
            "long -> double", set("2: long -> double", "4: long -> double"),
            "double -> long", set("2: double -> long", "6: double -> long", "6: 2.5 -> 2L"),
            "long -> float", set("2: long -> float", "4: long -> float"),
            "float -> long", set("2: float -> long", "5: float -> long", "5: 1.5f -> 1L"),
            "int -> long", set("2: int -> long", "3: int -> long", "3: 3 -> 3L"),
            "long -> int", set("2: long -> int", "4: long -> int", "4: 7L -> 7")));

    @Test
    void javaGetsEveryConversion() throws IOException {
        assertEquals(EXPECTED, byConversion("T.java", JAVA));
    }

    @Test
    void csharpGetsEveryConversion() throws IOException {
        assertEquals(EXPECTED, byConversion("T.cs", JAVA.replace("scale", "Scale")));
    }

    /** These have no sized number types, so the sized rules leave them alone. */
    @Test
    void unsizedLanguagesAreLeftAlone() throws IOException {
        assertEquals(Map.of(), byConversion("t.py", "def f(n: int, x: float):\n    return n * 2.5\n"));
        assertEquals(Map.of(), byConversion("t.js", "function f(n) { return n * 2.5 + 3; }\n"));
        assertEquals(Map.of(), byConversion("t.rkt", "#lang racket\n(define (f n) (* n 2.5 3))\n"));
    }

    /** Mutants grouped by conversion, read from the rule label ("integer: int32 → float: float64"). */
    private Map<String, Set<String>> byConversion(String fileName, String source) throws IOException {
        Path src = Files.createDirectories(Files.createTempDirectory(dir, "fixture").resolve("src"));
        Files.writeString(src.resolve(fileName), source);
        Map<String, Set<String>> found = new TreeMap<>();
        PrintStream out = System.out;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            for (MutationPoint p : Main.scanForMutations(src, SPEC)) {
                found.computeIfAbsent(conversion(p.getCustomLabel()), k -> new TreeSet<>())
                        .add(p.getLineNumber() + ": " + p.getOperator() + " -> " + p.getReplacement());
            }
        } finally {
            System.setOut(out);
        }
        return found;
    }

    private static String conversion(String label) {
        // Group names have no colon, so the first ": " ends the group path
        String rule = label.substring(label.indexOf(": ") + 2);
        String[] sides = rule.split(" → ");
        return typeName(sides[0]) + " -> " + typeName(sides[1]);
    }

    private static String typeName(String placeholder) {
        return switch (placeholder.strip()) {
            case "integer: int32" -> "int";
            case "integer: int64" -> "long";
            case "float: float32" -> "float";
            case "float: float64" -> "double";
            default -> placeholder;
        };
    }
}
