package mutationtester;

import java.io.File;

public class MutationPoint {
    private File sourceFile;
    private int lineNumber;
    private int columnNumber;
    private String operator;
    private String replacement;
    private Mutator.MutatorType type;
    private String customLabel;   // null for built-in mutations

    /** Constructor for built-in mutation types. */
    public MutationPoint(File sourceFile, int lineNumber, int columnNumber,
                        String operator, String replacement, Mutator.MutatorType type) {
        this(sourceFile, lineNumber, columnNumber, operator, replacement, type, null);
    }

    /** Constructor for spec-driven (CUSTOM) mutations. */
    public MutationPoint(File sourceFile, int lineNumber, int columnNumber,
                        String operator, String replacement,
                        Mutator.MutatorType type, String customLabel) {
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.operator = operator;
        this.replacement = replacement;
        this.type = type;
        this.customLabel = customLabel;
    }

    // Getters
    public File getSourceFile() { return sourceFile; }
    public int getLineNumber() { return lineNumber; }
    public int getColumnNumber() { return columnNumber; }
    public String getOperator() { return operator; }
    public String getReplacement() { return replacement; }
    public Mutator.MutatorType getType() { return type; }
    public Mutator.MutatorType getMutationType() { return type; }
    public String getCustomLabel() { return customLabel; }

    @Override
    public String toString() {
        String fileName = sourceFile != null ? sourceFile.getName() : "<unknown>";
        String base = String.format(
                "%s:%d:%d - %s -> %s (%s)",
                fileName, lineNumber, columnNumber, operator, replacement, type);
        return customLabel != null ? base + " [" + customLabel + "]" : base;
    }
}
