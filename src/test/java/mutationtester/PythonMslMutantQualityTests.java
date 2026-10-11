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
 * Python MSL mutant quality: f-string interpolations, special forms, bindings.
 * Each language stays in its own file. Specs use one operator spelling.
 */
class PythonMslMutantQualityTests {

    @TempDir
    Path tempDir;

    @Test
    void fStringInterpolationMutatesPlusAndPlainStringsDoNot() throws Exception {
        Path py = write("""
                a = 1
                b = 2
                x = f"{a + b}"
                y = "a + b"
                z = 'a + b'
                """);

        List<MutationPoint> plus = scan(py, "+", "-");
        assertEquals(1, plus.size(), plus.toString());
        assertEquals("+", plus.get(0).getOperator());
        assertEquals("-", plus.get(0).getReplacement());

        String mutated = Main.applyMutation(Files.readString(py), plus.get(0));
        assertTrue(mutated.contains("f\"{a - b}\"") || mutated.contains("f'{a - b}'"), mutated);
        assertTrue(mutated.contains("\"a + b\""));
        assertTrue(mutated.contains("'a + b'"));
        assertParsesAsPython(mutated);
    }

    @Test
    void rawAndFrFStringPrefixesMutate() throws Exception {
        Path py = write("""
                a = 1
                b = 2
                x = rf"{a + b}"
                y = fr"{a + b}"
                """);

        List<MutationPoint> plus = scan(py, "+", "-");
        assertEquals(2, plus.size(), plus.toString());
    }

    @Test
    void brokenOrEmptyFStringFieldsAreSkipped() throws Exception {
        Path py = write("""
                x = f"{}"
                y = f"{a +"
                z = 1 + 2
                """);

        List<MutationPoint> plus = scan(py, "+", "-");
        assertEquals(1, plus.size(), plus.toString());
        assertEquals("+", plus.get(0).getOperator());
    }

    @Test
    void forInAndComprehensionInAreNotEqualityMutants() throws Exception {
        Path py = write("""
                def walk(items):
                    for x in items:
                        pass
                    return [y for y in items]
                """);

        assertTrue(scan(py, "==", "!=").isEmpty());
        assertTrue(scan(py, "!=", "==").isEmpty());
    }

    @Test
    void starUnpackIsNotMultiply() throws Exception {
        Path py = write("def g(xs):\n    return h(*xs)\n");

        List<MutationPoint> star = scan(py, "*", "/");
        assertTrue(star.isEmpty(), star.toString());
    }

    @Test
    void decoratorAtIsNotMultiply() throws Exception {
        Path py = write("""
                def dec(f):
                    return f
                @dec
                def run(a, b):
                    return a * b
                """);

        List<MutationPoint> star = scan(py, "*", "/");
        assertEquals(1, star.size(), star.toString());
        assertEquals("*", star.get(0).getOperator());
    }

    @Test
    void lambdaBodyStillMutatesPlus() throws Exception {
        Path py = write("f = lambda x: x + 1\n");

        List<MutationPoint> plus = scan(py, "+", "-");
        assertEquals(1, plus.size());
        assertEquals("-", plus.get(0).getReplacement());
    }

    @Test
    void assignmentMutatesValueNotEqualsBinding() throws Exception {
        Path py = write("x = a + b\n");

        List<MutationPoint> plus = scan(py, "+", "-");
        assertEquals(1, plus.size());
        assertEquals("+", plus.get(0).getOperator());

        List<MutationPoint> eq = scan(py, "==", "!=");
        assertTrue(eq.isEmpty(), eq.toString());
    }

    @Test
    void defaultArgAndComprehensionFilterStillMutate() throws Exception {
        Path py = write("""
                def f(n=a + b):
                    return [x for x in items if x <= n]
                """);

        List<MutationPoint> plus = scan(py, "+", "-");
        assertEquals(1, plus.size(), plus.toString());

        List<MutationPoint> le = scan(py, "<=", "<");
        assertEquals(1, le.size(), le.toString());
        assertEquals("<", le.get(0).getReplacement());
    }

    private Path write(String source) throws Exception {
        Path py = tempDir.resolve("sample.py");
        Files.writeString(py, source);
        return py;
    }

    private List<MutationPoint> scan(Path py, String from, String to) throws Exception {
        Path spec = tempDir.resolve("rule-" + from.hashCode() + "-" + to.hashCode() + ".spec");
        Files.writeString(spec,
                "Quality\n\n"
                + "Replace: {quantity} " + from + " {quantity}\n"
                + "With: {quantity} " + to + " {quantity}\n\n"
                + "End\n");
        return Main.scanForMutations(py.getParent(), Mutator.MutatorType.CUSTOM, spec.toString())
                .stream()
                .filter(point -> point.getSourceFile().getName().endsWith(".py"))
                .filter(point -> !point.getSourceFile().getName().endsWith(".spec"))
                .toList();
    }

    private static void assertParsesAsPython(String source) throws Exception {
        Path tmp = Files.createTempFile("mutated", ".py");
        Files.writeString(tmp, source);
        ProcessBuilder pb = new ProcessBuilder("python", "-c", "import ast,sys; ast.parse(open(sys.argv[1], encoding='utf-8').read())", tmp.toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        int code = process.waitFor();
        if (code != 0) {
            pb = new ProcessBuilder("py", "-3", "-c", "import ast,sys; ast.parse(open(sys.argv[1], encoding='utf-8').read())", tmp.toString());
            pb.redirectErrorStream(true);
            process = pb.start();
            code = process.waitFor();
        }
        Files.deleteIfExists(tmp);
        assertEquals(0, code, "mutated source must parse as Python");
    }
}
