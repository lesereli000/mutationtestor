package mutationtester.msl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import mutationtester.model.Mutator;

/**
 * Parses a .spec file into a list of MutationRule objects.
 *
 * Supported format:
 *
 * <Group Name>
 *
 * Replace: {quantity} <op> {quantity}
 * With: {quantity} <op> {quantity}
 * [Except: <context pattern>]   ← optional, may repeat
 * [And: Inverse]                ← optional; generates the reverse rule too
 *
 * End
 *
 * The "Except:" clause suppresses the mutation when the source line
 * matches the given context pattern. Multiple Except: lines are allowed.
 *
 * Example:
 *   Replace: {quantity} <= {quantity}
 *   With: {quantity} < {quantity}
 *   Except: if ({quantity} <= 0) {quantity} = 0
 *   And: Inverse
 */
public class MutationRuleGenerator {

    public static List<MutationRule> generateRules(File specFile) throws IOException {
        return generateRules(Files.readAllLines(specFile.toPath()));
    }

    /** Parses spec text that is already in memory (e.g. a spec bundled in the jar). */
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

        // Parse Replace blocks. A spec may hold several groups, each closed by
        // "End"; the first non-blank line after an "End" names the next group.
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
            if (groupEnded && !line.startsWith("Replace:")) {
                groupLabel = toGroupLabel(line);
                groupEnded = false;
                i++;
                continue;
            }
            groupEnded = false;

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

                // Optional "Except: ..." — may appear multiple times
                List<ExceptPattern> exceptPatterns = new ArrayList<>();
                while (i < lines.size()) {
                    while (i < lines.size() && lines.get(i).isEmpty())
                        i++;
                    if (i < lines.size() && lines.get(i).startsWith("Except:")) {
                        String exceptTemplate = lines.get(i).substring("Except:".length()).strip();
                        exceptPatterns.add(new ExceptPattern(exceptTemplate));
                        i++;
                    } else {
                        break;
                    }
                }

                // Optional "And: Inverse"
                boolean inverse = false;
                while (i < lines.size() && lines.get(i).isEmpty())
                    i++;
                if (i < lines.size() && lines.get(i).startsWith("And: Inverse")) {
                    inverse = true;
                    i++;
                }

                MutationRule rule = createRule(fromPattern, toPattern, exceptPatterns, groupLabel);
                rules.add(rule);

                if (inverse) {
                    // The inverse rule inherits the same Except: clauses as the forward rule.
                    // This ensures that guarded contexts (e.g., clamp/guard patterns) are also
                    // suppressed when the operator is mutated in the opposite direction.
                    MutationRule inverseRule = createRule(toPattern, fromPattern,
                            exceptPatterns, groupLabel);
                    rules.add(inverseRule);
                }
                continue;
            }

            // Unknown line — skip
            i++;
        }

        return rules;
    }

    /** Group headings may be written with a trailing colon ("Math Operators:"). */
    private static String toGroupLabel(String heading) {
        String label = heading.strip();
        return label.endsWith(":") ? label.substring(0, label.length() - 1).strip() : label;
    }

    private static final java.util.regex.Pattern FUNC_CALL_PATTERN =
            java.util.regex.Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)\\(\\{quantity\\}\\)$");

    private static MutationRule createRule(String fromPattern, String toPattern,
                                           List<ExceptPattern> exceptPatterns,
                                           String groupLabel) {

        if (fromPattern.equals("{invocation_statement}") && toPattern.equals(";")) {
            return new StatementRemovalRule(groupLabel + ": statement \u2192 ;");
        }

        if (fromPattern.equals("{return_value}") && toPattern.equals("{empty_value}")) {
            return new ScannerBackedRule(
                    Mutator.MutatorType.EMPTY_RETURNS,
                    groupLabel + ": return value \u2192 empty value");
        }

        if (fromPattern.equals("{constructor_call}") && toPattern.equals("null")) {
            return new ScannerBackedRule(
                    Mutator.MutatorType.CONSTRUCTOR_CALLS,
                    groupLabel + ": constructor call \u2192 null");
        }

        if (fromPattern.equals("{non_void_method_call}") && toPattern.equals("{default_value}")) {
            return new ScannerBackedRule(
                    Mutator.MutatorType.NON_VOID_METHOD_CALLS,
                    groupLabel + ": non-void method call \u2192 default value");
        }

        if (fromPattern.equals("{equality_condition}") && toPattern.equals("true")) {
            return new ScannerBackedRule(
                    Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF,
                    groupLabel + ": equality condition \u2192 true");
        }

        if (fromPattern.equals("{equality_condition}") && toPattern.equals("false")) {
            return new ScannerBackedRule(
                    Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE,
                    groupLabel + ": equality condition \u2192 false");
        }

        if (fromPattern.equals("{order_condition}") && toPattern.equals("true")) {
            return new ScannerBackedRule(
                    Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF,
                    groupLabel + ": order condition \u2192 true");
        }

        if (fromPattern.equals("{order_condition}") && toPattern.equals("false")) {
            return new ScannerBackedRule(
                    Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_ELSE,
                    groupLabel + ": order condition \u2192 false");
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

        java.util.regex.Matcher m = FUNC_CALL_PATTERN.matcher(fromPattern);
        if (m.matches()) {
            String funcName = m.group(1);
            String suffix;
            if (toPattern.startsWith("{quantity}")) {
                suffix = toPattern.substring("{quantity}".length()).strip();
            } else {
                suffix = toPattern;
            }
            String label = groupLabel + ": " + funcName + "({quantity}) \u2192 " + toPattern;
            return new StringCallReplacementRule(funcName, suffix, label);
        }

        BinaryOpNode fromExpr = (BinaryOpNode) MSLExpressionFactory.parse(fromPattern);
        BinaryOpNode toExpr = (BinaryOpNode) MSLExpressionFactory.parse(toPattern);
        String label = groupLabel + ": " + fromExpr.getOperator()
                + " \u2192 " + toExpr.getOperator();
        return new ReplacementRule(fromExpr, toExpr, label, exceptPatterns);
    }
}
