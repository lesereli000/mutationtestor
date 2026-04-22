package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class EndToEndMSLTests {

    @TempDir
    Path tempDir;

    @Test
    void testCustomMutationScanningInJava() throws Exception {
        // 1. Create a sample Java file
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, 
            "public class Test {\n" +
            "    public int add(int a, int b) {\n" +
            "        return a + b;\n" +
            "    }\n" +
            "    public int sub(int a, int b) {\n" +
            "        return a - b;\n" +
            "    }\n" +
            "}\n"
        );

        // 2. Create a custom spec file
        Path specFile = tempDir.resolve("spec.txt");
        Files.writeString(specFile,
            "Arithmetic\n" +
            "    Replace: {quantity} + {quantity}\n" +
            "    With: {quantity} - {quantity}\n" +
            "    Replace: {quantity} - {quantity}\n" +
            "    With: {quantity} + {quantity}\n" +
            "End"
        );

        // 3. Run scanForMutations (we need to pass the directory containing the file)
        // Main.scanForMutations expects a directory
        List<MutationPoint> points = ReflectionUtils.invokeScanForMutations(
            tempDir, 
            Mutator.MutatorType.CUSTOM, 
            "AUTO", 
            specFile.toString()
        );

        // 4. Verify results
        assertEquals(2, points.size());
        
        MutationPoint p1 = points.get(0);
        assertEquals("+", p1.getOperator());
        assertEquals("-", p1.getReplacement());
        assertEquals("Arithmetic: + \u2192 -", p1.getCustomLabel());

        MutationPoint p2 = points.get(1);
        assertEquals("-", p2.getOperator());
        assertEquals("+", p2.getReplacement());
        assertEquals("Arithmetic: - \u2192 +", p2.getCustomLabel());
    }

    @Test
    void testCustomMutationScanningInPython() throws Exception {
        Path pyFile = tempDir.resolve("test.py");
        Files.writeString(pyFile, "x = 1 + 2\ny = 3 * 4");

        Path specFile = tempDir.resolve("spec.txt");
        Files.writeString(specFile,
            "PyMut\n" +
            "    Replace: {quantity} + {quantity}\n" +
            "    With: {quantity} - {quantity}\n" +
            "    Replace: {quantity} * {quantity}\n" +
            "    With: {quantity} / {quantity}\n" +
            "End"
        );

        List<MutationPoint> points = ReflectionUtils.invokeScanForMutations(
            tempDir, 
            Mutator.MutatorType.CUSTOM, 
            "AUTO", 
            specFile.toString()
        );

        assertEquals(2, points.size());
        assertEquals("PyMut: + \u2192 -", points.get(0).getCustomLabel());
        assertEquals("PyMut: * \u2192 /", points.get(1).getCustomLabel());
    }
}

/**
 * Helper to invoke private static method in Main for testing.
 */
class ReflectionUtils {
    @SuppressWarnings("unchecked")
    static List<MutationPoint> invokeScanForMutations(Path sourceDir, Mutator.MutatorType type, String strategy, String spec) throws Exception {
        java.lang.reflect.Method method = Main.class.getDeclaredMethod("scanForMutations", 
            Path.class, Mutator.MutatorType.class, String.class, String.class);
        method.setAccessible(true);
        return (List<MutationPoint>) method.invoke(null, sourceDir, type, strategy, spec);
    }
}
