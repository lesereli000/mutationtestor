package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GEtoGTTests {
    static final Mutator.MutatorType type = Mutator.MutatorType.ROR_GE_TO_GT;

    @Test
    void GTtoGEJavaTests() {
        Language language = new Language("samples/Demo.java"); // Should we do mocking? is it worth it right now?
        String initial;
        String expected;

        initial = "\"if(x >= y) {}\"";
        expected = "\"if(x >= y) {}\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x >= y) {}'";
        expected = "'if(x >= y) {}'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "//if(x >= y) {}";
        expected = "//if(x >= y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/*if(x >= y) {}*/";
        expected = "/*if(x >= y) {}*/";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x >= y) {}";
        expected = "if(x > y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGEPythonTests() {
        Language language = new Language("samples/Demo.py");
        String initial;
        String expected;

        initial = "\"if(x >= y):\"";
        expected = "\"if(x >= y):\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x >= y):'";
        expected = "'if(x >= y):'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "#if(x >= y):";
        expected = "#if(x >= y):";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x >= y):";
        expected = "if(x > y):";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGEJavaScriptTests() {
        Language language = new Language("samples/Demo.js");
        String initial;
        String expected;

        initial = "\"if(x >= y) {}\"";
        expected = "\"if(x >= y) {}\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x >= y) {}'";
        expected = "'if(x >= y) {}'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "//if(x >= y) {}";
        expected = "//if(x >= y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/*if(x >= y) {}*/";
        expected = "/*if(x >= y) {}*/";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x >= y) {}";
        expected = "if(x > y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGECSharpTests() {
        Language language = new Language("samples/Demo.cs");
        String initial;
        String expected;

        initial = "\"if(x >= y) {}\"";
        expected = "\"if(x >= y) {}\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "'if(x >= y) {}'";
        expected = "'if(x >= y) {}'";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "//if(x >= y) {}";
        expected = "//if(x >= y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "/*if(x >= y) {}*/";
        expected = "/*if(x >= y) {}*/";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));

        initial = "if(x >= y) {}";
        expected = "if(x > y) {}";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, false));
    }

    @Test
    void GTtoGERacketTests() {
        Language language = new Language("samples/Demo.rkt");
        String initial;
        String expected;

        initial = "\"(if (>= x y) ()())\"";
        expected = "\"(if (>= x y) ()())\"";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = ";(if (>= x y) ()())";
        expected = ";(if (>= x y) ()())";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "#|(if (>= x y) ()())|#";
        expected = "#|(if (>= x y) ()())|#";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));

        initial = "(if (>= x y) ()())";
        expected = "(if (> x y) ()())";
        assertEquals(expected, Mutator.mutateOutsideStringsAndComments(initial, type, 100, language, true));
    }
}
