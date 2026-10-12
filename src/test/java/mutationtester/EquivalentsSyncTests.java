package mutationtester;

import static mutationtester.GitTestRepos.git;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.ci.EquivalentsSync;
import mutationtester.equivalent.EquivalentsBranch;

public class EquivalentsSyncTests {
    private static final String REMOTE_DATA = "refs/heads/" + EquivalentsBranch.BRANCH;

    @TempDir
    Path dir;

    private Path remote;
    private Path ci;

    @BeforeEach
    void setUp() throws Exception {
        remote = GitTestRepos.bareRemote(dir.resolve("remote.git"));
        Path seed = GitTestRepos.cloneOf(remote, dir.resolve("seed"));
        Files.writeString(seed.resolve("README.md"), "project");
        git(seed, "add", "-A");
        git(seed, "commit", "--quiet", "-m", "init");
        git(seed, "push", "--quiet", "origin", "HEAD:refs/heads/main");
        ci = GitTestRepos.cloneOf(remote, dir.resolve("ci"));
    }

    @Test
    void failsOutsideAGitCheckout() throws Exception {
        Path plain = Files.createDirectories(dir.resolve("plain"));
        assertEquals(1, EquivalentsSync.sync(plain));
    }

    @Test
    void createsTheBranchWhenMissing() {
        assertEquals(0, EquivalentsSync.sync(ci));

        assertEquals(Set.of(), EquivalentsBranch.parseEquivalentsFile(remoteFile(EquivalentsBranch.FILE)));
        assertEquals(1, commitCount());
    }

    @Test
    void foldsPendingBatchesIntoOneCommitAndEmptiesPending() throws Exception {
        assertEquals(0, EquivalentsSync.sync(ci));
        pushPending(
                "pending/2026-01-01T00-00-00-000Z-a.json", "{\"add\": [\"one\", \"two\"]}",
                "pending/2026-01-02T00-00-00-000Z-b.json", "{\"remove\": [\"one\"], \"add\": [\"three\"]}");

        assertEquals(0, EquivalentsSync.sync(ci));

        assertEquals(Set.of("two", "three"), EquivalentsBranch.parseEquivalentsFile(remoteFile(EquivalentsBranch.FILE)));
        assertEquals(List.of(EquivalentsBranch.FILE), remoteTopLevel());
        assertEquals(3, commitCount()); // create, the reviewer's save, the fold
        assertTrue(git(remote, "log", "-1", "--format=%s", REMOTE_DATA).startsWith("Fold 2 equivalent-mutant batches"));
    }

    @Test
    void keepsInvalidBatchesInPlace() throws Exception {
        assertEquals(0, EquivalentsSync.sync(ci));
        pushPending(
                "pending/1-good.json", "{\"add\": [\"good\"]}",
                "pending/2-bad.json", "{ not json");

        assertEquals(0, EquivalentsSync.sync(ci));

        assertEquals(Set.of("good"), EquivalentsBranch.parseEquivalentsFile(remoteFile(EquivalentsBranch.FILE)));
        assertEquals("{ not json", remoteFile("pending/2-bad.json"));
        assertFalse(git(remote, "ls-tree", "--name-only", REMOTE_DATA, "pending/").contains("1-good.json"));
    }

    @Test
    void doesNothingWhenNothingIsPending() {
        assertEquals(0, EquivalentsSync.sync(ci));
        assertEquals(0, EquivalentsSync.sync(ci));
        assertEquals(1, commitCount());
    }

    @Test
    void retriesWhenAPushIsRejected() throws Exception {
        assertEquals(0, EquivalentsSync.sync(ci));
        pushPending("pending/1.json", "{\"add\": [\"x\"]}");

        // Reject exactly one push, the way a concurrent update would.
        Path marker = dir.resolve("rejected-once");
        Path hook = remote.resolve("hooks").resolve("pre-receive");
        Files.writeString(hook, "#!/bin/sh\n"
                + "if [ ! -f '" + marker.toString().replace('\\', '/') + "' ]; then\n"
                + "  touch '" + marker.toString().replace('\\', '/') + "'\n"
                + "  echo 'simulated concurrent update' >&2\n"
                + "  exit 1\n"
                + "fi\n", StandardCharsets.UTF_8);
        assertTrue(hook.toFile().setExecutable(true));

        assertEquals(0, EquivalentsSync.sync(ci));

        assertTrue(Files.exists(marker), "the first push should have been rejected");
        assertEquals(Set.of("x"), EquivalentsBranch.parseEquivalentsFile(remoteFile(EquivalentsBranch.FILE)));
    }

    /** A reviewer's "Save to GitHub" commit, made from a separate clone. */
    private void pushPending(String... pathsAndContents) throws Exception {
        Path reviewer = GitTestRepos.cloneOf(remote, Files.createTempDirectory(dir, "reviewer").resolve("clone"));
        git(reviewer, "checkout", "--quiet", "-b", EquivalentsBranch.BRANCH, "origin/" + EquivalentsBranch.BRANCH);
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Path file = reviewer.resolve(pathsAndContents[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, pathsAndContents[i + 1], StandardCharsets.UTF_8);
        }
        git(reviewer, "add", "-A");
        git(reviewer, "commit", "--quiet", "-m", "Mark equivalent mutants");
        git(reviewer, "push", "--quiet", "origin", EquivalentsBranch.BRANCH);
    }

    private String remoteFile(String path) {
        return git(remote, "show", REMOTE_DATA + ":" + path);
    }

    private List<String> remoteTopLevel() {
        return git(remote, "ls-tree", "--name-only", REMOTE_DATA).lines().toList();
    }

    private int commitCount() {
        return Integer.parseInt(git(remote, "rev-list", "--count", REMOTE_DATA).trim());
    }
}
