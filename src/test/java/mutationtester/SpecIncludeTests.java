package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.msl.MutationRule;
import mutationtester.msl.MutationRuleGenerator;

/**
 * A spec named in the config can Include: other spec files. Config still has
 * one custom_mutation_spec; only rule generation changes.
 */
class SpecIncludeTests {

    @TempDir
    Path dir;

    private static final String PLUS = """
            Math

            	Replace: {quantity} + {quantity}
            	With: {quantity} - {quantity}

            End
            """;

    private static final String TIMES = """
            Math

            	Replace: {quantity} * {quantity}
            	With: {quantity} / {quantity}

            End
            """;

    @Test
    void linkingSpecProducesTheSameRulesAsPuttingThemInOneFile() throws IOException {
        Path plus = Files.writeString(dir.resolve("plus.spec"), PLUS);
        Path times = Files.writeString(dir.resolve("times.spec"), TIMES);
        Path linked = Files.writeString(dir.resolve("linked.spec"), """
                Include: plus.spec
                Include: times.spec
                """);

        List<String> combined = new ArrayList<>();
        combined.addAll(labels(plus));
        combined.addAll(labels(times));
        assertEquals(combined, labels(linked));
    }

    @Test
    void includeIsRelativeToTheIncludingFile() throws IOException {
        Path nested = Files.createDirectories(dir.resolve("group"));
        Files.writeString(nested.resolve("plus.spec"), PLUS);
        Path linked = Files.writeString(dir.resolve("linked.spec"), "Include: group/plus.spec\n");
        assertEquals(labels(nested.resolve("plus.spec")), labels(linked));
    }

    @Test
    void repoLinkedSpecMatchesConcatenatingTheTwoFiles() throws IOException {
        List<String> combined = new ArrayList<>();
        combined.addAll(labels(Path.of("specs/OperatorMutations.spec")));
        combined.addAll(labels(Path.of("specs/StatementMutation.spec")));
        assertEquals(combined, labels(Path.of("specs/operators-and-statements.spec")));
    }

    @Test
    void ownRulesAndIncludesKeepEachFilesGroupLabels() throws IOException {
        Files.writeString(dir.resolve("plus.spec"), PLUS);
        Path mixed = Files.writeString(dir.resolve("mixed.spec"), """
                Extra

                	Replace: {quantity} == {quantity}
                	With: {quantity} != {quantity}

                End

                Include: plus.spec
                """);
        List<String> labels = labels(mixed);
        assertEquals(List.of("Extra: == → !=", "Math: + → -"), labels);
    }

    @Test
    void missingIncludeNamesTheLineAndPath() throws IOException {
        Path spec = Files.writeString(dir.resolve("bad.spec"), "Include: no-such-file.spec\n");
        IOException e = assertThrows(IOException.class, () -> MutationRuleGenerator.generateRules(spec.toFile()));
        assertTrue(e.getMessage().contains("line 1:"), e.getMessage());
        assertTrue(e.getMessage().contains("no-such-file.spec"), e.getMessage());
    }

    @Test
    void circularIncludeIsAnError() throws IOException {
        Files.writeString(dir.resolve("a.spec"), "Include: b.spec\n");
        Files.writeString(dir.resolve("b.spec"), "Include: a.spec\n");
        IOException e = assertThrows(IOException.class,
                () -> MutationRuleGenerator.generateRules(dir.resolve("a.spec").toFile()));
        assertTrue(e.getMessage().toLowerCase().contains("circular"), e.getMessage());
    }

    @Test
    void diamondIncludeDoesNotDuplicateRules() throws IOException {
        Files.writeString(dir.resolve("leaf.spec"), PLUS);
        Files.writeString(dir.resolve("left.spec"), "Include: leaf.spec\n");
        Files.writeString(dir.resolve("right.spec"), "Include: leaf.spec\n");
        Path top = Files.writeString(dir.resolve("top.spec"), """
                Include: left.spec
                Include: right.spec
                """);
        assertEquals(labels(dir.resolve("leaf.spec")), labels(top));
    }

    @Test
    void existingOperatorSpecStillHasTheSameRuleCount() throws IOException {
        assertEquals(34, MutationRuleGenerator.generateRules(Path.of("specs/OperatorMutations.spec").toFile()).size());
    }

    private static List<String> labels(Path spec) throws IOException {
        return MutationRuleGenerator.generateRules(spec.toFile()).stream()
                .map(MutationRule::getLabel)
                .toList();
    }
}
