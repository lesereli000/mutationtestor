package mutationtester.scanner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

/**
 * Conservative language adapters for shared PIT behaviors that are not yet
 * represented by a dedicated ANTLR visitor in every scanner.
 */
final class UniversalPitMutationScanner {

    private UniversalPitMutationScanner() {
    }

    static List<MutationPoint> scan(File file, Mutator.MutatorType type) throws IOException {
        String source = Files.readString(file.toPath());
        String extension = extension(file.getName());
        return switch (type) {
            case CONDITIONAL_TRUE, CONDITIONAL_FALSE -> scanConditions(file, source, extension, type);
            case REMOVE_LOGICAL_NEGATION -> scanLogicalNegation(file, source, extension, type);
            case EMPTY_RETURNS -> scanEmptyReturns(file, source, extension, type);
            case VML_VOID_METHOD_CALL -> scanVoidCalls(file, source, extension, type);
            case NON_VOID_METHOD_CALLS -> scanReturnedCalls(file, source, extension, type);
            case CONSTRUCTOR_CALLS -> scanConstructors(file, source, extension, type);
            default -> List.of();
        };
    }

    private static List<MutationPoint> scanConditions(
            File file, String source, String ext, Mutator.MutatorType type) {
        String replacement;
        if (ext.equals("py")) {
            replacement = type == Mutator.MutatorType.CONDITIONAL_TRUE ? "True" : "False";
        } else {
            replacement = type == Mutator.MutatorType.CONDITIONAL_TRUE ? "true" : "false";
        }
        if (ext.equals("py")) {
            List<MutationPoint> points = new ArrayList<>();
            Matcher matcher = Pattern.compile("(?m)^[ \\t]*if\\s+").matcher(source);
            while (matcher.find()) {
                int start = matcher.end();
                int colon = suiteColon(source, start);
                if (colon < 0) continue;
                String condition = source.substring(start, colon).stripTrailing();
                if (condition.isEmpty() || isIgnoredPosition(source, start, ext)) continue;
                add(file, source, type, points, start, condition, replacement);
            }
            return points;
        }
        Pattern pattern = Pattern.compile("\\bif\\s*\\(\\s*([^()\\r\\n]+?)\\s*\\)");
        return matches(file, source, type, pattern, 1, replacement);
    }

    /** Offset of the colon ending a Python condition, skipping strings and nested brackets; -1 if none. */
    private static int suiteColon(String source, int from) {
        int depth = 0;
        char quote = 0;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote || c == '\n') quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(' || c == '[' || c == '{') depth++;
            else if (c == ')' || c == ']' || c == '}') depth--;
            else if (c == '\n' && depth <= 0) return -1;
            else if (c == ':' && depth == 0
                    && (i + 1 >= source.length() || source.charAt(i + 1) != '=')) return i;
        }
        return -1;
    }

    /** Offset of the ')' matching the '(' at open, skipping string literals; -1 if unbalanced. */
    private static int closingParen(String source, int open) {
        int depth = 0;
        char quote = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (quote != 0) {
                if (c == '\\') i++;
                else if (c == quote) quote = 0;
                continue;
            }
            if (c == '"' || c == '\'') quote = c;
            else if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return i;
        }
        return -1;
    }

    private static List<MutationPoint> scanLogicalNegation(
            File file, String source, String ext, Mutator.MutatorType type) {
        Pattern pattern;
        if (ext.equals("py")) {
            pattern = Pattern.compile("\\bnot\\s+([A-Za-z_][A-Za-z0-9_.]*)");
        } else {
            pattern = Pattern.compile("!(?!=)\\s*([A-Za-z_$][A-Za-z0-9_$.]*)");
        }

        List<MutationPoint> points = new ArrayList<>();
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            if (isIgnoredPosition(source, matcher.start(), ext)) continue;
            add(file, source, type, points, matcher.start(), matcher.group(), matcher.group(1));
        }
        return points;
    }

    private static List<MutationPoint> scanEmptyReturns(
            File file, String source, String ext, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        if (ext.equals("rkt")) {
            addMatches(file, source, type, points,
                    Pattern.compile("\\(list\\s+[^()\\r\\n]+\\)"), 0, "'()");
            return points;
        }

        // Single quotes delimit strings in Python and JavaScript only; in Java and C# they are chars.
        boolean singleQuotedStrings = ext.equals("py") || ext.equals("js");
        addMatches(file, source, type, points,
                Pattern.compile(singleQuotedStrings
                        ? "\\breturn\\s+((?:\"(?:\\\\.|[^\"])*\"|'(?:\\\\.|[^'])*'))\\s*;?"
                        : "\\breturn\\s+(\"(?:\\\\.|[^\"])*\")\\s*;?"),
                1, "\"\"");
        if (ext.equals("py")) {
            addMatches(file, source, type, points,
                    Pattern.compile("\\breturn\\s+(\\[[^]\\r\\n]+])"), 1, "[]");
            addMatches(file, source, type, points,
                    Pattern.compile("\\breturn\\s+(\\{[^}\\r\\n]+})"), 1, "{}");
            addMatches(file, source, type, points,
                    Pattern.compile("\\breturn\\s+(\\([^()\\r\\n]*,[^()\\r\\n]*\\))"), 1, "()");
        } else if (ext.equals("java")) {
            addMatches(file, source, type, points,
                    Pattern.compile("\\breturn\\s+(List\\.of\\([^;\\r\\n]+\\))\\s*;"), 1, "List.of()");
        }
        return points;
    }

    private static List<MutationPoint> scanVoidCalls(
            File file, String source, String ext, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = source.split("\\n", -1);
        int offset = 0;
        Pattern pattern = ext.equals("py")
                ? Pattern.compile("^(\\s*)([a-z_][A-Za-z0-9_.]*\\([^\\r\\n]*\\))\\s*$")
                : Pattern.compile("^(\\s*)([a-zA-Z_$][A-Za-z0-9_$.]*\\([^;\\r\\n]*\\);)\\s*$");
        for (String line : lines) {
            Matcher matcher = pattern.matcher(line);
            if (matcher.matches()) {
                String call = matcher.group(2);
                String replacement = ext.equals("py") ? "pass" : ";";
                int callOffset = offset + matcher.start(2);
                if (!isIgnoredPosition(source, callOffset, ext)) {
                    add(file, source, type, points, callOffset, call, replacement);
                }
            }
            offset += line.length() + 1;
        }
        return points;
    }

    private static List<MutationPoint> scanReturnedCalls(
            File file, String source, String ext, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        if (ext.equals("rkt")) {
            Pattern pattern = Pattern.compile(
                    "\\(define\\s+\\([^)]*\\)\\s+(\\((?!(?:make-|list\\b|not\\b|if\\b|and\\b|or\\b|begin\\b|void\\b))[a-zA-Z_][A-Za-z0-9_?!-]*\\s+[^()]*\\))\\s*\\)");
            addMatches(file, source, type, points, pattern, 1, "'()");
            return points;
        }
        Pattern pattern = Pattern.compile(
                "\\breturn\\s+([a-z_$][A-Za-z0-9_$.]*\\([^;\\r\\n]*\\))\\s*;?");
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            if (isIgnoredPosition(source, matcher.start(), ext)) continue;
            String replacement = ext.equals("py") ? "None" : javaDefaultForEnclosingMethod(source, matcher.start());
            if (replacement != null) {
                add(file, source, type, points, matcher.start(1), matcher.group(1), replacement);
            }
        }
        return points;
    }

    private static List<MutationPoint> scanConstructors(
            File file, String source, String ext, Mutator.MutatorType type) {
        if (ext.equals("rkt")) {
            return matches(file, source, type,
                    Pattern.compile("(\\(make-[A-Za-z_][A-Za-z0-9_?!-]*\\s*[^()]*\\))"), 1, "'()");
        }

        boolean python = ext.equals("py");
        // A call's callee including any module prefix, up to its opening parenthesis.
        Pattern head = python
                ? Pattern.compile("(?<![\\w.@])(?:[A-Za-z_]\\w*\\.)*[A-Z][A-Za-z0-9_]*\\s*\\(")
                : Pattern.compile("\\bnew\\s+[A-Za-z_$][A-Za-z0-9_$<>., ?]*\\(");
        Pattern definition = Pattern.compile("^[ \\t]*(?:class|def|async\\s+def)\\s");
        List<MutationPoint> points = new ArrayList<>();
        Matcher matcher = head.matcher(source);
        while (matcher.find()) {
            int start = matcher.start();
            if (isIgnoredPosition(source, start, ext)) continue;
            int lineStart = source.lastIndexOf('\n', start - 1) + 1;
            if (python && definition.matcher(source.substring(lineStart)).find()) continue;
            int close = closingParen(source, matcher.end() - 1);
            if (close < 0) continue;
            if (!python) {
                // Anonymous classes (new T() { ... }) can't be replaced by null.
                int after = close + 1;
                while (after < source.length() && Character.isWhitespace(source.charAt(after))) after++;
                if (after < source.length() && source.charAt(after) == '{') continue;
            }
            add(file, source, type, points, start, source.substring(start, close + 1),
                    python ? "None" : "null");
        }
        return points;
    }

    private static List<MutationPoint> matches(File file, String source, Mutator.MutatorType type,
                                                Pattern pattern, int group, String replacement) {
        List<MutationPoint> points = new ArrayList<>();
        addMatches(file, source, type, points, pattern, group, replacement);
        return points;
    }

    private static void addMatches(File file, String source, Mutator.MutatorType type,
                                   List<MutationPoint> points, Pattern pattern,
                                   int group, String replacement) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            if (isIgnoredPosition(source, matcher.start(), extension(file.getName()))) continue;
            add(file, source, type, points, matcher.start(group), matcher.group(group), replacement);
        }
    }

    /** Returns a compile-safe default for the Java method containing the return statement. */
    private static String javaDefaultForEnclosingMethod(String source, int returnOffset) {
        Pattern declaration = Pattern.compile(
                "(?m)(?:public|protected|private|static|final|synchronized|native|abstract|\\s)+"
                        + "([A-Za-z_$][A-Za-z0-9_$<>.?\\[\\]]*)\\s+"
                        + "[A-Za-z_$][A-Za-z0-9_$]*\\s*\\([^;{}]*\\)\\s*\\{");
        Matcher matcher = declaration.matcher(source.substring(0, returnOffset));
        String returnType = null;
        while (matcher.find()) returnType = matcher.group(1);
        if (returnType == null || returnType.equals("void")) return null;
        return switch (returnType) {
            case "boolean" -> "false";
            case "byte", "short", "int", "long", "float", "double" -> "0";
            case "char" -> "'\\0'";
            default -> "null";
        };
    }

    /** Prevents regex fallbacks from generating mutations in comments or string literals. */
    private static boolean isIgnoredPosition(String source, int target, String ext) {
        boolean inString = false;
        boolean inLineComment = false;
        boolean inBlockComment = false;
        char quote = 0;
        for (int i = 0; i < target; i++) {
            char c = source.charAt(i);
            char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                if (c == '*' && next == '/') { inBlockComment = false; i++; }
                continue;
            }
            if (inString) {
                if (c == '\\') { i++; continue; }
                if (c == quote) inString = false;
                continue;
            }
            if (ext.equals("rkt") && c == '#' && next == '\\') {
                i += 2;
            } else if (c == '"' || (c == '\'' && !ext.equals("rkt")) || (ext.equals("js") && c == '`')) {
                inString = true;
                quote = c;
            } else if ((ext.equals("java") || ext.equals("js") || ext.equals("cs")) && c == '/' && next == '/') {
                inLineComment = true;
                i++;
            } else if ((ext.equals("java") || ext.equals("js") || ext.equals("cs")) && c == '/' && next == '*') {
                inBlockComment = true;
                i++;
            } else if (ext.equals("py") && c == '#') {
                inLineComment = true;
            } else if (ext.equals("rkt") && c == ';') {
                inLineComment = true;
            }
        }
        return inString || inLineComment || inBlockComment;
    }

    private static void add(File file, String source, Mutator.MutatorType type,
                            List<MutationPoint> points, int offset,
                            String original, String replacement) {
        if (original.equals(replacement)
                || (replacement.equals("\"\"") && original.equals("''"))) {
            return;
        }
        int line = 1;
        int lastNewline = -1;
        for (int i = 0; i < offset; i++) {
            if (source.charAt(i) == '\n') {
                line++;
                lastNewline = i;
            }
        }
        points.add(new MutationPoint(file, line, offset - lastNewline - 1,
                original, replacement, type));
    }

    private static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase();
    }
}
