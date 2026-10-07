package mutationtester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static mutationtester.OperatorMutationsCrossLanguageParityTests.mutatedLines;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * Runs StatementMutation.txt (S01-S08) over equivalent code in all five
 * languages and checks the source each mutant produces, so a rule can't
 * silently emit code that doesn't compile (e.g. "return null;" in an int
 * method, or Racket's ";", which starts a comment).
 */
class StatementMutationsCrossLanguageParityTests {

    @TempDir
    Path tempDir;

    private List<MutationPoint> points;

    @BeforeEach
    void scanFixtures() throws Exception {
        write("Svc.java",
                "class Svc {\n" +
                "  void log(String m) { System.out.println(m); }\n" +
                "  int compute(int a, int b) {\n" +
                "    log(\"start\");\n" +
                "    Object o = new Object();\n" +
                "    int v = helper(a);\n" +
                "    if (a == b) { return 0; }\n" +
                "    if (a < b) { return 1; }\n" +
                "    return a + b;\n" +
                "  }\n" +
                "  int helper(int x) { return x; }\n" +
                "  String name() { return \"svc\"; }\n" +
                "}\n");
        write("svc.js",
                "function log(m) { console.log(m); }\n" +
                "function helper(x) { return x; }\n" +
                "function compute(a, b) {\n" +
                "  log('start');\n" +
                "  const o = new Map();\n" +
                "  let v = helper(a);\n" +
                "  if (a === b) { return 0; }\n" +
                "  if (a < b) { return 1; }\n" +
                "  return a + b;\n" +
                "}\n");
        write("svc.py",
                "def log(m):\n    print(m)\n\n" +
                "def helper(x):\n    return x\n\n" +
                "def compute(a, b):\n" +
                "    log('start')\n" +
                "    o = Thing()\n" +
                "    v = helper(a)\n" +
                "    if a == b:\n        return 0\n" +
                "    if a < b:\n        return 1\n" +
                "    return a + b\n");
        write("Svc.cs",
                "class Svc {\n" +
                "  void Log(string m) { System.Console.WriteLine(m); }\n" +
                "  int Compute(int a, int b) {\n" +
                "    Log(\"start\");\n" +
                "    var o = new object();\n" +
                "    int v = Helper(a);\n" +
                "    if (a == b) { return 0; }\n" +
                "    if (a < b) { return 1; }\n" +
                "    return a + b;\n" +
                "  }\n" +
                "  int Helper(int x) { return x; }\n" +
                "  bool Ready() { return v > 0; }\n" +
                "}\n");
        write("svc.rkt",
                "#lang racket/base\n" +
                "(struct point (x y))\n" +
                "(define (helper x) x)\n" +
                "(define (compute a b)\n" +
                "  (displayln \"start\")\n" +
                "  (define p (point a b))\n" +
                "  (define v (helper a))\n" +
                "  (cond\n" +
                "    [(= a b) 0]\n" +
                "    [(< a b) 1]\n" +
                "    [else (+ a b)]))\n");

        points = Main.scanForMutations(tempDir, Mutator.MutatorType.CUSTOM, "StatementMutation.txt");
    }

    @Test
    void s01RemovesCallStatements() throws Exception {
        List<MutationPoint> rule = rule("statement \u2192 ;");
        assertEquals(s(";", "void log(String m) { ; }"), mutatedLines(rule, ".java"));
        assertEquals(s(";", "function log(m) { ; }"), mutatedLines(rule, ".js"));
        assertEquals(s("pass"), mutatedLines(rule, ".py"));
        assertEquals(s(";", "void Log(string m) { ; }"), mutatedLines(rule, ".cs"));
        // Only the effect-only call; definitions and the body's final form stay.
        assertEquals(s("(void)"), mutatedLines(rule, ".rkt"));
    }

    @Test
    void s02ReturnsAnEmptyValueOfTheDeclaredType() throws Exception {
        List<MutationPoint> rule = rule("return value \u2192 empty value");
        assertEquals(s("return 0;", "if (a < b) { return 0; }", "int helper(int x) { return 0; }",
                        "String name() { return \"\"; }"),
                mutatedLines(rule, ".java"));
        assertEquals(s("return null;", "if (a < b) { return 0; }", "function helper(x) { return null; }"),
                mutatedLines(rule, ".js"));
        assertEquals(s("return 0", "return None"), mutatedLines(rule, ".py"));
        assertEquals(s("return 0;", "if (a < b) { return 0; }", "int Helper(int x) { return 0; }",
                        "bool Ready() { return false; }"),
                mutatedLines(rule, ".cs"));
        // A Racket function returns its last body form.
        assertEquals(s("(define (helper x) #f)", "#f)"), mutatedLines(rule, ".rkt"));
    }

    @Test
    void s03ReplacesConstructorCalls() throws Exception {
        List<MutationPoint> rule = rule("constructor call \u2192 null");
        assertEquals(s("Object o = null;"), mutatedLines(rule, ".java"));
        assertEquals(s("const o = null;"), mutatedLines(rule, ".js"));
        assertEquals(s("o = None"), mutatedLines(rule, ".py"));
        // "var o = null;" doesn't compile, so a var keeps its type via default(T).
        assertEquals(s("var o = default(object);"), mutatedLines(rule, ".cs"));
        // '() is Racket's null, as in the other Racket null-value mutators.
        assertEquals(s("(define p '())"), mutatedLines(rule, ".rkt"));
    }

    @Test
    void s04ReplacesCallResultsWithTypedDefaults() throws Exception {
        List<MutationPoint> rule = rule("non-void method call \u2192 default value");
        assertEquals(s("int v = 0;"), mutatedLines(rule, ".java"));
        assertEquals(s("let v = null;"), mutatedLines(rule, ".js"));
        assertEquals(s("o = None", "v = None"), mutatedLines(rule, ".py"));
        assertEquals(s("int v = 0;"), mutatedLines(rule, ".cs"));
        assertEquals(s("(define p '())", "(define v '())"), mutatedLines(rule, ".rkt"));
    }

    @Test
    void s05ToS08RemoveConditionals() throws Exception {
        assertConditional("equality condition \u2192 true",
                "if (true) { return 0; }", "if True:", "[#t 0]");
        assertConditional("equality condition \u2192 false",
                "if (false) { return 0; }", "if False:", "[#f 0]");
        assertConditional("order condition \u2192 true",
                "if (true) { return 1; }", "if True:", "[#t 1]");
        assertConditional("order condition \u2192 false",
                "if (false) { return 1; }", "if False:", "[#f 1]");
    }

    @Test
    void everyMutantIsLabelledWithItsSpecRule() {
        for (MutationPoint point : points) {
            assertEquals(true, point.getCustomLabel() != null
                            && point.getCustomLabel().startsWith("Statement Mutations: "),
                    "Unlabelled mutant: " + point);
        }
    }

    private void assertConditional(String ruleSuffix, String cStyle, String python, String racket)
            throws Exception {
        List<MutationPoint> rule = rule(ruleSuffix);
        assertEquals(s(cStyle), mutatedLines(rule, ".java"), ruleSuffix);
        assertEquals(s(cStyle), mutatedLines(rule, ".js"), ruleSuffix);
        assertEquals(s(python), mutatedLines(rule, ".py"), ruleSuffix);
        assertEquals(s(cStyle), mutatedLines(rule, ".cs"), ruleSuffix);
        assertEquals(s(racket), mutatedLines(rule, ".rkt"), ruleSuffix);
    }

    private List<MutationPoint> rule(String labelSuffix) {
        return points.stream()
                .filter(p -> p.getCustomLabel() != null && p.getCustomLabel().endsWith(labelSuffix))
                .toList();
    }

    private static Set<String> s(String... lines) {
        return new TreeSet<>(List.of(lines));
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(tempDir.resolve(name), content);
    }
}
