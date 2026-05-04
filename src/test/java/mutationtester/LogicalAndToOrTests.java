package mutationtester;


import mutationtester.core.*;
import mutationtester.model.*;


import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class LogicalAndToOrTests {
    static final Mutator.MutatorType type = Mutator.MutatorType.LOGICAL_AND_TO_OR;

    @Test
    void AndToOrJavaTests() {
        Language language = new Language("samples/Demo.java");
        String initial;
        String expected;

        initial = "\"a && b\"";
        expected = "\"a && b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a && b'";
        expected = "'a && b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a && b";
        expected = "// a && b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a && b */";
        expected = "/* a && b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 && b > 0;";
        expected = "return a > 0 || b > 0;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void AndToOrPythonTests() {
        Language language = new Language("samples/Demo.py");
        String initial;
        String expected;

        initial = "\"foo and bar\"";
        expected = "\"foo and bar\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'foo and bar'";
        expected = "'foo and bar'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "# foo and bar";
        expected = "# foo and bar";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 and b > 0";
        expected = "return a > 0 or b > 0";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "command = 5";
        expected = "command = 5";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void AndToOrJavaScriptTests() {
        Language language = new Language("samples/Demo.js");
        String initial;
        String expected;

        initial = "\"a && b\"";
        expected = "\"a && b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a && b'";
        expected = "'a && b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a && b";
        expected = "// a && b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a && b */";
        expected = "/* a && b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 && b > 0;";
        expected = "return a > 0 || b > 0;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void AndToOrCSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"a && b\"";
        expected = "\"a && b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a && b'";
        expected = "'a && b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a && b";
        expected = "// a && b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a && b */";
        expected = "/* a && b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 && b > 0;";
        expected = "return a > 0 || b > 0;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void AndToOrRacketTests() {
        Language language = new Language("samples/Demo.rkt");
        String initial;
        String expected;

        initial = "\"(and (> a 0) (> b 0))\"";
        expected = "\"(and (> a 0) (> b 0))\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = ";(and (> a 0) (> b 0))";
        expected = ";(and (> a 0) (> b 0))";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "#|(and (> a 0) (> b 0))|#";
        expected = "#|(and (> a 0) (> b 0))|#";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(and (> a 0) (> b 0))";
        expected = "(or (> a 0) (> b 0))";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "\"foo and bar\"";
        expected = "\"foo and bar\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));
    }
}
