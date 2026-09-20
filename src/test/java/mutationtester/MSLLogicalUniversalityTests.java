package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import mutationtester.msl.MSLExpressionFactory;

/**
 * One shared Logical spec ({@code &&} ↔ {@code ||}) applied to all five languages.
 * Run with: mvn "-Dtest=MSLLogicalUniversalityTests" test
 */
@TestMethodOrder(OrderAnnotation.class)
class MSLLogicalUniversalityTests {

    private static final String LOGICAL_SPEC = """
            Logical Operators

            \tReplace: {quantity} && {quantity}
            \tWith: {quantity} || {quantity}
            \tAnd: Inverse

            End
            """;

    @TempDir
    Path tempDir;

    @Test
    @Order(1)
    @DisplayName("One Logical spec finds &&/|| on Java/JS/C# and and/or on Python/Racket")
    void oneSpecCoversAllFiveLanguages() throws Exception {
        Files.writeString(tempDir.resolve("Example.java"),
                "class Example {\n"
                + "    boolean both(boolean a, boolean b) { return a && b; }\n"
                + "    boolean either(boolean a, boolean b) { return a || b; }\n"
                + "}\n");
        Files.writeString(tempDir.resolve("example.js"),
                "function both(a, b) { return a && b; }\n"
                + "function either(a, b) { return a || b; }\n");
        Files.writeString(tempDir.resolve("example.py"),
                "def both(a, b):\n    return a and b\n"
                + "def either(a, b):\n    return a or b\n");
        Files.writeString(tempDir.resolve("Example.cs"),
                "class Example {\n"
                + "    bool Both(bool a, bool b) { return a && b; }\n"
                + "    bool Either(bool a, bool b) { return a || b; }\n"
                + "}\n");
        Files.writeString(tempDir.resolve("example.rkt"),
                "#lang racket\n"
                + "(define (both a b) (and a b))\n"
                + "(define (either a b) (or a b))\n");

        Path spec = tempDir.resolve("logical.spec");
        Files.writeString(spec, LOGICAL_SPEC);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());

        System.out.println();
        System.out.println("=== LOGICAL: one spec, five languages ===");
        for (MutationPoint point : points) {
            System.out.println("  " + point.getSourceFile().getName()
                    + ":" + point.getLineNumber()
                    + "  " + point.getOperator() + " -> " + point.getReplacement()
                    + "  [" + point.getCustomLabel() + "]");
        }

        assertTrue(has(points, ".java", "&&", "||"), "Java &&");
        assertTrue(has(points, ".java", "||", "&&"), "Java || inverse");
        assertTrue(has(points, ".js", "&&", "||"), "JS &&");
        assertTrue(has(points, ".js", "||", "&&"), "JS || inverse");
        assertTrue(has(points, ".cs", "&&", "||"), "C# &&");
        assertTrue(has(points, ".cs", "||", "&&"), "C# || inverse");
        assertTrue(has(points, ".py", "and", "or"), "Python and maps from &&");
        assertTrue(has(points, ".py", "or", "and"), "Python or inverse");
        assertTrue(has(points, ".rkt", "and", "or"), "Racket and maps from &&");
        assertTrue(has(points, ".rkt", "or", "and"), "Racket or inverse");
    }

    @Test
    @Order(2)
    @DisplayName("Apply/restore run: Python a and b becomes a or b, then original is restored")
    void applyAndRestorePythonLogical() throws Exception {
        Path py = tempDir.resolve("logical_demo.py");
        String original = "def both(a, b):\n    return a and b\n";
        Files.writeString(py, original);

        Path spec = tempDir.resolve("logical.spec");
        Files.writeString(spec, LOGICAL_SPEC);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());
        MutationPoint andToOr = points.stream()
                .filter(p -> p.getSourceFile().getName().endsWith(".py"))
                .filter(p -> "and".equals(p.getOperator()) && "or".equals(p.getReplacement()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No Python and mutant: " + points));

        String mutated = applyMutation(original, andToOr);
        System.out.println();
        System.out.println("=== APPLY / RESTORE (Python and) ===");
        System.out.println("Original:\n" + original);
        System.out.println("Mutated:\n" + mutated);
        assertTrue(mutated.contains("a or b"), mutated);
        assertFalse(mutated.contains("a and b"), mutated);

        Files.writeString(py, original);
        assertEquals(original, Files.readString(py));
        System.out.println("Restored original. PASS");
    }

    @Test
    @Order(3)
    @DisplayName("Bitwise & / bitwise-and are not logical &&")
    void doesNotMutateBitwiseAnd() throws Exception {
        Files.writeString(tempDir.resolve("bits.py"), "def band(a, b):\n    return a & b\n");
        Files.writeString(tempDir.resolve("bits.java"),
                "class Bits { int band(int a, int b) { return a & b; } }\n");
        Files.writeString(tempDir.resolve("bits.rkt"),
                "#lang racket\n(define (band a b) (bitwise-and a b))\n");
        Path spec = tempDir.resolve("logical.spec");
        Files.writeString(spec, LOGICAL_SPEC);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());
        assertTrue(points.isEmpty(), "bitwise & must not match && : " + points);
    }

    @Test
    @Order(4)
    @DisplayName("Spec spelling and/AND is rejected; use &&")
    void rejectsAndAlias() {
        IllegalArgumentException andWord = assertThrows(IllegalArgumentException.class,
                () -> MSLExpressionFactory.parse("{quantity} and {quantity}"));
        IllegalArgumentException andToken = assertThrows(IllegalArgumentException.class,
                () -> MSLExpressionFactory.parse("{quantity} AND {quantity}"));
        assertTrue(andWord.getMessage().contains("&&"), andWord.getMessage());
        assertTrue(andToken.getMessage().contains("&&"), andToken.getMessage());
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
