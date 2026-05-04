package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

public class LogicalOrToAndTests {
    static final Mutator.MutatorType type = Mutator.MutatorType.LOGICAL_OR_TO_AND;

    @Test
    void OrToAndJavaTests() {
        Language language = new Language("samples/Demo.java");
        String initial;
        String expected;

        initial = "\"a || b\"";
        expected = "\"a || b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a || b'";
        expected = "'a || b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a || b";
        expected = "// a || b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a || b */";
        expected = "/* a || b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 || b > 0;";
        expected = "return a > 0 && b > 0;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void OrToAndPythonTests() {
        Language language = new Language("samples/Demo.py");
        String initial;
        String expected;

        initial = "\"foo or bar\"";
        expected = "\"foo or bar\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'foo or bar'";
        expected = "'foo or bar'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "# foo or bar";
        expected = "# foo or bar";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 or b > 0";
        expected = "return a > 0 and b > 0";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "error = True";
        expected = "error = True";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void OrToAndJavaScriptTests() {
        Language language = new Language("samples/Demo.js");
        String initial;
        String expected;

        initial = "\"a || b\"";
        expected = "\"a || b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a || b'";
        expected = "'a || b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a || b";
        expected = "// a || b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a || b */";
        expected = "/* a || b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 || b > 0;";
        expected = "return a > 0 && b > 0;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void OrToAndCSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"a || b\"";
        expected = "\"a || b\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'a || b'";
        expected = "'a || b'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "// a || b";
        expected = "// a || b";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/* a || b */";
        expected = "/* a || b */";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "return a > 0 || b > 0;";
        expected = "return a > 0 && b > 0;";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void OrToAndRacketTests() {
        Language language = new Language("samples/Demo.rkt");
        String initial;
        String expected;

        initial = "\"(or (> a 0) (> b 0))\"";
        expected = "\"(or (> a 0) (> b 0))\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = ";(or (> a 0) (> b 0))";
        expected = ";(or (> a 0) (> b 0))";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "#|(or (> a 0) (> b 0))|#";
        expected = "#|(or (> a 0) (> b 0))|#";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(or (> a 0) (> b 0))";
        expected = "(and (> a 0) (> b 0))";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "\"foo or bar\"";
        expected = "\"foo or bar\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));
    }
}
