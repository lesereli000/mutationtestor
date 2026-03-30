import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * Here's a good summary comment I got for my code using claude.
 *
 * Scans Racket (.rkt) source files for mutation points using a custom
 * S-expression parser. No ANTLR grammar is needed because Racket/Scheme
 * code is inherently structured as S-expressions: the operator is always
 * the first element of a list, e.g. (+ a b), (> x 0), (and p q).
 *
 * Architecture mirrors JavaScriptMutationScanner and CSharpMutationScanner
 * so it slots into Main.findMutationsInFile() the same way.
 *
 * Pipeline:
 *   1. Tokenizer  — produces a flat list of Token objects with line/col info,
 *                   correctly skipping ; line comments, #| |# block comments,
 *                   and string literals.
 *   2. Tree builder — recursively nests tokens into SList / SAtom nodes.
 *   3. Visitor      — walks the tree, flags atoms in operator position that
 *                     match the requested MutatorType.
 */
public class RacketMutationScanner {

    // -------------------------------------------------------------------------
    // Public entry point
    // -------------------------------------------------------------------------

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String source = Files.readString(file.toPath());

        List<Token> tokens = tokenize(source);
        List<SNode> topLevel = parseTopLevel(tokens);

        List<MutationPoint> points = new ArrayList<>();
        for (SNode node : topLevel) {
            walkAndCollect(node, type, points, file);
        }
        return points;
    }

    // -------------------------------------------------------------------------
    // Phase 1: Tokenizer
    // -------------------------------------------------------------------------

    private enum TokenType { LPAREN, RPAREN, ATOM }

    private static class Token {
        final TokenType type;
        final String value;
        final int line;
        final int col;

        Token(TokenType type, String value, int line, int col) {
            this.type = type;
            this.value = value;
            this.line = line;
            this.col = col;
        }
    }

    /**
     * Walks the source character-by-character and produces a flat token list.
     *
     * Key rules:
     *  - ';' to end-of-line  → skipped (line comment)
     *  - #| ... |#           → skipped (block comment)
     *  - #lang ...           → skipped (language declaration line)
     *  - "..."               → swallowed whole, no token emitted (string literal)
     *  - ( [ and ) ]         → LPAREN / RPAREN tokens
     *  - everything else     → read until a delimiter, emit as ATOM
     *
     * Atoms are read greedily to the next delimiter, so identifiers like
     * list->vector, zero?, set!, and >= are kept intact as single tokens
     * and will never accidentally match a bare > or - operator.
     */
    private static List<Token> tokenize(String source) {
        List<Token> tokens = new ArrayList<>();
        int i = 0;
        int line = 1;
        int col = 0;

        while (i < source.length()) {
            char c = source.charAt(i);

            // -- newline tracking
            if (c == '\n') {
                line++;
                col = 0;
                i++;
                continue;
            }

            // -- whitespace
            if (Character.isWhitespace(c)) {
                col++;
                i++;
                continue;
            }

            // -- line comment:  ; ... \n
            if (c == ';') {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                }
                // newline handled on next iteration
                continue;
            }

            // -- block comment:  #| ... |#
            if (c == '#' && i + 1 < source.length() && source.charAt(i + 1) == '|') {
                i += 2;
                col += 2;
                while (i + 1 < source.length()) {
                    if (source.charAt(i) == '|' && source.charAt(i + 1) == '#') {
                        i += 2;
                        col += 2;
                        break;
                    }
                    if (source.charAt(i) == '\n') {
                        line++;
                        col = 0;
                    } else {
                        col++;
                    }
                    i++;
                }
                continue;
            }

            // -- #lang line  (e.g. #lang racket)
            if (c == '#' && source.startsWith("#lang", i)) {
                while (i < source.length() && source.charAt(i) != '\n') {
                    i++;
                    col++;
                }
                continue;
            }

            // -- string literal  "..."
            //    Swallowed whole; we emit no token so operators inside strings
            //    are never inspected.
            if (c == '"') {
                i++;
                col++;
                while (i < source.length()) {
                    char sc = source.charAt(i);
                    if (sc == '\\') {
                        // escape: skip two characters
                        i += 2;
                        col += 2;
                    } else if (sc == '\n') {
                        i++;
                        line++;
                        col = 0;
                    } else if (sc == '"') {
                        i++;
                        col++;
                        break;
                    } else {
                        i++;
                        col++;
                    }
                }
                continue;
            }

            // -- open paren / bracket
            if (c == '(' || c == '[') {
                tokens.add(new Token(TokenType.LPAREN, String.valueOf(c), line, col));
                i++;
                col++;
                continue;
            }

            // -- close paren / bracket
            if (c == ')' || c == ']') {
                tokens.add(new Token(TokenType.RPAREN, String.valueOf(c), line, col));
                i++;
                col++;
                continue;
            }

            // -- atom: read until the next delimiter
            //    Includes operators (+, -, >, >=, =, and, or, not) as well as
            //    identifiers, numbers, booleans (#t #f), and quoted symbols.
            int atomStartLine = line;
            int atomStartCol  = col;
            StringBuilder atom = new StringBuilder();
            while (i < source.length() && !isDelimiter(source.charAt(i))) {
                atom.append(source.charAt(i));
                i++;
                col++;
            }
            if (atom.length() > 0) {
                tokens.add(new Token(TokenType.ATOM, atom.toString(), atomStartLine, atomStartCol));
            }
        }

        return tokens;
    }

    /**
     * A delimiter is any character that terminates an atom.
     * Notably, '-', '>', '<', '=' are NOT delimiters so they stay
     * attached to adjacent characters in identifiers like list->vector.
     */
    private static boolean isDelimiter(char c) {
        return Character.isWhitespace(c)
                || c == '(' || c == ')'
                || c == '[' || c == ']'
                || c == '"'
                || c == ';';
    }

    // -------------------------------------------------------------------------
    // Phase 2: Tree builder
    // -------------------------------------------------------------------------

    /** Base class for S-expression nodes. */
    private abstract static class SNode {
        int line;
        int col;
    }

    /** A leaf node — a single symbol, number, keyword, or operator. */
    private static class SAtom extends SNode {
        String value;

        SAtom(String value, int line, int col) {
            this.value = value;
            this.line = line;
            this.col = col;
        }
    }

    /** A list node — a parenthesized form like (+ a b). */
    private static class SList extends SNode {
        List<SNode> children = new ArrayList<>();

        SList(int line, int col) {
            this.line = line;
            this.col = col;
        }
    }

    /**
     * Parses all top-level forms in the token stream.
     * A Racket file is a sequence of top-level S-expressions.
     */
    private static List<SNode> parseTopLevel(List<Token> tokens) {
        List<SNode> roots = new ArrayList<>();
        ListIterator<Token> it = tokens.listIterator();
        while (it.hasNext()) {
            roots.add(parseNode(it));
        }
        return roots;
    }

    /**
     * Parses a single S-expression node from the iterator.
     * If the next token is LPAREN, reads children recursively until RPAREN.
     * Otherwise emits an SAtom.
     */
    private static SNode parseNode(ListIterator<Token> it) {
        Token t = it.next();

        if (t.type == TokenType.LPAREN) {
            SList list = new SList(t.line, t.col);
            while (it.hasNext()) {
                Token peek = it.next();
                if (peek.type == TokenType.RPAREN) {
                    break;
                }
                it.previous(); // put back so parseNode() can consume it
                list.children.add(parseNode(it));
            }
            return list;
        }

        // RPAREN at top level or ATOM
        return new SAtom(t.value, t.line, t.col);
    }

    // -------------------------------------------------------------------------
    // Phase 3: Visitor — walk the tree and collect mutation points
    // -------------------------------------------------------------------------

    /**
     * Recursively walks every S-expression node.
     * For each SList whose first child is an SAtom, checks whether that atom
     * is a mutation target for the requested operator type.
     *
     * This mirrors the role of the ANTLR BaseVisitor subclasses used for
     * Java, JavaScript, and C#. The difference is that in Racket the
     * operator is structurally always in head position, so no grammar rules
     * are needed — we just look at children.get(0).
     */
    private static void walkAndCollect(SNode node, Mutator.MutatorType type,
                                       List<MutationPoint> points, File file) {
        if (!(node instanceof SList list)) {
            return; // atoms have no children to recurse into
        }

        // If the head of this list is an atom, check it as a potential operator
        if (!list.children.isEmpty() && list.children.get(0) instanceof SAtom head) {
            checkOperator(head, type, points, file);
        }

        // Recurse into every child regardless (handles nested expressions)
        for (SNode child : list.children) {
            walkAndCollect(child, type, points, file);
        }
    }

    /**
     * Given an atom in head (operator) position, adds a MutationPoint if the
     * atom matches the operator being mutated.
     *
     * Racket operator mapping:
     *   AOR_PLUS_TO_MINUS   :  +   →  -
     *   AOR_MINUS_TO_PLUS   :  -   →  +
     *   ROR_GT_TO_GE        :  >   →  >=
     *   ROR_GE_TO_GT        :  >=  →  >
     *   EQ_NEGATE           :  =   →  (not handled — see note below)
     *   LOGICAL_AND_TO_OR   :  and →  or
     *   LOGICAL_OR_TO_AND   :  or  →  and
     *
     * Note on EQ_NEGATE: In Racket, numeric equality is `=` and structural
     * equality is `equal?`. Negating `=` would require wrapping the whole
     * expression as `(not (= ...))`, which is a structural transformation
     * rather than a simple token swap. This is flagged as a known limitation
     * and left unimplemented so the rest of the pipeline is not disrupted.
     * A future implementation could emit a special MutationPoint subtype that
     * instructs the mutator to wrap the parent form instead of swapping a token.
     */
    private static void checkOperator(SAtom atom, Mutator.MutatorType type,
                                      List<MutationPoint> points, File file) {
        switch (type) {
            case AOR_PLUS_TO_MINUS -> {
                if (atom.value.equals("+")) {
                    points.add(new MutationPoint(file, atom.line, atom.col, "+", "-", type));
                }
            }
            case AOR_MINUS_TO_PLUS -> {
                if (atom.value.equals("-")) {
                    points.add(new MutationPoint(file, atom.line, atom.col, "-", "+", type));
                }
            }
            case ROR_GT_TO_GE -> {
                if (atom.value.equals(">")) {
                    points.add(new MutationPoint(file, atom.line, atom.col, ">", ">=", type));
                }
            }
            case ROR_GE_TO_GT -> {
                if (atom.value.equals(">=")) {
                    points.add(new MutationPoint(file, atom.line, atom.col, ">=", ">", type));
                }
            }
            case EQ_NEGATE -> {
                // Not implemented for Racket: negating `=` requires structural
                // wrapping (not (= ...)) rather than a simple token replacement.
                // Skipping silently so the pipeline continues for other operators.
            }
            case LOGICAL_AND_TO_OR -> {
                if (atom.value.equals("and")) {
                    points.add(new MutationPoint(file, atom.line, atom.col, "and", "or", type));
                }
            }
            case LOGICAL_OR_TO_AND -> {
                if (atom.value.equals("or")) {
                    points.add(new MutationPoint(file, atom.line, atom.col, "or", "and", type));
                }
            }
        }
    }
}
