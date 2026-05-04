package mutationtester.msl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a .spec file into a list of MutationRule objects.
 *
 * Expected format (verbatim from OperatorMutations.txt):
 *
 * <Group Name>
 *
 * Replace: {quantity} <op> {quantity}
 * With: {quantity} <op> {quantity}
 * [And: Inverse] ← optional; generates the reverse rule too
 *
 * ... more Replace blocks ...
 *
 * End
 *
 * Lines are trimmed before processing. Blank lines and tab-only lines
 * between blocks are ignored. The group name is the first non-blank line.
 * "End" terminates the file (or EOF also terminates safely).
 */
public class MutationRuleGenerator {

    public static List<MutationRule> generateRules(File specFile) throws IOException {
        List<String> rawLines = Files.readAllLines(specFile.toPath());
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
            groupLabel = lines.get(i);
            i++;
        }

        // Parse Replace blocks until "End" or EOF
        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.equalsIgnoreCase("End") || line.isEmpty()) {
                i++;
                continue;
            }

            if (line.startsWith("Replace:")) {
                String fromPattern = line.substring("Replace:".length()).strip();
                i++;

                // Expect "With: ..."
                while (i < lines.size() && lines.get(i).isEmpty())
                    i++;
                if (i >= lines.size() || !lines.get(i).startsWith("With:")) {
                    throw new IOException("Expected 'With:' after 'Replace:' at line " + (i + 1));
                }
                String toPattern = lines.get(i).substring("With:".length()).strip();
                i++;

                // Optional "And: Inverse"
                boolean inverse = false;
                while (i < lines.size() && lines.get(i).isEmpty())
                    i++;
                if (i < lines.size() && lines.get(i).startsWith("And: Inverse")) {
                    inverse = true;
                    i++;
                }

                MutationRule rule = createRule(fromPattern, toPattern, groupLabel);
                rules.add(rule);

                if (inverse) {
                    MutationRule inverseRule = createRule(toPattern, fromPattern, groupLabel);
                    rules.add(inverseRule);
                }
                continue;
            }

            // Unknown line — skip
            i++;
        }

        return rules;
    }

    private static MutationRule createRule(String fromPattern, String toPattern, String groupLabel) {
        if (fromPattern.equals("{invocation_statement}") && toPattern.equals(";")) {
            return new StatementRemovalRule(groupLabel + ": statement \u2192 ;");
        }

        if (fromPattern.equals("string.IsNullOrEmpty({quantity})")) {
            if (toPattern.equals("{quantity} != null")) {
                return new StringCallReplacementRule(
                        "string.IsNullOrEmpty",
                        "!= null",
                        groupLabel + ": string.IsNullOrEmpty \u2192 not null");
            }
            if (toPattern.equals("{quantity} != \"\"")) {
                return new StringCallReplacementRule(
                        "string.IsNullOrEmpty",
                        "!= \"\"",
                        groupLabel + ": string.IsNullOrEmpty \u2192 not empty");
            }
        }

        MSLNode fromExpr = MSLPatternFactory.parse(fromPattern);
        MSLNode toExpr = MSLPatternFactory.parse(toPattern);
        String label = groupLabel + ": " + fromExpr.getText()
                + " \u2192 " + toExpr.getText();
        return MutationRuleFactory.create("Replace", fromExpr, toExpr, label);
    }
}
