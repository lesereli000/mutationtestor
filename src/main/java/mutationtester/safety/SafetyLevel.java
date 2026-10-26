package mutationtester.safety;

/** The safety judgment made before a generated mutant is executed. */
public enum SafetyLevel {
    SAFE,
    REVIEW,
    BLOCK,
    UNKNOWN
}
