package mutationtester;

import mutationtester.coverage.*;
import mutationtester.model.*;
import mutationtester.msl.MutationRule;
import mutationtester.msl.MutationRuleGenerator;
import mutationtester.scanner.*;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CSharpScannerTests {

    @Test
    public void testCustomSpecSuppressesCSharpMutationThatWouldProduceLoop() throws Exception {
        Path source = Files.createTempFile("LoopGuardDemo", ".cs");
        Files.writeString(source, """
                namespace Demo
                {
                    public static class LoopGuardDemo
                    {
                        public static int GuardedIncrement(int value, int max)
                        {
                            if (value > max)
                            {
                                value = max;
                            }
                            return value;
                        }
                    }
                }
                """);

        Path baselineSpec = Files.createTempFile("loop-no-skip", ".spec");
        Files.writeString(baselineSpec, """
                Conditionals Boundary:

                    Replace: {quantity} > {quantity}
                    With: {quantity} >= {quantity}

                End
                """);

        Path spec = Files.createTempFile("loop-skip", ".spec");
        Files.writeString(spec, """
                Conditionals Boundary:

                    Replace: {quantity} > {quantity}
                    With: {quantity} >= {quantity}
                    Except: if ({match}) { {quantity} = {quantity} }

                End
                """);

        List<MutationPoint> baselinePoints = scanCSharpWithSpec(source.toFile(), baselineSpec.toFile());
        assertEquals(1, baselinePoints.size(), "The C# MSL scanner should find the guarded comparison without Except.");
        assertEquals(">", baselinePoints.get(0).getOperator());
        assertEquals(">=", baselinePoints.get(0).getReplacement());

        List<MutationPoint> points = scanCSharpWithSpec(source.toFile(), spec.toFile());
        assertEquals(0, points.size(),
                "The guarded assignment should be suppressed because mutating the boundary can create a loop.");
    }

    private List<MutationPoint> scanCSharpWithSpec(File sourceFile, File specFile) throws IOException {
        var adapter = CSharpMutationScanner.createAdapter(sourceFile);
        List<MutationRule> rules = MutationRuleGenerator.generateRules(specFile);
        List<MutationPoint> points = new ArrayList<>();
        for (MutationRule rule : rules) {
            points.addAll(rule.findMutationPoints(adapter, sourceFile));
        }
        return points;
    }

}
