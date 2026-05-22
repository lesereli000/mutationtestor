package mutationtester.msl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Converts an "Except:" template from a .spec file into a regex
 * that matches against a source line to suppress a mutation.
 *
 * Template syntax uses the same {quantity} placeholder as Replace/With.
 *
 * Example:
 *   Except: if ({quantity} <= 0) {quantity} = 0
 *
 *
 */
public class ExceptPattern {

    private final String template;
    private final Map<Class<?>, Pattern> regexByAdapter = new ConcurrentHashMap<>();

    public ExceptPattern(String template) {
        this.template = template;
    }

    /**
     * Returns true if the given source line matches this exception pattern
     * after the template has been translated for the supplied adapter.
     */
    public boolean matches(String sourceLine, MSLScannerAdapter adapter) {
        if (sourceLine == null || sourceLine.isBlank()) return false;
        Pattern regex = regexByAdapter.computeIfAbsent(
                adapter.getClass(),
                cls -> templateToRegex(adapter.translateExceptTemplate(template)));
        return regex.matcher(sourceLine).find();
    }

    /**
     * Returns true if the source line matches the untranslated template.
     * Retained for unit tests and adapter-less callers.
     */
    public boolean matches(String sourceLine) {
        if (sourceLine == null || sourceLine.isBlank()) return false;
        Pattern regex = regexByAdapter.computeIfAbsent(
                ExceptPattern.class,
                cls -> templateToRegex(template));
        return regex.matcher(sourceLine).find();
    }

    /**
     * Converts the template string to a regex Pattern.
     *
     * Steps:
     * 1. Split on {quantity} placeholders
     * 2. Quote each literal segment (so special chars are safe)
     * 3. Replace {quantity} with a permissive identifier/number pattern
     * 4. Allow flexible whitespace throughout
     */
    private static Pattern templateToRegex(String template) {
        String[] parts = template.split("\\{quantity\\}", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            // Quote the literal part so ( ) . etc are treated as literals
            sb.append(Pattern.quote(parts[i]));
            if (i < parts.length - 1) {
                // {quantity} matches identifiers, numbers, field accesses
                sb.append("[\\w.]+");
            }
        }
        // Allow flexible whitespace (the author may write spaces differently)
        String regexStr = sb.toString().replace("\\Q \\E", "\\s*").replaceAll("\\\\Q\\s+\\\\E", "\\\\s+");
        return Pattern.compile(regexStr);
    }

    @Override
    public String toString() {
        return template;
    }
}