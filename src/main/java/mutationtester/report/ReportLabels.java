package mutationtester.report;

/**
 * Shared display names for HTML and console reports.
 * Prefers the spec custom label; never invents AOR_* enum names.
 */
final class ReportLabels {

    private ReportLabels() {}

    static String displayLabel(String customLabel, String original, String replacement) {
        if (customLabel != null && !customLabel.isBlank()) {
            return normalize(customLabel);
        }
        String from = original == null ? "" : original;
        String to = replacement == null ? "" : replacement;
        return normalize(from + " -> " + to);
    }

    /**
     * User-facing mutation kind. {@code CUSTOM} is replaced by the spec group
     * (text before {@code :} in the label) or omitted.
     */
    static String displayType(String mutationType, String customLabel) {
        if (mutationType != null && !mutationType.isBlank() && !"CUSTOM".equals(mutationType)) {
            return mutationType;
        }
        return specGroup(customLabel);
    }

    static String specGroup(String customLabel) {
        if (customLabel == null || customLabel.isBlank()) {
            return null;
        }
        String normalized = normalize(customLabel);
        int colon = normalized.indexOf(':');
        if (colon <= 0) {
            return null;
        }
        String group = normalized.substring(0, colon).strip();
        return group.isEmpty() ? null : group;
    }

    static String normalize(String label) {
        if (label == null) {
            return "";
        }
        return label.replace(" ? ", " -> ").replace(" → ", " -> ");
    }
}
