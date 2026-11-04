package mutationtester;

import static mutationtester.SpecFixture.set;
import static mutationtester.SpecFixture.spec;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.msl.MSLNode;
import mutationtester.msl.MSLPatternParser;
import mutationtester.msl.MSLPlaceholder;
import mutationtester.msl.MutationRuleGenerator;

/** Named placeholders, constraints, spec errors and statement writing. */
class MslFoundationsTests {

    @TempDir
    Path dir;

    private static final String JAVA = """
            class A {
              int f(int end, int start, int a, int b) {
                int d = end - start;
                int z = a - a;
                x = load();
                if (ok) run();
                int y = b * 2;
                int w = b * a;
                return d;
              }
            }
            """;
    private static final String CSHARP = JAVA.replace("load()", "Load()").replace("run()", "Run()")
            .replace("int f(", "int F(");
    private static final String JS = """
            function f(end, start, a, b) {
              let d = end - start;
              let z = a - a;
              x = load();
              if (ok) run();
              let y = b * 2;
              let w = b * a;
              return d;
            }
            """;
    private static final String PYTHON = """
            def f(end, start, a, b):
                d = end - start
                z = a - a
                x = load()
                if ok:
                    run()
                y = b * 2
                w = b * a
                return d
            """;
    private static final String RACKET = """
            #lang racket
            (define (f end start a b)
              (set! x (load))
              (if ok (run) (stop))
              (list (- end start) (- a a) (* b 2) (* b a)))
            """;

    private Set<String> run(String spec, String file, String source) throws IOException {
        return SpecFixture.mutants(dir, spec, file, source);
    }

    // ── Parsing ──────────────────────────────────────────────────────────

    @Test
    void parsesNamesAndConstraints() {
        assertEquals(new MSLPlaceholder("quantity", "a", null, null), MSLPatternParser.parse("{quantity a}"));
        assertEquals(new MSLPlaceholder("integer", null, null, "int64"), MSLPatternParser.parse("{integer: int64}"));
        assertEquals(new MSLPlaceholder("integer", "n", null, "literal"), MSLPatternParser.parse("{integer n: literal}"));
        MSLPlaceholder constrained = (MSLPlaceholder) MSLPatternParser.parse("{quantity: {quantity} + 1}");
        assertEquals("binary", ((MSLNode) constrained.constraint()).kind());
        assertEquals("{quantity a: int64}", new MSLPlaceholder("quantity", "a", null, "int64").toSpecText());
        // A word that isn't a placeholder type opens a block, as before.
        assertEquals(MSLNode.BLOCK, ((MSLNode) MSLPatternParser.parse("{ x; y }")).kind());
        assertEquals(MSLNode.BLOCK, ((MSLNode) MSLPatternParser.parse("{ run(); }")).kind());
    }

    @Test
    void unknownConstraintWordIsAnError() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> MSLPatternParser.parse("{integer: int128}"));
        assertTrue(e.getMessage().contains("unknown constraint \"int128\""), e.getMessage());
    }

    // ── Spec errors (F7, F2, F8) ─────────────────────────────────────────

    private static IOException specError(String... lines) {
        return assertThrows(IOException.class, () -> MutationRuleGenerator.generateRules(List.of(lines)));
    }

    @Test
    void unknownPlaceholderIsAnError() {
        IOException e = specError("G", "", "\tReplace: {quantty} + 1", "\tWith: 0", "", "End");
        assertTrue(e.getMessage().contains("Unknown placeholder {quantty} at line 3"), e.getMessage());
    }

    @Test
    void withNameMustExistInThePattern() {
        IOException e = specError("G", "", "\tReplace: {quantity a} - {quantity b}", "\tWith: {quantity c}", "", "End");
        assertTrue(e.getMessage().contains("no placeholder named c"), e.getMessage());
    }

    @Test
    void conversionNeedsSomethingInItsFamily() {
        IOException e = specError("G", "", "\tReplace: {quantity}", "\tWith: {float}", "", "End");
        assertTrue(e.getMessage().contains("nothing to convert"), e.getMessage());
    }

    // ── Named placeholders (F2) ──────────────────────────────────────────

    private static final String SWAP = spec("Replace: {quantity a} - {quantity b}\nWith: {quantity b} - {quantity a}");

    @Test
    void namedPlaceholdersSwapOperandsInEveryLanguage() throws IOException {
        // a - a swapped is the same code, so it isn't a mutant.
        assertEquals(set("3: end - start -> (start - end)"), run(SWAP, "A.java", JAVA));
        assertEquals(set("3: end - start -> (start - end)"), run(SWAP, "A.cs", CSHARP));
        assertEquals(set("2: end - start -> (start - end)"), run(SWAP, "a.js", JS));
        assertEquals(set("2: end - start -> (start - end)"), run(SWAP, "a.py", PYTHON));
        assertEquals(set("5: (- end start) -> (- start end)"), run(SWAP, "a.rkt", RACKET));
    }

    private static final String SAME = spec("Replace: {quantity x} - {quantity x}\nWith: 0");

    @Test
    void aNameUsedTwiceMatchesTheSameText() throws IOException {
        assertEquals(set("4: a - a -> 0"), run(SAME, "A.java", JAVA));
        assertEquals(set("4: a - a -> 0"), run(SAME, "A.cs", CSHARP));
        assertEquals(set("3: a - a -> 0"), run(SAME, "a.js", JS));
        assertEquals(set("3: a - a -> 0"), run(SAME, "a.py", PYTHON));
        assertEquals(set("5: (- a a) -> 0"), run(SAME, "a.rkt", RACKET));
    }

    // ── Constraints (F6) ─────────────────────────────────────────────────

    private static final String LITERAL = spec("Replace: {quantity} * {quantity: literal}\nWith: {quantity}");

    @Test
    void literalConstraintInEveryLanguage() throws IOException {
        assertEquals(set("7: b * 2 -> b"), run(LITERAL, "A.java", JAVA));
        assertEquals(set("7: b * 2 -> b"), run(LITERAL, "A.cs", CSHARP));
        assertEquals(set("6: b * 2 -> b"), run(LITERAL, "a.js", JS));
        assertEquals(set("7: b * 2 -> b"), run(LITERAL, "a.py", PYTHON));
        assertEquals(set("5: (* b 2) -> b"), run(LITERAL, "a.rkt", RACKET));
    }

    // ── Writing statements (F1) ──────────────────────────────────────────

    private static final String ASSIGN = spec("Replace: {quantity} = {quantity}\nWith: {quantity} = null");

    @Test
    void assignmentRewriteInEveryLanguage() throws IOException {
        // The edit is the assignment itself: Java's ';' stays where it is.
        assertEquals(set("5: x = load() -> x = null"), run(ASSIGN, "A.java", JAVA));
        assertEquals(set("5: x = Load() -> x = null"), run(ASSIGN, "A.cs", CSHARP));
        assertEquals(set("4: x = load() -> x = null"), run(ASSIGN, "a.js", JS));
        // Python's first assignment is also its declaration, so every one matches.
        assertEquals(set("2: d = end - start -> d = None", "3: z = a - a -> z = None", "4: x = load() -> x = None",
                "7: y = b * 2 -> y = None", "8: w = b * a -> w = None"), run(ASSIGN, "a.py", PYTHON));
        assertEquals(set("3: (set! x (load)) -> (set! x '())"), run(ASSIGN, "a.rkt", RACKET));
    }

    private static final String IF_TRUE = spec("Replace: if ({quantity}) {statement}\nWith: if (true) {statement}");

    @Test
    void ifRewriteInEveryLanguage() throws IOException {
        assertEquals(set("6: if (ok) run(); -> if (true) run();"), run(IF_TRUE, "A.java", JAVA));
        assertEquals(set("6: if (ok) Run(); -> if (true) Run();"), run(IF_TRUE, "A.cs", CSHARP));
        assertEquals(set("5: if (ok) run(); -> if (true) run();"), run(IF_TRUE, "a.js", JS));
        // Python writes the colon and indented body, keeping the line break after it.
        assertEquals(set("5: if ok:\n        run()\n     -> if True:\n        run()\n    "),
                run(IF_TRUE, "a.py", PYTHON));
        // Racket's if always has an else, which this pattern doesn't write.
        assertEquals(set(), run(IF_TRUE, "a.rkt", RACKET));
    }

    private static final String UNWRAP = spec("Replace: if ({quantity}) {statement}\nWith: {statement}");

    @Test
    void ifUnwrapInEveryLanguage() throws IOException {
        assertEquals(set("6: if (ok) run(); -> run();"), run(UNWRAP, "A.java", JAVA));
        assertEquals(set("6: if (ok) Run(); -> Run();"), run(UNWRAP, "A.cs", CSHARP));
        assertEquals(set("5: if (ok) run(); -> run();"), run(UNWRAP, "a.js", JS));
        assertEquals(set("5: if ok:\n        run()\n     -> run()\n    "), run(UNWRAP, "a.py", PYTHON));
        // An if pattern without an else matches only ifs without one; Racket's
        // (if c a b) always has an else (its else-less if is (when c s)).
        assertEquals(set(), run(UNWRAP, "a.rkt", RACKET));
    }
}
