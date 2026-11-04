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
            if (placeholder.type().equals(MSLPlaceholder.CONTAINS)) {
                return placeholder.constraint() != null && containsMatch(placeholder.constraint(), ctx, adapter, mutated);
            }
            if (!adapter.matchesPlaceholder(placeholder.type(), ctx)) return false;
            if (placeholder.constraint() != null
                    && !matches(placeholder.constraint(), ctx, adapter, bindings, mutated)) {
                return false;
            }
            if (placeholder.condition() != null
                    && !adapter.satisfies(placeholder.condition(), placeholder.type(), ctx)) {
                return false;
            }
            if (placeholder.isNamed()) {
                // The same name twice must match the same text: {quantity x} - {quantity x}.
                MSLBindings.Binding earlier = bindings.named(placeholder.name());
                if (earlier != null) {
                    return adapter.getNodeText(earlier.node()).strip().equals(adapter.getNodeText(ctx).strip());
                }
            }
            bindings.add(placeholder.type(), placeholder.name(), placeholder.condition(), ctx);
            return true;
        }
        MSLNode node = (MSLNode) pattern;
        if (ctx == null) {
            // A missing part, such as a for loop without an update.
            return node.kind().equals(MSLNode.EMPTY);
        }
        List<ParseTree> children = mutated != null ? adapter.matchNodeInExcept(node, ctx) : adapter.matchNode(node, ctx);
        if (children == null) return false;
        int variadic = variadicIndex(node);
        if (variadic < 0) {
            if (children.size() != node.children().size()) return false;
            for (int i = 0; i < children.size(); i++) {
                if (!matches(node.child(i), children.get(i), adapter, bindings, mutated)) return false;
            }
            return true;
        }
        // {arguments} takes whatever the fixed children before and after it leave.
        int tail = node.children().size() - variadic - 1;
        if (children.size() < variadic + tail) return false;
        for (int i = 0; i < variadic; i++) {
            if (!matches(node.child(i), children.get(i), adapter, bindings, mutated)) return false;
        }
        int restEnd = children.size() - tail;
        MSLPlaceholder arguments = (MSLPlaceholder) node.child(variadic);
        bindings.addList(arguments.type(), arguments.name(), children.subList(variadic, restEnd));
        for (int j = 0; j < tail; j++) {
            if (!matches(node.child(variadic + 1 + j), children.get(restEnd + j), adapter, bindings, mutated)) {
                return false;
            }
        }
        return true;
    }

    /** True when the pattern matches ctx or a node anywhere inside it. */
    private static boolean containsMatch(MSLTree pattern, ParseTree ctx, MSLScannerAdapter adapter, ParseTree mutated) {
        java.util.ArrayDeque<ParseTree> todo = new java.util.ArrayDeque<>(List.of(ctx));
        while (!todo.isEmpty()) {
            ParseTree node = todo.pop();
            if (matches(pattern, node, adapter, new MSLBindings(), mutated)) return true;
            for (int i = 0; i < node.getChildCount(); i++) todo.push(node.getChild(i));
        }
        return false;
    }

    /** The position of the node's {arguments} child, or -1. */
    private static int variadicIndex(MSLNode node) {
        for (int i = 0; i < node.children().size(); i++) {
            if (node.child(i) instanceof MSLPlaceholder p && p.type().equals(MSLPlaceholder.ARGUMENTS)) return i;
        }
        return -1;
    }
}
