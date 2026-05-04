package mutationtester;



import mutationtester.model.*;


import mutationtester.msl.*;

import org.junit.jupiter.api.Test;
import java.io.File;
import static org.junit.jupiter.api.Assertions.*;

public class MSLTests {

    // ── Step 1: CUSTOM sentinel ───────────────────────────────────────────────

    @Test
    void customEnumValueExists() {
        Mutator.MutatorType t = Mutator.MutatorType.valueOf("CUSTOM");
        assertEquals(Mutator.MutatorType.CUSTOM, t);
    }

    // ── Step 2: MutationPoint.customLabel ────────────────────────────────────

    @Test
    void mutationPointCustomLabelRoundTrip() {
        File f = new File("Dummy.java");
        MutationPoint mp = new MutationPoint(f, 3, 5, "+", "-",
                Mutator.MutatorType.CUSTOM, "AOR swap");
        assertEquals("AOR swap", mp.getCustomLabel());
    }

    @Test
    void mutationPointCustomLabelNullForBuiltIn() {
        File f = new File("Dummy.java");
        MutationPoint mp = new MutationPoint(f, 1, 0, "+", "-",
                Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        assertNull(mp.getCustomLabel());
    }

    @Test
    void mutationPointToStringIncludesLabel() {
        File f = new File("Dummy.java");
        MutationPoint mp = new MutationPoint(f, 3, 5, "+", "-",
                Mutator.MutatorType.CUSTOM, "My Label");
        assertTrue(mp.toString().contains("My Label"));
    }

    // ── Step 2: getMutationType alias ─────────────────────────────────────────
    @Test
    void mutationPointGetMutationTypeReturnsType() {
        File f = new File("Dummy.java");
        MutationPoint mp = new MutationPoint(f, 1, 0, "+", "-",
                Mutator.MutatorType.CUSTOM, "lbl");
        assertEquals(Mutator.MutatorType.CUSTOM, mp.getMutationType());
    }

    // ── Step 3: Config.customMutationSpec ────────────────────────────────────

    @Test
    void readConfigParsesCustomMutationSpec(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp)
            throws Exception {
        java.nio.file.Path cfg = tmp.resolve("test.config");
        java.nio.file.Files.writeString(cfg,
            "source_code_directory=src\n" +
            "test_command=mvn test\n" +
            "mutator_type=CUSTOM\n" +
            "custom_mutation_spec=MyMutations.txt\n");

        Main.Config config = Main.readConfig(cfg.toFile());
        assertEquals("MyMutations.txt", config.customMutationSpec);
    }

    @Test
    void readConfigCustomMutationSpecAbsentYieldsNull(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path cfg = tmp.resolve("test.config");
        java.nio.file.Files.writeString(cfg,
            "source_code_directory=src\n" +
            "test_command=mvn test\n" +
            "mutator_type=AOR_PLUS_TO_MINUS\n");

        Main.Config config = Main.readConfig(cfg.toFile());
        assertNull(config.customMutationSpec);
    }

    // ── Step 11: MutationRuleGenerator ───────────────────────────────────────

    @Test
    void ruleGeneratorParsesAllRulesFromRealSpec() throws Exception {
        // The real OperatorMutations.txt: 11 Replace blocks, 8 with And: Inverse -> 19 total
        java.io.File spec = new java.io.File(
            "OperatorMutations.txt");
        java.util.List<MutationRule> rules = MutationRuleGenerator.generateRules(spec);
        assertEquals(23, rules.size());
    }

    @Test
    void ruleGeneratorLabelIncludesGroupLabel(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "Math Operations\n\n" +
            "\tReplace: {quantity} + {quantity}\n" +
            "\tWith: {quantity} - {quantity}\n\n" +
            "End\n");

        java.util.List<MutationRule> rules =
            MutationRuleGenerator.generateRules(spec.toFile());
        assertEquals(1, rules.size());
        assertTrue(rules.get(0).getLabel().contains("Math Operations"),
            "Label was: " + rules.get(0).getLabel());
    }

    @Test
    void ruleGeneratorInverseProducesExtraRule(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "Group\n\n" +
            "\tReplace: {quantity} + {quantity}\n" +
            "\tWith: {quantity} - {quantity}\n" +
            "\tAnd: Inverse\n\n" +
            "End\n");

        java.util.List<MutationRule> rules =
            MutationRuleGenerator.generateRules(spec.toFile());
        assertEquals(2, rules.size());
    }

}
