package mutationtester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * Checks every rule in OperatorMutations.txt against equivalent code in all
 * five languages. Each mutant is applied to the source, and the changed line
 * must match exactly, so this catches missing mutants, no-op mutants,
 * mutants on the wrong operator, and replacements that aren't valid code.
 *
 * Where a language has no operator for an MSL symbol, its standard spelling
 * stands in: Math.floorDiv(a, b) / Math.floor(a / b) / Math.Floor(a / b) /
 * (floor (/ a b)) for //, Math.pow / Math.Pow for **, and a shift of a value
 * masked to 32 bits for >>> in Python and Racket.
 */
class OperatorMutationsCrossLanguageParityTests {

    private static final String[] LANGUAGES = {".java", ".js", ".py", ".cs", ".rkt"};

    @TempDir
    Path tempDir;

    static Stream<Arguments> operatorRules() {
        return Stream.of(
            //   id     from   to     Java                   JavaScript              Python               C#                      Racket
            rule("O01", "+",   "-",   s("int r = a - b;"),   s("let r = a - b;"),    s("r = a - b"),      s("int r = a - b;"),    s("(- a b)")),
            rule("O02", "-",   "+",   s("r = a + b;"),       s("r = a + b;"),        s("r = a + b"),      s("r = a + b;"),        s("(+ a b)")),
            rule("O03", "*",   "/",   s("r = a / b;"),       s("r = a / b;"),        s("r = a / b"),      s("r = a / b;"),        s("(/ a b)")),
            rule("O04", "/",   "*",   s("r = a * b;"),       s("r = a * b;"),        s("r = a * b"),      s("r = a * b;"),        s("(* a b)")),
            rule("O05", "%",   "*",   s("r = a * b;"),       s("r = a * b;"),        s("r = a * b"),      s("r = a * b;"),        s("(* a b)")),
            rule("O06", "//",  "/",   s("r = (a / b);"),     s("r = (a / b);"),      s("r = a / b"),      s("r = (int)((double)a / b);"),
                                                                                                                                  s("(/ a b)", "(/ p q)")),
            rule("O07", "**",  "*",   s("double d = (a * b);"), s("r = a * b;"),     s("r = a * b"),      s("double d = (a * b);"), s("(* a b)")),
            rule("O08", "&",   "|",   s("r = a | b;"),       s("r = a | b;"),        s("r = a | b"),      s("r = a | b;"),        s("(bitwise-ior a b)")),
            rule("O09", "|",   "&",   s("r = a & b;"),       s("r = a & b;"),        s("r = a & b"),      s("r = a & b;"),        s("(bitwise-and a b)")),
            rule("O10", "^",   "&",   s("r = a & b;"),       s("r = a & b;"),        s("r = a & b"),      s("r = a & b;"),        s("(bitwise-and a b)")),
            rule("O11", ">>",  "<<",  s("r = a << b;"),      s("r = a << b;"),       s("r = a << b"),     s("r = a << b;"),       s("(arithmetic-shift a b)")),
            rule("O12", "<<",  ">>",  s("r = a >> b;"),      s("r = a >> b;"),       s("r = a >> b"),     s("r = a >> b;"),       s("(arithmetic-shift a (- b))")),
            rule("O13", ">>>", "<<",  s("r = a << b;"),      s("r = a << b;"),       s("r = (a & 0xFFFFFFFF) << b"),
                                                                                                          s("r = a << b;"),
                                                                                                          s("(arithmetic-shift (bitwise-and a #xFFFFFFFF) b)")),
            rule("O14", ">",   ">=",  s("boolean c = a >= b;"), s("let c = a >= b;"), s("c = a >= b"),    s("bool c = a >= b;"),  s("(>= a b)")),
            rule("O15", ">=",  ">",   s("c = a > b;"),       s("c = a > b;"),        s("c = a > b"),      s("c = a > b;"),        s("(> a b)")),
            rule("O16", "<",   "<=",  s("c = a <= b;"),      s("c = a <= b;"),       s("c = a <= b"),     s("c = a <= b;"),       s("(<= a b)")),
            rule("O17", "<=",  "<",   s("c = a < b;"),       s("c = a < b;"),        s("c = a < b"),      s("c = a < b;"),        s("(< a b)")),
            rule("O18", ">",   "<=",  s("boolean c = a <= b;"), s("let c = a <= b;"), s("c = a <= b"),    s("bool c = a <= b;"),  s("(<= a b)")),
            rule("O19", "<=",  ">",   s("c = a > b;"),       s("c = a > b;"),        s("c = a > b"),      s("c = a > b;"),        s("(> a b)")),
            rule("O20", "<",   ">=",  s("c = a >= b;"),      s("c = a >= b;"),       s("c = a >= b"),     s("c = a >= b;"),       s("(>= a b)")),
            rule("O21", ">=",  "<",   s("c = a < b;"),       s("c = a < b;"),        s("c = a < b"),      s("c = a < b;"),        s("(< a b)")),
            rule("O22", "==",  "!=",  s("c = a != b;"),      s("c = a != b;", "c = a !== b;"),
                                                                                     s("c = a != b"),     s("c = a != b;"),
                                                                                     s("(not (= a b))", "(not (not (= a b)))")),
            rule("O23", "!=",  "==",  s("c = a == b;"),      s("c = a == b;", "c = a === b;"),
                                                                                     s("c = a == b"),     s("c = a == b;"),       s("(= a b)")),
            rule("O24", "&&",  "||",  s("c = p || q;"),      s("c = p || q;"),       s("c = p or q"),     s("c = p || q;"),       s("(or p q)")),
            rule("O25", "||",  "&&",  s("c = p && q;"),      s("c = p && q;"),       s("c = p and q"),    s("c = p && q;"),       s("(and p q)))"))
        );
    }

    @ParameterizedTest(name = "{0}: {1} -> {2}")
    @MethodSource("operatorRules")
    void operatorRuleProducesTheSameMutantInEveryLanguage(
            String id, String from, String to, List<Set<String>> expectedByLanguage) throws Exception {
        writeOperatorFixtures(tempDir);
        List<MutationPoint> points = scan(tempDir, from, to);

        for (int i = 0; i < LANGUAGES.length; i++) {
            assertEquals(expectedByLanguage.get(i), mutatedLines(points, LANGUAGES[i]),
                    id + " mutants in " + LANGUAGES[i]);
        }
    }

    // ── Operand shapes ───────────────────────────────────────────────────────

    @Test
    void logicalOperatorsMatchComparisonOperands() throws Exception {
        write("Logic.java", "class Logic {\n  boolean f(int a, int b) {\n    return a > 0 && b > 0;\n  }\n}\n");
        write("logic.js", "function f(a, b) {\n  return a > 0 && b > 0;\n}\n");
        write("logic.py", "def f(a, b):\n    return a > 0 and b > 0\n");
        write("Logic.cs", "class Logic {\n  bool F(int a, int b) {\n    return a > 0 && b > 0;\n  }\n}\n");
        write("logic.rkt", "#lang racket/base\n(define (f a b)\n  (and (> a 0) (> b 0)))\n");

        List<MutationPoint> points = scan(tempDir, "&&", "||");

        assertEquals(s("return a > 0 || b > 0;"), mutatedLines(points, ".java"));
        assertEquals(s("return a > 0 || b > 0;"), mutatedLines(points, ".js"));
        assertEquals(s("return a > 0 or b > 0"), mutatedLines(points, ".py"));
        assertEquals(s("return a > 0 || b > 0;"), mutatedLines(points, ".cs"));
        assertEquals(s("(or (> a 0) (> b 0)))"), mutatedLines(points, ".rkt"));
    }

    @Test
    void arithmeticMatchesCallAndIndexOperands() throws Exception {
        write("Calls.java", "class Calls {\n  int f(int[] xs, int i) {\n    return xs[i] + size(xs);\n  }\n}\n");
        write("calls.js", "function f(xs, i) {\n  return xs[i] + xs.length;\n}\n");
        write("calls.py", "def f(xs, i):\n    return xs[i] + len(xs)\n");
        write("Calls.cs", "class Calls {\n  int F(int[] xs, int i) {\n    return xs[i] + xs.Length;\n  }\n}\n");
        write("calls.rkt", "#lang racket/base\n(define (f xs i)\n  (+ (list-ref xs i) (length xs)))\n");

        List<MutationPoint> points = scan(tempDir, "+", "-");

        assertEquals(s("return xs[i] - size(xs);"), mutatedLines(points, ".java"));
        assertEquals(s("return xs[i] - xs.length;"), mutatedLines(points, ".js"));
        assertEquals(s("return xs[i] - len(xs)"), mutatedLines(points, ".py"));
        assertEquals(s("return xs[i] - xs.Length;"), mutatedLines(points, ".cs"));
        assertEquals(s("(- (list-ref xs i) (length xs)))"), mutatedLines(points, ".rkt"));
    }

    @Test
    void stringConcatenationIsNotMutated() throws Exception {
        write("Strings.java", "class Strings {\n  String f(int n) {\n    return \"n=\" + n;\n  }\n}\n");
        write("strings.js", "function f(n) {\n  return 'n=' + n;\n}\n");
        write("strings.py", "def f(n):\n    return f'n=' + n\n");
        write("Strings.cs", "class Strings {\n  string F(int n) {\n    return $\"n=\" + n;\n  }\n}\n");

        List<MutationPoint> points = scan(tempDir, "+", "-");

        assertEquals(List.of(), points);
    }

    @Test
    void unaryMinusIsNotABinaryOperator() throws Exception {
        write("neg.py", "def f(a):\n    return -a\n");
        write("neg.rkt", "#lang racket/base\n(define (f a)\n  (- a))\n");

        assertEquals(List.of(), scan(tempDir, "-", "+"));
    }

    @Test
    void chainedOperatorsAreEachMutatedOnce() throws Exception {
        write("Chain.java", "class Chain {\n  int f(int a, int b, int c) {\n    return a + b - c;\n  }\n}\n");
        write("chain.js", "function f(a, b, c) {\n  return a + b - c;\n}\n");
        write("chain.py", "def f(a, b, c):\n    return a + b - c\n");
        write("Chain.cs", "class Chain {\n  int F(int a, int b, int c) {\n    return a + b - c;\n  }\n}\n");

        List<MutationPoint> points = scan(tempDir, "-", "+");

        assertEquals(s("return a + b + c;"), mutatedLines(points, ".java"));
        assertEquals(s("return a + b + c;"), mutatedLines(points, ".js"));
        assertEquals(s("return a + b + c"), mutatedLines(points, ".py"));
        assertEquals(s("return a + b + c;"), mutatedLines(points, ".cs"));
    }

    @Test
    void contextSpecSkipsClampGuardsInEveryLanguage() throws Exception {
        write("Clamp.java", "class Clamp {\n  int f(int a, int b) {\n    if (a <= 0) a = 0;\n    return a < b ? a : b;\n  }\n}\n");
        write("clamp.js", "function f(a, b) {\n  if (a <= 0) a = 0;\n  return a < b ? a : b;\n}\n");
        write("clamp.py", "def f(a, b):\n    if a <= 0: a = 0\n    return a if a < b else b\n");
        write("Clamp.cs", "class Clamp {\n  int F(int a, int b) {\n    if (a <= 0) a = 0;\n    return a < b ? a : b;\n  }\n}\n");
        write("clamp.rkt", "#lang racket/base\n(define (f a b)\n  (if (<= a 0) 0\n      (if (< a b) a b)))\n");

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, "specs/context-sensitive-mutations.spec");

        // Only the a < b comparison is mutated; the a <= 0 clamp is excepted.
        assertEquals(s("return a <= b ? a : b;"), mutatedLines(points, ".java"));
        assertEquals(s("return a <= b ? a : b;"), mutatedLines(points, ".js"));
        assertEquals(s("return a if a <= b else b"), mutatedLines(points, ".py"));
        assertEquals(s("return a <= b ? a : b;"), mutatedLines(points, ".cs"));
        assertEquals(s("(if (<= a b) a b)))"), mutatedLines(points, ".rkt"));
    }

    @Test
    void eachSpecSectionLabelsItsOwnRules() throws Exception {
        writeOperatorFixtures(tempDir);
        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, "OperatorMutations.txt");

        Set<String> sections = new TreeSet<>();
        for (MutationPoint point : points) {
            sections.add(point.getCustomLabel().substring(0, point.getCustomLabel().indexOf(':')));
        }
        assertEquals(s("Conditionals Boundary", "Logical Operators", "Math Operators", "Negate Conditionals"),
                sections);
    }

    @Test
    void equalityIncludesEachLanguagesIdentityAndValueComparisons() throws Exception {
        // == / != cover null checks (Python: is / is not), string comparisons,
        // and value-equality calls (Java .equals, C# .Equals, Racket string=?).
        write("Eq.java", "class Eq {\n  boolean f(String s) {\n    boolean n = s == null;\n    boolean v = s.equals(\"x\");\n    return !s.equals(\"y\");\n  }\n}\n");
        write("eq.js", "function f(s) {\n  const n = s === null;\n  const v = s === \"x\";\n  return s !== \"y\";\n}\n");
        write("eq.py", "def f(s):\n    n = s is None\n    v = s == \"x\"\n    return s is not \"y\"\n");
        write("Eq.cs", "class Eq {\n  bool F(string s) {\n    bool n = s == null;\n    bool v = s.Equals(\"x\");\n    return !s.Equals(\"y\");\n  }\n}\n");
        write("eq.rkt", "#lang racket/base\n(define (f s)\n  (define n (eq? s #f))\n  (define v (string=? s \"x\"))\n  (not (string=? s \"y\")))\n");

        List<MutationPoint> negate = scan(tempDir, "==", "!=");
        assertEquals(s("boolean n = s != null;", "boolean v = !s.equals(\"x\");", "return !!s.equals(\"y\");"),
                mutatedLines(negate, ".java"));
        assertEquals(s("const n = s !== null;", "const v = s !== \"x\";"), mutatedLines(negate, ".js"));
        assertEquals(s("n = s is not None", "v = s != \"x\""), mutatedLines(negate, ".py"));
        assertEquals(s("bool n = s != null;", "bool v = !s.Equals(\"x\");", "return !!s.Equals(\"y\");"),
                mutatedLines(negate, ".cs"));
        assertEquals(s("(define n (not (eq? s #f)))", "(define v (not (string=? s \"x\")))",
                       "(not (not (string=? s \"y\"))))"),
                mutatedLines(negate, ".rkt"));

        List<MutationPoint> restore = scan(tempDir, "!=", "==");
        assertEquals(s("return s.equals(\"y\");"), mutatedLines(restore, ".java"));
        assertEquals(s("return s === \"y\";"), mutatedLines(restore, ".js"));
        assertEquals(s("return s is \"y\""), mutatedLines(restore, ".py"));
        assertEquals(s("return s.Equals(\"y\");"), mutatedLines(restore, ".cs"));
        assertEquals(s("(string=? s \"y\"))"), mutatedLines(restore, ".rkt"));
    }

    @Test
    void idiomOperatorsAsTargetsAreWrittenOutWhereTypeSafe() throws Exception {
        write("T.java", "class T {\n  int f(int a, int b) {\n    return a / b + a * b + (a >> b);\n  }\n}\n");
        write("t.js", "function f(a, b) {\n  return a / b + a * b + (a >> b);\n}\n");
        write("t.py", "def f(a, b):\n    return a / b + a * b + (a >> b)\n");
        write("T.cs", "class T {\n  int F(int a, int b) {\n    return a / b + a * b + (a >> b);\n  }\n}\n");
        write("t.rkt", "#lang racket/base\n(define (f a b)\n  (+ (/ a b) (* a b) (arithmetic-shift a (- b))))\n");

        // Java and C# have no // or ** operator, and Math.floorDiv / Math.pow /
        // Math.Floor can't replace an operator without knowing the operand types
        // (floorDiv takes integers; pow and Floor return double).
        List<MutationPoint> floorDiv = scan(tempDir, "/", "//");
        assertEquals(s(), mutatedLines(floorDiv, ".java"));
        assertEquals(s("return Math.floor(a / b) + a * b + (a >> b);"), mutatedLines(floorDiv, ".js"));
        assertEquals(s("return a // b + a * b + (a >> b)"), mutatedLines(floorDiv, ".py"));
        assertEquals(s(), mutatedLines(floorDiv, ".cs"));
        assertEquals(s("(+ (floor (/ a b)) (* a b) (arithmetic-shift a (- b))))"), mutatedLines(floorDiv, ".rkt"));

        List<MutationPoint> power = scan(tempDir, "*", "**");
        assertEquals(s(), mutatedLines(power, ".java"));
        assertEquals(s("return a / b + a ** b + (a >> b);"), mutatedLines(power, ".js"));
        assertEquals(s("return a / b + a ** b + (a >> b)"), mutatedLines(power, ".py"));
        assertEquals(s(), mutatedLines(power, ".cs"));
        assertEquals(s("(+ (/ a b) (expt a b) (arithmetic-shift a (- b))))"), mutatedLines(power, ".rkt"));

        List<MutationPoint> unsigned = scan(tempDir, ">>", ">>>");
        assertEquals(s("return a / b + a * b + (a >>> b);"), mutatedLines(unsigned, ".java"));
        assertEquals(s("return a / b + a * b + (a >>> b);"), mutatedLines(unsigned, ".js"));
        assertEquals(s("return a / b + a * b + ((a & 0xFFFFFFFF) >> b)"), mutatedLines(unsigned, ".py"));
        assertEquals(s("return a / b + a * b + (a >>> b);"), mutatedLines(unsigned, ".cs"));
        assertEquals(s("(+ (/ a b) (* a b) (arithmetic-shift (bitwise-and a #xFFFFFFFF) (- b))))"),
                mutatedLines(unsigned, ".rkt"));
    }

    // ── Racket special forms ─────────────────────────────────────────────────

    @Test
    void racketEqualityFunctionsAreNegatedWithNot() throws Exception {
        write("eq.rkt", "#lang racket/base\n(define (f a b)\n  (list (equal? a b) (not (eqv? a b))))\n");

        assertEquals(s("(list (not (equal? a b)) (not (eqv? a b))))",
                       "(list (equal? a b) (not (not (eqv? a b)))))"),
                mutatedLines(scan(tempDir, "==", "!="), ".rkt"));
        assertEquals(s("(list (equal? a b) (eqv? a b)))"),
                mutatedLines(scan(tempDir, "!=", "=="), ".rkt"));
    }

    @Test
    void racketMultiLineEqualityIsWrappedInPlace() throws Exception {
        write("multi.rkt", "#lang racket/base\n(define (f a b)\n  (= a\n     b))\n");

        assertEquals(s("(not (= a\nb)))"), mutatedLines(scan(tempDir, "==", "!="), ".rkt"));
    }

    @Test
    void racketRebuildsCallsWhenSwitchingToASpecialForm() throws Exception {
        write("cmp.rkt", "#lang racket/base\n(define (f a b)\n  (list (< a b) (arithmetic-shift a b)))\n");

        assertEquals(s("(list (not (= a b)) (arithmetic-shift a b)))"),
                mutatedLines(scan(tempDir, "<", "!="), ".rkt"));
        assertEquals(s("(list (< a b) (+ a b)))"),
                mutatedLines(scan(tempDir, "<<", "+"), ".rkt"));
    }

    @Test
    void racketNegativeShiftLiteralIsARightShift() throws Exception {
        write("shift.rkt", "#lang racket/base\n(define (f a)\n  (arithmetic-shift a -2))\n");

        assertEquals(s("(arithmetic-shift a 2))"), mutatedLines(scan(tempDir, ">>", "<<"), ".rkt"));
        assertEquals(s(), mutatedLines(scan(tempDir, "<<", ">>"), ".rkt"));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static Arguments rule(String id, String from, String to,
                                  Set<String> java, Set<String> js, Set<String> py,
                                  Set<String> cs, Set<String> rkt) {
        return Arguments.of(id, from, to, List.of(java, js, py, cs, rkt));
    }

    private static Set<String> s(String... lines) {
        return new TreeSet<>(List.of(lines));
    }

    private void write(String name, String content) throws Exception {
        Files.writeString(tempDir.resolve(name), content);
    }

    private static List<MutationPoint> scan(Path dir, String from, String to) throws Exception {
        Path spec = Files.createTempFile(dir.getParent(), "rule", ".spec");
        Files.writeString(spec,
                "Rule\n\n" +
                "Replace: {quantity} " + from + " {quantity}\n" +
                "With: {quantity} " + to + " {quantity}\n\n" +
                "End\n");
        try {
            return Main.scanForMutations(dir, Mutator.MutatorType.CUSTOM, spec.toString());
        } finally {
            Files.deleteIfExists(spec);
        }
    }

    /**
     * Applies each mutant in files with the given extension and returns the
     * changed source, one entry per mutant (trimmed lines joined by "\n").
     */
    static Set<String> mutatedLines(List<MutationPoint> points, String extension) throws Exception {
        Set<String> result = new TreeSet<>();
        for (MutationPoint point : points) {
            if (!point.getSourceFile().getName().endsWith(extension)) continue;

            String original = Files.readString(point.getSourceFile().toPath());
            String mutated = Main.applyMutation(original, point);
            String[] before = original.split("\n", -1);
            String[] after = mutated.split("\n", -1);

            int start = 0;
            while (start < before.length && start < after.length && before[start].equals(after[start])) start++;
            int endBefore = before.length - 1;
            int endAfter = after.length - 1;
            while (endBefore >= start && endAfter >= start && before[endBefore].equals(after[endAfter])) {
                endBefore--;
                endAfter--;
            }

            StringBuilder changed = new StringBuilder();
            for (int i = start; i <= endAfter; i++) {
                if (changed.length() > 0) changed.append("\n");
                changed.append(after[i].strip());
            }
            result.add(changed.toString());
        }
        return result;
    }

    private static void writeOperatorFixtures(Path dir) throws Exception {
        Files.writeString(dir.resolve("Ops.java"),
                "class Ops {\n" +
                "  int f(int a, int b, boolean p, boolean q) {\n" +
                "    int r = a + b;\n" +
                "    r = a - b;\n" +
                "    r = a * b;\n" +
                "    r = a / b;\n" +
                "    r = a % b;\n" +
                "    r = a & b;\n" +
                "    r = a | b;\n" +
                "    r = a ^ b;\n" +
                "    r = a >> b;\n" +
                "    r = a << b;\n" +
                "    r = a >>> b;\n" +
                "    r = Math.floorDiv(a, b);\n" +
                "    double d = Math.pow(a, b);\n" +
                "    boolean c = a > b;\n" +
                "    c = a >= b;\n" +
                "    c = a < b;\n" +
                "    c = a <= b;\n" +
                "    c = a == b;\n" +
                "    c = a != b;\n" +
                "    c = p && q;\n" +
                "    c = p || q;\n" +
                "    return r;\n" +
                "  }\n" +
                "}\n");

        Files.writeString(dir.resolve("ops.js"),
                "function f(a, b, p, q) {\n" +
                "  let r = a + b;\n" +
                "  r = a - b;\n" +
                "  r = a * b;\n" +
                "  r = a / b;\n" +
                "  r = a % b;\n" +
                "  r = a ** b;\n" +
                "  r = a & b;\n" +
                "  r = a | b;\n" +
                "  r = a ^ b;\n" +
                "  r = a >> b;\n" +
                "  r = a << b;\n" +
                "  r = a >>> b;\n" +
                "  r = Math.floor(a / b);\n" +
                "  let c = a > b;\n" +
                "  c = a >= b;\n" +
                "  c = a < b;\n" +
                "  c = a <= b;\n" +
                "  c = a == b;\n" +
                "  c = a != b;\n" +
                "  c = a === b;\n" +
                "  c = a !== b;\n" +
                "  c = p && q;\n" +
                "  c = p || q;\n" +
                "  return r;\n" +
                "}\n");

        Files.writeString(dir.resolve("ops.py"),
                "def f(a, b, p, q):\n" +
                "    r = a + b\n" +
                "    r = a - b\n" +
                "    r = a * b\n" +
                "    r = a / b\n" +
                "    r = a % b\n" +
                "    r = a // b\n" +
                "    r = a ** b\n" +
                "    r = a & b\n" +
                "    r = a | b\n" +
                "    r = a ^ b\n" +
                "    r = a >> b\n" +
                "    r = a << b\n" +
                "    r = (a & 0xFFFFFFFF) >> b\n" +
                "    c = a > b\n" +
                "    c = a >= b\n" +
                "    c = a < b\n" +
                "    c = a <= b\n" +
                "    c = a == b\n" +
                "    c = a != b\n" +
                "    c = p and q\n" +
                "    c = p or q\n" +
                "    return r\n");

        Files.writeString(dir.resolve("Ops.cs"),
                "class Ops {\n" +
                "  int F(int a, int b, bool p, bool q) {\n" +
                "    int r = a + b;\n" +
                "    r = a - b;\n" +
                "    r = a * b;\n" +
                "    r = a / b;\n" +
                "    r = a % b;\n" +
                "    r = a & b;\n" +
                "    r = a | b;\n" +
                "    r = a ^ b;\n" +
                "    r = a >> b;\n" +
                "    r = a << b;\n" +
                "    r = a >>> b;\n" +
                "    r = (int)Math.Floor((double)a / b);\n" +
                "    double d = Math.Pow(a, b);\n" +
                "    bool c = a > b;\n" +
                "    c = a >= b;\n" +
                "    c = a < b;\n" +
                "    c = a <= b;\n" +
                "    c = a == b;\n" +
                "    c = a != b;\n" +
                "    c = p && q;\n" +
                "    c = p || q;\n" +
                "    return r;\n" +
                "  }\n" +
                "}\n");

        Files.writeString(dir.resolve("ops.rkt"),
                "#lang racket/base\n" +
                "(define (f a b p q)\n" +
                "  (list\n" +
                "   (+ a b)\n" +
                "   (- a b)\n" +
                "   (* a b)\n" +
                "   (/ a b)\n" +
                "   (modulo a b)\n" +
                "   (quotient a b)\n" +
                "   (floor (/ p q))\n" +
                "   (expt a b)\n" +
                "   (bitwise-and a b)\n" +
                "   (bitwise-ior a b)\n" +
                "   (bitwise-xor a b)\n" +
                "   (arithmetic-shift a (- b))\n" +
                "   (arithmetic-shift a b)\n" +
                "   (arithmetic-shift (bitwise-and a #xFFFFFFFF) (- b))\n" +
                "   (> a b)\n" +
                "   (>= a b)\n" +
                "   (< a b)\n" +
                "   (<= a b)\n" +
                "   (= a b)\n" +
                "   (not (= a b))\n" +
                "   (and p q)\n" +
                "   (or p q)))\n");
    }
}
