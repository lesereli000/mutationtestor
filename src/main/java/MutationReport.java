import java.util.List;
import java.util.stream.Collectors;

public class MutationReport {
    private List<MutantResult> results;
    private int totalMutants;
    private int killed;
    private int survived;
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
        survived = totalMutants - killed;
        
        if (totalMutants > 0) {
            mutationScore = (killed / (double) totalMutants) * 100.0;
        } else {
            mutationScore = 0.0;
        }
    }
    
    public void printReport() {
        // Print mutation score
        // List survived mutants
        // Summary statistics
    }
}