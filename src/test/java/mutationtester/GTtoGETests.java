package mutationtester;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GTtoGETests {
    static final Mutator.MutatorType type = Mutator.MutatorType.ROR_GT_TO_GE;

    @Test
    void GTtoGEJavaTests() {
        Language language = new Language("samples/Demo.java"); // Should we do mocking? is it worth it right now?
        String initial;
        String expected;

        initial = "\"if(x > y) {}\"";
        expected = "\"if(x > y) {}\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x > y) {}'";
        expected = "'if(x > y) {}'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "//if(x > y) {}";
        expected = "//if(x > y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/*if(x > y) {}*/";
        expected = "/*if(x > y) {}*/";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "x >> y";
        expected = "x >> y";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x > y) {}";
        expected = "if(x >= y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGEPythonTests() {
        Language language = new Language("samples/Demo.py");
        String initial;
        String expected;

        initial = "\"if(x > y):\"";
        expected = "\"if(x > y):\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x > y):'";
        expected = "'if(x > y):'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "#if(x > y):";
        expected = "#if(x > y):";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "x >> y";
        expected = "x >> y";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x > y):";
        expected = "if(x >= y):";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGEJavaScriptTests() {
        Language language = new Language("samples/Demo.js");
        String initial;
        String expected;

        initial = "\"if(x > y) {}\"";
        expected = "\"if(x > y) {}\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x > y) {}'";
        expected = "'if(x > y) {}'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "//if(x > y) {}";
        expected = "//if(x > y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/*if(x > y) {}*/";
        expected = "/*if(x > y) {}*/";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "x >> y";
        expected = "x >> y";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "x >>> y";
        expected = "x >>> y";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x > y) {}";
        expected = "if(x >= y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGECSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"if(x > y) {}\"";
        expected = "\"if(x > y) {}\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x > y) {}'";
        expected = "'if(x > y) {}'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "//if(x > y) {}";
        expected = "//if(x > y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/*if(x > y) {}*/";
        expected = "/*if(x > y) {}*/";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "x >> y";
        expected = "x >> y";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x > y) {}";
        expected = "if(x >= y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGERacketTests() {
        Language language = new Language("samples/Demo.rkt");
        String initial;
        String expected;

        initial = "\"(if (> x y) ()())\"";
        expected = "\"(if (> x y) ()())\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = ";(if (> x y) ()())";
        expected = ";(if (> x y) ()())";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "#|(if (> x y) ()())|#";
        expected = "#|(if (> x y) ()())|#";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(char>? #\\> #\\>)";
        expected = "(char>? #\\> #\\>)";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(string>? () ())";
        expected = "(string>? () ())";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(cond ((assoc 'a alist) => cdr) (else #f))";
        expected = "(cond ((assoc 'a alist) => cdr) (else #f))";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(if (> x y) ()())";
        expected = "(if (>= x y) ()())";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));
    }
}
