package mutationtester.ci;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import mutationtester.equivalent.EquivalentsBranch;
import mutationtester.equivalent.Git;

/**
 * Keeps a project's mutation-data branch in shape. Run from the project
 * checkout in CI (with permission to push):
 *
 *   java -cp mutationtester.jar mutationtester.ci.EquivalentsSync [--repo <dir>]
 *
 * Creates the branch with an empty equivalents.json when it is missing, and
 * otherwise folds pending/*.json batches into equivalents.json in one commit.
 * Everything is done with git plumbing against the fetched branch, so the
 * working tree and the checked-out branch are never touched. A push that
 * loses a race is retried from a fresh fetch; folding is deterministic so
 * retrying is safe.
 *
 * Exit code 0 on success (including "nothing to do"), 1 on failure.
 */
public final class EquivalentsSync {
    static final int MAX_ATTEMPTS = 5;

    private static final String REF = "refs/remotes/" + EquivalentsBranch.REMOTE + "/" + EquivalentsBranch.BRANCH;

    private EquivalentsSync() {
    }

    public static void main(String[] args) {
        Path repo = Path.of(".");
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--repo") && i + 1 < args.length) {
                repo = Path.of(args[++i]);
            } else {
                System.err.println("Usage: java -cp mutationtester.jar mutationtester.ci.EquivalentsSync [--repo <dir>]");
                System.exit(1);
            }
        }
        System.exit(sync(repo));
    }

    public static int sync(Path repo) {
        if (!EquivalentsBranch.isGitRepository(repo)) {
            System.err.println("ERROR: " + repo.toAbsolutePath() + " is not a git checkout.");
            return 1;
        }

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            Git.Result remoteHeads = Git.run(repo, "ls-remote", "--heads", EquivalentsBranch.REMOTE,
                    "refs/heads/" + EquivalentsBranch.BRANCH);
            if (!remoteHeads.ok()) {
                System.err.println("ERROR: Could not reach " + EquivalentsBranch.REMOTE + ": " + remoteHeads.stderr().trim());
                return 1;
            }

            Outcome outcome = remoteHeads.stdout().isBlank() ? createBranch(repo) : foldPending(repo);
            if (outcome != Outcome.RETRY) {
                return outcome == Outcome.DONE ? 0 : 1;
            }
            System.out.println("Someone else updated " + EquivalentsBranch.BRANCH + " at the same time; retrying ("
                    + attempt + "/" + MAX_ATTEMPTS + ").");
        }

        System.err.println("ERROR: Gave up updating " + EquivalentsBranch.BRANCH + " after " + MAX_ATTEMPTS + " attempts.");
        return 1;
    }

    private enum Outcome { DONE, RETRY, FAILED }

    private static Outcome createBranch(Path repo) {
        String blob = writeBlob(repo, EquivalentsBranch.toEquivalentsJson(List.of()));
        if (blob == null) {
            return Outcome.FAILED;
        }
        String tree = makeTree(repo, List.of(new TreeEntry("100644", "blob", blob, EquivalentsBranch.FILE)));
        if (tree == null) {
            return Outcome.FAILED;
        }
        String commit = commitTree(repo, tree, null, "Create " + EquivalentsBranch.BRANCH + " for equivalent mutants");
        if (commit == null) {
            return Outcome.FAILED;
        }

        Outcome pushed = push(repo, commit);
        if (pushed == Outcome.DONE) {
            System.out.println("Created the " + EquivalentsBranch.BRANCH + " branch with an empty "
                    + EquivalentsBranch.FILE + ".");
        }
        return pushed;
    }

    private static Outcome foldPending(Path repo) {
        Git.Result fetched = EquivalentsBranch.fetchBranch(repo, EquivalentsBranch.BRANCH);
        if (!fetched.ok()) {
            System.err.println("ERROR: Could not fetch " + EquivalentsBranch.BRANCH + ": " + fetched.stderr().trim());
            return Outcome.FAILED;
        }

        List<String> pending = EquivalentsBranch.listPendingFiles(repo, REF);
        if (pending.isEmpty()) {
            System.out.println("No pending equivalent-mutant changes on " + EquivalentsBranch.BRANCH + ".");
            return Outcome.DONE;
        }

        Set<String> ids = new LinkedHashSet<>();
        Git.Result current = Git.run(repo, "show", REF + ":" + EquivalentsBranch.FILE);
        if (current.ok()) {
            try {
                ids.addAll(EquivalentsBranch.parseEquivalentsFile(current.stdout()));
            } catch (IllegalArgumentException e) {
                // Folding would overwrite whatever is there, so stop and let a person look.
                System.err.println("ERROR: " + EquivalentsBranch.FILE + " on " + EquivalentsBranch.BRANCH
                        + " is not valid (" + e.getMessage() + "). Fix or delete it, then re-run.");
                return Outcome.FAILED;
            }
        }

        int folded = 0;
        List<String> kept = new ArrayList<>();
        for (String path : pending) {
            Git.Result batch = Git.run(repo, "show", REF + ":" + path);
            try {
                if (!batch.ok()) {
                    throw new IllegalArgumentException(batch.stderr().trim());
                }
                EquivalentsBranch.applyBatch(ids, EquivalentsBranch.parseBatch(batch.stdout()));
                folded++;
            } catch (IllegalArgumentException e) {
                // Left in place so nothing is lost; it warns on every run until fixed or deleted.
                System.err.println("WARNING: Leaving " + path + " in place, it is not a valid batch: " + e.getMessage());
                kept.add(path.substring(EquivalentsBranch.PENDING_DIR.length() + 1));
            }
        }
        if (folded == 0) {
            return Outcome.DONE;
        }

        String tree = buildFoldedTree(repo, EquivalentsBranch.toEquivalentsJson(ids), kept);
        if (tree == null) {
            return Outcome.FAILED;
        }
        Git.Result parent = Git.run(repo, "rev-parse", REF);
        String message = "Fold " + folded + " equivalent-mutant batch" + (folded == 1 ? "" : "es")
                + " (" + ids.size() + " mutant" + (ids.size() == 1 ? "" : "s") + " total)";
        String commit = commitTree(repo, tree, parent.stdout().trim(), message);
        if (commit == null) {
            return Outcome.FAILED;
        }

        Outcome pushed = push(repo, commit);
        if (pushed == Outcome.DONE) {
            System.out.println(message);
        }
        return pushed;
    }

    /** The branch's top-level tree with a new equivalents.json and only the kept pending files. */
    private static String buildFoldedTree(Path repo, String equivalentsJson, List<String> keptPending) {
        String blob = writeBlob(repo, equivalentsJson);
        if (blob == null) {
            return null;
        }

        List<TreeEntry> entries = new ArrayList<>();
        boolean wroteFile = false;
        for (TreeEntry entry : listTree(repo, REF)) {
            if (entry.name().equals(EquivalentsBranch.FILE)) {
                entries.add(new TreeEntry("100644", "blob", blob, EquivalentsBranch.FILE));
                wroteFile = true;
            } else if (entry.name().equals(EquivalentsBranch.PENDING_DIR)) {
                if (keptPending.isEmpty()) {
                    continue;
                }
                List<TreeEntry> kept = listTree(repo, REF + ":" + EquivalentsBranch.PENDING_DIR).stream()
                        .filter(child -> keptPending.contains(child.name()))
                        .toList();
                String pendingTree = makeTree(repo, kept);
                if (pendingTree == null) {
                    return null;
                }
                entries.add(new TreeEntry("040000", "tree", pendingTree, EquivalentsBranch.PENDING_DIR));
            } else {
                entries.add(entry);
            }
        }
        if (!wroteFile) {
            entries.add(new TreeEntry("100644", "blob", blob, EquivalentsBranch.FILE));
        }
        return makeTree(repo, entries);
    }

    private record TreeEntry(String mode, String type, String sha, String name) {
        String toLine() {
            return mode + " " + type + " " + sha + "\t" + name;
        }
    }

    private static List<TreeEntry> listTree(Path repo, String treeish) {
        List<TreeEntry> entries = new ArrayList<>();
        Git.Result listing = Git.run(repo, "ls-tree", treeish);
        if (!listing.ok()) {
            return entries;
        }
        for (String line : listing.stdout().split("\\R")) {
            int tab = line.indexOf('\t');
            if (tab < 0) {
                continue;
            }
            String[] parts = line.substring(0, tab).split(" ");
            if (parts.length == 3) {
                entries.add(new TreeEntry(parts[0], parts[1], parts[2], line.substring(tab + 1)));
            }
        }
        return entries;
    }

    private static String writeBlob(Path repo, String content) {
        return objectId(Git.runWithInput(repo, content, "hash-object", "-w", "--no-filters", "--stdin"), "write blob");
    }

    private static String makeTree(Path repo, List<TreeEntry> entries) {
        StringBuilder input = new StringBuilder();
        for (TreeEntry entry : entries) {
            input.append(entry.toLine()).append('\n');
        }
        return objectId(Git.runWithInput(repo, input.toString(), "mktree"), "write tree");
    }

    private static String commitTree(Path repo, String tree, String parent, String message) {
        List<String> args = new ArrayList<>(List.of("commit-tree", tree, "-m", message));
        if (parent != null) {
            args.add("-p");
            args.add(parent);
        }
        return objectId(Git.run(repo, null, botIdentity(), args.toArray(String[]::new)), "commit");
    }

    private static Outcome push(Path repo, String commit) {
        Git.Result pushed = Git.run(repo, "push", "--quiet", EquivalentsBranch.REMOTE,
                commit + ":refs/heads/" + EquivalentsBranch.BRANCH);
        if (pushed.ok()) {
            return Outcome.DONE;
        }
        String err = pushed.stderr().toLowerCase();
        if (err.contains("rejected") || err.contains("fetch first") || err.contains("non-fast-forward")
                || err.contains("already exists") || err.contains("cannot lock ref")) {
            return Outcome.RETRY;
        }
        System.err.println("ERROR: Could not push " + EquivalentsBranch.BRANCH + ": " + pushed.stderr().trim());
        if (err.contains("403") || err.contains("permission")) {
            System.err.println("The job needs 'permissions: contents: write', and branch rules must allow pushing to "
                    + EquivalentsBranch.BRANCH + ".");
        }
        return Outcome.FAILED;
    }

    private static String objectId(Git.Result result, String what) {
        if (!result.ok()) {
            System.err.println("ERROR: git could not " + what + ": " + result.stderr().trim());
            return null;
        }
        return result.stdout().trim();
    }

    private static Map<String, String> botIdentity() {
        boolean actions = "true".equals(System.getenv("GITHUB_ACTIONS"));
        String name = actions ? "github-actions[bot]" : "Mutation Tester";
        String email = actions ? "41898282+github-actions[bot]@users.noreply.github.com" : "mutationtester@users.noreply.github.com";
        return Map.of(
                "GIT_AUTHOR_NAME", name,
                "GIT_AUTHOR_EMAIL", email,
                "GIT_COMMITTER_NAME", name,
                "GIT_COMMITTER_EMAIL", email);
    }
}
