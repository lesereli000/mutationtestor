package mutationtester;

import static org.junit.jupiter.api.Assertions.*;
import java.io.File;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.safety.*;
import org.junit.jupiter.api.Test;

class AntlrSyntaxSafetyAssessorTests {
    private final AntlrSyntaxSafetyAssessor assessor = new AntlrSyntaxSafetyAssessor();

    private SafetyAssessment assess(String file, String original, String replacement) {
        int offset = original.indexOf("> 0");
        int line = (int) original.substring(0, offset).chars().filter(c -> c == '\n').count() + 1;
        int lastNewline = original.lastIndexOf('\n', offset);
        MutationPoint point = new MutationPoint(new File(file), line, offset - lastNewline - 1, ">", ">=", Mutator.MutatorType.ROR_GT_TO_GE);
        return assessor.assess(point, original, replacement);
    }

    @Test void identifiesGuardedCallsInFourLanguageSyntaxTrees() {
        String[][] examples = {
            {"a.java", "class A { void f(int x) { if (x > 0) { db.execute(); } } }", "DATABASE"},
            {"a.js", "function f(x) { if (x > 0) { fetch(url); } }", "NETWORK"},
            {"a.cs", "class A { void F(int x) { if (x > 0) { File.Delete(path); } } }", "FILESYSTEM"},
            {"a.rkt", "(define (f x) (if (> x 0) (delete-file \"x\") 0))", "FILESYSTEM"}
        };
        for (String[] example : examples) {
            String source = example[1];
            String changed = example[0].endsWith("rkt") ? source.replace("> x 0", ">= x 0") : source.replace("> 0", ">= 0");
            SafetyAssessment result;
            if (example[0].endsWith("rkt")) {
                MutationPoint p = new MutationPoint(new File(example[0]), 1, source.indexOf('>'), ">", ">=", Mutator.MutatorType.ROR_GT_TO_GE);
                result = assessor.assess(p, source, changed);
            } else result = assess(example[0], source, changed);
            assertEquals("CONTROL_FLOW_" + example[2], result.category(), example[0] + ": " + result);
            assertTrue(result.assessor().contains("ANTLR"));
        }
    }

    @Test void separateFunctionAndCommentDoNotCreateFalseAssociation() {
        String original = "function f(x) { if (x > 0) { return x; } }\nfunction other() { fetch(url); }";
        assertEquals(SafetyLevel.UNKNOWN, assess("a.js", original, original.replace("> 0", ">= 0")).level());
        String comment = "function f(x) { if (x > 0) { /* fetch(url) */ return x; } }";
        assertEquals(SafetyLevel.UNKNOWN, assess("a.js", comment, comment.replace("> 0", ">= 0")).level());
    }

    @Test void syntaxErrorsAndLoopsAreConservative() {
        String original = "function f(x) { while (x > 0) { x--; } }";
        assertEquals("RESOURCE_EXHAUSTION", assess("a.js", original, original.replace("> 0", ">= 0")).category());
        assertEquals(SafetyLevel.UNKNOWN, assess("a.js", original, "function !").level());
    }

    @Test void javaAndCSharpLoopsAreRecognized() {
        String java = "class A { void f(int x) { while (x > 0) { x--; } } }";
        String csharp = "class A { void F(int x) { while (x > 0) { x--; } } }";
        assertEquals("RESOURCE_EXHAUSTION", assess("a.java", java, java.replace("> 0", ">= 0")).category());
        assertEquals("RESOURCE_EXHAUSTION", assess("a.cs", csharp, csharp.replace("> 0", ">= 0")).category());
    }

    @Test void languageDispatchUsesSyntaxAssessment() {
        String source = "function f(x) { if (x > 0) { fetch(url); } }";
        MutationPoint p = new MutationPoint(new File("a.js"), 1, source.indexOf('>'), ">", ">=", Mutator.MutatorType.ROR_GT_TO_GE);
        SafetyController controller = new SafetyController(null, new java.io.PrintStream(new java.io.ByteArrayOutputStream()));
        SafetyController.Outcome result = controller.decideWithAssessment(p, source, source.replace("> 0", ">= 0"));
        assertEquals(SafetyAction.SKIP, result.action());
        assertEquals("CONTROL_FLOW_NETWORK", result.assessment().category());
        assertTrue(result.assessment().assessor().contains("ANTLR JavaScript"));
    }

    @Test void javascriptConditionFollowsPossibleSameFileHelperToNetworkCall() {
        String source = "function send() { fetch(url); }\nfunction f(x) { if (x > 0) { send(); } }";
        SafetyAssessment result = assess("a.js", source, source.replace("> 0", ">= 0"));
        assertEquals("CONTROL_FLOW_NETWORK", result.category());
        assertTrue(result.reason().contains("send -> fetch"));
        assertTrue(result.reason().contains("not verified"));
    }

    @Test void javascriptEarlyReturnCanGuardLaterHelperCall() {
        String source = "function send() { fetch(url); }\nfunction f(x) { if (x > 0) { return; } send(); }";
        SafetyAssessment result = assess("a.js", source, source.replace("> 0", ">= 0"));
        assertEquals("GUARD_CLAUSE_NETWORK", result.category());
        assertTrue(result.reason().contains("send -> fetch"));
    }

    @Test void duplicateHelperNamesDoNotEstablishPath() {
        String source = "function send() { fetch(url); }\nfunction send() {}\nfunction f(x) { if (x > 0) { send(); } }";
        assertEquals(SafetyLevel.UNKNOWN, assess("a.js", source, source.replace("> 0", ">= 0")).level());
    }
}
