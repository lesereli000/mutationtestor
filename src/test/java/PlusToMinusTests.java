import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class PlusToMinusTests {
    static final Mutator.MutatorType type = Mutator.MutatorType.AOR_PLUS_TO_MINUS;

    @Test
    void PlusToMinusJavaTests() {
        Language language = new Language("samples/Demo.java");
        String initial;
        String expected;

        initial = "\"a + b\"";
        expected = "\"a + b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a + b'";
        expected = "'a + b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a + b";
        expected = "// a + b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a + b */";
        expected = "/* a + b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a + b;";
        expected = "return a - b;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void PlusToMinusPythonTests() {
        Language language = new Language("samples/Demo.py");
        String initial;
        String expected;

        initial = "\"foo + bar\"";
        expected = "\"foo + bar\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'foo + bar'";
        expected = "'foo + bar'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "# foo + bar";
        expected = "# foo + bar";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a + b";
        expected = "return a - b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "command = 5";
        expected = "command = 5";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void PlusToMinusJavaScriptTests() {
        Language language = new Language("samples/Demo.js");
        String initial;
        String expected;

        initial = "\"a + b\"";
        expected = "\"a + b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a + b'";
        expected = "'a + b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a + b";
        expected = "// a + b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a + b */";
        expected = "/* a + b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a + b;";
        expected = "return a - b;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void PlusToMinusCSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"a + b\"";
        expected = "\"a + b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a + b'";
        expected = "'a + b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a + b";
        expected = "// a + b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a + b */";
        expected = "/* a + b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a + b;";
        expected = "return a - b;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void PlusToMinusRacketTests() {
        Language language = new Language("samples/Demo.rkt");
        String initial;
        String expected;

        initial = "\"(+ a b)\"";
        expected = "\"(+ a b)\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = ";(+ a b)";
        expected = ";(+ a b)";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "#|(+ a b)|#";
        expected = "#|(+ a b)|#";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(+ a b)";
        expected = "(- a b)";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "\"foo + bar\"";
        expected = "\"foo + bar\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));
    }
}
