import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SchemeMutationScanner (Racket/.rkt files).
 *
 * Uses:
 *   samples/SchemeProject/project/src/add1.rkt      → (+ 1 x)
 *   samples/SchemeProject/project/src/add2.rkt      → (+ 2 x)
 *   samples/SchemeProject/project/src/operators.rkt → covers all mutation types
 */
public class SchemeScannerTests {

    // -------------------------------------------------------------------------
    // AOR_PLUS_TO_MINUS
    // -------------------------------------------------------------------------

    @Test
    public void testAdd1PlusToMinus() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
        assertEquals(6, p.getLineNumber());
    }

    @Test
    public void testAdd2PlusToMinus() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add2.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
        assertEquals(6, p.getLineNumber());
    }

    @Test
    public void testOperatorsPlusToMinus() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

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
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        // add1.rkt has no - operators
        assertEquals(0, points.size());
    }

    @Test
    public void testOperatorsMinusToPlus() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

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
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        // operators.rkt has (> x y) in compare-gt, and two (> x 0) in check-logic
        assertEquals(5, points.size());
        for (MutationPoint p : points) {
            assertEquals(">", p.getOperator());
            assertEquals(">=", p.getReplacement());
        }
    }

    @Test
    public void testAdd1GTtoGE() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // ROR_GE_TO_GT
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsGEtoGT() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GE_TO_GT);

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
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);

        // operators.rkt has one (< x y) in compare-lt
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<", p.getOperator());
        assertEquals("<=", p.getReplacement());
    }

    @Test
    public void testAdd1LTtoLE() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);

        assertEquals(0, points.size());
    }

// -------------------------------------------------------------------------
// ROR_LE_TO_LT
// -------------------------------------------------------------------------

    @Test
    public void testOperatorsLEtoLT() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);

        // operators.rkt has one (<= x y) in compare-le
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<=", p.getOperator());
        assertEquals("<", p.getReplacement());
    }

    @Test
    public void testAdd1LEtoLT() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // EQ_NEGATE
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsEqNegate() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        // operators.rkt has one (= x y) in check-equal
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("=", p.getOperator());
        assertEquals("not =", p.getReplacement());
    }

    @Test
    public void testAdd1EqNegate() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // LOGICAL_AND_TO_OR
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsAndToOr() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        // operators.rkt has one (and ...) in check-logic
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("and", p.getOperator());
        assertEquals("or", p.getReplacement());
    }

    @Test
    public void testAdd1AndToOr() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // LOGICAL_OR_TO_AND
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsOrToAnd() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_OR_TO_AND);

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
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.NLT_INTEGER_TO_FLOAT);

        // add1.rkt has one integer literal: 1 in (+ 1 x)
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("1", p.getOperator());
        assertEquals("1.0", p.getReplacement());
    }

    @Test
    public void testAdd2IntegerToFloat() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add2.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.NLT_INTEGER_TO_FLOAT);

        // add2.rkt has one integer literal: 2 in (+ 2 x)
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("2", p.getOperator());
        assertEquals("2.0", p.getReplacement());
    }

    @Test
    public void testOperatorsIntegerToFloat() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.NLT_INTEGER_TO_FLOAT);

        // operators.rkt has integers: 0 (twice in check-logic), 5, 10 in check-floats
        assertEquals(6, points.size());
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
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.NLT_FLOAT_TO_INTEGER);

        // add1.rkt has no float literals
        assertEquals(0, points.size());
    }

    @Test
    public void testOperatorsFloatToInteger() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.NLT_FLOAT_TO_INTEGER);

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
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

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
        File file = new File("samples/SchemeProject/project/src/typed-functions.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);

        // typed-functions.rkt has 4 Integer type annotations
        assertEquals(4, points.size());
        for (MutationPoint p : points) {
            assertEquals("Integer", p.getOperator());
            assertEquals("Float", p.getReplacement());
        }
    }

    @Test
    public void testOperatorsIntToFloat() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);

        // operators.rkt has no type annotations (not Typed Racket)
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // TYPE_FLOAT_TO_INT
    // -------------------------------------------------------------------------

    @Test
    public void testTypedFunctionsFloatToInt() throws IOException {
        File file = new File("samples/SchemeProject/project/src/typed-functions.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);

        // typed-functions.rkt has 3 Float type annotations
        assertEquals(3, points.size());
        for (MutationPoint p : points) {
            assertEquals("Float", p.getOperator());
            assertEquals("Integer", p.getReplacement());
        }
    }

    @Test
    public void testOperatorsFloatToInt() throws IOException {
        File file = new File("samples/SchemeProject/project/src/operators.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);

        // operators.rkt has no type annotations (not Typed Racket)
        assertEquals(0, points.size());
    }
}