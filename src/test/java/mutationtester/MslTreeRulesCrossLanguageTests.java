package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * One spec, five languages: the three rule types, Except: trees, plain-English
 * checks and the report of rules a language can't apply.
 */
class MslTreeRulesCrossLanguageTests {

    @TempDir
    Path dir;

    private List<MutationPoint> scan(String spec) throws Exception {
        Path specFile = dir.resolve("rules.spec");
        Files.writeString(specFile, spec);
        return Main.scanForMutations(dir, specFile.toString());
    }

    /** extension → "original => replacement" for each mutant, in file order. */
    private static Map<String, List<String>> byLanguage(List<MutationPoint> points) {
        Map<String, List<String>> out = new TreeMap<>();
        for (MutationPoint p : points) {
            String name = p.getSourceFile().getName();
            out.computeIfAbsent(name.substring(name.lastIndexOf('.')), k -> new java.util.ArrayList<>())
                    .add(p.getOperator().replace("\r", "") + " => " + p.getReplacement());
        }
        return out;
    }

    @Test
    void oneExceptLineCoversTheClampGuardInEveryLanguage() throws Exception {
        Files.writeString(dir.resolve("C.java"), "class C { int f(int n) { if (n <= 0) n = 0; return n <= 9 ? 1 : 0; } }");
        Files.writeString(dir.resolve("c.js"), "function f(n) { if (n <= 0) n = 0; return n <= 9; }");
        Files.writeString(dir.resolve("c.py"), "def f(n):\n    if n <= 0: n = 0\n    return n <= 9\n");
        Files.writeString(dir.resolve("C.cs"), "class C { int F(int n) { if (n <= 0) n = 0; return n <= 9 ? 1 : 0; } }");
        Files.writeString(dir.resolve("c.rkt"), "#lang racket\n(define (f n) (if (<= n 0) 0 n))\n(define (g n) (<= n 9))\n");

        Map<String, List<String>> found = byLanguage(scan(
                "Boundary\n"
                + "    Replace: {quantity} <= {quantity}\n"
                + "    With: {quantity} < {quantity}\n"
                + "    Except: if ({match: {quantity} <= 0}) {quantity} = 0\n"
                + "End\n"));

        // Each guard is left alone; the other <= is mutated.
        for (String ext : List.of(".java", ".js", ".py", ".cs", ".rkt")) {
            assertEquals(List.of("<= => <"), found.get(ext), ext);
        }
    }

    @Test
    void plainEnglishNullCheckMeansEachLanguagesIdiom() throws Exception {
        Files.writeString(dir.resolve("S.java"), "class S { boolean f(String s) { return s == null || s.isEmpty(); } }");
        Files.writeString(dir.resolve("s.js"), "function f(s) { return s == null || s === ''; }");
        Files.writeString(dir.resolve("s.py"), "def f(s):\n    return s is None or s == ''\n");
        Files.writeString(dir.resolve("S.cs"), "class S { bool F(string s) { return string.IsNullOrEmpty(s); } }");
        Files.writeString(dir.resolve("s.rkt"), "#lang racket\n(define (f s) (or (not s) (string=? s \"\")))\n");

        Map<String, List<String>> found = byLanguage(scan(
                "Strings\n"
                + "    Replace: {quantity} is null\n"
                + "    With: {quantity} != null\n"
                + "End\n"));

        assertEquals(List.of("s == null || s.isEmpty() => (s != null)"), found.get(".java"));
        assertEquals(List.of("s == null || s === '' => (s != null)"), found.get(".js"));
        assertEquals(List.of("s is None or s == '' => (s is not None)"), found.get(".py"));
        assertEquals(List.of("string.IsNullOrEmpty(s) => (s != null)"), found.get(".cs"));
        assertEquals(List.of("(or (not s) (string=? s \"\")) => (not (eq? s #f))"), found.get(".rkt"));
    }

    @Test
    void insertWritesAStatementInEachLanguage() throws Exception {
        Files.writeString(dir.resolve("I.java"), "class I {\n  void f() {\n    log(1);\n  }\n}\n");
        Files.writeString(dir.resolve("i.py"), "def f():\n    log(1)\n    return 2\n");
        Files.writeString(dir.resolve("i.rkt"), "#lang racket\n(define (f)\n  (log 1)\n  2)\n");

        Map<String, List<String>> found = byLanguage(scan(
                "Inserts\n"
                + "    Insert: x = 0\n"
                + "    Before: {invocation_statement}\n"
                + "End\n"));

        assertEquals(List.of(" => x = 0;\n    "), found.get(".java"));
        assertEquals(List.of(" => x = 0\n    "), found.get(".py"));
        assertEquals(List.of(" => (set! x 0)\n  "), found.get(".rkt"));
    }

    @Test
    void deleteUsesEachLanguagesEmptyStatement() throws Exception {
        Files.writeString(dir.resolve("D.java"), "class D { void f() { log(1); } }");
        Files.writeString(dir.resolve("d.py"), "def f():\n    log(1)\n    return 2\n");
        Files.writeString(dir.resolve("d.rkt"), "#lang racket\n(define (f)\n  (log 1)\n  2)\n");

        Map<String, List<String>> found = byLanguage(scan(
                "Deletes\n"
                + "    Delete: {invocation_statement}\n"
                + "End\n"));

        assertEquals(List.of("log(1); => ;"), found.get(".java"));
        assertEquals(List.of("log(1) => pass"), found.get(".py"));
        assertEquals(List.of("(log 1) => (void)"), found.get(".rkt"));
    }

    @Test
    void rulesALanguageCantApplyAreReported() throws Exception {
        Files.writeString(dir.resolve("w.rkt"), "#lang racket\n(define (f n) (+ n 1))\n");
        PrintStream out = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        List<MutationPoint> points;
        try {
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            // Racket has no while loop, so this rule can't apply there.
            points = scan("Loops\n"
                    + "    Replace: while ({quantity}) {statement}\n"
                    + "    With: {statement}\n"
                    + "End\n");
        } finally {
            System.setOut(out);
        }
        assertTrue(points.isEmpty());
        String log = captured.toString("UTF-8");
        assertTrue(log.contains("Rules not applied to .rkt files"), log);
        assertTrue(log.contains("Loops: while ({quantity}) {statement}"), log);
    }
}
