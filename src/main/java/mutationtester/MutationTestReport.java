package mutationtester;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;


public class MutationTestReport {
    private final List<MutationTestResult> results = new ArrayList<>();
    private final String reportTitle;
    private final LocalDateTime timestamp;

    public MutationTestReport(String reportTitle) {
        this.reportTitle = reportTitle;
        this.timestamp = LocalDateTime.now();
    }

    public void addResult(MutationTestResult result) {
        results.add(result);
    }

    public int getTotalTests() { return results.size(); }
    
    public int getPassedTests() { 
        return (int) results.stream().filter(MutationTestResult::isPassed).count(); 
    }
    
    public int getFailedTests() { 
        return getTotalTests() - getPassedTests(); 
    }

    public double getPassRate() {
        if (getTotalTests() == 0) return 0.0;
        return (double) getPassedTests() / getTotalTests() * 100;
    }


    // Generate a clear readable report

    public String generateFullReport() {
        StringBuilder sb = new StringBuilder();
 
        sb.append("\n");
        sb.append("╔═══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append(String.format("║  MUTATION TESTING REPORT: %-51s ║\n", reportTitle));
        sb.append(String.format("║  Generated: %-63s ║\n", 
            timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════╣\n");

        sb.append("║                              SUMMARY                                          ║\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Total Tests:  %-62d ║\n", getTotalTests()));
        sb.append(String.format("║  Passed:       %-62d ║\n", getPassedTests()));
        sb.append(String.format("║  Failed:       %-62d ║\n", getFailedTests()));
        sb.append(String.format("║  Pass Rate:    %-61.1f%% ║\n", getPassRate()));
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════╣\n");

        sb.append("║                         RESULTS BY MUTATION TYPE                              ║\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════╣\n");
        
        Map<String, List<MutationTestResult>> byType = results.stream()
            .collect(Collectors.groupingBy(MutationTestResult::getMutationType));
        
        for (Map.Entry<String, List<MutationTestResult>> entry : byType.entrySet()) {
            long passed = entry.getValue().stream().filter(MutationTestResult::isPassed).count();
            long total = entry.getValue().size();
            double rate = (double) passed / total * 100;
            sb.append(String.format("║  %-30s %3d/%3d passed (%5.1f%%)                   ║\n", 
                entry.getKey(), passed, total, rate));
        }
        
        // Results by Language
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║                           RESULTS BY LANGUAGE                                 ║\n");
        sb.append("╠═══════════════════════════════════════════════════════════════════════════════╣\n");
        
        Map<String, List<MutationTestResult>> byLang = results.stream()
            .collect(Collectors.groupingBy(MutationTestResult::getLanguage));
        
        for (Map.Entry<String, List<MutationTestResult>> entry : byLang.entrySet()) {
            long passed = entry.getValue().stream().filter(MutationTestResult::isPassed).count();
            long total = entry.getValue().size();
            double rate = (double) passed / total * 100;
            sb.append(String.format("║  %-30s %3d/%3d passed (%5.1f%%)                   ║\n", 
                entry.getKey(), passed, total, rate));
        }
        
        sb.append("╚═══════════════════════════════════════════════════════════════════════════════╝\n");
        
        // Detailed Results
        sb.append("\n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
        sb.append("                              DETAILED RESULTS                                  \n");
        sb.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
        
        for (MutationTestResult result : results) {
            sb.append(result.toReport());
            sb.append("\n");
        }
        
        // Failed Tests Summary (if any)
        if (getFailedTests() > 0) {
            sb.append("═══════════════════════════════════════════════════════════════════════════════\n");
            sb.append("                              FAILED TESTS SUMMARY                              \n");
            sb.append("═══════════════════════════════════════════════════════════════════════════════\n\n");
            
            for (MutationTestResult result : results) {
                if (!result.isPassed()) {
                    sb.append(String.format("  ✗ %s [%s/%s]\n", 
                        result.getTestName(), result.getMutationType(), result.getLanguage()));
                    sb.append(String.format("    Reason: %s\n", result.getFailureReason()));
                    sb.append(String.format("    Original: %s\n", result.getOriginalCode()));
                    sb.append(String.format("    Expected: %s\n", result.getExpectedMutation()));
                    sb.append(String.format("    Actual:   %s\n\n", result.getMutatedCode()));
                }
            }
        }
        
        return sb.toString();
    }
}
