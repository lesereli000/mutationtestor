package mutationtester.msl;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Insert: tree, with Before: anchor or After: anchor. The tree is written next
 * to each CST node the anchor matches; its placeholders take the text the
 * anchor matched.
 *
 * Example spec:
 *   Insert: {quantity} = 0
 *   Before: return {quantity}
 */
public class InsertRule extends TreeRule {

    private final MSLTree inserted;
    private final boolean before;

    public InsertRule(MSLTree inserted, MSLTree anchor, boolean before, List<MSLTree> excepts, String label) {
        super(anchor, excepts, label);
        this.inserted = inserted;
        this.before = before;
    }

    public MSLTree getInserted() {
        return inserted;
    }

    public boolean isBefore() {
        return before;
    }

    @Override
    MSLTree getWritten() {
        return inserted;
    }

    @Override
    MSLScannerAdapter.OperatorEdit edit(MSLScannerAdapter scanner, ParseTree ctx, MSLBindings bindings) {
        return scanner.insert(ctx, inserted, before, bindings);
    }
}
