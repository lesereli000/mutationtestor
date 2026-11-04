package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import mutationtester.msl.MSLNode;
import mutationtester.msl.MSLPlaceholder;
import mutationtester.msl.MSLScannerAdapter;
import mutationtester.scanner.CSharpMutationScanner;
import mutationtester.scanner.JavaMutationScanner;
import mutationtester.scanner.JavaScriptMutationScanner;
import mutationtester.scanner.PythonMutationScanner;
import mutationtester.scanner.RacketMutationScanner;

/** The matrix in docs/spec-format.md matches the adapters; -Dmsl.matrix.update=true rewrites it. */
class CoverageMatrixTests {

    private static final Path DOC = Path.of("docs", "spec-format.md");
    private static final String START = "<!-- coverage-matrix:start -->";
    private static final String END = "<!-- coverage-matrix:end -->";

    @TempDir
    Path dir;

    private Map<String, MSLScannerAdapter> adapters() throws IOException {
        Map<String, MSLScannerAdapter> adapters = new LinkedHashMap<>();
        adapters.put("Java", JavaMutationScanner.createAdapter(file("A.java", "class A {}\n")));
        adapters.put("C#", CSharpMutationScanner.createAdapter(file("A.cs", "class A {}\n")));
        adapters.put("JS", JavaScriptMutationScanner.createAdapter(file("a.js", "let a = 1;\n")));
        adapters.put("Python", PythonMutationScanner.createAdapter(file("a.py", "a = 1\n")));
        adapters.put("Racket", RacketMutationScanner.createAdapter(file("a.rkt", "#lang racket\n(define a 1)\n")));
        return adapters;
    }

    private File file(String name, String text) throws IOException {
        return Files.writeString(dir.resolve(name), text).toFile();
    }

    /** Every construct kind the spec parser produces, in declaration order. */
    private static List<String> kinds() throws IllegalAccessException {
        List<String> kinds = new ArrayList<>();
        for (Field f : MSLNode.class.getFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class && !f.getName().equals("EMPTY")) {
                kinds.add((String) f.get(null));
            }
        }
        return kinds;
    }

    private String matrix() throws Exception {
        Map<String, MSLScannerAdapter> adapters = adapters();
        StringBuilder out = new StringBuilder();
        out.append("| Construct | ").append(String.join(" | ", adapters.keySet())).append(" |\n");
        out.append("|---|").append("---|".repeat(adapters.size())).append('\n');
        for (String kind : kinds()) {
            out.append("| `").append(kind).append("` |");
            for (MSLScannerAdapter a : adapters.values()) out.append(a.supportedKinds().contains(kind) ? " ✓ |" : " — |");
            out.append('\n');
        }
        out.append('\n');
        out.append("| Placeholder | ").append(String.join(" | ", adapters.keySet())).append(" |\n");
        out.append("|---|").append("---|".repeat(adapters.size())).append('\n');
        for (String type : new TreeSet<>(MSLPlaceholder.INPUT_TYPES)) {
            if (type.equals(MSLPlaceholder.MATCH)) continue;
            out.append("| `{").append(type).append("}` |");
            for (MSLScannerAdapter a : adapters.values()) {
                // {arguments} and {contains:} are handled by the shared matcher.
                boolean shared = type.equals(MSLPlaceholder.ARGUMENTS) || type.equals(MSLPlaceholder.CONTAINS);
                out.append(shared || a.supportedPlaceholders().contains(type) ? " ✓ |" : " — |");
            }
            out.append('\n');
        }
        for (String type : new TreeSet<>(MSLPlaceholder.OUTPUT_TYPES)) {
            out.append("| `{").append(type).append("}` (output) |").append(" ✓ |".repeat(adapters.size())).append('\n');
        }
        return out.toString();
    }

    @Test
    void docsMatchTheAdapters() throws Exception {
        String doc = Files.readString(DOC, StandardCharsets.UTF_8).replace("\r\n", "\n");
        int start = doc.indexOf(START) + START.length();
        int end = doc.indexOf(END);
        String current = doc.substring(start, end).strip();
        String generated = matrix().strip();
        if (Boolean.getBoolean("msl.matrix.update")) {
            Files.writeString(DOC, doc.substring(0, start) + "\n" + generated + "\n" + doc.substring(end),
                    StandardCharsets.UTF_8);
            return;
        }
        assertEquals(generated, current, "docs/spec-format.md is out of date; run with -Dmsl.matrix.update=true");
    }
}
