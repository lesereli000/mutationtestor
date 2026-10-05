package mutationtester;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static mutationtester.OperatorMutationsCrossLanguageParityTests.mutatedLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * StringMutation.txt (T01-T02) is written against C#'s string.IsNullOrEmpty.
 * Every language has the same check: as a library call (C#, Java's Guava and
 * Commons) or written out (s == null || s.isEmpty(), s is None or s == "",
 * (or (not s) (string=? s ""))). Each replacement uses the language's own
 * not-null and not-empty tests, so it means the same thing everywhere.
 */
class StringMutationsCrossLanguageParityTests {

    @TempDir
    Path tempDir;

    @Test
    void nullOrEmptyChecksAreMutatedInEveryLanguage() throws Exception {
        Files.writeString(tempDir.resolve("Names.java"),
                "class Names {\n" +
                "  boolean blank(String s) {\n    return Strings.isNullOrEmpty(s);\n  }\n" +
                "  boolean none(String t) {\n    return t == null || t.isEmpty();\n  }\n" +
                "}\n");
        Files.writeString(tempDir.resolve("Names.cs"),
                "class Names {\n" +
                "  bool Blank(string s) {\n    return string.IsNullOrEmpty(s);\n  }\n" +
                "  bool None(string t) {\n    return t == null || t == \"\";\n  }\n" +
                "}\n");
        Files.writeString(tempDir.resolve("names.js"),
                "function blank(s) {\n  return s == null || s === \"\";\n}\n");
        Files.writeString(tempDir.resolve("names.py"),
                "def blank(s):\n    return s is None or s == \"\"\n");
        Files.writeString(tempDir.resolve("names.rkt"),
                "#lang racket/base\n(define (blank s)\n  (or (not s) (string=? s \"\")))\n");

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, "StringMutation.txt");

        assertEquals(s("return (s != null);", "return (!\"\".equals(s));",
                       "return (t != null);", "return (!\"\".equals(t));"),
                mutatedLines(points, ".java"));
        assertEquals(s("return (s != null);", "return (s != \"\");",
                       "return (t != null);", "return (t != \"\");"),
                mutatedLines(points, ".cs"));
        assertEquals(s("return (s != null);", "return (s !== \"\");"), mutatedLines(points, ".js"));
        assertEquals(s("return (s is not None)", "return (s != \"\")"), mutatedLines(points, ".py"));
        assertEquals(s("(not (eq? s #f)))", "(not (equal? s \"\")))"), mutatedLines(points, ".rkt"));
    }

    private static Set<String> s(String... lines) {
        return new TreeSet<>(List.of(lines));
    }
}
