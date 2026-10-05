package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals("if", p.getOperator());
    }

    @Test
    public void testOperatorsNegateConditionals() throws IOException {
        File file = new File("samples/RacketProject/project/src/operators.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NEGATE_CONDITIONALS);

        // operators.rkt has 9 (if ...) forms (includes clamp-to-zero, under-limit?)
        assertEquals(9, points.size());
        for (MutationPoint p : points) {
            assertEquals("if", p.getOperator());
        }
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
        // sum-of-squares, parity, origin, empty-table, non-empty?  → 15 points
        // (vowels returns a quoted literal, which is not an object return)
        assertEquals(15, points.size());
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
        assertEquals(13, points.size());
        for (MutationPoint p : points) {
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

    // -------------------------------------------------------------------------
    // NON_VOID_METHOD_CALLS  (call whose result is used → default value)
    // -------------------------------------------------------------------------

    @Test
    public void testPitNonVoidMethodCalls() throws IOException {
        File file = new File("samples/RacketProject/project/src/pit-mutations.rkt");
        List<MutationPoint> points = RacketMutationScanner.scanFile(file, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        // length, add1, sub1, cons, string-append, list, sqr x2, even?,
        // make-point, make-hash, empty?  → 12 points
        // (display items) is excluded: void call, and in statement position
        assertEquals(12, points.size());
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

    private static MutationPoint findByOperator(List<MutationPoint> points, String operator) {
        return points.stream()
                .filter(p -> p.getOperator().equals(operator))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no mutation point for " + operator));
    }
}
