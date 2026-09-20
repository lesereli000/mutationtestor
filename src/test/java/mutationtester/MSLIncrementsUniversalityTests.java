package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * One shared Increments spec applied to all five languages.
 * Run with: mvn "-Dtest=MSLIncrementsUniversalityTests" test
 */
@TestMethodOrder(OrderAnnotation.class)
class MSLIncrementsUniversalityTests {

    private static final String INCREMENTS_SPEC = """
            Increments

            \tReplace: {quantity} ++
            \tWith: {quantity} --
            \tAnd: Inverse

            \tReplace: {quantity} += {quantity}
            \tWith: {quantity} -= {quantity}
            \tAnd: Inverse

            End
            """;

    @TempDir
    Path tempDir;

    @Test
    @Order(1)
    @DisplayName("One Increments spec finds ++ and += on Java/JS/C#, += on Python, add1 on Racket")
    void oneSpecCoversAllFiveLanguages() throws Exception {
        Files.writeString(tempDir.resolve("Example.java"),
                "class Example {\n"
                + "    int postfix(int i) { i++; return i; }\n"
                + "    int prefix(int i) { ++i; return i; }\n"
                + "    int decrement(int i) { i--; return i; }\n"
                + "    int plusEquals(int i) { i += 1; return i; }\n"
                + "    int minusEquals(int i) { i -= 1; return i; }\n"
                + "}\n");
        Files.writeString(tempDir.resolve("example.js"),
                "function postfix(i) { i++; return i; }\n"
                + "function prefix(i) { ++i; return i; }\n"
                + "function decrement(i) { i--; return i; }\n"
                + "function plusEquals(i) { i += 1; return i; }\n"
                + "function minusEquals(i) { i -= 1; return i; }\n");
        Files.writeString(tempDir.resolve("example.py"),
                "def plus_equals(x):\n    x += 1\n    return x\n"
                + "def minus_equals(x):\n    x -= 1\n    return x\n");
        Files.writeString(tempDir.resolve("Example.cs"),
                "class Example {\n"
                + "    int Postfix(int i) { i++; return i; }\n"
                + "    int Prefix(int i) { ++i; return i; }\n"
                + "    int Decrement(int i) { i--; return i; }\n"
                + "    int PlusEquals(int i) { i += 1; return i; }\n"
                + "    int MinusEquals(int i) { i -= 1; return i; }\n"
                + "}\n");
        Files.writeString(tempDir.resolve("example.rkt"),
                "#lang racket\n"
                + "(define (increment-x x) (add1 x))\n"
                + "(define (decrement-x x) (sub1 x))\n");

        Path spec = tempDir.resolve("increments.spec");
        Files.writeString(spec, INCREMENTS_SPEC);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());

        System.out.println();
        System.out.println("=== INCREMENTS: one spec, five languages ===");
        for (MutationPoint point : points) {
            System.out.println("  " + point.getSourceFile().getName()
                    + ":" + point.getLineNumber()
                    + "  " + point.getOperator() + " -> " + point.getReplacement()
                    + "  [" + point.getCustomLabel() + "]");
        }

        assertTrue(has(points, ".java", "++", "--"), "Java postfix/prefix ++");
        assertTrue(has(points, ".java", "--", "++"), "Java inverse --");
        assertTrue(has(points, ".java", "+=", "-="), "Java +=");
        assertTrue(has(points, ".java", "-=", "+="), "Java inverse -=");

        assertTrue(has(points, ".js", "++", "--"), "JS ++");
        assertTrue(has(points, ".js", "+=", "-="), "JS +=");

        assertTrue(has(points, ".cs", "++", "--"), "C# ++");
        assertTrue(has(points, ".cs", "+=", "-="), "C# +=");

        assertTrue(has(points, ".py", "+=", "-="), "Python +=");
        assertFalse(has(points, ".py", "++", "--"), "Python has no ++");

        assertTrue(has(points, ".rkt", "add1", "sub1"), "Racket add1 maps from ++");
        assertTrue(has(points, ".rkt", "sub1", "add1"), "Racket inverse");
        assertFalse(has(points, ".rkt", "+=", "-="), "Racket has no +=");
    }

    @Test
    @Order(2)
    @DisplayName("Apply/restore run: Python x += 1 becomes x -= 1, then original is restored")
    void applyAndRestorePythonIncrement() throws Exception {
        Path py = tempDir.resolve("increment_demo.py");
        String original = "def test():\n    x = 1\n    x += 1\n    return x\n";
        Files.writeString(py, original);

        Path spec = tempDir.resolve("increments.spec");
        Files.writeString(spec, INCREMENTS_SPEC);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());
        MutationPoint plusEquals = points.stream()
                .filter(p -> p.getSourceFile().getName().endsWith(".py"))
                .filter(p -> "+=".equals(p.getOperator()) && "-=".equals(p.getReplacement()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Python += mutant: " + points));

        String mutated = applyMutation(original, plusEquals);
        System.out.println();
        System.out.println("=== APPLY / RESTORE (Python +=) ===");
        System.out.println("Original:\n" + original);
        System.out.println("Mutated:\n" + mutated);
        assertTrue(mutated.contains("x -= 1"), mutated);
        assertFalse(mutated.contains("x += 1"), mutated);

        // Restore is writing the original back — same as Main.testSingleMutant.
        Files.writeString(py, original);
        assertEquals(original, Files.readString(py));
        System.out.println("Restored original. PASS");
    }

    @Test
    @Order(3)
    @DisplayName("Ordinary + is not treated as an increment")
    void doesNotMutatePlainPlus() throws Exception {
        Files.writeString(tempDir.resolve("plain.py"), "def add(a, b):\n    return a + b\n");
        Files.writeString(tempDir.resolve("plain.java"),
                "class Plain { int add(int a, int b) { return a + b; } }\n");
        Path spec = tempDir.resolve("increments.spec");
        Files.writeString(spec, INCREMENTS_SPEC);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());
        assertTrue(points.isEmpty(), "plain + must not match ++ or += : " + points);
    }

    private static boolean has(List<MutationPoint> points, String extension,
                               String from, String to) {
        return points.stream().anyMatch(p ->
                p.getSourceFile().getName().endsWith(extension)
                        && from.equals(p.getOperator())
                        && to.equals(p.getReplacement()));
    }

    private static String applyMutation(String content, MutationPoint point) throws Exception {
        Method method = Main.class.getDeclaredMethod(
                "applyMutation", String.class, MutationPoint.class);
        method.setAccessible(true);
        return (String) method.invoke(null, content, point);
    }
}
