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