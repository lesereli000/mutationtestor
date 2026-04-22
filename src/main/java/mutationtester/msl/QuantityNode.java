package mutationtester.msl;

import mutationtester.*;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * An MSLExpression that matches any parse-tree node that the adapter
 * considers a numeric quantity (literal, identifier, or arithmetic sub-expr).
 */
public class QuantityNode implements MSLExpression {

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        return scanner.isQuantity(ctx);
    }

    @Override
    public String getReplacementText(MSLScannerAdapter scanner, ParseTree ctx) {
        return scanner.getNodeText(ctx);
    }
}
