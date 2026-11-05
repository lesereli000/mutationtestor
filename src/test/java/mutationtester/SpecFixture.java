package mutationtester;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import mutationtester.model.MutationPoint;

/** Runs a spec over one snippet, returning mutants as "line: original -> replacement". */
final class SpecFixture {

    private SpecFixture() {
    }

    /** The mutants {@code spec} makes in a file called {@code fileName} holding {@code source}. */
    static Set<String> mutants(Path dir, String spec, String fileName, String source) throws IOException {
        return new TreeSet<>(mutantList(dir, spec, fileName, source));
    }

    /** As {@link #mutants}, but keeping duplicates. */
    static List<String> mutantList(Path dir, String spec, String fileName, String source) throws IOException {
        Path work = Files.createTempDirectory(dir, "fixture");
        Path src = Files.createDirectories(work.resolve("src"));
        Files.writeString(src.resolve(fileName), source);
        Path specFile = Files.writeString(work.resolve("rules.spec"), spec);
        PrintStream out = System.out;
        List<String> mutants = new ArrayList<>();
        try {
            System.setOut(new PrintStream(OutputStream.nullOutputStream()));
            for (MutationPoint p : Main.scanForMutations(src, specFile.toString())) {
                mutants.add(p.getLineNumber() + ": " + p.getOperator() + " -> " + p.getReplacement());
            }
        } finally {
            System.setOut(out);
        }
        return mutants;
    }

    /** A spec with one group holding {@code rules} (tab-indented lines). */
    static String spec(String... rules) {
        StringBuilder text = new StringBuilder("Fixture\n\n");
        for (String rule : rules) {
            for (String line : rule.split("\n")) text.append('\t').append(line).append('\n');
            text.append('\n');
        }
        return text.append("End\n").toString();
    }

    /** A sorted set, for writing expected mutants. */
    static Set<String> set(String... mutants) {
        return new TreeSet<>(Set.of(mutants));
    }
}
