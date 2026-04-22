package mutationtester.msl;

import mutationtester.*;
/**
 * Factory that creates MutationRule instances from parsed .spec file directives.
 * Currently supports "Replace" → SwapRule. Throws for unknown keywords.
 */
public class MutationRuleFactory {

    private MutationRuleFactory() {}

    /**
     * @param keyword   The rule keyword (e.g., "Replace")
     * @param from      The source expression pattern
     * @param to        The target expression pattern
     * @param label     Human-readable label for reports
     */
    public static MutationRule create(String keyword,
                                      BinaryOpNode from,
                                      BinaryOpNode to,
                                      String label) {
        return switch (keyword.trim()) {
            case "Replace" -> new SwapRule(from, to, label);
            default -> throw new IllegalArgumentException(
                "Unknown rule keyword: \"" + keyword + "\". Supported: Replace");
        };
    }
}
