package mutationtester.safety;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Objects;
import mutationtester.model.MutationPoint;

/** Makes the final decision before a mutant is written or a command is run. */
public final class SafetyController {
    private final ContextualSafetyAssessor assessor = new ContextualSafetyAssessor();
    private final PythonSyntaxSafetyAssessor pythonAssessor = new PythonSyntaxSafetyAssessor();
    private final AntlrSyntaxSafetyAssessor syntaxAssessor = new AntlrSyntaxSafetyAssessor();
    private final BufferedReader input;
    private final PrintStream output;
    private final OpenAiSafetyReviewer reviewer;
    // unattended_safety=run_unknown: with no terminal, UNKNOWN mutants are tested (REVIEW and BLOCK still skipped).
    private volatile boolean runUnknownUnattended;

    public SafetyController(BufferedReader input, PrintStream output) {
        this(input, output, null);
    }

    public SafetyController(BufferedReader input, PrintStream output, OpenAiSafetyReviewer reviewer) {
        this.input = input;
        this.output = Objects.requireNonNull(output);
        this.reviewer = reviewer;
    }

    public static SafetyController fromEnvironment(BufferedReader input, PrintStream output) {
        if (!"yes".equalsIgnoreCase(System.getenv("MUTATION_SAFETY_SEND_CODE"))) return new SafetyController(input, output);
        String key = System.getenv("MUTATION_SAFETY_API_KEY");
        if (key == null || key.isBlank()) key = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("MUTATION_SAFETY_MODEL");
        String endpoint = System.getenv("MUTATION_SAFETY_API_URL");
        if (endpoint == null || endpoint.isBlank()) endpoint = "https://api.openai.com/v1/responses";
        if (key == null || key.isBlank() || model == null || model.isBlank()) {
            output.println("Safety model review requested but the API key or MUTATION_SAFETY_MODEL is missing; using local review.");
            return new SafetyController(input, output);
        }
        try {
            OpenAiSafetyReviewer reviewer = OpenAiSafetyReviewer.create(key, model, endpoint);
            output.println("Safety model review enabled: source files and compile/test commands are sent to the configured API provider.");
            return new SafetyController(input, output, reviewer);
        } catch (IllegalArgumentException ex) {
            output.println("Invalid safety reviewer endpoint; using local review. An HTTPS Responses API URL is required.");
            return new SafetyController(input, output);
        }
    }

    public void setRunUnknownUnattended(boolean runUnknown) {
        this.runUnknownUnattended = runUnknown;
    }

    public record Outcome(SafetyAction action, SafetyAssessment assessment) {}

    public SafetyAction decide(MutationPoint point, String original, String mutated) {
        return decideWithAssessment(point, original, mutated).action();
    }

    public Outcome decideWithAssessment(MutationPoint point, String original, String mutated) {
        return decideWithAssessment(point, original, mutated, "", "");
    }

    public Outcome decideWithAssessment(MutationPoint point, String original, String mutated,
            String compileCommand, String testCommand) {
        SafetyAssessment local = assessLocal(point, original, mutated);
        SafetyAssessment assessment = reviewer == null || local.level() == SafetyLevel.SAFE || local.level() == SafetyLevel.BLOCK
                ? local : reviewer.assess(original, mutated, local, point.getSourceFile().getName(), compileCommand, testCommand);
        if (assessment.level() == SafetyLevel.SAFE) return new Outcome(SafetyAction.CONTINUE, assessment);
        output.println("\n" + point + "\n" + SafetyGate.consolePrompt(assessment));
        output.println("Original line: " + lineAt(original, point.getLineNumber()));
        output.println("Mutated line:  " + lineAt(mutated, point.getLineNumber()));
        if (input == null) {
            if (runUnknownUnattended && assessment.level() == SafetyLevel.UNKNOWN) {
                output.println("No interactive terminal; unattended_safety=run_unknown, so testing this mutant.");
                return new Outcome(SafetyAction.CONTINUE, assessment);
            }
            output.println("No interactive terminal; skipping this mutant.");
            return new Outcome(SafetyAction.SKIP, assessment);
        }
        while (true) {
            output.print("Choice: ");
            output.flush();
            try {
                String answer = input.readLine();
                if (answer == null) return new Outcome(SafetyAction.SKIP, assessment);
                SafetyAction action = switch (answer.trim().toLowerCase()) {
                    case "c", "continue" -> SafetyAction.CONTINUE;
                    case "s", "skip" -> SafetyAction.SKIP;
                    case "a", "abort" -> SafetyAction.ABORT;
                    default -> null;
                };
                if (action != null && SafetyGate.decide(assessment, action, action == SafetyAction.CONTINUE).permitted()) return new Outcome(action, assessment);
                output.println("Invalid or unavailable choice; try again.");
            } catch (IOException e) {
                output.println("Could not read a choice; skipping this mutant.");
                return new Outcome(SafetyAction.SKIP, assessment);
            }
        }
    }
    public SafetyAssessment assess(String original, String mutated) {
        SafetyAssessment local = assessor.assess(original, mutated);
        if (reviewer == null || local.level() == SafetyLevel.SAFE || local.level() == SafetyLevel.BLOCK) return local;
        return reviewer.assess(original, mutated, local);
    }

    private SafetyAssessment assessLocal(MutationPoint point, String original, String mutated) {
        SafetyAssessment fallback = assessor.assess(original, mutated);
        if (point.getSourceFile() != null && point.getSourceFile().getName().toLowerCase(java.util.Locale.ROOT).endsWith(".py")) {
            SafetyAssessment syntax = pythonAssessor.assess(point, original, mutated);
            if (fallback.level() == SafetyLevel.BLOCK && (syntax.category().equals("DATABASE") || syntax.category().equals("PROCESS"))) return fallback;
            return syntax;
        }
        String file = point.getSourceFile() == null ? "" : point.getSourceFile().getName().toLowerCase(java.util.Locale.ROOT);
        if (file.endsWith(".java") || file.endsWith(".js") || file.endsWith(".cs") || file.endsWith(".rkt")) {
            return syntaxAssessor.assess(point, original, mutated);
        }
        return fallback;
    }

    private static String lineAt(String text, int lineNumber) {
        if (text == null || lineNumber < 1) return "<unavailable>";
        String[] lines = text.split("\\R", -1);
        return lineNumber <= lines.length ? lines[lineNumber - 1] : "<unavailable>";
    }
}
