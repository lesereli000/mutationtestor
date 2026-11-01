package mutationtester;

import static org.junit.jupiter.api.Assertions.*;

import mutationtester.safety.ContextualSafetyAssessor;
import mutationtester.safety.SafetyAssessment;
import mutationtester.safety.SafetyLevel;
import org.junit.jupiter.api.Test;

class ContextualSafetyAssessorTests {
    private final ContextualSafetyAssessor assessor = new ContextualSafetyAssessor();

    @Test void changedGuardsNearExternalEffectsRequireReviewAcrossLanguages() {
        String[][] pairs = {
            {"if (authorized) {\n  db.execute(sql);\n}", "if (!authorized) {\n  db.execute(sql);\n}"},
            {"if authorized:\n    open(path, 'w')", "if not authorized:\n    open(path, 'w')"},
            {"if (allowed) {\n  fetch(url);\n}", "if (!allowed) {\n  fetch(url);\n}"},
            {"if (allowed) {\n  File.Delete(path);\n}", "if (!allowed) {\n  File.Delete(path);\n}"},
            {"(when allowed\n  (delete path))", "(unless allowed\n  (delete path))"}
        };
        for (String[] pair : pairs) {
            SafetyAssessment result = assessor.assess(pair[0], pair[1]);
            assertEquals(SafetyLevel.REVIEW, result.level(), pair[0]);
            assertTrue(result.category().startsWith("CONTROL_FLOW_"), result.toString());
        }
    }

    @Test void distinguishesChangedStatementFromUnrelatedRiskElsewhere() {
        String padding = "\n".repeat(25);
        String before = "function calculate(x) { return x + 1; }" + padding + "db.execute(sql);";
        String after = before.replace("x + 1", "x - 1");
        assertEquals(SafetyLevel.UNKNOWN, assessor.assess(before, after).level());
    }

    @Test void introducedDestructiveCommandBlocksButExistingOneIsReviewable() {
        assertEquals(SafetyLevel.BLOCK, assessor.assess("execute('select 1')", "execute('DROP TABLE users')").level());
        assertEquals(SafetyLevel.REVIEW, assessor.assess("execute('DROP TABLE a')", "execute('DROP TABLE b')").level());
    }

    @Test void loopBoundAndNetworkChangeExplainTheirRisks() {
        SafetyAssessment loop = assessor.assess("while (i < 10) { i++; }", "while (i > 10) { i++; }");
        assertEquals("RESOURCE_EXHAUSTION", loop.category());
        SafetyAssessment network = assessor.assess("fetch(oldUrl)", "fetch(newUrl)");
        assertEquals("NETWORK", network.category());
        assertTrue(network.reason().contains("network"));
    }

    @Test void identicalOrIncompleteInputsNeverRunAutomatically() {
        assertEquals(SafetyLevel.UNKNOWN, assessor.assess(null, "x").level());
        assertEquals(SafetyLevel.UNKNOWN, assessor.assess("x", "x").level());
    }
}
