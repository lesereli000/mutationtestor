package mutationtester.msl;

import mutationtester.MutationPoint;
import mutationtester.Mutator;
import org.antlr.v4.runtime.tree.ParseTree;

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
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        String argument = scanner.getSingleArgumentText(ctx, functionName);
        if (argument == null || argument.isBlank()) {
            return false;
        }
        ParseTree parent = ctx.getParent();
        return parent == null || scanner.getSingleArgumentText(parent, functionName) == null;
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        int[] pos = scanner.getTokenPosition(ctx);
        String original = scanner.getNodeText(ctx);
        String argument = scanner.getSingleArgumentText(ctx, functionName);
        String replacement = "(" + argument + " " + replacementSuffix + ")";
        return new MutationPoint(
                sourceFile,
                pos[0],
                pos[1],
                original,
                replacement,
                Mutator.MutatorType.CUSTOM,
                label
        );
    }

    @Override
    public String getLabel() {
        return label;
    }
}
