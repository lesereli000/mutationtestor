package mutationtester.cli;

import mutationtester.core.Language;
import mutationtester.model.Mutator;
import mutationtester.report.MutationTestReport;
import mutationtester.report.MutationTestResult;

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

    //Run a single mutation test and record the result

    private static void testMutation(String testName, String mutationType, String language,
                                     String fileName, String original, String expected, boolean isRacket) {
        totalTests++;
        
        // Create language object from filename (this loads the right .dict file)
        Language lang = new Language(fileName);
        
        // Get the mutation type enum
        Mutator.MutatorType type = Mutator.MutatorType.valueOf(mutationType);
        
        // RUN THE ACTUAL MUTATION
        String actual = Mutator.mutateOutsideStringsAndComments(original, type, 100, lang, isRacket);
        
        // Check if it matches what we expected
        boolean passed = expected.equals(actual);
        
        if (passed) {
            passedTests++;
        } else {
            failedTests++;
        }
        
        // Record the result with ALL the details
        report.addResult(new MutationTestResult.Builder()
            .testName(testName)
            .mutationType(mutationType)
            .language(language)
            .originalCode(original)
            .mutatedCode(actual)
            .expectedMutation(expected)
            .passed(passed)
            .failureReason(passed ? "" : "Expected [" + expected + "] but got [" + actual + "]")
            .build());
    }

    private static void runAORTests() {
        System.out.println("Running AOR (Arithmetic Operator Replacement) Tests...\n");
        
        // from PLUS TO MINUS 
        
        // Java tests
        testMutation("Java: + should become -", "AOR_PLUS_TO_MINUS", "Java",
            "test.java", "return a + b;", "return a - b;", false);
        testMutation("Java: + in string preserved", "AOR_PLUS_TO_MINUS", "Java",
            "test.java", "\"a + b\"", "\"a + b\"", false);
        testMutation("Java: + in line comment preserved", "AOR_PLUS_TO_MINUS", "Java",
            "test.java", "// a + b", "// a + b", false);
        testMutation("Java: + in block comment preserved", "AOR_PLUS_TO_MINUS", "Java",
            "test.java", "/* a + b */", "/* a + b */", false);
        testMutation("Java: ++ not changed", "AOR_PLUS_TO_MINUS", "Java",
            "test.java", "i++;", "i++;", false);
        
        // Python tests
        testMutation("Python: + should become -", "AOR_PLUS_TO_MINUS", "Python",
            "test.py", "return a + b", "return a - b", false);
        testMutation("Python: + in comment preserved", "AOR_PLUS_TO_MINUS", "Python",
            "test.py", "# a + b", "# a + b", false);
        
        // JavaScript tests
        testMutation("JavaScript: + should become -", "AOR_PLUS_TO_MINUS", "JavaScript",
            "test.js", "return a + b;", "return a - b;", false);
        
        // C# tests
        testMutation("C#: + should become -", "AOR_PLUS_TO_MINUS", "C#",
            "test.cs", "return a + b;", "return a - b;", false);
        
        // Racket tests (note: isRacket = true)
        testMutation("Racket: (+ a b) to (- a b)", "AOR_PLUS_TO_MINUS", "Racket",
            "test.rkt", "(+ a b)", "(- a b)", true);
        testMutation("Racket: + in string preserved", "AOR_PLUS_TO_MINUS", "Racket",
            "test.rkt", "\"a + b\"", "\"a + b\"", true);
        testMutation("Racket: + in ; comment preserved", "AOR_PLUS_TO_MINUS", "Racket",
            "test.rkt", "; a + b", "; a + b", true);
        
        // from MINUS TO PLUS 
        
        testMutation("Java: - should become +", "AOR_MINUS_TO_PLUS", "Java",
            "test.java", "return a - b;", "return a + b;", false);
        testMutation("Java: -- not changed", "AOR_MINUS_TO_PLUS", "Java",
            "test.java", "i--;", "i--;", false);
        testMutation("Python: - should become +", "AOR_MINUS_TO_PLUS", "Python",
            "test.py", "return a - b", "return a + b", false);
        testMutation("Racket: (- a b) to (+ a b)", "AOR_MINUS_TO_PLUS", "Racket",
            "test.rkt", "(- a b)", "(+ a b)", true);
    }

    private static void runRORTests() {
        System.out.println("Running ROR (Relational Operator Replacement) Tests...\n");
        
        // from GT TO GE 
        
        // Java
        testMutation("Java: > should become >=", "ROR_GT_TO_GE", "Java",
            "test.java", "if(x > y) {}", "if(x >= y) {}", false);
        testMutation("Java: >> preserved (bit shift)", "ROR_GT_TO_GE", "Java",
            "test.java", "x >> y", "x >> y", false);
        testMutation("Java: > in string preserved", "ROR_GT_TO_GE", "Java",
            "test.java", "\"if(x > y) {}\"", "\"if(x > y) {}\"", false);
        testMutation("Java: > in comment preserved", "ROR_GT_TO_GE", "Java",
            "test.java", "//if(x > y) {}", "//if(x > y) {}", false);
        
        // Python
        testMutation("Python: > should become >=", "ROR_GT_TO_GE", "Python",
            "test.py", "if x > y:", "if x >= y:", false);
        testMutation("Python: >> preserved", "ROR_GT_TO_GE", "Python",
            "test.py", "x >> y", "x >> y", false);
        
        // JavaScript
        testMutation("JavaScript: > should become >=", "ROR_GT_TO_GE", "JavaScript",
            "test.js", "if(x > y) {}", "if(x >= y) {}", false);
        testMutation("JavaScript: >>> preserved", "ROR_GT_TO_GE", "JavaScript",
            "test.js", "x >>> y", "x >>> y", false);
        
        // C#
        testMutation("C#: > should become >=", "ROR_GT_TO_GE", "C#",
            "test.cs", "if(x > y) {}", "if(x >= y) {}", false);
        
        // Racket
        testMutation("Racket: (> x y) to (>= x y)", "ROR_GT_TO_GE", "Racket",
            "test.rkt", "(if (> x y) ()())", "(if (>= x y) ()())", true);
        testMutation("Racket: char>? preserved", "ROR_GT_TO_GE", "Racket",
            "test.rkt", "(char>? #\\> #\\>)", "(char>? #\\> #\\>)", true);
        testMutation("Racket: => preserved", "ROR_GT_TO_GE", "Racket",
            "test.rkt", "(cond ((assoc 'a alist) => cdr) (else #f))", "(cond ((assoc 'a alist) => cdr) (else #f))", true);
        
        // from GE TO GT
        
        testMutation("Java: >= should become >", "ROR_GE_TO_GT", "Java",
            "test.java", "if(x >= y) {}", "if(x > y) {}", false);
        testMutation("Java: >= in string preserved", "ROR_GE_TO_GT", "Java",
            "test.java", "\"if(x >= y) {}\"", "\"if(x >= y) {}\"", false);
        testMutation("Python: >= should become >", "ROR_GE_TO_GT", "Python",
            "test.py", "if x >= y:", "if x > y:", false);
        testMutation("JavaScript: >= should become >", "ROR_GE_TO_GT", "JavaScript",
            "test.js", "if(x >= y) {}", "if(x > y) {}", false);
        testMutation("C#: >= should become >", "ROR_GE_TO_GT", "C#",
            "test.cs", "if(x >= y) {}", "if(x > y) {}", false);
        testMutation("Racket: (>= x y) to (> x y)", "ROR_GE_TO_GT", "Racket",
            "test.rkt", "(if (>= x y) ()())", "(if (> x y) ()())", true);
    }

    private static void runLogicalOperatorTests() {
        System.out.println("Running LOR (Logical Operator Replacement) Tests...\n");
        
        // from AND TO OR 
        
        // Java (&&  to ||)
        testMutation("Java: && should become ||", "LOGICAL_AND_TO_OR", "Java",
            "test.java", "return a > 0 && b > 0;", "return a > 0 || b > 0;", false);
        testMutation("Java: && in string preserved", "LOGICAL_AND_TO_OR", "Java",
            "test.java", "\"a && b\"", "\"a && b\"", false);
        testMutation("Java: && in comment preserved", "LOGICAL_AND_TO_OR", "Java",
            "test.java", "// a && b", "// a && b", false);
        
        // Python (and to or)
        testMutation("Python: and should become or", "LOGICAL_AND_TO_OR", "Python",
            "test.py", "return a > 0 and b > 0", "return a > 0 or b > 0", false);
        testMutation("Python: 'and' in string preserved", "LOGICAL_AND_TO_OR", "Python",
            "test.py", "\"foo and bar\"", "\"foo and bar\"", false);
        testMutation("Python: 'and' in comment preserved", "LOGICAL_AND_TO_OR", "Python",
            "test.py", "# foo and bar", "# foo and bar", false);
        testMutation("Python: 'command' preserved (word boundary)", "LOGICAL_AND_TO_OR", "Python",
            "test.py", "command = 5", "command = 5", false);
        
        // JavaScript
        testMutation("JavaScript: && should become ||", "LOGICAL_AND_TO_OR", "JavaScript",
            "test.js", "return a > 0 && b > 0;", "return a > 0 || b > 0;", false);
        
        // C#
        testMutation("C#: && should become ||", "LOGICAL_AND_TO_OR", "C#",
            "test.cs", "return a > 0 && b > 0;", "return a > 0 || b > 0;", false);
        
        // Racket
        testMutation("Racket: (and ...) to (or ...)", "LOGICAL_AND_TO_OR", "Racket",
            "test.rkt", "(and (> a 0) (> b 0))", "(or (> a 0) (> b 0))", true);
        testMutation("Racket: 'and' in string preserved", "LOGICAL_AND_TO_OR", "Racket",
            "test.rkt", "\"foo and bar\"", "\"foo and bar\"", true);
        
        // from OR TO AND 
        
        // Java
        testMutation("Java: || should become &&", "LOGICAL_OR_TO_AND", "Java",
            "test.java", "return a > 0 || b > 0;", "return a > 0 && b > 0;", false);
        testMutation("Java: || in string preserved", "LOGICAL_OR_TO_AND", "Java",
            "test.java", "\"a || b\"", "\"a || b\"", false);
        
        // Python
        testMutation("Python: or should become and", "LOGICAL_OR_TO_AND", "Python",
            "test.py", "return a > 0 or b > 0", "return a > 0 and b > 0", false);
        testMutation("Python: 'or' in string preserved", "LOGICAL_OR_TO_AND", "Python",
            "test.py", "\"foo or bar\"", "\"foo or bar\"", false);
        testMutation("Python: 'error' preserved (word boundary)", "LOGICAL_OR_TO_AND", "Python",
            "test.py", "error = True", "error = True", false);
        
        // JavaScript
        testMutation("JavaScript: || should become &&", "LOGICAL_OR_TO_AND", "JavaScript",
            "test.js", "return a > 0 || b > 0;", "return a > 0 && b > 0;", false);
        
        // C#
        testMutation("C#: || should become &&", "LOGICAL_OR_TO_AND", "C#",
            "test.cs", "return a > 0 || b > 0;", "return a > 0 && b > 0;", false);
        
        // Racket
        testMutation("Racket: (or ...) to (and ...)", "LOGICAL_OR_TO_AND", "Racket",
            "test.rkt", "(or (> a 0) (> b 0))", "(and (> a 0) (> b 0))", true);
        testMutation("Racket: 'or' in string preserved", "LOGICAL_OR_TO_AND", "Racket",
            "test.rkt", "\"foo or bar\"", "\"foo or bar\"", true);
    }
}
