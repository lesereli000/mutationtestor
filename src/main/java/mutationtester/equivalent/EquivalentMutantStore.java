package mutationtester.equivalent;

import mutationtester.model.MutantResult;
import mutationtester.model.MutationPoint;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class EquivalentMutantStore {
    private static final Path STORE_PATH = Path.of("./output/report/equivalent-mutants.json");
    private static final Pattern STRING_PATTERN = Pattern.compile("\\\"((?:\\\\.|[^\\\\\"])*)\\\"");
    private static final Pattern PYTHON_FUNCTION_PATTERN = Pattern.compile("^\\s*(?:async\\s+)?def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    private static final Pattern RACKET_FUNCTION_PATTERN = Pattern.compile("^\\s*\\(define\\s+\\(?\\s*([A-Za-z_+\\-*/<>=!?][A-Za-z0-9_+\\-*/<>=!?]*)");
    private static final Pattern JS_FUNCTION_PATTERN = Pattern.compile("\\bfunction\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*\\(");
    private static final Pattern JS_ASSIGNMENT_FUNCTION_PATTERN = Pattern.compile("\\b(?:const|let|var)\\s+([A-Za-z_$][A-Za-z0-9_$]*)\\s*=\\s*(?:async\\s*)?(?:\\([^)]*\\)|[A-Za-z_$][A-Za-z0-9_$]*)\\s*=>");
    private static final Pattern BRACE_LANGUAGE_METHOD_PATTERN = Pattern.compile("\\b([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;{}]*\\)\\s*(?:\\{|=>)?\\s*$");

    public static Path getStorePath() {
        return STORE_PATH;
    }

    public static Set<String> loadHiddenMutantIds() throws IOException {
        if (!Files.exists(STORE_PATH)) {
            return new LinkedHashSet<>();
        }

        String content = Files.readString(STORE_PATH, StandardCharsets.UTF_8);
        int arrayStart = content.indexOf('[');
        int arrayEnd = content.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < arrayStart) {
            return new LinkedHashSet<>();
        }

        String arrayBody = content.substring(arrayStart + 1, arrayEnd);
        Set<String> ids = new LinkedHashSet<>();
        boolean migrated = false;
        Matcher matcher = STRING_PATTERN.matcher(arrayBody);
        while (matcher.find()) {
            String id = unescapeJson(matcher.group(1));
            String normalizedId = normalizeStoredMutantId(id);
            ids.add(normalizedId);
            migrated = migrated || !normalizedId.equals(id);
        }

        if (migrated) {
            saveHiddenMutantIds(ids);
        }
        return ids;
    }

    public static void saveHiddenMutantIds(Set<String> hiddenIds) throws IOException {
        Files.createDirectories(STORE_PATH.getParent());

        Set<String> normalizedHiddenIds = new LinkedHashSet<>();
        for (String id : hiddenIds) {
            normalizedHiddenIds.add(normalizeStoredMutantId(id));
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"hiddenMutantIds\": [\n");

        int index = 0;
        for (String id : normalizedHiddenIds) {
            if (index++ > 0) {
                json.append(",\n");
            }
            json.append("    \"").append(escapeJson(id)).append("\"");
        }

        json.append("\n  ]\n}\n");
        Files.writeString(STORE_PATH, json.toString(), StandardCharsets.UTF_8);
    }

    public static void addHiddenMutantId(String mutantId) throws IOException {
        Set<String> hiddenIds = loadHiddenMutantIds();
        hiddenIds.add(normalizeStoredMutantId(mutantId));
        saveHiddenMutantIds(hiddenIds);
    }

    public static void removeHiddenMutantId(String mutantId) throws IOException {
        Set<String> hiddenIds = loadHiddenMutantIds();
        hiddenIds.remove(mutantId);
        hiddenIds.remove(normalizeStoredMutantId(mutantId));
        saveHiddenMutantIds(hiddenIds);
    }

    public static String buildMutantId(MutantResult result) {
        return buildMutantId(result.getMutationPoint());
    }

    public static String buildMutantId(MutationPoint point) {
        String fileName = point.getSourceFile() == null ? "<unknown>" : point.getSourceFile().getName();
        String methodName = safe(inferMethodName(point));
        if (methodName.isEmpty()) {
            methodName = "<unknown>";
        }
        String normalizedLine = normalizeSourceLine(readSourceLine(point));
        String operator = safe(point.getOperator());
        String replacement = safe(point.getReplacement());

        if (!normalizedLine.isEmpty()) {
            return "CONTENT|" + fileName + "|" + methodName + "|" + normalizedLine + "|" + operator + "|" + replacement;
        }

        return buildLegacyMutantId(point);
    }

    public static Set<String> buildCandidateMutantIds(MutantResult result) {
        return buildCandidateMutantIds(result.getMutationPoint());
    }

    public static Set<String> buildCandidateMutantIds(MutationPoint point) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(buildMutantId(point));
        ids.add(buildContentMutantIdWithoutMethod(point));
        ids.add(buildLegacyMutantId(point));
        return ids;
    }

    public static boolean matchesHiddenMutant(Set<String> hiddenIds, MutantResult result) {
        return matchesHiddenMutant(hiddenIds, result.getMutationPoint());
    }

    public static boolean matchesHiddenMutant(Set<String> hiddenIds, MutationPoint point) {
        for (String candidate : buildCandidateMutantIds(point)) {
            if (hiddenIds.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static String buildLegacyMutantId(MutationPoint point) {
        String fileName = point.getSourceFile() == null ? "<unknown>" : point.getSourceFile().getName();
        String type = point.getType() == null ? "UNKNOWN" : point.getType().name();
        return "LEGACY|" + fileName + "|" + point.getLineNumber() + "|" + point.getColumnNumber() + "|" + type + "|" + safe(point.getOperator()) + "|" + safe(point.getReplacement());
    }

    private static String buildContentMutantIdWithoutMethod(MutationPoint point) {
        String fileName = point.getSourceFile() == null ? "<unknown>" : point.getSourceFile().getName();
        String normalizedLine = normalizeSourceLine(readSourceLine(point));
        String operator = safe(point.getOperator());
        String replacement = safe(point.getReplacement());

        if (!normalizedLine.isEmpty()) {
            return "CONTENT|" + fileName + "|" + normalizedLine + "|" + operator + "|" + replacement;
        }

        return buildLegacyMutantId(point);
    }

    private static String normalizeStoredMutantId(String mutantId) {
        if (mutantId == null || !mutantId.startsWith("CONTENT|")) {
            return mutantId;
        }

        int fileEnd = mutantId.indexOf('|', "CONTENT|".length());
        if (fileEnd < 0) {
            return mutantId;
        }

        String fileName = mutantId.substring("CONTENT|".length(), fileEnd);
        String rest = mutantId.substring(fileEnd + 1);

        for (MutationLocation location : findMutationLocations(fileName)) {
            String newPrefix = location.methodName() + "|" + location.normalizedLine() + "|";
            if (rest.startsWith(newPrefix)) {
                return mutantId;
            }

            String oldPrefix = location.normalizedLine() + "|";
            if (rest.startsWith(oldPrefix)) {
                return "CONTENT|" + fileName + "|" + location.methodName() + "|" + rest;
            }
        }

        return mutantId;
    }

    private static List<MutationLocation> findMutationLocations(String fileName) {
        List<MutationLocation> locations = new java.util.ArrayList<>();
        Path root = Path.of(".");

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName() != null && path.getFileName().toString().equals(fileName))
                    .filter(path -> !path.toString().contains("/target/"))
                    .filter(path -> !path.toString().contains("/output/"))
                    .filter(path -> !path.toString().contains("/.git/"))
                    .forEach(path -> addMutationLocations(path, locations));
        } catch (IOException e) {
            return locations;
        }

        return locations;
    }

    private static void addMutationLocations(Path path, List<MutationLocation> locations) {
        try {
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (int i = 0; i < lines.size(); i++) {
                String normalizedLine = normalizeSourceLine(lines.get(i));
                if (normalizedLine.isEmpty()) {
                    continue;
                }

                String methodName = inferMethodName(lines, i);
                if (methodName.isEmpty()) {
                    methodName = "<unknown>";
                }
                locations.add(new MutationLocation(methodName, normalizedLine));
            }
        } catch (IOException e) {
            // Ignore unreadable source candidates.
        }
    }

    private static String inferMethodName(MutationPoint point) {
        if (point == null || point.getSourceFile() == null) {
            return "";
        }

        try {
            List<String> lines = Files.readAllLines(point.getSourceFile().toPath(), StandardCharsets.UTF_8);
            int index = Math.min(point.getLineNumber() - 1, lines.size() - 1);
            return inferMethodName(lines, index);
        } catch (IOException e) {
            return "";
        }

    }

    private static String inferMethodName(List<String> lines, int lineIndex) {
        int index = Math.min(lineIndex, lines.size() - 1);
        for (int i = index; i >= 0; i--) {
            String methodName = extractMethodName(lines.get(i));
            if (!methodName.isEmpty()) {
                return methodName;
            }
        }

        return "";
    }

    private static String extractMethodName(String line) {
        String withoutComment = line == null ? "" : line.replaceAll("//.*$", "").trim();
        if (withoutComment.isEmpty()) {
            return "";
        }

        Matcher pythonMatcher = PYTHON_FUNCTION_PATTERN.matcher(withoutComment);
        if (pythonMatcher.find()) {
            return pythonMatcher.group(1);
        }

        Matcher racketMatcher = RACKET_FUNCTION_PATTERN.matcher(withoutComment);
        if (racketMatcher.find()) {
            return racketMatcher.group(1);
        }

        Matcher jsFunctionMatcher = JS_FUNCTION_PATTERN.matcher(withoutComment);
        if (jsFunctionMatcher.find()) {
            return jsFunctionMatcher.group(1);
        }

        Matcher jsAssignmentMatcher = JS_ASSIGNMENT_FUNCTION_PATTERN.matcher(withoutComment);
        if (jsAssignmentMatcher.find()) {
            return jsAssignmentMatcher.group(1);
        }

        Matcher braceLanguageMatcher = BRACE_LANGUAGE_METHOD_PATTERN.matcher(withoutComment);
        if (braceLanguageMatcher.find()) {
            String candidate = braceLanguageMatcher.group(1);
            if (!isControlKeyword(candidate)) {
                return candidate;
            }
        }

        return "";
    }

    private static boolean isControlKeyword(String value) {
        return value.equals("if")
                || value.equals("for")
                || value.equals("foreach")
                || value.equals("while")
                || value.equals("switch")
                || value.equals("catch")
                || value.equals("using")
                || value.equals("return");
    }

    private record MutationLocation(String methodName, String normalizedLine) {}

    private static String readSourceLine(MutationPoint point) {
        if (point == null || point.getSourceFile() == null) {
            return "";
        }

        try {
            List<String> lines = Files.readAllLines(point.getSourceFile().toPath(), StandardCharsets.UTF_8);
            int index = point.getLineNumber() - 1;
            if (index >= 0 && index < lines.size()) {
                return lines.get(index);
            }
        } catch (IOException e) {
            return "";
        }

        return "";
    }

    private static String normalizeSourceLine(String line) {
        if (line == null) {
            return "";
        }

        String normalized = line.trim();

        if (normalized.isEmpty()) {
            return "";
        }

        normalized = normalized.replaceAll("//.*$", "");
        normalized = normalized.replaceAll("/\\*.*?\\*/", "");
        normalized = normalized.trim();

        normalized = normalized.replaceAll("\\s+", "");
        normalized = normalized.replace("“", "\"").replace("”", "\"");
        normalized = normalized.replace("‘", "'").replace("’", "'");

        return normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String value) {
        return value
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
