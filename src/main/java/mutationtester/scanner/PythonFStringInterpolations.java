package mutationtester.scanner;

import java.util.ArrayList;
import java.util.List;

/**
 * Pulls executed expressions out of Python f-string tokens.
 * The lexer stores an f-string as one STRING, so MSL never sees {@code a + b}
 * inside {@code f"{a + b}"} until those fields are parsed on their own.
 */
final class PythonFStringInterpolations {

    record Field(String expression, int fileOffset) {}

    private PythonFStringInterpolations() {
    }

    static boolean isFStringToken(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        int i = 0;
        boolean formatted = false;
        while (i < text.length() && i < 3 && "fFrRbBuU".indexOf(text.charAt(i)) >= 0) {
            char c = text.charAt(i);
            if (c == 'f' || c == 'F') {
                formatted = true;
            }
            i++;
        }
        return formatted;
    }

    static List<Field> fieldsInToken(String tokenText, int tokenStartInFile) {
        List<Field> fields = new ArrayList<>();
        if (!isFStringToken(tokenText)) {
            return fields;
        }
        int i = 0;
        while (i < tokenText.length() && "fFrRbBuU".indexOf(tokenText.charAt(i)) >= 0) {
            i++;
        }
        if (i >= tokenText.length()) {
            return fields;
        }
        boolean triple = i + 2 < tokenText.length()
                && tokenText.charAt(i) == tokenText.charAt(i + 1)
                && tokenText.charAt(i) == tokenText.charAt(i + 2)
                && (tokenText.charAt(i) == '"' || tokenText.charAt(i) == '\'');
        char quote = tokenText.charAt(i);
        i += triple ? 3 : 1;
        int end = tokenText.length() - (triple ? 3 : 1);
        if (end < i) {
            return fields;
        }
        while (i < end) {
            char c = tokenText.charAt(i);
            if (c == '{' && i + 1 < end && tokenText.charAt(i + 1) == '{') {
                i += 2;
                continue;
            }
            if (c == '}' && i + 1 < end && tokenText.charAt(i + 1) == '}') {
                i += 2;
                continue;
            }
            if (c == '{') {
                int close = matchingBrace(tokenText, i, end);
                if (close < 0) {
                    break;
                }
                String raw = tokenText.substring(i + 1, close);
                String expr = expressionOfField(raw);
                if (!expr.isBlank()) {
                    int exprOffsetInToken = i + 1 + raw.indexOf(expr);
                    if (!raw.contains(expr)) {
                        exprOffsetInToken = i + 1;
                    } else {
                        exprOffsetInToken = i + 1 + indexOfExpression(raw, expr);
                    }
                    fields.add(new Field(expr, tokenStartInFile + exprOffsetInToken));
                }
                i = close + 1;
                continue;
            }
            i++;
        }
        return fields;
    }

    private static int indexOfExpression(String raw, String expr) {
        int skip = 0;
        while (skip < raw.length() && Character.isWhitespace(raw.charAt(skip))) {
            skip++;
        }
        return skip;
    }

    /** Field body without conversion {@code !s} or format spec {@code :...}. */
    static String expressionOfField(String raw) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean triple = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (triple) {
                    if (c == quote && i + 2 < raw.length()
                            && raw.charAt(i + 1) == quote && raw.charAt(i + 2) == quote) {
                        inString = false;
                        i += 2;
                    }
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                inString = true;
                quote = c;
                triple = i + 2 < raw.length() && raw.charAt(i + 1) == c && raw.charAt(i + 2) == c;
                if (triple) {
                    i += 2;
                }
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            } else if (depth == 0 && (c == '!' || c == ':' || c == '=')) {
                if (c == '!' && i + 1 < raw.length() && "sraSRA".indexOf(raw.charAt(i + 1)) >= 0) {
                    return raw.substring(0, i).strip();
                }
                if (c == ':') {
                    return raw.substring(0, i).strip();
                }
                if (c == '=' && (i + 1 >= raw.length() || raw.charAt(i + 1) == '!' || raw.charAt(i + 1) == ':' || raw.charAt(i + 1) == '}')) {
                    return raw.substring(0, i).strip();
                }
            }
        }
        return raw.strip();
    }

    private static int matchingBrace(String text, int open, int end) {
        int depth = 0;
        boolean inString = false;
        char quote = 0;
        boolean triple = false;
        for (int i = open; i < end; i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (triple) {
                    if (c == quote && i + 2 < end
                            && text.charAt(i + 1) == quote && text.charAt(i + 2) == quote) {
                        inString = false;
                        i += 2;
                    }
                } else if (c == quote) {
                    inString = false;
                }
                continue;
            }
            if (c == '\'' || c == '"') {
                inString = true;
                quote = c;
                triple = i + 2 < end && text.charAt(i + 1) == c && text.charAt(i + 2) == c;
                if (triple) {
                    i += 2;
                }
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }
}
