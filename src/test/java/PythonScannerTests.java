import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PythonMutationScanner (.py files).
 *
 * Uses:
 *   samples/PytestProject/src/Add1.py      → def add1(a): return a + 1
 *   samples/PytestProject/src/Add2.py      → def add2(a): return a + 2
 *   samples/PytestProject/src/operators.py  → covers all mutation types
 */
public class PythonScannerTests {

    // -------------------------------------------------------------------------
    // AOR_PLUS_TO_MINUS
    // -------------------------------------------------------------------------

    @Test
    public void testAdd1PlusToMinus() throws IOException {
        File file = new File("samples/PytestProject/src/Add1.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testAdd2PlusToMinus() throws IOException {
        File file = new File("samples/PytestProject/src/Add2.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testOperatorsPlusToMinus() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        // operators.py has + in: add, typed_add, typed_float_add
        assertTrue(points.size() >= 3);
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
        File file = new File("samples/PytestProject/src/Add1.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        // Add1.py has no - operators
        assertEquals(0, points.size());
    }

    @Test
    public void testOperatorsMinusToPlus() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        // operators.py has one - in subtract function
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
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        // operators.py has > in: compare_gt, check_logic (x > 0 and y > 0), check_logic_or (x > 0 or y > 0)
        assertTrue(points.size() >= 1);
        for (MutationPoint p : points) {
            assertEquals(">", p.getOperator());
            assertEquals(">=", p.getReplacement());
        }
    }

    @Test
    public void testAdd1GTtoGE() throws IOException {
        File file = new File("samples/PytestProject/src/Add1.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // ROR_GE_TO_GT
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsGEtoGT() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GE_TO_GT);

        // operators.py has one >= in compare_ge
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
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);

        // operators.py has one < in compare_lt
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<", p.getOperator());
        assertEquals("<=", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // ROR_LE_TO_LT
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsLEtoLT() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);

        // operators.py has one <= in compare_le
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<=", p.getOperator());
        assertEquals("<", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // EQ_NEGATE
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsEqNegate() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        // operators.py has == in check_equal and != in check_not_equal
        assertTrue(points.size() >= 2);

        boolean foundEq = false;
        boolean foundNeq = false;
        for (MutationPoint p : points) {
            if (p.getOperator().equals("==")) {
                assertEquals("!=", p.getReplacement());
                foundEq = true;
            } else if (p.getOperator().equals("!=")) {
                assertEquals("==", p.getReplacement());
                foundNeq = true;
            }
        }
        assertTrue(foundEq, "Should find == -> != mutation");
        assertTrue(foundNeq, "Should find != -> == mutation");
    }

    @Test
    public void testAdd1EqNegate() throws IOException {
        File file = new File("samples/PytestProject/src/Add1.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // LOGICAL_AND_TO_OR
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsAndToOr() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        // operators.py has one 'and' in check_logic
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("and", p.getOperator());
        assertEquals("or", p.getReplacement());
    }

    @Test
    public void testAdd1AndToOr() throws IOException {
        File file = new File("samples/PytestProject/src/Add1.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // LOGICAL_OR_TO_AND
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsOrToAnd() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_OR_TO_AND);

        // operators.py has one 'or' in check_logic_or
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("or", p.getOperator());
        assertEquals("and", p.getReplacement());
    }

    // -------------------------------------------------------------------------
    // TYPE_INT_TO_FLOAT
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsIntToFloat() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);

        // operators.py has int annotations in typed_add: (a: int, b: int) -> int = 3
        assertTrue(points.size() >= 3);
        for (MutationPoint p : points) {
            assertEquals("int", p.getOperator());
            assertEquals("float", p.getReplacement());
        }
    }

    @Test
    public void testAdd1IntToFloat() throws IOException {
        File file = new File("samples/PytestProject/src/Add1.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);

        // Add1.py has no type annotations
        assertEquals(0, points.size());
    }

    // -------------------------------------------------------------------------
    // TYPE_FLOAT_TO_INT
    // -------------------------------------------------------------------------

    @Test
    public void testOperatorsFloatToInt() throws IOException {
        File file = new File("samples/PytestProject/src/operators.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);

        // operators.py has float annotations in typed_float_add: (a: float, b: float) -> float = 3
        assertTrue(points.size() >= 3);
        for (MutationPoint p : points) {
            assertEquals("float", p.getOperator());
            assertEquals("int", p.getReplacement());
        }
    }

    @Test
    public void testAdd1FloatToInt() throws IOException {
        File file = new File("samples/PytestProject/src/Add1.py");
        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);

        // Add1.py has no type annotations
        assertEquals(0, points.size());
    }
}
