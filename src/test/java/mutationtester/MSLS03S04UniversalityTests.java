package mutationtester;

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

/**
 * One shared spec per mutation, applied to every language sample in one scan.
 * Racket has no constructor / non-void call node, so it must stay at zero.
 */
class MSLS03S04UniversalityTests {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("S03 spec: constructor replacement on Java, JS, Python, C#; not Racket")
    void constructorSpecHitsFourLanguages() throws Exception {
        Files.writeString(tempDir.resolve("Example.java"),
                "class Example { Widget make() { return new Widget(); } }\nclass Widget {}\n");
        Files.writeString(tempDir.resolve("example.js"),
                "function Widget() {}\nfunction make() { return new Widget(); }\n");
        Files.writeString(tempDir.resolve("example.py"),
                "class Widget:\n    pass\ndef make():\n    return Widget()\n");
        Files.writeString(tempDir.resolve("Example.cs"),
                "class Example { public Widget Make() { return new Widget(); } }\nclass Widget {}\n");
        Files.writeString(tempDir.resolve("example.rkt"),
                "#lang racket\n(define (make) (list 1))\n");

        Path spec = tempDir.resolve("constructor.spec");
        Files.writeString(spec, """
                Constructor calls

                \tReplace: {constructor_call}
                \tWith: null

                End
                """);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());

        System.out.println("=== S03 one spec, all languages ===");
        points.forEach(p -> System.out.println("  " + p.getSourceFile().getName()
                + "  " + p.getOperator() + " -> " + p.getReplacement()));

        assertTrue(has(points, ".java", "null"), "Java S03");
        assertTrue(has(points, ".js", "null"), "JS S03");
        assertTrue(has(points, ".py", "None"), "Python S03");
        assertTrue(has(points, ".cs", "null"), "C# S03");
        assertFalse(hasExt(points, ".rkt"), "Racket S03 unsupported");
    }

    @Test
    @DisplayName("S04 spec: valued-call replacement on Java, JS, Python, C#; not Racket")
    void nonVoidSpecHitsFourLanguages() throws Exception {
        Files.writeString(tempDir.resolve("Example.java"),
                "class Example { int read() { return compute(); } int compute() { return 9; } }\n");
        Files.writeString(tempDir.resolve("example.js"),
                "function compute() { return 9; }\nfunction read() { return compute(); }\n");
        Files.writeString(tempDir.resolve("example.py"),
                "def compute():\n    return 9\ndef read():\n    return compute()\n");
        Files.writeString(tempDir.resolve("Example.cs"),
                "class Example {\n"
                + "    public int Read() { return Compute(); }\n"
                + "    private int Compute() { return 9; }\n"
                + "}\n");
        Files.writeString(tempDir.resolve("example.rkt"),
                "#lang racket\n(define (compute) 9)\n(define (read) (compute))\n");

        Path spec = tempDir.resolve("nonvoid.spec");
        Files.writeString(spec, """
                Non-void method calls

                \tReplace: {non_void_method_call}
                \tWith: {default_value}

                End
                """);

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());

        System.out.println("=== S04 one spec, all languages ===");
        points.forEach(p -> System.out.println("  " + p.getSourceFile().getName()
                + "  " + p.getOperator() + " -> " + p.getReplacement()));

        assertTrue(has(points, ".java", "0"), "Java S04");
        assertTrue(has(points, ".js", "null"), "JS S04");
        assertTrue(has(points, ".py", "None"), "Python S04");
        assertTrue(has(points, ".cs", "0"), "C# S04");
        assertFalse(hasExt(points, ".rkt"), "Racket S04 unsupported");
    }

    private static boolean has(List<MutationPoint> points, String extension, String replacement) {
        return points.stream().anyMatch(p ->
                p.getSourceFile().getName().endsWith(extension)
                        && replacement.equals(p.getReplacement()));
    }

    private static boolean hasExt(List<MutationPoint> points, String extension) {
        return points.stream().anyMatch(p -> p.getSourceFile().getName().endsWith(extension));
    }
}
