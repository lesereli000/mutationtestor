package mutationtester;

public class MutantResult {
    private MutationPoint mutationPoint;
    private MutantStatus status;
    private String testOutput;
    private boolean testsPass;
    
    public enum MutantStatus {
        KILLED,         // Tests failed 
        SURVIVED,       // Tests passed 
        COMPILE_ERROR,
        TIMEOUT
    }
    
    public MutantResult(MutationPoint point, MutantStatus status, 
                       String testOutput, boolean testsPass) {
        this.mutationPoint = point;
        this.status = status;
        this.testOutput = testOutput;
        this.testsPass = testsPass;
    }

    public MutationPoint getMutationPoint() { return mutationPoint; }
    public MutantStatus getStatus() { return status; }
    public String getTestOutput() { return testOutput; }
    public boolean isTestsPass() { return testsPass; }
}
