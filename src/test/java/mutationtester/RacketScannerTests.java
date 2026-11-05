package mutationtester;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.RacketMutationScanner;

/**
 * Tests for SchemeMutationScanner (Racket/.rkt files).
 * Uses:
 *   samples/SchemeProject/project/src/add1.rkt      → (+ 1 x)
 *   samples/SchemeProject/project/src/add2.rkt      → (+ 2 x)
 *   samples/SchemeProject/project/src/operators.rkt → covers all mutation types
 */
public class RacketScannerTests {

    // -------------------------------------------------------------------------
    // AOR_PLUS_TO_MINUS
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // AOR_MINUS_TO_PLUS
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // ROR_GT_TO_GE
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // ROR_GE_TO_GT
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
// ROR_LT_TO_LE
// -------------------------------------------------------------------------

// -------------------------------------------------------------------------
// ROR_LE_TO_LT
// -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // EQ_NEGATE
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // LOGICAL_AND_TO_OR
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // LOGICAL_OR_TO_AND
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // NLT_INTEGER_TO_FLOAT
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // NLT_FLOAT_TO_INTEGER
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Verify strings and comments are never scanned
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // TYPE_INT_TO_FLOAT
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // TYPE_FLOAT_TO_INT
    // -------------------------------------------------------------------------


    // -------------------------------------------------------------------------
// AOR_MULTIPLY_TO_DIVIDE
// -------------------------------------------------------------------------

// -------------------------------------------------------------------------
// AOR_DIVIDE_TO_MULTIPLY
// -------------------------------------------------------------------------



    // -------------------------------------------------------------------------
    // NEGATE_CONDITIONALS: (if cond ...) → (if (not cond) ...)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // AOR_MODULO_TO_MULTIPLY
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
// BOR_AND_TO_OR
// -------------------------------------------------------------------------

// -------------------------------------------------------------------------
// BOR_OR_TO_AND
// -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
// CONDITIONAL_TRUE
// -------------------------------------------------------------------------

// -------------------------------------------------------------------------
// CONDITIONAL_FALSE
// -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
// STATEMENT_MUTATION
// -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // VML_VOID_METHOD_CALL
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // AOR_INCREMENT_TO_DECREMENT  (add1 → sub1)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // AOR_DECREMENT_TO_INCREMENT  (sub1 → add1)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // INEG_INVERT_NEGATIVE  ((- x) → x)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // RVM_TRUE_TO_FALSE  (#t → #f)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // RVM_FALSE_TO_TRUE  (#f → #t)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // RVM_ZERO_TO_ONE  (0 → 1)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // RVM_NONZERO_TO_ZERO  (non-zero integer → 0)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // RVM_PRIMITIVE_TO_ZERO  (numeric literal → 0, excluding 0 itself)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // RVM_OBJECT_TO_NULL  (complex/identifier return → '())
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // REMOVE_CONDITIONALS_*  (whole condition → #t / #f)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // EMPTY_RETURNS  (returned string → "", returned list → '())
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // NON_VOID_METHOD_CALLS  (call whose result is used → default value)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // CONSTRUCTOR_CALLS  (struct constructor call → '())
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // REMOVE_LOGICAL_NEGATION  ((not p) → p)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // JS_STRICT_EQ_TO_LOOSE_EQ  (eq? / eqv? → equal?)
    // -------------------------------------------------------------------------

    // -------------------------------------------------------------------------
    // Macro definitions are never mutated
    // -------------------------------------------------------------------------

    /** Two macros, then an ordinary function on line 9. */
    private static File writeMacroFile() throws IOException {
        File tmp = File.createTempFile("macros", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define-syntax-rule (when-positive n body ...)",
                "  (when (> n 0) (displayln n) (make-widget n) body ...))",
                "(define-syntax swap!",
                "  (syntax-rules ()",
                "    [(swap! a b) (let ([tmp a]) (set! a b) (set! b tmp))]))",
                "(define-syntax-rule (with-value v e)",
                "  (let () (define v (compute 1)) e))",
                "(define (after x) (if (> x 0) (+ x 1) (make-widget x)))",
                ""));
        return tmp;
    }

    @Test
    public void testSpecRulesSkipMacroDefinitions() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("macro-spec");
        java.nio.file.Files.copy(writeMacroFile().toPath(), dir.resolve("macros.rkt"));

        // Covers both the adapter-based rules and the scanner-backed rules'
        // regex fallbacks, which match (make-widget n) and (define v (compute 1))
        for (String spec : List.of("specs/OperatorMutations.spec", "specs/StatementMutation.spec")) {
            List<MutationPoint> points = Main.scanForMutations(dir, spec);
            assertFalse(points.isEmpty(), spec + " should still mutate the function after the macros");
            for (MutationPoint p : points) {
                assertEquals(9, p.getLineNumber(), spec + " mutated a macro: " + p.getOperator());
            }
        }
    }

    // -------------------------------------------------------------------------
    // let / lambda bodies: bindings are values, non-last body forms are statements
    // -------------------------------------------------------------------------

    @Test
    public void testSpecStatementRemovalCoversEveryBodyNotJustBegin() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("statements");
        java.nio.file.Files.writeString(dir.resolve("statements.rkt"), String.join("\n",
                "#lang racket",
                "(define (body x)",
                "  (displayln x)",
                "  (add1 x))",
                "(define (in-let x)",
                "  (let ([y (add1 x)])",
                "    (log-it y)",
                "    (* y 2)))",
                "(define (in-lambda) (lambda (x) (note x) x))",
                "(define (in-when x) (when (> x 0) (warn x) (add1 x)))",
                "(define (in-begin0 x) (begin0 (compute x) (record x)))",
                "(define (nested x)",
                "  (when (> x 0) (alert x))",
                "  (set! x 1)",
                "  x)",
                ""));

        // The discarded calls in a function, let, lambda, when and begin0 body, and
        // (alert x), the value of a when whose own value is discarded. Used values
        // stay: (add1 x), (* y 2), (compute x) (begin0's value); when and set! aren't calls.
        List<String> statements = Main.scanForMutations(dir, "specs/StatementMutation.spec").stream()
                .filter(p -> p.getCustomLabel().endsWith("delete invocation statement"))
                .map(MutationPoint::getOperator).toList();
        assertEquals(List.of("(displayln x)", "(log-it y)", "(note x)", "(warn x)", "(record x)", "(alert x)"),
                statements);

        // {non_void_method_call} uses the same positions: none of those is a used value
        List<String> used = RacketMutationScanner.visitorPoints(dir.resolve("statements.rkt").toFile(),
                Mutator.MutatorType.NON_VOID_METHOD_CALLS).stream().map(MutationPoint::getOperator).toList();
        for (String statement : statements) {
            assertFalse(used.contains(statement), "statement mutated as a used value: " + statement);
        }
        assertTrue(used.containsAll(List.of("(add1 x)", "(compute x)")), used.toString());
    }

    @Test
    public void testSpecStatementRemovalSkipsLetBindings() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("let-statements");
        java.nio.file.Files.writeString(dir.resolve("lets.rkt"), String.join("\n",
                "#lang racket",
                "(define (bindings a b)",
                "  (let ([add (+ a b)] [sub (- a b)])",
                "    (list add sub)))",
                "(define (let-body x)",
                "  (let ([y (add1 x)])",
                "    (displayln y)",
                "    (* y 2)))",
                "(define (star-body x)",
                "  (let* ([a (sqr x)] [b (add1 a)])",
                "    (displayln b)",
                "    b))",
                "(define (rec-body x)",
                "  (letrec ([f (lambda (n) n)])",
                "    (displayln x)",
                "    (f x)))",
                "(define (lam)",
                "  (lambda (x) (displayln x) x))",
                ""));

        List<MutationPoint> points = Main.scanForMutations(dir, "specs/StatementMutation.spec")
                .stream().filter(p -> p.getCustomLabel().endsWith("delete invocation statement")).toList();

        // Only the discarded displayln calls in each body; the binding values
        // (+ a b), (add1 x), (sqr x), (lambda (n) n) etc. are used, so they stay.
        assertEquals(List.of(7, 11, 15, 18), points.stream().map(MutationPoint::getLineNumber).toList());
        for (MutationPoint p : points) {
            assertTrue(p.getOperator().startsWith("(displayln"), p.getOperator());
            assertEquals("(void)", p.getReplacement());
        }
    }

    // -------------------------------------------------------------------------
    // Spec condition rules replace the whole Racket test
    // -------------------------------------------------------------------------

    @Test
    public void testSpecConditionRulesReplaceWholeRacketTests() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("racket-conditions");
        java.nio.file.Path file = dir.resolve("conds.rkt");
        java.nio.file.Files.writeString(file, String.join("\n",
                "#lang racket",
                "(define (a s t) (if (string<? s t) 1 2))",
                "(define (b s t) (when (string=? s t) (displayln s)))",
                "(define (c x y) (cond [(char=? x y) 1] [else 2]))",
                "(define (d n) (if (< n 0) 1 2))",
                ""));
        String source = java.nio.file.Files.readString(file);

        List<MutationPoint> points = Main.scanForMutations(dir, "specs/StatementMutation.spec")
                .stream().filter(p -> p.getCustomLabel().contains("condition →")).toList();

        // Names with <, > or = (string<?, char=?) are classified too, and each
        // test is replaced whole: (if (#t) 1 2) would fail when run.
        java.util.Set<String> mutatedLines = new java.util.TreeSet<>();
        for (MutationPoint p : points) {
            mutatedLines.add(Main.applyMutation(source, p).split("\n")[p.getLineNumber() - 1]);
        }
        assertEquals(new java.util.TreeSet<>(List.of(
                "(define (a s t) (if #t 1 2))", "(define (a s t) (if #f 1 2))",
                "(define (b s t) (when #t (displayln s)))", "(define (b s t) (when #f (displayln s)))",
                "(define (c x y) (cond [#t 1] [else 2]))", "(define (c x y) (cond [#f 1] [else 2]))",
                "(define (d n) (if #t 1 2))", "(define (d n) (if #f 1 2))")), mutatedLines);
        // One mutant per test per rule: the native and fallback scans don't double up
        assertEquals(8, points.size());
    }

    private static MutationPoint findByOperator(List<MutationPoint> points, String operator) {
        return points.stream()
                .filter(p -> p.getOperator().equals(operator))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no mutation point for " + operator));
    }

    // Parse-tree visitor behind the Racket adapter's PIT-style placeholders

    @Test
    public void testPitRemoveConditionalsEqualIf() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.visitorPoints(file, Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF);

        // Only describe-zero has an equality condition: (= n 0)
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("(= n 0)", p.getOperator());
        assertEquals("#t", p.getReplacement());
        assertEquals(57, p.getLineNumber());
        assertEquals(6, p.getColumnNumber());
    }

    @Test
    public void testPitRemoveConditionalsOrderIf() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.visitorPoints(file, Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF);

        // sign has two cond clauses with order checks; the else clause is skipped
        assertEquals(2, points.size());
        assertEquals("(< n 0)", points.get(0).getOperator());
        assertEquals("(> n 0)", points.get(1).getOperator());
        for (MutationPoint p : points) {
            assertEquals("#t", p.getReplacement());
        }
    }

    @Test
    public void testOperatorsRemoveConditionalsOrder() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.visitorPoints(file, Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF);

        // if conditions with an order check: compare-gt, compare-ge, compare-lt,
        // compare-le, check-logic (outer and + inner or), clamp-to-zero, under-limit?
        assertEquals(8, points.size());
        assertTrue(points.stream().anyMatch(p -> p.getOperator().equals("(and (> x 0) (> y 0))")),
                "nested order checks inside and/or should be detected");
        assertTrue(points.stream().noneMatch(p -> p.getOperator().equals("(= x y)")),
                "equality condition must not match ORDER variants");
    }

    @Test
    public void testTypedEmptyReturnsUseTheValuesInferredType() throws IOException {
        File tmp = File.createTempFile("typed-empty-returns", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (num x) (+ x 1))",
                "(define (call x) (sqr x))",
                "(define (lit) 42)",
                "(define (cmp a b) (< a b))",
                "(define (pred? x) (zero? x))",
                "(define (yes) #t)",
                "(define (str) \"hi\")",
                "(define (lst x) (list x))",
                "(define (unknown x) (helper x))",
                "(define (branches x) (if (> x 0) 1 0))",
                "(define (inner x)",
                "  (define y (add1 x))",
                "  (- y))",
                "(define (empty) '())",
                "(define (none) #f)",
                ""));

        List<MutationPoint> points = RacketMutationScanner.visitorPoints(tmp, Mutator.MutatorType.EMPTY_RETURNS);

        // Each tail value becomes the empty value of its type; the 0 branch,
        // '() and #f are already empty, and the internal define isn't a return.
        java.util.Map<String, String> mutated = new java.util.LinkedHashMap<>();
        for (MutationPoint p : points) mutated.put(p.getOperator(), p.getReplacement());
        java.util.Map<String, String> expected = new java.util.LinkedHashMap<>();
        expected.put("(+ x 1)", "0");
        expected.put("(sqr x)", "0");
        expected.put("42", "0");
        expected.put("(< a b)", "#f");
        expected.put("(zero? x)", "#f");
        expected.put("#t", "#f");
        expected.put("\"hi\"", "\"\"");
        expected.put("(list x)", "'()");
        expected.put("(helper x)", "'()");
        expected.put("1", "0");
        expected.put("(- y)", "0");
        assertEquals(expected, mutated);
    }

    @Test
    public void testPitNonVoidMethodCalls() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.visitorPoints(file, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        // length, add1, sub1, cons, string-append, list, sqr x2, even?,
        // make-point, make-hash, empty?, eq?  → 13 points
        // (display items) is excluded: void call, and in statement position
        assertEquals(13, points.size());
        assertTrue(points.stream().noneMatch(p -> p.getOperator().startsWith("(display")));

        MutationPoint sqr = findByOperator(points, "(sqr a)");
        assertEquals("0", sqr.getReplacement());
        assertEquals(79, sqr.getLineNumber());
        assertEquals(5, sqr.getColumnNumber());

        assertEquals("#f", findByOperator(points, "(even? n)").getReplacement());
        assertEquals("\"\"", findByOperator(points, "(string-append \"Hello, \" name)").getReplacement());
        assertEquals("'()", findByOperator(points, "(cons x y)").getReplacement());
    }

    @Test
    public void testNonVoidCallsSkipsDiscardedAndVoidCalls() throws IOException {
        File tmp = File.createTempFile("non-void-calls", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (f x)",
                "  (helper x)",
                "  (list (begin (log-it x) (compute x))",
                "        (begin0 (first-val x) (after x))",
                "        (when (ready? x) (prep x) (finish x))",
                "        (set-box! x 1)",
                "        (printf \"~a\" x)",
                "        (outer (inner x))))",
                ""));

        List<MutationPoint> points = RacketMutationScanner.visitorPoints(tmp, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        // Skipped: helper/log-it/after/prep (result discarded), set-box!/printf (void),
        // when (macro, not a call). The list call spans several lines and is mutated whole.
        List<String> mutated = points.stream().map(MutationPoint::getOperator).toList();
        assertTrue(mutated.get(0).startsWith("(list (begin (log-it x) (compute x))\n"), mutated.get(0));
        assertTrue(mutated.get(0).endsWith("(outer (inner x)))"), mutated.get(0));
        assertEquals(List.of("(compute x)", "(first-val x)", "(ready? x)", "(finish x)",
                "(outer (inner x))", "(inner x)"), mutated.subList(1, mutated.size()));
        assertEquals("#f", findByOperator(points, "(ready? x)").getReplacement());
        assertEquals("'()", findByOperator(points, "(inner x)").getReplacement());
    }

    @Test
    public void testPitConstructorCalls() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.visitorPoints(file, Mutator.MutatorType.CONSTRUCTOR_CALLS);

        // (make-point 0 0) from define-struct, (make-hash) built-in  → 2 points
        assertEquals(2, points.size());

        MutationPoint point = findByOperator(points, "(make-point 0 0)");
        assertEquals("'()", point.getReplacement());
        assertEquals(89, point.getLineNumber());
        assertEquals(2, point.getColumnNumber());

        assertEquals("'()", findByOperator(points, "(make-hash)").getReplacement());
    }

    @Test
    public void testMultiLineCallsAndValuesAreMutatedWhole() throws IOException {
        File tmp = File.createTempFile("multi-line", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(struct posn (x y))",
                "(define (build a)",
                "  (posn a",
                "        a))",
                "(define (greet name)",
                "  (string-append \"Hello, \"",
                "                 name))",
                ""));
        String source = java.nio.file.Files.readString(tmp.toPath());

        // Each operator finds the call that spans two lines, and Main replaces all of it
        MutationPoint constructor = RacketMutationScanner.visitorPoints(tmp, Mutator.MutatorType.CONSTRUCTOR_CALLS).get(0);
        assertEquals("(posn a\n        a)", constructor.getOperator());
        assertTrue(Main.applyMutation(source, constructor).contains("(define (build a)\n  '())"));

        MutationPoint nonVoid = findByOperator(RacketMutationScanner.visitorPoints(tmp,
                Mutator.MutatorType.NON_VOID_METHOD_CALLS), "(string-append \"Hello, \"\n                 name)");
        assertTrue(Main.applyMutation(source, nonVoid).contains("(define (greet name)\n  \"\")"));

        // Every returned value gets an empty one, so (posn a a) is found too
        MutationPoint empty = findByOperator(RacketMutationScanner.visitorPoints(tmp,
                Mutator.MutatorType.EMPTY_RETURNS), nonVoid.getOperator());
        assertTrue(Main.applyMutation(source, empty).contains("(define (greet name)\n  \"\")"));
    }

    @Test
    public void testOnlyDeclaredStructsAndBuiltInsAreConstructors() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("make-functions");
        java.nio.file.Files.writeString(dir.resolve("make.rkt"), String.join("\n",
                "#lang racket",
                "(struct point (x y))",
                "(define-struct box (v))",
                "(define (make-report x) (list x))",
                "(define (build a)",
                "  (list (point a 1) (make-box a) (make-hash) (make-vector 3 0) (make-report a)))",
                ""));

        // make-report is an ordinary function that happens to start with make-
        List<String> expected = List.of("(point a 1)", "(make-box a)", "(make-hash)", "(make-vector 3 0)");
        assertEquals(expected, RacketMutationScanner.visitorPoints(dir.resolve("make.rkt").toFile(),
                Mutator.MutatorType.CONSTRUCTOR_CALLS).stream().map(MutationPoint::getOperator).toList());

        // The spec rule, including the adapter's single-line fallback, agrees
        List<String> spec = Main.scanForMutations(dir, "specs/StatementMutation.spec")
                .stream().filter(p -> p.getCustomLabel().endsWith("constructor call → null"))
                .map(MutationPoint::getOperator).toList();
        assertEquals(expected, spec);
    }

    @Test
    public void testStructsFromRequiredFilesAreConstructors() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("required-structs");
        java.nio.file.Files.createDirectories(dir.resolve("model"));
        java.nio.file.Files.createDirectories(dir.resolve("src"));
        java.nio.file.Files.writeString(dir.resolve("shapes.rkt"), String.join("\n",
                "#lang racket",
                "(provide (all-defined-out))",
                "(struct posn (x y))",
                "(define-struct box (v))",
                "; (struct commented-out (x))",
                "(module+ test (require \"src/main.rkt\"))",   // a cycle back to main.rkt
                ""));
        java.nio.file.Files.writeString(dir.resolve("model/all.rkt"), String.join("\n",
                "#lang racket",
                "(require \"../shapes.rkt\")",
                "(provide (all-defined-out) (all-from-out \"../shapes.rkt\"))",
                "(struct circle (r))",
                ""));
        java.nio.file.Files.writeString(dir.resolve("src/main.rkt"), String.join("\n",
                "#lang racket",
                "(require \"../shapes.rkt\"",
                "         (prefix-in m: \"../model/all.rkt\")",
                "         (rename-in (file \"../shapes.rkt\") [posn point])",
                "         racket/list)",
                "(define (helper a) a)",
                "(define (build a)",
                "  (list (posn a 1) (make-box a) (m:circle a) (m:posn a a) (point 1 2)",
                "        (circle a) (commented-out a) (helper a)))",
                ""));

        // circle is only reachable as m:circle; commented-out and helper aren't structs
        List<String> expected = List.of("(posn a 1)", "(make-box a)", "(m:circle a)", "(m:posn a a)", "(point 1 2)");
        assertEquals(expected, RacketMutationScanner.visitorPoints(dir.resolve("src/main.rkt").toFile(),
                Mutator.MutatorType.CONSTRUCTOR_CALLS).stream().map(MutationPoint::getOperator).toList());

        List<String> spec = Main.scanForMutations(dir, "specs/StatementMutation.spec")
                .stream().filter(p -> p.getCustomLabel().endsWith("constructor call → null"))
                .filter(p -> p.getSourceFile().getName().equals("main.rkt"))
                .map(MutationPoint::getOperator).toList();
        assertEquals(expected, spec);
    }

    @Test
    public void testConstructorCallsFindsDeclaredStructsAndSkipsOtherCalls() throws IOException {
        File tmp = File.createTempFile("constructor-calls", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (build a)",
                "  (list (posn a 1) (make-hash) (helper a)))",
                "(define (nested) (wrap (posn (posn 1 2) 3)))",
                "(define (spread)",
                "  (posn 1",
                "        2))",
                "(struct posn (x y))",
                "(define-struct box (v))",
                "(define (boxed) (make-box 5))",
                ""));

        List<MutationPoint> points = RacketMutationScanner.visitorPoints(tmp, Mutator.MutatorType.CONSTRUCTOR_CALLS);

        // posn is found although its struct is declared after the calls, including the
        // call that spans several lines. Skipped: list/helper/wrap (not constructors)
        // and the (struct ...) declaration
        List<String> mutated = points.stream().map(MutationPoint::getOperator).toList();
        assertEquals(List.of("(posn a 1)", "(make-hash)", "(posn (posn 1 2) 3)", "(posn 1 2)",
                "(posn 1\n        2)", "(make-box 5)"), mutated);
        assertTrue(points.stream().allMatch(p -> p.getReplacement().equals("'()")));
    }

    @Test
    public void testNonVoidCallsSkipDiscardedLetAndLambdaBodyForms() throws IOException {
        File tmp = File.createTempFile("let-non-void", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (f x)",
                "  (let ([y (add1 x)])",
                "    (compute y)",
                "    (finish y)))",
                "(define (g)",
                "  (lambda (x) (log-it x) (result x)))",
                ""));

        List<MutationPoint> points = RacketMutationScanner.visitorPoints(tmp, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        // (compute y) and (log-it x) are discarded; the binding value and the
        // last body forms are used
        assertEquals(List.of("(add1 x)", "(finish y)", "(result x)"),
                points.stream().map(MutationPoint::getOperator).toList());
    }
}
