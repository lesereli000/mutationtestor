package mutationtester.model;

import mutationtester.core.Language;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Mutator {

    public enum MutatorType {
        // Arithmetic Operator Replacement
        AOR_PLUS_TO_MINUS,
        AOR_MINUS_TO_PLUS,
        AOR_MULTIPLY_TO_DIVIDE,
        AOR_DIVIDE_TO_MULTIPLY,
        AOR_MODULO_TO_MULTIPLY,
        AOR_FLOOR_DIV_TO_DIV,   // Python: floor division // → regular division /
        AOR_POWER_TO_MULTIPLY,  // Python: exponentiation ** → multiply *
        INVERT_NEGS,            // Python: logical not x → x (removes `not`)
        NULL_RETURNS,           // Python: return <expr> → return None

        //Bitwise Operator Replacement
        BOR_AND_TO_OR,
        BOR_OR_TO_AND,

        // Relational Operator Replacement
        ROR_LT_TO_LE,
        ROR_LE_TO_LT,
        ROR_GT_TO_GE,
        ROR_GE_TO_GT,

        ROR_GT_TO_LT,
        ROR_GT_TO_GTE,
        ROR_LT_TO_GT,
        ROR_LT_TO_LTE,
        ROR_GTE_TO_GT,
        ROR_GTE_TO_LT,
        ROR_LTE_TO_LT,
        ROR_LTE_TO_GT,
        ROR_EQ_TO_NE,
        ROR_NE_TO_EQ,

        // Logical Connector Replacement
        LCR_AND_TO_OR,          // && to ||
        LCR_OR_TO_AND,          // || to &&

        EQ_NEGATE, // switch between == and !=
        LOGICAL_AND_TO_OR, // && to || (or language equivalents)
        LOGICAL_OR_TO_AND, // || to && (or language equivalents)
        TYPE_INT_TO_DOUBLE,
        TYPE_INT_TO_FLOAT,
        TYPE_DOUBLE_TO_INT,
        TYPE_FLOAT_TO_INT,
        TYPE_INT_TO_LONG,
        TYPE_LONG_TO_INT,
        TYPE_LONG_TO_DOUBLE,
        TYPE_LONG_TO_FLOAT,
        TYPE_DOUBLE_TO_LONG,
        TYPE_FLOAT_TO_LONG,
        // INC_TO_DEC, // ++ to --
        // DEC_TO_INC， // -- to ++

        CONDITIONAL_TRUE,
        CONDITIONAL_FALSE,
        NEGATE_CONDITIONALS,
        STATEMENT_MUTATION,
        STRING_MUTATION_TO_NOT_NULL,
        STRING_MUTATION_TO_NOT_EMPTY,

        REMOVE_INCREMENT, // remove-- or ++

        // Numeric Literal Type mutations for JavaScript
        NLT_INTEGER_TO_FLOAT, // 42 → 42.0
        NLT_INTEGER_TO_EXPONENTIAL, // 42 → 4.2e1
        NLT_FLOAT_TO_INTEGER, // 3.14 → 3
        NLT_FLOAT_TO_EXPONENTIAL, // 3.14 → 3.14e0
        NLT_EXPONENTIAL_TO_INTEGER, // 1e10 → 10000000000
        NLT_EXPONENTIAL_TO_FLOAT, // 1e10 → 10000000000.0

        // Return Value Mutations for JavaScript
        RVM_TRUE_TO_FALSE,
        RVM_FALSE_TO_TRUE,
        RVM_ZERO_TO_ONE,
        RVM_NONZERO_TO_ZERO,



        CUSTOM  // spec-driven branch; config must also set custom_mutation_spec=<path>

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
        Language language;
        try {
            language = new Language(inputPath.getFileName().toString());
        } catch (Exception e) {
            System.out.println(e.getMessage());
            return code;
        }

        boolean isRacket = inputPath.getFileName().toString().toLowerCase().endsWith(".rkt");

        if (isRacket && type == MutatorType.NEGATE_CONDITIONALS) {
            return mutateRacketNegateConditionals(code);
        }

        // return mutateOutsideStringsAndComments(code, type, 1, language, isRacket);
        return mutateOutsideStringsAndComments(code, type, Integer.MAX_VALUE, language, isRacket);
    }

    public static String mutateOutsideStringsAndComments(String code, MutatorType type, int maxEdits, Language language,
            boolean isRacket) {
        StringBuilder out = new StringBuilder(code.length());
        State state = State.CODE;
        int edits = 0;

        char[] lineComment = language.getSyntax(Language.LINE_COMMENT);
        char[] blockOpen = language.getSyntax(Language.BLOCK_COMMENT_OPEN);
        char[] blockClose = language.getSyntax(Language.BLOCK_COMMENT_CLOSE);
        char[] doubleQuote = language.getSyntax(Language.DOUBLE_STRING);
        char[] singleQuote = language.getSyntax(Language.SINGLE_STRING);
        char[] escape = language.getSyntax(Language.ESCAPE_CHARACTER);
        char[] logicalAnd = language.getSyntax(Language.LOGICAL_AND);
        char[] logicalOr = language.getSyntax(Language.LOGICAL_OR);

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);
            char next = (i + 1 < code.length()) ? code.charAt(i + 1) : '\0';
            char prev = (i - 1 < 0) ? '\0' : code.charAt(i - 1);

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

                    // // Equality
                    // //Racket specific mutations
                    // // equal to not-equal
                    // if ((type == MutatorType.EQ_TO_NEQ) && isRacket) {
                    // if (matchesAtPosition(code, i, "equal?")) {
                    // out.append("not-equal?");
                    // i += "equal?".length() - 1;
                    // edits++;
                    // continue;
                    // }
                    // }

                    // // not equal to equal
                    // if ((type == MutatorType.NEQ_TO_EQ) && isRacket) {
                    // if (matchesAtPosition(code, i, "not-equal?")) {
                    // out.append("equal?");
                    // i += "not-equal?".length() - 1;
                    // edits++;
                    // continue;
                    // }
                    // }

                    // // For JS:
                    // // === to !==
                    // if ((type == MutatorType.EQ_TO_NEQ) && c == '=' && next == '=' && next2 ==
                    // '=') {
                    // String fileName = "";
                    // if (equalTo.length == 3) {
                    // out.append("!==");
                    // i += 2;
                    // edits++;
                    // continue;
                    // }
                    // }

                    // // !== to ===
                    // if ((type == MutatorType.NEQ_TO_EQ) && c == '!' && next == '=' && next2 ==
                    // '=') {
                    // if (notEqual.length == 3) {
                    // out.append("===");
                    // i += 2;
                    // edits++;
                    // continue;
                    // }
                    // }

                    // // C-style languages:
                    // // == to !=
                    // if ((type == MutatorType.EQ_TO_NEQ) && c == '=' && next == '=' && next2 !=
                    // '=') {
                    // if (equalTo.length == 2 && !isRacket) {
                    // out.append("!=");
                    // i += 1;
                    // edits++;
                    // continue;
                    // }
                    // }

                    // // != to ==
                    // if ((type == MutatorType.NEQ_TO_EQ) && c == '!' && next == '=' && next2 !=
                    // '=') {
                    // if (notEqual.length == 2 && !isRacket) {
                    // out.append("==");
                    // i += 1;
                    // edits++;
                    // continue;
                    // }
                    // }

                    // Logical && <-> || (or language-specific equivalents)
                    if (tryMutateLogicalOperators(code, type, i, logicalAnd, logicalOr, out)) {
                        i += (type == MutatorType.LOGICAL_AND_TO_OR ? logicalAnd.length : logicalOr.length) - 1;
                        edits++;
                        continue;
                    }

                    if (!isRacket) {
                        if (type == MutatorType.TYPE_INT_TO_DOUBLE && matchesWord(code, i, "int")) {
                            out.append("double");
                            i += "int".length() - 1;
                            edits++;
                            continue;
                        }

                        if (type == MutatorType.TYPE_INT_TO_FLOAT && matchesWord(code, i, "int")) {
                            out.append("float");
                            i += "int".length() - 1;
                            edits++;
                            continue;
                        }

                        if (type == MutatorType.TYPE_DOUBLE_TO_INT && matchesWord(code, i, "double")) {
                            out.append("int");
                            i += "double".length() - 1;
                            edits++;
                            continue;
                        }

                        if (type == MutatorType.TYPE_FLOAT_TO_INT && matchesWord(code, i, "float")) {
                            out.append("int");
                            i += "float".length() - 1;
                            edits++;
                            continue;
                        }
                    }

                    // Mutation cases
                    // math:
                    // 1. ++ -> --
                    // if (type == MutatorType.INC_TO_DEC && c == '+' && next == '+') {
                    // out.append("--");
                    // i++;
                    // edits++;
                    // continue;
                    // }

                    // 2. -- -> ++
                    // if (type == MutatorType.DEC_TO_INC && c == '-' && next == '-') {
                    // out.append("++");
                    // i++;
                    // edits++;
                    // continue;
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
                    if (type == MutatorType.AOR_MINUS_TO_PLUS && c == '-') {
                        if (isRacket) {
                            if (shouldMutateRacketMinusToken(code, i)) {
                                out.append('+');
                                edits++;
                                continue;
                            }
                        } else {
                            if (next != '-' && next != '=' && next != '>' && shouldMutateBinaryOp(code, i)) {
                                out.append('+');
                                edits++;
                                continue;
                            }
                        }
                    }

                    // AOR: * -> /
                    if (type == MutatorType.AOR_MULTIPLY_TO_DIVIDE && c == '*') {
                        if (isRacket) {
                            if (shouldMutateRacketMultiplyToken(code, i)) {
                                out.append('/');
                                edits++;
                                continue;
                            }
                        } else {
                            if (shouldMutateBinaryOp(code, i)) {
                                out.append('/');
                                edits++;
                                continue;
                            }
                        }
                    }

// AOR: / -> *
                    if (type == MutatorType.AOR_DIVIDE_TO_MULTIPLY && c == '/') {
                        if (isRacket) {
                            if (shouldMutateRacketDivideToken(code, i)) {
                                out.append('*');
                                edits++;
                                continue;
                            }
                        } else {
                            if (shouldMutateBinaryOp(code, i)) {
                                out.append('*');
                                edits++;
                                continue;
                            }
                        }
                    }
                    // Relational
                    // 5. < -> <=
                    // if (type == MutatorType.ROR_LT_TO_LE && c == '<' && next != '=' && next !=
                    // '<') {
                    // out.append("<=");
                    // edits++;
                    // continue;
                    // }

                    // 6. <= -> <
                    // if (type == MutatorType.ROR_LE_TO_LT && c == '<' && next == '=') {
                    // out.append('<');
                    // i++;
                    // edits++;
                    // continue;
                    // }

                    // 7. > -> >=
                    if (type == MutatorType.ROR_GT_TO_GE && c == '>' && next != '=' && next != '>' && next != '?'
                            && prev != '>' && prev != '\\' && prev != '=') {
                        out.append(">=");
                        edits++;
                        continue;
                    }

                    // 8. >= -> >
                    if (type == MutatorType.ROR_GE_TO_GT && c == '>' && next == '=') {
                        out.append('>');
                        i++;
                        edits++;
                        continue;
                    }

                    // Logical && <-> || (or language-specific equivalents)
                    if (tryMutateLogicalOperators(code, type, i, logicalAnd, logicalOr, out)) {
                        i += (type == MutatorType.LOGICAL_AND_TO_OR ? logicalAnd.length : logicalOr.length) - 1;
                        edits++;
                        continue;
                    }

                    out.append(c);
                }

                case LINE_COMMENT -> {
                    out.append(c);
                    if (c == '\n')
                        state = State.CODE;
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
                    if (doubleQuote.length >= 1 && c == doubleQuote[0])
                        state = State.CODE;
                }

                case STRING_SINGLE -> {
                    out.append(c);
                    if (escape.length >= 1 && c == escape[0] && next != '\0') {
                        out.append(next);
                        i++;
                        continue;
                    }
                    if (singleQuote.length >= 1 && c == singleQuote[0])
                        state = State.CODE;
                }
            }
        }

        return out.toString();
    }

    private static boolean shouldMutateRacketMultiplyToken(String code, int index) {
        char prev = (index - 1 >= 0) ? code.charAt(index - 1) : '\0';
        char next = (index + 1 < code.length()) ? code.charAt(index + 1) : '\0';
        return isRacketDelimiter(prev) && isRacketDelimiter(next);
    }

    private static boolean shouldMutateRacketDivideToken(String code, int index) {
        char prev = (index - 1 >= 0) ? code.charAt(index - 1) : '\0';
        char next = (index + 1 < code.length()) ? code.charAt(index + 1) : '\0';
        // Don't mutate rational literals like 3/2 (digit before slash)
        if (Character.isDigit(prev))
            return false;
        return isRacketDelimiter(prev) && isRacketDelimiter(next);
    }

    /**
     * Wraps every (if condition ...) condition with (not ...) in Racket code.
     * Handles paren-expression conditions and simple-token conditions.
     * Skips line comments (;) and block comments (#| |#).
     */
    private static String mutateRacketNegateConditionals(String code) {
        StringBuilder out = new StringBuilder(code.length() + 64);
        int i = 0;
        while (i < code.length()) {
            char c = code.charAt(i);

            // Skip line comments
            if (c == ';') {
                while (i < code.length() && code.charAt(i) != '\n')
                    out.append(code.charAt(i++));
                continue;
            }
            // Skip block comments #| ... |#
            if (c == '#' && i + 1 < code.length() && code.charAt(i + 1) == '|') {
                out.append(c);
                i++;
                while (i < code.length()) {
                    char cc = code.charAt(i);
                    out.append(cc);
                    if (cc == '|' && i + 1 < code.length() && code.charAt(i + 1) == '#') {
                        out.append(code.charAt(i + 1));
                        i += 2;
                        break;
                    }
                    i++;
                }
                continue;
            }
            // Detect "(if " — paren followed by "if" token followed by delimiter
            if (c == '(' && i + 3 <= code.length() && code.startsWith("if", i + 1)) {
                int afterIf = i + 3;
                boolean ifTokenEnd = afterIf >= code.length() || isRacketDelimiter(code.charAt(afterIf));
                if (ifTokenEnd) {
                    // Consume "(if"
                    out.append("(if");
                    i = afterIf;
                    // Copy whitespace between "if" and condition
                    while (i < code.length() && Character.isWhitespace(code.charAt(i))) {
                        out.append(code.charAt(i++));
                    }
                    if (i >= code.length()) continue;
                    // Insert "(not " before condition
                    out.append("(not ");
                    // Copy the condition expression
                    char condStart = code.charAt(i);
                    if (condStart == '(') {
                        // Paren expression: copy until the matching closing paren
                        int depth = 0;
                        while (i < code.length()) {
                            char cc = code.charAt(i++);
                            out.append(cc);
                            if (cc == '(') depth++;
                            else if (cc == ')') { depth--; if (depth == 0) break; }
                        }
                    } else {
                        // Simple token: copy until whitespace or closing paren
                        while (i < code.length()) {
                            char cc = code.charAt(i);
                            if (Character.isWhitespace(cc) || cc == ')') break;
                            out.append(cc);
                            i++;
                        }
                    }
                    // Close the "(not"
                    out.append(')');
                    continue;
                }
            }

            out.append(c);
            i++;
        }
        return out.toString();
    }


    // binary operator check
    private static boolean shouldMutateBinaryOp(String code, int opIndex) {
        int left = opIndex - 1;
        while (left >= 0 && Character.isWhitespace(code.charAt(left)))
            left--;
        if (left < 0)
            return false;

        int right = opIndex + 1;
        while (right < code.length() && Character.isWhitespace(code.charAt(right)))
            right++;
        if (right >= code.length())
            return false;

        char l = code.charAt(left);
        char r = code.charAt(right);

        // Avoid obvious unary contexts
        if ("(=,{[;:+-*/%&|^!<>".indexOf(l) >= 0)
            return false;

        if (!isLikelyOperandChar(l))
            return false;
        if (!isLikelyOperandChar(r) && r != '(')
            return false;

        return true;
    }

    private static boolean isLikelyOperandChar(char c) {
        return Character.isJavaIdentifierPart(c) || Character.isDigit(c) || c == ']' || c == ')' || c == '.';
    }

    private static boolean shouldMutateRacketPlusToken(String code, int plusIndex) {
        char prev = (plusIndex - 1 >= 0) ? code.charAt(plusIndex - 1) : '\0';
        char next = (plusIndex + 1 < code.length()) ? code.charAt(plusIndex + 1) : '\0';

        if (Character.isDigit(next) || next == '.')
            return false;

        return isRacketDelimiter(prev) && isRacketDelimiter(next);
    }

    private static boolean shouldMutateRacketMinusToken(String code, int minusIndex) {
        char prev = (minusIndex - 1 >= 0) ? code.charAt(minusIndex - 1) : '\0';
        char next = (minusIndex + 1 < code.length()) ? code.charAt(minusIndex + 1) : '\0';

        if (Character.isDigit(next) || next == '.')
            return false;

        return isRacketDelimiter(prev) && isRacketDelimiter(next);
    }

    private static boolean isRacketDelimiter(char c) {
        if (c == '\0')
            return true;
        if (Character.isWhitespace(c))
            return true;
        return c == '(' || c == ')' || c == '[' || c == ']' || c == '{' || c == '}';
    }

    private static boolean matchesAt(String code, int index, char[] token) {
        if (token.length == 0)
            return false;
        if (index + token.length > code.length())
            return false;
        for (int j = 0; j < token.length; j++) {
            if (code.charAt(index + j) != token[j])
                return false;
        }
        return true;
    }

    private static void appendToken(StringBuilder out, char[] token) {
        out.append(token, 0, token.length);
    }

    private static boolean tryMutateLogicalOperators(String code, MutatorType type, int index, char[] logicalAnd,
            char[] logicalOr, StringBuilder out) {
        if (logicalAnd.length == 0 || logicalOr.length == 0)
            return false;

        if (type == MutatorType.LOGICAL_AND_TO_OR && matchesTokenWithBoundaries(code, index, logicalAnd)) {
            appendToken(out, logicalOr);
            return true;
        }

        if (type == MutatorType.LOGICAL_OR_TO_AND && matchesTokenWithBoundaries(code, index, logicalOr)) {
            appendToken(out, logicalAnd);
            return true;
        }

        return false;
    }

    private static boolean matchesTokenWithBoundaries(String code, int index, char[] token) {
        if (!matchesAt(code, index, token))
            return false;
        if (!needsWordBoundaryCheck(token))
            return true;
        int len = token.length;
        char before = index > 0 ? code.charAt(index - 1) : '\0';
        char after = (index + len < code.length()) ? code.charAt(index + len) : '\0';
        boolean beforeOk = before == '\0' || !isIdentifierLike(before);
        boolean afterOk = after == '\0' || !isIdentifierLike(after);
        return beforeOk && afterOk;
    }

    private static boolean needsWordBoundaryCheck(char[] token) {
        for (char ch : token) {
            if (Character.isLetterOrDigit(ch) || ch == '_' || ch == '?' || ch == '!') {
                return true;
            }
        }
        return false;
    }

    private static boolean isIdentifierLike(char ch) {
        return Character.isLetterOrDigit(ch) || ch == '_' || ch == '?' || ch == '!' || ch == '-' || ch == '.';
    }

    private static boolean matchesWord(String code, int index, String word) {
        if (!code.startsWith(word, index))
            return false;
        char before = index > 0 ? code.charAt(index - 1) : '\0';
        char after = index + word.length() < code.length() ? code.charAt(index + word.length()) : '\0';
        boolean beforeOk = before == '\0' || !isIdentifierLike(before);
        boolean afterOk = after == '\0' || !isIdentifierLike(after);
        return beforeOk && afterOk;
    }
}
