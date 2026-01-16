public final class RacketMutator {
    private RacketMutator() {}
    private enum State {
        CODE,           // normal code
        LINE_COMMENT,   // ; comment
        BLOCK_COMMENT,  // #| ... |#
        STRING          // "..."
    }

    public static String mutatePlusToMinus(String code) {
        StringBuilder out = new StringBuilder(code.length());
        State state = State.CODE;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';

            switch (state) {

                case CODE -> {
                    // Line comment starts with ';'
                    if (c == ';') {
                        out.append(c);
                        state = State.LINE_COMMENT;
                        continue;
                    }

                    // Block comment starts with '#|'
                    if (c == '#' && next == '|') {
                        out.append(c).append(next);
                        i++;
                        state = State.BLOCK_COMMENT;
                        continue;
                    }

                    // String literal starts with '"'
                    if (c == '"') {
                        out.append(c);
                        state = State.STRING;
                        continue;
                    }

                    // Mutate '+' only if it is a standalone Racket token
                    if (c == '+' && shouldMutatePlusToken(code, i)) {
                        out.append('-');
                    } else {
                        out.append(c);
                    }
                }

                case LINE_COMMENT -> {
                    out.append(c);
                    if (c == '\n') {
                        state = State.CODE;
                    }
                }

                case BLOCK_COMMENT -> {
                    out.append(c);
                    // Block comment ends with '|#'
                    if (c == '|' && next == '#') {
                        out.append(next);
                        i++;
                        state = State.CODE;
                    }
                }

                case STRING -> {
                    out.append(c);
                    // Preserve escaped characters inside strings
                    if (c == '\\' && next != '\0') {
                        out.append(next);
                        i++;
                        continue;
                    }
                    if (c == '"') {
                        state = State.CODE;
                    }
                }
            }
        }

        return out.toString();
    }

    private static boolean shouldMutatePlusToken(String code, int plusIndex) {
        char prev = (plusIndex - 1 >= 0) ? code.charAt(plusIndex - 1) : '\0';
        char next = (plusIndex + 1 < code.length()) ? code.charAt(plusIndex + 1) : '\0';

        // Numeric literal prefix: +1, +2.3
        if (Character.isDigit(next) || next == '.') {
            return false;
        }

        // '+' must be surrounded by delimiters to be a standalone token
        return isRacketDelimiter(prev) && isRacketDelimiter(next);
    }

    private static boolean isRacketDelimiter(char c) {
        if (c == '\0') return true;
        if (Character.isWhitespace(c)) return true;
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }
}
