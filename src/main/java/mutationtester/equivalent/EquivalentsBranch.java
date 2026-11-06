package mutationtester.equivalent;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Equivalent mutants shared through a git branch in the project under test.
 *
 * The branch holds one {@code equivalents.json} ({"hiddenMutantIds": [...]}).
 * Saving from the HTML report creates a small batch file under
 * {@code pending/} ({"add": [...], "remove": [...]}) because GitHub can only
 * prefill new files from a link. {@link mutationtester.ci.EquivalentsSync}
 * folds those batches into equivalents.json; until it does, readers apply
 * them on the fly so no mark is ever missed.
 */
public final class EquivalentsBranch {
    public static final String BRANCH = "mutation-data";
    public static final String FILE = "equivalents.json";
    public static final String PENDING_DIR = "pending";
    public static final String REMOTE = "origin";

    public enum Source { BRANCH, LOCAL_FILE, NONE }

    /** The equivalent mutant IDs a run will use, and where they came from. */
    public record Snapshot(Set<String> ids, Source source, String description) {
        public static Snapshot none() {
            return new Snapshot(new LinkedHashSet<>(), Source.NONE, "none");
        }
    }

    /** One batch file from pending/. */
    public record Batch(List<String> add, List<String> remove) {
    }

    private EquivalentsBranch() {
    }

    /**
     * Reads the equivalents from the project's mutation-data branch.
     *
     * @param projectDir the project checkout (not the mutant working copy)
     * @param fetch      whether to fetch the branch from origin first
     * @return the snapshot, or null when the project has no such branch
     */
    public static Snapshot load(Path projectDir, boolean fetch) {
        if (!isGitRepository(projectDir)) {
            return null;
        }

        if (fetch) {
            Git.Result fetched = fetchBranch(projectDir, BRANCH);
            if (!fetched.ok() && !fetched.stderr().contains("couldn't find remote ref")) {
                System.err.println("WARNING: Could not fetch the " + BRANCH + " branch: " + fetched.stderr().trim());
            }
        }

        String ref = findRef(projectDir);
        if (ref == null) {
            return null;
        }

        Set<String> ids = new LinkedHashSet<>();
        Git.Result file = Git.run(projectDir, "show", ref + ":" + FILE);
        if (file.ok()) {
            try {
                ids.addAll(parseEquivalentsFile(file.stdout()));
            } catch (IllegalArgumentException e) {
                System.err.println("WARNING: " + FILE + " on " + BRANCH + " is not valid: " + e.getMessage());
            }
        } else {
            System.err.println("WARNING: " + BRANCH + " has no " + FILE + "; starting from an empty list.");
        }

        int applied = 0;
        for (String path : listPendingFiles(projectDir, ref)) {
            Git.Result batchFile = Git.run(projectDir, "show", ref + ":" + path);
            if (!batchFile.ok()) {
                continue;
            }
            try {
                applyBatch(ids, parseBatch(batchFile.stdout()));
                applied++;
            } catch (IllegalArgumentException e) {
                System.err.println("WARNING: Skipping " + path + " on " + BRANCH + ": " + e.getMessage());
            }
        }

        String description = BRANCH + " branch (" + ids.size() + " mutant" + (ids.size() == 1 ? "" : "s")
                + (applied > 0 ? ", including " + applied + " pending batch" + (applied == 1 ? "" : "es") : "")
                + ")";
        return new Snapshot(ids, Source.BRANCH, description);
    }

    public static boolean isGitRepository(Path dir) {
        Git.Result result = Git.run(dir, "rev-parse", "--is-inside-work-tree");
        return result.ok() && result.stdout().trim().equals("true");
    }

    /**
     * Fetches just the data branch into refs/remotes/origin/. Shallow CI
     * checkouts only map the checked-out branch, so the refspec is explicit.
     * A full clone is never made shallow.
     */
    public static Git.Result fetchBranch(Path dir, String branch) {
        List<String> args = new ArrayList<>(List.of("fetch", "--quiet", "--no-tags"));
        Git.Result shallow = Git.run(dir, "rev-parse", "--is-shallow-repository");
        if (shallow.ok() && shallow.stdout().trim().equals("true")) {
            args.add("--depth=1");
        }
        args.add(REMOTE);
        args.add("+refs/heads/" + branch + ":refs/remotes/" + REMOTE + "/" + branch);
        return Git.run(dir, args.toArray(String[]::new));
    }

    private static String findRef(Path dir) {
        for (String ref : List.of("refs/remotes/" + REMOTE + "/" + BRANCH, "refs/heads/" + BRANCH)) {
            if (Git.run(dir, "rev-parse", "--verify", "--quiet", ref + "^{commit}").ok()) {
                return ref;
            }
        }
        return null;
    }

    /** Pending batch paths on the given ref, oldest first (names start with a timestamp). */
    public static List<String> listPendingFiles(Path dir, String ref) {
        Git.Result listing = Git.run(dir, "ls-tree", "--name-only", ref, PENDING_DIR + "/");
        List<String> paths = new ArrayList<>();
        if (!listing.ok()) {
            return paths;
        }
        for (String line : listing.stdout().split("\\R")) {
            String path = line.trim();
            if (path.endsWith(".json")) {
                paths.add(path);
            }
        }
        paths.sort(null);
        return paths;
    }

    /** Parses {"hiddenMutantIds": [...]}. */
    public static Set<String> parseEquivalentsFile(String json) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        return new LinkedHashSet<>(stringList(map.get("hiddenMutantIds"), "hiddenMutantIds"));
    }

    /** Parses {"add": [...], "remove": [...]}; either list may be left out, but not both. */
    public static Batch parseBatch(String json) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("expected a JSON object");
        }
        if (!map.containsKey("add") && !map.containsKey("remove")) {
            throw new IllegalArgumentException("expected an \"add\" or \"remove\" list");
        }
        return new Batch(stringList(map.get("add"), "add"), stringList(map.get("remove"), "remove"));
    }

    public static void applyBatch(Set<String> ids, Batch batch) {
        batch.remove().forEach(ids::remove);
        ids.addAll(batch.add());
    }

    /** Writes the equivalents file in the same layout as the local store. */
    public static String toEquivalentsJson(Collection<String> ids) {
        StringBuilder json = new StringBuilder("{\n  \"hiddenMutantIds\": [");
        int index = 0;
        for (String id : ids) {
            json.append(index++ > 0 ? ",\n    " : "\n    ").append(MiniJson.quote(id));
        }
        json.append(index > 0 ? "\n  ]\n}\n" : "]\n}\n");
        return json.toString();
    }

    private static List<String> stringList(Object value, String name) {
        List<String> result = new ArrayList<>();
        if (value == null) {
            return result;
        }
        if (!(value instanceof List<?> list)) {
            throw new IllegalArgumentException("\"" + name + "\" must be a list");
        }
        for (Object item : list) {
            if (!(item instanceof String s)) {
                throw new IllegalArgumentException("\"" + name + "\" must only contain strings");
            }
            result.add(s);
        }
        return result;
    }
}
