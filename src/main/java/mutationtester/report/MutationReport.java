package mutationtester.report;

import mutationtester.equivalent.EquivalentMutantStore;
import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class MutationReport {
    private List<MutantResult> results;
    private int totalMutants;
    private int killed;
    private int survived;
    private int noCoverage;
    private int equivalent;
    private double mutationScore;
    private int notTested;
    private String partialReason;

    public MutationReport(List<MutantResult> results) {
        this(results, Set.of());
    }

    /**
     * Mutants matching an equivalent ID are left out of every count and the
     * score, the same way the HTML report hides them.
     */
    public MutationReport(List<MutantResult> results, Set<String> equivalentIds) {
        List<MutantResult> scored = new ArrayList<>();
        for (MutantResult result : results) {
            if (!equivalentIds.isEmpty() && EquivalentMutantStore.matchesHiddenMutant(equivalentIds, result)) {
                equivalent++;
            } else {
                scored.add(result);
            }
        }
        this.results = scored;
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

    /**
     * Marks the report as covering only part of the run (time limit or cancel).
     * The counts and score already cover just the tested mutants.
     */
    public void markPartial(int notTested, String reason) {
        this.notTested = notTested;
        this.partialReason = reason;
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("MUTATION TESTING REPORT");
        System.out.println("=".repeat(80));
        if (isPartial()) {
            System.out.println("PARTIAL: " + partialReason + ".");
            System.out.printf("Not tested:       %d (not in any count or the score)\n", notTested);
            System.out.println("-".repeat(80));
        }
        System.out.printf("Total Mutants:    %d\n", totalMutants);
        System.out.printf("Killed:           %d (%.1f%%)\n",
                killed, totalMutants > 0 ? (killed / (double) totalMutants) * 100 : 0.0);
        System.out.printf("Survived:         %d (%.1f%%)\n",
                survived, totalMutants > 0 ? (survived / (double) totalMutants) * 100 : 0.0);
        System.out.printf("No Coverage:      %d (%.1f%%)\n",
                noCoverage, totalMutants > 0 ? (noCoverage / (double) totalMutants) * 100 : 0.0);
        if (equivalent > 0) {
            System.out.printf("Equivalent:       %d (excluded from all counts and the score)\n", equivalent);
        }
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

    public int getEquivalent() {
        return equivalent;
    }

    public boolean isPartial() {
        return partialReason != null;
    }

    public int getNotTested() {
        return notTested;
    }

    /** e.g. "stopped after 140 of 212 mutants (time limit of 45 min reached)"; null for a complete run. */
    public String getPartialReason() {
        return partialReason;
    }

    /** Scored survivors, in run order (equivalent mutants are not included). */
    public List<MutantResult> getSurvivedResults() {
        return results.stream()
                .filter(r -> r.getStatus() == MutantResult.MutantStatus.SURVIVED)
                .toList();
    }
}
