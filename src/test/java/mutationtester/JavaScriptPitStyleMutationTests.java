package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.JavaScriptMutationScanner;

class JavaScriptPitStyleMutationTests {

    @TempDir
    Path tempDir;

    private Path sourceFile;
    private String source;

    @BeforeEach
    void createJavaScriptFixture() throws Exception {
        source = String.join("\n",
                "function condition(flag, a, b) { if (flag) return a; return flag ? a : b; }",
                "function strings() { return \"items\"; }",
                "function arrays() { return [1, 2]; }",
                "function objects() { return { active: true }; }",
                "function construct() { return new Widget(1); }",
                "function call(value) { return calculate(value); }",
                "function negate(flag) { return !flag; }",
                "function strict(value) { return value === 1; }",
                "function strictNot(value) { return value !== 1; }");
        sourceFile = tempDir.resolve("pit-style.js");
        Files.writeString(sourceFile, source);
    }

    @Test
    void replacesIfAndTernaryConditionsWithTrueOrFalse() throws Exception {
        List<MutationPoint> truePoints = scan(Mutator.MutatorType.CONDITIONAL_TRUE);
        List<MutationPoint> falsePoints = scan(Mutator.MutatorType.CONDITIONAL_FALSE);

        assertEquals(2, truePoints.size());
        assertEquals(2, falsePoints.size());
        assertTrue(truePoints.stream().allMatch(p -> p.getReplacement().equals("true")));
        assertTrue(falsePoints.stream().allMatch(p -> p.getReplacement().equals("false")));
        assertTrue(apply(truePoints.get(0)).contains("if (true)"));
        show("Remove conditionals", truePoints);
    }

    @Test
    void replacesClearLiteralReturnsWithEmptyValues() throws Exception {
        List<MutationPoint> points = scan(Mutator.MutatorType.EMPTY_RETURNS);

        assertEquals(3, points.size());
        assertTrue(points.stream().anyMatch(p -> p.getReplacement().equals("\"\"")));
        assertTrue(points.stream().anyMatch(p -> p.getReplacement().equals("[]")));
        assertTrue(points.stream().anyMatch(p -> p.getReplacement().equals("{}")));
        MutationPoint arrayPoint = points.stream()
                .filter(p -> p.getReplacement().equals("[]"))
                .findFirst().orElseThrow();
        assertTrue(apply(arrayPoint).contains("return [];"));
        show("Empty returns", points);
    }

    @Test
    void replacesConstructorCallsWithNull() throws Exception {
        MutationPoint point = only(scan(Mutator.MutatorType.CONSTRUCTOR_CALLS));
        assertEquals("new Widget(1)", point.getOperator());
        assertEquals("null", point.getReplacement());
        assertTrue(apply(point).contains("return null;"));
        show("Constructor calls", List.of(point));
    }

    @Test
    void replacesDirectReturnedMethodCallsWithNull() throws Exception {
        MutationPoint point = only(scan(Mutator.MutatorType.NON_VOID_METHOD_CALLS));
        assertEquals("calculate(value)", point.getOperator());
        assertEquals("null", point.getReplacement());
        assertTrue(apply(point).contains("function call(value) { return null; }"));
        show("Non-void method calls", List.of(point));
    }

    @Test
    void removesLogicalNegation() throws Exception {
        MutationPoint point = only(scan(Mutator.MutatorType.REMOVE_LOGICAL_NEGATION));
        assertEquals("!", point.getOperator());
        assertEquals("", point.getReplacement());
        String mutated = apply(point);
        assertTrue(mutated.contains("return flag;"));
        assertFalse(mutated.contains("return !flag;"));
        show("Logical negation removal", List.of(point));
    }

    @Test
    void weakensStrictEqualityAndInequality() throws Exception {
        MutationPoint equal = only(scan(Mutator.MutatorType.JS_STRICT_EQ_TO_LOOSE_EQ));
        MutationPoint notEqual = only(scan(Mutator.MutatorType.JS_STRICT_NE_TO_LOOSE_NE));

        assertEquals("===", equal.getOperator());
        assertEquals("==", equal.getReplacement());
        assertTrue(apply(equal).contains("value == 1"));
        assertEquals("!==", notEqual.getOperator());
        assertEquals("!=", notEqual.getReplacement());
        assertTrue(apply(notEqual).contains("value != 1"));
        show("Strict equality", List.of(equal, notEqual));
    }

    private List<MutationPoint> scan(Mutator.MutatorType type) throws Exception {
        return JavaScriptMutationScanner.scanFile(sourceFile.toFile(), type);
    }

    private MutationPoint only(List<MutationPoint> points) {
        assertEquals(1, points.size());
        return points.get(0);
    }

    private String apply(MutationPoint point) throws Exception {
        Method method = Main.class.getDeclaredMethod("applyMutation", String.class, MutationPoint.class);
        method.setAccessible(true);
        return (String) method.invoke(null, source, point);
    }

    private void show(String category, List<MutationPoint> points) {
        System.out.println("[PASS] " + category);
        for (MutationPoint point : points) {
            System.out.println("       " + point.getOperator() + "  ->  " + point.getReplacement());
        }
    }
}
