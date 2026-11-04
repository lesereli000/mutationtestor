package mutationtester.msl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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

    private static final List<String> RULE_KEYWORDS = List.of(REPLACE, DELETE, INSERT);
    private static final List<String> CLAUSE_KEYWORDS = List.of(WITH, BEFORE, AFTER, EXCEPT, INVERSE);

    public static List<MutationRule> generateRules(File specFile) throws IOException {
        return generateRules(Files.readAllLines(specFile.toPath()));
    }

    /** Parses spec text that is already in memory. */
    public static List<MutationRule> generateRules(List<String> rawLines) throws IOException {
        List<String> lines = rawLines.stream()
                .map(String::strip)
                .toList();

        List<MutationRule> rules = new ArrayList<>();

        int i = 0;
        // First non-blank line is the group label
        String groupLabel = "";
        while (i < lines.size() && lines.get(i).isEmpty())
            i++;
        if (i < lines.size()) {
            groupLabel = toGroupLabel(lines.get(i));
            i++;
        }

        // A spec may hold several groups, each closed by "End"; the first
        // non-blank line after an "End" names the next group.
        boolean groupEnded = false;
        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.equalsIgnoreCase("End")) {
                groupEnded = true;
                i++;
                continue;
            }
            if (line.isEmpty()) {
                i++;
                continue;
            }
            String keyword = keywordOf(line, RULE_KEYWORDS);
            if (keyword == null && keywordOf(line, CLAUSE_KEYWORDS) == null && looksLikeKeyword(line)) {
                // A misspelled keyword would otherwise be skipped as a note,
                // silently dropping the rule.
                throw new IOException("Unknown keyword '" + line.substring(0, line.indexOf(':') + 1) + "' at line "
                        + (i + 1) + "; rules start with Replace:, Delete: or Insert:");
            }
            if (groupEnded && keyword == null) {
                groupLabel = toGroupLabel(line);
                groupEnded = false;
                i++;
                continue;
            }
            groupEnded = false;

            if (keyword == null) {
                String misplaced = keywordOf(line, CLAUSE_KEYWORDS);
                if (misplaced != null) {
                    throw new IOException("'" + misplaced + "' at line " + (i + 1)
                            + " must follow a Replace:, Delete: or Insert: line");
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
                        throw new IOException("Expected 'With:' after 'Replace:' at line " + (i + 1));
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
                        throw new IOException("Expected 'Before:' or 'After:' after 'Insert:' at line " + (i + 1));
                    }
                    MSLTree anchor = parse(lines.get(i).substring((before ? BEFORE : AFTER).length()), i + 1);
                    checkPlaceholdersBound(target, anchor, ruleLine);
                    i = skipBlank(lines, i + 1);
                    if (i < lines.size() && (lines.get(i).startsWith(BEFORE) || lines.get(i).startsWith(AFTER))) {
                        throw new IOException("An Insert: takes either 'Before:' or 'After:', not both (line " + (i + 1) + ")");
                    }
                    List<MSLTree> excepts = new ArrayList<>();
                    i = readExcepts(lines, i, excepts);
                    rules.add(new InsertRule(target, anchor, before, excepts, groupLabel + ": insert "
                            + describe(target) + (before ? " before " : " after ") + describe(anchor)));
                }
                default -> throw new IllegalStateException(keyword);
            }
            if (i < lines.size() && lines.get(i).startsWith(INVERSE)) {
                throw new IOException("'And: Inverse' at line " + (i + 1) + " is only allowed on a Replace: rule");
            }
        }

        return rules;
    }

    private static int readExcepts(List<String> lines, int i, List<MSLTree> excepts) throws IOException {
        while (i < lines.size() && lines.get(i).startsWith(EXCEPT)) {
            MSLTree except = parse(lines.get(i).substring(EXCEPT.length()), i + 1);
            int matches = countMatchPlaceholders(except);
            if (matches != 1) {
                throw new IOException("An Except: pattern must contain exactly one {match} (line " + (i + 1)
                        + " has " + matches + ")");
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
            throw new IOException(e.getMessage() + " (line " + lineNumber + ")", e);
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
                throw new IOException("Unknown placeholder {" + p.type() + "} at line " + lineNumber
                        + "; placeholders are " + String.join(", ", known));
            }
            if (p.constraint() != null) checkKnownTypes(p.constraint(), lineNumber);
            return;
        }
        for (MSLTree child : ((MSLNode) tree).children()) checkKnownTypes(child, lineNumber);
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
                throw new IOException("Line " + lineNumber + " uses {" + entry.getValue() + " " + entry.getKey()
                        + "} but the pattern it refers to has no placeholder named " + entry.getKey());
            }
            if (!type.equals(entry.getValue()) && (MSLPlaceholder.familyOf(type) == null
                    || MSLPlaceholder.familyOf(type) != MSLPlaceholder.familyOf(entry.getValue()))) {
                throw new IOException("Line " + lineNumber + " uses {" + entry.getValue() + " " + entry.getKey()
                        + "} but " + entry.getKey() + " is a {" + type + "}");
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
                throw new IOException("Line " + lineNumber + " writes " + want + " number or text placeholder(s) but"
                        + " the pattern it refers to matches " + have + ", so there is nothing to convert");
            }
        }
        for (var entry : used.entrySet()) {
            if (MSLPlaceholder.familyOf(entry.getKey()) != null) continue;
            Integer have = available.get(entry.getKey());
            if (have != null && entry.getValue() > have) {
                throw new IOException("Line " + lineNumber + " uses {" + entry.getKey() + "} " + entry.getValue()
                        + " times but the pattern it refers to matches only " + have);
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
