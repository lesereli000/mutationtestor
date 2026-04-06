import java.io.File;

public class MutationPoint {
    private File sourceFile;
    private int lineNumber;
    private int columnNumber;
    private String operator;
    private String replacement;
    private Mutator.MutatorType type;
    
    public MutationPoint(File sourceFile, int lineNumber, int columnNumber,
                        String operator, String replacement, Mutator.MutatorType type) {
        this.sourceFile = sourceFile;
        this.lineNumber = lineNumber;
        this.columnNumber = columnNumber;
        this.operator = operator;
        this.replacement = replacement;
        this.type = type;
    }
    
    // Getters
    public File getSourceFile() { return sourceFile; }
    public int getLineNumber() { return lineNumber; }
    public int getColumnNumber() { return columnNumber; }
    public String getOperator() { return operator; }
    public String getReplacement() { return replacement; }
    public Mutator.MutatorType getType() { return type; }

    @Override
    public String toString() {
        String fileName = sourceFile != null ? sourceFile.getName() : "<unknown>";
        return String.format(
                "%s:%d:%d - %s -> %s (%s)",
                fileName,
                lineNumber,
                columnNumber,
                operator,
                replacement,
                type
        );
    }
}