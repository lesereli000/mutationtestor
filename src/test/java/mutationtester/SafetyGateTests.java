package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import mutationtester.safety.SafetyAction;
import mutationtester.safety.SafetyAssessment;
import mutationtester.safety.SafetyDecision;
import mutationtester.safety.SafetyGate;
import mutationtester.safety.SafetyLevel;

class SafetyGateTests {
    @Test
    void safeMutantCanContinueWithoutConfirmation() {
        SafetyDecision decision = SafetyGate.decide(
                assessment(SafetyLevel.SAFE), SafetyAction.CONTINUE, false);

        assertTrue(decision.permitted());
    }

    @Test
    void reviewMutantRequiresExplicitConfirmation() {
        SafetyDecision unconfirmed = SafetyGate.decide(
                assessment(SafetyLevel.REVIEW), SafetyAction.CONTINUE, false);
        SafetyDecision confirmed = SafetyGate.decide(
                assessment(SafetyLevel.REVIEW), SafetyAction.CONTINUE, true);

        assertFalse(unconfirmed.permitted());
        assertTrue(confirmed.permitted());
    }

    @Test
    void blockedMutantCannotContinueEvenWithConfirmation() {
        SafetyDecision decision = SafetyGate.decide(
                assessment(SafetyLevel.BLOCK), SafetyAction.CONTINUE, true);

        assertFalse(decision.permitted());
    }

    @Test
    void unattendedRunsSkipAnythingNotKnownSafe() {
        assertEquals(SafetyAction.CONTINUE,
                SafetyGate.unattendedAction(assessment(SafetyLevel.SAFE)));
        assertEquals(SafetyAction.SKIP,
                SafetyGate.unattendedAction(assessment(SafetyLevel.UNKNOWN)));
        assertEquals(SafetyAction.SKIP,
                SafetyGate.unattendedAction(assessment(SafetyLevel.REVIEW)));
        assertEquals(SafetyAction.SKIP,
                SafetyGate.unattendedAction(assessment(SafetyLevel.BLOCK)));
    }

    @Test
    void blockedPromptDoesNotOfferContinue() {
        String prompt = SafetyGate.consolePrompt(assessment(SafetyLevel.BLOCK));

        assertTrue(prompt.contains("Skip mutant"));
        assertTrue(prompt.contains("Abort run"));
        assertFalse(prompt.contains("Continue once"));
    }

    private static SafetyAssessment assessment(SafetyLevel level) {
        return new SafetyAssessment(
                level,
                "FILE_SYSTEM",
                "The mutant may change a file operation.",
                0.85,
                "prototype-rule");
    }
}
