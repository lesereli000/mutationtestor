package mutationtester;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.DictionaryMutationScanner;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DictionaryMutationScannerTests {

    // ── Python ────────────────────────────────────────────────────────────────

    @Test
    void python_plusToMinus() throws IOException {
        File f = py("return a + b");
        var pts = scan(f, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(1, pts.size());
        assertEquals("+", pts.get(0).getOperator());
        assertEquals("-", pts.get(0).getReplacement());
    }

    @Test
    void python_plusInsideStringIsSkipped() throws IOException {
        File f = py("x = \"a + b\"");
        assertTrue(scan(f, Mutator.MutatorType.AOR_PLUS_TO_MINUS).isEmpty());
    }

    @Test
    void python_plusAfterHashCommentIsSkipped() throws IOException {
        File f = py("# return a + b");
        assertTrue(scan(f, Mutator.MutatorType.AOR_PLUS_TO_MINUS).isEmpty());
    }

    @Test
    void python_minusToPlus() throws IOException {
        File f = py("return a - b");
        var pts = scan(f, Mutator.MutatorType.AOR_MINUS_TO_PLUS);
        assertEquals(1, pts.size());
    }

    @Test
    void python_multiplyToDivide_skipsDoublestar() throws IOException {
        File f = py("return x * y\nreturn x ** y");
        var pts = scan(f, Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE);
        // Only plain * should be caught; ** is power and must be skipped
        assertEquals(1, pts.size());
        assertEquals("*", pts.get(0).getOperator());
    }

    @Test
    void python_divideToMultiply_skipsFloorDiv() throws IOException {
        File f = py("return x / y\nreturn x // y");
        var pts = scan(f, Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY);
        // Only the single / should be caught; // is floor division, not a comment
        assertEquals(1, pts.size());
    }

    @Test
    void python_moduloToMultiply() throws IOException {
        File f = py("return x % 2");
        var pts = scan(f, Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY);
        assertEquals(1, pts.size());
        assertEquals("%", pts.get(0).getOperator());
        assertEquals("*", pts.get(0).getReplacement());
    }

    @Test
    void python_floorDivToDiv() throws IOException {
        File f = py("return a // b");
        var pts = scan(f, Mutator.MutatorType.AOR_FLOOR_DIV_TO_DIV);
        assertEquals(1, pts.size());
        assertEquals("//", pts.get(0).getOperator());
        assertEquals("/", pts.get(0).getReplacement());
    }

    @Test
    void python_floorDivAssignIsSkipped() throws IOException {
        File f = py("x //= 2");
        var pts = scan(f, Mutator.MutatorType.AOR_FLOOR_DIV_TO_DIV);
        assertTrue(pts.isEmpty());
    }

    @Test
    void python_floorDivAfterHashCommentIsSkipped() throws IOException {
        File f = py("# return a // b");
        var pts = scan(f, Mutator.MutatorType.AOR_FLOOR_DIV_TO_DIV);
        assertTrue(pts.isEmpty());
    }

    @Test
    void python_powerToMultiply() throws IOException {
        File f = py("return a ** b");
        var pts = scan(f, Mutator.MutatorType.AOR_POWER_TO_MULTIPLY);
        assertEquals(1, pts.size());
        assertEquals("**", pts.get(0).getOperator());
        assertEquals("*", pts.get(0).getReplacement());
    }

    @Test
    void python_powerAssignIsSkipped() throws IOException {
        File f = py("x **= 2");
        var pts = scan(f, Mutator.MutatorType.AOR_POWER_TO_MULTIPLY);
        assertTrue(pts.isEmpty());
    }

    @Test
    void python_singleStarNotDetectedAsPower() throws IOException {
        File f = py("return a * b");
        var pts = scan(f, Mutator.MutatorType.AOR_POWER_TO_MULTIPLY);
        assertTrue(pts.isEmpty());
    }

    @Test
    void python_bitwiseAndToOr() throws IOException {
        File f = py("return x & y");
        var pts = scan(f, Mutator.MutatorType.BOR_AND_TO_OR);
        assertEquals(1, pts.size());
        assertEquals("&", pts.get(0).getOperator());
        assertEquals("|", pts.get(0).getReplacement());
    }

    @Test
    void python_bitwiseOrToAnd() throws IOException {
        File f = py("return x | y");
        var pts = scan(f, Mutator.MutatorType.BOR_OR_TO_AND);
        assertEquals(1, pts.size());
        assertEquals("|", pts.get(0).getOperator());
        assertEquals("&", pts.get(0).getReplacement());
    }

    @Test
    void python_ltToLe_skipsAlreadyLe() throws IOException {
        File f = py("if x < y and x <= z:");
        var pts = scan(f, Mutator.MutatorType.ROR_LT_TO_LE);
        assertEquals(1, pts.size()); // only the bare <
        assertEquals("<", pts.get(0).getOperator());
        assertEquals("<=", pts.get(0).getReplacement());
    }

    @Test
    void python_leToLt() throws IOException {
        File f = py("if x <= y:");
        var pts = scan(f, Mutator.MutatorType.ROR_LE_TO_LT);
        assertEquals(1, pts.size());
        assertEquals("<=", pts.get(0).getOperator());
        assertEquals("<", pts.get(0).getReplacement());
    }

    @Test
    void python_gtToGe_skipsAlreadyGe() throws IOException {
        File f = py("if x > y and x >= z:");
        var pts = scan(f, Mutator.MutatorType.ROR_GT_TO_GE);
        assertEquals(1, pts.size()); // only the bare >
    }

    @Test
    void python_geToGt() throws IOException {
        File f = py("if x >= y:");
        var pts = scan(f, Mutator.MutatorType.ROR_GE_TO_GT);
        assertEquals(1, pts.size());
        assertEquals(">=", pts.get(0).getOperator());
        assertEquals(">", pts.get(0).getReplacement());
    }

    @Test
    void python_eqNegate_bothDirections() throws IOException {
        File f = py("if x == y or x != 0:");
        var pts = scan(f, Mutator.MutatorType.EQ_NEGATE);
        assertEquals(2, pts.size());
        assertTrue(pts.stream().anyMatch(p -> p.getOperator().equals("==") && p.getReplacement().equals("!=")));
        assertTrue(pts.stream().anyMatch(p -> p.getOperator().equals("!=") && p.getReplacement().equals("==")));
    }

    @Test
    void python_logicalAndToOr_keywordOnly() throws IOException {
        // "and" inside "command" or "wand" must not match
        File f = py("if a and b:\n    x = command + wand");
        var pts = scan(f, Mutator.MutatorType.LOGICAL_AND_TO_OR);
        assertEquals(1, pts.size());
        assertEquals("and", pts.get(0).getOperator());
        assertEquals("or", pts.get(0).getReplacement());
    }

    @Test
    void python_logicalOrToAnd_keywordOnly() throws IOException {
        // "or" inside "for" or "order" must not match
        File f = py("if a or b:\n    for i in order:");
        var pts = scan(f, Mutator.MutatorType.LOGICAL_OR_TO_AND);
        assertEquals(1, pts.size());
        assertEquals("or", pts.get(0).getOperator());
        assertEquals("and", pts.get(0).getReplacement());
    }

    // ── C-like (non-Python) ───────────────────────────────────────────────────

    @Test
    void clike_slashSlashIsComment_notFloorDiv() throws IOException {
        File f = js("var x = a + b; // a + c");
        var pts = scan(f, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(1, pts.size()); // only the real + is mutated, not the one in the comment
    }

    @Test
    void clike_logicalAndToOr_usesDoubleAmpersand() throws IOException {
        File f = js("if (a && b) { }");
        var pts = scan(f, Mutator.MutatorType.LOGICAL_AND_TO_OR);
        assertEquals(1, pts.size());
        assertEquals("&&", pts.get(0).getOperator());
        assertEquals("||", pts.get(0).getReplacement());
    }

    // ── Comment/string detection helpers ─────────────────────────────────────

    @Test
    void python_tripleQuotedStringProtectsOperator() throws IOException {
        File f = py("x = \"\"\"a + b\"\"\"\nreturn x");
        // The + inside the triple-quoted string must be skipped
        assertTrue(scan(f, Mutator.MutatorType.AOR_PLUS_TO_MINUS).isEmpty());
    }

    @Test
    void python_hashInStringIsNotAComment() throws IOException {
        File f = py("tag = \"#tag\"\nreturn a + b");
        var pts = scan(f, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(1, pts.size()); // the + on line 2 must still be found
    }

    @Test
    void python_invertNegsDetectsNot() throws IOException {
        File f = py("return not x");
        var pts = scan(f, Mutator.MutatorType.INVERT_NEGS);
        assertEquals(1, pts.size());
        assertEquals("not ", pts.get(0).getOperator());
        assertEquals("", pts.get(0).getReplacement());
    }

    @Test
    void python_invertNegsSkipsNotInHashComment() throws IOException {
        File f = py("# return not x");
        var pts = scan(f, Mutator.MutatorType.INVERT_NEGS);
        assertTrue(pts.isEmpty());
    }

    @Test
    void python_invertNegsSkipsNotInsideString() throws IOException {
        File f = py("return \"not x\"");
        var pts = scan(f, Mutator.MutatorType.INVERT_NEGS);
        assertTrue(pts.isEmpty());
    }

    @Test
    void python_invertNegsDoesNotMatchNoteOrNothing() throws IOException {
        // "note" or "nothing" must not match
        File f = py("note = nothing");
        var pts = scan(f, Mutator.MutatorType.INVERT_NEGS);
        assertTrue(pts.isEmpty());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static List<MutationPoint> scan(File f, Mutator.MutatorType t) throws IOException {
        return DictionaryMutationScanner.scanFile(f, t);
    }

    private static File py(String content) throws IOException {
        return writeTempFile(content, ".py");
    }

    private static File js(String content) throws IOException {
        return writeTempFile(content, ".js");
    }

    private static File writeTempFile(String content, String suffix) throws IOException {
        Path path = Files.createTempFile("dict-mutation-", suffix);
        Files.writeString(path, content + "\n");
        path.toFile().deleteOnExit();
        return path.toFile();
    }
}
