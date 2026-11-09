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

/** Phase 3: collection and object literals, lambdas, functions and parameters, type checks, casts, null-safe access, fields and initialized new. Each test checks the exact mutants in all five languages. */
class MslCollectionsFunctionsTypesTests {

    @TempDir
    Path dir;

    static final Map<String, String> CODE = new LinkedHashMap<>();
    static {
        CODE.put("A.java", """
                class A {
                  private int size = 10;
                  int f(Object o, int n) {
                    int[] xs = {1, 2, 3};
                    Fn g = (a, b) -> a + b;
                    boolean s = o instanceof String;
                    long w = (long) n;
                    Runnable r = new Thread() { public void run() { go(); } };
                    return xs[0];
                  }
                }
                """);
        CODE.put("A.cs", """
                class A {
                  private int size = 10;
                  int F(object o, int n = 5) {
                    int[] xs = { 1, 2, 3 };
                    var g = (int a, int b) => a + b;
                    bool s = o is string;
                    long w = (long) n;
                    var p = new Point { X = 1, Y = 2 };
                    var len = o?.Length;
                    var v = o ?? "none";
                    return xs[0];
                  }
                }
                """);
        CODE.put("a.js", """
                class A {
                  size = 10;
                }
                function f(o, n = 5) {
                  const xs = [1, 2, 3];
                  const m = { a: 1, b: 2 };
                  const g = (a, b) => a + b;
                  const s = o instanceof String;
                  const len = o?.length;
                  const v = o ?? "none";
                  return xs[0];
                }
                """);
        CODE.put("a.py", """
                class A:
                    size = 10

                def f(o, n=5):
                    xs = [1, 2, 3]
                    m = {"a": 1, "b": 2}
                    g = lambda a, b: a + b
                    s = isinstance(o, str)
                    return xs[0]
                """);
        CODE.put("a.rkt", """
                #lang racket
                (define (f o n)
                  (list (list 1 2 3) (hash 'a 1 'b 2) (lambda (a b) (+ a b)) (number? o)))
                """);
    }

    private void check(String spec, Map<String, java.util.Set<String>> expected) throws IOException {
        for (var e : expected.entrySet()) {
            assertEquals(e.getValue(), SpecFixture.mutants(dir, spec, e.getKey(), CODE.get(e.getKey())), e.getKey());
        }
    }

    /** Replace: [{arguments}] / With: {empty_collection} */
    @Test
    void rule01() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("4: {1, 2, 3} -> {}"));
        expected.put("A.cs", set("4: { 1, 2, 3 } -> { }"));
        expected.put("a.js", set("5: [1, 2, 3] -> []"));
        expected.put("a.py", set("5: [1, 2, 3] -> []"));
        expected.put("a.rkt", set("3: (list (list 1 2 3) (hash 'a 1 'b 2) (lambda (a b) (+ a b)) (number? o)) -> '()", "3: (list 1 2 3) -> '()"));
        check(spec("Replace: [{arguments}]\nWith: {empty_collection}"), expected);
    }

    /** Replace: { {arguments} } / With: {empty_collection} */
    @Test
    void rule02() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set("6: { a: 1, b: 2 } -> {}"));
        expected.put("a.py", set("6: {\"a\": 1, \"b\": 2} -> {}"));
        expected.put("a.rkt", set("3: (hash 'a 1 'b 2) -> (hash)"));
        check(spec("Replace: { {arguments} }\nWith: {empty_collection}"), expected);
    }

    /** Replace: { {quantity k}: {quantity v}, {arguments} } / With: { {arguments} } */
    @Test
    void rule03() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set());
        expected.put("a.js", set("6: { a: 1, b: 2 } -> { b: 2 }"));
        expected.put("a.py", set("6: {\"a\": 1, \"b\": 2} -> {\"b\": 2}"));
        expected.put("a.rkt", set("3: (hash 'a 1 'b 2) -> (hash 'b 2)"));
        check(spec("Replace: { {quantity k}: {quantity v}, {arguments} }\nWith: { {arguments} }"), expected);
    }

    /** Replace: ({arguments}) => {quantity} / With: ({arguments}) => null */
    @Test
    void rule04() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("5: (a, b) -> a + b -> (a, b) -> null"));
        expected.put("A.cs", set("5: (int a, int b) => a + b -> (int a, int b) => null"));
        expected.put("a.js", set("7: (a, b) => a + b -> (a, b) => null"));
        expected.put("a.py", set("7: lambda a, b: a + b -> lambda a, b: None"));
        expected.put("a.rkt", set("3: (lambda (a b) (+ a b)) -> (lambda (a b) '())"));
        check(spec("Replace: ({arguments}) => {quantity}\nWith: ({arguments}) => null"), expected);
    }

    /** Replace: {lambda} / With: null */
    @Test
    void rule05() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("5: (a, b) -> a + b -> null"));
        expected.put("A.cs", set("5: (int a, int b) => a + b -> null"));
        expected.put("a.js", set("7: (a, b) => a + b -> null"));
        expected.put("a.py", set("7: lambda a, b: a + b -> None"));
        expected.put("a.rkt", set("3: (lambda (a b) (+ a b)) -> '()"));
        check(spec("Replace: {lambda}\nWith: null"), expected);
    }

    /** Replace: parameter {name} = {quantity} / With: parameter {name} */
    @Test
    void rule06() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set("3: int n = 5 -> int n"));
        expected.put("a.js", set("4: n = 5 -> n"));
        expected.put("a.py", set("4: n=5 -> n"));
        expected.put("a.rkt", set());
        check(spec("Replace: parameter {name} = {quantity}\nWith: parameter {name}"), expected);
    }

    /** Replace: function {name}({arguments}) {block} / With: ; */
    @Test
    void rule07() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("3: int f(Object o, int n) {\n    int[] xs = {1, 2, 3};\n    Fn g = (a, b) -> a + b;\n    boolean s = o instanceof String;\n    long w = (long) n;\n    Runnable r = new Thread() { public void run() { go(); } };\n    return xs[0];\n  } -> ;", "8: void run() { go(); } -> ;"));
        expected.put("A.cs", set("3: int F(object o, int n = 5) {\n    int[] xs = { 1, 2, 3 };\n    var g = (int a, int b) => a + b;\n    bool s = o is string;\n    long w = (long) n;\n    var p = new Point { X = 1, Y = 2 };\n    var len = o?.Length;\n    var v = o ?? \"none\";\n    return xs[0];\n  } -> ;"));
        expected.put("a.js", set("4: function f(o, n = 5) {\n  const xs = [1, 2, 3];\n  const m = { a: 1, b: 2 };\n  const g = (a, b) => a + b;\n  const s = o instanceof String;\n  const len = o?.length;\n  const v = o ?? \"none\";\n  return xs[0];\n} -> ;"));
        expected.put("a.py", set("4: def f(o, n=5):\n    xs = [1, 2, 3]\n    m = {\"a\": 1, \"b\": 2}\n    g = lambda a, b: a + b\n    s = isinstance(o, str)\n    return xs[0]\n -> pass\n"));
        expected.put("a.rkt", set("2: (define (f o n)\n  (list (list 1 2 3) (hash 'a 1 'b 2) (lambda (a b) (+ a b)) (number? o))) -> (void)"));
        check(spec("Replace: function {name}({arguments}) {block}\nWith: ;"), expected);
    }

    /** Replace: {quantity} is a {type} / With: true */
    @Test
    void rule08() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("6: o instanceof String -> true"));
        expected.put("A.cs", set("6: o is string -> true"));
        expected.put("a.js", set("8: o instanceof String -> true"));
        expected.put("a.py", set("8: isinstance(o, str) -> True"));
        expected.put("a.rkt", set("3: (number? o) -> #t"));
        check(spec("Replace: {quantity} is a {type}\nWith: true"), expected);
    }

    /** Replace: ({type}) {quantity} / With: {quantity} */
    @Test
    void rule09() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("7: (long) n -> n"));
        expected.put("A.cs", set("7: (long) n -> n"));
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: ({type}) {quantity}\nWith: {quantity}"), expected);
    }

    /** Replace: {quantity}?.{name} / With: {quantity}.{name} */
    @Test
    void rule10() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set("9: o?.Length -> o.Length"));
        expected.put("a.js", set("9: o?.length -> o.length"));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity}?.{name}\nWith: {quantity}.{name}"), expected);
    }

    /** Replace: {quantity a} ?? {quantity b} / With: {quantity a} */
    @Test
    void rule11() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set());
        expected.put("A.cs", set("10: o ?? \"none\" -> o"));
        expected.put("a.js", set("10: o ?? \"none\" -> o"));
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: {quantity a} ?? {quantity b}\nWith: {quantity a}"), expected);
    }

    /** Replace: field {name} = {quantity} / With: field {name} */
    @Test
    void rule12() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("2: int size = 10; -> int size;"));
        expected.put("A.cs", set("2: private int size = 10; -> private int size;"));
        expected.put("a.js", set("2: size = 10 -> size"));
        expected.put("a.py", set("2: size = 10 -> size"));
        expected.put("a.rkt", set());
        check(spec("Replace: field {name} = {quantity}\nWith: field {name}"), expected);
    }

    /** Replace: new {type} { {arguments} } / With: new {type} { } */
    @Test
    void rule13() throws IOException {
        Map<String, java.util.Set<String>> expected = new LinkedHashMap<>();
        expected.put("A.java", set("8: new Thread() { public void run() { go(); } } -> new Thread()"));
        expected.put("A.cs", set("8: new Point { X = 1, Y = 2 } -> new Point()"));
        expected.put("a.js", set());
        expected.put("a.py", set());
        expected.put("a.rkt", set());
        check(spec("Replace: new {type} { {arguments} }\nWith: new {type} { }"), expected);
    }

}
