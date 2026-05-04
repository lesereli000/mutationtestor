package mutationtester;



import mutationtester.model.*;
import mutationtester.scanner.*;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PythonScannerTests {

    @Test
    public void testAdditiveMutation() throws IOException {
        File file = writeTempPythonFile("def add1(a):\n    return a + 1\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        assertEquals("+", points.get(0).getOperator());
        assertEquals("-", points.get(0).getReplacement());
    }

    @Test
    public void testSubtractiveMutation() throws IOException {
        File file = writeTempPythonFile("def subtract(a, b):\n    return a - b\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

        assertEquals(1, points.size());
        assertEquals("-", points.get(0).getOperator());
        assertEquals("+", points.get(0).getReplacement());
    }

    @Test
    public void testEqualityMutation() throws IOException {
        File file = writeTempPythonFile("def compare(a, b):\n    if a == b or a != 0:\n        return True\n    return False\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.EQ_NEGATE);

        assertEquals(2, points.size());
        assertTrue(points.stream().anyMatch(point -> point.getOperator().equals("==") && point.getReplacement().equals("!=")));
        assertTrue(points.stream().anyMatch(point -> point.getOperator().equals("!=") && point.getReplacement().equals("==")));
    }

    @Test
    public void testLogicalMutation() throws IOException {
        File file = writeTempPythonFile("def choose(a, b):\n    if a and b:\n        return True\n    return False\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_AND_TO_OR);

        assertEquals(1, points.size());
        assertEquals("and", points.get(0).getOperator());
        assertEquals("or", points.get(0).getReplacement());
    }

    @Test
    public void testTypeMutation() throws IOException {
        File file = writeTempPythonFile("x: int = 5\n\ndef add(a: int) -> int:\n    return a\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_FLOAT);

        assertEquals(3, points.size());
        assertTrue(points.stream().allMatch(point -> point.getOperator().equals("int")));
        assertTrue(points.stream().allMatch(point -> point.getReplacement().equals("float")));
    }

    @Test
    public void testMultiplyToDivideMutation() throws IOException {
        File file = writeTempPythonFile("def multiply(x, y):\n    return x * y\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE);

        assertEquals(1, points.size());
        assertEquals("*", points.get(0).getOperator());
        assertEquals("/", points.get(0).getReplacement());
    }

    @Test
    public void testDivideToMultiplyMutation() throws IOException {
        File file = writeTempPythonFile("def divide(x, y):\n    return x / y\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY);

        assertEquals(1, points.size());
        assertEquals("/", points.get(0).getOperator());
        assertEquals("*", points.get(0).getReplacement());
    }

    @Test
    public void testModuloToMultiplyMutation() throws IOException {
        File file = writeTempPythonFile("def modulo(x, y):\n    return x % y\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY);

        assertEquals(1, points.size());
        assertEquals("%", points.get(0).getOperator());
        assertEquals("*", points.get(0).getReplacement());
    }

    @Test
    public void testBitwiseAndToOr() throws IOException {
        File file = writeTempPythonFile("def bitwise_and(x, y):\n    return x & y\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.BOR_AND_TO_OR);

        assertEquals(1, points.size());
        assertEquals("&", points.get(0).getOperator());
        assertEquals("|", points.get(0).getReplacement());
    }

    @Test
    public void testBitwiseOrToAnd() throws IOException {
        File file = writeTempPythonFile("def bitwise_or(x, y):\n    return x | y\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.BOR_OR_TO_AND);

        assertEquals(1, points.size());
        assertEquals("|", points.get(0).getOperator());
        assertEquals("&", points.get(0).getReplacement());
    }

    @Test
    public void testRorLtToLe() throws IOException {
        File file = writeTempPythonFile("def is_below(a, b):\n    return a < b\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);

        assertEquals(1, points.size());
        assertEquals("<", points.get(0).getOperator());
        assertEquals("<=", points.get(0).getReplacement());
    }

    @Test
    public void testRorLeToLt() throws IOException {
        File file = writeTempPythonFile("def is_at_most(a, b):\n    return a <= b\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);

        assertEquals(1, points.size());
        assertEquals("<=", points.get(0).getOperator());
        assertEquals("<", points.get(0).getReplacement());
    }

    @Test
    public void testRorGtToGe() throws IOException {
        File file = writeTempPythonFile("def is_above(a, b):\n    return a > b\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GT_TO_GE);

        assertEquals(1, points.size());
        assertEquals(">", points.get(0).getOperator());
        assertEquals(">=", points.get(0).getReplacement());
    }

    @Test
    public void testRorGeToGt() throws IOException {
        File file = writeTempPythonFile("def is_at_least(a, b):\n    return a >= b\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.ROR_GE_TO_GT);

        assertEquals(1, points.size());
        assertEquals(">=", points.get(0).getOperator());
        assertEquals(">", points.get(0).getReplacement());
    }

    @Test
    public void testLogicalOrToAnd() throws IOException {
        File file = writeTempPythonFile("def either(a, b):\n    if a or b:\n        return True\n    return False\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.LOGICAL_OR_TO_AND);

        assertEquals(1, points.size());
        assertEquals("or", points.get(0).getOperator());
        assertEquals("and", points.get(0).getReplacement());
    }

    @Test
    public void testTypeFloatToInt() throws IOException {
        File file = writeTempPythonFile("def scale(a: float) -> float:\n    return a\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_FLOAT_TO_INT);

        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(point -> point.getOperator().equals("float")));
        assertTrue(points.stream().allMatch(point -> point.getReplacement().equals("int")));
    }

    @Test
    public void testUnsupportedMutationReturnsNoPoints() throws IOException {
        File file = writeTempPythonFile("x: int = 5\n");

        List<MutationPoint> points = PythonMutationScanner.scanFile(file, Mutator.MutatorType.TYPE_INT_TO_DOUBLE);

        assertTrue(points.isEmpty());
    }

    private File writeTempPythonFile(String content) throws IOException {
        Path path = Files.createTempFile("python-mutation-", ".py");
        Files.writeString(path, content);
        path.toFile().deleteOnExit();
        return path.toFile();
    }
}
