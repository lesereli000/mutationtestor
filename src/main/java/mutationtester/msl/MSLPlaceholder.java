package mutationtester.msl;

/**
 * A typed hole in an MSL tree, written {type} in a spec: {quantity},
 * {return_value}, {invocation_statement}, and so on. The adapter decides which
 * CST nodes fit each type.
 *
 * In a With: or Insert: tree a placeholder refers back to what the Replace: or
 * anchor tree matched: the n-th {quantity} means the n-th {quantity} matched.
 * Output types such as {empty_value} instead ask the adapter for a value.
 *
 * {match} is reserved for Except: trees and marks where the mutated node must
 * sit; {@code constraint} is the optional pattern of {match: pattern}.
 */
public record MSLPlaceholder(String type, MSLTree constraint) implements MSLTree {

    /** Any expression. */
    public static final String QUANTITY = "quantity";
    /** Any statement. */
    public static final String STATEMENT = "statement";
    /** The node being mutated, inside an Except: tree. */
    public static final String MATCH = "match";

    public MSLPlaceholder(String type) {
        this(type, null);
    }

    public boolean isMatch() {
        return MATCH.equals(type);
    }

    /** "return_value" reads as "return value" in report labels. */
    public String describe() {
        return type.replace('_', ' ');
    }

    @Override
    public String toSpecText() {
        return constraint == null ? "{" + type + "}" : "{" + type + ": " + constraint.toSpecText() + "}";
    }
}
