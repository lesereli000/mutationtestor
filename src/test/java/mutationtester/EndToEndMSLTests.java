package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

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

    @Test
    void testCustomMutationScanningForRelationalAndLogicalOperatorsInJava() throws Exception {
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile,
            "public class Test {\n" +
            "    public boolean bigger(int a, int b) {\n" +
            "        return a > b;\n" +
            "    }\n" +
            "    public boolean both(boolean a, boolean b) {\n" +
            "        return a && b;\n" +
            "    }\n" +
            "}\n"
        );

        Path specFile = tempDir.resolve("spec.txt");
        Files.writeString(specFile,
            "Operators\n" +
            "    Replace: {quantity} > {quantity}\n" +
            "    With: {quantity} >= {quantity}\n" +
            "    Replace: {quantity} && {quantity}\n" +
            "    With: {quantity} || {quantity}\n" +
            "End"
        );

        List<MutationPoint> points = ReflectionUtils.invokeScanForMutations(
            tempDir,
            Mutator.MutatorType.CUSTOM,
            "AUTO",
            specFile.toString()
        );

        assertEquals(2, points.size());
        assertEquals(">", points.get(0).getOperator());
        assertEquals(">=", points.get(0).getReplacement());
        assertEquals("Operators: > \u2192 >=", points.get(0).getCustomLabel());
        assertEquals("&&", points.get(1).getOperator());
        assertEquals("||", points.get(1).getReplacement());
        assertEquals("Operators: && \u2192 ||", points.get(1).getCustomLabel());
    }

    @Test
    void testCustomMutationScanningForStatementMutationInCSharp() throws Exception {
        Path csFile = tempDir.resolve("Demo.cs");
        Files.writeString(csFile,
            "public class Demo {\n" +
            "    public void Run() {\n" +
            "        RegisterDomainEvent(new ContributorNameUpdatedEvent(this));\n" +
            "    }\n" +
            "}\n"
        );

        Path specFile = tempDir.resolve("csharp-spec.txt");
        Files.writeString(specFile,
            "CSharp Custom Mutations\n" +
            "    Replace: {invocation_statement}\n" +
            "    With: ;\n" +
            "End"
        );

        List<MutationPoint> points = ReflectionUtils.invokeScanForMutations(
            tempDir,
            Mutator.MutatorType.CUSTOM,
            "AUTO",
            specFile.toString()
        );

        assertEquals(1, points.size());
        assertEquals("RegisterDomainEvent(new ContributorNameUpdatedEvent(this));", points.get(0).getOperator());
        assertEquals(";", points.get(0).getReplacement());
        assertEquals("CSharp Custom Mutations: statement \u2192 ;", points.get(0).getCustomLabel());
    }

    @Test
    void testCustomMutationScanningForStringMutationsInCSharp() throws Exception {
        Path csFile = tempDir.resolve("Demo.cs");
        Files.writeString(csFile,
            "public class Demo {\n" +
            "    public bool Check(string name) {\n" +
            "        return string.IsNullOrEmpty(name);\n" +
            "    }\n" +
            "}\n"
        );

        Path specFile = tempDir.resolve("csharp-spec.txt");
        Files.writeString(specFile,
            "CSharp Custom Mutations\n" +
            "    Replace: string.IsNullOrEmpty({quantity})\n" +
            "    With: {quantity} != null\n" +
            "\n" +
            "    Replace: string.IsNullOrEmpty({quantity})\n" +
            "    With: {quantity} != \"\"\n" +
            "End"
        );

        List<MutationPoint> points = ReflectionUtils.invokeScanForMutations(
            tempDir,
            Mutator.MutatorType.CUSTOM,
            "AUTO",
            specFile.toString()
        );

        assertEquals(2, points.size());
        assertEquals("string.IsNullOrEmpty(name)", points.get(0).getOperator());
        assertEquals("(name != null)", points.get(0).getReplacement());
        assertEquals("CSharp Custom Mutations: string.IsNullOrEmpty \u2192 not null", points.get(0).getCustomLabel());
        assertEquals("string.IsNullOrEmpty(name)", points.get(1).getOperator());
        assertEquals("(name != \"\")", points.get(1).getReplacement());
        assertEquals("CSharp Custom Mutations: string.IsNullOrEmpty \u2192 not empty", points.get(1).getCustomLabel());
    }

    @Test
    void testCustomMutationScanningInRacket() throws Exception {
        Path rktFile = tempDir.resolve("test.rkt");
        Files.writeString(rktFile,
                "#lang racket\n" +
                        "(define (add x y) (+ x y))\n" +
                        "(define (mul x y) (* x y))\n"
        );

        Path specFile = tempDir.resolve("spec.txt");
        Files.writeString(specFile,
                "RktMut\n" +
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
        assertEquals("+", points.get(0).getOperator());
        assertEquals("-", points.get(0).getReplacement());
        assertEquals("RktMut: + \u2192 -", points.get(0).getCustomLabel());
        assertEquals("*", points.get(1).getOperator());
        assertEquals("/", points.get(1).getReplacement());
        assertEquals("RktMut: * \u2192 /", points.get(1).getCustomLabel());
    }
}

/**
 * Helper to invoke private static method in Main for testing.
 */
class ReflectionUtils {
    @SuppressWarnings("unchecked")
    static List<MutationPoint> invokeScanForMutations(Path sourceDir, Mutator.MutatorType type, String strategy, String spec) throws Exception {
        java.lang.reflect.Method method = Main.class.getDeclaredMethod("scanForMutations",
            Path.class, Mutator.MutatorType.class, String.class);
        method.setAccessible(true);
        return (List<MutationPoint>) method.invoke(null, sourceDir, type, spec);
    }
}
