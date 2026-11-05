package mutationtester;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import mutationtester.safety.*;
import org.junit.jupiter.api.Test;

class OpenAiSafetyReviewerTests {
    private final SafetyAssessment unknown = new SafetyAssessment(SafetyLevel.UNKNOWN, "UNRESOLVED", "Not established.", 0, "local");

    private static String response(String label, String reason) {
        return "{\"status\":\"completed\",\"output\":[{\"role\":\"assistant\",\"type\":\"message\",\"status\":\"completed\",\"content\":[{\"text\":\"{\\\"judgment\\\":\\\"" + label + "\\\",\\\"reason\\\":\\\"" + reason + "\\\"}\",\"type\":\"output_text\"}]}]}";
    }

    @Test void modelIdentifiesContextualRiskWithoutAuthorizingExecution() {
        OpenAiSafetyReviewer reviewer = new OpenAiSafetyReviewer("test-key", "test-model", (request, key) -> {
            assertEquals("test-key", key);
            assertTrue(request.contains("\\n"));
            assertTrue(request.contains("\"store\":false"));
            assertTrue(request.contains("\"json_schema\""));
            assertTrue(request.contains("python3 -m unittest"));
            return response("RISK", "The altered guard can send an invoice twice.");
        });
        SafetyAssessment result = reviewer.assess("if (once) invoice();", "if (!once) invoice();", unknown, "invoice.py", "", "python3 -m unittest");
        assertEquals(SafetyLevel.REVIEW, result.level());
        assertTrue(result.reason().contains("invoice twice"));
        assertEquals(SafetyAction.SKIP, SafetyGate.unattendedAction(result));
    }

    @Test void modelCannotApproveAnUncertainMutation() {
        OpenAiSafetyReviewer reviewer = new OpenAiSafetyReviewer("k", "m", (request, key) -> response("UNCERTAIN", "Need project context."));
        assertEquals(SafetyLevel.UNKNOWN, reviewer.assess("a", "b", unknown).level());
    }

    @Test void failuresAndUnexpectedAnswersFailClosed() {
        OpenAiSafetyReviewer failure = new OpenAiSafetyReviewer("k", "m", (request, key) -> { throw new IOException("offline"); });
        assertEquals(SafetyLevel.UNKNOWN, failure.assess("a", "b", unknown).level());
        OpenAiSafetyReviewer malformed = new OpenAiSafetyReviewer("k", "m", (request, key) -> response("SAFE", "run it"));
        assertEquals(SafetyLevel.UNKNOWN, malformed.assess("a", "b", unknown).level());
        SafetyAssessment review = new SafetyAssessment(SafetyLevel.REVIEW, "FILE", "Needs review.", 0, "local");
        assertEquals(SafetyLevel.REVIEW, malformed.assess("a", "b", review).level());
    }

    @Test void controllerRequestsOnlyOneAssessmentPerDecision() {
        AtomicInteger calls = new AtomicInteger();
        OpenAiSafetyReviewer reviewer = new OpenAiSafetyReviewer("k", "m", (request, key) -> {
            calls.incrementAndGet();
            return response("RISK", "The behavior may send money.");
        });
        SafetyController controller = new SafetyController(null, System.out, reviewer);
        mutationtester.model.MutationPoint p = new mutationtester.model.MutationPoint(new java.io.File("example.py"), 1, 0, "x", "y", mutationtester.model.Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        SafetyController.Outcome outcome = controller.decideWithAssessment(p, "if ready: pay()", "if not ready: pay()");
        assertEquals(SafetyAction.SKIP, outcome.action());
        assertEquals(1, calls.get());
        assertTrue(outcome.assessment().reason().contains("send money"));
    }

    @Test void incompleteRefusedAndMalformedResponsesAreNotTrusted() {
        String[] bad = {
            response("RISK", "incomplete").replace("\"status\":\"completed\"", "\"status\":\"incomplete\""),
            "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"refusal\",\"refusal\":\"no\"}]}]}",
            response("RISK", "bad") + "garbage",
            "{\"status\":\"completed\",\"status\":\"completed\",\"output\":[]}"
        };
        for (String body : bad) {
            OpenAiSafetyReviewer reviewer = new OpenAiSafetyReviewer("k", "m", (request, key) -> body);
            SafetyAssessment result = reviewer.assess("a", "b", unknown);
            assertEquals(SafetyLevel.UNKNOWN, result.level());
            assertTrue(result.assessor().contains("unavailable"));
        }
    }
}
