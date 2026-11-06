package mutationtester.msl;

import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Delete: target. A matched statement becomes the empty statement; an
 * expression target only deletes a whole expression statement.
 */
public class DeleteRule extends TreeRule {

    public DeleteRule(MSLTree target, List<MSLTree> excepts, String label) {
        super(target, excepts, label);
    }

    @Override
    MSLScannerAdapter.OperatorEdit edit(MSLScannerAdapter scanner, ParseTree ctx, MSLBindings bindings) {
        // List items leave nothing behind.
        if (getTarget() instanceof MSLNode node
                && (node.kind().equals(MSLNode.ARM) || node.kind().equals(MSLNode.ANNOTATION)
                    || node.kind().equals(MSLNode.WHERE))) {
            return scanner.deleteItem(ctx);
        }
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
