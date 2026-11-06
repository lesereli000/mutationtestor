package mutationtester;

import static mutationtester.GitTestRepos.commitToDataBranch;
import static mutationtester.GitTestRepos.git;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.equivalent.MiniJson;

public class EquivalentsBranchTests {

    // IDs embed source lines, so they can hold anything a line of code can.
    private static final String TRICKY_ID = "CONTENT|A.java|run|x[i]=\"a\\\\b\"+'}'+é;|+|-";

    @Test
    void miniJsonParsesNestedValuesAndEscapes() {
        Object parsed = MiniJson.parse("{\"a\": [1, true, null, \"x\\\"y\\u0041\"], \"b\": {}}");
        Map<?, ?> map = (Map<?, ?>) parsed;
        List<?> list = (List<?>) map.get("a");
        assertEquals(1.0, list.get(0));
        assertEquals(true, list.get(1));
        assertNull(list.get(2));
        assertEquals("x\"yA", list.get(3));
        assertEquals(Map.of(), map.get("b"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{\"a\": [1,]"));
        assertThrows(IllegalArgumentException.class, () -> MiniJson.parse("{} trailing"));
    }

    @Test
    void equivalentsFileRoundTripsTrickyIds() {
        Set<String> ids = new LinkedHashSet<>(List.of(TRICKY_ID, "LEGACY|B.py|3|4|ROR|<|<="));
        String json = EquivalentsBranch.toEquivalentsJson(ids);
        assertEquals(ids, EquivalentsBranch.parseEquivalentsFile(json));
        assertEquals(Set.of(), EquivalentsBranch.parseEquivalentsFile(EquivalentsBranch.toEquivalentsJson(List.of())));
    }

    @Test
    void batchesRemoveThenAdd() {
        Set<String> ids = new LinkedHashSet<>(List.of("keep", "drop"));
        EquivalentsBranch.applyBatch(ids, EquivalentsBranch.parseBatch("{\"add\": [\"new\"], \"remove\": [\"drop\"]}"));
        assertEquals(Set.of("keep", "new"), ids);

        EquivalentsBranch.applyBatch(ids, EquivalentsBranch.parseBatch("{\"remove\": [\"keep\"]}"));
        assertEquals(Set.of("new"), ids);
    }

    @Test
    void invalidBatchesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EquivalentsBranch.parseBatch("not json"));
        assertThrows(IllegalArgumentException.class, () -> EquivalentsBranch.parseBatch("[]"));
        assertThrows(IllegalArgumentException.class, () -> EquivalentsBranch.parseBatch("{}"));
        assertThrows(IllegalArgumentException.class, () -> EquivalentsBranch.parseBatch("{\"add\": [1]}"));
        assertThrows(IllegalArgumentException.class, () -> EquivalentsBranch.parseBatch("{\"add\": \"x\"}"));
    }

    @Test
    void loadReturnsNullOutsideGit(@TempDir Path dir) {
        assertNull(EquivalentsBranch.load(dir, false));
    }

    @Test
    void loadReturnsNullWithoutTheBranch(@TempDir Path dir) throws Exception {
        Path repo = GitTestRepos.init(dir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "hi");
        git(repo, "add", "-A");
        git(repo, "commit", "--quiet", "-m", "init");

        assertNull(EquivalentsBranch.load(repo, false));
    }

    @Test
    void loadAppliesPendingBatchesInOrderAndSkipsInvalidOnes(@TempDir Path dir) throws Exception {
        Path repo = GitTestRepos.init(dir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "hi");
        git(repo, "add", "-A");
        git(repo, "commit", "--quiet", "-m", "init");

        commitToDataBranch(repo, "data",
                EquivalentsBranch.FILE, EquivalentsBranch.toEquivalentsJson(List.of("a", "b")),
                "pending/2026-01-02T00-00-00-000Z-x.json", "{\"add\": [\"c\"], \"remove\": [\"a\"]}",
                "pending/2026-01-01T00-00-00-000Z-x.json", "{\"add\": [\"a2\"]}",
                "pending/2026-01-03T00-00-00-000Z-x.json", "{ broken",
                "pending/2026-01-04T00-00-00-000Z-x.json", "{\"remove\": [\"a2\"], \"add\": [\"" + TRICKY_ID.replace("\\", "\\\\").replace("\"", "\\\"") + "\"]}");

        EquivalentsBranch.Snapshot snapshot = EquivalentsBranch.load(repo, false);

        assertNotNull(snapshot);
        assertEquals(EquivalentsBranch.Source.BRANCH, snapshot.source());
        assertEquals(Set.of("b", "c", TRICKY_ID), snapshot.ids());
        assertTrue(snapshot.description().contains("3 pending batches"), snapshot.description());
    }

    @Test
    void loadStartsEmptyWhenTheFileIsMissing(@TempDir Path dir) throws Exception {
        Path repo = GitTestRepos.init(dir.resolve("repo"));
        Files.writeString(repo.resolve("README.md"), "hi");
        git(repo, "add", "-A");
        git(repo, "commit", "--quiet", "-m", "init");
        commitToDataBranch(repo, "data", "pending/1.json", "{\"add\": [\"only\"]}");

        assertEquals(Set.of("only"), EquivalentsBranch.load(repo, false).ids());
    }
}
