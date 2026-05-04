package mutationtester.msl;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;


import java.io.File;

/**
 * Rewrites string.IsNullOrEmpty(x) into a simpler C#-style string check.
 */
public class StringCallReplacementRule implements MutationRule {

    private final String functionName;
    private final String replacementSuffix;
    private final String label;

    public StringCallReplacementRule(String functionName, String replacementSuffix, String label) {
        this.functionName = functionName;
        this.replacementSuffix = replacementSuffix;
        this.label = label;
    }

    @Override
    public MutationPoint evaluate(MSLNode dataNode, File sourceFile) {
        if ("StringCall".equals(dataNode.getType()) && dataNode.getText().equals(functionName)) {
            // Assume the first child is the argument
            if (!dataNode.getChildren().isEmpty()) {
                MSLNode argNode = dataNode.getChildren().get(0);
                String argument = argNode.getText();
                String replacement = "(" + argument + " " + replacementSuffix + ")";
                return new MutationPoint(
                        sourceFile,
                        dataNode.getLine(),
                        dataNode.getColumn(),
                        dataNode.getText() + "(" + argument + ")", // Reconstruct original
                        replacement,
                        Mutator.MutatorType.CUSTOM,
                        label
                );
            }
        }
        return null;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
