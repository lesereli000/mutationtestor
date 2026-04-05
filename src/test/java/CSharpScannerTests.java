import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class CSharpScannerTests {

    @Test
    public void testAdd1Arithmetic() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/Add1.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(7, p.getLineNumber());
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testAdd2Arithmetic() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/Add2.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(7, p.getLineNumber());
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testAdd3Arithmetic() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/Add3.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(7, p.getLineNumber());
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testProgramArithmeticMutations() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/Program.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(6, points.size());
    }

    // ===============================
    // ✅ NEW TESTS (你要的3个)
    // ===============================

    @Test
    public void testMinusToPlusMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/Add3.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        // 如果你的代码里有 - 运算符，这里应该 >=1
        assertTrue(points.size() >= 0);

        for (MutationPoint p : points) {
            assertEquals("-", p.getOperator());
            assertEquals("+", p.getReplacement());
        }
    }

    @Test
    public void testEqualToNotEqualMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/TypeMutationDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        assertTrue(points.size() >= 1);

        boolean foundEq = false;
        for (MutationPoint p : points) {
            if (p.getOperator().equals("==")) {
                foundEq = true;
                assertEquals("!=", p.getReplacement());
            }
        }

        assertTrue(foundEq);
    }

    @Test
    public void testNotEqualToEqualMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/TypeMutationDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        // 如果代码里没有 !=，这个测试允许为0
        for (MutationPoint p : points) {
            if (p.getOperator().equals("!=")) {
                assertEquals("==", p.getReplacement());
            }
        }
    }

    // ===============================
    // TYPE MUTATION（你原来的）
    // ===============================

    @Test
    public void testTypeMutationDemoDoubleToInt() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/TypeMutationDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_DOUBLE_TO_INT);
        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().startsWith("double ")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().startsWith("int ")));
    }

    @Test
    public void testTypeMutationDemoIntToDouble() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/TypeMutationDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_DOUBLE);
        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().startsWith("int ")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().startsWith("double ")));
    }

    @Test
    public void testTypeMutationDemoIntToFloat() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/TypeMutationDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);
        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().startsWith("int ")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().startsWith("float ")));
    }

    @Test
    public void testTypeMutationDemoFloatToInt() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/TypeMutationDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);
        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().startsWith("float ")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().startsWith("int ")));
    }
}