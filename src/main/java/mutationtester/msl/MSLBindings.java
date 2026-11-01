package mutationtester.msl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The CST nodes a pattern's placeholders matched, in the order the placeholders
 * appear in the pattern. A With: or Insert: tree reads them back by type: its
 * n-th {quantity} is the n-th {quantity} matched.
 */
public final class MSLBindings {

    private final Map<String, List<ParseTree>> byType = new HashMap<>();

    void add(String type, ParseTree node) {
        byType.computeIfAbsent(type, t -> new ArrayList<>()).add(node);
    }

    /** The n-th node matched by a placeholder of this type, or null. */
    public ParseTree get(String type, int n) {
        List<ParseTree> nodes = byType.get(type);
        return nodes != null && n < nodes.size() ? nodes.get(n) : null;
    }

    /** Reads bindings in order: each call for a type returns that type's next node. */
    public Cursor cursor() {
        return new Cursor();
    }

    public final class Cursor {
        private final Map<String, Integer> next = new HashMap<>();

        /** The next node bound to this type, or null when the pattern bound no more. */
        public ParseTree next(String type) {
            int n = next.merge(type, 1, Integer::sum) - 1;
            return get(type, n);
        }
    }
}
