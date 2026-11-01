package mutationtester.safety;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares the change with nearby code. This is an evidence-based triage aid,
 * not an interpreter, sandbox, or proof that a program is safe.
 */
public final class ContextualSafetyAssessor {
    private static final String FLAGS = "(?is)";
    private static final Pattern DESTRUCTIVE = Pattern.compile(FLAGS + "\\b(drop\\s+(table|database)|truncate\\s+table|rm\\s+-[a-z]*r[a-z]*f\\b|mkfs(?:\\.[a-z0-9]+)?\\b)\\b");
    private static final Pattern DATABASE = Pattern.compile(FLAGS + "\\b(delete\\s+from|update\\s+\\w+|insert\\s+into|execute(?:update)?|executemany|commit|query)\\b");
    private static final Pattern FILESYSTEM = Pattern.compile(FLAGS + "(?:\\b(unlink|rmtree|remove|delete|deleteifexists|write(?:alltext|allbytes|_text|_bytes)?|open|createfile|fileoutputstream)\\s*\\(|\\((delete-file|delete-directory|delete|call-with-output-file|open-output-file)\\b)");
    private static final Pattern NETWORK = Pattern.compile(FLAGS + "\\b(fetch|request|requests?\\.(get|post|put|delete)|connect|send|sendall|httpclient|urlopen)\\s*\\(");
    private static final Pattern PROCESS = Pattern.compile(FLAGS + "\\b(popen|system|exec|spawn|processbuilder|startprocess|run)\\s*\\(");
    private static final Pattern CREDENTIAL = Pattern.compile(FLAGS + "\\b(password|credential|secret|api[_-]?key|access[_-]?token)\\b");
    private static final Pattern CONTROL = Pattern.compile(FLAGS + "\\b(if|elif|else|unless|when|while|for|assert|return|throw|raise|try|catch|except|cond)\\b|&&|\\|\\||[<>!=]=?|\\b(and|or|not)\\b");
    private static final Pattern LOOP = Pattern.compile(FLAGS + "\\b(while|for|loop|recur|recursion)\\b");
    private static final Pattern PURE = Pattern.compile("\\s*[0-9\\s+*%().<>=!&|^~-]+\\s*");
    private static final int CONTEXT_LINES = 8;

    public SafetyAssessment assess(String original, String mutated) {
        if (original == null || mutated == null || original.equals(mutated)) {
            return result(SafetyLevel.UNKNOWN, "UNVERIFIED_CHANGE", "The original and changed code could not be compared.");
        }
        Change change = Change.between(original, mutated);
        String before = change.beforeLine();
        String after = change.afterLine();
        String nearby = change.nearby();

        // Block only a newly introduced explicit destructive operation. Existing
        // operations or ambiguous references require review, not a blanket block.
        if (DESTRUCTIVE.matcher(after).find() && !DESTRUCTIVE.matcher(before).find()) {
            return result(SafetyLevel.BLOCK, "DESTRUCTIVE_CHANGE", "The changed statement introduces an explicit destructive command or database operation.");
        }
        String category = riskCategory(before + "\n" + after);
        if (category != null) {
            return result(SafetyLevel.REVIEW, category, "The changed statement may alter " + riskDescription(category) + ". Review the original and replacement before running it.");
        }
        String nearbyCategory = riskCategory(nearby);
        if (nearbyCategory != null && (CONTROL.matcher(before).find() || CONTROL.matcher(after).find())) {
            return result(SafetyLevel.REVIEW, "CONTROL_FLOW_" + nearbyCategory,
                    "The changed condition or control flow is near " + riskDescription(nearbyCategory) + "; it could alter whether or how often that operation runs.");
        }
        if (LOOP.matcher(before + "\n" + after).find() ||
                ((before.contains("<") || before.contains(">")) && LOOP.matcher(nearby).find())) {
            return result(SafetyLevel.REVIEW, "RESOURCE_EXHAUSTION", "The mutation may change a loop or its termination condition.");
        }
        if (PURE.matcher(original).matches() && PURE.matcher(mutated).matches()) {
            return result(SafetyLevel.SAFE, "LITERAL_EXPRESSION", "Both inputs contain only numeric literals and simple operators; the wider test environment is not assessed.");
        }
        return result(SafetyLevel.UNKNOWN, "UNRESOLVED_CONTEXT", "The change is not demonstrably safe: calls, aliases, dependencies, and test behavior may have effects outside this file.");
    }

    private static String riskCategory(String text) {
        if (DESTRUCTIVE.matcher(text).find() || DATABASE.matcher(text).find()) return "DATABASE";
        if (FILESYSTEM.matcher(text).find()) return "FILESYSTEM";
        if (NETWORK.matcher(text).find()) return "NETWORK";
        if (PROCESS.matcher(text).find()) return "PROCESS";
        if (CREDENTIAL.matcher(text).find()) return "CREDENTIALS";
        return null;
    }

    private static String riskDescription(String category) {
        return switch (category.toUpperCase(Locale.ROOT)) {
            case "DATABASE" -> "a database or destructive operation";
            case "FILESYSTEM" -> "a filesystem operation";
            case "NETWORK" -> "a network operation";
            case "PROCESS" -> "an external process or command";
            case "CREDENTIALS" -> "credential handling";
            default -> "an external operation";
        };
    }

    private static SafetyAssessment result(SafetyLevel level, String category, String reason) {
        return new SafetyAssessment(level, category, reason, 0.0, "local change and context analysis (heuristic)");
    }

    private record Change(String beforeLine, String afterLine, String nearby) {
        static Change between(String before, String after) {
            int prefix = 0;
            int max = Math.min(before.length(), after.length());
            while (prefix < max && before.charAt(prefix) == after.charAt(prefix)) prefix++;
            int suffix = 0;
            while (suffix < before.length() - prefix && suffix < after.length() - prefix
                    && before.charAt(before.length() - suffix - 1) == after.charAt(after.length() - suffix - 1)) suffix++;
            int beforeStart = lineStart(before, prefix);
            int afterStart = lineStart(after, prefix);
            int beforeEnd = lineEnd(before, before.length() - suffix);
            int afterEnd = lineEnd(after, after.length() - suffix);
            return new Change(before.substring(beforeStart, beforeEnd), after.substring(afterStart, afterEnd),
                    window(before, beforeStart, beforeEnd) + "\n" + window(after, afterStart, afterEnd));
        }

        private static int lineStart(String text, int position) {
            return text.lastIndexOf('\n', Math.max(-1, position - 1)) + 1;
        }

        private static int lineEnd(String text, int position) {
            int end = text.indexOf('\n', position);
            return end < 0 ? text.length() : end;
        }

        private static String window(String text, int start, int end) {
            for (int i = 0; i < CONTEXT_LINES && start > 0; i++) start = lineStart(text, start - 1);
            for (int i = 0; i < CONTEXT_LINES && end < text.length(); i++) end = lineEnd(text, end + 1);
            return text.substring(start, end);
        }
    }
}
