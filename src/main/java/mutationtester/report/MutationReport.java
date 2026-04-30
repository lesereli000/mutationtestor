package mutationtester.report;

import mutationtester.MutantResult;
import mutationtester.MutationPoint;

import java.util.List;
import java.util.stream.Collectors;

public class MutationReport {
    private List<MutantResult> results;
    private int totalMutants;
    private int killed;
    private int survived;
    private int noCoverage;
    private double mutationScore;

    public MutationReport(List<MutantResult> results) {
        this.results = results;
        calculateStatistics();
    }

    private void calculateStatistics() {
        totalMutants = results.size();
        killed = (int) results.stream()
                .filter(r -> r.getStatus() == MutantResult.MutantStatus.KILLED)
                .count();
        survived = (int) results.stream()
                .filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED)
                .count();
        noCoverage = (int) results.stream()
                .filter(r -> r.getStatus() == MutantResult.MutantStatus.NO_COVERAGE)
                .count();

        if (totalMutants > 0) {
            mutationScore = (killed / (double) totalMutants) * 100.0;
        } else {
            mutationScore = 0.0;
        }
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MUTATION TESTING REPORT");
        System.out.println("=".repeat(80));
        System.out.printf("Total Mutants:    %d\n", totalMutants);
        System.out.printf("Killed:           %d (%.1f%%)\n",
                killed, totalMutants > 0 ? (killed / (double) totalMutants) * 100 : 0.0);
        System.out.printf("Survived:         %d (%.1f%%)\n",
                survived, totalMutants > 0 ? (survived / (double) totalMutants) * 100 : 0.0);
        System.out.printf("No Coverage:      %d (%.1f%%)\n",
                noCoverage, totalMutants > 0 ? (noCoverage / (double) totalMutants) * 100 : 0.0);
        System.out.println("-".repeat(80));
        System.out.printf("MUTATION SCORE:   %.1f%%\n", mutationScore);
        System.out.println("=".repeat(80));

        // Show survived mutants especially for large numbers
        if (survived > 0) {
            System.out.println("\nSURVIVED MUTANTS (Weak Test Coverage):");
            System.out.println("-".repeat(80));

            List<MutantResult> survivedList = results.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED)
                    .collect(Collectors.toList());

            for (int i = 0; i < survivedList.size(); i++) {
                MutantResult r = survivedList.get(i);
                MutationPoint p = r.getMutationPoint();

                String label = p.getCustomLabel() != null ? " [" + p.getCustomLabel() + "]" : "";
                System.out.printf("[%d] %s:%d:%d - %s -> %s %s\n",
                        i + 1,
                        p.getSourceFile().getName(),
                        p.getLineNumber(),
                        p.getColumnNumber(),
                        p.getOperator(),
                        p.getReplacement(),
                        label);
            }

            System.out.println("-".repeat(80));
            System.out.println("These mutations were NOT caught by tests!");
            System.out.println("Consider adding tests to cover these cases.");
        }

        if (noCoverage > 0) {
            System.out.println("\nNO COVERAGE MUTANTS:");
            System.out.println("-".repeat(80));

            List<MutantResult> noCoverageList = results.stream()
                    .filter(r -> r.getStatus() == MutantResult.MutantStatus.NO_COVERAGE)
                    .collect(Collectors.toList());

            for (int i = 0; i < noCoverageList.size(); i++) {
                MutantResult r = noCoverageList.get(i);
                MutationPoint p = r.getMutationPoint();
                String label = p.getCustomLabel() != null ? " [" + p.getCustomLabel() + "]" : "";
                System.out.printf("[%d] %s:%d:%d - %s -> %s %s\n",
                        i + 1,
                        p.getSourceFile().getName(),
                        p.getLineNumber(),
                        p.getColumnNumber(),
                        p.getOperator(),
                        p.getReplacement(),
                        label);
            }

            System.out.println("-".repeat(80));
            System.out.println("These mutations are on lines not executed by the test suite.");
        }

        // Show killed mutants summary
        if (killed > 0) {
            System.out.println("\nKILLED MUTANTS:");
            System.out.println("-".repeat(80));
            System.out.printf("%d mutants were successfully detected by tests\n", killed);

            // Show details only if small number (to avoid overwhelming output)
            if (killed <= 10) {
                List<MutantResult> killedList = results.stream()
                        .filter(r -> r.getStatus() == MutantResult.MutantStatus.KILLED)
                        .collect(Collectors.toList());

                for (MutantResult result : killedList) {
                    MutationPoint point = result.getMutationPoint();
                    String label = point.getCustomLabel() != null ? " [" + point.getCustomLabel() + "]" : "";
                    System.out.printf("  %s:%d - %s -> %s%s\n",
                            point.getSourceFile().getName(),
                            point.getLineNumber(),
                            point.getOperator(),
                            point.getReplacement(),
                            label);
                }
            } else {
                System.out.println("(Too many to list individually - see above summary)");
            }
        }

        System.out.println("\n" + "=".repeat(80));
    }

    // Getters
    public double getMutationScore() {
        return mutationScore;
    }

    public int getTotalMutants() {
        return totalMutants;
    }

    public int getKilled() {
        return killed;
    }

    public int getSurvived() {
        return survived;
    }

    public int getNoCoverage() {
        return noCoverage;
    }
}
