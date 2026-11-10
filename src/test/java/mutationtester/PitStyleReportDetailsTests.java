package mutationtester;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.report.PitStyleReportGenerator;

class PitStyleReportDetailsTests {

    @TempDir
    Path tempDir;

    @Test
    void reportShowsSharedTypeLanguageOriginalReplacementAndResult() throws Exception {
        Path source = tempDir.resolve("example.py");
        Path report = tempDir.resolve("report.html");
        Files.writeString(source, "if ready:\n    run()\n");

        MutationPoint point = new MutationPoint(source.toFile(), 1, 3,
                "ready", "True", Mutator.MutatorType.CONDITIONAL_TRUE);
        MutantResult result = new MutantResult(point, MutantResult.MutantStatus.KILLED,
                "test failed", false);

        PitStyleReportGenerator.generatePitStyleReport(
                source.toString(), report.toString(), List.of(result), 1, 1, 0, 0, 100.0);

        String html = Files.readString(report);
        assertTrue(html.contains("<b>Language:</b> Python"));
        assertTrue(html.contains("<b>Shared type:</b> CONDITIONAL_TRUE"));
        assertTrue(html.contains("<b>Original:</b> <code>ready</code>"));
        assertTrue(html.contains("<b>Replacement:</b> <code>True</code>"));
        assertTrue(html.contains("KILLED"));
        assertTrue(html.contains("ready -&gt; True"));
        assertTrue(html.contains("Back to summary"));
        assertTrue(html.contains("href=\"mutation-report-demo.html\""));
    }

    @Test
    void specMutantUsesGroupAsTypeAndDoesNotShowCustom() throws Exception {
        Path source = tempDir.resolve("Add.java");
        Path allFiles = tempDir.resolve("allFiles");
        Files.createDirectories(allFiles);
        Path report = allFiles.resolve("Add.html");
        Files.writeString(source, "return a + b;\n");

        MutationPoint point = new MutationPoint(source.toFile(), 1, 9,
                "+", "-", Mutator.MutatorType.CUSTOM, "Math Operators: + → -");
        MutantResult result = new MutantResult(point, MutantResult.MutantStatus.SURVIVED,
                "tests passed", true);

        PitStyleReportGenerator.generatePitStyleReport(
                source.toString(), report.toString(), List.of(result), 1, 0, 1, 0, 0.0);

        String html = Files.readString(report);
        assertTrue(html.contains("<strong>Math Operators: + -&gt; -</strong>"));
        assertTrue(html.contains("<b>Shared type:</b> Math Operators"));
        assertFalse(html.contains("<b>Shared type:</b> CUSTOM"));
        assertTrue(html.contains("href=\"../mutation-report-demo.html\""));
        assertTrue(html.contains("href=\"../all-survivors.html\""));
    }
}
