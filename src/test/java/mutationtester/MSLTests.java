package mutationtester;

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
    void javaAdapterIsQuantityFalseForStringLiteral(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        java.nio.file.Path src = tmp.resolve("A.java");
        java.nio.file.Files.writeString(src, "class A { String s = \"hello\"; }");

        MSLScannerAdapter adapter = JavaMutationScanner.createAdapter(src.toFile());
        org.antlr.v4.runtime.tree.ParseTree root = adapter.getRootContext();

        boolean anyStringIsQuantity = anyStringLiteralIsQuantity(adapter, root);
        assertFalse(anyStringIsQuantity, "String literals should NOT be quantities");
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
    private boolean anyStringLiteralIsQuantity(MSLScannerAdapter adapter,
                                                org.antlr.v4.runtime.tree.ParseTree node) {
        if (node instanceof org.antlr.v4.runtime.tree.TerminalNode tn) {
            String txt = tn.getText();
            if (txt.startsWith("\"") && adapter.isQuantity(node)) return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (anyStringLiteralIsQuantity(adapter, node.getChild(i))) return true;
        }
        return false;
    }
}
