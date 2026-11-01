package mutationtester.msl;

import java.util.List;

/**
 * A syntax node of an MSL tree. {@code kind} says what construct it is, and
 * is deliberately an open string so a spec can name constructs that only some
 * adapters support. {@code value} is the operator, phrase, function name or
 * literal text, or empty when the kind needs none. Children are ordered.
 *
 * The kinds the spec parser produces are the constants below.
 */
public record MSLNode(String kind, String value, List<MSLTree> children) implements MSLTree {

    /** {@code a + b}: value is the canonical operator, children are [left, right]. */
    public static final String BINARY = "binary";
    /** {@code !a}, {@code -a}: value is the operator, children are [operand]. */
    public static final String UNARY = "unary";
    /**
     * {@code a++}, {@code a--}: value is "++" or "--", children are [operand].
     * It means "add (or subtract) one to the variable"; {@code ++a} is the same
     * node, and each adapter matches its language's spellings of it.
     */
    public static final String INCREMENT = "increment";
    /** {@code a is null}: value is the plain-English phrase, children are [subject]. */
    public static final String PREDICATE = "predicate";
    /** {@code f(a, b)}: value is the function name, children are the arguments. */
    public static final String CALL = "call";
    /** {@code 0}, {@code "s"}, {@code true}, {@code false}, {@code null}: value is the literal. */
    public static final String LITERAL = "literal";
    /** A name: value is the name. */
    public static final String IDENTIFIER = "identifier";
    /** {@code if (c) s}: children are [condition, then-statement]. */
    public static final String IF = "if";
    /** {@code while (c) s}: children are [condition, body]. */
    public static final String WHILE = "while";
    /** {@code for (i; c; u) s}: children are [init, condition, update, body]. */
    public static final String FOR = "for";
    /** {@code a = b}: children are [target, value]. */
    public static final String ASSIGN = "assign";
    /** {@code { s1 s2 }}: children are the leading statements of a block. */
    public static final String BLOCK = "block";
    /** Nothing: a missing for-loop part, or the empty statement ";". */
    public static final String EMPTY = "empty";

    public MSLNode {
        value = value == null ? "" : value;
        children = List.copyOf(children);
    }

    public MSLNode(String kind, String value, MSLTree... children) {
        this(kind, value, List.of(children));
    }

    public MSLTree child(int i) {
        return children.get(i);
    }

    @Override
    public String toSpecText() {
        return switch (kind) {
            case BINARY -> child(0).toSpecText() + " " + value + " " + child(1).toSpecText();
            case UNARY -> value + child(0).toSpecText();
            case INCREMENT -> child(0).toSpecText() + value;
            case PREDICATE -> child(0).toSpecText() + " " + value;
            case CALL -> value + "(" + String.join(", ", children.stream().map(MSLTree::toSpecText).toList()) + ")";
            case LITERAL, IDENTIFIER -> value;
            case IF -> "if (" + child(0).toSpecText() + ") " + child(1).toSpecText();
            case WHILE -> "while (" + child(0).toSpecText() + ") " + child(1).toSpecText();
            case FOR -> "for (" + part(0) + "; " + part(1) + "; " + part(2) + ") " + child(3).toSpecText();
            case ASSIGN -> child(0).toSpecText() + " = " + child(1).toSpecText();
            case BLOCK -> "{ " + String.join("; ", children.stream().map(MSLTree::toSpecText).toList()) + " }";
            case EMPTY -> ";";
            default -> kind + "(" + String.join(", ", children.stream().map(MSLTree::toSpecText).toList()) + ")";
        };
    }

    /** A for loop's part, which may be missing. */
    private String part(int i) {
        return child(i) instanceof MSLNode n && n.kind.equals(EMPTY) ? "" : child(i).toSpecText();
    }
}
