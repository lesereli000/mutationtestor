package mutationtester.msl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Parses a .spec file into a list of MutationRule objects.
 *
 * Supported format:
 *
 * <Group Name>
 *
 * Replace: <pattern>
 * With: <pattern>
 * [Except: <pattern containing {match}>]   ← optional, may repeat
 * [And: Inverse]                           ← optional; generates the reverse rule too
 *
 * Delete: <pattern>
 * [Except: ...]
 *
 * Insert: <pattern>
 * Before: <pattern>    (or After: <pattern>)
 * [Except: ...]
 *
 * Include: <path>      ← another spec file; its rules are added here
 *
 * Group: <name>        ← a nested group, closed by its own End; may nest again
 *   ...rules...
 * End
 *
 * End
 *
 * Only the keywords are read as text; every pattern is parsed into an
 * {@link MSLTree} by {@link MSLPatternParser}. An Except: tree suppresses a
 * mutation when it matches an ancestor of the mutated node with the mutated
 * node in its {match} position.
 *
 * Example:
 *   Replace: {quantity} <= {quantity}
 *   With: {quantity} < {quantity}
 *   Except: if ({match: {quantity} <= 0}) {quantity} = 0
 *   And: Inverse
 */
public class MutationRuleGenerator {

    private static final String REPLACE = "Replace:";
    private static final String WITH = "With:";
    private static final String DELETE = "Delete:";
    private static final String INSERT = "Insert:";
    private static final String BEFORE = "Before:";
    private static final String AFTER = "After:";
    private static final String EXCEPT = "Except:";
    private static final String INVERSE = "And: Inverse";
    private static final String INCLUDE = "Include:";
    private static final String GROUP = "Group:";

    private static final List<String> RULE_KEYWORDS = List.of(REPLACE, DELETE, INSERT);
    private static final List<String> CLAUSE_KEYWORDS = List.of(WITH, BEFORE, AFTER, EXCEPT, INVERSE);

    /**
     * Reads a spec file. A mistake in it is an IOException whose message is one
     * line naming the file, line and fix, e.g.
     * {@code my.spec, line 3: Unsupported MSL operator "LE". Use the canonical operator "<=".}
     */
    public static List<MutationRule> generateRules(File specFile) throws IOException {
        return loadFile(specFile, new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    /** Parses spec text that is already in memory; errors read "line N: problem. fix". */
    public static List<MutationRule> generateRules(List<String> rawLines) throws IOException {
        return parseLines(rawLines, null, null, new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    private static List<MutationRule> loadFile(File specFile, Set<String> stack, Set<String> done)
            throws IOException {
        if (!specFile.isFile()) {
            throw new IOException("Spec file not found: " + specFile.getPath());
        }
        String key = "file:" + specFile.getCanonicalPath();
        if (done.contains(key)) {
            return List.of();
        }
        if (!stack.add(key)) {
            throw new IOException(specFile.getName() + ", circular Include of \"" + specFile.getPath() + "\".");
        }
        try {
            List<MutationRule> rules = parseLines(Files.readAllLines(specFile.toPath()), specFile, null, stack, done);
            done.add(key);
            return rules;
        } catch (SpecError e) {
            throw new IOException(specFile.getName() + ", " + e.getMessage(), e);
        } finally {
            stack.remove(key);
        }
    }

    private static List<MutationRule> parseLines(List<String> rawLines, File fromFile, String resourceDir,
            Set<String> stack, Set<String> done) throws IOException {
        List<String> lines = rawLines.stream()
                .map(String::strip)
                .toList();

        List<MutationRule> rules = new ArrayList<>();

        int i = 0;
        // The open groups, outermost first; labels join them ("Conditionals > Boundary").
        List<String> groups = new ArrayList<>();
        // The line of each open nested Group:, so a missing End can name it.
        List<Integer> nestedLines = new ArrayList<>();
        boolean groupEnded = false;
        // First non-blank line is the group label, unless it is Include:.
        while (i < lines.size() && lines.get(i).isEmpty())
            i++;
        if (i < lines.size() && !lines.get(i).startsWith(INCLUDE)) {
            groups.add(lines.get(i).startsWith(GROUP)
                    ? groupName(lines.get(i), i + 1)
                    : toGroupLabel(lines.get(i)));
            i++;
        } else {
            groupEnded = true;
        }

        // A spec may hold several groups, each closed by "End"; the first
        // non-blank line after an "End" names the next group. Inside a group,
        // "Group: <name>" opens a nested group, closed by its own End.
        while (i < lines.size()) {
            String line = lines.get(i);
            String groupLabel = String.join(" > ", groups);

            if (line.equalsIgnoreCase("End")) {
                if (nestedLines.isEmpty()) {
                    groupEnded = true;
                } else {
                    groups.remove(groups.size() - 1);
                    nestedLines.remove(nestedLines.size() - 1);
                }
                i++;
                continue;
            }
            if (line.startsWith(GROUP)) {
                String name = groupName(line, i + 1);
                if (groupEnded) {
                    // Between groups, Group: just names the next one
                    groups.clear();
                    groupEnded = false;
                } else {
                    nestedLines.add(i + 1);
                }
                groups.add(name);
                i++;
                continue;
            }
            if (line.isEmpty()) {
                i++;
                continue;
            }
            if (line.startsWith(INCLUDE)) {
                rules.addAll(includeFrom(line.substring(INCLUDE.length()).strip(), i + 1,
                        fromFile, resourceDir, stack, done));
                i++;
                continue;
            }
            String keyword = keywordOf(line, RULE_KEYWORDS);
            if (keyword == null && keywordOf(line, CLAUSE_KEYWORDS) == null && looksLikeKeyword(line)) {
                // A misspelled keyword would otherwise be skipped as a note,
                // silently dropping the rule.
                throw error(i + 1, "Unknown keyword '" + line.substring(0, line.indexOf(':') + 1)
                        + "'. Rules start with \"Replace:\", \"Delete:\" or \"Insert:\";"
                        + " \"Include:\" links another spec file and \"Group:\" opens a nested group.");
            }
            if (groupEnded && keyword == null) {
                groups.clear();
                groups.add(toGroupLabel(line));
                groupEnded = false;
                i++;
                continue;
            }
            groupEnded = false;

            if (keyword == null) {
                String misplaced = keywordOf(line, CLAUSE_KEYWORDS);
                if (misplaced != null) {
                    throw error(i + 1, "\"" + misplaced + "\" must follow a Replace:, Delete: or Insert: line.");
                }
                // A rule missing its colon ("Replace {quantity} ...") would be skipped as a note.
                if (line.contains("{quantity}")) {
                    throw error(i + 1, "\"" + line + "\" isn't part of a rule."
                            + " A rule starts with \"Replace:\", \"Delete:\" or \"Insert:\".");
                }
                // Anything else (notes, comments) is skipped.
                i++;
                continue;
            }

            int ruleLine = i + 1;
            MSLTree target = parse(line.substring(keyword.length()), ruleLine);
            i = skipBlank(lines, i + 1);

            switch (keyword) {
                case REPLACE -> {
                    if (i >= lines.size() || !lines.get(i).startsWith(WITH)) {
                        throw error(ruleLine, "\"Replace:\" has no \"With:\" line after it."
                                + " Add one, e.g. \"With: {quantity} - {quantity}\".");
                    }
                    MSLTree to = parse(lines.get(i).substring(WITH.length()), i + 1);
                    checkPlaceholdersBound(to, target, i + 1);
                    i = skipBlank(lines, i + 1);
                    List<MSLTree> excepts = new ArrayList<>();
                    i = readExcepts(lines, i, excepts);
                    boolean inverse = i < lines.size() && lines.get(i).startsWith(INVERSE);
                    if (inverse) i++;

                    rules.add(new ReplaceRule(target, to, excepts, groupLabel + ": " + describeReplace(target, to)));
                    if (inverse) {
                        // The inverse rule inherits the same Except: clauses, so guarded
                        // contexts (e.g. clamp/guard patterns) are also suppressed when
                        // the operator is mutated in the opposite direction.
                        checkPlaceholdersBound(target, to, ruleLine);
                        rules.add(new ReplaceRule(to, target, excepts, groupLabel + ": " + describeReplace(to, target)));
                    }
                }
                case DELETE -> {
                    List<MSLTree> excepts = new ArrayList<>();
                    i = readExcepts(lines, i, excepts);
                    rules.add(new DeleteRule(target, excepts, groupLabel + ": delete " + describe(target)));
                }
                case INSERT -> {
                    boolean before = i < lines.size() && lines.get(i).startsWith(BEFORE);
                    boolean after = i < lines.size() && lines.get(i).startsWith(AFTER);
                    if (!before && !after) {
                        throw error(ruleLine, "\"Insert:\" has no \"Before:\" or \"After:\" line after it.");
                    }
                    MSLTree anchor = parse(lines.get(i).substring((before ? BEFORE : AFTER).length()), i + 1);
                    checkPlaceholdersBound(target, anchor, ruleLine);
                    i = skipBlank(lines, i + 1);
                    if (i < lines.size() && (lines.get(i).startsWith(BEFORE) || lines.get(i).startsWith(AFTER))) {
                        throw error(i + 1, "An Insert: takes either \"Before:\" or \"After:\", not both.");
                    }
                    List<MSLTree> excepts = new ArrayList<>();
                    i = readExcepts(lines, i, excepts);
                    rules.add(new InsertRule(target, anchor, before, excepts, groupLabel + ": insert "
                            + describe(target) + (before ? " before " : " after ") + describe(anchor)));
                }
                default -> throw new IllegalStateException(keyword);
            }
            if (i < lines.size() && lines.get(i).startsWith(INVERSE)) {
                throw error(i + 1, "\"And: Inverse\" is only allowed on a Replace: rule.");
            }
        }

        // A top-level group may run to the end of the file, as it always could,
        // but a nested one without its End would swallow the groups after it.
        if (!nestedLines.isEmpty()) {
            int open = nestedLines.get(nestedLines.size() - 1);
            throw error(open, "\"Group: " + groups.get(groups.size() - 1) + "\" has no End."
                    + " Close each nested group with its own End line.");
        }

        return rules;
    }

    /** The name on a "Group: <name>" line. */
    private static String groupName(String line, int lineNumber) throws IOException {
        String name = toGroupLabel(line.substring(GROUP.length()));
        if (name.isEmpty()) {
            throw error(lineNumber, "Group: needs a name, e.g. \"Group: Boundary\".");
        }
        return name;
    }

    private static int readExcepts(List<String> lines, int i, List<MSLTree> excepts) throws IOException {
        while (i < lines.size() && lines.get(i).startsWith(EXCEPT)) {
            MSLTree except = parse(lines.get(i).substring(EXCEPT.length()), i + 1);
            int matches = countMatchPlaceholders(except);
            if (matches != 1) {
                throw error(i + 1, "An Except: pattern must contain exactly one {match}; this one has " + matches + ".");
            }
            excepts.add(except);
            i = skipBlank(lines, i + 1);
        }
        return i;
    }

    private static MSLTree parse(String pattern, int lineNumber) throws IOException {
        MSLTree tree;
        try {
            tree = MSLPatternParser.parse(pattern);
        } catch (IllegalArgumentException e) {
            throw error(lineNumber, e.getMessage());
        }
        checkKnownTypes(tree, lineNumber);
        return tree;
    }

    /** A misspelled placeholder would match nothing, silently: reject it. */
    private static void checkKnownTypes(MSLTree tree, int lineNumber) throws IOException {
        if (tree instanceof MSLPlaceholder p) {
            if (!MSLPlaceholder.isKnownType(p.type())) {
                java.util.Set<String> known = new java.util.TreeSet<>(MSLPlaceholder.INPUT_TYPES);
                known.addAll(MSLPlaceholder.OUTPUT_TYPES);
                throw error(lineNumber, "Unknown placeholder {" + p.type() + "}. Placeholders are "
                        + String.join(", ", known) + ".");
            }
            if (p.constraint() != null) checkKnownTypes(p.constraint(), lineNumber);
            return;
        }
        for (MSLTree child : ((MSLNode) tree).children()) checkKnownTypes(child, lineNumber);
    }

    /** A spec mistake, "line N: problem. fix"; the file-based entry point adds the file name. */
    private static final class SpecError extends IOException {
        SpecError(int line, String message) {
            super("line " + line + ": " + message);
        }
    }

    private static SpecError error(int line, String message) {
        return new SpecError(line, message);
    }

    /**
     * Loads the spec named on an Include: line. Paths are tried relative to the
     * including file, then the working directory, then under specs/ on disk and
     * on the classpath (so a bundled jar can Include: OperatorMutations.spec).
     * A file already loaded in this run is skipped, so a diamond of includes
     * does not duplicate rules. A cycle is an error.
     */
    private static List<MutationRule> includeFrom(String path, int lineNumber, File fromFile,
            String resourceDir, Set<String> stack, Set<String> done) throws IOException {
        if (path.isEmpty()) {
            throw error(lineNumber, "Include: needs a spec file path.");
        }
        File disk = resolveDisk(path, fromFile);
        if (disk != null) {
            String key = "file:" + disk.getCanonicalPath();
            if (done.contains(key)) {
                return List.of();
            }
            if (stack.contains(key)) {
                throw error(lineNumber, "circular Include of \"" + path + "\".");
            }
            return loadFile(disk, stack, done);
        }
        String resource = resolveResource(path, fromFile, resourceDir);
        if (resource != null) {
            String key = "resource:" + resource;
            if (done.contains(key)) {
                return List.of();
            }
            if (stack.contains(key)) {
                throw error(lineNumber, "circular Include of \"" + path + "\".");
            }
            return loadResource(resource, stack, done);
        }
        throw error(lineNumber, "Include: spec file not found: \"" + path + "\".");
    }

    private static File resolveDisk(String path, File fromFile) {
        List<File> candidates = new ArrayList<>();
        if (fromFile != null && fromFile.getParentFile() != null) {
            candidates.add(new File(fromFile.getParentFile(), path));
        }
        candidates.add(new File(path));
        String slash = path.replace('\\', '/');
        if (!slash.startsWith("specs/")) {
            candidates.add(new File("specs", slash));
        }
        for (File candidate : candidates) {
            if (candidate.isFile()) {
                try {
                    return candidate.getCanonicalFile();
                } catch (IOException e) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String resolveResource(String path, File fromFile, String resourceDir) {
        String slash = path.replace('\\', '/');
        List<String> candidates = new ArrayList<>();
        if (resourceDir != null && !resourceDir.isEmpty()) {
            candidates.add(resourceDir + "/" + slash);
        }
        if (fromFile != null && fromFile.getParentFile() != null) {
            Path parent = fromFile.getParentFile().toPath().toAbsolutePath().normalize();
            Path specsRoot = Path.of("specs").toAbsolutePath().normalize();
            if (parent.startsWith(specsRoot)) {
                Path relative = specsRoot.relativize(parent.resolve(path).normalize());
                if (!relative.startsWith("..")) {
                    candidates.add("specs/" + relative.toString().replace('\\', '/'));
                }
            }
        }
        candidates.add(slash.startsWith("specs/") ? slash : "specs/" + slash);
        ClassLoader loader = MutationRuleGenerator.class.getClassLoader();
        for (String raw : candidates) {
            String resource = normalizeResource(raw);
            if (resource != null && loader.getResource(resource) != null) {
                return resource;
            }
        }
        return null;
    }

    private static String normalizeResource(String resource) {
        String normalized = Path.of(resource).normalize().toString().replace('\\', '/');
        if (normalized.isEmpty() || normalized.startsWith("..") || normalized.startsWith("/")) {
            return null;
        }
        return normalized;
    }

    private static List<MutationRule> loadResource(String resource, Set<String> stack, Set<String> done)
            throws IOException {
        String key = "resource:" + resource;
        if (done.contains(key)) {
            return List.of();
        }
        if (!stack.add(key)) {
            throw new IOException("circular Include of \"" + resource + "\".");
        }
        String dir = resource.contains("/") ? resource.substring(0, resource.lastIndexOf('/')) : "";
        String name = resource.substring(resource.lastIndexOf('/') + 1);
        try (InputStream in = MutationRuleGenerator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Spec file not found: " + resource);
            }
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toList();
            List<MutationRule> rules = parseLines(lines, null, dir, stack, done);
            done.add(key);
            return rules;
        } catch (SpecError e) {
            throw new IOException(name + ", " + e.getMessage(), e);
        } finally {
            stack.remove(key);
        }
    }

    private static int skipBlank(List<String> lines, int i) {
        while (i < lines.size() && lines.get(i).isEmpty()) i++;
        return i;
    }

    private static String keywordOf(String line, List<String> keywords) {
        for (String keyword : keywords) {
            if (line.startsWith(keyword)) return keyword;
        }
        return null;
    }

    /**
     * Each placeholder that {@code uses} reads back must have been matched by
     * {@code source}. A type the source never matches is an output placeholder,
     * such as {empty_value}, whose value the adapter supplies.
     */
    private static void checkPlaceholdersBound(MSLTree uses, MSLTree source, int lineNumber) throws IOException {
        java.util.Map<String, Integer> available = new java.util.HashMap<>();
        countPlaceholders(source, available);
        java.util.Map<String, Integer> used = new java.util.HashMap<>();
        countPlaceholders(uses, used);
        java.util.Map<String, String> sourceNames = new java.util.HashMap<>();
        collectNames(source, sourceNames);
        java.util.Map<String, String> usedNames = new java.util.HashMap<>();
        collectNames(uses, usedNames);
        for (var entry : usedNames.entrySet()) {
            String type = sourceNames.get(entry.getKey());
            if (type == null) {
                throw error(lineNumber, "{" + entry.getValue() + " " + entry.getKey()
                        + "} refers back to the pattern, which has no placeholder named " + entry.getKey() + ".");
            }
            if (!type.equals(entry.getValue()) && (MSLPlaceholder.familyOf(type) == null
                    || MSLPlaceholder.familyOf(type) != MSLPlaceholder.familyOf(entry.getValue()))) {
                throw error(lineNumber, "{" + entry.getValue() + " " + entry.getKey() + "} uses " + entry.getKey()
                        + " as a {" + entry.getValue() + "}, but the pattern has it as a {" + type + "}.");
            }
        }
        // Number and text placeholders pair by position within their family,
        // counting named ones in the pattern too.
        java.util.Map<String, Integer> all = new java.util.HashMap<>();
        countAllPlaceholders(source, all);
        for (java.util.Set<String> family : MSLPlaceholder.FAMILIES) {
            int have = 0;
            int want = 0;
            for (String type : family) {
                have += all.getOrDefault(type, 0);
                want += used.getOrDefault(type, 0);
            }
            if (want > have) {
                throw error(lineNumber, "Writes " + want + " number or text placeholder(s) but the pattern"
                        + " matches " + have + ", so there is nothing to convert.");
            }
        }
        for (var entry : used.entrySet()) {
            if (MSLPlaceholder.familyOf(entry.getKey()) != null) continue;
            Integer have = available.get(entry.getKey());
            if (have != null && entry.getValue() > have) {
                throw error(lineNumber, "Uses {" + entry.getKey() + "} " + entry.getValue()
                        + " times but the pattern matches only " + have + ".");
            }
        }
    }

    private static void countAllPlaceholders(MSLTree tree, java.util.Map<String, Integer> counts) {
        if (tree instanceof MSLPlaceholder p) {
            if (!p.isMatch()) counts.merge(p.type(), 1, Integer::sum);
        } else {
            for (MSLTree child : ((MSLNode) tree).children()) countAllPlaceholders(child, counts);
        }
    }

    /** Named placeholders and their types. */
    private static void collectNames(MSLTree tree, java.util.Map<String, String> names) {
        if (tree instanceof MSLPlaceholder p) {
            if (p.isNamed()) names.putIfAbsent(p.name(), p.type());
        } else {
            for (MSLTree child : ((MSLNode) tree).children()) collectNames(child, names);
        }
    }

    private static void countPlaceholders(MSLTree tree, java.util.Map<String, Integer> counts) {
        if (tree instanceof MSLPlaceholder p) {
            // Named placeholders refer back by name, not by position.
            if (!p.isMatch() && !p.isNamed()) counts.merge(p.type(), 1, Integer::sum);
        } else {
            for (MSLTree child : ((MSLNode) tree).children()) countPlaceholders(child, counts);
        }
    }

    private static int countMatchPlaceholders(MSLTree tree) {
        if (tree instanceof MSLPlaceholder p) {
            // {match} itself, and any inside a constraint ({contains: {match}}).
            int inside = p.constraint() == null ? 0 : countMatchPlaceholders(p.constraint());
            return (p.isMatch() ? 1 : 0) + inside;
        }
        int n = 0;
        for (MSLTree child : ((MSLNode) tree).children()) n += countMatchPlaceholders(child);
        return n;
    }

    /** "Word: text", the shape of a keyword line ("Remove: {quantity}++"). */
    private static boolean looksLikeKeyword(String line) {
        int colon = line.indexOf(':');
        if (colon <= 0 || colon == line.length() - 1) return false;
        for (int c = 0; c < colon; c++) {
            if (!Character.isLetter(line.charAt(c))) return false;
        }
        return true;
    }

    /** Group headings may be written with a trailing colon ("Math Operators:"). */
    private static String toGroupLabel(String heading) {
        String label = heading.strip();
        return label.endsWith(":") ? label.substring(0, label.length() - 1).strip() : label;
    }

    /** "+ → -" for operator swaps, otherwise both trees spelled out. */
    private static String describeReplace(MSLTree from, MSLTree to) {
        if (isOperatorOnPlaceholders(from) && isOperatorOnPlaceholders(to)) {
            return ((MSLNode) from).value() + " → " + ((MSLNode) to).value();
        }
        return describe(from) + " → " + describe(to);
    }

    private static boolean isOperatorOnPlaceholders(MSLTree tree) {
        return tree instanceof MSLNode node && node.kind().equals(MSLNode.BINARY)
                && node.children().stream().allMatch(c -> c instanceof MSLPlaceholder);
    }

    private static String describe(MSLTree tree) {
        return tree instanceof MSLPlaceholder p && p.constraint() == null ? p.describe() : tree.toSpecText();
    }
}
