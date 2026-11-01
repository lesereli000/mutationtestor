package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.msl.MSLNode;
import mutationtester.msl.MSLPatternParser;
import mutationtester.msl.MutationRuleGenerator;

/**
 * The increment node ({quantity}++ / {quantity}--): each language matches its
 * own spelling of "add one to x" (x++ and ++x, x += 1, (add1 x)), Delete:
 * removes only whole increment statements, and With: {quantity} is skipped
 * where the value is thrown away.
 */
class IncrementMutationsTests {

    private static final String SPEC = """
            Invert and Remove Increments

            \tReplace: {quantity}++
            \tWith: {quantity}--
            \tAnd: Inverse

            \tReplace: {quantity}++
            \tWith: {quantity}

            \tReplace: {quantity}--
            \tWith: {quantity}

            \tDelete: {quantity}++
            \tExcept: for ({quantity}; {quantity}; {match}) {statement}

            \tDelete: {quantity}--
            \tExcept: for ({quantity}; {quantity}; {match}) {statement}

            End
            """;

    @TempDir
    Path dir;

    private Set<String> mutants(String fileName, String source) throws IOException {
        Files.writeString(dir.resolve(fileName), source);
        Path spec = Files.writeString(dir.resolve("increments.spec"), SPEC);
        Path src = dir.resolve("src");
        Files.createDirectories(src);
        Files.move(dir.resolve(fileName), src.resolve(fileName));
        Set<String> out = new TreeSet<>();
        for (MutationPoint p : Main.scanForMutations(src, spec.toString())) {
            out.add(p.getLineNumber() + ": " + p.getOperator() + " -> " + p.getReplacement());
        }
        return out;
    }

    @Test
    void parsesPostfixAndPrefixAsTheSameNode() {
        MSLNode post = (MSLNode) MSLPatternParser.parse("{quantity}++");
        MSLNode pre = (MSLNode) MSLPatternParser.parse("--{quantity}");
        assertEquals(MSLNode.INCREMENT, post.kind());
        assertEquals("++", post.value());
        assertEquals(MSLNode.INCREMENT, pre.kind());
        assertEquals("--", pre.value());
        assertEquals("{quantity}++", post.toSpecText());
        // Spacing doesn't matter, and + / ++ still read as separate operators.
        assertEquals(post, MSLPatternParser.parse("{quantity} ++"));
        assertEquals("binary", ((MSLNode) MSLPatternParser.parse("{quantity} + {quantity}")).kind());
    }

    @Test
    void misspelledRuleKeywordIsAnError() {
        IOException e = assertThrows(IOException.class, () -> MutationRuleGenerator.generateRules(List.of(
                "Increments", "", "\tRemove: {quantity}++", "", "End")));
        assertTrue(e.getMessage().contains("Unknown keyword 'Remove:'"), e.getMessage());
    }

    @Test
    void java() throws IOException {
        Set<String> m = mutants("A.java", """
                class A {
                    int f(int[] a, int n) {
                        int i = 0;
                        i++;
                        --n;
                        a[i++] = 1;
                        for (int j = 0; j < n; j++) {
                            n--;
                        }
                        return i;
                    }
                }
                """);
        assertEquals(new TreeSet<>(List.of(
                "4: i++ -> i--", "4: i++; -> ;",
                "5: --n -> ++n", "5: --n; -> ;",
                "6: i++ -> i--", "6: i++ -> i",
                "7: j++ -> j--",
                "8: n-- -> n++", "8: n--; -> ;")), m);
    }

    @Test
    void csharp() throws IOException {
        Set<String> m = mutants("A.cs", """
                class A {
                    int F(int[] a, int n) {
                        int i = 0;
                        i++;
                        --n;
                        a[i++] = 1;
                        for (int j = 0; j < n; j++) {
                            n--;
                        }
                        return i;
                    }
                }
                """);
        assertEquals(new TreeSet<>(List.of(
                "4: i++ -> i--", "4: i++; -> ;",
                "5: --n -> ++n", "5: --n; -> ;",
                "6: i++ -> i--", "6: i++ -> i",
                "7: j++ -> j--",
                "8: n-- -> n++", "8: n--; -> ;")), m);
    }

    @Test
    void javascript() throws IOException {
        Set<String> m = mutants("a.js", """
                function f(a, n) {
                    let i = 0;
                    i++;
                    --n;
                    a[i++] = 1;
                    for (let j = 0; j < n; j++) {
                        n--;
                    }
                    return i;
                }
                """);
        assertEquals(new TreeSet<>(List.of(
                "3: i++ -> i--", "3: i++; -> ;",
                "4: --n -> ++n", "4: --n; -> ;",
                "5: i++ -> i--", "5: i++ -> i",
                "6: j++ -> j--",
                "7: n-- -> n++", "7: n--; -> ;")), m);
    }

    @Test
    void python() throws IOException {
        Set<String> m = mutants("a.py", """
                def f(n):
                    i = 0
                    i += 1
                    n -= 1
                    i += 2
                    return i
                """);
        assertEquals(new TreeSet<>(List.of(
                "3: i += 1 -> i -= 1", "3: i += 1 -> pass",
                "4: n -= 1 -> n += 1", "4: n -= 1 -> pass")), m);
    }

    @Test
    void racket() throws IOException {
        Set<String> m = mutants("a.rkt", """
                #lang racket
                (define (count-up i n)
                  (if (< i n) (count-up (add1 i) n) (sub1 i)))
                """);
        assertEquals(new TreeSet<>(List.of(
                "3: (add1 i) -> (sub1 i)", "3: (add1 i) -> i",
                "3: (sub1 i) -> (add1 i)", "3: (sub1 i) -> i")), m);
    }
}
