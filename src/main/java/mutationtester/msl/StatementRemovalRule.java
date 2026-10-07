package mutationtester.msl;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;

/**
 * Removes an invocation statement by replacing the full statement text with the
 * language's empty statement (";", "pass", "(void)").
 * Intended for language-specific statement mutators such as C# method-call removal.
 */
public class StatementRemovalRule implements MutationRule {

    private final String label;

    public StatementRemovalRule(String label) {
        this.label = label;
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        if (!scanner.isInvocationStatement(ctx)) {
            return false;
        }
        ParseTree parent = ctx.getParent();
        return parent == null || !scanner.isInvocationStatement(parent);
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        int[] pos = scanner.getTokenPosition(ctx);
        String statement = scanner.getNodeText(ctx);
        return new MutationPoint(
                sourceFile,
                pos[0],
                pos[1],
                statement,
                scanner.getEmptyStatement(ctx),
                Mutator.MutatorType.CUSTOM,
                label
        );
    }

    @Override
    public String getLabel() {
        return label;
    }
}
