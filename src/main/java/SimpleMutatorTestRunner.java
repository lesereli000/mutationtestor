/*
 Simple Test Runne should runs all mutation tests and generates reports.
    java -cp out/classes SimpleMutatorTestRunner
 */
public class SimpleMutatorTestRunner {

    private static MutationTestReport report;
    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    public static void main(String[] args) {
        printHeader();
        report = new MutationTestReport("Complete Mutator Test Suite");

        // Run all test suites
        runAORTests();
        runRORTests();
        runLogicalOperatorTests();

        // Print report
        System.out.println(report.generateFullReport());

        // Print quick summary
        System.out.println("\n=== QUICK SUMMARY ===");
        System.out.println("Total Tests: " + totalTests);
        System.out.println("Passed: " + passedTests);
        System.out.println("Failed: " + failedTests);
        System.out.println("Pass Rate: " + String.format("%.1f%%", (double)passedTests/totalTests*100));

        // Exit with appropriate code (0 = success, 1 = failures)
        System.exit(failedTests > 0 ? 1 : 0);
    }

    private static void printHeader() {
        System.out.println("\n");
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              MUTATION TESTING TOOL - COMPLETE TEST SUITE                      ║");
        System.out.println("║                                                                               ║");
        System.out.println("║  Languages: Java, Python, JavaScript, C#, Racket                              ║");
        System.out.println("║  Mutations: AOR (+/-), ROR (>/>=/), LOR (&&/|| and/or)                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println("\n");
    }