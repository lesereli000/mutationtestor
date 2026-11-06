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
    /** {@code c ? a : b}: children are [condition, then, otherwise]. */
    public static final String CONDITIONAL = "conditional";
    /** {@code a += b}: value is the operator ("+="), children are [target, value]. */
    public static final String COMPOUND_ASSIGN = "compound_assign";
    /** {@code a[i]}: children are [collection, index]. */
    public static final String INDEX = "index";
    /** {@code obj.name}: children are [object, member name]. */
    public static final String MEMBER = "member";
    /** {@code obj.name(args)}: children are [object, method name, arguments...]. */
    public static final String MEMBER_CALL = "member_call";
    /** {@code a is null}: value is the plain-English phrase, children are [subject]. */
    public static final String PREDICATE = "predicate";
    /** {@code f(a, b)}: value is the name, children the arguments ({name}(...): no value, callee first). */
    public static final String CALL = "call";
    /** {@code 0}, {@code "s"}, {@code true}, {@code false}, {@code null}: value is the literal. */
    public static final String LITERAL = "literal";
    /** A name: value is the name. */
    public static final String IDENTIFIER = "identifier";
    /** {@code if (c) s [else s]}: children are [condition, then, else?]. */
    public static final String IF = "if";
    /** {@code while (c) s}: children are [condition, body]. */
    public static final String WHILE = "while";
    /** {@code for (i; c; u) s}: children are [init, condition, update, body]. */
    public static final String FOR = "for";
    /** {@code a = b}: children are [target, value]. */
    public static final String ASSIGN = "assign";
    /** {@code { s1 s2 }}: children are the leading statements of a block. */
    public static final String BLOCK = "block";
    /** {@code [a, b]}: children are the elements. */
    public static final String COLLECTION = "collection";
    /** {@code { k: v, ... }}: children are entries (or {arguments}). */
    public static final String OBJECT = "object";
    /** {@code k: v} inside an object: children are [key, value]. */
    public static final String ENTRY = "entry";
    /** {@code (a, b) => body}: children are the parameters, then the body. */
    public static final String LAMBDA = "lambda";
    /** {@code function f(a, b) body}: children are [name, parameters..., body]. */
    public static final String FUNCTION = "function";
    /** {@code parameter x} / {@code parameter x = v}: children are [name] or [name, default]. */
    public static final String PARAMETER = "parameter";
    /** {@code a is a T}: children are [value, type]. */
    public static final String TYPE_CHECK = "type_check";
    /** {@code (T) a}: children are [type, value]. */
    public static final String CAST = "cast";
    /** {@code a?.name}: children are [object, member name]. */
    public static final String SAFE_MEMBER = "safe_member";
    /** {@code field x} / {@code field x = v}: children are [name] or [name, value]. */
    public static final String FIELD = "field";
    /** {@code new T { a, b }}: children are [type, initializers...]. */
    public static final String INITIALIZED_NEW = "initialized_new";
    /** {@code await a}: children are [value]. */
    public static final String AWAIT = "await";
    /** {@code [e for x in xs if c]}: children are [element, name, iterable] and an optional [condition]. */
    public static final String COMPREHENSION = "comprehension";
    /** {@code when (c) s} / {@code unless (c) s}: children are [condition, body]. */
    public static final String WHEN = "when";
    public static final String UNLESS = "unless";
    /** {@code where c}: a query filter clause; children are [condition]. */
    public static final String WHERE = "where";
    /** {@code assert c}: children are [condition]. */
    public static final String ASSERT = "assert";
    /** {@code with (r) s}: a resource or lock block; children are [resource, body]. */
    public static final String WITH = "with";
    /** {@code yield a} / {@code yield}: children are [value] or none. */
    public static final String YIELD = "yield";
    /** {@code label: s}: children are [label, statement]. */
    public static final String LABELED = "labeled";
    /** {@code a in b}, {@code a not in b}: value is "in" or "not in", children are [item, container]. */
    public static final String MEMBERSHIP = "membership";
    /** {@code a[i:j]}: children are [collection, from, to], a missing bound being empty. */
    public static final String SLICE = "slice";
    /** {@code `...${a}...`}: children are the interpolated expressions. */
    public static final String INTERPOLATION = "interpolation";
    /** {@code (a, b)}: children are the elements (a tuple, or destructuring targets). */
    public static final String TUPLE = "tuple";
    /** {@code ...a}: children are [value]. */
    public static final String SPREAD = "spread";
    /** {@code case p => v}: a switch-expression or match arm; children are [pattern, value]. */
    public static final String ARM = "arm";
    /** {@code delete a}: children are [target]. */
    public static final String DELETE = "delete";
    /** {@code @name}: an annotation, attribute or decorator; children are [name]. */
    public static final String ANNOTATION = "annotation";
    /** {@code return a} / {@code return}: children are [value] or none. */
    public static final String RETURN = "return";
    /** {@code break} / {@code break label}: children are none, or [label]. */
    public static final String BREAK = "break";
    /** {@code continue} / {@code continue label}: children are none, or [label]. */
    public static final String CONTINUE = "continue";
    /** {@code var x = v}: children are [name, value]. */
    public static final String DECLARE = "declare";
    /** {@code for (x in xs) s}: children are [name, collection, body]. */
    public static final String FOR_EACH = "for_each";
    /** {@code do s while (c)}: children are [body, condition]. */
    public static final String DO_WHILE = "do_while";
    /** {@code case v: s}: children are [value, body]. */
    public static final String CASE = "case";
    /** {@code default: s}: children are [body]. */
    public static final String DEFAULT_CASE = "default_case";
    /** {@code throw a}: children are [value]. */
    public static final String THROW = "throw";
    /** {@code try s catch s finally s}: children are [body, handler, finally], a missing part being empty. */
    public static final String TRY = "try";
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
            case CALL -> value.isEmpty()
                    ? child(0).toSpecText() + "(" + joined(1) + ")"
                    : value + "(" + joined(0) + ")";
            case COLLECTION -> "[" + joined(0) + "]";
            case AWAIT -> "await " + child(0).toSpecText();
            case COMPREHENSION -> "[" + child(0).toSpecText() + " for " + child(1).toSpecText() + " in "
                    + child(2).toSpecText() + (children.size() > 3 ? " if " + child(3).toSpecText() : "") + "]";
            case WHEN, UNLESS -> kind + " (" + child(0).toSpecText() + ") " + child(1).toSpecText();
            case WHERE -> "where " + child(0).toSpecText();
            case ASSERT -> "assert " + child(0).toSpecText();
            case WITH -> "with (" + child(0).toSpecText() + ") " + child(1).toSpecText();
            case YIELD -> children.isEmpty() ? "yield" : "yield " + child(0).toSpecText();
            case LABELED -> child(0).toSpecText() + ": " + child(1).toSpecText();
            case MEMBERSHIP -> child(0).toSpecText() + " " + value + " " + child(1).toSpecText();
            case SLICE -> child(0).toSpecText() + "[" + part(1, "") + ":" + part(2, "") + "]";
            case INTERPOLATION -> "`" + String.join("", children.stream().map(c -> "${" + c.toSpecText() + "}").toList()) + "`";
            case TUPLE -> "(" + joined(0) + ")";
            case SPREAD -> "..." + child(0).toSpecText();
            case ARM -> "case " + child(0).toSpecText() + " => " + child(1).toSpecText();
            case DELETE -> "delete " + child(0).toSpecText();
            case ANNOTATION -> "@" + child(0).toSpecText();
            case OBJECT -> "{ " + joined(0) + " }";
            case ENTRY -> child(0).toSpecText() + ": " + child(1).toSpecText();
            case LAMBDA -> "(" + String.join(", ", children.subList(0, children.size() - 1).stream()
                    .map(MSLTree::toSpecText).toList()) + ") => " + child(children.size() - 1).toSpecText();
            case FUNCTION -> "function " + child(0).toSpecText() + "(" + String.join(", ",
                    children.subList(1, children.size() - 1).stream().map(MSLTree::toSpecText).toList()) + ") "
                    + child(children.size() - 1).toSpecText();
            case PARAMETER, FIELD -> kind + " " + child(0).toSpecText()
                    + (children.size() > 1 ? " = " + child(1).toSpecText() : "");
            case TYPE_CHECK -> child(0).toSpecText() + " is a " + child(1).toSpecText();
            case CAST -> "(" + child(0).toSpecText() + ") " + child(1).toSpecText();
            case SAFE_MEMBER -> child(0).toSpecText() + "?." + child(1).toSpecText();
            case INITIALIZED_NEW -> "new " + child(0).toSpecText() + " { " + joined(1) + " }";
            case CONDITIONAL -> child(0).toSpecText() + " ? " + child(1).toSpecText() + " : " + child(2).toSpecText();
            case COMPOUND_ASSIGN -> child(0).toSpecText() + " " + value + " " + child(1).toSpecText();
            case INDEX -> child(0).toSpecText() + "[" + child(1).toSpecText() + "]";
            case MEMBER -> child(0).toSpecText() + "." + child(1).toSpecText();
            case MEMBER_CALL -> child(0).toSpecText() + "." + child(1).toSpecText() + "(" + joined(2) + ")";
            case LITERAL, IDENTIFIER -> value;
            case IF -> "if (" + child(0).toSpecText() + ") " + child(1).toSpecText()
                    + (children.size() > 2 ? " else " + child(2).toSpecText() : "");
            case RETURN -> children.isEmpty() ? "return" : "return " + child(0).toSpecText();
            case BREAK, CONTINUE -> kind + (children.isEmpty() ? "" : " " + child(0).toSpecText());
            case DECLARE -> "var " + child(0).toSpecText() + " = " + child(1).toSpecText();
            case FOR_EACH -> "for (" + child(0).toSpecText() + " in " + child(1).toSpecText() + ") "
                    + child(2).toSpecText() + (children.size() > 3 ? " else " + child(3).toSpecText() : "");
            case DO_WHILE -> "do " + child(0).toSpecText() + " while (" + child(1).toSpecText() + ")";
            case CASE -> "case " + child(0).toSpecText() + ": " + child(1).toSpecText();
            case DEFAULT_CASE -> "default: " + child(0).toSpecText();
            case THROW -> "throw " + child(0).toSpecText();
            case TRY -> "try " + child(0).toSpecText() + part(1, " catch ") + part(2, " finally ");
            case WHILE -> "while (" + child(0).toSpecText() + ") " + child(1).toSpecText()
                    + (children.size() > 2 ? " else " + child(2).toSpecText() : "");
            case FOR -> "for (" + part(0) + "; " + part(1) + "; " + part(2) + ") " + child(3).toSpecText();
            case ASSIGN -> child(0).toSpecText() + " = " + child(1).toSpecText();
            case BLOCK -> "{ " + String.join("; ", children.stream().map(MSLTree::toSpecText).toList()) + " }";
            case EMPTY -> ";";
            default -> kind + "(" + String.join(", ", children.stream().map(MSLTree::toSpecText).toList()) + ")";
        };
    }

    /** The children from {@code from} on, comma-separated. */
    private String joined(int from) {
        return String.join(", ", children.subList(from, children.size()).stream().map(MSLTree::toSpecText).toList());
    }

    /** " keyword part" for a try's optional part, or "" when it is missing. */
    private String part(int i, String keyword) {
        return child(i) instanceof MSLNode n && n.kind.equals(EMPTY) ? "" : keyword + child(i).toSpecText();
    }

    /** A for loop's part, which may be missing. */
    private String part(int i) {
        return child(i) instanceof MSLNode n && n.kind.equals(EMPTY) ? "" : child(i).toSpecText();
    }
}
