package mutationtester;



import mutationtester.model.*;


import mutationtester.equivalent.EquivalentMutantStore;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class EquivalentMutantStoreTests {

    @Test
    void contentMutantIdIncludesMethodNameAfterFileName() throws Exception {
        Path file = Files.createTempFile("EquivalentDemo", ".cs");
        Files.writeString(file, """
                namespace Demo
                {
                    public static class EquivalentDemo
                    {
                        public static int EquivalentOrToAnd(int x)
                        {
                            return x | x;
                        }
                    }
                }
                """);

        MutationPoint point = new MutationPoint(
                file.toFile(),
                7,
                21,
                "|",
                "&",
                Mutator.MutatorType.BOR_OR_TO_AND
        );

        String expected = "CONTENT|" + file.getFileName() + "|EquivalentOrToAnd|returnx|x;|||&";
        assertEquals(expected, EquivalentMutantStore.buildMutantId(point));
    }

    @Test
    void candidateMutantIdsKeepLegacyContentFormat() throws Exception {
        Path file = Files.createTempFile("EquivalentDemo", ".cs");
        Files.writeString(file, """
                public static class EquivalentDemo
                {
                    public static int EquivalentOrToAnd(int x)
                    {
                        return x | x;
                    }
                }
                """);

        MutationPoint point = new MutationPoint(
                file.toFile(),
                5,
                21,
                "|",
                "&",
                Mutator.MutatorType.BOR_OR_TO_AND
        );

        Set<String> candidates = EquivalentMutantStore.buildCandidateMutantIds(point);

        assertTrue(candidates.contains(
                "CONTENT|" + file.getFileName() + "|EquivalentOrToAnd|returnx|x;|||&"));
        assertTrue(candidates.contains(
                "CONTENT|" + file.getFileName() + "|returnx|x;|||&"));
    }

    @Test
    void loadHiddenMutantIdsMigratesOldContentIds() throws Exception {
        Path reportDir = Path.of("output", "report");
        Files.createDirectories(reportDir);
        Path store = reportDir.resolve("equivalent-mutants.json");
        String originalContent = Files.exists(store) ? Files.readString(store) : null;

        try {
            Path sourceDir = Path.of("samples", "CSProject", "src", "CSProject");
            Files.createDirectories(sourceDir);
            Path source = sourceDir.resolve("EquivalentDemo.cs");
            String originalSource = Files.exists(source) ? Files.readString(source) : null;

            try {
                Files.writeString(source, """
                        namespace Demo
                        {
                            public static class EquivalentDemo
                            {
                                public static int EquivalentOrToAnd(int x)
                                {
                                    return x | x;
                                }
                            }
                        }
                        """);
                Files.writeString(store, """
                        {
                          "hiddenMutantIds": [
                            "CONTENT|EquivalentDemo.cs|returnx|x;|||&"
                          ]
                        }
                        """);

                Set<String> ids = EquivalentMutantStore.loadHiddenMutantIds();

                assertTrue(ids.contains("CONTENT|EquivalentDemo.cs|EquivalentOrToAnd|returnx|x;|||&"));
                assertTrue(Files.readString(store).contains(
                        "CONTENT|EquivalentDemo.cs|EquivalentOrToAnd|returnx|x;|||&"));
            } finally {
                if (originalSource == null) {
                    Files.deleteIfExists(source);
                } else {
                    Files.writeString(source, originalSource);
                }
            }
        } finally {
            if (originalContent == null) {
                Files.deleteIfExists(store);
            } else {
                Files.writeString(store, originalContent);
            }
        }
    }

    @Test
    void addHiddenMutantIdNormalizesOldContentIdsBeforeSaving() throws Exception {
        Path reportDir = Path.of("output", "report");
        Files.createDirectories(reportDir);
        Path store = reportDir.resolve("equivalent-mutants.json");
        String originalContent = Files.exists(store) ? Files.readString(store) : null;

        try {
            Path sourceDir = Path.of("samples", "CSProject", "src", "CSProject");
            Files.createDirectories(sourceDir);
            Path source = sourceDir.resolve("EquivalentDemo.cs");
            String originalSource = Files.exists(source) ? Files.readString(source) : null;

            try {
                Files.writeString(source, """
                        namespace Demo
                        {
                            public static class EquivalentDemo
                            {
                                public static int EquivalentOrToAnd(int x)
                                {
                                    return x | x;
                                }
                            }
                        }
                        """);
                Files.writeString(store, """
                        {
                          "hiddenMutantIds": [
                            "CONTENT|EquivalentDemo.cs|EquivalentOrToAnd|returnx|x;|||&"
                          ]
                        }
                        """);

                EquivalentMutantStore.addHiddenMutantId("CONTENT|EquivalentDemo.cs|returnx|x;|||&");

                String saved = Files.readString(store);
                assertTrue(saved.contains("CONTENT|EquivalentDemo.cs|EquivalentOrToAnd|returnx|x;|||&"));
                assertTrue(!saved.contains("\"CONTENT|EquivalentDemo.cs|returnx|x;|||&\""));
            } finally {
                if (originalSource == null) {
                    Files.deleteIfExists(source);
                } else {
                    Files.writeString(source, originalSource);
                }
            }
        } finally {
            if (originalContent == null) {
                Files.deleteIfExists(store);
            } else {
                Files.writeString(store, originalContent);
            }
        }
    }
}
