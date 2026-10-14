package mutationtester.msl;

import mutationtester.model.MutationPoint;
import org.antlr.v4.runtime.tree.ParseTree;
import java.io.File;

/**
 * A single mutation rule parsed from a .spec file.
 * Implementations include SwapRule (operator replacement).
 */
public interface MutationRule {

    /** Returns true if this rule's pattern matches the given parse-tree node. */
    boolean matches(MSLScannerAdapter scanner, ParseTree ctx);

    /**
     * Produces the MutationPoint for this rule applied to the given node.
     * Only call this when matches() returns true. May return null when the
     * match cannot be turned into a valid mutant.
     */
    MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile);

    /** Human-readable label used in reports (e.g., "Math Operations: + → -"). */
    String getLabel();

    /**
     * Scans the entire AST via the adapter and returns all mutation points
     * found by this rule.
     */
    default java.util.List<MutationPoint> findMutationPoints(MSLScannerAdapter scanner, File sourceFile) {
        java.util.List<MutationPoint> points = new java.util.ArrayList<>();
        traverseAndFind(scanner, scanner.getRootContext(), points, sourceFile);
        return points;
    }

    private void traverseAndFind(MSLScannerAdapter scanner, ParseTree ctx, java.util.List<MutationPoint> points, File sourceFile) {
        if (ctx == null || scanner.skipsSubtree(ctx)) return;

        if (matches(scanner, ctx)) {
            MutationPoint point = apply(scanner, ctx, sourceFile);
            if (point != null) {
                points.add(point);
            }
        }
        
        for (int i = 0; i < ctx.getChildCount(); i++) {
            traverseAndFind(scanner, ctx.getChild(i), points, sourceFile);
        }
    }
}
