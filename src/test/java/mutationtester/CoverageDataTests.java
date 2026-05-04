package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CoverageDataTests {

    @Test
    void parsesLcovCoveredLines() throws Exception {
        Path report = Files.createTempFile("coverage", ".info");
        Files.writeString(report, """
                TN:
                SF:src/CSProject/EquivalentDemo.cs
                DA:7,1
                DA:8,0
                end_of_record
                """);

        CoverageData data = CoverageData.load(report);

        assertTrue(data.hasCoverage());
        assertTrue(data.isLineCovered(Path.of("output/workdir-1/src/CSProject/EquivalentDemo.cs"), 7));
        assertFalse(data.isLineCovered(Path.of("output/workdir-1/src/CSProject/EquivalentDemo.cs"), 8));
    }

    @Test
    void parsesCoberturaCoveredLines() throws Exception {
        Path report = Files.createTempFile("coverage", ".xml");
        Files.writeString(report, """
                <?xml version="1.0"?>
                <coverage>
                  <packages>
                    <package>
                      <classes>
                        <class filename="src/CSProject/EquivalentDemo.cs">
                          <lines>
                            <line number="7" hits="3"/>
                            <line number="8" hits="0"/>
                          </lines>
                        </class>
                      </classes>
                    </package>
                  </packages>
                </coverage>
                """);

        CoverageData data = CoverageData.load(report);

        assertTrue(data.hasCoverage());
        assertTrue(data.isLineCovered(Path.of("output/workdir-1/src/CSProject/EquivalentDemo.cs"), 7));
        assertFalse(data.isLineCovered(Path.of("output/workdir-1/src/CSProject/EquivalentDemo.cs"), 8));
    }

    @Test
    void parsesJacocoCoveredLines() throws Exception {
        Path report = Files.createTempFile("jacoco", ".xml");
        Files.writeString(report, """
                <?xml version="1.0"?>
                <report name="demo">
                  <package name="main/java">
                    <sourcefile name="Add1.java">
                      <line nr="7" mi="0" ci="2" mb="0" cb="0"/>
                      <line nr="8" mi="2" ci="0" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                </report>
                """);

        CoverageData data = CoverageData.load(report);

        assertTrue(data.hasCoverage());
        assertTrue(data.isLineCovered(Path.of("samples/MavenProject/src/main/java/Add1.java"), 7));
        assertFalse(data.isLineCovered(Path.of("samples/MavenProject/src/main/java/Add1.java"), 8));
    }

    @Test
    void parsesJacocoReportWithExternalDtdDeclaration() throws Exception {
        Path report = Files.createTempFile("jacoco-with-dtd", ".xml");
        Files.writeString(report, """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <!DOCTYPE report PUBLIC "-//JACOCO//DTD Report 1.1//EN" "missing-report.dtd">
                <report name="demo">
                  <package name="main/java">
                    <sourcefile name="Add1.java">
                      <line nr="7" mi="0" ci="2" mb="0" cb="0"/>
                    </sourcefile>
                  </package>
                </report>
                """);

        CoverageData data = CoverageData.load(report);

        assertTrue(data.hasCoverage());
        assertTrue(data.isLineCovered(Path.of("samples/MavenProject/src/main/java/Add1.java"), 7));
    }


    @Test
    void parsesPythonTraceCoverageDirectory() throws Exception {
        Path reportDirectory = Files.createTempDirectory("python-trace-coverage");
        Path coverFile = reportDirectory.resolve("src.Add1.cover");
        Files.writeString(coverFile, """
                    1: def add1(a):
                       \s
                >>>>>>     skipped()
                    4:     return a + 1
                """);

        CoverageData data = CoverageData.load(reportDirectory);

        assertTrue(data.hasCoverage());
        assertTrue(data.isLineCovered(Path.of("samples/PytestProject/src/Add1.py"), 1));
        assertFalse(data.isLineCovered(Path.of("samples/PytestProject/src/Add1.py"), 3));
        assertTrue(data.isLineCovered(Path.of("samples/PytestProject/src/Add1.py"), 4));
    }
}
