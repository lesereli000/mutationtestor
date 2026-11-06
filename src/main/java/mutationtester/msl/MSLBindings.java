package mutationtester.msl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * What a pattern's placeholders matched, read back by position, by name, or by
 * position within a family (so With: {float} can convert a matched {integer}).
 */
public final class MSLBindings {

    /** One matched placeholder; {arguments} fills {@code nodes} instead of {@code node}. */
    public record Binding(String type, String name, String condition, ParseTree node, List<ParseTree> nodes) {}

    private final List<Binding> inOrder = new ArrayList<>();
    private final Map<String, List<Binding>> byType = new HashMap<>();
    private final Map<String, Binding> byName = new HashMap<>();

    void add(String type, ParseTree node) {
        add(type, null, null, node);
    }

    void add(String type, String name, String condition, ParseTree node) {
        record(new Binding(type, name, condition, node, null));
    }

    void addList(String type, String name, List<ParseTree> nodes) {
        record(new Binding(type, name, null, null, List.copyOf(nodes)));
    }

    private void record(Binding binding) {
        inOrder.add(binding);
        byType.computeIfAbsent(binding.type(), t -> new ArrayList<>()).add(binding);
        if (binding.name() != null) byName.put(binding.name(), binding);
    }

    /** The n-th node matched by a placeholder of this type, or null. */
    public ParseTree get(String type, int n) {
        Binding b = binding(type, n);
        return b == null ? null : b.node();
    }

    private Binding binding(String type, int n) {
        List<Binding> bindings = byType.get(type);
        return bindings != null && n < bindings.size() ? bindings.get(n) : null;
    }

    /** What the placeholder with this name matched, or null. */
    public Binding named(String name) {
        return byName.get(name);
    }

    /** Reads bindings in order: each call for a type returns that type's next node. */
    public Cursor cursor() {
        return new Cursor();
    }

    public final class Cursor {
        private final Map<String, Integer> next = new HashMap<>();
        private final Map<Set<String>, Integer> nextInFamily = new HashMap<>();

        /** The next node bound to this type, or null when the pattern bound no more. */
        public ParseTree next(String type) {
            Binding b = nextBinding(type);
            return b == null ? null : b.node();
        }

        /** The next binding of this type, which may be a list ({arguments}), or null. */
        public Binding nextBinding(String type) {
            // Named matches are read by name, so skip them here.
            int n = next.merge(type, 1, Integer::sum) - 1;
            List<Binding> bindings = byType.getOrDefault(type, List.of());
            int seen = 0;
            for (Binding b : bindings) {
                if (b.name() == null && seen++ == n) return b;
            }
            return null;
        }

        /** What the placeholder with this name matched, or null. */
        public Binding named(String name) {
            return MSLBindings.this.named(name);
        }

        /** The next binding of any type in the family, in match order, or null. */
        public Binding nextInFamily(Set<String> family) {
            int n = nextInFamily.merge(family, 1, Integer::sum) - 1;
            int seen = 0;
            for (Binding b : inOrder) {
                if (family.contains(b.type()) && seen++ == n) return b;
            }
            return null;
        }
    }
}
