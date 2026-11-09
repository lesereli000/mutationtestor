package mutationtester.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.equivalent.EquivalentMutantStore;
import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

public class EquivalentsPageDataTests {

    private static EquivalentsBranch.Snapshot branch(String... ids) {
        return new EquivalentsBranch.Snapshot(new LinkedHashSet<>(List.of(ids)), EquivalentsBranch.Source.BRANCH, "branch");
    }

    @Test
    void repoUrlComesFromTheActionsVariables() {
        assertEquals("https://github.com/me/proj",
                EquivalentsPageData.forRun(branch(), null, "me/proj").repoUrl());
        assertEquals("https://ghe.example.com/me/proj",
                EquivalentsPageData.forRun(branch(), "https://ghe.example.com/", "me/proj").repoUrl());
        assertNull(EquivalentsPageData.forRun(branch(), "https://github.com", null).repoUrl());
    }

    @Test
    void dataScriptCannotBeClosedBySourceCode() {
        String script = EquivalentsPageData.forRun(branch("CONTENT|a.js|f|s=\"</script><b>\";|+|-"), null, "me/proj")
                .toDataScript();
        assertEquals(1, script.split("</script>", -1).length - 1, script);
        assertTrue(script.contains("\\u003c/script>"));
        assertTrue(script.contains("\"repoUrl\":\"https://github.com/me/proj\""));
        assertTrue(script.contains("\"branch\":\"mutation-data\""));
    }

    @Test
    void localRunsHaveNoRepository() {
        EquivalentsPageData data = EquivalentsPageData.forRun(EquivalentsBranch.Snapshot.none(), null, null);
        assertEquals("none", data.source());
        assertTrue(data.toDataScript().contains("\"repoUrl\":null"));
    }

    @Test
    void equivalentMutantsAreLeftOutOfTheScore(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("Calc.java");
        Files.writeString(source, """
                public class Calc {
                    int add(int a, int b) {
                        return a + b;
                    }
                    int sub(int a, int b) {
                        return a - b;
                    }
                }
                """);
        MutationPoint plus = new MutationPoint(source.toFile(), 3, 17, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        MutationPoint minus = new MutationPoint(source.toFile(), 6, 17, "-", "+", Mutator.MutatorType.AOR_MINUS_TO_PLUS);
        MutationPoint other = new MutationPoint(new File("Other.java"), 1, 0, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        List<MutantResult> results = List.of(
                new MutantResult(plus, MutantResult.MutantStatus.SURVIVED, "", true),
                new MutantResult(minus, MutantResult.MutantStatus.KILLED, "", false),
                new MutantResult(other, MutantResult.MutantStatus.SURVIVED, "", true));

        MutationReport all = new MutationReport(results);
        assertEquals(3, all.getTotalMutants());

        MutationReport scored = new MutationReport(results, Set.of(EquivalentMutantStore.buildMutantId(plus)));
        assertEquals(2, scored.getTotalMutants());
        assertEquals(1, scored.getKilled());
        assertEquals(1, scored.getSurvived());
        assertEquals(1, scored.getEquivalent());
        assertEquals(50.0, scored.getMutationScore(), 0.001);
        assertFalse(scored.getSurvivedResults().stream().anyMatch(r -> r.getMutationPoint() == plus));

        MutationReport allEquivalent = new MutationReport(results.subList(0, 1), Set.of(EquivalentMutantStore.buildMutantId(plus)));
        assertEquals(0, allEquivalent.getTotalMutants());
        assertEquals(0.0, allEquivalent.getMutationScore());
    }
}
