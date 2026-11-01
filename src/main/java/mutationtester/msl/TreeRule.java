package mutationtester.msl;

import java.io.File;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * Shared behaviour of the three rule types: a target tree matched against the
 * CST, Except: trees that suppress a match, and turning an edit into a
 * MutationPoint.
 */
abstract class TreeRule implements MutationRule {

    private final MSLTree target;
    private final List<MSLTree> excepts;
    private final String label;

    TreeRule(MSLTree target, List<MSLTree> excepts, String label) {
        this.target = target;
        this.excepts = excepts == null ? List.of() : List.copyOf(excepts);
        this.label = label;
    }

    /** The tree that picks the CST nodes this rule mutates. */
    public MSLTree getTarget() {
        return target;
    }

    public List<MSLTree> getExcepts() {
        return excepts;
    }

    @Override
    public String getLabel() {
        return label;
    }

    /** The tree the rule writes (With: or Insert:), or null when it writes none. */
    MSLTree getWritten() {
        return null;
    }

    /**
     * The trees that must be supported for the rule to apply. Except: trees
     * are left out: a construct a language doesn't have simply never
     * suppresses anything there.
     */
    @Override
    public List<MSLTree> getTrees() {
        List<MSLTree> trees = new java.util.ArrayList<>();
        trees.add(target);
        if (getWritten() != null) trees.add(getWritten());
        return trees;
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        if (MSLMatcher.match(target, ctx, scanner) == null) return false;
        // A wrapper node spanning the same tokens matches too; mutate it once,
        // at the outermost node.
        ParseTree parent = ctx.getParent();
        if (parent != null && sameSpan(parent, ctx) && MSLMatcher.match(target, parent, scanner) != null) {
            return false;
        }
        for (MSLTree except : excepts) {
            if (MSLMatcher.excepted(except, ctx, scanner)) return false;
        }
        return true;
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        MSLBindings bindings = MSLMatcher.match(target, ctx, scanner);
        MSLScannerAdapter.OperatorEdit edit = bindings == null ? null : edit(scanner, ctx, bindings);
        // No mutant when the change can't be written in this language, or when
        // it would leave the source unchanged (an equivalent mutant).
        if (edit == null || edit.original().equals(edit.replacement())) {
            return null;
        }
        return new MutationPoint(sourceFile, edit.line(), edit.column(), edit.original(),
                edit.replacement(), Mutator.MutatorType.CUSTOM, label);
    }

    /** The source edit for a match, or null when it can't be expressed. */
    abstract MSLScannerAdapter.OperatorEdit edit(MSLScannerAdapter scanner, ParseTree ctx, MSLBindings bindings);

    private static boolean sameSpan(ParseTree a, ParseTree b) {
        return a instanceof ParserRuleContext pa && b instanceof ParserRuleContext pb
                && pa.getStart() == pb.getStart() && pa.getStop() == pb.getStop();
    }
}
