package mutationtester.msl;

import java.util.List;
import java.util.Set;

/**
 * A typed hole in an MSL tree: {type}, optionally named ({quantity a}) and
 * constrained by a pattern ({match: {quantity} <= 0}) or a word
 * ({integer: int64}). The adapter decides which CST nodes fit each type.
 */
public record MSLPlaceholder(String type, String name, MSLTree constraint, String condition) implements MSLTree {

    /** Any expression. */
    public static final String QUANTITY = "quantity";
    /** Any statement. */
    public static final String STATEMENT = "statement";
    /** The node being mutated, inside an Except: tree. */
    public static final String MATCH = "match";
    /** In an Except: tree, a node with a match of its pattern somewhere inside: {contains: {match}}. */
    public static final String CONTAINS = "contains";
    /** Zero or more call arguments, inside a call's parentheses. */
    public static final String ARGUMENTS = "arguments";

    /** Placeholders that match source code. */
    public static final Set<String> INPUT_TYPES = Set.of(
            QUANTITY, STATEMENT, MATCH, "invocation_statement", "return_value", "constructor_call",
            "non_void_method_call", "equality_condition", "order_condition",
            "number", "integer", "float", "exponential", "based_integer", "rational", "complex",
            "string", "boolean", "char", "bytes",
            "condition", "name", "block", "arguments", "collection", "lambda", "type", "field", CONTAINS);

    /** Placeholders whose text the adapter computes. */
    public static final Set<String> OUTPUT_TYPES = Set.of(
            "empty_value", "default_value", "empty_string", "empty_collection", "empty_block");

    /** Types that convert into each other ({integer} to {float}). */
    public static final List<Set<String>> FAMILIES = List.of(
            Set.of("number", "integer", "float", "exponential", "based_integer", "rational", "complex"),
            Set.of("char", "string", "bytes"));

    /** Words a placeholder may be constrained by, as in {integer: int64}. */
    public static final Set<String> CONDITIONS = Set.of(
            "literal", "type", "number", "integer", "float", "boolean", "char", "string", "bytes", "null",
            "collection", "int8", "int16", "int32", "int64", "uint8", "uint16", "uint32", "uint64", "bigint",
            "float32", "float64", "decimal");

    public MSLPlaceholder {
        if (constraint != null && condition != null) {
            throw new IllegalArgumentException("a placeholder takes a pattern or a word constraint, not both");
        }
    }

    public MSLPlaceholder(String type) {
        this(type, null, null, null);
    }

    public MSLPlaceholder(String type, MSLTree constraint) {
        this(type, null, constraint, null);
    }

    public boolean isMatch() {
        return MATCH.equals(type);
    }

    public boolean isNamed() {
        return name != null;
    }

    /** True for a type this language of placeholders knows. */
    public static boolean isKnownType(String type) {
        return INPUT_TYPES.contains(type) || OUTPUT_TYPES.contains(type);
    }

    /** The family a type converts within, or null. */
    public static Set<String> familyOf(String type) {
        for (Set<String> family : FAMILIES) {
            if (family.contains(type)) return family;
        }
        return null;
    }

    /** "return_value" reads as "return value" in report labels. */
    public String describe() {
        return type.replace('_', ' ') + (name == null ? "" : " " + name)
                + (condition == null ? "" : ": " + condition);
    }

    @Override
    public String toSpecText() {
        String head = name == null ? type : type + " " + name;
        if (constraint != null) return "{" + head + ": " + constraint.toSpecText() + "}";
        if (condition != null) return "{" + head + ": " + condition + "}";
        return "{" + head + "}";
    }
}
