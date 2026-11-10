package mutationtester;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import mutationtester.coverage.CoverageData;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.report.MutationReport;
import mutationtester.safety.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SafetyIntegrationTests {
    @TempDir Path dir;
    private static final String MARK_EXECUTED = "echo executed > executed";

    private SafetyController controller(String input) {
        return new SafetyController(input == null ? null : new BufferedReader(new StringReader(input)), new PrintStream(new ByteArrayOutputStream()));
    }

    @Test void classifiesMultipleContextsConservatively() {
        ContextualSafetyAssessor assessor = new ContextualSafetyAssessor();
        assertEquals(SafetyLevel.SAFE, assessor.assess("1 + 2", "1 - 2").level());
        assertEquals(SafetyLevel.REVIEW, assessor.assess("db.execute('UPDATE t')", "db.execute('DELETE t')").level());
        assertEquals(SafetyLevel.REVIEW, assessor.assess("open('x', 'w')", "open('y', 'w')").level());
        assertEquals(SafetyLevel.REVIEW, assessor.assess("fetch(url)", "fetch(other)").level());
        assertEquals(SafetyLevel.REVIEW, assessor.assess("while (x < 1) {}", "while (x > 1) {}").level());
        assertEquals(SafetyLevel.BLOCK, assessor.assess("run('SELECT * FROM users')", "run('DROP TABLE users')").level());
        assertEquals(SafetyLevel.UNKNOWN, assessor.assess("return x + y;", "return x - y;").level());
        assertEquals(SafetyLevel.UNKNOWN, assessor.assess("x", "x").level());
    }

    @Test void unattendedSkipNeverRunsCommandOrChangesSource() throws Exception {
        Path file = dir.resolve("sample.py");
        Files.writeString(file, "value = x + y\n");
        MutationPoint point = new MutationPoint(file.toFile(), 1, 10, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        MutantResult result = Main.testSingleMutant(point, dir, "", MARK_EXECUTED, CoverageData.empty(), controller(null));
        assertEquals(MutantResult.MutantStatus.SAFETY_SKIPPED, result.getStatus());
        assertNotNull(result.getSafetyAssessment());
        assertFalse(Files.exists(dir.resolve("executed")));
        assertEquals("value = x + y\n", Files.readString(file));
    }

    @Test void runUnknownUnattendedTestsOnlyUnknownMutants() {
        SafetyController safety = controller(null);
        safety.setRunUnknownUnattended(true);
        MutationPoint point = new MutationPoint(dir.resolve("a.txt").toFile(), 1, 0, "+", "-", Mutator.MutatorType.CUSTOM);
        assertEquals(SafetyAction.CONTINUE, safety.decide(point, "return x + y;", "return x - y;"));
        assertEquals(SafetyAction.SKIP, safety.decide(point, "fetch(url)", "fetch(other)"));
        assertEquals(SafetyAction.SKIP, safety.decide(point, "run('SELECT * FROM users')", "run('DROP TABLE users')"));
    }

    @Test void explicitContinueRunsAndRestoresSource() throws Exception {
        Path file = dir.resolve("sample.py");
        Files.writeString(file, "value = x + y\n");
        MutationPoint point = new MutationPoint(file.toFile(), 1, 10, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        MutantResult result = Main.testSingleMutant(point, dir, "", MARK_EXECUTED, CoverageData.empty(), controller("c\n"));
        assertEquals(MutantResult.MutantStatus.SURVIVED, result.getStatus());
        assertNotNull(result.getSafetyAssessment());
        assertTrue(Files.exists(dir.resolve("executed")));
        assertEquals("value = x + y\n", Files.readString(file));
    }

    @Test void blockedCannotContinueAndAbortIsReported() throws Exception {
        Path file = dir.resolve("sample.py");
        Files.writeString(file, "run('SELECT * FROM users')\n");
        MutationPoint point = new MutationPoint(file.toFile(), 1, 5, "SELECT * FROM users", "DROP TABLE users", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        MutantResult result = Main.testSingleMutant(point, dir, "", MARK_EXECUTED, CoverageData.empty(), controller("c\na\n"));
        assertEquals(MutantResult.MutantStatus.SAFETY_ABORTED, result.getStatus());
        assertFalse(Files.exists(dir.resolve("executed")));
        assertEquals("run('SELECT * FROM users')\n", Files.readString(file));
    }

    @Test void skippedResultsDoNotReduceScore() {
        MutationPoint p = new MutationPoint(dir.resolve("a.py").toFile(), 1, 0, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        MutationReport report = new MutationReport(java.util.List.of(new MutantResult(p, MutantResult.MutantStatus.KILLED, "", false), new MutantResult(p, MutantResult.MutantStatus.SAFETY_SKIPPED, "", false)));
        assertEquals(100.0, report.getMutationScore());
        assertEquals(1, report.getSafetySkipped());
    }

    @Test void closedInputAndInvalidChoicesFailClosed() {
        MutationPoint p = new MutationPoint(dir.resolve("a.py").toFile(), 1, 0, "+", "-", Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertEquals(SafetyAction.SKIP, controller("").decide(p, "x + 1", "x - 1"));
        assertEquals(SafetyAction.SKIP, controller("invalid\ns\n").decide(p, "x + 1", "x - 1"));
    }
}
