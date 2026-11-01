package mutationtester.safety;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small strict JSON reader for bounded API responses; rejects duplicate object keys. */
final class JsonReader {
    private final String text;
    private int pos;
    private JsonReader(String text) { this.text = text; }

    static Object read(String text) throws IOException {
        if (text == null || text.length() > 100000) throw new IOException("JSON too large");
        JsonReader parser = new JsonReader(text);
        Object value = parser.value(0);
        parser.space();
        if (parser.pos != text.length()) throw new IOException("Trailing JSON data");
        return value;
    }

    private Object value(int depth) throws IOException {
        if (depth > 40) throw new IOException("JSON too deeply nested");
        space();
        if (pos >= text.length()) throw new IOException("Unexpected end of JSON");
        return switch (text.charAt(pos)) {
            case '{' -> object(depth + 1);
            case '[' -> array(depth + 1);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object(int depth) throws IOException {
        pos++;
        Map<String, Object> result = new LinkedHashMap<>();
        space();
        if (take('}')) return result;
        do {
            space();
            if (pos >= text.length() || text.charAt(pos) != '"') throw new IOException("Invalid object key");
            String key = string();
            space();
            if (!take(':') || result.containsKey(key)) throw new IOException("Invalid or duplicate object key");
            result.put(key, value(depth));
            space();
            if (take('}')) return result;
        } while (take(','));
        throw new IOException("Invalid JSON object");
    }

    private List<Object> array(int depth) throws IOException {
        pos++;
        List<Object> result = new ArrayList<>();
        space();
        if (take(']')) return result;
        do {
            result.add(value(depth));
            space();
            if (take(']')) return result;
        } while (take(','));
        throw new IOException("Invalid JSON array");
    }

    private String string() throws IOException {
        pos++;
        StringBuilder out = new StringBuilder();
        while (pos < text.length()) {
            char c = text.charAt(pos++);
            if (c == '"') return out.toString();
            if (c < 32) throw new IOException("Invalid control character");
            if (c != '\\') { out.append(c); continue; }
            if (pos == text.length()) throw new IOException("Invalid escape");
            switch (text.charAt(pos++)) {
                case '"' -> out.append('"');
                case '\\' -> out.append('\\');
                case '/' -> out.append('/');
                case 'b' -> out.append('\b');
                case 'f' -> out.append('\f');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) throw new IOException("Invalid unicode escape");
                    try { out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16)); }
                    catch (NumberFormatException e) { throw new IOException("Invalid unicode escape", e); }
                    pos += 4;
                }
                default -> throw new IOException("Invalid escape");
            }
        }
        throw new IOException("Unterminated JSON string");
    }

    private Object number() throws IOException {
        int start = pos;
        while (pos < text.length() && "-+0123456789.eE".indexOf(text.charAt(pos)) >= 0) pos++;
        String number = text.substring(start, pos);
        if (!number.matches("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")) throw new IOException("Invalid JSON number");
        return number;
    }

    private Object literal(String token, Object value) throws IOException {
        if (!text.startsWith(token, pos)) throw new IOException("Invalid JSON literal");
        pos += token.length();
        return value;
    }

    private boolean take(char c) {
        if (pos < text.length() && text.charAt(pos) == c) { pos++; return true; }
        return false;
    }

    private void space() {
        while (pos < text.length() && " \n\r\t".indexOf(text.charAt(pos)) >= 0) pos++;
    }
}
