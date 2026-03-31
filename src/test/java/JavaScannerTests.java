import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

public class JavaScannerTests {

    @Test
    public void testAdd1() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/Add1.java");
        List<MutationPoint> points = JavaMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS, false);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(6, p.getLineNumber());
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testAdd2() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/Add2.java");
        List<MutationPoint> points = JavaMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS, false);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(6, p.getLineNumber());
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testAdd3() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/Add3.java");
        List<MutationPoint> points = JavaMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS, false);
        assertEquals(2, points.size());
        MutationPoint p = points.get(0);
        assertEquals(7, p.getLineNumber());
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testMain() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/Main.java");
        List<MutationPoint> points = JavaMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS, false);
        assertEquals(0, points.size());
    }

    @Test
    public void testPrototypeSample() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/prototypeSample.java");

        // AOR_PLUS_TO_MINUS
        List<MutationPoint> plusPoints = JavaMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS,
                false);
        assertEquals(8, plusPoints.size());

        // AOR_MINUS_TO_PLUS
        List<MutationPoint> minusPoints = JavaMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS,
                false);
        assertEquals(4, minusPoints.size());

        // ROR_GT_TO_GE
        List<MutationPoint> gtPoints = JavaMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE, false);
        assertEquals(9, gtPoints.size());

        // ROR_GE_TO_GT
        List<MutationPoint> gePoints = JavaMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GE_TO_GT, false);
        assertEquals(2, gePoints.size());

        // LOGICAL_AND_TO_OR
        List<MutationPoint> andPoints = JavaMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR,
                false);
        assertEquals(2, andPoints.size());

        // LOGICAL_OR_TO_AND
        List<MutationPoint> orPoints = JavaMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_OR_TO_AND, false);
        assertEquals(2, orPoints.size());
    }

    @Test
    public void testTypeSwappingAdd1() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/Add1.java");
        List<MutationPoint> points = JavaMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_LONG, false);
        // Expecting 2: parameter 'x' and return type 'int'
        assertEquals(2, points.size());
    }

    @Test
    public void testTypeSwappingAdd2() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/Add2.java");
        List<MutationPoint> points = JavaMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_LONG, false);
        // Expecting 3: parameter 'x', local 'y', and return type 'int'
        assertEquals(3, points.size());
    }

    @Test
    public void testTypeSwappingAdd3() throws IOException {
        File file = new File("samples/MavenProject/src/main/java/Add3.java");

        // int -> double: Expecting 2 (return type and cast)
        List<MutationPoint> intToDouble = JavaMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_DOUBLE,
                false);
        assertEquals(2, intToDouble.size());

        // double -> int: Expecting 2 (parameter x and local z)
        List<MutationPoint> doubleToInt = JavaMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_DOUBLE_TO_INT,
                false);
        assertEquals(2, doubleToInt.size());

        // float -> int: Expecting 1 (local y)
        List<MutationPoint> floatToInt = JavaMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT,
                false);
        assertEquals(1, floatToInt.size());
    }

}