package mutationtester.msl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a .spec file into a list of MutationRule objects.
 *
 * Expected format (verbatim from MathematicalMutations.txt):
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

                // Build the rule
                BinaryOpNode fromExpr = (BinaryOpNode) MSLExpressionFactory.parse(fromPattern);
                BinaryOpNode toExpr = (BinaryOpNode) MSLExpressionFactory.parse(toPattern);

                String label = groupLabel + ": " + fromExpr.getOperator()
                        + " \u2192 " + toExpr.getOperator();
                rules.add(MutationRuleFactory.create("Replace", fromExpr, toExpr, label));

                if (inverse) {
                    String inverseLabel = groupLabel + ": " + toExpr.getOperator()
                            + " \u2192 " + fromExpr.getOperator();
                    rules.add(MutationRuleFactory.create("Replace", toExpr, fromExpr, inverseLabel));
                }
                continue;
            }

            // Unknown line — skip
            i++;
        }

        return rules;
    }
}
