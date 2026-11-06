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

/** Phase 4: assert, resource and lock blocks, yield, labels, membership, slices, interpolation, destructuring, spread, switch arms, delete and annotations. Each test checks the exact mutants in all five languages. */
class MslRemainingStructuresTests {

    @TempDir
    Path dir;

    static final Map<String, String> CODE = new LinkedHashMap<>();
    static {
        CODE.put("A.java", """
                class A {
                  @Override
                  public int f(int n, Object lock) {
                    assert n > 0;
                    synchronized (lock) { go(); }
                    outer: for (int i = 0; i < n; i++) { break outer; }
                    int k = switch (n) {
                      case 1 -> 10;
                      default -> { yield 20; }
                    };
                    return k;
                  }
                }
                """);
        CODE.put("A.cs", """
                class A {
                  [Obsolete]
                  public IEnumerable<int> F(int[] a, int n, object o) {
                    lock (o) { Go(); }
                    var t = (n, 2);
                    var s = a[1..3];
                    var all = [..a, 4];
                    var msg = $"n is {n}";
                    var k = n switch { 1 => 10, _ => 20 };
                    yield return k;
                  }
                }
                """);
        CODE.put("a.js", """
                function* f(o, xs, n) {
                  outer: for (const x of xs) { break outer; }
                  const has = "k" in o;
                  const [a, b] = [n, 2];
                  const all = [...xs, 4];
                  delete o.k;
                  const msg = `n is ${n}`;
                  yield n;
                }
                """);
        CODE.put("a.py", """
                @cache
                def f(o, xs, n):
                    assert n > 0
                    with open(o) as h:
                        go()
                    has = "k" in o
                    gone = "k" not in o
                    a, b = n, 2
                    s = xs[1:3]
                    all = [*xs, 4]
                    del o["k"]
                    yield n
                """);
        CODE.put("a.rkt", """
                #lang racket
                (define (f v)
                  (match v (1 "one") (_ "other")))
                """);
    }

    private void check(String spec, Map<String, java.util.Set<String>> expected) throws IOException {
        for (var e : expected.entrySet()) {
            assertEquals(e.getValue(), SpecFixture.mutants(dir, spec, e.getKey(), CODE.get(e.getKey())), e.getKey());
        }
    }

    /** Delete: assert {quantity} */
    @Test
    void rule01() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("4: assert n > 0; -> ;"));
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set("3: assert n > 0 -> pass"));
        expected.put("a.rkt", set());
        check(spec("Delete: assert {quantity}"), expected);
    }

    /** Replace: with ({quantity}) {statement} / With: {statement} */
    @Test
    void rule02() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("5: synchronized (lock) { go(); } -> { go(); }"));
        expected.put("A.cs", set("4: lock (o) { Go(); } -> { Go(); }"));
        expected.put("a.js", set());
        expected.put("a.py", set("4: with open(o) as h:\n        go()\n     -> go()\n    "));
        expected.put("a.rkt", set());
        check(spec("Replace: with ({quantity}) {statement}\nWith: {statement}"), expected);
    }

    /** Replace: yield {quantity} / With: yield 0 */
    @Test
    void rule03() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("9: yield 20; -> yield 0;"));
        expected.put("A.cs", set("10: yield return k; -> yield return 0;"));
        expected.put("a.js", set("8: yield n; -> yield 0;"));
        expected.put("a.py", set("12: yield n -> yield 0"));
        expected.put("a.rkt", set());
        check(spec("Replace: yield {quantity}\nWith: yield 0"), expected);
    }

    /** Replace: {name}: {statement} / With: {statement} */
    @Test
    void rule04() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("6: outer: for (int i = 0; i < n; i++) { break outer; } -> for (int i = 0; i < n; i++) { break outer; }"));
        expected.put("A.cs", set());
        expected.put("a.js", set("2: outer: for (const x of xs) { break outer; } -> for (const x of xs) { break outer; }"));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: {name}: {statement}\nWith: {statement}"), expected);
    }

    /** Replace: {quantity} in {quantity} / With: {quantity} not in {quantity} */
    @Test
    void rule05() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set("3: \"k\" in o -> !(\"k\" in o)"));
        expected.put("a.py", set("6: \"k\" in o -> \"k\" not in o"));
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity} in {quantity}\nWith: {quantity} not in {quantity}"), expected);
    }

    /** Replace: {quantity}[{quantity a}:{quantity b}] / With: {quantity}[{quantity a}:] */
    @Test
    void rule06() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set("6: a[1..3] -> a[1..]"));
        expected.put("a.js", set());
        expected.put("a.py", set("9: xs[1:3] -> xs[1:]"));
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity}[{quantity a}:{quantity b}]\nWith: {quantity}[{quantity a}:]"), expected);
    }

    /** Replace: `${{quantity}}` / With: `${\"\"}` */
    @Test
    void rule07() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set("8: $\"n is {n}\" -> $\"n is {\"\"}\""));
        expected.put("a.js", set("7: `n is ${n}` -> `n is ${\"\"}`"));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: `${{quantity}}`\nWith: `${\"\"}`"), expected);
    }

    /** Replace: ({quantity a}, {quantity b}) = {quantity} / With: ({quantity b}, {quantity a}) = {quantity} */
    @Test
    void rule08() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set("8: a, b = n, 2 -> b, a = n, 2"));
        expected.put("a.rkt", set());
        check(spec("Replace: ({quantity a}, {quantity b}) = {quantity}\nWith: ({quantity b}, {quantity a}) = {quantity}"), expected);
    }

    /** Replace: ...{quantity} / With: {quantity} */
    @Test
    void rule09() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set("7: ..a -> a"));
        expected.put("a.js", set("5: ...xs -> xs"));
        expected.put("a.py", set("10: *xs -> xs"));
        expected.put("a.rkt", set());
        check(spec("Replace: ...{quantity}\nWith: {quantity}"), expected);
    }

    /** Delete: case {quantity} => {quantity} */
    @Test
    void rule10() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("8: case 1 -> 10; -> "));
        expected.put("A.cs", set("9: 1 => 10, -> ", "9: _ => 20 -> "));
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set("3: (1 \"one\") -> ", "3: (_ \"other\") -> "));
        check(spec("Delete: case {quantity} => {quantity}"), expected);
    }

    /** Delete: delete {quantity} */
    @Test
    void rule11() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set("6: delete o.k -> ;"));
        expected.put("a.py", set("11: del o[\"k\"] -> pass"));
        expected.put("a.rkt", set());
        check(spec("Delete: delete {quantity}"), expected);
    }

    /** Delete: @{name} */
    @Test
    void rule12() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("2: @Override -> "));
        expected.put("A.cs", set("2: [Obsolete] -> "));
        expected.put("a.js", set());
        expected.put("a.py", set("1: @cache\n -> "));
        expected.put("a.rkt", set());
        check(spec("Delete: @{name}"), expected);
    }

}
