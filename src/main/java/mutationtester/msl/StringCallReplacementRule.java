package mutationtester.msl;

import java.io.File;

import org.antlr.v4.runtime.tree.ParseTree;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * Rewrites a function call such as string.IsNullOrEmpty(x) into a simpler check.
 * For IsNullOrEmpty it also matches each language's equivalent null-or-empty
 * check, and writes the replacement with the language's own null/empty tests.
 */
public class StringCallReplacementRule implements MutationRule {

    private static final String NULL_OR_EMPTY = "string.IsNullOrEmpty";

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
        String argument = getArgument(scanner, ctx);
        if (argument == null || argument.isBlank()) {
            return false;
        }
        ParseTree parent = ctx.getParent();
        return parent == null || getArgument(scanner, parent) == null;
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        int[] pos = scanner.getTokenPosition(ctx);
        String original = scanner.getNodeText(ctx);
        String argument = getArgument(scanner, ctx);
        String replacement;
        if ("!= null".equals(replacementSuffix)) {
            replacement = scanner.notNullCheck(argument);
        } else if ("!= \"\"".equals(replacementSuffix)) {
            replacement = scanner.notEmptyStringCheck(argument);
        } else if (replacementSuffix.isEmpty()) {
            // Empty suffix means pure removal: str(x) -> x
            replacement = argument;
        } else {
            replacement = "(" + argument + " " + replacementSuffix + ")";
        }
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

    private String getArgument(MSLScannerAdapter scanner, ParseTree ctx) {
        for (String alias : scanner.getFunctionAliases(functionName)) {
            String argument = scanner.getSingleArgumentText(ctx, alias);
            if (argument != null && !argument.isBlank()) {
                return argument;
            }
        }
        // Languages without an IsNullOrEmpty call spell the same check out,
        // e.g. s is None or s == "" in Python.
        if (NULL_OR_EMPTY.equals(functionName)) {
            return scanner.getNullOrEmptySubject(ctx);
        }
        return null;
    }
}
