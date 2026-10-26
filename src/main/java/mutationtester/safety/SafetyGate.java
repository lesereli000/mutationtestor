package mutationtester.safety;

import java.util.Objects;

/**
 * Research prototype for enforcing block/skip/continue decisions.
 * It is intentionally not connected to mutation execution yet.
 */
public final class SafetyGate {
    private SafetyGate() {
    }

    public static SafetyDecision decide(
            SafetyAssessment assessment,
            SafetyAction requestedAction,
            boolean userConfirmed) {
        Objects.requireNonNull(assessment, "assessment");
        Objects.requireNonNull(requestedAction, "requestedAction");

        if (requestedAction == SafetyAction.ABORT) {
            return allowed(requestedAction, "The mutation run will stop.");
        }
        if (requestedAction == SafetyAction.SKIP) {
            return allowed(requestedAction, "The mutant will not be executed.");
        }

        return switch (assessment.level()) {
            case SAFE -> allowed(requestedAction, "The assessor found no elevated risk.");
            case REVIEW, UNKNOWN -> userConfirmed
                    ? allowed(requestedAction, "The user explicitly approved this mutant once.")
                    : denied(requestedAction, "Explicit user approval is required before continuing.");
            case BLOCK -> denied(requestedAction, "Blocked mutants cannot be executed.");
        };
    }

    /** Default for CI and other unattended runs: only SAFE mutants continue. */
    public static SafetyAction unattendedAction(SafetyAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        return assessment.level() == SafetyLevel.SAFE
                ? SafetyAction.CONTINUE
                : SafetyAction.SKIP;
    }

    public static String consolePrompt(SafetyAssessment assessment) {
        Objects.requireNonNull(assessment, "assessment");
        String options = assessment.level() == SafetyLevel.BLOCK
                ? "[S] Skip mutant  [A] Abort run"
                : "[C] Continue once  [S] Skip mutant  [A] Abort run";

        return """
                SAFETY REVIEW: %s
                Category: %s
                Reason: %s
                Assessor: %s (confidence %.0f%%)
                %s
                """.formatted(
                assessment.level(),
                assessment.category(),
                assessment.reason(),
                assessment.assessor(),
                assessment.confidence() * 100.0,
                options).stripTrailing();
    }

    private static SafetyDecision allowed(SafetyAction action, String explanation) {
        return new SafetyDecision(action, true, explanation);
    }

    private static SafetyDecision denied(SafetyAction action, String explanation) {
        return new SafetyDecision(action, false, explanation);
    }
}
