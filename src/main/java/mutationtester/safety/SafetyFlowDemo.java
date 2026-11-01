package mutationtester.safety;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/** Interactive examples using the same assessor and gate as the real runner. */
public final class SafetyFlowDemo {
    public static void main(String[] args) {
        SafetyController controller = new SafetyController(new BufferedReader(new InputStreamReader(System.in)), System.out);
        System.out.println("Interactive safety examples. Type c, s, or a at each prompt.");
        System.out.println("These examples do not execute the displayed code.\n");
        if (!demo(controller, "Literal arithmetic", "1 + 2", "1 - 2")) return;
        if (!demo(controller, "Database", "db.execute('UPDATE t')", "db.execute('DELETE t')")) return;
        if (!demo(controller, "File operation", "open('old', 'w')", "open('new', 'w')")) return;
        if (!demo(controller, "Network operation", "fetch(oldUrl)", "fetch(newUrl)")) return;
        if (!demo(controller, "Loop termination", "while (i < 10) { i++; }", "while (i > 10) { i++; }")) return;
        if (!demo(controller, "Guard before a database operation", "if (allowed) {\n  db.execute('DELETE FROM users');\n}", "if (!allowed) {\n  db.execute('DELETE FROM users');\n}")) return;
        if (!demo(controller, "New destructive operation", "run('SELECT * FROM users')", "run('DROP TABLE users')")) return;
        if (!demo(controller, "Unknown context", "return a + b;", "return a - b;")) return;
        System.out.println("\nInteractive examples complete. Run the sample project to test real mutants.");
    }

    private static boolean demo(SafetyController controller, String title, String original, String mutated) {
        System.out.println("\n--- " + title + " ---");
        System.out.println("Original: " + original + "\nMutated:  " + mutated);
        MutationPoint point = new MutationPoint(new File("demo"), 1, 0, original, mutated, Mutator.MutatorType.AOR_PLUS_TO_MINUS);
        SafetyController.Outcome outcome = controller.decideWithAssessment(point, original, mutated);
        System.out.println("Classification: " + outcome.assessment().level());
        System.out.println("Decision: " + outcome.action());
        if (outcome.action() == SafetyAction.ABORT) {
            System.out.println("Demo stopped.");
            return false;
        }
        return true;
    }
}
