package mutationtester;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * MSL matching works only from MSL trees and the CST. This guards the shared
 * MSL code and every language adapter against regular expressions creeping
 * back in: java.util.regex, String.matches/replaceAll/replaceFirst/split.
 *
 * An adapter is the code from a scanner's createAdapter(File) to the end of
 * its file; the rest of each scanner is the deprecated non-spec scanning.
 */
class NoRegexInMslTests {

    private static final String[] FORBIDDEN = {
        "java.util.regex", "Pattern.compile", ".matches(", ".replaceAll(", ".replaceFirst(", ".split(",
    };

    private static final Path MAIN = Paths.get("src", "main", "java", "mutationtester");

    @Test
    void sharedMslCodeUsesNoRegex() throws IOException {
        List<String> found = new ArrayList<>();
        try (Stream<Path> files = Files.list(MAIN.resolve("msl"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                check(file, Files.readString(file), found);
            }
        }
        check(MAIN.resolve("scanner/TypedEmptyValues.java"),
                Files.readString(MAIN.resolve("scanner/TypedEmptyValues.java")), found);
        assertTrue(found.isEmpty(), "Regex use in MSL code:\n" + String.join("\n", found));
    }

    @Test
    void languageAdaptersUseNoRegex() throws IOException {
        List<String> found = new ArrayList<>();
        for (String scanner : List.of("Java", "JavaScript", "Python", "CSharp", "Racket")) {
            Path file = MAIN.resolve("scanner/" + scanner + "MutationScanner.java");
            String source = Files.readString(file);
            int adapter = source.indexOf("public static MSLScannerAdapter createAdapter(");
            assertTrue(adapter >= 0, file + " has no createAdapter");
            check(file, source.substring(adapter), found);
        }
        assertTrue(found.isEmpty(), "Regex use in language adapters:\n" + String.join("\n", found));
    }

    private static void check(Path file, String source, List<String> found) {
        String[] lines = source.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) continue;
            for (String forbidden : FORBIDDEN) {
                if (line.contains(forbidden)) {
                    found.add(file.getFileName() + ": " + line);
                }
            }
        }
    }
}
