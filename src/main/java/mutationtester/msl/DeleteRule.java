package mutationtester.msl;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Delete: target. Each matched statement is removed; the adapter supplies what
 * stands in its place (";", "pass", "(void)"). When the target is an
 * expression (Delete: {quantity}++), the expression statement it makes up is
 * removed, and a match inside a larger expression or a for loop's header is
 * left alone.
 */
public class DeleteRule extends TreeRule {

    public DeleteRule(MSLTree target, List<MSLTree> excepts, String label) {
        super(target, excepts, label);
    }

    @Override
    MSLScannerAdapter.OperatorEdit edit(MSLScannerAdapter scanner, ParseTree ctx, MSLBindings bindings) {
        ParseTree statement = isExpression(getTarget()) ? scanner.expressionStatement(ctx) : ctx;
        return statement == null ? null : scanner.delete(statement, bindings);
    }

    private static boolean isExpression(MSLTree tree) {
        return tree instanceof MSLNode node && switch (node.kind()) {
            case MSLNode.BINARY, MSLNode.UNARY, MSLNode.INCREMENT, MSLNode.PREDICATE,
                 MSLNode.CALL, MSLNode.LITERAL, MSLNode.IDENTIFIER -> true;
            default -> false;
        };
    }
}
