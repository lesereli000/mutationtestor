package mutationtester;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EquivalentMutantStore {
    private static final Path STORE_PATH = Path.of("./output/report/equivalent-mutants.json");
    private static final Pattern STRING_PATTERN = Pattern.compile("\\\"((?:\\\\.|[^\\\\\"])*)\\\"");

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
        Matcher matcher = STRING_PATTERN.matcher(arrayBody);
        while (matcher.find()) {
            ids.add(unescapeJson(matcher.group(1)));
        }
        return ids;
    }

    public static void saveHiddenMutantIds(Set<String> hiddenIds) throws IOException {
        Files.createDirectories(STORE_PATH.getParent());

        StringBuilder json = new StringBuilder();
        json.append("{\n  \"hiddenMutantIds\": [\n");

        int index = 0;
        for (String id : hiddenIds) {
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
        hiddenIds.add(mutantId);
        saveHiddenMutantIds(hiddenIds);
    }

    public static void removeHiddenMutantId(String mutantId) throws IOException {
        Set<String> hiddenIds = loadHiddenMutantIds();
        hiddenIds.remove(mutantId);
        saveHiddenMutantIds(hiddenIds);
    }

    public static String buildMutantId(MutantResult result) {
        return buildMutantId(result.getMutationPoint());
    }

    public static String buildMutantId(MutationPoint point) {
        String fileName = point.getSourceFile() == null ? "<unknown>" : point.getSourceFile().getName();
        String normalizedLine = normalizeSourceLine(readSourceLine(point));
        String operator = safe(point.getOperator());
        String replacement = safe(point.getReplacement());

        if (!normalizedLine.isEmpty()) {
            return "CONTENT|" + fileName + "|" + normalizedLine + "|" + operator + "|" + replacement;
        }

        return buildLegacyMutantId(point);
    }

    public static Set<String> buildCandidateMutantIds(MutantResult result) {
        return buildCandidateMutantIds(result.getMutationPoint());
    }

    public static Set<String> buildCandidateMutantIds(MutationPoint point) {
        Set<String> ids = new LinkedHashSet<>();
        ids.add(buildMutantId(point));
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