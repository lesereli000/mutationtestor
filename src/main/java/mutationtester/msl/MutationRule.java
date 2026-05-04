package mutationtester.msl;

import mutationtester.model.MutationPoint;

import java.io.File;

/**
 * A single mutation rule parsed from a .spec file.
 * Implementations include SwapRule (operator replacement).
 */
public interface MutationRule {

    /** Human-readable label used in reports (e.g., "Math Operations: + → -"). */
    String getLabel();

    // --- NEW ARCHITECTURE ---
    
    /**
     * Evaluates this rule against a generic MSLNode extracted by the Language Scanners.
     * Returns a MutationPoint if a match is found and replacement is generated, null otherwise.
     */
    default MutationPoint evaluate(MSLNode dataNode, File sourceFile) {
        return null;
    }

}
