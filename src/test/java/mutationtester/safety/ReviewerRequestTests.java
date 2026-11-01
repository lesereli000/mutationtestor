package mutationtester.safety;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReviewerRequestTests {
    @Test void sendsValidJsonSchemaAndExecutionContext() {
        SafetyAssessment local = new SafetyAssessment(SafetyLevel.UNKNOWN, "UNKNOWN", "Needs review", 0, "local");
        OpenAiSafetyReviewer reviewer = new OpenAiSafetyReviewer("secret", "example-model", (request, key) -> {
            Map<?, ?> root = (Map<?, ?>) JsonReader.read(request);
            assertEquals("example-model", root.get("model"));
            assertEquals(Boolean.FALSE, root.get("store"));
            assertTrue(((String) root.get("input")).contains("python3 -m unittest"));
            assertTrue(((String) root.get("input")).contains("a\\\"b"));
            Map<?, ?> format = (Map<?, ?>) ((Map<?, ?>) root.get("text")).get("format");
            assertEquals("json_schema", format.get("type"));
            assertEquals(Boolean.TRUE, format.get("strict"));
            assertEquals(List.of("judgment", "reason"), ((Map<?, ?>) format.get("schema")).get("required"));
            return "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"status\":\"completed\",\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"judgment\\\":\\\"RISK\\\",\\\"reason\\\":\\\"Risk detected\\\"}\"}]}]}";
        });
        SafetyAssessment result = reviewer.assess("a\\\"b", "c", local, "a.py", "", "python3 -m unittest");
        assertEquals(SafetyLevel.REVIEW, result.level());
    }
}
