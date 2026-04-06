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