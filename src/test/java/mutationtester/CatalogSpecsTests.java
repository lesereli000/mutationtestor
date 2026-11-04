package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.msl.MutationRule;
import mutationtester.msl.MutationRuleGenerator;

/** Each operator group in specs/catalog fires in every language whose fixture has the construct. */
class CatalogSpecsTests {

    @TempDir
    Path dir;

    static final Map<String, String> FIXTURES = new LinkedHashMap<>();
    static {
        FIXTURES.put("A.java", """
                class A {
                  private int size = 10;
                  boolean ok(int a, int b, Shape s) {
                    int[] xs = {1, 2, 3};
                    int c = a + b * 2 - a % 3;
                    a++;
                    a += 2;
                    int m = -a << 1;
                    boolean f = !(a == b) && a < b || a > b & true;
                    log(a);
                    Point p = new Point(a, b);
                    int n = count(a, b);
                    int w = s.area(a);
                    switch (a) {
                      case 1: one(); break;
                      default: other();
                    }
                    for (int i = 0; i < n; i++) { if (i > 3) break; continue; }
                    Fn g = (x, y) -> x + y;
                    String t = "hi";
                    if (f) { go(); } else { stop(); }
                    if (a == 0) return false;
                    return true;
                  }
                  int seven() { return 7; }
                }
                """);
        FIXTURES.put("A.cs", """
                class A {
                  private int size = 10;
                  bool Ok(int a, int b = 5, Shape s = null) {
                    int[] xs = { 1, 2, 3 };
                    int c = a + b * 2 - a % 3;
                    a++;
                    a += 2;
                    int m = -a << 1;
                    bool f = !(a == b) && a < b || a > b & true;
                    Log(a);
                    var p = new Point(a, b);
                    int n = Count(a, b);
                    int w = s.Area(a);
                    var len = s?.Name;
                    var v = s ?? null;
                    switch (a) {
                      case 1: One(); break;
                      default: Other(); break;
                    }
                    for (int i = 0; i < n; i++) { if (i > 3) break; continue; }
                    var g = (int x, int y) => x + y;
                    string t = "hi";
                    if (f) { Go(); } else { Stop(); }
                    if (a == 0) return false;
                    return true;
                  }
                  int Seven() { return 7; }
                }
                """);
        FIXTURES.put("a.js", """
                class A {
                  size = 10;
                }
                function ok(a, b = 5, s) {
                  const xs = [1, 2, 3];
                  const o = { k: 1, j: 2 };
                  const c = a + b * 2 - a % 3;
                  a++;
                  a += 2;
                  const m = -a << 1;
                  const f = !(a == b) && a < b || a > b & true;
                  const strict = a === b;
                  log(a);
                  const p = new Point(a, b);
                  const n = count(a, b);
                  const w = s.area(a);
                  const len = s?.name;
                  const v = s ?? null;
                  const has = "k" in o;
                  switch (a) {
                    case 1: one(); break;
                    default: other();
                  }
                  for (let i = 0; i < n; i++) { if (i > 3) break; continue; }
                  const g = (x, y) => x + y;
                  const t = "hi";
                  if (f) { go(); } else { stop(); }
                  if (a == 0) return false;
                  return true;
                }
                function seven() { return 7; }
                """);
        FIXTURES.put("a.py", """
                class A:
                    size = 10

                @cache
                def ok(a, b=5, s=None):
                    xs = [1, 2, 3]
                    o = {"k": 1, "j": 2}
                    c = a + b * 2 - a % 3
                    a += 2
                    m = -a << 1
                    f = not (a == b) and a < b or a > b & True
                    log(a)
                    p = Point(a, b)
                    n = count(a, b)
                    w = s.area(a)
                    has = "k" in o
                    for i in range(n):
                        if i > 3:
                            break
                        continue
                    g = lambda x, y: x + y
                    t = "hi"
                    if f:
                        go()
                    else:
                        stop()
                    if a == 0:
                        return False
                    return True

                def seven():
                    return 7
                """);
        FIXTURES.put("a.rkt", """
                #lang racket
                (define-struct point (x y))
                (define (ok a b s)
                  (define c (- (+ a (* b 2)) (modulo a 3)))
                  (define f (or (and (not (= a b)) (< a b)) (> a b)))
                  (define g (lambda (x y) (+ x y)))
                  (define n (count a b))
                  (define xs (list 1 2 3))
                  (define t "hi")
                  (log a)
                  (if f (go) (stop))
                  (if (= a 0) #f (make-point a b)))
                (define (seven) 7)
                """);
    }

    /** Language per fixture file, for readable expectations. */
    private static String language(String file) {
        return switch (file.substring(file.lastIndexOf('.') + 1)) {
            case "java" -> "java";
            case "cs" -> "csharp";
            case "js" -> "javascript";
            case "py" -> "python";
            default -> "racket";
        };
    }

    /** Group label -> languages whose fixture got at least one mutant from it. */
    private Map<String, Set<String>> coverage(String spec) throws IOException {
        Map<String, Set<String>> byGroup = new TreeMap<>();
        for (MutationRule rule : MutationRuleGenerator.generateRules(Path.of("specs/catalog", spec).toFile())) {
            byGroup.putIfAbsent(group(rule.getLabel()), new TreeSet<>());
        }
        for (var fixture : FIXTURES.entrySet()) {
            Path src = Files.createDirectories(Files.createTempDirectory(dir, "catalog").resolve("src"));
            Files.writeString(src.resolve(fixture.getKey()), fixture.getValue());
            PrintStream out = System.out;
            List<MutationPoint> points;
            try {
                System.setOut(new PrintStream(OutputStream.nullOutputStream()));
                points = Main.scanForMutations(src, "specs/catalog/" + spec);
            } finally {
                System.setOut(out);
            }
            for (MutationPoint p : points) {
                byGroup.computeIfAbsent(group(p.getCustomLabel()), g -> new TreeSet<>()).add(language(fixture.getKey()));
            }
        }
        return byGroup;
    }

    private static String group(String label) {
        return label.substring(0, label.indexOf(':'));
    }

    private static Set<String> langs(String... languages) {
        return new TreeSet<>(List.of(languages));
    }

    private static final Set<String> ALL = langs("csharp", "java", "javascript", "python", "racket");

    // Missing languages lack the construct, or the fixture doesn't use it.
    private static final Set<String> NO_RACKET = langs("csharp", "java", "javascript", "python");
    private static final Set<String> C_LIKE = langs("csharp", "java", "javascript");

    @Test
    void pit() throws IOException {
        Map<String, Set<String>> expected = new TreeMap<>();
        for (String g : List.of("AOD", "CONDITIONALS_BOUNDARY", "CONSTRUCTOR_CALLS", "EMPTY_RETURNS",
                "EXPERIMENTAL_ARGUMENT_PROPAGATION", "INLINE_CONSTS", "MATH", "NEGATE_CONDITIONALS",
                "NON_VOID_METHOD_CALLS", "NULL_RETURNS", "REMOVE_CONDITIONALS", "VOID_METHOD_CALLS")) {
            expected.put(g, ALL);
        }
        for (String g : List.of("EXPERIMENTAL_MEMBER_VARIABLE", "EXPERIMENTAL_NAKED_RECEIVER", "FALSE_RETURNS",
                "INVERT_NEGS", "TRUE_RETURNS")) {
            expected.put(g, NO_RACKET);
        }
        expected.put("PRIMITIVE_RETURNS", ALL);
        for (String g : List.of("EXPERIMENTAL_SWITCH", "INCREMENTS")) expected.put(g, C_LIKE);
        assertEquals(expected, coverage("pit.spec"));
    }

    @Test
    void stryker() throws IOException {
        Map<String, Set<String>> expected = new TreeMap<>();
        for (String g : List.of("ArithmeticOperator", "ArrayDeclaration", "ArrowFunction", "BlockStatement",
                "BooleanLiteral", "ConditionalExpression", "EqualityOperator", "LogicalOperator", "StringLiteral")) {
            expected.put(g, ALL);
        }
        for (String g : List.of("AssignmentOperator", "UnaryOperator")) expected.put(g, NO_RACKET);
        expected.put("ObjectLiteral", langs("javascript", "python"));
        expected.put("OptionalChaining", langs("csharp", "javascript"));
        expected.put("UpdateOperator", C_LIKE);
        assertEquals(expected, coverage("stryker.spec"));
    }

    @Test
    void mutmut() throws IOException {
        Map<String, Set<String>> expected = new TreeMap<>();
        for (String g : List.of("Keywords", "Lambda to None", "Number literals", "Operator swaps", "String literals")) {
            expected.put(g, ALL);
        }
        expected.put("Assignment to None", langs("python"));
        expected.put("Decorators", langs("python"));
        expected.put("Default arguments", langs("csharp", "javascript", "python"));
        assertEquals(expected, coverage("mutmut.spec"));
    }

    @Test
    void classic() throws IOException {
        Map<String, Set<String>> expected = new TreeMap<>();
        for (String g : List.of("AOR (arithmetic operator replacement)", "COR (conditional operator replacement)",
                "ROR (relational operator replacement)", "SDL (statement deletion)", "UOI (unary operator insertion)")) {
            expected.put(g, ALL);
        }
        for (String g : List.of("ABS (absolute value insertion)", "ASR (assignment operator replacement)",
                "LCR (logical connector replacement)", "SOR (shift operator replacement)")) {
            expected.put(g, NO_RACKET);
        }
        assertEquals(expected, coverage("classic.spec"));
    }
}
