package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * Java MSL mutant quality: quoted text, syntax that looks like operators, bindings.
 * Each language stays in its own file. Specs use one operator spelling.
 */
class JavaMslMutantQualityTests {

    @TempDir
    Path tempDir;

    @Test
    void quotedTextIsNotMutatedAndRealPlusIs() throws Exception {
        Path java = write("""
                class Sample {
                    int mix(int a, int b) {
                        int x = a + b;
                        String y = "a + b";
                        String z = \"\"\"
                            a + b
                            \"\"\";
                        return x;
                    }
                }
                """);

        List<MutationPoint> plus = scan(java, "+", "-");
        assertEquals(1, plus.size(), plus.toString());
        assertEquals("+", plus.get(0).getOperator());
        assertEquals("-", plus.get(0).getReplacement());

        String mutated = Main.applyMutation(Files.readString(java), plus.get(0));
        assertTrue(mutated.contains("int x = a - b;"), mutated);
        assertTrue(mutated.contains("\"a + b\""), mutated);
        assertTrue(mutated.contains("a + b"), mutated);
    }

    @Test
    void plusInsideLambdaStillMutates() throws Exception {
        Path java = write("""
                class Sample {
                    java.util.function.IntBinaryOperator op = (a, b) -> a + b;
                }
                """);

        List<MutationPoint> plus = scan(java, "+", "-");
        assertEquals(1, plus.size(), plus.toString());
        assertEquals("-", plus.get(0).getReplacement());
    }

    @Test
    void genericsAreNotComparisons() throws Exception {
        Path java = write("""
                class Sample {
                    java.util.List<String> names;
                    java.util.Map<String, java.util.List<Integer>> nested;
                }
                """);

        assertTrue(scan(java, "<", "<=").isEmpty());
        assertTrue(scan(java, ">", ">=").isEmpty());
    }

    @Test
    void enhancedForIsNotAnOperator() throws Exception {
        Path java = write("""
                class Sample {
                    void walk(java.util.List<String> items) {
                        for (String x : items) {
                        }
                    }
                }
                """);

        assertTrue(scan(java, "==", "!=").isEmpty());
        assertTrue(scan(java, "<", "<=").isEmpty());
    }

    @Test
    void instanceofIsNotEquality() throws Exception {
        Path java = write("""
                class Sample {
                    boolean check(Object x) {
                        return x instanceof String;
                    }
                }
                """);

        assertTrue(scan(java, "==", "!=").isEmpty());
        assertTrue(scan(java, "!=", "==").isEmpty());
    }

    @Test
    void typeIntersectionIsNotBitwiseAnd() throws Exception {
        Path java = write("""
                interface A {}
                interface B {}
                class Sample<T extends A & B> {}
                """);

        assertTrue(scan(java, "&", "|").isEmpty());
    }

    @Test
    void assignmentMutatesValueNotEqualsBinding() throws Exception {
        Path java = write("""
                class Sample {
                    int mix(int a, int b) {
                        int x = a + b;
                        return x;
                    }
                }
                """);

        List<MutationPoint> plus = scan(java, "+", "-");
        assertEquals(1, plus.size(), plus.toString());
        assertEquals("+", plus.get(0).getOperator());

        List<MutationPoint> eq = scan(java, "==", "!=");
        assertTrue(eq.isEmpty(), eq.toString());
    }

    private Path write(String source) throws Exception {
        Path java = tempDir.resolve("Sample.java");
        Files.writeString(java, source);
        return java;
    }

    private List<MutationPoint> scan(Path java, String from, String to) throws Exception {
        Path spec = tempDir.resolve("rule-" + from.hashCode() + "-" + to.hashCode() + ".spec");
        Files.writeString(spec,
                "Quality\n\n"
                + "Replace: {quantity} " + from + " {quantity}\n"
                + "With: {quantity} " + to + " {quantity}\n\n"
                + "End\n");
        return Main.scanForMutations(java.getParent(), Mutator.MutatorType.CUSTOM, spec.toString())
                .stream()
                .filter(point -> point.getSourceFile().getName().endsWith(".java"))
                .toList();
    }
}
