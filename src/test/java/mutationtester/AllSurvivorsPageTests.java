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
import mutationtester.report.AllSurvivorsPageGenerator;

class AllSurvivorsPageTests {

    @TempDir
    Path tempDir;

    @Test
    void survivorsPageUsesSpecLabelBackLinkAndEquivalentAction() throws Exception {
        Path source = tempDir.resolve("Add.java");
        Files.writeString(source, "return a + b;\n");
        Path report = tempDir.resolve("all-survivors.html");

        MutationPoint point = new MutationPoint(source.toFile(), 1, 9,
                "+", "-", Mutator.MutatorType.CUSTOM, "Math Operators: + ? -");
        MutantResult result = new MutantResult(point, MutantResult.MutantStatus.SURVIVED,
                "tests passed", true);

        AllSurvivorsPageGenerator.generateAllSurvivorsPage(List.of(result), report.toString());

        String html = Files.readString(report);
        assertTrue(html.contains("href=\"mutation-report-demo.html\""));
        assertTrue(html.contains("Back to summary"));
        assertTrue(html.contains("Math Operators: + -&gt; -"));
        assertFalse(html.contains("AOR_PLUS_TO_MINUS"));
        assertFalse(html.contains(" + ? -"));
        assertTrue(html.contains("Mark as Equivalent"));
        assertTrue(html.contains("class=\"mark-equivalent-btn\""));
        assertTrue(html.contains("allFiles/Add.html#line-1"));
    }
}
