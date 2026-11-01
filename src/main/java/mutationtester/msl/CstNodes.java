package mutationtester.msl;

import java.util.ArrayList;
import java.util.List;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/** Small CST helpers shared by the language adapters. */
public final class CstNodes {

    private CstNodes() {
    }

    /**
     * A char stream indexed like Java strings (UTF-16 units), so token
     * offsets and columns match the source text even after an emoji or other
     * character outside the BMP. CharStreams.fromString counts code points.
     */
    @SuppressWarnings("deprecation")
    public static org.antlr.v4.runtime.CharStream utf16(String source) {
        return new org.antlr.v4.runtime.ANTLRInputStream(source);
    }

    /** The node's tokens, left to right. */
    public static List<Token> tokens(ParseTree node) {
        List<Token> out = new ArrayList<>();
        collect(node, out);
        return out;
    }

    private static void collect(ParseTree node, List<Token> out) {
        if (node instanceof TerminalNode tn) {
            if (tn.getSymbol().getType() != Token.EOF) out.add(tn.getSymbol());
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) collect(node.getChild(i), out);
    }

    /** True when any token's text contains one of the fragments. */
    public static boolean anyTokenContains(ParseTree node, String... fragments) {
        for (Token t : tokens(node)) {
            for (String f : fragments) {
                if (t.getText().contains(f)) return true;
            }
        }
        return false;
    }

    /** The first ancestor (excluding node) of the given type, or null. */
    public static <T extends ParseTree> T ancestor(ParseTree node, Class<T> type) {
        for (ParseTree p = node == null ? null : node.getParent(); p != null; p = p.getParent()) {
            if (type.isInstance(p)) return type.cast(p);
        }
        return null;
    }

    /** Descends through wrappers that have a single child. */
    public static ParseTree unwrap(ParseTree node) {
        ParseTree p = node;
        while (p != null && p.getChildCount() == 1 && !(p.getChild(0) instanceof TerminalNode)) p = p.getChild(0);
        return p;
    }

    /** The single token under a chain of single-child wrappers, or null. */
    public static TerminalNode singleToken(ParseTree node) {
        ParseTree p = node;
        while (p != null && p.getChildCount() == 1) p = p.getChild(0);
        return p instanceof TerminalNode tn ? tn : null;
    }

    /**
     * True when the node's tokens are names separated by '.': x, this.x,
     * a.b.c, with no calls, indexing or operators.
     */
    public static boolean isDottedName(ParseTree node) {
        List<Token> tokens = tokens(node);
        if (tokens.isEmpty() || tokens.size() % 2 == 0) return false;
        for (int i = 0; i < tokens.size(); i++) {
            String text = tokens.get(i).getText();
            if (i % 2 == 1 ? !text.equals(".") : !isName(text)) return false;
        }
        return true;
    }

    /** A letter, '_' or '$', then letters, digits, '_' or '$'. */
    public static boolean isName(String text) {
        if (text.isEmpty()) return false;
        char first = text.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != '$') return false;
        for (int i = 1; i < text.length(); i++) {
            char c = text.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') return false;
        }
        return true;
    }

    /** True when a and b cover the same tokens. */
    public static boolean sameSpan(ParseTree a, ParseTree b) {
        return a instanceof ParserRuleContext pa && b instanceof ParserRuleContext pb
                && pa.getStart() == pb.getStart() && pa.getStop() == pb.getStop();
    }

    /** The token that starts the node, or null. */
    public static Token start(ParseTree node) {
        if (node instanceof TerminalNode tn) return tn.getSymbol();
        if (node instanceof ParserRuleContext prc) return prc.getStart();
        return null;
    }

    /** The token that ends the node, or null. */
    public static Token stop(ParseTree node) {
        if (node instanceof TerminalNode tn) return tn.getSymbol();
        if (node instanceof ParserRuleContext prc) return prc.getStop();
        return null;
    }

    /** The node's text without whitespace or comments: its tokens joined. */
    public static String compactText(ParseTree node) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens(node)) sb.append(t.getText());
        return sb.toString();
    }

    /** "-12", "3.5": an optional '-' then digits with at most one '.'. */
    public static boolean isNumberText(String text) {
        int i = text.startsWith("-") ? 1 : 0;
        boolean digits = false;
        boolean dot = false;
        for (; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isDigit(c)) {
                digits = true;
            } else if (c == '.' && !dot && digits) {
                dot = true;
                digits = false;
            } else {
                return false;
            }
        }
        return digits;
    }

    /** True when text starts and ends with the same quote character. */
    public static boolean isQuoted(String text) {
        if (text.length() < 2) return false;
        char q = text.charAt(0);
        return (q == '"' || q == '\'') && text.charAt(text.length() - 1) == q;
    }
}
