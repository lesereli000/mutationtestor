import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Mutator {

    public enum MutatorType {
        AOR_PLUS_TO_MINUS,
        // AOR_MINUS_TO_PLUS,
        // ROR_LT_TO_LE,
        // ROR_LE_TO_LT,
        // ROR_GT_TO_GE,
        // ROR_GE_TO_GT,
        // EQ_NEGATE,          // switch between == and !=
        // LOGICAL_AND_TO_OR,  // && to ||
        // LOGICAL_OR_TO_AND,  // || to &&
        // INC_TO_DEC,         // ++ to --
        // DEC_TO_INC          // -- to ++
    }

    // State machine states
    private enum State {
        CODE,
        LINE_COMMENT,
        BLOCK_COMMENT,
        STRING_DOUBLE,
        STRING_SINGLE
    }

    public static String mutate(Path inputPath, MutatorType type) throws IOException {
        String code = Files.readString(inputPath);
        Language language = new Language(inputPath.getFileName().toString());


        boolean isRacket = inputPath.getFileName().toString().toLowerCase().endsWith(".rkt");

        return mutateOutsideStringsAndComments(code, type, 1, language, isRacket);
        // return mutateOutsideStringsAndComments(code, type, Integer.MAX_VALUE, language, isRacket);
    }

    private static String mutateOutsideStringsAndComments(String code, MutatorType type, int maxEdits, Language language, boolean isRacket) {
        StringBuilder out = new StringBuilder(code.length());
        State state = State.CODE;
        int edits = 0;

        char[] lineComment = language.getSyntax(Language.LINE_COMMENT);
        char[] blockOpen = language.getSyntax(Language.BLOCK_COMMENT_OPEN);
        char[] blockClose = language.getSyntax(Language.BLOCK_COMMENT_CLOSE);
        char[] doubleQuote = language.getSyntax(Language.DOUBLE_STRING);
        char[] singleQuote = language.getSyntax(Language.SINGLE_STRING);
        char[] escape = language.getSyntax(Language.ESCAPE_CHARACTER);

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';

            switch (state) {
                case CODE -> {
                    // comments
                    if (lineComment.length == 1 && c == lineComment[0]) {
                        out.append(c);
                        state = State.LINE_COMMENT;
                        continue;
                    }
                    if (lineComment.length >= 2 && c == lineComment[0] && next == lineComment[1]) {
                        out.append(c).append(next);
                        i++;
                        state = State.LINE_COMMENT;
                        continue;
                    }
                    if (blockOpen.length >= 2 && c == blockOpen[0] && next == blockOpen[1]) {
                        out.append(c).append(next);
                        i++;
                        state = State.BLOCK_COMMENT;
                        continue;
                    }

                    // strings
                    if (doubleQuote.length >= 1 && c == doubleQuote[0]) {
                        out.append(c);
                        state = State.STRING_DOUBLE;
                        continue;
                    }
                    if (singleQuote.length >= 1 && c == singleQuote[0]) {
                        out.append(c);
                        state = State.STRING_SINGLE;
                        continue;
                    }

                    if (edits >= maxEdits) {
                        out.append(c);
                        continue;
                    }

                    //  Mutation cases
                    // math:
                    // 1. ++ -> --
                    // if (type == MutatorType.INC_TO_DEC && c == '+' && next == '+') {
                    //     out.append("--");
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }

                    // 2. -- -> ++
                    // if (type == MutatorType.DEC_TO_INC && c == '-' && next == '-') {
                    //     out.append("++");
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }

                    // 3. + -> -
                    if (type == MutatorType.AOR_PLUS_TO_MINUS && c == '+') {
                        if (isRacket) {
                            if (shouldMutateRacketPlusToken(code, i)) {
                                out.append('-');
                                edits++;
                                continue;
                            }
                        } else {
                            if (next != '+' && next != '=' && shouldMutateBinaryOp(code, i)) {
                                out.append('-');
                                edits++;
                                continue;
                            }
                        }
                    }

                    // 4. - -> +
                    // if (type == MutatorType.AOR_MINUS_TO_PLUS && c == '-') {
                    //     if (next != '-' && next != '=' && next != '>' && shouldMutateBinaryOp(code, i)) {
                    //         out.append('+');
                    //         edits++;
                    //         continue;
                    //     }
                    // }
                    // Relational
                    // 5. < -> <=
                    // if (type == MutatorType.ROR_LT_TO_LE && c == '<' && next != '=' && next != '<') {
                    //     out.append("<=");
                    //     edits++;
                    //     continue;
                    // }

                    // 6. <= -> <
                    // if (type == MutatorType.ROR_LE_TO_LT && c == '<' && next == '=') {
                    //     out.append('<');
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }

                    // 7. > -> >=
                    // if (type == MutatorType.ROR_GT_TO_GE && c == '>' && next != '=' && next != '>') {
                    //     out.append(">=");
                    //     edits++;
                    //     continue;
                    // }

                    // 8. >= -> >
                    // if (type == MutatorType.ROR_GE_TO_GT && c == '>' && next == '=') {
                    //     out.append('>');
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }
                    // Equality
                    // 9. == -> !=
                    // if (type == MutatorType.EQ_NEGATE && c == '=' && next == '=') {
                    //     out.append("!=");
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }

                    // 10. Equality: != -> ==
                    // if (type == MutatorType.EQ_NEGATE && c == '!' && next == '=') {
                    //     out.append("==");
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }
                    // logical
                    // 11. && -> ||
                    // if (type == MutatorType.LOGICAL_AND_TO_OR && c == '&' && next == '&') {
                    //     out.append("||");
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }

                    // 12. || -> &&
                    // if (type == MutatorType.LOGICAL_OR_TO_AND && c == '|' && next == '|') {
                    //     out.append("&&");
                    //     i++;
                    //     edits++;
                    //     continue;
                    // }

                    out.append(c);
                }

                case LINE_COMMENT -> {
                    out.append(c);
                    if (c == '\n') state = State.CODE;
                }

                case BLOCK_COMMENT -> {
                    out.append(c);
                    if (blockClose.length >= 2 && c == blockClose[0] && next == blockClose[1]) {
                        out.append(next);
                        i++;
                        state = State.CODE;
                    }
                }

                case STRING_DOUBLE -> {
                    out.append(c);
                    if (escape.length >= 1 && c == escape[0] && next != '\0') {
                        out.append(next);
                        i++;
                        continue;
                    }
                    if (doubleQuote.length >= 1 && c == doubleQuote[0]) state = State.CODE;
                }

                case STRING_SINGLE -> {
                    out.append(c);
                    if (escape.length >= 1 && c == escape[0] && next != '\0') {
                        out.append(next);
                        i++;
                        continue;
                    }
                    if (singleQuote.length >= 1 && c == singleQuote[0]) state = State.CODE;
                }
            }
        }

        return out.toString();
    }

    // binary operator check
    private static boolean shouldMutateBinaryOp(String code, int opIndex) {
        int left = opIndex - 1;
        while (left >= 0 && Character.isWhitespace(code.charAt(left))) left--;
        if (left < 0) return false;

        int right = opIndex + 1;
        while (right < code.length() && Character.isWhitespace(code.charAt(right))) right++;
        if (right >= code.length()) return false;

        char l = code.charAt(left);
        char r = code.charAt(right);

        // Avoid obvious unary contexts
        if ("(=,{[;:+-*/%&|^!<>".indexOf(l) >= 0) return false;

        if (!isLikelyOperandChar(l)) return false;
        if (!isLikelyOperandChar(r) && r != '(') return false;

        return true;
    }

    private static boolean isLikelyOperandChar(char c) {
        return Character.isJavaIdentifierPart(c) || Character.isDigit(c) || c == ']' || c == ')' || c == '.';
    }

    private static boolean shouldMutateRacketPlusToken(String code, int plusIndex) {
        char prev = (plusIndex - 1 >= 0) ? code.charAt(plusIndex - 1) : '\0';
        char next = (plusIndex + 1 < code.length()) ? code.charAt(plusIndex + 1) : '\0';

        if (Character.isDigit(next) || next == '.') return false;

        return isRacketDelimiter(prev) && isRacketDelimiter(next);
    }

    private static boolean isRacketDelimiter(char c) {
        if (c == '\0') return true;
        if (Character.isWhitespace(c)) return true;
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }
}
