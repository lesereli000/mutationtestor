package mutationtester.safety;

/** The policy-enforced result of a requested safety action. */
public record SafetyDecision(SafetyAction action, boolean permitted, String explanation) {
}
