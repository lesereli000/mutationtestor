package mutationtester;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.safety.PythonSyntaxSafetyAssessor;
import mutationtester.safety.SafetyAssessment;
import mutationtester.safety.SafetyLevel;
import org.junit.jupiter.api.Test;

class PythonSyntaxSafetyAssessorTests {
    private final PythonSyntaxSafetyAssessor assessor = new PythonSyntaxSafetyAssessor();

    private SafetyAssessment assess(String before, String after, int line, String operator) {
        String target = before.split("\n", -1)[line - 1];
        MutationPoint point = new MutationPoint(new File("example.py"), line, target.indexOf(operator), operator, "replacement", Mutator.MutatorType.ROR_GT_TO_GE);
        return assessor.assess(point, before, after);
    }

    @Test void findsGuardedOperationBeyondEightLines() {
        String before = "def update(flag):\n    if flag > 0:\n" + "        pass\n".repeat(12) + "        db.execute('DELETE FROM orders')\n";
        SafetyAssessment result = assess(before, before.replace("flag > 0", "flag >= 0"), 2, ">");
        assertEquals("CONTROL_FLOW_DATABASE", result.category());
        assertTrue(result.reason().contains("controls the call to db.execute"));
        assertTrue(result.assessor().contains("ANTLR"));
    }

    @Test void unrelatedOperationInSiblingFunctionIsNotAttributedToCondition() {
        String before = "def amount(x):\n    if x > 0:\n        return x\n    return 0\n\ndef other(db):\n    db.execute('DELETE FROM orders')\n";
        SafetyAssessment result = assess(before, before.replace("x > 0", "x >= 0"), 2, ">");
        assertEquals(SafetyLevel.UNKNOWN, result.level());
        assertFalse(result.reason().contains("controls the call"));
    }

    @Test void commentsAndStringsAreNotCalls() {
        String before = "def f(x):\n    if x > 0:\n        # db.execute('delete')\n        text = \"open('x')\"\n        return text\n";
        assertEquals(SafetyLevel.UNKNOWN, assess(before, before.replace("x > 0", "x >= 0"), 2, ">").level());
    }

    @Test void loopConditionIsRecognized() {
        String before = "def f(x):\n    while x > 0:\n        x -= 1\n";
        assertEquals("RESOURCE_EXHAUSTION", assess(before, before.replace("x > 0", "x >= 0"), 2, ">").category());
    }

    @Test void detectsOperationInElseBranch() {
        String before = "def f(x):\n    if x > 0:\n        return True\n    else:\n        db.execute('DELETE FROM users')\n";
        assertEquals("CONTROL_FLOW_DATABASE", assess(before, before.replace("x > 0", "x >= 0"), 2, ">").category());
    }

    @Test void earlyReturnGuardCanAffectLaterDatabaseCall() {
        String before = "def save(x, db):\n    if x > 0:\n        return False\n    db.commit()\n";
        SafetyAssessment result = assess(before, before.replace("x > 0", "x >= 0"), 2, ">");
        assertEquals("GUARD_CLAUSE_DATABASE", result.category());
        assertTrue(result.reason().contains("early exit"));
        assertTrue(result.reason().contains("db.commit"));
    }

    @Test void ordinaryIfDoesNotClaimToGuardFollowingCall() {
        String before = "def save(x, db):\n    if x > 0:\n        print(x)\n    db.commit()\n";
        assertEquals(SafetyLevel.UNKNOWN, assess(before, before.replace("x > 0", "x >= 0"), 2, ">").level());
    }

    @Test void unresolvedIndirectCallsAndParseFailuresRemainUnknown() {
        String before = "def f(x):\n    if x > 0:\n        perform_action()\n";
        assertEquals(SafetyLevel.UNKNOWN, assess(before, before.replace("x > 0", "x >= 0"), 2, ">").level());
        assertEquals(SafetyLevel.UNKNOWN, assess(before, "def f(:\n", 2, ">").level());
    }

    @Test void tracesPossibleSameFileHelperChain() {
        String before = "def send_update(db):\n    save(db)\n\ndef save(db):\n    db.execute('UPDATE orders SET done=1')\n\ndef f(x, db):\n    if x > 0:\n        send_update(db)\n";
        SafetyAssessment result = assess(before, before.replace("x > 0", "x >= 0"), 8, ">");
        assertEquals("CONTROL_FLOW_DATABASE", result.category());
        assertTrue(result.reason().contains("send_update -> save -> db.execute"));
        assertTrue(result.reason().contains("bindings are not verified"));
    }

    @Test void recursionAndDuplicateHelperDefinitionsRemainUnknown() {
        String recursive = "def helper():\n    helper()\n\ndef f(x):\n    if x > 0:\n        helper()\n";
        assertEquals(SafetyLevel.UNKNOWN, assess(recursive, recursive.replace("x > 0", "x >= 0"), 5, ">").level());
        String duplicate = "def helper():\n    db.execute('UPDATE t SET x=1')\n\ndef helper():\n    pass\n\ndef f(x):\n    if x > 0:\n        helper()\n";
        assertEquals(SafetyLevel.UNKNOWN, assess(duplicate, duplicate.replace("x > 0", "x >= 0"), 8, ">").level());
    }

    @Test void recognizesModifiedSensitiveCallArguments() {
        String before = "def f(path):\n    open(path + '.txt', 'w')\n";
        assertEquals("FILESYSTEM", assess(before, before.replace("path +", "path -"), 2, "+").category());
    }
}
