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

/** Phase 2: if/else, return, break/continue, declarations, for-each, do-while, cases, throw, try, blocks and {contains:} exceptions. Each test checks the exact mutants in all five languages. */
class MslStatementsTests {

    @TempDir
    Path dir;

    static final Map<String, String> CODE = new LinkedHashMap<>();
    static {
        CODE.put("A.java", """
                class A {
                  int f(int[] xs, boolean ok, int n) {
                    if (ok) run(); else stop();
                    if (n > 0) go();
                    for (int x : xs) use(x);
                    do { n--; } while (n > 0);
                    n--;
                    switch (n) {
                      case 1: one(); break;
                      default: other();
                    }
                    try { risky(); } catch (Exception e) { log(e); } finally { done(); }
                    int total = 0;
                    while (ok) { if (n > 3) break; continue; }
                    if (n < 0) throw new IllegalStateException();
                    return total;
                  }
                }
                """);
        CODE.put("A.cs", """
                class A {
                  int F(int[] xs, bool ok, int n) {
                    if (ok) Run(); else Stop();
                    if (n > 0) Go();
                    foreach (var x in xs) Use(x);
                    do { n--; } while (n > 0);
                    n--;
                    switch (n) {
                      case 1: One(); break;
                      default: Other(); break;
                    }
                    try { Risky(); } catch (Exception e) { Log(e); } finally { Done(); }
                    int total = 0;
                    while (ok) { if (n > 3) break; continue; }
                    if (n < 0) throw new InvalidOperationException();
                    return total;
                  }
                }
                """);
        CODE.put("a.js", """
                function f(xs, ok, n) {
                  if (ok) run(); else stop();
                  if (n > 0) go();
                  for (const x of xs) use(x);
                  do { n--; } while (n > 0);
                  n--;
                  switch (n) {
                    case 1: one(); break;
                    default: other();
                  }
                  try { risky(); } catch (e) { log(e); } finally { done(); }
                  let total = 0;
                  while (ok) { if (n > 3) break; continue; }
                  if (n < 0) throw new Error();
                  return total;
                }
                """);
        CODE.put("a.py", """
                def f(xs, ok, n):
                    if ok:
                        run()
                    else:
                        stop()
                    if n > 0:
                        go()
                    for x in xs:
                        use(x)
                    try:
                        risky()
                    except Exception as e:
                        log(e)
                    finally:
                        done()
                    total = 0
                    while ok:
                        if n > 3:
                            break
                        continue
                    if n < 0:
                        raise ValueError()
                    return total
                """);
        CODE.put("a.rkt", """
                #lang racket
                (define (f xs ok n)
                  (if ok (run) (stop))
                  (when (> n 0) (go))
                  (for ((x xs)) (use x))
                  (with-handlers ((exn? log)) (risky))
                  (define total 0)
                  (if (< n 0) (raise 'bad) total))
                """);
    }

    private void check(String spec, Map<String, java.util.Set<String>> expected) throws IOException {
        for (var e : expected.entrySet()) {
            assertEquals(e.getValue(), SpecFixture.mutants(dir, spec, e.getKey(), CODE.get(e.getKey())), e.getKey());
        }
    }

    /** Replace: if ({quantity}) {statement a} else {statement b} / With: {statement b} */
    @Test
    void rule01() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("3: if (ok) run(); else stop(); -> stop();"));
        expected.put("A.cs", set("3: if (ok) Run(); else Stop(); -> Stop();"));
        expected.put("a.js", set("2: if (ok) run(); else stop(); -> stop();"));
        expected.put("a.py", set("2: if ok:\n        run()\n    else:\n        stop()\n     -> stop()\n    "));
        expected.put("a.rkt", set("3: (if ok (run) (stop)) -> (stop)", "8: (if (< n 0) (raise 'bad) total) -> total"));
        check(spec("Replace: if ({quantity}) {statement a} else {statement b}\nWith: {statement b}"), expected);
    }

    /** Replace: if ({quantity}) {statement} / With: {statement} */
    @Test
    void rule02() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("14: if (n > 3) break; -> break;", "15: if (n < 0) throw new IllegalStateException(); -> throw new IllegalStateException();", "4: if (n > 0) go(); -> go();"));
        expected.put("A.cs", set("14: if (n > 3) break; -> break;", "15: if (n < 0) throw new InvalidOperationException(); -> throw new InvalidOperationException();", "4: if (n > 0) Go(); -> Go();"));
        expected.put("a.js", set("13: if (n > 3) break; -> break;", "14: if (n < 0) throw new Error(); -> throw new Error();", "3: if (n > 0) go(); -> go();"));
        expected.put("a.py", set("18: if n > 3:\n            break\n         -> break\n        ", "21: if n < 0:\n        raise ValueError()\n     -> raise ValueError()\n    ", "6: if n > 0:\n        go()\n     -> go()\n    "));
        expected.put("a.rkt", set("4: (when (> n 0) (go)) -> (go)"));
        check(spec("Replace: if ({quantity}) {statement}\nWith: {statement}"), expected);
    }

    /** Replace: return {quantity} / With: return 0 */
    @Test
    void rule03() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("16: return total; -> return 0;"));
        expected.put("A.cs", set("16: return total; -> return 0;"));
        expected.put("a.js", set("15: return total; -> return 0;"));
        expected.put("a.py", set("23: return total -> return 0"));
        expected.put("a.rkt", set("8: (if (< n 0) (raise 'bad) total) -> 0"));
        check(spec("Replace: return {quantity}\nWith: return 0"), expected);
    }

    /** Replace: break / With: continue */
    @Test
    void rule04() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("14: break; -> continue;", "9: break; -> continue;"));
        expected.put("A.cs", set("10: break; -> continue;", "14: break; -> continue;", "9: break; -> continue;"));
        expected.put("a.js", set("13: break; -> continue;", "8: break; -> continue;"));
        expected.put("a.py", set("19: break -> continue"));
        expected.put("a.rkt", set());
        check(spec("Replace: break\nWith: continue"), expected);
    }

    /** Delete: continue */
    @Test
    void rule05() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("14: continue; -> ;"));
        expected.put("A.cs", set("14: continue; -> ;"));
        expected.put("a.js", set("13: continue; -> ;"));
        expected.put("a.py", set("20: continue -> pass"));
        expected.put("a.rkt", set());
        check(spec("Delete: continue"), expected);
    }

    /** Replace: var {name} = {quantity} / With: var {name} = 1 */
    @Test
    void rule06() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("13: int total = 0 -> int total = 1"));
        expected.put("A.cs", set("13: int total = 0 -> int total = 1"));
        expected.put("a.js", set("12: let total = 0 -> let total = 1"));
        expected.put("a.py", set("16: total = 0 -> total = 1"));
        expected.put("a.rkt", set("7: (define total 0) -> (define total 1)"));
        check(spec("Replace: var {name} = {quantity}\nWith: var {name} = 1"), expected);
    }

    /** Replace: for ({name} in {quantity}) {statement} / With: ; */
    @Test
    void rule07() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("5: for (int x : xs) use(x); -> ;"));
        expected.put("A.cs", set("5: foreach (var x in xs) Use(x); -> ;"));
        expected.put("a.js", set("4: for (const x of xs) use(x); -> ;"));
        expected.put("a.py", set("8: for x in xs:\n        use(x)\n     -> pass\n    "));
        expected.put("a.rkt", set("5: (for ((x xs)) (use x)) -> (void)"));
        check(spec("Replace: for ({name} in {quantity}) {statement}\nWith: ;"), expected);
    }

    /** Replace: do {statement} while ({quantity}) / With: {statement} */
    @Test
    void rule08() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("6: do { n--; } while (n > 0); -> { n--; }"));
        expected.put("A.cs", set("6: do { n--; } while (n > 0); -> { n--; }"));
        expected.put("a.js", set("5: do { n--; } while (n > 0); -> { n--; }"));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: do {statement} while ({quantity})\nWith: {statement}"), expected);
    }

    /** Delete: case {quantity}: {statement} */
    @Test
    void rule09() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("9: case 1: one(); break; -> ;"));
        expected.put("A.cs", set("9: case 1: One(); break; -> ;"));
        expected.put("a.js", set("8: case 1: one(); break; -> ;"));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Delete: case {quantity}: {statement}"), expected);
    }

    /** Delete: throw {quantity} */
    @Test
    void rule10() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("15: throw new IllegalStateException(); -> ;"));
        expected.put("A.cs", set("15: throw new InvalidOperationException(); -> ;"));
        expected.put("a.js", set("14: throw new Error(); -> ;"));
        expected.put("a.py", set("22: raise ValueError() -> pass"));
        expected.put("a.rkt", set("8: (raise 'bad) -> (void)"));
        check(spec("Delete: throw {quantity}"), expected);
    }

    /** Replace: try {block a} catch {block b} finally {block c} / With: {block a} */
    @Test
    void rule11() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("12: try { risky(); } catch (Exception e) { log(e); } finally { done(); } -> { risky(); }"));
        expected.put("A.cs", set("12: try { Risky(); } catch (Exception e) { Log(e); } finally { Done(); } -> { Risky(); }"));
        expected.put("a.js", set("11: try { risky(); } catch (e) { log(e); } finally { done(); } -> { risky(); }"));
        expected.put("a.py", set("10: try:\n        risky()\n    except Exception as e:\n        log(e)\n    finally:\n        done()\n     -> risky()\n    "));
        expected.put("a.rkt", set());
        check(spec("Replace: try {block a} catch {block b} finally {block c}\nWith: {block a}"), expected);
    }

    /** Replace: while ({quantity}) {block} / With: while ({quantity}) {empty_block} */
    @Test
    void rule12() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("14: while (ok) { if (n > 3) break; continue; } -> while (ok) {}"));
        expected.put("A.cs", set("14: while (ok) { if (n > 3) break; continue; } -> while (ok) {}"));
        expected.put("a.js", set("13: while (ok) { if (n > 3) break; continue; } -> while (ok) {}"));
        expected.put("a.py", set("17: while ok:\n        if n > 3:\n            break\n        continue\n     -> while ok:\n        pass\n    "));
        expected.put("a.rkt", set());
        check(spec("Replace: while ({quantity}) {block}\nWith: while ({quantity}) {empty_block}"), expected);
    }

    /** Replace: {quantity}-- / With: {quantity}++ / Except: do {contains: {match}} while ({quantity}) */
    @Test
    void rule13() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("7: n-- -> n++"));
        expected.put("A.cs", set("7: n-- -> n++"));
        expected.put("a.js", set("6: n-- -> n++"));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity}--\nWith: {quantity}++\nExcept: do {contains: {match}} while ({quantity})"), expected);
    }

}
