package mutationtester.scanner;

import mutationtester.JavaScriptParser;
import mutationtester.JavaScriptParserBaseVisitor;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * PIT-style Return Value Mutations (RVM) for JavaScript.
 *
 * return true        -> return false  (RVM_TRUE_TO_FALSE)
 * return false       -> return true   (RVM_FALSE_TO_TRUE)
 * return 0           -> return 1      (RVM_ZERO_TO_ONE)
 * return nonzero     -> return 0      (RVM_NONZERO_TO_ZERO)
 * return object/ref  -> return null   (RVM_OBJECT_TO_NULL)
 * return primitive   -> return 0      (RVM_PRIMITIVE_TO_ZERO)
 * return literal     -> empty literal (EMPTY_RETURNS)
 */
public class JavaScriptRVMVisitor extends JavaScriptParserBaseVisitor<Void> {

    private final List<MutationPoint> mutations = new ArrayList<>();
    private final File sourceFile;
    private final Mutator.MutatorType mutationType;
    private final String sourceCode;

    public JavaScriptRVMVisitor(File sourceFile, Mutator.MutatorType mutationType,
                                String sourceCode) {
        this.sourceFile = sourceFile;
        this.mutationType = mutationType;
        this.sourceCode = sourceCode;
    }

    public List<MutationPoint> getMutations() {
        return mutations;
    }

    @Override
    public Void visitReturnStatement(JavaScriptParser.ReturnStatementContext ctx) {
        if (ctx.expressionSequence() == null) return visitChildren(ctx);

        int startIndex = ctx.expressionSequence().getStart().getStartIndex();
        int stopIndex = ctx.expressionSequence().getStop().getStopIndex();
        String returnedValue = sourceCode.substring(startIndex, stopIndex + 1);
        String compactValue = ctx.expressionSequence().getText();
        String replacement = null;

        switch (mutationType) {
            case RVM_TRUE_TO_FALSE:
                if (compactValue.equals("true"))  replacement = "false";
                break;
            case RVM_FALSE_TO_TRUE:
                if (compactValue.equals("false")) replacement = "true";
                break;
            case RVM_ZERO_TO_ONE:
                if (compactValue.equals("0"))     replacement = "1";
                break;
            case RVM_NONZERO_TO_ZERO:
                if (compactValue.matches("^[1-9]\\d*$")) replacement = "0";
                break;
            case RVM_OBJECT_TO_NULL:
                if (isObjectReturn(compactValue)) replacement = "null";
                break;
            case RVM_PRIMITIVE_TO_ZERO:
                if (compactValue.matches("^-?\\d+\\.?\\d*$") && !compactValue.equals("0"))
                    replacement = "0";
                break;
            case EMPTY_RETURNS:
                replacement = emptyReplacement(compactValue);
                break;
            default:
                break;
        }

        if (replacement != null) {
            int line = ctx.expressionSequence().getStart().getLine();
            int col  = ctx.expressionSequence().getStart().getCharPositionInLine();
            mutations.add(new MutationPoint(sourceFile, line, col,
                    returnedValue, replacement, mutationType));
        }

        return visitChildren(ctx);
    }

    private String emptyReplacement(String value) {
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))
                || (value.startsWith("`") && value.endsWith("`"))) {
            return value.equals("\"\"") || value.equals("''") || value.equals("``")
                    ? null : "\"\"";
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            return value.equals("[]") ? null : "[]";
        }
        if (value.startsWith("{") && value.endsWith("}")) {
            return value.equals("{}") ? null : "{}";
        }
        return null;
    }

    /**
     * Returns true for object references/identifiers.
     * Excludes primitives: true, false, null, undefined, numbers, strings.
     */
    private boolean isObjectReturn(String value) {
        if (value == null || value.isBlank()) return false;
        if (value.equals("true") || value.equals("false")
                || value.equals("null") || value.equals("undefined")) return false;
        if (value.matches("^-?\\d+\\.?\\d*([eE][+-]?\\d+)?$")) return false;
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) return false;
        return value.matches("^[a-zA-Z_$][\\w$.]*$")
                || value.contains(".")
                || value.startsWith("new ")
                || value.startsWith("this");
    }
}
