package mutationtester;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.PrintStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * "No losses" regression check for the spec files: every mutant recorded in
 * src/test/resources/msl-baseline must still be produced. New mutants are
 * allowed but reported, so they can be reviewed and added to the baseline.
 *
 * Mutants are compared by file, line, column, original text and replacement;
 * report labels may change. Run with -Dmsl.baseline.update=true to rewrite the
 * baseline from the current output (after reviewing the reported additions).
 * Corpora under external/ are submodules: they run with
 * -Dmsl.baseline.external=true, and are skipped when not checked out.
 * Losses listed in pending-review-losses.txt are reported but allowed.
 */
@Tag("baseline")
class MslBaselineTests {

    private static final Path BASELINE_DIR = Paths.get("src", "test", "resources", "msl-baseline");

    private static final String[] MAIN_SPECS = {
        "specs/OperatorMutations.spec",
        "specs/StatementMutation.spec",
        "specs/StringMutation.spec",
        "specs/context-sensitive-mutations.spec",
    };

    private static final String[] CORPORA = {
        "samples/CSProject",
        "samples/JSProject",
        "samples/MavenProject",
        "samples/PytestProject/src",
        "samples/PytestProject/tests",
        "samples/RacketCalculator",
        "samples/RacketProject",
        "samples/SharedPitProject",
        "external/CSharpProject",
        "external/JavaProject",
        "external/JavaScriptProject",
        "external/PythonProject",
        "external/RacketProject",
    };

    /** The sample projects that hold the Demo* files the single-operator specs were written for. */
    private static final String[] SINGLE_OPERATOR_CORPORA = {
        "samples/CSProject",
        "samples/JSProject",
        "samples/MavenProject",
        "samples/PytestProject/src",
        "samples/RacketProject",
        "samples/SharedPitProject",
    };

    static Stream<Arguments> cases() {
        List<Arguments> cases = new ArrayList<>();
        for (String spec : MAIN_SPECS) {
            for (String corpus : CORPORA) {
                cases.add(Arguments.of(spec, corpus));
            }
        }
        for (int i = 1; i <= 25; i++) {
            String spec = String.format("specs/single-operator/O%02d.spec", i);
            for (String corpus : SINGLE_OPERATOR_CORPORA) {
                cases.add(Arguments.of(spec, corpus));
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{0} on {1}")
    @MethodSource("cases")
    void noMutantsAreLost(String spec, String corpus) throws IOException {
        // The external projects take about half an hour, so they run only with
        // -Dmsl.baseline.external=true. -Dmsl.baseline.only=external/Java limits
        // a run to matching corpora.
        assumeTrue(!corpus.startsWith("external/") || Boolean.getBoolean("msl.baseline.external")
                || Boolean.getBoolean("msl.baseline.update"), "external corpora need -Dmsl.baseline.external=true");
        String only = System.getProperty("msl.baseline.only");
        assumeTrue(only == null || corpus.contains(only), "not selected by msl.baseline.only");
        Path root = Paths.get(corpus);
        assumeTrue(Files.isDirectory(root) && hasSources(root), corpus + " is not checked out");

        Set<String> actual = scan(spec, corpus);
        Path baselineFile = BASELINE_DIR.resolve(fileName(spec, corpus));
        // The current mutants, for comparing with the baseline by hand.
        Path current = Paths.get("target", "msl-current");
        Files.createDirectories(current);
        Files.write(current.resolve(fileName(spec, corpus)), actual, StandardCharsets.UTF_8);

        if (Boolean.getBoolean("msl.baseline.update")) {
            Files.createDirectories(BASELINE_DIR);
            Files.write(baselineFile, actual, StandardCharsets.UTF_8);
            return;
        }

        assumeTrue(Files.exists(baselineFile), "no baseline recorded for " + spec + " on " + corpus);
        Set<String> expected = new TreeSet<>(Files.readAllLines(baselineFile, StandardCharsets.UTF_8));
        expected.remove("");

        Set<String> lost = new TreeSet<>(expected);
        lost.removeAll(actual);
        Set<String> pending = pendingReview(baselineFile.getFileName().toString());
        int awaitingReview = (int) lost.stream().filter(pending::contains).count();
        if (awaitingReview > 0) {
            System.out.println("[msl-baseline] " + awaitingReview + " lost mutant(s) for " + spec + " on " + corpus
                    + " are listed in " + PENDING_REVIEW + " and await review");
        }
        lost.removeAll(pending);
        Set<String> added = new TreeSet<>(actual);
        added.removeAll(expected);
        if (!added.isEmpty()) {
            System.out.println("[msl-baseline] " + added.size() + " new mutant(s) for " + spec + " on "
                    + corpus + " (review, then update the baseline):");
            added.stream().limit(50).forEach(m -> System.out.println("  + " + m));
        }
        assertTrue(lost.isEmpty(), lost.size() + " mutant(s) lost for " + spec + " on " + corpus + ":\n  - "
                + String.join("\n  - ", lost.stream().limit(50).toList()));
    }

    private static Set<String> scan(String spec, String corpus) {
        Path scanRoot = Paths.get(corpus);

        PrintStream out = System.out;
        List<MutationPoint> points;
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            points = Main.scanForMutations(scanRoot, spec);
        } finally {
            System.setOut(out);
        }

        Set<String> keys = new TreeSet<>();
        for (MutationPoint p : points) {
            String file = scanRoot.toAbsolutePath().relativize(p.getSourceFile().toPath().toAbsolutePath())
                    .toString().replace('\\', '/');
            keys.add(file + "\t" + p.getLineNumber() + "\t" + p.getColumnNumber() + "\t"
                    + escape(p.getOperator()) + "\t" + escape(p.getReplacement()));
        }
        return keys;
    }

    /**
     * Losses explained in pending-review-losses.txt ("baseline file, reason,
     * mutant" per line) don't fail the test until a reviewer accepts them
     * (delete the line, and the mutant from the baseline) or rejects them
     * (delete the line, and fix the adapter).
     */
    private static final Path PENDING_REVIEW = BASELINE_DIR.resolve("pending-review-losses.txt");

    private static Set<String> pendingReview(String baselineFileName) throws IOException {
        Set<String> keys = new TreeSet<>();
        if (!Files.exists(PENDING_REVIEW)) return keys;
        for (String line : Files.readAllLines(PENDING_REVIEW, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            if (fields.length >= 7 && fields[0].equals(baselineFileName)) {
                keys.add(String.join("\t", fields[2], fields[3], fields[4], fields[5], fields[6]));
            }
        }
        return keys;
    }

    private static boolean hasSources(Path root) throws IOException {
        try (Stream<Path> files = Files.walk(root, 3)) {
            return files.anyMatch(Files::isRegularFile);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String fileName(String spec, String corpus) {
        String base = Paths.get(spec).getFileName().toString();
        base = base.substring(0, base.lastIndexOf('.'));
        return base + "__" + corpus.replace('/', '_') + ".txt";
    }
}
