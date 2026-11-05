package mutationtester.model;

import mutationtester.safety.SafetyAssessment;

public class MutantResult {
    private MutationPoint mutationPoint;
    private MutantStatus status;
    private String testOutput;
    private boolean testsPass;
    private final SafetyAssessment safetyAssessment;
    
    public enum MutantStatus {
        KILLED,         // Tests failed 
        SURVIVED,       // Tests passed 
        NO_COVERAGE,    // Tests passed and the mutated line was not covered
        SAFETY_SKIPPED,
        SAFETY_ABORTED,
        COMPILE_ERROR,
        TIMEOUT
    }
    
    public MutantResult(MutationPoint point, MutantStatus status, 
                       String testOutput, boolean testsPass) {
        this(point, status, testOutput, testsPass, null);
    }

    public MutantResult(MutationPoint point, MutantStatus status,
                       String testOutput, boolean testsPass, SafetyAssessment safetyAssessment) {
        this.mutationPoint = point;
        this.status = status;
        this.testOutput = testOutput;
        this.testsPass = testsPass;
        this.safetyAssessment = safetyAssessment;
    }

    public MutationPoint getMutationPoint() { return mutationPoint; }
    public MutantStatus getStatus() { return status; }
    public String getTestOutput() { return testOutput; }
    public boolean isTestsPass() { return testsPass; }
    public SafetyAssessment getSafetyAssessment() { return safetyAssessment; }
}
