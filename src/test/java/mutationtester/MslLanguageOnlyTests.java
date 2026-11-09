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

/** Phase 5: strict equality, comprehensions, await, when/unless, let bindings, LINQ where, loop else and chained comparisons. Each test checks the exact mutants in all five languages. */
class MslLanguageOnlyTests {

    @TempDir
    Path dir;

    static final Map<String, String> CODE = new LinkedHashMap<>();
    static {
        CODE.put("A.cs", """
                class A {
                  async Task<int> F(int[] xs) {
                    var big = from x in xs where x > 3 select x;
                    var n = await Load();
                    return n;
                  }
                }
                """);
        CODE.put("a.js", """
                async function f(x) {
                  const z = x === 0;
                  const y = await load(x);
                  return y;
                }
                """);
        CODE.put("a.py", """
                async def f(xs, n):
                    pos = [x for x in xs if x > 0]
                    data = await load(n)
                    for x in xs:
                        go(x)
                    else:
                        done()
                    ok = 0 < n < 10
                    return data
                """);
        CODE.put("a.rkt", """
                #lang racket
                (define (f ok n)
                  (when ok (save))
                  (let ([a 1] [b 2]) (+ a b n)))
                """);
    }

    private void check(String spec, Map<String, java.util.Set<String>> expected) throws IOException {
        for (var e : expected.entrySet()) {
            assertEquals(e.getValue(), SpecFixture.mutants(dir, spec, e.getKey(), CODE.get(e.getKey())), e.getKey());
        }
    }

    /** Replace: {quantity} === {quantity} / With: {quantity} == {quantity} */
    @Test
    void rule01() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set());
        expected.put("a.js", set("2: === -> =="));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity} === {quantity}\nWith: {quantity} == {quantity}"), expected);
    }

    /** Replace: [{quantity} for {name} in {quantity} if {condition}] / With: [{quantity} for {name} in {quantity}] */
    @Test
    void rule02() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set("2: [x for x in xs if x > 0] -> [x for x in xs]"));
        expected.put("a.rkt", set());
        check(spec("Replace: [{quantity} for {name} in {quantity} if {condition}]\nWith: [{quantity} for {name} in {quantity}]"), expected);
    }

    /** Replace: await {quantity} / With: {quantity} */
    @Test
    void rule03() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set("4: await Load() -> Load()"));
        expected.put("a.js", set("3: await load(x) -> load(x)"));
        expected.put("a.py", set("3: await load(n) -> load(n)"));
        expected.put("a.rkt", set());
        check(spec("Replace: await {quantity}\nWith: {quantity}"), expected);
    }

    /** Replace: when ({condition}) {statement} / With: unless ({condition}) {statement} */
    @Test
    void rule04() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set("3: (when ok (save)) -> (unless ok (save))"));
        check(spec("Replace: when ({condition}) {statement}\nWith: unless ({condition}) {statement}"), expected);
    }

    /** Replace: var {name} = {quantity} / With: var {name} = 0 */
    @Test
    void rule05() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set("3: var big = from x in xs where x > 3 select x -> var big = 0", "4: var n = await Load() -> var n = 0"));
        expected.put("a.js", set("2: const z = x === 0 -> const z = 0", "3: const y = await load(x) -> const y = 0"));
        expected.put("a.py", set("2: pos = [x for x in xs if x > 0] -> pos = 0", "3: data = await load(n) -> data = 0", "8: ok = 0 < n < 10 -> ok = 0"));
        expected.put("a.rkt", set("4: 1 -> 0", "4: 2 -> 0"));
        check(spec("Replace: var {name} = {quantity}\nWith: var {name} = 0"), expected);
    }

    /** Delete: where {condition} */
    @Test
    void rule06() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set("3: where x > 3 -> "));
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Delete: where {condition}"), expected);
    }

    /** Replace: for ({name} in {quantity}) {statement a} else {statement b} / With: for ({name} in {quantity}) {statement a} */
    @Test
    void rule07() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set("4: for x in xs:\n        go(x)\n    else:\n        done()\n     -> for x in xs:\n        go(x)\n    "));
        expected.put("a.rkt", set());
        check(spec("Replace: for ({name} in {quantity}) {statement a} else {statement b}\nWith: for ({name} in {quantity}) {statement a}"), expected);
    }

    /** Replace: {quantity} < {quantity} / With: {quantity} <= {quantity} */
    @Test
    void rule08() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.cs", set());
        expected.put("a.js", set());
        expected.put("a.py", set("8: < -> <="));
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity} < {quantity}\nWith: {quantity} <= {quantity}"), expected);
    }

}
