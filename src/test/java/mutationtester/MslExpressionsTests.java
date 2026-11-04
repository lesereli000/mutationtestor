package mutationtester;

import static mutationtester.SpecFixture.set;
import static mutationtester.SpecFixture.spec;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Phase 1: unary, conditional, compound assignment, indexing, member access, variadic calls, literal and type-name placeholders and their conversions, {condition} and {name}. Each test checks the exact mutants in all five languages. */
class MslExpressionsTests {

    @TempDir
    Path dir;

    static final Map<String, String> CODE = new LinkedHashMap<>();
    static {
        CODE.put("A.java", """
                class A {
                  double f(int[] a, int i, boolean ok, String s, Obj o) {
                    boolean n = !ok;
                    int m = -i;
                    int t = ok ? 1 : 2;
                    i += 3;
                    int e = a[i];
                    int l = o.size;
                    int g = o.get(1, 2);
                    run(i);
                    long big = 7L;
                    double d = 2.5;
                    int h = 0x1F;
                    char c = 'x';
                    String w = "hi";
                    if (ok) run();
                    return (double) i + o.count;
                  }
                }
                """);
        CODE.put("A.cs", """
                class A {
                  double F(int[] a, int i, bool ok, string s, Obj o) {
                    bool n = !ok;
                    int m = -i;
                    int t = ok ? 1 : 2;
                    i += 3;
                    int e = a[i];
                    int l = o.Size;
                    int g = o.Get(1, 2);
                    Run(i);
                    long big = 7L;
                    double d = 2.5;
                    int h = 0x1F;
                    char c = 'x';
                    string w = "hi";
                    if (ok) Run();
                    return (double) i + o.Count;
                  }
                }
                """);
        CODE.put("a.js", """
                function f(a, i, ok, s, o) {
                  let n = !ok;
                  let m = -i;
                  let t = ok ? 1 : 2;
                  i += 3;
                  let e = a[i];
                  let l = o.size;
                  let g = o.get(1, 2);
                  run(i);
                  let big = 7n;
                  let d = 2.5;
                  let h = 0x1F;
                  let w = "hi";
                  if (ok) run();
                  return i + o.count;
                }
                """);
        CODE.put("a.py", """
                def f(a, i: int, ok, s, o):
                    n = not ok
                    m = -i
                    t = 1 if ok else 2
                    i += 3
                    e = a[i]
                    l = o.size
                    g = o.get(1, 2)
                    run(i)
                    big = 7
                    d = 2.5
                    h = 0x1F
                    w = "hi"
                    b = b"x"
                    if ok:
                        run()
                    return float(i) + o.count
                """);
        CODE.put("a.rkt", """
                #lang racket
                (define (f a i ok s)
                  (list (not ok) (- i) (if ok 1 2) (list-ref a i) (run i) (add 1 2)
                        7 2.5 #x1F 1/2 "hi" #\\x))
                """);
    }

    private void check(String spec, Map<String, java.util.Set<String>> expected) throws IOException {
        for (var e : expected.entrySet()) {
            assertEquals(e.getValue(), SpecFixture.mutants(dir, spec, e.getKey(), CODE.get(e.getKey())), e.getKey());
        }
    }

    /** Replace: !{quantity} / With: {quantity} */
    @Test
    void rule01() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("3: !ok -> ok"));
        expected.put("A.cs", set("3: !ok -> ok"));
        expected.put("a.js", set("2: !ok -> ok"));
        expected.put("a.py", set("2: not ok -> ok"));
        expected.put("a.rkt", set("3: (not ok) -> ok"));
        check(spec("Replace: !{quantity}\nWith: {quantity}"), expected);
    }

    /** Replace: -{quantity} / With: {quantity} */
    @Test
    void rule02() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("4: -i -> i"));
        expected.put("A.cs", set("4: -i -> i"));
        expected.put("a.js", set("3: -i -> i"));
        expected.put("a.py", set("3: -i -> i"));
        expected.put("a.rkt", set("3: (- i) -> i"));
        check(spec("Replace: -{quantity}\nWith: {quantity}"), expected);
    }

    /** Replace: {quantity c} ? {quantity a} : {quantity b} / With: {quantity c} ? {quantity b} : {quantity a} */
    @Test
    void rule03() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("5: ok ? 1 : 2 -> (ok ? 2 : 1)"));
        expected.put("A.cs", set("5: ok ? 1 : 2 -> (ok ? 2 : 1)"));
        expected.put("a.js", set("4: ok ? 1 : 2 -> (ok ? 2 : 1)"));
        expected.put("a.py", set("4: 1 if ok else 2 -> (2 if ok else 1)"));
        expected.put("a.rkt", set("3: (if ok 1 2) -> (if ok 2 1)"));
        check(spec("Replace: {quantity c} ? {quantity a} : {quantity b}\nWith: {quantity c} ? {quantity b} : {quantity a}"), expected);
    }

    /** Replace: {quantity} += {quantity} / With: {quantity} -= {quantity} */
    @Test
    void rule04() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("6: i += 3 -> i -= 3"));
        expected.put("A.cs", set("6: i += 3 -> i -= 3"));
        expected.put("a.js", set("5: i += 3 -> i -= 3"));
        expected.put("a.py", set("5: i += 3 -> i -= 3"));
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity} += {quantity}\nWith: {quantity} -= {quantity}"), expected);
    }

    /** Replace: {quantity}[{quantity}] / With: {quantity}[0] */
    @Test
    void rule05() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("7: a[i] -> a[0]"));
        expected.put("A.cs", set("7: a[i] -> a[0]"));
        expected.put("a.js", set("6: a[i] -> a[0]"));
        expected.put("a.py", set("6: a[i] -> a[0]"));
        expected.put("a.rkt", set("3: (list-ref a i) -> (list-ref a 0)"));
        check(spec("Replace: {quantity}[{quantity}]\nWith: {quantity}[0]"), expected);
    }

    /** Replace: {quantity}.{name} / With: {quantity} */
    @Test
    void rule06() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("17: o.count -> o", "8: o.size -> o"));
        expected.put("A.cs", set("17: o.Count -> o", "8: o.Size -> o"));
        expected.put("a.js", set("15: o.count -> o", "7: o.size -> o"));
        expected.put("a.py", set("17: o.count -> o", "7: o.size -> o"));
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity}.{name}\nWith: {quantity}"), expected);
    }

    /** Replace: {quantity}.{name}({arguments}) / With: {quantity} */
    @Test
    void rule07() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("9: o.get(1, 2) -> o"));
        expected.put("A.cs", set("9: o.Get(1, 2) -> o"));
        expected.put("a.js", set("8: o.get(1, 2) -> o"));
        expected.put("a.py", set("8: o.get(1, 2) -> o"));
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity}.{name}({arguments})\nWith: {quantity}"), expected);
    }

    /** Replace: {name}({arguments}) / With: {name}() */
    @Test
    void rule08() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("10: run(i) -> run()"));
        expected.put("A.cs", set("10: Run(i) -> Run()"));
        expected.put("a.js", set("9: run(i) -> run()"));
        expected.put("a.py", set("17: float(i) -> float()", "9: run(i) -> run()"));
        expected.put("a.rkt", set("3: (add 1 2) -> (add)", "3: (list (not ok) (- i) (if ok 1 2) (list-ref a i) (run i) (add 1 2)\n        7 2.5 #x1F 1/2 \"hi\" #\\x) -> (list)", "3: (list-ref a i) -> (list-ref)", "3: (not ok) -> (not)", "3: (run i) -> (run)"));
        check(spec("Replace: {name}({arguments})\nWith: {name}()"), expected);
    }

    /** Replace: {integer} / With: {float} */
    @Test
    void rule09() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("11: long -> double", "13: int -> double", "2: int -> double", "4: int -> double", "5: int -> double", "6: 3 -> 3.0", "7: int -> double", "8: int -> double", "9: int -> double"));
        expected.put("A.cs", set("11: long -> double", "13: int -> double", "2: int -> double", "4: int -> double", "5: int -> double", "6: 3 -> 3.0", "7: int -> double", "8: int -> double", "9: int -> double"));
        expected.put("a.js", set("10: 7n -> 7.0", "12: 0x1F -> 31.0", "4: 1 -> 1.0", "4: 2 -> 2.0", "5: 3 -> 3.0", "8: 1 -> 1.0", "8: 2 -> 2.0"));
        expected.put("a.py", set("10: 7 -> 7.0", "12: 0x1F -> 31.0", "1: int -> float", "4: 1 -> 1.0", "4: 2 -> 2.0", "5: 3 -> 3.0", "8: 1 -> 1.0", "8: 2 -> 2.0"));
        expected.put("a.rkt", set("3: 1 -> 1.0", "3: 2 -> 2.0", "4: #x1F -> 31.0", "4: 7 -> 7.0"));
        check(spec("Replace: {integer}\nWith: {float}"), expected);
    }

    /** Replace: {float} / With: {integer} */
    @Test
    void rule10() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("12: 2.5 -> 2", "12: double -> long", "17: double -> long", "2: double -> long"));
        expected.put("A.cs", set("12: 2.5 -> 2", "12: double -> long", "17: double -> long", "2: double -> long"));
        expected.put("a.js", set("11: 2.5 -> 2"));
        expected.put("a.py", set("11: 2.5 -> 2", "17: float -> int"));
        expected.put("a.rkt", set("4: 2.5 -> 2"));
        check(spec("Replace: {float}\nWith: {integer}"), expected);
    }

    /** Replace: {integer: int64} / With: {integer: int32} */
    @Test
    void rule11() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("11: 7L -> 7", "11: long -> int"));
        expected.put("A.cs", set("11: 7L -> 7", "11: long -> int"));
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: {integer: int64}\nWith: {integer: int32}"), expected);
    }

    /** Replace: {based_integer} / With: {integer} */
    @Test
    void rule12() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("13: 0x1F -> 31"));
        expected.put("A.cs", set("13: 0x1F -> 31"));
        expected.put("a.js", set("12: 0x1F -> 31"));
        expected.put("a.py", set("12: 0x1F -> 31"));
        expected.put("a.rkt", set("4: #x1F -> 31"));
        check(spec("Replace: {based_integer}\nWith: {integer}"), expected);
    }

    /** Replace: {integer} / With: {exponential} */
    @Test
    void rule13() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("6: 3 -> 3.0e0"));
        expected.put("A.cs", set("6: 3 -> 3.0e0"));
        expected.put("a.js", set("10: 7n -> 7.0e0", "12: 0x1F -> 3.1e1", "4: 1 -> 1.0e0", "4: 2 -> 2.0e0", "5: 3 -> 3.0e0", "8: 1 -> 1.0e0", "8: 2 -> 2.0e0"));
        expected.put("a.py", set("10: 7 -> 7.0e0", "12: 0x1F -> 3.1e1", "4: 1 -> 1.0e0", "4: 2 -> 2.0e0", "5: 3 -> 3.0e0", "8: 1 -> 1.0e0", "8: 2 -> 2.0e0"));
        expected.put("a.rkt", set());
        check(spec("Replace: {integer}\nWith: {exponential}"), expected);
    }

    /** Replace: {rational} / With: {float} */
    @Test
    void rule14() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set("4: 1/2 -> 0.5"));
        check(spec("Replace: {rational}\nWith: {float}"), expected);
    }

    /** Replace: {char} / With: {string} */
    @Test
    void rule15() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("14: 'x' -> \"x\""));
        expected.put("A.cs", set("14: 'x' -> \"x\""));
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set("4: #\\x -> \"x\""));
        check(spec("Replace: {char}\nWith: {string}"), expected);
    }

    /** Replace: {string} / With: {empty_string} */
    @Test
    void rule16() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("15: \"hi\" -> \"\""));
        expected.put("A.cs", set("15: \"hi\" -> \"\""));
        expected.put("a.js", set("13: \"hi\" -> \"\""));
        expected.put("a.py", set("13: \"hi\" -> \"\""));
        expected.put("a.rkt", set("4: \"hi\" -> \"\""));
        check(spec("Replace: {string}\nWith: {empty_string}"), expected);
    }

    /** Replace: {bytes} / With: {string} */
    @Test
    void rule17() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set("14: b\"x\" -> \"x\""));
        expected.put("a.rkt", set());
        check(spec("Replace: {bytes}\nWith: {string}"), expected);
    }

    /** Replace: {condition} / With: true */
    @Test
    void rule18() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("16: ok -> true", "5: ok -> true"));
        expected.put("A.cs", set("16: ok -> true", "5: ok -> true"));
        expected.put("a.js", set("14: ok -> true", "4: ok -> true"));
        expected.put("a.py", set("15: ok -> True", "4: ok -> True"));
        expected.put("a.rkt", set("3: ok -> #t"));
        check(spec("Replace: {condition}\nWith: true"), expected);
    }

    /** Replace: {quantity} + {name} / With: {quantity} */
    @Test
    void rule19() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity} + {name}\nWith: {quantity}"), expected);
    }

}
