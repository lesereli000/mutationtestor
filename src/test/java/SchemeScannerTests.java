import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RacketMutationScanner.
 *
 * Each test scans a real .rkt sample file and asserts:
 *   - the correct number of mutation points are found
 *   - the operator and replacement are correct
 *   - line numbers are correct
 *
 * Sample files used:
 *   samples/RacketProject/project/src/add1.rkt  → (+ 1 x)
 *   samples/RacketProject/project/src/add2.rkt  → (+ 2 x)
 *   samples/Demo.rkt                            → multiple operators, comments, strings
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
    public void testDemoPlusToMinus() throws IOException {
        File file = new File("samples/Demo.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        // Demo.rkt has: (+ a b), (+ (+ a b) c), (+ 1 2 3)
        // + inside string "String has +: 2+3" and comments should NOT be counted
        assertEquals(4, points.size());
        for (MutationPoint p : points) {
            assertEquals("+", p.getOperator());
            assertEquals("-", p.getReplacement());
        }
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
    public void testAdd2MinusToPlus() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add2.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // ROR_GT_TO_GE
    // -------------------------------------------------------------------------

    @Test
    public void testDemoGTtoGE() throws IOException {
        File file = new File("samples/Demo.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        // Demo.rkt has one (> a equal) in GTtoGE function
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(">", p.getOperator());
        assertEquals(">=", p.getReplacement());
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
    public void testDemoGEtoGT() throws IOException {
        File file = new File("samples/Demo.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GE_TO_GT);

        // Demo.rkt has one (>= a equal) in GEtoGT function
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(">=", p.getOperator());
        assertEquals(">", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // EQ_NEGATE
    // -------------------------------------------------------------------------

    @Test
    public void testAdd1EqNegate() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        // add1.rkt has no = operators
        assertEquals(0, points.size());
    }

    @Test
    public void testDemoEqNegate() throws IOException {
        File file = new File("samples/Demo.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        // Demo.rkt has no (= ...) forms — update count if you add = to Demo.rkt
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // LOGICAL_AND_TO_OR / LOGICAL_OR_TO_AND
    // -------------------------------------------------------------------------

    @Test
    public void testAdd1AndToOr() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        assertEquals(0, points.size());
    }

    @Test
    public void testAdd1OrToAnd() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add1.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_OR_TO_AND);

        assertEquals(0, points.size());
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
    public void testDemoIntegerToFloat() throws IOException {
        File file = new File("samples/Demo.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.NLT_INTEGER_TO_FLOAT);

        // Demo.rkt has integers: 5 (twice in GTtoGE), 5 (twice in GEtoGT), 2, 3, 1, 2, 3, 1, 2, 3
        // Exact count depends on Demo.rkt content — adjust after first run
        assertTrue(points.size() > 0);
        for (MutationPoint p : points) {
            assertTrue(p.getReplacement().endsWith(".0"));
            assertFalse(p.getOperator().contains("."));
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
    public void testAdd2FloatToInteger() throws IOException {
        File file = new File("samples/SchemeProject/project/src/add2.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.NLT_FLOAT_TO_INTEGER);

        // add2.rkt has no float literals
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // Verify strings and comments are never scanned
    // -------------------------------------------------------------------------

    @Test
    public void testStringOperatorsNotMutated() throws IOException {
        // Demo.rkt has "String has +: 2+3" — the + inside should not be flagged
        File file = new File("samples/Demo.rkt");
        List<MutationPoint> points = SchemeMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        // All found points should be on lines with real code, not the string line
        for (MutationPoint p : points) {
            assertNotEquals(13, p.getLineNumber()); // line 13 is the displayln with string
        }
    }
}
