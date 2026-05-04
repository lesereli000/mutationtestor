package mutationtester;

import java.util.Scanner;

/**
 * Interactive menu for selecting mutation testing strategy
 */
public class StrategySelector {
    
    public static MutationStrategy selectStrategy() {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║           MUTATION TESTING STRATEGY SELECTOR                       ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                    ║");
        System.out.println("║  [1] AUTO (Recommended)                                           ║");
        System.out.println("║      Smart selection: AST for semantic mutations,                 ║");
        System.out.println("║      Dictionary for simple operator swaps                          ║");
        System.out.println("║      [+] Best balance of speed and accuracy                       ║");
        System.out.println("║      [+] Language-aware optimization                              ║");
        System.out.println("║                                                                    ║");
        System.out.println("║  [2] DICTIONARY (Fastest)                                         ║");
        System.out.println("║      Regex-based pattern matching                                  ║");
        System.out.println("║      [+] 100x faster than AST                                     ║");
        System.out.println("║      [+] Works on any language                                    ║");
        System.out.println("║      [-] May mutate comments/strings                              ║");
        System.out.println("║      [-] No type awareness                                        ║");
        System.out.println("║                                                                    ║");
        System.out.println("║  [3] AST (Most Accurate)                                          ║");
        System.out.println("║      Parse tree analysis via ANTLR                                 ║");
        System.out.println("║      [+] Semantic understanding                                   ║");
        System.out.println("║      [+] Type-aware mutations                                     ║");
        System.out.println("║      [+] Never mutates comments/strings                           ║");
        System.out.println("║      [-] 100x slower than Dictionary                              ║");
        System.out.println("║      [-] Requires grammar per language                            ║");
        System.out.println("║                                                                    ║");
        System.out.println("║  [4] MSL (Specification-Driven)                                   ║");
        System.out.println("║      Declarative mutation rules from .spec files                   ║");
        System.out.println("║      [+] Easy to define custom mutations                          ║");
        System.out.println("║      [+] Language-agnostic rule format                            ║");
        System.out.println("║      [+] No code changes for new mutations                        ║");
        System.out.println("║      [-] Requires MSL adapter per language                        ║");
        System.out.println("║                                                                    ║");
        System.out.println("╠════════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Enter your choice (1-4):                                         ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.print(">>> ");
        
        Scanner scanner = new Scanner(System.in);
        int choice = scanner.nextInt();
        
        return switch (choice) {
            case 1 -> MutationStrategy.AUTO;
            case 2 -> MutationStrategy.DICTIONARY;
            case 3 -> MutationStrategy.AST;
            case 4 -> MutationStrategy.MSL;
            default -> {
                System.out.println("Invalid choice. Using AUTO strategy.");
                yield MutationStrategy.AUTO;
            }
        };
    }
    
    public static void printStrategyDetails(MutationStrategy strategy) {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("Selected Strategy: " + strategy);
        System.out.println("=".repeat(80));
        
        switch (strategy) {
            case AUTO -> {
                System.out.println("[+] Automatically selects best approach per mutation type");
                System.out.println("[+] AST for: Numeric literal transformations (NLT_*)");
                System.out.println("[+] Dictionary for: Operator replacements (AOR_*, ROR_*, LCR_*)");
                System.out.println("[+] Optimizes for both speed and accuracy");
            }
            case DICTIONARY -> {
                System.out.println("[+] Fast regex-based pattern matching");
                System.out.println("[+] Simple operator swaps: + to -, > to <, && to ||");
                System.out.println("[!] May generate false positives in comments/strings");
            }
            case AST -> {
                System.out.println("[+] Full parse tree analysis");
                System.out.println("[+] Context-aware mutations");
                System.out.println("[+] Distinguishes types: 1e10 (exponential) vs 42 (integer)");
                System.out.println("[!] Slower execution time");
            }
            case MSL -> {
                System.out.println("[+] Using specification file for mutation rules");
                System.out.println("[+] Declarative mutation definitions");
                System.out.println("[+] Easy to add new mutation types without coding");
            }
        }
        System.out.println("=".repeat(80) + "\n");
    }
}