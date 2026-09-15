package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.msl.MSLExpressionFactory;

/**
 * Presentation-friendly regression demo for the universal MSL operator syntax.
 * Run with: mvn "-Dtest=MSLOperatorUniversalityDemoTests" test
 */
@TestMethodOrder(OrderAnnotation.class)
class MSLOperatorUniversalityDemoTests {

    @TempDir
    Path tempDir;

    @Test
    @Order(1)
    @DisplayName("OLD PROBLEM: language grammar names are not a universal MSL vocabulary")
    void showOldProblem() {
        String javaGrammarName = "LE";
        String racketGrammarName = "LEQ";
        String sourceSymbol = "<=";

        System.out.println();
        System.out.println("=== SECTION 1: OLD PROBLEM ===");
        System.out.println("The same operation had competing names:");
        System.out.println("  Java grammar name:   " + javaGrammarName);
        System.out.println("  Racket grammar name: " + racketGrammarName);
        System.out.println("  Source-code symbol:  " + sourceSymbol);
        System.out.println("These strings differ, so using grammar names in MSL is not universal.");

        assertTrue(!javaGrammarName.equals(racketGrammarName)
                && !javaGrammarName.equals(sourceSymbol)
                && !racketGrammarName.equals(sourceSymbol));
    }

    @Test
    @Order(2)
    @DisplayName("NEW SOLUTION: one <= specification works for all five languages")
    void showCanonicalOperatorWorksForEveryLanguage() throws Exception {
        Files.writeString(tempDir.resolve("Example.java"),
                "class Example { boolean check(int a, int b) { return a <= b; } }");
        Files.writeString(tempDir.resolve("example.js"),
                "function check(a, b) { return a <= b; }");
        Files.writeString(tempDir.resolve("example.py"),
                "def check(a, b):\n    return a <= b\n");
        Files.writeString(tempDir.resolve("Example.cs"),
                "class Example { bool Check(int a, int b) { return a <= b; } }");
        Files.writeString(tempDir.resolve("example.rkt"),
                "#lang racket\n(define (check a b) (<= a b))\n");

        Path spec = tempDir.resolve("universal.spec");
        Files.writeString(spec,
                "Universal Boundary Mutation\n\n"
                + "Replace: {quantity} <= {quantity}\n"
                + "With: {quantity} < {quantity}\n\n"
                + "End\n");

        List<MutationPoint> points = Main.scanForMutations(
                tempDir, Mutator.MutatorType.CUSTOM, spec.toString());

        System.out.println();
        System.out.println("=== SECTION 2: NEW SOLUTION ===");
        System.out.println("One canonical MSL rule: <= to <");
        for (String extension : List.of(".java", ".js", ".py", ".cs", ".rkt")) {
            long matches = points.stream()
                    .filter(point -> point.getSourceFile().getName().endsWith(extension))
                    .count();
            System.out.println("  " + extension + ": " + matches + " mutation found");
            assertEquals(1, matches, "Expected one mutation for " + extension);
        }
        assertEquals(5, points.size());
        System.out.println("PASS: The same specification worked for all five languages.");
    }

    @Test
    @Order(3)
    @DisplayName("NEW SOLUTION: noncanonical aliases are rejected with guidance")
    void showAliasesAreRejected() {
        IllegalArgumentException upperAlias = assertThrows(IllegalArgumentException.class,
                () -> MSLExpressionFactory.parse("{quantity} LE {quantity}"));
        IllegalArgumentException lowerAlias = assertThrows(IllegalArgumentException.class,
                () -> MSLExpressionFactory.parse("{quantity} leq {quantity}"));

        System.out.println();
        System.out.println("=== SECTION 3: INVALID ALIASES ===");
        System.out.println("  LE:  " + upperAlias.getMessage());
        System.out.println("  leq: " + lowerAlias.getMessage());
        System.out.println("PASS: Only the canonical <= spelling is accepted.");

        assertTrue(upperAlias.getMessage().contains("\"<=\""));
        assertTrue(lowerAlias.getMessage().contains("\"<=\""));
    }
}
