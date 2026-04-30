package mutationtester.model;

public class Mutation {
    private String operatorName;  // e.g., "AOR_PLUS_TO_MINUS"
    private int line;
    private int column;
    private String originalOperator;  // e.g., "+"
    private String mutatedOperator;   // e.g., "-"
    private String sourceLine;
    
    public Mutation(String operatorName, int line, int column, 
                   String originalOperator, String mutatedOperator, String sourceLine) {
        this.operatorName = operatorName;
        this.line = line;
        this.column = column;
        this.originalOperator = originalOperator;
        this.mutatedOperator = mutatedOperator;
        this.sourceLine = sourceLine;
    }
    
    // Getters
    public String getOperatorName() { return operatorName; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public String getOriginalOperator() { return originalOperator; }
    public String getMutatedOperator() { return mutatedOperator; }
    public String getSourceLine() { return sourceLine; }
    
    @Override
    public String toString() {
        return String.format("Line %d, Col %d: %s (%s → %s) in: %s",
            line, column, operatorName, originalOperator, mutatedOperator, sourceLine);
    }
}
