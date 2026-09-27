package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.JavaMutationScanner;
import mutationtester.scanner.JavaScriptMutationScanner;
import mutationtester.scanner.PythonMutationScanner;

/**
 * S03 constructor-call replacement.
 * Racket is unsupported: no separate constructor node.
 *
 * Three cases per language: constructor, normal method call, replacement text.
 */
class ConstructorCallTests {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Java: new produces exactly one S03 mutation")
    void javaConstructorProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("Value.java");
        Files.writeString(file, """
                class Value {
                    Widget make() { return new Widget(); }
                }
                class Widget {}
                """);
        List<MutationPoint> points = JavaMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertTrue(points.get(0).getOperator().contains("new"), points.toString());
        assertEquals("null", points.get(0).getReplacement());
        assertFalse(points.get(0).getReplacement().equals(";")
                || points.get(0).getReplacement().isEmpty());
    }

    @Test
    @DisplayName("Java: normal method call produces zero S03 mutations")
    void javaMethodCallProducesZero() throws Exception {
        Path file = tempDir.resolve("Call.java");
        Files.writeString(file, """
                class Call {
                    int read() { return compute(); }
                    int compute() { return 9; }
                }
                """);
        List<MutationPoint> points = JavaMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("Java: super constructor call is not S03")
    void javaSuperProducesZero() throws Exception {
        Path file = tempDir.resolve("Child.java");
        Files.writeString(file, """
                class Parent { Parent(int x) {} }
                class Child extends Parent {
                    Child() { super(1); }
                }
                """);
        List<MutationPoint> points = JavaMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("JavaScript: new produces exactly one S03 mutation")
    void jsConstructorProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("value.js");
        Files.writeString(file, """
                function Widget() {}
                function make() { return new Widget(); }
                """);
        List<MutationPoint> points = JavaScriptMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertTrue(points.get(0).getOperator().contains("new"), points.toString());
        assertEquals("null", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("JavaScript: normal call produces zero S03 mutations")
    void jsMethodCallProducesZero() throws Exception {
        Path file = tempDir.resolve("call.js");
        Files.writeString(file, """
                function compute() { return 9; }
                function read() { return compute(); }
                """);
        List<MutationPoint> points = JavaScriptMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("JavaScript: S03 replacement is null, not a deleted statement")
    void jsReplacementIsNull() throws Exception {
        Path file = tempDir.resolve("assign.js");
        Files.writeString(file, """
                function Widget() {}
                function make() { const o = new Widget(); return o; }
                """);
        List<MutationPoint> points = JavaScriptMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("null", points.get(0).getReplacement());
        assertFalse(points.get(0).getReplacement().isBlank());
    }

    @Test
    @DisplayName("Python: Widget() constructor produces exactly one S03 mutation")
    void pythonConstructorProducesOneMutation() throws Exception {
        Path file = tempDir.resolve("value.py");
        Files.writeString(file, """
                class Widget:
                    pass
                def make():
                    return Widget()
                """);
        List<MutationPoint> points = PythonMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("Widget()", points.get(0).getOperator());
        assertEquals("None", points.get(0).getReplacement());
    }

    @Test
    @DisplayName("Python: normal compute() call produces zero S03 mutations")
    void pythonMethodCallProducesZero() throws Exception {
        Path file = tempDir.resolve("call.py");
        Files.writeString(file, """
                def compute():
                    return 9
                def read():
                    return compute()
                """);
        List<MutationPoint> points = PythonMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertTrue(points.isEmpty(), points.toString());
    }

    @Test
    @DisplayName("Python: S03 replacement is None, not a deleted statement")
    void pythonReplacementIsNone() throws Exception {
        Path file = tempDir.resolve("assign.py");
        Files.writeString(file, """
                class Widget:
                    pass
                def make():
                    o = Widget()
                    return o
                """);
        List<MutationPoint> points = PythonMutationScanner.scanFile(
                file.toFile(), Mutator.MutatorType.CONSTRUCTOR_CALLS);
        assertEquals(1, points.size(), points.toString());
        assertEquals("None", points.get(0).getReplacement());
        assertFalse(points.get(0).getReplacement().equals("pass")
                || points.get(0).getReplacement().isEmpty());
    }
}
