package mutationtester.msl;

import java.io.File;
import java.util.List;

import org.antlr.v4.runtime.tree.TerminalNode;
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
        // Wrappers often match too; mutate only the innermost, keeping their ';'.
        for (ParseTree inner = onlyNodeChild(ctx); inner != null; inner = onlyNodeChild(inner)) {
            if (MSLMatcher.match(target, inner, scanner) != null) return false;
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
        if (edit == null || sameCode(edit.original(), edit.replacement())) {
            return null;
        }
        return new MutationPoint(sourceFile, edit.line(), edit.column(), edit.original(),
                edit.replacement(), Mutator.MutatorType.CUSTOM, label);
    }

    /** The source edit for a match, or null when it can't be expressed. */
    abstract MSLScannerAdapter.OperatorEdit edit(MSLScannerAdapter scanner, ParseTree ctx, MSLBindings bindings);

    /** Equal apart from spacing and one pair of outer parentheses: a - a and (a - a). */
    private static boolean sameCode(String original, String replacement) {
        return unwrapped(original).equals(unwrapped(replacement));
    }

    private static String unwrapped(String code) {
        // Spacing between tokens doesn't matter; spacing inside a string does (" " is not "").
        StringBuilder compact = new StringBuilder();
        char quote = 0;
        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            if (quote != 0) {
                compact.append(c);
                if (c == '\\' && i + 1 < code.length()) compact.append(code.charAt(++i));
                else if (c == quote) quote = 0;
            } else if (c == '"' || c == '\'' || c == '`') {
                quote = c;
                compact.append(c);
            } else if (!Character.isWhitespace(c)) {
                compact.append(c);
            }
        }
        String s = compact.toString();
        if (s.length() < 2 || s.charAt(0) != '(' || s.charAt(s.length() - 1) != ')') return s;
        // Only when the outer parentheses enclose everything: not (a)+(b).
        int depth = 0;
        for (int i = 0; i < s.length() - 1; i++) {
            if (s.charAt(i) == '(') depth++;
            else if (s.charAt(i) == ')' && --depth == 0) return s;
        }
        return s.substring(1, s.length() - 1);
    }

    /** The only non-token child (a terminator rule such as JavaScript's eos counts as a token), or null. */
    private static ParseTree onlyNodeChild(ParseTree ctx) {
        ParseTree only = null;
        for (int i = 0; i < ctx.getChildCount(); i++) {
            ParseTree child = ctx.getChild(i);
            if (child instanceof TerminalNode) continue;
            String text = child.getText();
            if (text.isEmpty() || text.equals(";") || text.equals("<EOF>")) continue;
            if (only != null) return null;
            only = child;
        }
        return only;
    }
}
