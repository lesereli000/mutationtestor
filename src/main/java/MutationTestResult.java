
public class MutationTestResult {
    private final String testName;
    private final String mutationType;
    private final String language;
    private final String originalCode;
    private final String mutatedCode;
    private final String expectedMutation;
    private final boolean passed;
    private final String failureReason;

    public MutationTestResult(String testName, String mutationType, String language,
                              String originalCode, String mutatedCode, 
                              String expectedMutation, boolean passed, String failureReason) {
        this.testName = testName;
        this.mutationType = mutationType;
        this.language = language;
        this.originalCode = originalCode;
        this.mutatedCode = mutatedCode;
        this.expectedMutation = expectedMutation;
        this.passed = passed;
        this.failureReason = failureReason;
    }

    public String getTestName() { return testName; }
    public String getMutationType() { return mutationType; }
    public String getLanguage() { return language; }
    public String getOriginalCode() { return originalCode; }
    public String getMutatedCode() { return mutatedCode; }
    public String getExpectedMutation() { return expectedMutation; }
    public boolean isPassed() { return passed; }
    public String getFailureReason() { return failureReason; }

    /**
     * Generate a readable report for this single test result
     */
    public String toReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("┌─────────────────────────────────────────────────────────────────\n");
        sb.append(String.format("│ Test: %s\n", testName));
        sb.append(String.format("│ Mutation Type: %s | Language: %s\n", mutationType, language));
        sb.append("├─────────────────────────────────────────────────────────────────\n");
        sb.append(String.format("│ ORIGINAL:  %s\n", originalCode));
        sb.append(String.format("│ EXPECTED:  %s\n", expectedMutation));
        sb.append(String.format("│ ACTUAL:    %s\n", mutatedCode));
        sb.append("├─────────────────────────────────────────────────────────────────\n");
        if (passed) {
            sb.append("│ PASSED - Mutation correctly applied\n");
        } else {
            sb.append(String.format("│ FAILED - %s\n", failureReason));
        }
        sb.append("└─────────────────────────────────────────────────────────────────\n");
        return sb.toString();
    }

    public static class Builder {
        private String testName = "Unnamed Test";
        private String mutationType = "Unknown";
        private String language = "Unknown";
        private String originalCode = "";
        private String mutatedCode = "";
        private String expectedMutation = "";
        private boolean passed = false;
        private String failureReason = "";

        public Builder testName(String testName) { this.testName = testName; return this; }
        public Builder mutationType(String mutationType) { this.mutationType = mutationType; return this; }
        public Builder language(String language) { this.language = language; return this; }
        public Builder originalCode(String originalCode) { this.originalCode = originalCode; return this; }
        public Builder mutatedCode(String mutatedCode) { this.mutatedCode = mutatedCode; return this; }
        public Builder expectedMutation(String expectedMutation) { this.expectedMutation = expectedMutation; return this; }
        public Builder passed(boolean passed) { this.passed = passed; return this; }
        public Builder failureReason(String failureReason) { this.failureReason = failureReason; return this; }

        public MutationTestResult build() {
            return new MutationTestResult(testName, mutationType, language, 
                originalCode, mutatedCode, expectedMutation, passed, failureReason);
        }
    }
}

