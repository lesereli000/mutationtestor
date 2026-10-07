package mutationtester.msl;

import java.io.File;
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * A MutationRule that replaces one operator with another.
 * Corresponds to the "Replace: ... With: ..." directive in a .spec file.
 *
 * Optionally supports "Except:" clauses that suppress the mutation
 * when the source line matches a given context pattern.
 *
 * Example spec:
 *   Replace: {quantity} <= {quantity}
 *   With: {quantity} < {quantity}
 *   Except: if ({quantity} <= 0) {quantity} = 0
 */
public class ReplacementRule implements MutationRule {

    private final BinaryOpNode fromExpr;
    private final BinaryOpNode toExpr;
    private final String label;
    private final List<ExceptPattern> exceptPatterns;

    /** Constructor for rules without any Except: clauses. */
    public ReplacementRule(BinaryOpNode fromExpr, BinaryOpNode toExpr, String label) {
        this(fromExpr, toExpr, label, List.of());
    }

    /** Constructor for rules with one or more Except: clauses. */
    public ReplacementRule(BinaryOpNode fromExpr, BinaryOpNode toExpr,
                           String label, List<ExceptPattern> exceptPatterns) {
        this.fromExpr = fromExpr;
        this.toExpr = toExpr;
        this.label = label;
        this.exceptPatterns = exceptPatterns != null ? exceptPatterns : List.of();
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        // First check the operator pattern matches
        if (!fromExpr.matches(scanner, ctx)) {
            return false;
        }

        // If any Except: pattern matches the source line, suppress this mutation
        if (!exceptPatterns.isEmpty()) {
            String sourceLine = scanner.getSourceLine(ctx);
            for (ExceptPattern except : exceptPatterns) {
                if (except.matches(sourceLine, scanner)) {
                    return false; // suppressed — this context is excluded
                }
            }
        }

        return true;
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        MSLScannerAdapter.OperatorEdit edit = scanner.createOperatorEdit(
                ctx, fromExpr.getOperator(), toExpr.getOperator());
        // No mutant when the target can't be written in this language, or when
        // the edit would leave the source unchanged (an equivalent mutant).
        if (edit == null || edit.original().equals(edit.replacement())) {
            return null;
        }
        return new MutationPoint(
                sourceFile, edit.line(), edit.column(),
                edit.original(),
                edit.replacement(),
                Mutator.MutatorType.CUSTOM,
                label);
    }

    @Override
    public String getLabel() {
        return label;
    }

    public List<ExceptPattern> getExceptPatterns() {
        return exceptPatterns;
    }
}