package mutationtester.equivalent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Just enough JSON for the equivalent-mutant files: objects, arrays, strings,
 * numbers, booleans and null. Mutant IDs embed source lines, so strings can
 * contain brackets, quotes and escapes - a regex is not good enough here.
 */
public final class MiniJson {
    private final String text;
    private int pos;

    private MiniJson(String text) {
        this.text = text;
    }

    /**
     * Parses a JSON document into Map (objects), List (arrays), String, Double,
     * Boolean or null.
     *
     * @throws IllegalArgumentException if the text is not valid JSON
     */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("No JSON content");
        }
        MiniJson parser = new MiniJson(text);
        parser.skipWhitespace();
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (parser.pos != text.length()) {
            throw parser.error("Unexpected trailing content");
        }
        return value;
    }

    /** Quotes a string as a JSON string literal. */
    public static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    private Object readValue() {
        if (pos >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        char c = text.charAt(pos);
        return switch (c) {
            case '{' -> readObject();
            case '[' -> readArray();
            case '"' -> readString();
            case 't' -> readLiteral("true", Boolean.TRUE);
            case 'f' -> readLiteral("false", Boolean.FALSE);
            case 'n' -> readLiteral("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield readNumber();
                }
                throw error("Unexpected character '" + c + "'");
            }
        };
    }

    private Map<String, Object> readObject() {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // {
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw error("Expected a string key");
            }
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();
            map.put(key, readValue());
            skipWhitespace();
            char c = next();
            if (c == '}') {
                return map;
            }
            if (c != ',') {
                throw error("Expected ',' or '}'");
            }
        }
    }

    private List<Object> readArray() {
        List<Object> list = new ArrayList<>();
        pos++; // [
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue());
            skipWhitespace();
            char c = next();
            if (c == ']') {
                return list;
            }
            if (c != ',') {
                throw error("Expected ',' or ']'");
            }
        }
    }

    private String readString() {
        pos++; // opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= text.length()) {
                throw error("Unterminated string");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            if (pos >= text.length()) {
                throw error("Unterminated escape");
            }
            char e = text.charAt(pos++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw error("Bad unicode escape");
                    }
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw error("Bad unicode escape");
                    }
                    pos += 4;
                }
                default -> throw error("Bad escape '\\" + e + "'");
            }
        }
    }

    private Double readNumber() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        try {
            return Double.parseDouble(text.substring(start, pos));
        } catch (NumberFormatException e) {
            throw error("Bad number");
        }
    }

    private Object readLiteral(String literal, Object value) {
        if (!text.startsWith(literal, pos)) {
            throw error("Unexpected token");
        }
        pos += literal.length();
        return value;
    }

    private void skipWhitespace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("Unexpected end of JSON");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("Expected '" + c + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException(message + " at position " + pos);
    }
}
