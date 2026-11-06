package mutationtester.msl;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Replace: from / With: to. Every CST node the from tree matches is rewritten
 * as the to tree, whose placeholders take the text the from tree matched.
 *
 * Example spec:
 *   Replace: {quantity} &lt;= {quantity}
 *   With: {quantity} &lt; {quantity}
 *   Except: if ({match: {quantity} &lt;= 0}) {quantity} = 0
 */
public class ReplaceRule extends TreeRule {

    private final MSLTree replacement;

    public ReplaceRule(MSLTree from, MSLTree to, List<MSLTree> excepts, String label) {
        super(from, excepts, label);
        this.replacement = to;
    }

    public MSLTree getFrom() {
        return getTarget();
    }

    public MSLTree getTo() {
        return replacement;
    }

    @Override
    MSLTree getWritten() {
        return replacement;
    }

    @Override
    MSLScannerAdapter.OperatorEdit edit(MSLScannerAdapter scanner, ParseTree ctx, MSLBindings bindings) {
        // With: {quantity} keeps just an operand. Where the value is thrown
        // away (x++; becoming x;) that only deletes the statement, which a
        // Delete: rule says directly, and Java and C# reject it.
        if (replacement instanceof MSLPlaceholder p && !p.isMatch() && scanner.discardsValue(ctx)) {
            return null;
        }
        return scanner.replace(ctx, getTarget(), replacement, bindings);
    }
}
