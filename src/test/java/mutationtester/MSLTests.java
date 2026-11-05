package mutationtester;

import mutationtester.model.*;
import mutationtester.scanner.*;

import mutationtester.msl.*;

import org.antlr.v4.runtime.CommonToken;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;
import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
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
    void readConfigRequiresCustomMutationSpec(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        // Mutations always come from a spec file; mutator_type alone isn't enough.
        java.nio.file.Path cfg = tmp.resolve("test.config");
        java.nio.file.Files.writeString(cfg,
            "source_code_directory=src\n" +
            "test_command=mvn test\n" +
            "mutator_type=AOR_PLUS_TO_MINUS\n");

        java.io.IOException error = assertThrows(java.io.IOException.class, () -> Main.readConfig(cfg.toFile()));
        assertTrue(error.getMessage().contains("custom_mutation_spec"), error.getMessage());
    }

    @Test
    void readConfigNoLongerNeedsMutatorType(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path cfg = tmp.resolve("test.config");
        java.nio.file.Files.writeString(cfg,
            "source_code_directory=src\n" +
            "test_command=mvn test\n" +
            "custom_mutation_spec=specs/OperatorMutations.spec\n");

        assertEquals("specs/OperatorMutations.spec", Main.readConfig(cfg.toFile()).customMutationSpec);
    }

    // ── Placeholders ──────────────────────────────────────────────────────────

    @Test
    void quantityPlaceholderMatchesWhenAdapterSaysQuantity() {
        assertNotNull(MSLMatcher.match(new MSLPlaceholder("quantity"), node("x"), mockAdapter(true)));
    }

    @Test
    void quantityPlaceholderRejectsWhenAdapterDenies() {
        assertNull(MSLMatcher.match(new MSLPlaceholder("quantity"), node("x"), mockAdapter(false)));
    }

    @Test
    void placeholderBindsTheMatchedNode() {
        ParseTree x = node("x");
        MSLBindings bindings = MSLMatcher.match(new MSLPlaceholder("quantity"), x, mockAdapter(true));
        assertSame(x, bindings.get("quantity", 0));
    }

    @Test
    void unknownPlaceholderTypeMatchesNothing() {
        assertNull(MSLMatcher.match(new MSLPlaceholder("no_such_thing"), node("x"), mockAdapter(true)));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A one-token CST node, enough for adapters that answer from text. */
    private static ParseTree node(String text) {
        return new TerminalNodeImpl(new CommonToken(0, text));
    }

    private static MSLScannerAdapter mockAdapter(boolean isQuantityResult) {
        return new MSLScannerAdapter() {
            public boolean isBinaryExpressionFor(ParseTree c, String op) { return false; }
            public ParseTree getLeftOperand(ParseTree c) { return null; }
            public ParseTree getRightOperand(ParseTree c) { return null; }
            public ParseTree getOperatorToken(ParseTree c) { return null; }
            public boolean isQuantity(ParseTree c) { return isQuantityResult; }
            public String getNodeText(ParseTree c) { return c == null ? "" : c.getText(); }
            public int[] getTokenPosition(ParseTree c) { return new int[]{1, 0}; }
            public ParseTree getRootContext() { return null; }
        };
    }

    // ── Binary operator trees ─────────────────────────────────────────────────

    private static final MSLTree PLUS = MSLPatternParser.parse("{quantity} + {quantity}");
    private static final MSLTree MINUS = MSLPatternParser.parse("{quantity} - {quantity}");

    @Test
    void binaryTreeMatchesWhenAdapterConfirms() {
        assertNotNull(MSLMatcher.match(PLUS, node("a+b"), mockBinaryAdapter("+", true, true, true)));
    }

    @Test
    void binaryTreeRejectsWhenAdapterDeniesExpression() {
        assertNull(MSLMatcher.match(PLUS, node("a+b"), mockBinaryAdapter("+", false, true, true)));
    }

    @Test
    void binaryTreeRejectsWhenLeftNotQuantity() {
        assertNull(MSLMatcher.match(PLUS, node("a+b"), mockBinaryAdapter("+", true, false, true)));
    }

    @Test
    void binaryTreeRejectsWhenRightNotQuantity() {
        assertNull(MSLMatcher.match(PLUS, node("a+b"), mockBinaryAdapter("+", true, true, false)));
    }

    @Test
    void binaryTreeKeepsItsOperator() {
        MSLNode shift = (MSLNode) MSLPatternParser.parse("{quantity} >> {quantity}");
        assertEquals(MSLNode.BINARY, shift.kind());
        assertEquals(">>", shift.value());
    }

    /** Adapter where isBinaryExpressionFor checks op match, and left/right isQuantity is configurable. */
    private static MSLScannerAdapter mockBinaryAdapter(
            String expectedOp, boolean binaryMatch,
            boolean leftIsQuantity, boolean rightIsQuantity) {
        ParseTree left = node("a");
        ParseTree right = node("b");
        return new MSLScannerAdapter() {
            public boolean isBinaryExpressionFor(ParseTree c, String op) {
                return binaryMatch && op.equals(expectedOp) && c != left && c != right;
            }
            public ParseTree getLeftOperand(ParseTree c) { return left; }
            public ParseTree getRightOperand(ParseTree c) { return right; }
            public ParseTree getOperatorToken(ParseTree c) { return null; }
            public boolean isQuantity(ParseTree c) {
                return c == left ? leftIsQuantity : rightIsQuantity;
            }
            public String getNodeText(ParseTree c) { return c == null ? "" : c.getText(); }
            public int[] getTokenPosition(ParseTree c) { return new int[]{1, 0}; }
            public ParseTree getRootContext() { return null; }
        };
    }

    // ── Pattern parser ────────────────────────────────────────────────────────

    private static String binaryOperator(String pattern) {
        MSLNode node = (MSLNode) MSLPatternParser.parse(pattern);
        assertEquals(MSLNode.BINARY, node.kind());
        return node.value();
    }

    @Test
    void parserParsesQuantity() {
        assertEquals(new MSLPlaceholder("quantity"), MSLPatternParser.parse("{quantity}"));
    }

    @Test
    void parserParsesCanonicalOperators() {
        for (String op : List.of("+", "-", "*", "/", "%", "//", "**", "<", "<=", ">", ">=", "==", "!=",
                "&&", "||", "&", "|", "^", "<<", ">>", ">>>")) {
            assertEquals(op, binaryOperator("{quantity} " + op + " {quantity}"), op);
        }
    }

    @Test
    void parserReadsTheLongestOperator() {
        // >>> is one operator, not >> followed by >.
        assertEquals(">>>", binaryOperator("{quantity}>>>{quantity}"));
    }

    @Test
    void parserRespectsPrecedence() {
        MSLNode sum = (MSLNode) MSLPatternParser.parse("{quantity} + {quantity} * {quantity}");
        assertEquals("+", sum.value());
        assertEquals("*", ((MSLNode) sum.child(1)).value());
        MSLNode grouped = (MSLNode) MSLPatternParser.parse("({quantity} + {quantity}) * {quantity}");
        assertEquals("*", grouped.value());
    }

    @Test
    void parserReadsPlainEnglishChecks() {
        MSLNode check = (MSLNode) MSLPatternParser.parse("{quantity} is null");
        assertEquals(MSLNode.PREDICATE, check.kind());
        assertEquals("is null", check.value());
        assertEquals("is not null", ((MSLNode) MSLPatternParser.parse("{quantity} is not null")).value());
    }

    @Test
    void parserReadsCallsAndLiterals() {
        MSLNode call = (MSLNode) MSLPatternParser.parse("max({quantity}, 0)");
        assertEquals(MSLNode.CALL, call.kind());
        assertEquals("max", call.value());
        assertEquals(new MSLNode(MSLNode.LITERAL, "0"), call.child(1));
        assertEquals(new MSLNode(MSLNode.LITERAL, "null"), MSLPatternParser.parse("null"));
    }

    @Test
    void parserReadsStatements() {
        MSLNode guard = (MSLNode) MSLPatternParser.parse("if ({match: {quantity} <= 0}) {quantity} = 0");
        assertEquals(MSLNode.IF, guard.kind());
        MSLPlaceholder match = (MSLPlaceholder) guard.child(0);
        assertTrue(match.isMatch());
        assertEquals("<=", ((MSLNode) match.constraint()).value());
        assertEquals(MSLNode.ASSIGN, ((MSLNode) guard.child(1)).kind());

        MSLNode loop = (MSLNode) MSLPatternParser.parse("for ({quantity}; {match}; {quantity}) {statement}");
        assertEquals(MSLNode.FOR, loop.kind());
        assertEquals(4, loop.children().size());

        MSLNode block = (MSLNode) MSLPatternParser.parse("while ({match}) { {quantity} = 0 }");
        assertEquals(MSLNode.BLOCK, ((MSLNode) block.child(1)).kind());
    }

    @Test
    void parserRejectsParserTokenAlias() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MSLPatternParser.parse("{quantity} LE {quantity}"));
        assertTrue(error.getMessage().contains("Use the canonical operator \"<=\""),
            error.getMessage());
    }

    @Test
    void parserRejectsCaseVariantAlias() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MSLPatternParser.parse("{quantity} leq {quantity}"));
        assertTrue(error.getMessage().contains("Use the canonical operator \"<=\""),
            error.getMessage());
    }

    @Test
    void parserRejectsMalformedPatterns() {
        assertThrows(IllegalArgumentException.class, () -> MSLPatternParser.parse("{quantity} +"));
        assertThrows(IllegalArgumentException.class, () -> MSLPatternParser.parse("for ({quantity}"));
        assertThrows(IllegalArgumentException.class, () -> MSLPatternParser.parse("{quantity} is"));
        assertThrows(IllegalArgumentException.class, () -> MSLPatternParser.parse(""));
    }

    // ── Rules ─────────────────────────────────────────────────────────────────

    @Test
    void replaceRuleMatchesWhenItsTreeMatches() {
        ReplaceRule rule = new ReplaceRule(PLUS, MINUS, List.of(), "AOR + to -");
        assertTrue(rule.matches(mockBinaryAdapter("+", true, true, true), node("a+b")));
    }

    @Test
    void replaceRuleDoesNotMatchWhenItsTreeDoesNot() {
        ReplaceRule rule = new ReplaceRule(PLUS, MINUS, List.of(), "AOR + to -");
        assertFalse(rule.matches(mockBinaryAdapter("+", false, true, true), node("a+b")));
    }

    @Test
    void replaceRuleApplyProducesCorrectMutationPoint() {
        MSLScannerAdapter adapter = mockBinaryAdapterWithPosition("+", 5, 10);
        File f = new File("Test.java");
        ReplaceRule rule = new ReplaceRule(PLUS, MINUS, List.of(), "AOR + to -");

        MutationPoint mp = rule.apply(adapter, node("a+b"), f);
        assertAll(
            () -> assertEquals("+",   mp.getOperator()),
            () -> assertEquals("-",   mp.getReplacement()),
            () -> assertEquals(5,     mp.getLineNumber()),
            () -> assertEquals(10,    mp.getColumnNumber()),
            () -> assertEquals(Mutator.MutatorType.CUSTOM, mp.getMutationType()),
            () -> assertEquals("AOR + to -", mp.getCustomLabel())
        );
    }

    @Test
    void replaceRuleGetLabelReturnsLabel() {
        assertEquals("my label", new ReplaceRule(PLUS, MINUS, List.of(), "my label").getLabel());
    }

    /** Adapter that returns specific line/col for position-extraction test. */
    private static MSLScannerAdapter mockBinaryAdapterWithPosition(
            String expectedOp, int line, int col) {
        ParseTree left = node("a");
        ParseTree right = node("b");
        return new MSLScannerAdapter() {
            public boolean isBinaryExpressionFor(ParseTree c, String op) {
                return op.equals(expectedOp) && c != left && c != right;
            }
            public ParseTree getLeftOperand(ParseTree c) { return left; }
            public ParseTree getRightOperand(ParseTree c) { return right; }
            public ParseTree getOperatorToken(ParseTree c) { return null; }
            public boolean isQuantity(ParseTree c) { return true; }
            public String getNodeText(ParseTree c) { return c == null ? "" : c.getText(); }
            public int[] getTokenPosition(ParseTree c) { return new int[]{line, col}; }
            public ParseTree getRootContext() { return null; }
        };
    }

    // ── MutationRuleFactory ───────────────────────────────────────────────────

    // ── MutationRuleGenerator ─────────────────────────────────────────────────

    private static List<MutationRule> rulesFrom(java.nio.file.Path tmp, String spec) throws Exception {
        java.nio.file.Path file = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(file, spec);
        return MutationRuleGenerator.generateRules(file.toFile());
    }

    @Test
    void ruleGeneratorParsesAllRulesFromRealSpec() throws Exception {
        // The real specs/OperatorMutations.spec: 25 Replace blocks, 7 with And: Inverse,
        // and 2 Delete blocks -> 34 total
        java.util.List<MutationRule> rules = MutationRuleGenerator.generateRules(new java.io.File("specs/OperatorMutations.spec"));
        assertEquals(34, rules.size());
    }

    @Test
    void ruleGeneratorParsesEveryBundledSpec() throws Exception {
        for (String spec : List.of("specs/OperatorMutations.spec", "specs/StatementMutation.spec", "specs/StringMutation.spec",
                "specs/context-sensitive-mutations.spec")) {
            assertFalse(MutationRuleGenerator.generateRules(new File(spec)).isEmpty(), spec);
        }
    }

    @Test
    void ruleGeneratorLabelIncludesGroupLabel(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationRule> rules = rulesFrom(tmp,
            "Math Operations\n\n" +
            "\tReplace: {quantity} + {quantity}\n" +
            "\tWith: {quantity} - {quantity}\n\n" +
            "End\n");
        assertEquals(1, rules.size());
        assertEquals("Math Operations: + → -", rules.get(0).getLabel());
    }

    @Test
    void ruleGeneratorInverseProducesExtraRule(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationRule> rules = rulesFrom(tmp,
            "Group\n\n" +
            "\tReplace: {quantity} + {quantity}\n" +
            "\tWith: {quantity} - {quantity}\n" +
            "\tAnd: Inverse\n\n" +
            "End\n");
        assertEquals(2, rules.size());
        assertEquals("Group: - → +", rules.get(1).getLabel());
    }

    // ── Bad spec files: one clear line naming the file, line and fix ──────────

    private static java.io.IOException specError(java.nio.file.Path dir, String content) throws Exception {
        java.nio.file.Path spec = dir.resolve("bad.spec");
        java.nio.file.Files.writeString(spec, content);
        return assertThrows(java.io.IOException.class,
                () -> MutationRuleGenerator.generateRules(spec.toFile()));
    }

    @Test
    void badSpecWithAnOperatorAliasNamesTheFileLineAndFix(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.io.IOException e = specError(tmp,
            "Boundary\n\n\tReplace: {quantity} LE {quantity}\n\tWith: {quantity} < {quantity}\nEnd\n");
        assertEquals("bad.spec, line 3: Unsupported MSL operator \"LE\". Use the canonical operator \"<=\".",
            e.getMessage());
    }

    @Test
    void badSpecWithAWordOperatorNamesTheFix(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.io.IOException e = specError(tmp,
            "Logic\n\n\tReplace: {quantity} and {quantity}\n\tWith: {quantity} || {quantity}\nEnd\n");
        assertTrue(e.getMessage().startsWith("bad.spec, line 3: "), e.getMessage());
        assertTrue(e.getMessage().contains("Use the canonical operator \"&&\""), e.getMessage());
    }

    @Test
    void badSpecWithReplaceButNoWithPointsAtTheReplaceLine(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.io.IOException e = specError(tmp,
            "Math\n\n\tReplace: {quantity} + {quantity}\n\n\tReplace: {quantity} - {quantity}\n"
            + "\tWith: {quantity} + {quantity}\nEnd\n");
        assertTrue(e.getMessage().startsWith("bad.spec, line 3: \"Replace:\" has no \"With:\""), e.getMessage());
    }

    @Test
    void badSpecWithAMistypedRuleLineIsNotSilentlySkipped(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.io.IOException e = specError(tmp,
            "Math\n\n\tReplace {quantity} + {quantity}\n\tWith: {quantity} - {quantity}\nEnd\n");
        assertTrue(e.getMessage().startsWith("bad.spec, line 3: \"Replace {quantity} + {quantity}\" isn't part of a rule"),
            e.getMessage());
    }

    @Test
    void missingSpecFileSaysNotFound() {
        java.io.IOException e = assertThrows(java.io.IOException.class,
            () -> MutationRuleGenerator.generateRules(new File("no-such-spec.txt")));
        assertTrue(e.getMessage().startsWith("Spec file not found: "), e.getMessage());
    }

    @Test
    void everySpecFileInTheRepoStillLoads() throws Exception {
        List<File> specs;
        try (var paths = java.nio.file.Files.walk(java.nio.file.Path.of("specs"))) {
            specs = paths.filter(p -> p.toString().endsWith(".spec")).map(java.nio.file.Path::toFile).toList();
        }
        assertFalse(specs.isEmpty(), "no spec files under specs/");
        for (File spec : specs) {
            assertFalse(MutationRuleGenerator.generateRules(spec).isEmpty(), spec + " has no rules");
        }
    }

    @Test
    void ruleGeneratorBuildsPitStyleRulesAsTrees(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationRule> rules = rulesFrom(tmp,
            "PIT Built-in Mutations\n\n" +
            "\tDelete: {invocation_statement}\n\n" +
            "\tReplace: {return_value}\n" +
            "\tWith: {empty_value}\n\n" +
            "\tReplace: {constructor_call}\n" +
            "\tWith: null\n\n" +
            "\tReplace: {non_void_method_call}\n" +
            "\tWith: {default_value}\n\n" +
            "\tReplace: {equality_condition}\n" +
            "\tWith: true\n\n" +
            "\tReplace: {order_condition}\n" +
            "\tWith: false\n\n" +
            "End\n");

        assertEquals(6, rules.size());
        assertInstanceOf(DeleteRule.class, rules.get(0));
        assertTrue(rules.subList(1, 6).stream().allMatch(rule -> rule instanceof ReplaceRule));
        assertEquals("PIT Built-in Mutations: delete invocation statement", rules.get(0).getLabel());
        assertEquals("PIT Built-in Mutations: return value → empty value", rules.get(1).getLabel());
        assertEquals("PIT Built-in Mutations: constructor call → null", rules.get(2).getLabel());
        assertEquals("PIT Built-in Mutations: non void method call → default value", rules.get(3).getLabel());
        assertEquals("PIT Built-in Mutations: equality condition → true", rules.get(4).getLabel());
        assertEquals("PIT Built-in Mutations: order condition → false", rules.get(5).getLabel());
    }

    @Test
    void ruleGeneratorReadsInsertBeforeAndAfter(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationRule> rules = rulesFrom(tmp,
            "Inserts\n\n" +
            "\tInsert: {quantity} = 0\n" +
            "\tBefore: {quantity} = {quantity}\n\n" +
            "\tInsert: {quantity} = 0\n" +
            "\tAfter: {quantity} = {quantity}\n\n" +
            "End\n");
        assertEquals(2, rules.size());
        assertTrue(((InsertRule) rules.get(0)).isBefore());
        assertFalse(((InsertRule) rules.get(1)).isBefore());
        assertEquals("Inserts: insert {quantity} = 0 before {quantity} = {quantity}", rules.get(0).getLabel());
    }

    @Test
    void ruleGeneratorRejectsBadClauses(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) {
        // Except: without {match}
        assertThrows(java.io.IOException.class, () -> rulesFrom(tmp,
            "G\n\tReplace: {quantity} + {quantity}\n\tWith: {quantity} - {quantity}\n"
            + "\tExcept: if ({quantity} > 0) {quantity} = 0\nEnd\n"));
        // Insert: with neither Before: nor After:
        assertThrows(java.io.IOException.class, () -> rulesFrom(tmp,
            "G\n\tInsert: {quantity} = 0\n\tWith: {quantity}\nEnd\n"));
        // Insert: with both
        assertThrows(java.io.IOException.class, () -> rulesFrom(tmp,
            "G\n\tInsert: {quantity} = 0\n\tBefore: {quantity} = 1\n\tAfter: {quantity} = 1\nEnd\n"));
        // With: reads more {quantity} than Replace: matched
        assertThrows(java.io.IOException.class, () -> rulesFrom(tmp,
            "G\n\tReplace: -{quantity}\n\tWith: {quantity} - {quantity}\nEnd\n"));
        // And: Inverse on a Delete:
        assertThrows(java.io.IOException.class, () -> rulesFrom(tmp,
            "G\n\tDelete: {invocation_statement}\n\tAnd: Inverse\nEnd\n"));
        // An old-style fragment that isn't a whole pattern
        assertThrows(java.io.IOException.class, () -> rulesFrom(tmp,
            "G\n\tReplace: {quantity} < {quantity}\n\tWith: {quantity} <= {quantity}\n\tExcept: for ({quantity}\nEnd\n"));
    }

    @Test
    void ruleGeneratorInverseInheritsExcepts(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationRule> rules = rulesFrom(tmp,
            "Group\n\n" +
            "\tReplace: {quantity} <= {quantity}\n" +
            "\tWith: {quantity} < {quantity}\n" +
            "\tExcept: if ({match: {quantity} <= 0}) {quantity} = 0\n" +
            "\tAnd: Inverse\n\n" +
            "End\n");
        assertEquals(2, rules.size());
        assertEquals(1, ((ReplaceRule) rules.get(0)).getExcepts().size(),
            "Forward rule should have 1 Except: pattern");
        assertEquals(1, ((ReplaceRule) rules.get(1)).getExcepts().size(),
            "Inverse rule should inherit Except: patterns from the forward rule");
    }

    // ── JavaMutationScanner.createAdapter() ───────────────────────────────────

    @Test
    void javaAdapterIsBinaryExpressionForPlus(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        MSLScannerAdapter adapter = javaAdapter(tmp, "class A { int x = 1 + 2; }");
        assertTrue(treeMatches(adapter, adapter.getRootContext(), PLUS), "Should find '1 + 2' as a + binary expression");
    }

    @Test
    void javaAdapterIsBinaryExpressionForMinus(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        MSLScannerAdapter adapter = javaAdapter(tmp, "class A { int x = 1 + 2; }");
        assertFalse(treeMatches(adapter, adapter.getRootContext(), MINUS), "Should NOT find '1 + 2' as a - expression");
    }

    @Test
    void javaAdapterIsQuantityTrueForIntLiteral(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        MSLScannerAdapter adapter = javaAdapter(tmp, "class A { int x = 1 + 2; }");
        assertTrue(anyLiteralIsQuantity(adapter, adapter.getRootContext()), "Integer literals should be quantities");
    }

    @Test
    void javaAdapterFlagsStringLiteralsSoArithmeticSkipsThem(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        // String literals are operands (s == "" is mutated), but the adapter
        // flags them so arithmetic rules skip concatenation like "a" + b.
        MSLScannerAdapter adapter = javaAdapter(tmp, "class A { String s = \"hello\"; }");
        assertTrue(anyStringLiteralIsFlagged(adapter, adapter.getRootContext()), "String literals should be flagged as strings");
    }

    @Test
    void javaAdapterMatchesEachOperatorKind(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        for (String[] c : new String[][]{{"int", ">>"}, {"boolean", ">"}, {"boolean", "=="}, {"boolean", "&&"}}) {
            MSLScannerAdapter adapter = javaAdapter(tmp, "class A { " + c[0] + " x = a " + c[1] + " b; }");
            MSLTree tree = MSLPatternParser.parse("{quantity} " + c[1] + " {quantity}");
            assertTrue(treeMatches(adapter, adapter.getRootContext(), tree), "Should find a " + c[1] + " b");
        }
    }

    @Test
    void javaAdapterMatchesStatementTrees(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        MSLScannerAdapter adapter = javaAdapter(tmp,
            "class A { void f(int n) { if (n <= 0) n = 0; while (n > 1) { n = 0; } } }");
        assertTrue(treeMatches(adapter, adapter.getRootContext(), MSLPatternParser.parse("if ({quantity}) {quantity} = 0")));
        assertTrue(treeMatches(adapter, adapter.getRootContext(), MSLPatternParser.parse("while ({quantity}) { {quantity} = 0 }")));
        assertFalse(treeMatches(adapter, adapter.getRootContext(), MSLPatternParser.parse("while ({quantity}) {quantity} = 0")),
            "A block body is not a bare assignment");
    }

    // ── Tree-walking helpers for adapter tests ────────────────────────────────

    private static MSLScannerAdapter javaAdapter(java.nio.file.Path tmp, String source) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, source);
        return JavaMutationScanner.createAdapter(src.toFile());
    }

    /** Returns true if any node in the tree matches the MSL tree. */
    private boolean treeMatches(MSLScannerAdapter adapter, ParseTree node, MSLTree pattern) {
        if (MSLMatcher.match(pattern, node, adapter) != null) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (treeMatches(adapter, node.getChild(i), pattern)) return true;
        }
        return false;
    }

    /** Returns true if any TerminalNode in the tree is considered a quantity. */
    private boolean anyLiteralIsQuantity(MSLScannerAdapter adapter, ParseTree node) {
        if (node instanceof TerminalNode tn) {
            String txt = tn.getText();
            try { Integer.parseInt(txt); return adapter.isQuantity(node); } catch (NumberFormatException ignored) {}
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (anyLiteralIsQuantity(adapter, node.getChild(i))) return true;
        }
        return false;
    }

    /** Returns true if any string-literal TerminalNode is flagged as a string. */
    private boolean anyStringLiteralIsFlagged(MSLScannerAdapter adapter, ParseTree node) {
        if (node instanceof TerminalNode tn) {
            String txt = tn.getText();
            if (txt.startsWith("\"") && adapter.isStringLiteral(node)) return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (anyStringLiteralIsFlagged(adapter, node.getChild(i))) return true;
        }
        return false;
    }

    // ── Java context-sensitive (Except:) mutations ────────────────────────────

    private static final String LE_CLAMP_SPEC =
            "Test\n\n" +
            "\tReplace: {quantity} <= {quantity}\n" +
            "\tWith: {quantity} < {quantity}\n" +
            "\tExcept: if ({match: {quantity} <= 0}) {quantity} = 0\n\n" +
            "End\n";

    private static final String GE_CLAMP_SPEC = LE_CLAMP_SPEC.replace("<=", ">=").replace(" < ", " > ");

    @Test
    void exceptSuppressesMutationOnLeClampLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationPoint> points = scanJava(tmp,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n <= 0) n = 0;\n" +
            "        return n;\n" +
            "    }\n" +
            "}\n", LE_CLAMP_SPEC);
        assertEquals(0, points.size(),
            "Clamp line should be suppressed by Except: — expected 0, got " + points.size());
    }

    @Test
    void exceptSuppressesLeClampButNotRealLogicLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationPoint> points = scanJava(tmp,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n <= 0) n = 0;\n" +   // guard — suppressed
            "        return n;\n" +
            "    }\n" +
            "    boolean isUnder(int x, int limit) {\n" +
            "        return x <= limit;\n" +    // real logic — fires
            "    }\n" +
            "}\n", LE_CLAMP_SPEC);
        assertEquals(1, points.size(),
            "Only real-logic <= should fire; clamp suppressed. Points: " + points.size());
        assertEquals("<=", points.get(0).getOperator());
    }

    @Test
    void exceptOnlyCoversTheMatchPosition(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        // The guard's condition is suppressed, but a <= elsewhere inside the
        // same if statement is not: {match} pins the position.
        List<MutationPoint> points = scanJava(tmp,
            "public class T {\n" +
            "    int f(int n, int m) {\n" +
            "        if (n <= 0) n = 0;\n" +
            "        if (m <= 1) m = 0;\n" +   // not "<= 0": mutated
            "        return n;\n" +
            "    }\n" +
            "}\n", LE_CLAMP_SPEC);
        assertEquals(1, points.size());
        assertEquals(4, points.get(0).getLineNumber());
    }

    @Test
    void exceptSuppressesMutationOnGeClampLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationPoint> points = scanJava(tmp,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n >= 0) n = 0;\n" +
            "        return n;\n" +
            "    }\n" +
            "}\n", GE_CLAMP_SPEC);
        assertEquals(0, points.size(),
            ">= clamp line should be suppressed by Except: — expected 0, got " + points.size());
    }

    @Test
    void exceptSuppressesGeClampButNotRealLogicLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationPoint> points = scanJava(tmp,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n >= 0) n = 0;\n" +   // guard — suppressed
            "        return n;\n" +
            "    }\n" +
            "    boolean isAbove(int x, int threshold) {\n" +
            "        return x >= threshold;\n" + // real logic — fires
            "    }\n" +
            "}\n", GE_CLAMP_SPEC);
        assertEquals(1, points.size(),
            "Only real-logic >= should fire; clamp suppressed. Points: " + points.size());
        assertEquals(">=", points.get(0).getOperator());
    }

    @Test
    void endToEndContextSensitiveJavaSpecSuppressesGuardLine(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("Boundary.java");
        java.nio.file.Files.writeString(src,
            "public class Boundary {\n" +
            "    int clamp(int n) {\n" +
            "        if (n <= 0) n = 0;\n" +   // suppressed by real spec
            "        return n;\n" +
            "    }\n" +
            "    boolean isUnder(int x, int limit) {\n" +
            "        return x <= limit;\n" +    // fires
            "    }\n" +
            "}\n");

        List<MutationPoint> points = scanWithSpec(src.toFile(), new File("specs/context-sensitive-mutations.spec"));

        long lePoints = points.stream()
            .filter(p -> p.getOperator().equals("<="))
            .count();
        assertEquals(1, lePoints,
            "Only 1 <= mutation should fire (clamp suppressed by real spec). " +
            "Total points: " + points.size());
    }

    @Test
    void exceptBlockPatternMatchesAcrossLines(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationPoint> points = scanJava(tmp,
            "public class SpacingTest {\n" +
            "    void test(int a) {\n" +
            "        if (a > 0) {\n" +
            "            a = 0;\n" +
            "        }\n" +
            "    }\n" +
            "}\n",
            "Test Spacing\n\n" +
            "    Replace: {quantity} > {quantity}\n" +
            "    With: {quantity} >= {quantity}\n" +
            "    Except: if ({match}) { {quantity} = {quantity} }\n\n" +
            "End\n");
        assertEquals(0, points.size(),
            "The multi-line brace structure should be successfully matched and suppressed.");
    }

    @Test
    void deleteAndInsertRulesEditStatements(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        List<MutationPoint> points = scanJava(tmp,
            "class T {\n" +
            "    void f(int n) {\n" +
            "        log(n);\n" +
            "    }\n" +
            "}\n",
            "Statements\n\n" +
            "\tDelete: {invocation_statement}\n\n" +
            "\tInsert: n = 0\n" +
            "\tBefore: {invocation_statement}\n\n" +
            "End\n");
        assertEquals(2, points.size());
        MutationPoint delete = points.get(0);
        assertEquals("log(n);", delete.getOperator());
        assertEquals(";", delete.getReplacement());
        MutationPoint insert = points.get(1);
        assertEquals("", insert.getOperator());
        assertEquals("n = 0;\n        ", insert.getReplacement());
        assertEquals(3, insert.getLineNumber());
        assertEquals(8, insert.getColumnNumber());
    }

    // ── Helpers for the Except: tests ─────────────────────────────────────────

    private List<MutationPoint> scanJava(java.nio.file.Path tmp, String source, String spec) throws Exception {
        java.nio.file.Path src = tmp.resolve("T.java");
        java.nio.file.Files.writeString(src, source);
        java.nio.file.Path specFile = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(specFile, spec);
        return scanWithSpec(src.toFile(), specFile.toFile());
    }

    /** Runs adapter + rule pipeline, returns all mutation points found. */
    private List<MutationPoint> scanWithSpec(File sourceFile, File specFile)
            throws Exception {
        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(sourceFile);
        java.util.List<MutationRule> rules = MutationRuleGenerator.generateRules(specFile);
        List<MutationPoint> points = new java.util.ArrayList<>();
        for (MutationRule rule : rules) {
            points.addAll(rule.findMutationPoints(adapter, sourceFile));
        }
        return points;
    }
}
