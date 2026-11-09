package mutationtester.msl;

/**
 * A node of an MSL pattern tree, parsed from a spec file. A tree is built from
 * only two kinds of node: {@link MSLNode} (a piece of syntax such as a binary
 * operator, a call or an if statement) and {@link MSLPlaceholder} (a typed hole
 * such as {quantity}). Language adapters decide which CST nodes a tree matches
 * and how a tree is written in their language.
 */
public sealed interface MSLTree permits MSLNode, MSLPlaceholder {

    /** The tree in canonical MSL syntax, as it could be written in a spec. */
    String toSpecText();
}
