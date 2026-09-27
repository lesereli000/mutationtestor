package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.CSharpMutationScanner;
import mutationtester.scanner.JavaMutationScanner;
import mutationtester.scanner.JavaScriptMutationScanner;
import mutationtester.scanner.PythonMutationScanner;
import mutationtester.scanner.RacketMutationScanner;

/**
 * S04 non-void method-call replacement.
 * Racket is unsupported: ISL has no separate non-void call node.
 *
 * Three cases per language: valued call, standalone call, constructor.
 */
class NonVoidMethodCallTests {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Java: valued call produces exactly one S04 mutation")
    void javaValuedCallProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("Value.java");
        Files.writeString(file, """
                class Value {
                    int read() { return compute(); }
                    int compute() { return 9; }
                }
                """);
        List<MutationPoint> points = JavaMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("compute()", points.get(0).getOperator());
        assertEquals("0", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("Java: standalone call produces zero S04 mutations")
    void javaStandaloneCallProducesZero() throws Exception {
        Path file = tempDir.resolve("Standalone.java");
        Files.writeString(file, """
                class Standalone {
                    void run() { compute(); }
                    int compute() { return 9; }
                }
                """);
        List<MutationPoint> points = JavaMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("Java: constructor new produces zero S04 mutations")
    void javaConstructorProducesZero() throws Exception {
        Path file = tempDir.resolve("Ctor.java");
        Files.writeString(file, """
                class Ctor {
                    Widget make() { return new Widget(); }
                }
                class Widget {}
                """);
        List<MutationPoint> points = JavaMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("JavaScript: valued call produces exactly one S04 mutation")
    void jsValuedCallProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("value.js");
        Files.writeString(file, """
                function compute() { return 9; }
                function read() { return compute(); }
                """);
        List<MutationPoint> points = JavaScriptMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("compute()", points.get(0).getOperator());
        assertEquals("null", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("JavaScript: standalone call produces zero S04 mutations")
    void jsStandaloneCallProducesZero() throws Exception {
        Path file = tempDir.resolve("standalone.js");
        Files.writeString(file, """
                function compute() { return 9; }
                function run() { compute(); }
                """);
        List<MutationPoint> points = JavaScriptMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("JavaScript: new constructor produces zero S04 mutations")
    void jsConstructorProducesZero() throws Exception {
        Path file = tempDir.resolve("ctor.js");
        Files.writeString(file, """
                function Widget() {}
                function make() { return new Widget(); }
                """);
        List<MutationPoint> points = JavaScriptMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("Python: valued call produces exactly one S04 mutation")
    void pythonValuedCallProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("value.py");
        Files.writeString(file, """
                def compute():
                    return 9
                def read():
                    return compute()
                """);
        List<MutationPoint> points = PythonMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("compute()", points.get(0).getOperator());
        assertEquals("None", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("Python: standalone call produces zero S04 mutations")
    void pythonStandaloneCallProducesZero() throws Exception {
        Path file = tempDir.resolve("standalone.py");
        Files.writeString(file, """
                def compute():
                    return 9
                def run():
                    compute()
                """);
        List<MutationPoint> points = PythonMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("Python: Foo() constructor produces zero S04 mutations")
    void pythonConstructorProducesZero() throws Exception {
        Path file = tempDir.resolve("ctor.py");
        Files.writeString(file, """
                class Widget:
                    pass
                def make():
                    return Widget()
                """);
        List<MutationPoint> points = PythonMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("Java: assignment of a valued call produces exactly one S04 mutation")
    void javaAssignmentProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("Assign.java");
        Files.writeString(file, """
                class Assign {
                    int read() { int value = compute(); return value; }
                    int compute() { return 9; }
                }
                """);
        List<MutationPoint> points = JavaMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("0", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("JavaScript: assignment of a valued call produces exactly one S04 mutation")
    void jsAssignmentProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("assign.js");
        Files.writeString(file, """
                function compute() { return 9; }
                function read() { const value = compute(); return value; }
                """);
        List<MutationPoint> points = JavaScriptMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("null", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("Python: assignment of a valued call produces exactly one S04 mutation")
    void pythonAssignmentProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("assign.py");
        Files.writeString(file, """
                def compute():
                    return 9
                def read():
                    value = compute()
                    return value
                """);
        List<MutationPoint> points = PythonMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("None", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("C#: valued call produces S04 mutations with default 0")
    void csharpValuedCallProducesMutations() throws Exception {
        Path file = tempDir.resolve("Value.cs");
        Files.writeString(file, """
                class Value {
                    public int Read() { return Compute(); }
                    private int Compute() { return 9; }
                }
                """);
        List<MutationPoint> points = CSharpMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("Compute()", points.get(0).getOperator());
        assertEquals("0", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("C#: standalone call produces zero S04 mutations")
    void csharpStandaloneCallProducesZero() throws Exception {
        Path file = tempDir.resolve("Standalone.cs");
        Files.writeString(file, """
                class Standalone {
                    public void Run() { Compute(); }
                    private int Compute() { return 9; }
                }
                """);
        List<MutationPoint> points = CSharpMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("C#: constructor new produces zero S04 mutations")
    void csharpConstructorProducesZero() throws Exception {
        Path file = tempDir.resolve("Ctor.cs");
        Files.writeString(file, """
                class Ctor {
                    public Widget Make() { return new Widget(); }
                }
                class Widget {}
                """);
        List<MutationPoint> points = CSharpMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("Racket: S04 is unsupported (no non-void call node)")
    void racketUnsupportedProducesZero() throws Exception {
        Path file = tempDir.resolve("value.rkt");
        Files.writeString(file, """
                #lang racket
                (define (compute) 9)
                (define (read) (compute))
                """);
        List<MutationPoint> points = RacketMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.NON_VOID_METHOD_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }
}
