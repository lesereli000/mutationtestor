package mutationtester.msl;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;


import java.io.File;

/**
 * Removes an invocation statement by replacing the full statement text with ";".
 * Intended for language-specific statement mutators such as C# method-call removal.
 */
public class StatementRemovalRule implements MutationRule {

    private final String label;

    public StatementRemovalRule(String label) {
        this.label = label;
    }

    @Override
    public MutationPoint evaluate(MSLNode dataNode, File sourceFile) {
        if ("InvocationStatement".equals(dataNode.getType())) {
            return new MutationPoint(
                    sourceFile,
                    dataNode.getLine(),
                    dataNode.getColumn(),
                    dataNode.getText(),
                    ";",
                    Mutator.MutatorType.CUSTOM,
                    label
            );
        }
        return null;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
