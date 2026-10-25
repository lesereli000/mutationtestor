package mutationtester.safety;

import java.util.Objects;

/**
 * Structured result returned by a future deterministic or model-based assessor.
 * This type deliberately contains no execution authority.
 */
public record SafetyAssessment(
        SafetyLevel level,
        String category,
        String reason,
        double confidence,
        String assessor) {

    public SafetyAssessment {
        Objects.requireNonNull(level, "level");
        category = requireText(category, "category");
        reason = requireText(reason, "reason");
        assessor = requireText(assessor, "assessor");
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be between 0.0 and 1.0");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
