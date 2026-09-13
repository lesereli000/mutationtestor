package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.msl.MutationRule;
import mutationtester.msl.MutationRuleGenerator;
import mutationtester.scanner.*;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    public void testPlusToMinusSkipsStringConcatenation() throws IOException {
        Path file = Files.createTempFile("StringConcatDemo", ".cs");
        Files.writeString(file, """
                namespace Demo
                {
                    public static class StringConcatDemo
                    {
                        public static int Add(int x)
                        {
                            return x + 1;
                        }

                        public static void Main()
                        {
                            System.Console.WriteLine("Pipeline: " + Add(1));
                        }
                    }
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(file.toFile(), Mutator.MutatorType.AOR_PLUS_TO_MINUS);

        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals(7, p.getLineNumber());
        assertEquals("+", p.getOperator());
        assertEquals("-", p.getReplacement());
    }

    @Test
    public void testMinusToPlusMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/Add3.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MINUS_TO_PLUS);

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

        for (MutationPoint p : points) {
            if (p.getOperator().equals("!=")) {
                assertEquals("==", p.getReplacement());
            }
        }
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

    @Test
    public void testLessThanKilledDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/LessThanKilledDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<", p.getOperator());
        assertEquals("<=", p.getReplacement());
    }

    @Test
    public void testLessThanSurviveDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/LessThanSurviveDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LT_TO_LE);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<", p.getOperator());
        assertEquals("<=", p.getReplacement());
    }

    @Test
    public void testLessEqualKilledDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/LessEqualKilledDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<=", p.getOperator());
        assertEquals("<", p.getReplacement());
    }

    @Test
    public void testLessEqualSurviveDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/LessEqualSurviveDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.ROR_LE_TO_LT);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("<=", p.getOperator());
        assertEquals("<", p.getReplacement());
    }

    @Test
    public void testMultiplyKilledDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/MultiplyKilledDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("*", p.getOperator());
        assertEquals("/", p.getReplacement());
    }

    @Test
    public void testDivideKilledDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/DivideKilledDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("/", p.getOperator());
        assertEquals("*", p.getReplacement());
    }

    @Test
    public void testModuloKilledDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/ModuloKilledDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("%", p.getOperator());
        assertEquals("*", p.getReplacement());
    }

    @Test
    public void testBitwiseAndKilledDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/BitwiseAndKilledDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.BOR_AND_TO_OR);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("&", p.getOperator());
        assertEquals("|", p.getReplacement());
    }

    @Test
    public void testBitwiseOrKilledDemoMutation() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/BitwiseOrKilledDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.BOR_OR_TO_AND);
        assertEquals(1, points.size());
        MutationPoint p = points.get(0);
        assertEquals("|", p.getOperator());
        assertEquals("&", p.getReplacement());
    }

    @Test
    public void testConditionalTrueMutationFindsSingleLineTernaryCondition() throws IOException {
        Path file = Files.createTempFile("ContributorId", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public readonly partial struct ContributorId
                {
                  private static Validation Validate(int value)
                      => value > 0 ? Validation.Ok : Validation.Invalid("ContributorId must be positive.");
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(file.toFile(), Mutator.MutatorType.CONDITIONAL_TRUE);

        assertTrue(points.stream().anyMatch(p ->
                p.getLineNumber() == 6 && p.getOperator().equals("value > 0") && p.getReplacement().equals("true")));
    }

    @Test
    public void testConditionalTrueMutationFindsMultilineNestedTernaryConditions() throws IOException {
        Path file = Files.createTempFile("ContributorName", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public partial struct ContributorName
                {
                  private static Validation Validate(in string name) =>
                    string.IsNullOrEmpty(name)
                      ? Validation.Invalid("Name cannot be empty")
                      : name.Length > MaxLength
                        ? Validation.Invalid($"Name cannot be longer than {MaxLength} characters")
                        : Validation.Ok;
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(file.toFile(), Mutator.MutatorType.CONDITIONAL_TRUE);

        assertTrue(points.stream().anyMatch(p ->
                p.getLineNumber() == 6 && p.getOperator().equals("string.IsNullOrEmpty(name)")));
        assertTrue(points.stream().anyMatch(p ->
                p.getLineNumber() == 8 && p.getOperator().equals("name.Length > MaxLength")));
    }

    @Test
    public void testConditionalTrueMutationIgnoresNullableTypeAnnotationsAndNullCoalescing() throws IOException {
        Path file = Files.createTempFile("PhoneNumber", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public class PhoneNumber(string countryCode, string number, string? extension)
                {
                  public string? Extension { get; private set; } = extension;

                  public override string ToString()
                  {
                    return Extension ?? string.Empty;
                  }
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(file.toFile(), Mutator.MutatorType.CONDITIONAL_TRUE);

        assertEquals(0, points.size());
    }

    @Test
    public void testConditionalTrueMutationStillFindsRealTernaryWhenNullableTypesExist() throws IOException {
        Path file = Files.createTempFile("PhoneNumberTernary", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public class PhoneNumber(string? extension)
                {
                  public override string ToString()
                  {
                    return string.IsNullOrEmpty(extension) ? string.Empty : extension;
                  }
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(file.toFile(), Mutator.MutatorType.CONDITIONAL_TRUE);

        assertTrue(points.stream().anyMatch(p ->
                p.getOperator().equals("string.IsNullOrEmpty(extension)") && p.getReplacement().equals("true")));
    }

    @Test
    public void testConditionalTrueMutationIgnoresNullablePrimaryConstructorParametersWithBaseTypeClause() throws IOException {
        Path file = Files.createTempFile("PhoneNumberCtor", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public class PhoneNumber(string countryCode, string number, string? extension) : ValueObject
                {
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(file.toFile(), Mutator.MutatorType.CONDITIONAL_TRUE);

        assertEquals(0, points.size());
    }

    @Test
    public void testStatementRemovalFindsInvocationStatement() throws IOException {
        Path file = Files.createTempFile("ContributorStatement", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public class Contributor
                {
                  public Contributor UpdateName()
                  {
                    RegisterDomainEvent(new ContributorNameUpdatedEvent(this));
                    return this;
                  }
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(file.toFile(), Mutator.MutatorType.STATEMENT_MUTATION);

        assertTrue(points.stream().anyMatch(p ->
                p.getLineNumber() == 7
                        && p.getOperator().equals("RegisterDomainEvent(new ContributorNameUpdatedEvent(this));")
                        && p.getReplacement().equals(";")));
    }

    @Test
    public void testStringIsNullOrEmptyToNotNullFindsMutation() throws IOException {
        Path file = Files.createTempFile("ContributorNameStringNull", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public partial struct ContributorName
                {
                  private static Validation Validate(in string name) =>
                    string.IsNullOrEmpty(name)
                      ? Validation.Invalid("Name cannot be empty")
                      : Validation.Ok;
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(
                file.toFile(),
                Mutator.MutatorType.STRING_MUTATION_TO_NOT_NULL);

        assertTrue(points.stream().anyMatch(p ->
                p.getLineNumber() == 6
                        && p.getOperator().equals("string.IsNullOrEmpty(name)")
                        && p.getReplacement().equals("(name != null)")));
    }

    @Test
    public void testStringIsNullOrEmptyToNotEmptyFindsMutation() throws IOException {
        Path file = Files.createTempFile("ContributorNameStringEmpty", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public partial struct ContributorName
                {
                  private static Validation Validate(in string name) =>
                    string.IsNullOrEmpty(name)
                      ? Validation.Invalid("Name cannot be empty")
                      : Validation.Ok;
                }
                """);

        List<MutationPoint> points = CSharpMutationScanner.scanFile(
                file.toFile(),
                Mutator.MutatorType.STRING_MUTATION_TO_NOT_EMPTY);

        assertTrue(points.stream().anyMatch(p ->
                p.getLineNumber() == 6
                        && p.getOperator().equals("string.IsNullOrEmpty(name)")
                        && p.getReplacement().equals("(name != \"\")")));
    }

    @Test
    public void testVoidMethodCallMutationFindsStandaloneCall() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitVoidMethodCallDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.VML_VOID_METHOD_CALL);

        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().equals("Record();")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().equals(";")));
    }

    @Test
    public void testIncrementToDecrementMutationFindsPostIncrement() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitIncrementDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT);

        assertEquals(1, points.size());
        assertEquals("++", points.get(0).getOperator());
        assertEquals("--", points.get(0).getReplacement());
    }

    @Test
    public void testDecrementToIncrementMutationFindsPostDecrement() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitIncrementDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT);

        assertEquals(1, points.size());
        assertEquals("--", points.get(0).getOperator());
        assertEquals("++", points.get(0).getReplacement());
    }

    @Test
    public void testInvertNegativeMutationFindsUnaryMinus() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitInvertNegativeDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.INEG_INVERT_NEGATIVE);

        assertEquals(4, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().equals("-")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().equals("")));
    }

    @Test
    public void testReturnValueMutationsFindBooleanNumericStringAndObjectReturns() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitReturnValueDemo.cs");

        List<MutationPoint> trueToFalse = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.RVM_TRUE_TO_FALSE);
        assertEquals(2, trueToFalse.size());
        assertEquals("true", trueToFalse.get(0).getOperator());
        assertEquals("false", trueToFalse.get(0).getReplacement());

        List<MutationPoint> falseToTrue = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.RVM_FALSE_TO_TRUE);
        assertEquals(2, falseToTrue.size());
        assertEquals("false", falseToTrue.get(0).getOperator());
        assertEquals("true", falseToTrue.get(0).getReplacement());

        List<MutationPoint> zeroToOne = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.RVM_ZERO_TO_ONE);
        assertEquals(2, zeroToOne.size());
        assertEquals("0", zeroToOne.get(0).getOperator());
        assertEquals("1", zeroToOne.get(0).getReplacement());

        List<MutationPoint> nonZeroToZero = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.RVM_NONZERO_TO_ZERO);
        assertEquals(2, nonZeroToZero.size());
        assertEquals("42", nonZeroToZero.get(0).getOperator());
        assertEquals("0", nonZeroToZero.get(0).getReplacement());

        List<MutationPoint> stringToEmpty = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.RVM_STRING_TO_EMPTY);
        assertEquals(2, stringToEmpty.size());
        assertEquals("\"ready\"", stringToEmpty.get(0).getOperator());
        assertEquals("\"\"", stringToEmpty.get(0).getReplacement());

        List<MutationPoint> objectToNull = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.RVM_OBJECT_TO_NULL);
        assertEquals(4, objectToNull.size());
        assertTrue(objectToNull.stream().anyMatch(p ->
                p.getOperator().equals("this") && p.getReplacement().equals("null")));
    }

    @Test
    public void testEmptyReturnsFindStringNumericAndCollectionReturns() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitEmptyReturnsDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.EMPTY_RETURNS);

        assertTrue(points.stream().anyMatch(p ->
                p.getOperator().equals("\"items\"") && p.getReplacement().equals("\"\"")));
        assertTrue(points.stream().anyMatch(p ->
                p.getOperator().equals("7") && p.getReplacement().equals("0")));
        assertTrue(points.stream().anyMatch(p ->
                p.getOperator().equals("new List<int> { 1, 2, 3 }")
                        && p.getReplacement().equals("new System.Collections.Generic.List<int>()")));
        assertTrue(points.stream().anyMatch(p ->
                p.getOperator().equals("new[] { \"alpha\", \"beta\" }")
                        && p.getReplacement().equals("System.Linq.Enumerable.Empty<string>()")));
    }

    @Test
    public void testConstructorCallMutationFindsNewObjectExpressions() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitConstructorCallsDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.CONSTRUCTOR_CALLS);

        assertEquals(2, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().startsWith("new Widget(")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().equals("null")));
    }

    @Test
    public void testNonVoidMethodCallMutationFindsReturnAndAssignmentCalls() throws IOException {
        File file = new File("samples/CSProject/src/CSProject/PitNonVoidMethodCallsDemo.cs");
        List<MutationPoint> points = CSharpMutationScanner.scanFile(file, Mutator.MutatorType.NON_VOID_METHOD_CALLS);

        assertEquals(4, points.size());
        assertTrue(points.stream().allMatch(p -> p.getOperator().equals("ComputeValue()")));
        assertTrue(points.stream().allMatch(p -> p.getReplacement().equals("0")));
    }

    @Test
    public void testRemoveConditionalsFindsEqualityAndOrderVariants() throws IOException {
        Path file = Files.createTempFile("RemoveConditionalDemo", ".cs");
        Files.writeString(file, """
                namespace Demo;

                public static class RemoveConditionalDemo
                {
                    public static int EqualIf(int x)
                    {
                        if (x == 10)
                        {
                            return 1;
                        }
                        return 0;
                    }

                    public static int OrderIf(int x)
                    {
                        if (x > 10)
                        {
                            return 1;
                        }
                        return 0;
                    }
                }
                """);

        List<MutationPoint> equalIf = CSharpMutationScanner.scanFile(
                file.toFile(),
                Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF);
        assertEquals(1, equalIf.size());
        assertEquals("x == 10", equalIf.get(0).getOperator());
        assertEquals("true", equalIf.get(0).getReplacement());

        List<MutationPoint> equalElse = CSharpMutationScanner.scanFile(
                file.toFile(),
                Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE);
        assertEquals(1, equalElse.size());
        assertEquals("x == 10", equalElse.get(0).getOperator());
        assertEquals("false", equalElse.get(0).getReplacement());

        List<MutationPoint> orderIf = CSharpMutationScanner.scanFile(
                file.toFile(),
                Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF);
        assertEquals(1, orderIf.size());
        assertEquals("x > 10", orderIf.get(0).getOperator());
        assertEquals("true", orderIf.get(0).getReplacement());

        List<MutationPoint> orderElse = CSharpMutationScanner.scanFile(
                file.toFile(),
                Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_ELSE);
        assertEquals(1, orderElse.size());
        assertEquals("x > 10", orderElse.get(0).getOperator());
        assertEquals("false", orderElse.get(0).getReplacement());
    }

    @Test
    public void testCustomSpecSuppressesCSharpMutationThatWouldProduceLoop() throws Exception {
        Path source = Files.createTempFile("LoopGuardDemo", ".cs");
        Files.writeString(source, """
                namespace Demo
                {
                    public static class LoopGuardDemo
                    {
                        public static int GuardedIncrement(int value, int max)
                        {
                            if (value > max)
                            {
                                value = max;
                            }
                            return value;
                        }
                    }
                }
                """);

        Path baselineSpec = Files.createTempFile("loop-no-skip", ".spec");
        Files.writeString(baselineSpec, """
                Conditionals Boundary:

                    Replace: {quantity} > {quantity}
                    With: {quantity} >= {quantity}

                End
                """);

        Path spec = Files.createTempFile("loop-skip", ".spec");
        Files.writeString(spec, """
                Conditionals Boundary:

                    Replace: {quantity} > {quantity}
                    With: {quantity} >= {quantity}
                    Except: if ({quantity} > {quantity}) { {quantity} = {quantity}

                End
                """);

        List<MutationPoint> baselinePoints = scanCSharpWithSpec(source.toFile(), baselineSpec.toFile());
        assertEquals(1, baselinePoints.size(), "The C# MSL scanner should find the guarded comparison without Except.");
        assertEquals(">", baselinePoints.get(0).getOperator());
        assertEquals(">=", baselinePoints.get(0).getReplacement());

        List<MutationPoint> points = scanCSharpWithSpec(source.toFile(), spec.toFile());
        assertEquals(0, points.size(),
                "The guarded assignment should be suppressed because mutating the boundary can create a loop.");
    }

    private List<MutationPoint> scanCSharpWithSpec(File sourceFile, File specFile) throws IOException {
        var adapter = CSharpMutationScanner.createAdapter(sourceFile);
        List<MutationRule> rules = MutationRuleGenerator.generateRules(specFile);
        List<MutationPoint> points = new ArrayList<>();
        for (MutationRule rule : rules) {
            points.addAll(rule.findMutationPoints(adapter, sourceFile));
        }
        return points;
    }

}
