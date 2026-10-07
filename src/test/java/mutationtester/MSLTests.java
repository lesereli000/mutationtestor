package mutationtester;

import mutationtester.coverage.*;
import mutationtester.core.*;
import mutationtester.model.*;
import mutationtester.scanner.*;

import mutationtester.msl.*;

import org.junit.jupiter.api.Test;
import java.io.File;
import java.util.List;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
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

    // ── Step 5: QuantityNode ──────────────────────────────────────────────────

    @Test
    void quantityNodeMatchesTrueWhenAdapterSaysQuantity() {
        QuantityNode node = new QuantityNode();
        assertTrue(node.matches(mockAdapter(true), null));
    }

    @Test
    void quantityNodeMatchesFalseWhenAdapterDenies() {
        QuantityNode node = new QuantityNode();
        assertFalse(node.matches(mockAdapter(false), null));
    }

    @Test
    void quantityNodeGetReplacementTextDelegatesToAdapter() {
        QuantityNode node = new QuantityNode();
        assertEquals("x", node.getReplacementText(mockAdapterWithText("x"), null));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static MSLScannerAdapter mockAdapter(boolean isQuantityResult) {
        return new MSLScannerAdapter() {
            public boolean isBinaryExpressionFor(org.antlr.v4.runtime.tree.ParseTree c, String op) { return false; }
            public org.antlr.v4.runtime.tree.ParseTree getLeftOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getRightOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getOperatorToken(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public boolean isQuantity(org.antlr.v4.runtime.tree.ParseTree c) { return isQuantityResult; }
            public String getNodeText(org.antlr.v4.runtime.tree.ParseTree c) { return ""; }
            public int[] getTokenPosition(org.antlr.v4.runtime.tree.ParseTree c) { return new int[]{1, 0}; }
            public org.antlr.v4.runtime.tree.ParseTree getRootContext() { return null; }
        };
    }

    private static MSLScannerAdapter mockAdapterWithText(String text) {
        return new MSLScannerAdapter() {
            public boolean isBinaryExpressionFor(org.antlr.v4.runtime.tree.ParseTree c, String op) { return false; }
            public org.antlr.v4.runtime.tree.ParseTree getLeftOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getRightOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getOperatorToken(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public boolean isQuantity(org.antlr.v4.runtime.tree.ParseTree c) { return true; }
            public String getNodeText(org.antlr.v4.runtime.tree.ParseTree c) { return text; }
            public int[] getTokenPosition(org.antlr.v4.runtime.tree.ParseTree c) { return new int[]{1, 0}; }
            public org.antlr.v4.runtime.tree.ParseTree getRootContext() { return null; }
        };
    }

    // ── Step 6: BinaryOpNode ──────────────────────────────────────────────────

    @Test
    void binaryOpNodeMatchesWhenAdapterConfirms() {
        MSLScannerAdapter adapter = mockBinaryAdapter("+", true, true, true);
        BinaryOpNode node = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        assertTrue(node.matches(adapter, null));
    }

    @Test
    void binaryOpNodeRejectsWhenAdapterDeniesExpression() {
        MSLScannerAdapter adapter = mockBinaryAdapter("+", false, true, true);
        BinaryOpNode node = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        assertFalse(node.matches(adapter, null));
    }

    @Test
    void binaryOpNodeRejectsWhenLeftNotQuantity() {
        MSLScannerAdapter adapter = mockBinaryAdapter("+", true, false, true);
        BinaryOpNode node = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        assertFalse(node.matches(adapter, null));
    }

    @Test
    void binaryOpNodeRejectsWhenRightNotQuantity() {
        MSLScannerAdapter adapter = mockBinaryAdapter("+", true, true, false);
        BinaryOpNode node = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        assertFalse(node.matches(adapter, null));
    }

    @Test
    void binaryOpNodeGetOperatorReturnsStoredOperator() {
        BinaryOpNode node = new BinaryOpNode(">>", new QuantityNode(), new QuantityNode());
        assertEquals(">>", node.getOperator());
    }

    /** Adapter where isBinaryExpressionFor checks op match, and left/right isQuantity is configurable. */
    private static MSLScannerAdapter mockBinaryAdapter(
            String expectedOp, boolean binaryMatch,
            boolean leftIsQuantity, boolean rightIsQuantity) {
        return new MSLScannerAdapter() {
            int callCount = 0;
            public boolean isBinaryExpressionFor(org.antlr.v4.runtime.tree.ParseTree c, String op) {
                return binaryMatch && op.equals(expectedOp);
            }
            public org.antlr.v4.runtime.tree.ParseTree getLeftOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getRightOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getOperatorToken(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public boolean isQuantity(org.antlr.v4.runtime.tree.ParseTree c) {
                return callCount++ == 0 ? leftIsQuantity : rightIsQuantity;
            }
            public String getNodeText(org.antlr.v4.runtime.tree.ParseTree c) { return ""; }
            public int[] getTokenPosition(org.antlr.v4.runtime.tree.ParseTree c) { return new int[]{1, 0}; }
            public org.antlr.v4.runtime.tree.ParseTree getRootContext() { return null; }
        };
    }

    // ── Step 7: MSLExpressionFactory ─────────────────────────────────────────

    @Test
    void expressionFactoryParsesQuantity() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity}");
        assertInstanceOf(QuantityNode.class, expr);
    }

    @Test
    void expressionFactoryParsesBinaryPlus() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} + {quantity}");
        assertInstanceOf(BinaryOpNode.class, expr);
        assertEquals("+", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryParsesBinaryShiftRight() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} >> {quantity}");
        assertEquals(">>", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryParsesBinaryUnsignedShift() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} >>> {quantity}");
        assertEquals(">>>", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryParsesModulo() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} % {quantity}");
        assertEquals("%", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryParsesGreaterThan() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} > {quantity}");
        assertEquals(">", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryParsesEquality() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} == {quantity}");
        assertEquals("==", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryParsesLogicalAnd() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} && {quantity}");
        assertEquals("&&", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryAcceptsCanonicalLessThanOrEqualSymbol() {
        MSLExpression expr = MSLExpressionFactory.parse("{quantity} <= {quantity}");
        assertEquals("<=", ((BinaryOpNode) expr).getOperator());
    }

    @Test
    void expressionFactoryRejectsParserTokenAlias() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MSLExpressionFactory.parse("{quantity} LE {quantity}"));
        assertTrue(error.getMessage().contains("Use the canonical operator \"<=\""),
            error.getMessage());
    }

    @Test
    void expressionFactoryRejectsCaseVariantAlias() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
            () -> MSLExpressionFactory.parse("{quantity} leq {quantity}"));
        assertTrue(error.getMessage().contains("Use the canonical operator \"<=\""),
            error.getMessage());
    }

    @Test
    void expressionFactoryThrowsOnUnknownPattern() {
        assertThrows(IllegalArgumentException.class,
            () -> MSLExpressionFactory.parse("{unknown} + {unknown}"));
    }

    // ── Steps 9: ReplacementRule ─────────────────────────────────────────────────────

    @Test
    void swapRuleMatchesDelegatesToFromExpr() {
        MSLScannerAdapter adapter = mockBinaryAdapter("+", true, true, true);
        BinaryOpNode from = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        BinaryOpNode to   = new BinaryOpNode("-", new QuantityNode(), new QuantityNode());
        ReplacementRule rule = new ReplacementRule(from, to, "AOR + to -");
        assertTrue(rule.matches(adapter, null));
    }

    @Test
    void swapRuleDoesNotMatchWhenFromDoesNot() {
        MSLScannerAdapter adapter = mockBinaryAdapter("+", false, true, true);
        BinaryOpNode from = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        BinaryOpNode to   = new BinaryOpNode("-", new QuantityNode(), new QuantityNode());
        ReplacementRule rule = new ReplacementRule(from, to, "AOR + to -");
        assertFalse(rule.matches(adapter, null));
    }

    @Test
    void swapRuleApplyProducesCorrectMutationPoint() {
        MSLScannerAdapter adapter = mockBinaryAdapterWithPosition("+", 5, 10);
        File f = new File("Test.java");
        BinaryOpNode from = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        BinaryOpNode to   = new BinaryOpNode("-", new QuantityNode(), new QuantityNode());
        ReplacementRule rule = new ReplacementRule(from, to, "AOR + to -");

        MutationPoint mp = rule.apply(adapter, null, f);
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
    void swapRuleGetLabelReturnsLabel() {
        BinaryOpNode from = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        BinaryOpNode to   = new BinaryOpNode("-", new QuantityNode(), new QuantityNode());
        assertEquals("my label", new ReplacementRule(from, to, "my label").getLabel());
    }

    /** Adapter that returns specific line/col for position-extraction test. */
    private static MSLScannerAdapter mockBinaryAdapterWithPosition(
            String expectedOp, int line, int col) {
        return new MSLScannerAdapter() {
            public boolean isBinaryExpressionFor(org.antlr.v4.runtime.tree.ParseTree c, String op) {
                return op.equals(expectedOp);
            }
            public org.antlr.v4.runtime.tree.ParseTree getLeftOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getRightOperand(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public org.antlr.v4.runtime.tree.ParseTree getOperatorToken(org.antlr.v4.runtime.tree.ParseTree c) { return null; }
            public boolean isQuantity(org.antlr.v4.runtime.tree.ParseTree c) { return true; }
            public String getNodeText(org.antlr.v4.runtime.tree.ParseTree c) { return ""; }
            public int[] getTokenPosition(org.antlr.v4.runtime.tree.ParseTree c) { return new int[]{line, col}; }
            public org.antlr.v4.runtime.tree.ParseTree getRootContext() { return null; }
        };
    }

    // ── Step 10: MutationRuleFactory ──────────────────────────────────────────

    @Test
    void ruleFactoryCreatesSwapRule() {
        BinaryOpNode from = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        BinaryOpNode to   = new BinaryOpNode("-", new QuantityNode(), new QuantityNode());
        MutationRule rule = MutationRuleFactory.create("Replace", from, to, "label");
        assertInstanceOf(ReplacementRule.class, rule);
    }

    @Test
    void ruleFactoryLabelIsPreserved() {
        BinaryOpNode from = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        BinaryOpNode to   = new BinaryOpNode("-", new QuantityNode(), new QuantityNode());
        MutationRule rule = MutationRuleFactory.create("Replace", from, to, "my label");
        assertEquals("my label", rule.getLabel());
    }

    @Test
    void ruleFactoryThrowsOnUnknownKeyword() {
        BinaryOpNode from = new BinaryOpNode("+", new QuantityNode(), new QuantityNode());
        BinaryOpNode to   = new BinaryOpNode("-", new QuantityNode(), new QuantityNode());
        assertThrows(IllegalArgumentException.class,
            () -> MutationRuleFactory.create("Delete", from, to, "label"));
    }

    // ── Step 11: MutationRuleGenerator ───────────────────────────────────────

    @Test
    void ruleGeneratorParsesAllRulesFromRealSpec() throws Exception {
        // The real OperatorMutations.txt: 19 Replace blocks, 6 with And: Inverse -> 25 total
        java.io.File spec = new java.io.File(
            "OperatorMutations.txt");
        java.util.List<MutationRule> rules = MutationRuleGenerator.generateRules(spec);
        assertEquals(25, rules.size());
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

    @Test
    void ruleGeneratorParsesPitStyleScannerBackedRules(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "PIT Built-in Mutations\n\n" +
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

        java.util.List<MutationRule> rules =
            MutationRuleGenerator.generateRules(spec.toFile());

        assertEquals(5, rules.size());
        assertTrue(rules.stream().allMatch(rule -> rule instanceof ScannerBackedRule));
        assertEquals("PIT Built-in Mutations: return value \u2192 empty value", rules.get(0).getLabel());
        assertEquals("PIT Built-in Mutations: constructor call \u2192 null", rules.get(1).getLabel());
        assertEquals("PIT Built-in Mutations: non-void method call \u2192 default value", rules.get(2).getLabel());
        assertEquals("PIT Built-in Mutations: equality condition \u2192 true", rules.get(3).getLabel());
        assertEquals("PIT Built-in Mutations: order condition \u2192 false", rules.get(4).getLabel());
    }

    @Test
    void ruleGeneratorInverseInheritsExceptPatterns(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "Group\n\n" +
            "\tReplace: {quantity} <= {quantity}\n" +
            "\tWith: {quantity} < {quantity}\n" +
            "\tExcept: if ({quantity} <= 0) {quantity} = 0\n" +
            "\tAnd: Inverse\n\n" +
            "End\n");

        java.util.List<MutationRule> rules =
            MutationRuleGenerator.generateRules(spec.toFile());
        assertEquals(2, rules.size());

        // Forward rule: <= -> <, should carry the Except: pattern
        ReplacementRule forward = (ReplacementRule) rules.get(0);
        assertEquals(1, forward.getExceptPatterns().size(),
            "Forward rule should have 1 Except: pattern");

        // Inverse rule: < -> <=, must ALSO inherit the same Except: pattern
        ReplacementRule inverse = (ReplacementRule) rules.get(1);
        assertEquals(1, inverse.getExceptPatterns().size(),
            "Inverse rule should inherit Except: patterns from the forward rule");
    }

    // ── Step 13: JavaMutationScanner.createAdapter() ──────────────────────────

    @Test
    void javaAdapterIsBinaryExpressionForPlus(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { int x = 1 + 2; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        // Walk tree looking for a BinaryOpNode match for "+"
        boolean found = treeContainsMatch(adapter, root, "+");
        assertTrue(found, "Should find '1 + 2' as a + binary expression");
    }

    @Test
    void javaAdapterIsBinaryExpressionForMinus(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { int x = 1 + 2; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        boolean found = treeContainsMatch(adapter, root, "-");
        assertFalse(found, "Should NOT find '1 + 2' as a - expression");
    }

    @Test
    void javaAdapterIsQuantityTrueForIntLiteral(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { int x = 1 + 2; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        // Find any integer literal terminal and confirm isQuantity
        boolean found = anyLiteralIsQuantity(adapter, root);
        assertTrue(found, "Integer literals should be quantities");
    }

    @Test
    void javaAdapterFlagsStringLiteralsSoArithmeticSkipsThem(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        // String literals are operands (s == "" is mutated), but the adapter
        // flags them so arithmetic rules skip concatenation like "a" + b.
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { String s = \"hello\"; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        assertTrue(anyStringLiteralIsFlagged(adapter, root), "String literals should be flagged as strings");
    }

    @Test
    void javaAdapterHandlesShiftOperator(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { int x = 1 >> 2; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        assertTrue(treeContainsMatch(adapter, root, ">>"),
            "Should find '1 >> 2' as a >> binary expression");
    }

    @Test
    void javaAdapterHandlesRelationalOperator(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { boolean x = 1 > 2; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        assertTrue(treeContainsMatch(adapter, root, ">"),
            "Should find '1 > 2' as a > binary expression");
    }

    @Test
    void javaAdapterHandlesEqualityOperator(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { boolean x = 1 == 2; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        assertTrue(treeContainsMatch(adapter, root, "=="),
            "Should find '1 == 2' as a == binary expression");
    }

    @Test
    void javaAdapterHandlesLogicalAndOperator(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { boolean x = a && b; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        assertTrue(treeContainsMatch(adapter, root, "&&"),
            "Should find 'a && b' as a && binary expression");
    }

    // ── Tree-walking helpers for adapter tests ────────────────────────────────

    /** Returns true if any node in the tree is a binary expression for the given op. */
    private boolean treeContainsMatch(MSLScannerAdapter adapter,
                                       org.antlr.v4.runtime.tree.ParseTree node, String op) {
        if (adapter.isBinaryExpressionFor(node, op)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (treeContainsMatch(adapter, node.getChild(i), op)) return true;
        }
        return false;
    }

    /** Returns true if any TerminalNode in the tree is considered a quantity. */
    private boolean anyLiteralIsQuantity(MSLScannerAdapter adapter,
                                          org.antlr.v4.runtime.tree.ParseTree node) {
        if (node instanceof org.antlr.v4.runtime.tree.TerminalNode tn) {
            String txt = tn.getText();
            try { Integer.parseInt(txt); return adapter.isQuantity(node); } catch (NumberFormatException ignored) {}
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (anyLiteralIsQuantity(adapter, node.getChild(i))) return true;
        }
        return false;
    }

    /** Returns true if any string-literal TerminalNode is considered a quantity. */
    private boolean anyStringLiteralIsFlagged(MSLScannerAdapter adapter,
                                              org.antlr.v4.runtime.tree.ParseTree node) {
        if (node instanceof org.antlr.v4.runtime.tree.TerminalNode tn) {
            String txt = tn.getText();
            if (txt.startsWith("\"") && adapter.isStringLiteral(node)) return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (anyStringLiteralIsFlagged(adapter, node.getChild(i))) return true;
        }
        return false;
    }

    // ── Step 14: Java context-sensitive (Except:) mutations ───────────────────

    @Test
    void javaAdapterGetSourceLineReturnsLineOneContent(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src,
            "class A {\n" +
            "    int x = 1;\n" +
            "}\n");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        ParseTree root = adapter.getRootContext();
        ParseTree nodeOnLine1 = findFirstTerminalOnLine(root, 1);

        assertNotNull(nodeOnLine1, "Should find a terminal node on line 1");
        String line = adapter.getSourceLine(nodeOnLine1);
        assertFalse(line.isBlank(),
            "getSourceLine() should return non-blank content for line 1, not \"\"");
        assertTrue(line.contains("class A"),
            "Line 1 should contain 'class A', but was: \"" + line + "\"");
    }

    @Test
    void javaAdapterGetSourceLineReturnsDifferentContentForDifferentLines(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src,
            "class A {\n" +
            "    int x = 1;\n" +
            "}\n");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        ParseTree root = adapter.getRootContext();
        ParseTree nodeOnLine1 = findFirstTerminalOnLine(root, 1);
        ParseTree nodeOnLine2 = findFirstTerminalOnLine(root, 2);

        assertNotNull(nodeOnLine1, "Should find a node on line 1");
        assertNotNull(nodeOnLine2, "Should find a node on line 2");
        assertNotEquals(
            adapter.getSourceLine(nodeOnLine1),
            adapter.getSourceLine(nodeOnLine2),
            "getSourceLine() must return different content for different lines");
    }

    @Test
    void exceptSuppressesMutationOnLeClampLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("T.java");
        java.nio.file.Files.writeString(src,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n <= 0) n = 0;\n" +
            "        return n;\n" +
            "    }\n" +
            "}\n");
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "Test\n\n" +
            "\tReplace: {quantity} <= {quantity}\n" +
            "\tWith: {quantity} < {quantity}\n" +
            "\tExcept: if ({quantity} <= 0) {quantity} = 0\n\n" +
            "End\n");

        List<MutationPoint> points = scanWithSpec(src.toFile(), spec.toFile());
        assertEquals(0, points.size(),
            "Clamp line should be suppressed by Except: — expected 0, got " + points.size());
    }

    @Test
    void exceptSuppressesLeClampButNotRealLogicLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("T.java");
        java.nio.file.Files.writeString(src,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n <= 0) n = 0;\n" +   // guard — suppressed
            "        return n;\n" +
            "    }\n" +
            "    boolean isUnder(int x, int limit) {\n" +
            "        return x <= limit;\n" +    // real logic — fires
            "    }\n" +
            "}\n");
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "Test\n\n" +
            "\tReplace: {quantity} <= {quantity}\n" +
            "\tWith: {quantity} < {quantity}\n" +
            "\tExcept: if ({quantity} <= 0) {quantity} = 0\n\n" +
            "End\n");

        List<MutationPoint> points = scanWithSpec(src.toFile(), spec.toFile());
        assertEquals(1, points.size(),
            "Only real-logic <= should fire; clamp suppressed. Points: " + points.size());
        assertEquals("<=", points.get(0).getOperator());
    }

    @Test
    void exceptSuppressesMutationOnGeClampLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("T.java");
        java.nio.file.Files.writeString(src,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n >= 0) n = 0;\n" +
            "        return n;\n" +
            "    }\n" +
            "}\n");
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "Test\n\n" +
            "\tReplace: {quantity} >= {quantity}\n" +
            "\tWith: {quantity} > {quantity}\n" +
            "\tExcept: if ({quantity} >= 0) {quantity} = 0\n\n" +
            "End\n");

        List<MutationPoint> points = scanWithSpec(src.toFile(), spec.toFile());
        assertEquals(0, points.size(),
            ">= clamp line should be suppressed by Except: — expected 0, got " + points.size());
    }

    @Test
    void exceptSuppressesGeClampButNotRealLogicLineInJava(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("T.java");
        java.nio.file.Files.writeString(src,
            "public class T {\n" +
            "    int clamp(int n) {\n" +
            "        if (n >= 0) n = 0;\n" +   // guard — suppressed
            "        return n;\n" +
            "    }\n" +
            "    boolean isAbove(int x, int threshold) {\n" +
            "        return x >= threshold;\n" + // real logic — fires
            "    }\n" +
            "}\n");
        java.nio.file.Path spec = tmp.resolve("spec.txt");
        java.nio.file.Files.writeString(spec,
            "Test\n\n" +
            "\tReplace: {quantity} >= {quantity}\n" +
            "\tWith: {quantity} > {quantity}\n" +
            "\tExcept: if ({quantity} >= 0) {quantity} = 0\n\n" +
            "End\n");

        List<MutationPoint> points = scanWithSpec(src.toFile(), spec.toFile());
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

        File specFile = new File("specs/context-sensitive-mutations.spec");
        List<MutationPoint> points = scanWithSpec(src.toFile(), specFile);

        long lePoints = points.stream()
            .filter(p -> p.getOperator().equals("<="))
            .count();
        assertEquals(1, lePoints,
            "Only 1 <= mutation should fire (clamp suppressed by real spec). " +
            "Total points: " + points.size());
    }

    @Test
    void exceptPatternRobustlyMatchesSpacingAndNewlines(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("SpacingTest.java");
        java.nio.file.Files.writeString(src,
            "public class SpacingTest {\n" +
            "    void test(int a) {\n" +
            "        if (a > 0) {\n" +
            "            a = 0;\n" +
            "        }\n" +
            "    }\n" +
            "}\n");

        java.nio.file.Path spec = tmp.resolve("spacing.spec");
        java.nio.file.Files.writeString(spec,
            "Test Spacing\n\n" +
            "    Replace: {quantity} > {quantity}\n" +
            "    With: {quantity} >= {quantity}\n" +
            "    Except: if ({quantity} > {quantity}) { {quantity} = {quantity}\n\n" +
            "End\n");

        List<MutationPoint> points = scanWithSpec(src.toFile(), spec.toFile());
        assertEquals(0, points.size(),
            "The multi-line brace structure should be successfully matched and suppressed.");
    }

    // ── Helpers for Step 14 ───────────────────────────────────────────────────

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

    /** Depth-first search: returns the first TerminalNode on the given 1-based line. */
    private ParseTree findFirstTerminalOnLine(ParseTree node, int targetLine) {
        if (node instanceof TerminalNode tn) {
            org.antlr.v4.runtime.Token sym = tn.getSymbol();
            if (sym.getLine() == targetLine) return tn;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            ParseTree result = findFirstTerminalOnLine(node.getChild(i), targetLine);
            if (result != null) return result;
        }
        return null;
    }
}
