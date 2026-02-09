import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

public class MockCommandLineMutationTester {

    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java MockCommandLineMutationTester <project directory> <config file>");
            return;
        }

        System.out.println("------------------------------------------\n");

        Random rand = new Random();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        Path configPath = Path.of(args[1]);
        if(configPath.equals(Path.of("proper.config"))){
            System.out.println("Found Config File: " + args[1]);
        } else {
            System.out.println("Config file " + args[1] + " not found.");
            return;
        }

        System.out.println("\n------------------------------------------\n");

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        Path projectPath = Path.of(args[0]);
        if(projectPath.equals(Path.of("./samples/MavenProject")) ||
        projectPath.equals(Path.of("./samples/PytestProject")) ||
        projectPath.equals(Path.of("./samples/RacketProject"))) {

            int numLangs = 1;
            String[] langsPresent = {"Java"};

            System.out.println("Found Project directory: " + args[0]);
            System.out.println(numLangs + " Languages detected: " + Arrays.toString(langsPresent));

        } else {
            System.out.println("Project directory " + args[0] + " not found.");
            return;
        }

        System.out.println("\n------------------------------------------\n");
        System.out.println("Discovering mutants...");

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        int mutantsMade = rand.nextInt(1500, 2000);
        System.out.println("Found " + mutantsMade + " mutants");
        System.out.println("\n------------------------------------------\n");
        System.out.println("Starting to run mutants...");

        int completion = 0;
        while(completion < 100){
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            int inc = rand.nextInt(5, 20);
            if(completion + inc > 100){
                break;
            }
            completion += inc;

            System.out.println("Running mutants: " + completion + "%");
        }
        System.out.println("Finished running tests");

        System.out.println("\n------------------------------------------\n");

        System.out.println("Generating Report ... \n");

        int buildFailures = rand.nextInt(500);
        System.out.printf("Discovered %d mutants.\n", mutantsMade);
        System.out.printf("Only %d mutants could be built and ran successfully.\n", mutantsMade - buildFailures);

        int survived = rand.nextInt(2, 5);
        int killed = mutantsMade - survived - buildFailures;
        int totalLines = rand.nextInt(3000, 7000);
        int mutatedLines = totalLines - rand.nextInt(300, 700);

        System.out.println();
        System.out.printf("Mutants Killed: %d\n", killed);
        System.out.printf("Surviving Mutants: %d\n", survived);

        System.out.printf("\nMutation Score: %.2f\n", ((float) killed / survived) * 100);
        System.out.printf("Mutation Coverage: %.2f\n", ((float) mutatedLines / totalLines) * 100);
        System.out.printf("Mutation Survival Rate: %.2f\n", ((float) survived / mutantsMade) * 100);
        System.out.printf("Mutation Density: %.2f\n", ((float) mutantsMade / totalLines));

        System.out.println();
        System.out.println("Mutants that survived: ");

        for(int i = 0; i < survived; i++){
            System.out.println("--------------------------\n");
            System.out.printf("Location: %s, line %d\n", "Main.java", rand.nextInt(i*100, i*100 + 100));
            System.out.printf("Original Line: %s\n", "int c = a + b;");
            System.out.printf("Mutated Line: %s\n", "int c = a - b;");
        }

        System.out.println("\n------------------------------------------\n");
    }
}
