package mutationtester;

import mutationtester.msl.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
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
        String type = point.getType() == null ? "UNKNOWN" : point.getType().name();
        return fileName + ":" + point.getLineNumber() + ":" + point.getColumnNumber() + ":" + type + ":" + point.getOperator() + ":" + point.getReplacement();
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
