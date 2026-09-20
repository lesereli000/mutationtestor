package mutationtester.msl;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.CSharpMutationScanner;

import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * MSL rule for patterns that are easier to find with an existing language scanner
 * than with a single parse-tree node pattern.
 */
public class ScannerBackedRule implements MutationRule {

    private final Mutator.MutatorType mutatorType;
    private final String label;

    public ScannerBackedRule(Mutator.MutatorType mutatorType, String label) {
        this.mutatorType = mutatorType;
        this.label = label;
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        return false;
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        throw new UnsupportedOperationException("Scanner-backed rules scan whole files directly.");
    }

    @Override
    public List<MutationPoint> findMutationPoints(MSLScannerAdapter scanner, File sourceFile) {
        if (!sourceFile.getName().endsWith(".cs")) {
            return Collections.emptyList();
        }

        try {
            return CSharpMutationScanner.scanFile(sourceFile, mutatorType);
        } catch (IOException e) {
            System.err.println("Warning: Failed to apply MSL scanner-backed rule "
                    + label + " to " + sourceFile + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public String getLabel() {
        return label;
    }
}
