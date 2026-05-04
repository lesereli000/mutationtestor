package mutationtester.msl;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * A MutationRule that replaces one pattern with another.
 */
public class ReplacementRule implements MutationRule {

    private final MSLNode fromExpr;
    private final MSLNode toExpr;
    private final String label;

    public ReplacementRule(MSLNode fromExpr, MSLNode toExpr, String label) {
        this.fromExpr = fromExpr;
        this.toExpr = toExpr;
        this.label = label;
    }


    /**
     * The NEW matching method that evaluates the rule against an MSLNode.
     */
    public MutationPoint evaluate(MSLNode dataNode, File sourceFile) {
        Map<String, MSLNode> captures = new HashMap<>();
        if (fromExpr.matches(dataNode, captures)) {
             // If we captured something, we can construct the replacement
             // For now, assuming basic text replacement like old logic
             return new MutationPoint(
                     sourceFile, dataNode.getLine(), dataNode.getColumn(),
                     fromExpr.getText(), // old operator
                     toExpr.getText(),   // new operator
                     Mutator.MutatorType.CUSTOM,
                     label);
        }
        return null;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
