package mutationtester;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.equivalent.Git;

/** Throwaway git repositories for the equivalent-mutant branch tests. */
final class GitTestRepos {
    private GitTestRepos() {
    }

    static String git(Path dir, String... args) {
        Git.Result result = Git.run(dir, args);
        assertTrue(result.ok(), "git " + String.join(" ", args) + " failed: " + result.stderr());
        return result.stdout();
    }

    static Path init(Path dir) throws IOException {
        Files.createDirectories(dir);
        git(dir, "init", "--quiet");
        identify(dir);
        return dir;
    }

    static void identify(Path dir) {
        git(dir, "config", "user.name", "Test");
        git(dir, "config", "user.email", "test@example.com");
        git(dir, "config", "core.autocrlf", "false");
    }

    static Path bareRemote(Path dir) throws IOException {
        Files.createDirectories(dir);
        git(dir, "init", "--quiet", "--bare");
        return dir;
    }

    static Path cloneOf(Path remote, Path dir) {
        git(remote.getParent(), "clone", "--quiet", remote.toString(), dir.toString());
        identify(dir);
        return dir;
    }

    /**
     * Commits files to the data branch of {@code repo} (creating the branch if
     * needed) without touching the checked-out branch's history.
     */
    static void commitToDataBranch(Path repo, String message, String... pathsAndContents) throws IOException {
        Path work = Files.createTempDirectory(repo.getParent(), "data-worktree");
        boolean exists = Git.run(repo, "rev-parse", "--verify", "--quiet", "refs/heads/" + EquivalentsBranch.BRANCH).ok();
        if (exists) {
            git(repo, "worktree", "add", "--quiet", work.toString(), EquivalentsBranch.BRANCH);
        } else {
            git(repo, "worktree", "add", "--quiet", "--detach", work.toString());
            git(work, "checkout", "--quiet", "--orphan", EquivalentsBranch.BRANCH);
            git(work, "rm", "-rf", "--quiet", "--ignore-unmatch", ".");
        }
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Path file = work.resolve(pathsAndContents[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, pathsAndContents[i + 1], StandardCharsets.UTF_8);
        }
        git(work, "add", "-A");
        git(work, "commit", "--quiet", "-m", message);
        git(repo, "worktree", "remove", "--force", work.toString());
    }

    static String show(Path repo, String refAndPath) {
        return git(repo, "show", refAndPath);
    }
}
