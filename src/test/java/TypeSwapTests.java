import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class TypeSwapTests {
    static final Mutator.MutatorType intToDoubleType = Mutator.MutatorType.TYPE_INT_TO_DOUBLE;
    static final Mutator.MutatorType intToFloatType = Mutator.MutatorType.TYPE_INT_TO_FLOAT;
    static final Mutator.MutatorType doubleToIntType = Mutator.MutatorType.TYPE_DOUBLE_TO_INT;
    static final Mutator.MutatorType floatToIntType = Mutator.MutatorType.TYPE_FLOAT_TO_INT;

    @Test
    void IntToDoubleCSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"int a = 5;\"";
        expected = "\"int a = 5;\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, intToDoubleType, 100, language, false));

        initial = "// int a = 5;";
        expected = "// int a = 5;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, intToDoubleType, 100, language, false));

        initial = "int a = 5;";
        expected = "double a = 5;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, intToDoubleType, 100, language, false));

        initial = "printValue";
        expected = "printValue";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, intToDoubleType, 100, language, false));
    }

    @Test
    void IntToFloatCSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"int a = 5;\"";
        expected = "\"int a = 5;\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, intToFloatType, 100, language, false));

        initial = "int a = 5;";
        expected = "float a = 5;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, intToFloatType, 100, language, false));

        initial = "printValue";
        expected = "printValue";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, intToFloatType, 100, language, false));
    }

    @Test
    void DoubleToIntCSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"double a = 5.0;\"";
        expected = "\"double a = 5.0;\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, doubleToIntType, 100, language, false));

        initial = "double a = 5.0;";
        expected = "int a = 5.0;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, doubleToIntType, 100, language, false));

        initial = "mydoublevalue";
        expected = "mydoublevalue";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, doubleToIntType, 100, language, false));
    }

    @Test
    void FloatToIntCSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"float a = 5.0f;\"";
        expected = "\"float a = 5.0f;\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, floatToIntType, 100, language, false));

        initial = "float a = 5.0f;";
        expected = "int a = 5.0f;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, floatToIntType, 100, language, false));

        initial = "myfloatvalue";
        expected = "myfloatvalue";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, floatToIntType, 100, language, false));
    }
}