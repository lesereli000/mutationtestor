package mutationtester.msl;

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
 * This suppresses the mutation on any line matching that pattern,
 * e.g.:   if(n <= 0) n = 0;   would be excluded.
 */
public class ExceptPattern {

    private final Pattern regex;
    private final String original;

    public ExceptPattern(String template) {
        this.original = template;
        this.regex = templateToRegex(template);
    }

    /**
     * Returns true if the given source line matches this exception pattern,
     * meaning the mutation should be suppressed on that line.
     */
    public boolean matches(String sourceLine) {
        if (sourceLine == null || sourceLine.isBlank()) return false;
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
            // Replace spaces with \E\s*\Q so they become flexible whitespace outside literal quotes
            String part = parts[i].replaceAll("\\s+", "\\\\E\\\\s*\\\\Q");
            sb.append("\\Q").append(part).append("\\E");
            if (i < parts.length - 1) {
                // {quantity} matches identifiers, numbers, field accesses
                sb.append("[\\w.]+");
            }
        }
        // Cleanup empty quotes (e.g., \Q\E) that may have been generated
        String regexStr = sb.toString().replace("\\Q\\E", "");
        return Pattern.compile(regexStr);
    }

    @Override
    public String toString() {
        return original;
    }
}