package mutationtester;

import mutationtester.coverage.*;
import mutationtester.model.*;
import mutationtester.msl.*;
import mutationtester.scanner.*;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PythonScannerTests {

    // ── Context-sensitive (Except:) tests ────────────────────────────────────

    @Test
    public void testExceptSuppressesMutationOnClampLine() throws IOException {
        // "if n <= 0: n = 0" matches the Except: pattern — mutation should be suppressed
        File file = writeTempPythonFile(
                "def clamp(n):\n    if n <= 0: n = 0\n    return n\n");
        File specFile = writeSpecFile(
                "Test\n\n\tReplace: {quantity} <= {quantity}\n\tWith: {quantity} < {quantity}\n\tExcept: if ({match: {quantity} <= 0}) {quantity} = 0\n\nEnd\n");

        MSLScannerAdapter adapter = PythonMutationScanner.createAdapter(file);
        List<MutationRule> rules = MutationRuleGenerator.generateRules(specFile);
        List<MutationPoint> points = new java.util.ArrayList<>();
        for (MutationRule rule : rules) {
            points.addAll(rule.findMutationPoints(adapter, file));
        }

        assertTrue(points.isEmpty(), "Mutation on clamp line should be suppressed by Except:");
    }

    @Test
    public void testExceptDoesNotSuppressMutationOnRealLogicLine() throws IOException {
        // "if x <= limit:" does NOT match the Except: pattern — mutation should fire
        File file = writeTempPythonFile(
                "def is_under(x, limit):\n    if x <= limit:\n        return True\n    return False\n");
        File specFile = writeSpecFile(
                "Test\n\n\tReplace: {quantity} <= {quantity}\n\tWith: {quantity} < {quantity}\n\tExcept: if ({match: {quantity} <= 0}) {quantity} = 0\n\nEnd\n");

        MSLScannerAdapter adapter = PythonMutationScanner.createAdapter(file);
        List<MutationRule> rules = MutationRuleGenerator.generateRules(specFile);
        List<MutationPoint> points = new java.util.ArrayList<>();
        for (MutationRule rule : rules) {
            points.addAll(rule.findMutationPoints(adapter, file));
        }

        assertEquals(1, points.size(), "Mutation on real-logic line should NOT be suppressed");
        assertEquals("<=", points.get(0).getOperator());
        assertEquals("<", points.get(0).getReplacement());
    }

    @Test
    public void testExceptWithNoMatchingLinesFindsAllMutations() throws IOException {
        // No line matches the Except: pattern — all <= mutations should fire
        File file = writeTempPythonFile(
                "def check(a, b):\n    return a <= b\n");
        File specFile = writeSpecFile(
                "Test\n\n\tReplace: {quantity} <= {quantity}\n\tWith: {quantity} < {quantity}\n\tExcept: if ({match: {quantity} <= 0}) {quantity} = 0\n\nEnd\n");

        MSLScannerAdapter adapter = PythonMutationScanner.createAdapter(file);
        List<MutationRule> rules = MutationRuleGenerator.generateRules(specFile);
        List<MutationPoint> points = new java.util.ArrayList<>();
        for (MutationRule rule : rules) {
            points.addAll(rule.findMutationPoints(adapter, file));
        }

        assertEquals(1, points.size());
        assertEquals("<=", points.get(0).getOperator());
    }

    private File writeSpecFile(String content) throws IOException {
        Path path = Files.createTempFile("python-spec-", ".spec");
        Files.writeString(path, content);
        path.toFile().deleteOnExit();
        return path.toFile();
    }

    private File writeTempPythonFile(String content) throws IOException {
        Path path = Files.createTempFile("python-mutation-", ".py");
        Files.writeString(path, content);
        path.toFile().deleteOnExit();
        return path.toFile();
    }
}
