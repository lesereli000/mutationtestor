package mutationtester;

import mutationtester.coverage.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            specFile.toString()
        );

        assertEquals(1, points.size());
        assertEquals("RegisterDomainEvent(new ContributorNameUpdatedEvent(this));", points.get(0).getOperator());
        assertEquals(";", points.get(0).getReplacement());
        assertEquals("CSharp Custom Mutations: invocation statement \u2192 ;", points.get(0).getCustomLabel());
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
            specFile.toString()
        );

        assertEquals(2, points.size());
        assertEquals("string.IsNullOrEmpty(name)", points.get(0).getOperator());
        assertEquals("(name != null)", points.get(0).getReplacement());
        assertEquals("CSharp Custom Mutations: string.IsNullOrEmpty({quantity}) \u2192 {quantity} != null", points.get(0).getCustomLabel());
        assertEquals("string.IsNullOrEmpty(name)", points.get(1).getOperator());
        assertEquals("(name != \"\")", points.get(1).getReplacement());
        assertEquals("CSharp Custom Mutations: string.IsNullOrEmpty({quantity}) \u2192 {quantity} != \"\"", points.get(1).getCustomLabel());
    }

    @Test
    void testCustomMutationScanningForPitStyleScannerBackedRulesInCSharp() throws Exception {
        Path emptyReturnsFile = tempDir.resolve("PitEmptyReturnsDemo.cs");
        Files.writeString(emptyReturnsFile,
            "public sealed class PitEmptyReturnsDemo {\n" +
            "    public string Name() { return \"items\"; }\n" +
            "}\n"
        );

        Path constructorFile = tempDir.resolve("PitConstructorCallsDemo.cs");
        Files.writeString(constructorFile,
            "public sealed class PitConstructorCallsDemo {\n" +
            "    public Widget Create() { return new Widget(\"created\"); }\n" +
            "}\n" +
            "public sealed class Widget {\n" +
            "    public Widget(string name) { Name = name; }\n" +
            "    public string Name { get; }\n" +
            "}\n"
        );

        Path nonVoidFile = tempDir.resolve("PitNonVoidMethodCallsDemo.cs");
        Files.writeString(nonVoidFile,
            "public sealed class PitNonVoidMethodCallsDemo {\n" +
            "    public int Read() { return ComputeValue(); }\n" +
            "    private int ComputeValue() { return 9; }\n" +
            "}\n"
        );

        Path equalConditionFile = tempDir.resolve("PitRemoveEqualityConditionalsDemo.cs");
        Files.writeString(equalConditionFile,
            "public sealed class PitRemoveEqualityConditionalsDemo {\n" +
            "    public int Check(int value) { if (value == 10) { return 1; } return 0; }\n" +
            "}\n"
        );

        Path orderConditionFile = tempDir.resolve("PitRemoveOrderConditionalsDemo.cs");
        Files.writeString(orderConditionFile,
            "public sealed class PitRemoveOrderConditionalsDemo {\n" +
            "    public int Check(int value) { if (value > 10) { return 1; } return 0; }\n" +
            "}\n"
        );

        Path specFile = tempDir.resolve("pit-mutators.spec");
        Files.writeString(specFile,
            "PIT Built-in Mutations\n" +
            "    Replace: {return_value}\n" +
            "    With: {empty_value}\n" +
            "    Replace: {constructor_call}\n" +
            "    With: null\n" +
            "    Replace: {non_void_method_call}\n" +
            "    With: {default_value}\n" +
            "    Replace: {equality_condition}\n" +
            "    With: true\n" +
            "    Replace: {equality_condition}\n" +
            "    With: false\n" +
            "    Replace: {order_condition}\n" +
            "    With: true\n" +
            "    Replace: {order_condition}\n" +
            "    With: false\n" +
            "End"
        );

        List<MutationPoint> points = ReflectionUtils.invokeScanForMutations(
            tempDir,
            specFile.toString()
        );

        assertTrue(points.stream().anyMatch(p ->
                p.getCustomLabel().endsWith("return value \u2192 empty value")
                        && p.getOperator().equals("\"items\"")
                        && p.getReplacement().equals("\"\"")));
        assertTrue(points.stream().anyMatch(p ->
                p.getCustomLabel().endsWith("constructor call \u2192 null")
                        && p.getOperator().equals("new Widget(\"created\")")
                        && p.getReplacement().equals("null")));
        assertTrue(points.stream().anyMatch(p ->
                p.getCustomLabel().endsWith("non void method call \u2192 default value")
                        && p.getOperator().equals("ComputeValue()")
                        && p.getReplacement().equals("0")));
        assertTrue(points.stream().anyMatch(p ->
                p.getCustomLabel().endsWith("equality condition \u2192 true")
                        && p.getOperator().equals("value == 10")
                        && p.getReplacement().equals("true")));
        assertTrue(points.stream().anyMatch(p ->
                p.getCustomLabel().endsWith("equality condition \u2192 false")
                        && p.getOperator().equals("value == 10")
                        && p.getReplacement().equals("false")));
        assertTrue(points.stream().anyMatch(p ->
                p.getCustomLabel().endsWith("order condition \u2192 true")
                        && p.getOperator().equals("value > 10")
                        && p.getReplacement().equals("true")));
        assertTrue(points.stream().anyMatch(p ->
                p.getCustomLabel().endsWith("order condition \u2192 false")
                        && p.getOperator().equals("value > 10")
                        && p.getReplacement().equals("false")));
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
    static List<MutationPoint> invokeScanForMutations(Path sourceDir, String spec) throws Exception {
        java.lang.reflect.Method method = Main.class.getDeclaredMethod("scanForMutations",
            Path.class, String.class);
        method.setAccessible(true);
        return (List<MutationPoint>) method.invoke(null, sourceDir, spec);
    }
}
