package mutationtester.msl;

import mutationtester.*;
import org.antlr.v4.runtime.tree.ParseTree;
import java.io.File;

/**
 * A MutationRule that replaces one operator with another.
 * Corresponds to the "Replace: ... With: ..." directive in a .spec file.
 */
public class SwapRule implements MutationRule {

    private final BinaryOpNode fromExpr;
    private final BinaryOpNode toExpr;
    private final String label;

    public SwapRule(BinaryOpNode fromExpr, BinaryOpNode toExpr, String label) {
        this.fromExpr = fromExpr;
        this.toExpr   = toExpr;
        this.label    = label;
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        return fromExpr.matches(scanner, ctx);
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        ParseTree opToken = scanner.getOperatorToken(ctx);
        int[] pos = scanner.getTokenPosition(opToken);
        return new MutationPoint(
            sourceFile, pos[0], pos[1],
            fromExpr.getOperator(),
            toExpr.getOperator(),
            Mutator.MutatorType.CUSTOM,
            label);
    }

    @Override
    public String getLabel() { return label; }
}
