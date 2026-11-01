package mutationtester.msl;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Matches an MSL tree against the CST. The walk over the pattern, and the
 * recording of what each placeholder matched, is shared by every language;
 * the adapter only answers questions about one CST node at a time.
 */
public final class MSLMatcher {

    private MSLMatcher() {
    }

    /** What the pattern's placeholders matched, or null when ctx doesn't match. */
    public static MSLBindings match(MSLTree pattern, ParseTree ctx, MSLScannerAdapter adapter) {
        MSLBindings bindings = new MSLBindings();
        return matches(pattern, ctx, adapter, bindings, null) ? bindings : null;
    }

    /**
     * True when an Except: tree matches some ancestor of {@code mutated} (or
     * the node itself) with {@code mutated} in its {match} position.
     */
    public static boolean excepted(MSLTree exceptTree, ParseTree mutated, MSLScannerAdapter adapter) {
        for (ParseTree p = mutated; p != null; p = p.getParent()) {
            if (matches(exceptTree, p, adapter, new MSLBindings(), mutated)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(MSLTree pattern, ParseTree ctx, MSLScannerAdapter adapter,
                                   MSLBindings bindings, ParseTree mutated) {
        if (pattern instanceof MSLPlaceholder placeholder) {
            if (ctx == null) return false;
            if (placeholder.isMatch()) {
                return mutated != null && adapter.isSameNode(ctx, mutated)
                        && (placeholder.constraint() == null
                            || matches(placeholder.constraint(), mutated, adapter, bindings, null));
            }
            if (!adapter.matchesPlaceholder(placeholder.type(), ctx)) return false;
            bindings.add(placeholder.type(), ctx);
            return true;
        }
        MSLNode node = (MSLNode) pattern;
        if (ctx == null) {
            // A missing part, such as a for loop without an update.
            return node.kind().equals(MSLNode.EMPTY);
        }
        List<ParseTree> children = adapter.matchNode(node, ctx);
        if (children == null || children.size() != node.children().size()) return false;
        for (int i = 0; i < children.size(); i++) {
            if (!matches(node.child(i), children.get(i), adapter, bindings, mutated)) return false;
        }
        return true;
    }
}
