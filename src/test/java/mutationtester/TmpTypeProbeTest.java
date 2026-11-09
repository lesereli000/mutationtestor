package mutationtester;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Throwaway probe: prints each candidate type rule's mutants per language. Deleted after use. */
class TmpTypeProbeTest {

    @TempDir
    Path dir;

    @Test
    void probe() throws Exception {
        String[][] rules = {
            {"int -> double", "{integer: int32}", "{float: float64}"},
            {"int -> float", "{integer: int32}", "{float: float32}"},
            {"double -> int", "{float: float64}", "{integer: int32}"},
            {"float -> int", "{float: float32}", "{integer: int32}"},
            {"int -> long", "{integer: int32}", "{integer: int64}"},
            {"long -> int", "{integer: int64}", "{integer: int32}"},
            {"long -> double", "{integer: int64}", "{float: float64}"},
            {"long -> float", "{integer: int64}", "{float: float32}"},
            {"double -> long", "{float: float64}", "{integer: int64}"},
            {"float -> long", "{float: float32}", "{integer: int64}"},
            {"plain float -> integer", "{float}", "{integer}"},
        };
        StringBuilder out = new StringBuilder();
        for (String[] r : rules) {
            out.append("### ").append(r[0]).append('\n');
            String spec = SpecFixture.spec("Replace: " + r[1] + "\nWith: " + r[2]);
            for (var e : MslExpressionsTests.CODE.entrySet()) {
                out.append("  ").append(e.getKey()).append(": ")
                   .append(SpecFixture.mutants(dir, spec, e.getKey(), e.getValue())).append('\n');
            }
        }
        java.nio.file.Files.writeString(Path.of("target/type-probe.txt"), out);
    }
}
