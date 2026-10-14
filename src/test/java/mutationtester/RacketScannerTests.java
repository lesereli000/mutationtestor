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

    @Test
    public void testAdd1PlusToMinus() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
        assertEquals(6, p.getLineNumber());
    }

    @Test
    public void testAdd2PlusToMinus() throws IOException {
        File file = new File("samples/RacketProject/project/src/add2.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
        assertEquals(6, p.getLineNumber());
    }

    @Test
    public void testOperatorsPlusToMinus() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        // operators.rkt has one (+ x y) in add function
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // AOR_MINUS_TO_PLUS
    // -------------------------------------------------------------------------

    @Test
    public void testAdd1MinusToPlus() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        // add1.rkt has no - operators
        assertEquals(0, points.size());
    }

    @Test
    public void testOperatorsMinusToPlus() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        // operators.rkt has one (- x y) in subtract function
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("-", p.getOperator());
        assertEquals("+", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // ROR_GT_TO_GE
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsGTtoGE() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        // operators.rkt has (> x y) in compare-gt, and two (> x 0) in check-logic
        assertEquals(5, points.size());
        for (MutationPoint p : points) {
            assertEquals(">", p.getOperator());
            assertEquals(">=", p.getReplacement());
        }
    }

    @Test
    public void testAdd1GTtoGE() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // ROR_GE_TO_GT
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsGEtoGT() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GE_TO_GT);

        // operators.rkt has one (>= x y) in compare-ge
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(">=", p.getOperator());
        assertEquals(">", p.getReplacement());
    }

    // -------------------------------------------------------------------------
// ROR_LT_TO_LE
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsLTtoLE() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);

        // operators.rkt has one (< x y) in compare-lt
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<", p.getOperator());
        assertEquals("<=", p.getReplacement());
    }

    @Test
    public void testAdd1LTtoLE() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);

        assertEquals(0, points.size());
    }

// -------------------------------------------------------------------------
// ROR_LE_TO_LT
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsLEtoLT() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);

        // operators.rkt has compare-le, clamp-to-zero, and under-limit? using <=
        assertEquals(3, points.size());
        for (MutationPoint p : points) {
            assertEquals("<=", p.getOperator());
            assertEquals("<", p.getReplacement());
        }
    }

    @Test
    public void testAdd1LEtoLT() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // EQ_NEGATE
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsEqNegate() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        // operators.rkt has one (= x y) in check-equal
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("=", p.getOperator());
        assertEquals("not =", p.getReplacement());
    }

    @Test
    public void testAdd1EqNegate() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // LOGICAL_AND_TO_OR
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsAndToOr() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        // operators.rkt has one (and ...) in check-logic
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("and", p.getOperator());
        assertEquals("or", p.getReplacement());
    }

    @Test
    public void testAdd1AndToOr() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // LOGICAL_OR_TO_AND
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsOrToAnd() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_OR_TO_AND);

        // operators.rkt has one (or ...) in check-logic
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("or", p.getOperator());
        assertEquals("and", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // NLT_INTEGER_TO_FLOAT
    // -------------------------------------------------------------------------

    @Test
    public void testAdd1IntegerToFloat() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NLT_INTEGER_TO_FLOAT);

        // add1.rkt has one integer literal: 1 in (+ 1 x)
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("1", p.getOperator());
        assertEquals("1.0", p.getReplacement());
    }

    @Test
    public void testAdd2IntegerToFloat() throws IOException {
        File file = new File("samples/RacketProject/project/src/add2.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NLT_INTEGER_TO_FLOAT);

        // add2.rkt has one integer literal: 2 in (+ 2 x)
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("2", p.getOperator());
        assertEquals("2.0", p.getReplacement());
    }

    @Test
    public void testOperatorsIntegerToFloat() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NLT_INTEGER_TO_FLOAT);

        // operators.rkt: 0x4 in check-logic, 5+10 in check-floats, 0x2 in clamp-to-zero
        assertEquals(8, points.size());
        for (MutationPoint p : points) {
            assertFalse(p.getOperator().contains("."));
            assertTrue(p.getReplacement().endsWith(".0"));
        }
    }

    // -------------------------------------------------------------------------
    // NLT_FLOAT_TO_INTEGER
    // -------------------------------------------------------------------------

    @Test
    public void testAdd1FloatToInteger() throws IOException {
        File file = new File("samples/RacketProject/project/src/add1.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NLT_FLOAT_TO_INTEGER);

        // add1.rkt has no float literals
        assertEquals(0, points.size());
    }

    @Test
    public void testOperatorsFloatToInteger() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NLT_FLOAT_TO_INTEGER);

        // operators.rkt has one float literal: 3.14 in check-floats
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("3.14", p.getOperator());
        assertEquals("3", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // Verify strings and comments are never scanned
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsStringNotMutated() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        // All found points should have operator "+" not inside a string
        for (MutationPoint p : points) {
            assertEquals("+", p.getOperator());
        }
    }

    // -------------------------------------------------------------------------
    // TYPE_INT_TO_FLOAT
    // -------------------------------------------------------------------------

    @Test
    public void testTypedFunctionsIntToFloat() throws IOException {
        File file = new File("samples/RacketProject/project/src/typed-functions.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);

        // typed-functions.rkt has 0 Integer type annotations
        assertEquals(0, points.size());
        for (MutationPoint p : points) {
            assertEquals("Integer", p.getOperator());
            assertEquals("Float", p.getReplacement());
        }
    }

    @Test
    public void testOperatorsIntToFloat() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);

        // operators.rkt has no type annotations (not Typed Racket)
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // TYPE_FLOAT_TO_INT
    // -------------------------------------------------------------------------

    @Test
    public void testTypedFunctionsFloatToInt() throws IOException {
        File file = new File("samples/RacketProject/project/src/typed-functions.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);

        // typed-functions.rkt has 0 Float type annotations
        assertEquals(0, points.size());
        for (MutationPoint p : points) {
            assertEquals("Float", p.getOperator());
            assertEquals("Integer", p.getReplacement());
        }
    }

    @Test
    public void testOperatorsFloatToInt() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);

        // operators.rkt has no type annotations (not Typed Racket)
        assertEquals(0, points.size());
    }


    // -------------------------------------------------------------------------
// AOR_MULTIPLY_TO_DIVIDE
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsMultiplyToDivide() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE);


        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("*", p.getOperator());
        assertEquals("/", p.getReplacement());
    }

// -------------------------------------------------------------------------
// AOR_DIVIDE_TO_MULTIPLY
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsDivideToMultiply() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("/", p.getOperator());
        assertEquals("*", p.getReplacement());
    }


    @Test
    public void testArithmeticMultiplyToDivide() throws IOException {
        File file = new File("samples/RacketCalculator/calculator/src/arithmetic.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE);
        assertEquals(2, points.size());
        for (MutationPoint p : points) {
            assertEquals("*", p.getOperator());
            assertEquals("/", p.getReplacement());
        }
    }

    @Test
    public void testArithmeticDivideToMultiply() throws IOException {
        File file = new File("samples/RacketCalculator/calculator/src/arithmetic.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("/", p.getOperator());
        assertEquals("*", p.getReplacement());
    }

    @Test
    public void testOperatorsNoMultiply() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE);
        assertEquals(1, points.size());
    }

    @Test
    public void testOperatorsNoDivide() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY);
        assertEquals(1, points.size());
    }


    // -------------------------------------------------------------------------
    // NEGATE_CONDITIONALS: (if cond ...) → (if (not cond) ...)
    // -------------------------------------------------------------------------

    @Test
    public void testArithmeticNegateConditionals() throws IOException {
        File file = new File("samples/RacketCalculator/calculator/src/arithmetic.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NEGATE_CONDITIONALS);

        // arithmetic.rkt has exactly one (if ...) form in divide
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("(= y 0)", p.getOperator());
        assertEquals("(not (= y 0))", p.getReplacement());
    }

    @Test
    public void testOperatorsNegateConditionals() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NEGATE_CONDITIONALS);

        // operators.rkt has 9 (if ...) forms (includes clamp-to-zero, under-limit?);
        // each condition is wrapped in (not ...), never the if keyword itself
        assertEquals(List.of("(> x y)", "(>= x y)", "(< x y)", "(<= x y)", "(= x y)",
                "(and (> x 0) (> y 0))", "(or (> x 0) (> y 0))", "(<= n 0)", "(<= x limit)"),
                points.stream().map(MutationPoint::getOperator).toList());
        for (MutationPoint p : points) {
            assertEquals("(not " + p.getOperator() + ")", p.getReplacement());
        }
    }

    @Test
    public void testNegateConditionalsProducesValidIf() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        String source = java.nio.file.Files.readString(file.toPath());
        MutationPoint p = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NEGATE_CONDITIONALS).get(0);

        // Before, the keyword was wrapped: ((not if) (> x y) ...), which doesn't run
        String mutatedLine = Main.applyMutation(source, p).split("\n")[p.getLineNumber() - 1].strip();
        assertEquals("(if (not (> x y))", mutatedLine);
    }

    // -------------------------------------------------------------------------
    // AOR_MODULO_TO_MULTIPLY
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsModuloToMultiply() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY);

        // operators.rkt has one (modulo x y) in mod-test
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("modulo", p.getOperator());
        assertEquals("*", p.getReplacement());
    }

    // -------------------------------------------------------------------------
// BOR_AND_TO_OR
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsBitwiseAndToOr() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.BOR_AND_TO_OR);

        // operators.rkt has one (bitwise-and x y) in bitwise-test
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("bitwise-and", p.getOperator());
        assertEquals("bitwise-ior", p.getReplacement());
    }

// -------------------------------------------------------------------------
// BOR_OR_TO_AND
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsBitwiseOrToAnd() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.BOR_OR_TO_AND);

        // operators.rkt has one (bitwise-ior x y) in bitwise-or-test
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("bitwise-ior", p.getOperator());
        assertEquals("bitwise-and", p.getReplacement());
    }

    // -------------------------------------------------------------------------
// CONDITIONAL_TRUE
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsConditionalTrue() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.CONDITIONAL_TRUE);

        // operators.rkt has 9 (if ...) expressions (includes clamp-to-zero, under-limit?)
        assertEquals(9, points.size());
        for (MutationPoint p : points) {
            assertEquals("#t", p.getReplacement());
            assertFalse(p.getOperator().isEmpty());
        }
    }

// -------------------------------------------------------------------------
// CONDITIONAL_FALSE
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsConditionalFalse() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.CONDITIONAL_FALSE);

        // operators.rkt has 9 (if ...) expressions (includes clamp-to-zero, under-limit?)
        assertEquals(9, points.size());
        for (MutationPoint p : points) {
            assertEquals("#f", p.getReplacement());
            assertFalse(p.getOperator().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
// STATEMENT_MUTATION
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsStatementMutation() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.STATEMENT_MUTATION);

        // operators.rkt has one statement-level function call: (display name) in greet
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertTrue(p.getOperator().contains("display"));
        assertEquals("(void)", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // VML_VOID_METHOD_CALL
    // -------------------------------------------------------------------------

    @Test
    public void testPitVoidMethodCall() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.VML_VOID_METHOD_CALL);

        // pit-mutations.rkt has one void call: (display items) in log-and-count
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertTrue(p.getOperator().contains("display"));
        assertEquals("", p.getReplacement());
    }

    @Test
    public void testOperatorsVoidMethodCall() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.VML_VOID_METHOD_CALL);

        // Same call site as STATEMENT_MUTATION, but replacement is ""
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertTrue(p.getOperator().contains("display"));
        assertEquals("", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // AOR_INCREMENT_TO_DECREMENT  (add1 → sub1)
    // -------------------------------------------------------------------------

    @Test
    public void testPitIncrementToDecrement() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("add1", p.getOperator());
        assertEquals("sub1", p.getReplacement());
    }

    @Test
    public void testOperatorsNoIncrement() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT);
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // AOR_DECREMENT_TO_INCREMENT  (sub1 → add1)
    // -------------------------------------------------------------------------

    @Test
    public void testPitDecrementToIncrement() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("sub1", p.getOperator());
        assertEquals("add1", p.getReplacement());
    }

    @Test
    public void testOperatorsNoDecrement() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT);
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // INEG_INVERT_NEGATIVE  ((- x) → x)
    // -------------------------------------------------------------------------

    @Test
    public void testPitInvertNegative() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.INEG_INVERT_NEGATIVE);

        // negate-x body is (- x), a unary minus with one argument
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertTrue(p.getOperator().contains("-"));
        assertEquals("x", p.getReplacement());
    }

    @Test
    public void testOperatorsNoInvertNegative() throws IOException {
        // operators.rkt has only binary minus (- x y), never unary (- x)
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.INEG_INVERT_NEGATIVE);
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // RVM_TRUE_TO_FALSE  (#t → #f)
    // -------------------------------------------------------------------------

    @Test
    public void testPitRvmTrueToFalse() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_TRUE_TO_FALSE);

        // always-true returns #t
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("#t", p.getOperator());
        assertEquals("#f", p.getReplacement());
    }

    @Test
    public void testOperatorsRvmTrueToFalse() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_TRUE_TO_FALSE);

        // under-limit? returns (if ...) not a bare #t, so no match
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // RVM_FALSE_TO_TRUE  (#f → #t)
    // -------------------------------------------------------------------------

    @Test
    public void testPitRvmFalseToTrue() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_FALSE_TO_TRUE);

        // always-false returns #f
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("#f", p.getOperator());
        assertEquals("#t", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // RVM_ZERO_TO_ONE  (0 → 1)
    // -------------------------------------------------------------------------

    @Test
    public void testPitRvmZeroToOne() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_ZERO_TO_ONE);

        // return-zero returns 0
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("0", p.getOperator());
        assertEquals("1", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // RVM_NONZERO_TO_ZERO  (non-zero integer → 0)
    // -------------------------------------------------------------------------

    @Test
    public void testPitRvmNonzeroToZero() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_NONZERO_TO_ZERO);

        // return-nonzero returns 42
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("42", p.getOperator());
        assertEquals("0", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // RVM_PRIMITIVE_TO_ZERO  (numeric literal → 0, excluding 0 itself)
    // -------------------------------------------------------------------------

    @Test
    public void testPitRvmPrimitiveToZero() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_PRIMITIVE_TO_ZERO);

        // return-nonzero returns 42; return-zero returns 0 (excluded)
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("42", p.getOperator());
        assertEquals("0", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // RVM_OBJECT_TO_NULL  (complex/identifier return → '())
    // -------------------------------------------------------------------------

    @Test
    public void testPitRvmObjectToNull() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_OBJECT_TO_NULL);

        // Functions with non-literal returns: log-and-count, increment-x, decrement-x,
        // negate-x, get-pair, get-id, describe-zero, sign, greeting, pair-list,
        // sum-of-squares, parity, origin, empty-table, non-empty?, same-object?  → 16 points
        // (vowels returns a quoted literal, which is not an object return)
        assertEquals(16, points.size());
        for (MutationPoint p : points) {
            assertEquals("'()", p.getReplacement());
        }
    }

    @Test
    public void testOperatorsRvmObjectToNull() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.RVM_OBJECT_TO_NULL);

        // Every function in operators.rkt has a complex return expression
        assertTrue(points.size() > 0);
        for (MutationPoint p : points) {
            assertEquals("'()", p.getReplacement());
        }
    }

    // -------------------------------------------------------------------------
    // REMOVE_CONDITIONALS_*  (whole condition → #t / #f)
    // -------------------------------------------------------------------------

    @Test
    public void testPitRemoveConditionalsEqualIf() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF);

        // Only describe-zero has an equality condition: (= n 0)
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("(= n 0)", p.getOperator());
        assertEquals("#t", p.getReplacement());
        assertEquals(57, p.getLineNumber());
        assertEquals(6, p.getColumnNumber());
    }

    @Test
    public void testPitRemoveConditionalsEqualElse() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE);

        assertEquals(1, points.size());
        assertEquals("(= n 0)", points.get(0).getOperator());
        assertEquals("#f", points.get(0).getReplacement());
    }

    @Test
    public void testPitRemoveConditionalsOrderIf() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF);

        // sign has two cond clauses with order checks; the else clause is skipped
        assertEquals(2, points.size());
        assertEquals("(< n 0)", points.get(0).getOperator());
        assertEquals("(> n 0)", points.get(1).getOperator());
        for (MutationPoint p : points) {
            assertEquals("#t", p.getReplacement());
        }
    }

    @Test
    public void testPitRemoveConditionalsOrderElse() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_ELSE);

        assertEquals(2, points.size());
        for (MutationPoint p : points) {
            assertEquals("#f", p.getReplacement());
        }
    }

    @Test
    public void testOperatorsRemoveConditionalsOrder() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF);

        // if conditions with an order check: compare-gt, compare-ge, compare-lt,
        // compare-le, check-logic (outer and + inner or), clamp-to-zero, under-limit?
        assertEquals(8, points.size());
        assertTrue(points.stream().anyMatch(p -> p.getOperator().equals("(and (> x 0) (> y 0))")),
                "nested order checks inside and/or should be detected");
        assertTrue(points.stream().noneMatch(p -> p.getOperator().equals("(= x y)")),
                "equality condition must not match ORDER variants");
    }

    @Test
    public void testOperatorsRemoveConditionalsEqual() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE);

        // Only check-equal has an equality condition: (= x y)
        assertEquals(1, points.size());
        assertEquals("(= x y)", points.get(0).getOperator());
        assertEquals("#f", points.get(0).getReplacement());
    }

    @Test
    public void testRemoveConditionalsWhenUnlessAndNot() throws IOException {
        File tmp = File.createTempFile("remove-cond", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (f x)",
                "  (when (not (equal? x 'a)) (display x))",
                "  (unless (negative? x) (display x))",
                "  (if (> x",
                "         0) 1 2))",
                ""));

        List<MutationPoint> eq = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF);
        assertEquals(1, eq.size());
        assertEquals("(not (equal? x 'a))", eq.get(0).getOperator());
        assertEquals(3, eq.get(0).getLineNumber());

        // (negative? x) under unless is an order check; the multi-line (> x 0) is skipped
        List<MutationPoint> ord = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_ELSE);
        assertEquals(1, ord.size());
        assertEquals("(negative? x)", ord.get(0).getOperator());
        assertEquals("#f", ord.get(0).getReplacement());
    }

    // -------------------------------------------------------------------------
    // EMPTY_RETURNS  (returned string → "", returned list → '())
    // -------------------------------------------------------------------------

    @Test
    public void testPitEmptyReturns() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.EMPTY_RETURNS);

        // describe-zero: 2 if branches, sign: 3 cond clauses (incl. else),
        // greeting, vowels, pair-list, parity: 2 if branches  → 10 points
        assertEquals(10, points.size());

        MutationPoint greeting = findByOperator(points, "(string-append \"Hello, \" name)");
        assertEquals("\"\"", greeting.getReplacement());

        MutationPoint vowels = findByOperator(points, "'(a e i o u)");
        assertEquals("'()", vowels.getReplacement());
        assertEquals(71, vowels.getLineNumber());
        assertEquals(2, vowels.getColumnNumber());

        assertEquals("'()", findByOperator(points, "(list x y)").getReplacement());
        assertEquals(2, points.stream().filter(p -> p.getOperator().equals("\"zero\"")).count(),
                "\"zero\" is returned from both describe-zero and sign's else clause");
    }

    @Test
    public void testOperatorsEmptyReturnsInBranches() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.EMPTY_RETURNS);

        // 5 two-branch string ifs + check-logic's nested if (3 string results)
        // + check-floats' (list a b c), after its internal defines
        assertEquals(14, points.size());
        for (MutationPoint p : points) {
            if (p.getOperator().equals("(list a b c)")) {
                assertEquals("'()", p.getReplacement());
                continue;
            }
            assertTrue(p.getOperator().startsWith("\""));
            assertEquals("\"\"", p.getReplacement());
        }
        assertTrue(points.stream().anyMatch(p -> p.getOperator().equals("\"neither-positive\"")),
                "string in a nested if's else branch should be found");
    }

    @Test
    public void testEmptyReturnsSkipsNonEmptiableAndAlreadyEmpty() throws IOException {
        File tmp = File.createTempFile("empty-returns", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (a) \"\")",
                "(define (b) '())",
                "(define (c) (list))",
                "(define (d x) (+ x 1))",
                "(define (e) #t)",
                "(define (f x) (let ([y x]) (map add1 y)))",
                "(define (g x) (begin (display x) (format \"~a\" x)))",
                "(define (h) (list 1",
                "                  2))",
                ""));

        List<MutationPoint> points = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.EMPTY_RETURNS);

        // a/b/c already empty, d/e not a string or list, h spans two lines;
        // only the let body in f and the last begin expr in g are mutated
        assertEquals(2, points.size());
        assertEquals("(map add1 y)", points.get(0).getOperator());
        assertEquals("'()", points.get(0).getReplacement());
        assertEquals("(format \"~a\" x)", points.get(1).getOperator());
        assertEquals("\"\"", points.get(1).getReplacement());
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

        List<MutationPoint> points = RacketMutationScanner.scanTypedEmptyReturns(tmp);

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

    // -------------------------------------------------------------------------
    // NON_VOID_METHOD_CALLS  (call whose result is used → default value)
    // -------------------------------------------------------------------------

    @Test
    public void testPitNonVoidMethodCalls() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

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

        List<MutationPoint> points = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        // Skipped: helper/log-it/after/prep (result discarded), set-box!/printf (void),
        // when (macro, not a call), list (spans several lines)
        List<String> mutated = points.stream().map(MutationPoint::getOperator).toList();
        assertEquals(List.of("(compute x)", "(first-val x)", "(ready? x)", "(finish x)",
                "(outer (inner x))", "(inner x)"), mutated);
        assertEquals("#f", findByOperator(points, "(ready? x)").getReplacement());
        assertEquals("'()", findByOperator(points, "(inner x)").getReplacement());
    }

    // -------------------------------------------------------------------------
    // CONSTRUCTOR_CALLS  (struct constructor call → '())
    // -------------------------------------------------------------------------

    @Test
    public void testPitConstructorCalls() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.CONSTRUCTOR_CALLS);

        // (make-point 0 0) from define-struct, (make-hash) built-in  → 2 points
        assertEquals(2, points.size());

        MutationPoint point = findByOperator(points, "(make-point 0 0)");
        assertEquals("'()", point.getReplacement());
        assertEquals(89, point.getLineNumber());
        assertEquals(2, point.getColumnNumber());

        assertEquals("'()", findByOperator(points, "(make-hash)").getReplacement());
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

        List<MutationPoint> points = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.CONSTRUCTOR_CALLS);

        // posn is found although its struct is declared after the calls.
        // Skipped: list/helper/wrap (not constructors), the (struct ...) declaration,
        // and the posn call that spans several lines
        List<String> mutated = points.stream().map(MutationPoint::getOperator).toList();
        assertEquals(List.of("(posn a 1)", "(make-hash)", "(posn (posn 1 2) 3)", "(posn 1 2)",
                "(make-box 5)"), mutated);
        assertTrue(points.stream().allMatch(p -> p.getReplacement().equals("'()")));
    }

    // -------------------------------------------------------------------------
    // REMOVE_LOGICAL_NEGATION  ((not p) → p)
    // -------------------------------------------------------------------------

    @Test
    public void testPitRemoveLogicalNegation() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.REMOVE_LOGICAL_NEGATION);

        // (not (empty? items)) in non-empty?  → 1 point
        assertEquals(1, points.size());

        MutationPoint point = points.get(0);
        assertEquals("(not (empty? items))", point.getOperator());
        assertEquals("(empty? items)", point.getReplacement());
        assertEquals(97, point.getLineNumber());
        assertEquals(2, point.getColumnNumber());
    }

    @Test
    public void testRemoveLogicalNegationUnwrapsOperandAndSkipsOtherForms() throws IOException {
        File tmp = File.createTempFile("remove-negation", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (f a b)",
                "  (if (not a) (not (not b)) (and (not (= a b)) b)))",
                "(define (bad) (not))",
                "(define (two) (not 1 2))",
                "(define (spread x)",
                "  (not",
                "    x))",
                "; (not hidden)",
                "(define (s) \"(not quoted)\")",
                ""));

        List<MutationPoint> points = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.REMOVE_LOGICAL_NEGATION);

        // Nested negations each get their own point. Skipped: (not) and (not 1 2)
        // (not single-operand negations), the multi-line negation, and the
        // comment/string, which aren't code
        List<String> mutated = points.stream().map(MutationPoint::getOperator).toList();
        assertEquals(List.of("(not a)", "(not (not b))", "(not b)", "(not (= a b))"), mutated);
        List<String> replacements = points.stream().map(MutationPoint::getReplacement).toList();
        assertEquals(List.of("a", "(not b)", "b", "(= a b)"), replacements);
    }

    // -------------------------------------------------------------------------
    // JS_STRICT_EQ_TO_LOOSE_EQ  (eq? / eqv? → equal?)
    // -------------------------------------------------------------------------

    @Test
    public void testPitStrictEquality() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.JS_STRICT_EQ_TO_LOOSE_EQ);

        // (eq? a b) in same-object?  → 1 point
        assertEquals(1, points.size());

        MutationPoint point = points.get(0);
        assertEquals("eq?", point.getOperator());
        assertEquals("equal?", point.getReplacement());
        assertEquals(101, point.getLineNumber());
        assertEquals(3, point.getColumnNumber());
    }

    @Test
    public void testStrictEqualityWeakensEqAndEqvOnly() throws IOException {
        File tmp = File.createTempFile("strict-equality", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (f a b)",
                "  (and (eq? a b) (eqv? a 1) (equal? a b) (= a b)))",
                "(define (g x) (memq x '(eq? eqv?)))",
                "; (eq? hidden 1)",
                "(define (s) \"(eqv? 1 2)\")",
                ""));

        List<MutationPoint> points = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.JS_STRICT_EQ_TO_LOOSE_EQ);

        // Skipped: equal? (already loose), = (numeric, not identity), the quoted
        // symbols, and the comment/string, which aren't code
        assertEquals(List.of("eq?", "eqv?"), points.stream().map(MutationPoint::getOperator).toList());
        assertTrue(points.stream().allMatch(p -> p.getReplacement().equals("equal?")));
        assertTrue(points.stream().allMatch(p -> p.getLineNumber() == 3));
    }

    @Test
    public void testQuotedListsWithNumbersBooleansAndOperatorsParse() throws IOException {
        File tmp = File.createTempFile("quoted-lists", ".rkt");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), String.join("\n",
                "#lang racket",
                "(define (nums) (length '(1 2.5 3)))",
                "(define (bools) (length '(#t #f)))",
                "(define (ops) (length '(+ - < >=)))",
                "(define (nested) (length '(1 () (2 3))))",
                "(define (after x) (add1 x))",
                ""));

        List<MutationPoint> points = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        // Each call is captured whole (a quoted number used to end the parse
        // early, cutting off the closing paren), and parsing continues past them.
        assertEquals(List.of("(length '(1 2.5 3))", "(length '(#t #f))", "(length '(+ - < >=))",
                "(length '(1 () (2 3)))", "(add1 x)"),
                points.stream().map(MutationPoint::getOperator).toList());
    }

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
    public void testBuiltInOperatorsSkipMacroDefinitions() throws IOException {
        File file = writeMacroFile();
        for (Mutator.MutatorType type : RacketMutationScanner.getSupportedTypes()) {
            for (MutationPoint p : RacketMutationScanner.scanFile(file, type)) {
                assertEquals(9, p.getLineNumber(), type + " mutated a macro: " + p.getOperator());
            }
        }
        // The function after the macros is still mutated
        assertEquals(1, RacketMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS).size());
        assertEquals(1, RacketMutationScanner.scanFile(file, Mutator.MutatorType.CONSTRUCTOR_CALLS).size());
    }

    @Test
    public void testSpecRulesSkipMacroDefinitions() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("macro-spec");
        java.nio.file.Files.copy(writeMacroFile().toPath(), dir.resolve("macros.rkt"));

        // Covers both the adapter-based rules and the scanner-backed rules'
        // regex fallbacks, which match (make-widget n) and (define v (compute 1))
        for (String spec : List.of("OperatorMutations.txt", "StatementMutation.txt")) {
            List<MutationPoint> points = Main.scanForMutations(dir, Mutator.MutatorType.CUSTOM, spec);
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

        List<MutationPoint> points = Main.scanForMutations(dir, Mutator.MutatorType.CUSTOM, "StatementMutation.txt")
                .stream().filter(p -> p.getCustomLabel().endsWith("statement → ;")).toList();

        // Only the discarded displayln calls in each body; the binding values
        // (+ a b), (add1 x), (sqr x), (lambda (n) n) etc. are used, so they stay.
        assertEquals(List.of(7, 11, 15, 18), points.stream().map(MutationPoint::getLineNumber).toList());
        for (MutationPoint p : points) {
            assertTrue(p.getOperator().startsWith("(displayln"), p.getOperator());
            assertEquals("(void)", p.getReplacement());
        }
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

        List<MutationPoint> points = RacketMutationScanner.scanFile(tmp, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        // (compute y) and (log-it x) are discarded; the binding value and the
        // last body forms are used
        assertEquals(List.of("(add1 x)", "(finish y)", "(result x)"),
                points.stream().map(MutationPoint::getOperator).toList());
    }

    @Test
    public void testEmptyLambdaParametersAndLetBindingsParse() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("empty-parens");
        java.nio.file.Path file = dir.resolve("empty.rkt");
        java.nio.file.Files.writeString(file, String.join("\n",
                "#lang racket",
                "(define (main d)",
                "  (hash-set! d 'k (lambda () \"v\"))",
                "  (lookup d))",
                "(define (scoped)",
                "  (let ()",
                "    (displayln 1)",
                "    (result)))",
                ""));

        // A lexer reads () as one token; before, (lambda () ...) didn't parse, the
        // function ended early and (hash-set! ...) lost its closing paren.
        List<MutationPoint> returns = RacketMutationScanner.scanFile(file.toFile(),
                Mutator.MutatorType.RVM_OBJECT_TO_NULL);
        assertEquals(List.of("(lookup d)", "(let ()\n    (displayln 1)\n    (result))"),
                returns.stream().map(p -> p.getOperator().replace("\r", "")).toList());

        List<MutationPoint> statements = Main.scanForMutations(dir, Mutator.MutatorType.CUSTOM, "StatementMutation.txt")
                .stream().filter(p -> p.getCustomLabel().endsWith("statement → ;")).toList();
        assertEquals(List.of("(hash-set! d 'k (lambda () \"v\"))", "(displayln 1)"),
                statements.stream().map(MutationPoint::getOperator).toList());
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

        List<MutationPoint> points = Main.scanForMutations(dir, Mutator.MutatorType.CUSTOM, "StatementMutation.txt")
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
}
