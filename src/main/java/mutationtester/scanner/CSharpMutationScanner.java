package mutationtester.scanner;

import mutationtester.*;
import mutationtester.model.Mutation;
import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import mutationtester.msl.MSLScannerAdapter;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CSharpMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        sourceCode = removeBom(sourceCode);

        if (isTypeSwapMutation(type)) {
            return scanTypeSwapMutations(file, sourceCode, type);
        }
        if (isReturnValueMutation(type)) {
            return scanReturnValueMutations(file, sourceCode, type);
        }
        if (type == Mutator.MutatorType.VML_VOID_METHOD_CALL) {
            return scanVoidMethodCallMutations(file, sourceCode);
        }
        if (type == Mutator.MutatorType.CONSTRUCTOR_CALLS) {
            return scanConstructorCallMutations(file, sourceCode);
        }
        if (type == Mutator.MutatorType.NON_VOID_METHOD_CALLS) {
            return scanNonVoidMethodCallMutations(file, sourceCode);
        }
        if (type == Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT
                || type == Mutator.MutatorType.AOR_DECREMENT_TO_INCREMENT) {
            return scanIncrementSwapMutations(file, sourceCode, type);
        }
        if (type == Mutator.MutatorType.INEG_INVERT_NEGATIVE) {
            return scanInvertNegativeMutations(file, sourceCode);
        }
        if (type == Mutator.MutatorType.REMOVE_INCREMENT) {
            return scanRemoveIncrementMutations(file, sourceCode);
        }
        if (type == Mutator.MutatorType.STATEMENT_MUTATION) {
            return scanStatementRemovalMutations(file, sourceCode);
        }
        if (type == Mutator.MutatorType.STRING_MUTATION_TO_NOT_NULL
                || type == Mutator.MutatorType.STRING_MUTATION_TO_NOT_EMPTY) {
            return scanStringIsNullOrEmptyMutations(file, sourceCode, type);
        }
        if (type == Mutator.MutatorType.CONDITIONAL_TRUE || type == Mutator.MutatorType.CONDITIONAL_FALSE) {
            return scanConditionalMutations(file, sourceCode, type);
        }
        if (isRemoveConditionalMutation(type)) {
            return scanRemoveConditionalMutations(file, sourceCode, type);
        }

        CharStream input = CharStreams.fromString(sourceCode);
        CSharpLexer lexer = new CSharpLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CSharpParser parser = new CSharpParser(tokens);

        ParseTree tree = parser.prog();

        CSharpMutationVisitor visitor = new CSharpMutationVisitor(file, type);
        visitor.visit(tree);

        return visitor.getMutationPoints();
    }

    private static List<MutationPoint> scanConditionalMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        String replacement = type == Mutator.MutatorType.CONDITIONAL_TRUE ? "true" : "false";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            addParenthesizedCondition(points, file, line, i + 1, type, replacement, "if");
            addParenthesizedCondition(points, file, line, i + 1, type, replacement, "while");
            addTernaryCondition(points, file, lines, i, type, replacement);
        }

        return points;
    }

    private static List<MutationPoint> scanRemoveConditionalMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        String replacement = removeConditionalReplacement(type);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            addRemoveConditionalPoint(points, file, line, i + 1, type, replacement, "if");
            addRemoveConditionalPoint(points, file, line, i + 1, type, replacement, "while");
            addRemoveConditionalTernaryPoint(points, file, lines, i, type, replacement);
        }

        return points;
    }

    private static void addRemoveConditionalPoint(List<MutationPoint> points, File file, String line, int lineNumber,
                                                  Mutator.MutatorType type, String replacement, String keyword) {
        int searchFrom = 0;

        while (searchFrom < line.length()) {
            int keywordIndex = findKeywordOutsideStringOrComment(line, keyword, searchFrom);
            if (keywordIndex < 0) {
                return;
            }

            int openParen = line.indexOf('(', keywordIndex + keyword.length());
            if (openParen < 0 || isInStringOrComment(line, openParen)) {
                searchFrom = keywordIndex + keyword.length();
                continue;
            }

            int closeParen = findMatchingParen(line, openParen);
            if (closeParen < 0) {
                searchFrom = openParen + 1;
                continue;
            }

            String condition = line.substring(openParen + 1, closeParen).trim();
            int conditionColumn = line.indexOf(condition, openParen + 1);

            if (isValidConditionText(condition) && removeConditionalMatches(type, condition)) {
                points.add(new MutationPoint(file, lineNumber, conditionColumn, condition, replacement, type));
            }

            searchFrom = closeParen + 1;
        }
    }

    private static void addRemoveConditionalTernaryPoint(List<MutationPoint> points, File file, String[] lines, int lineIndex,
                                                         Mutator.MutatorType type, String replacement) {
        String line = lines[lineIndex];
        int questionMark = findQuestionMarkOutsideStringOrComment(line);
        ConditionSpan conditionSpan = null;

        if (questionMark >= 0) {
            conditionSpan = extractInlineTernaryCondition(line, questionMark);
        } else if (lineIndex + 1 < lines.length && startsWithQuestionMark(lines[lineIndex + 1])) {
            conditionSpan = extractTrailingTernaryCondition(line);
        }

        if (conditionSpan == null) {
            return;
        }

        String condition = conditionSpan.text().trim();
        if (isValidConditionText(condition) && removeConditionalMatches(type, condition)) {
            points.add(new MutationPoint(
                    file,
                    lineIndex + 1,
                    conditionSpan.startColumn(),
                    condition,
                    replacement,
                    type
            ));
        }
    }

    private static boolean isRemoveConditionalMutation(Mutator.MutatorType type) {
        return type == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                || type == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE
                || type == Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF
                || type == Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_ELSE;
    }

    private static String removeConditionalReplacement(Mutator.MutatorType type) {
        return (type == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                || type == Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF) ? "true" : "false";
    }

    private static boolean removeConditionalMatches(Mutator.MutatorType type, String condition) {
        boolean equality = containsOperatorOutsideStringOrComment(condition, "==")
                || containsOperatorOutsideStringOrComment(condition, "!=");
        boolean order = containsOperatorOutsideStringOrComment(condition, "<=")
                || containsOperatorOutsideStringOrComment(condition, ">=")
                || containsStandaloneOrderOperator(condition, '<')
                || containsStandaloneOrderOperator(condition, '>');

        if (type == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                || type == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE) {
            return equality;
        }

        return order;
    }

    private static boolean containsOperatorOutsideStringOrComment(String text, String operator) {
        int index = text.indexOf(operator);
        while (index >= 0) {
            if (!isInStringOrComment(text, index)) {
                return true;
            }
            index = text.indexOf(operator, index + operator.length());
        }
        return false;
    }

    private static boolean containsStandaloneOrderOperator(String text, char operator) {
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != operator || isInStringOrComment(text, i)) {
                continue;
            }

            char prev = i > 0 ? text.charAt(i - 1) : '\0';
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';
            if (prev != operator && next != operator && next != '=') {
                return true;
            }
        }
        return false;
    }

    private static void addParenthesizedCondition(List<MutationPoint> points, File file, String line, int lineNumber,
                                                  Mutator.MutatorType type, String replacement, String keyword) {
        int searchFrom = 0;

        while (searchFrom < line.length()) {
            int keywordIndex = findKeywordOutsideStringOrComment(line, keyword, searchFrom);
            if (keywordIndex < 0) {
                return;
            }

            int openParen = line.indexOf('(', keywordIndex + keyword.length());
            if (openParen < 0 || isInStringOrComment(line, openParen)) {
                searchFrom = keywordIndex + keyword.length();
                continue;
            }

            int closeParen = findMatchingParen(line, openParen);
            if (closeParen < 0) {
                searchFrom = openParen + 1;
                continue;
            }

            String condition = line.substring(openParen + 1, closeParen).trim();
            int conditionColumn = line.indexOf(condition, openParen + 1);

            if (isValidConditionText(condition)) {
                points.add(new MutationPoint(file, lineNumber, conditionColumn, condition, replacement, type));
            }

            searchFrom = closeParen + 1;
        }
    }

    private static void addTernaryCondition(List<MutationPoint> points, File file, String[] lines, int lineIndex,
                                            Mutator.MutatorType type, String replacement) {
        String line = lines[lineIndex];
        int questionMark = findQuestionMarkOutsideStringOrComment(line);
        if (questionMark >= 0) {
            addConditionPoint(points, file, lineIndex + 1, type, replacement,
                    extractInlineTernaryCondition(line, questionMark));
            return;
        }

        if (lineIndex + 1 < lines.length && startsWithQuestionMark(lines[lineIndex + 1])) {
            addConditionPoint(points, file, lineIndex + 1, type, replacement,
                    extractTrailingTernaryCondition(line));
        }
    }

    private static void addConditionPoint(List<MutationPoint> points, File file, int lineNumber,
                                          Mutator.MutatorType type, String replacement, ConditionSpan conditionSpan) {
        if (conditionSpan == null) {
            return;
        }

        String condition = conditionSpan.text().trim();
        if (isValidConditionText(condition)) {
            points.add(new MutationPoint(
                    file,
                    lineNumber,
                    conditionSpan.startColumn(),
                    condition,
                    replacement,
                    type
            ));
        }
    }

    private static ConditionSpan extractInlineTernaryCondition(String line, int questionMark) {
        String prefix = line.substring(0, questionMark);
        return buildConditionSpan(line, prefix, findConditionStart(prefix));
    }

    private static ConditionSpan extractTrailingTernaryCondition(String line) {
        return buildConditionSpan(line, line, findConditionStart(line));
    }

    private static ConditionSpan buildConditionSpan(String fullLine, String candidateSource, int start) {
        if (start < 0 || start >= candidateSource.length()) {
            return null;
        }

        String candidate = candidateSource.substring(start).trim();
        if (candidate.isEmpty()) {
            return null;
        }

        int column = fullLine.indexOf(candidate, start);
        if (column < 0) {
            return null;
        }

        return new ConditionSpan(candidate, column);
    }

    private static int findConditionStart(String text) {
        int start = skipLeadingTrivia(text, 0);

        if (text.startsWith("=>", start)) {
            return skipLeadingTrivia(text, start + 2);
        }

        if (start < text.length() && text.charAt(start) == ':') {
            return skipLeadingTrivia(text, start + 1);
        }

        if (text.startsWith("return", start)) {
            int next = start + "return".length();
            if (next >= text.length() || Character.isWhitespace(text.charAt(next))) {
                return skipLeadingTrivia(text, next);
            }
        }

        return start;
    }

    private static int skipLeadingTrivia(String text, int start) {
        int index = start;
        while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int findKeywordOutsideStringOrComment(String line, String keyword, int from) {
        int index = line.indexOf(keyword, from);

        while (index >= 0) {
            boolean beforeOk = index == 0 || !isIdentifierChar(line.charAt(index - 1));
            int afterIndex = index + keyword.length();
            boolean afterOk = afterIndex >= line.length() || !isIdentifierChar(line.charAt(afterIndex));

            if (beforeOk && afterOk && !isInStringOrComment(line, index)) {
                return index;
            }

            index = line.indexOf(keyword, index + keyword.length());
        }

        return -1;
    }

    private static int findMatchingParen(String line, int openParen) {
        int depth = 0;

        for (int i = openParen; i < line.length(); i++) {
            if (isInStringOrComment(line, i)) {
                continue;
            }

            char c = line.charAt(i);

            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }

        return -1;
    }

    private static int findQuestionMarkOutsideStringOrComment(String line) {
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '?' && isTernaryQuestionMark(line, i)) {
                return i;
            }
        }

        return -1;
    }

    private static boolean startsWithQuestionMark(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("?") && !trimmed.startsWith("??");
    }

    private static boolean isTernaryQuestionMark(String line, int index) {
        if (index < 0 || index >= line.length() || line.charAt(index) != '?' || isInStringOrComment(line, index)) {
            return false;
        }

        if ((index > 0 && line.charAt(index - 1) == '?')
                || (index + 1 < line.length() && line.charAt(index + 1) == '?')) {
            return false;
        }

        int prev = previousNonWhitespace(line, index - 1);
        if (prev < 0) {
            return false;
        }

        char prevChar = line.charAt(prev);
        if (!Character.isLetterOrDigit(prevChar)
                && prevChar != ')'
                && prevChar != ']'
                && prevChar != '}'
                && prevChar != '"'
                && prevChar != '\''
                && prevChar != '_') {
            return false;
        }

        return findMatchingTernaryColon(line, index) >= 0;
    }

    private static int findMatchingTernaryColon(String line, int questionMark) {
        int baseParenDepth = nestingDepth(line, questionMark, '(', ')');
        int baseBracketDepth = nestingDepth(line, questionMark, '[', ']');
        int baseBraceDepth = nestingDepth(line, questionMark, '{', '}');
        int nestedTernaryDepth = 0;
        int parenDepth = baseParenDepth;
        int bracketDepth = baseBracketDepth;
        int braceDepth = baseBraceDepth;

        for (int i = questionMark + 1; i < line.length(); i++) {
            if (isInStringOrComment(line, i)) {
                continue;
            }

            char c = line.charAt(i);
            if (c == '(') parenDepth++;
            else if (c == ')') parenDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
            else if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;

            if (parenDepth < baseParenDepth || bracketDepth < baseBracketDepth || braceDepth < baseBraceDepth) {
                return -1;
            }

            if (parenDepth == baseParenDepth && bracketDepth == baseBracketDepth && braceDepth == baseBraceDepth) {
                if (c == '?' && isTernaryQuestionMark(line, i)) {
                    nestedTernaryDepth++;
                } else if (c == ':') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == ':') {
                        i++;
                        continue;
                    }

                    if (nestedTernaryDepth == 0) {
                        return i;
                    }
                    nestedTernaryDepth--;
                }
            }
        }

        return -1;
    }

    private static int nestingDepth(String line, int endExclusive, char openChar, char closeChar) {
        int depth = 0;
        for (int i = 0; i < endExclusive; i++) {
            if (isInStringOrComment(line, i)) {
                continue;
            }

            char c = line.charAt(i);
            if (c == openChar) {
                depth++;
            } else if (c == closeChar && depth > 0) {
                depth--;
            }
        }
        return depth;
    }

    private static int previousNonWhitespace(String line, int index) {
        int i = index;
        while (i >= 0 && Character.isWhitespace(line.charAt(i))) {
            i--;
        }
        return i;
    }

    private static boolean isValidConditionText(String condition) {
        if (condition == null || condition.isBlank()) {
            return false;
        }

        String trimmed = condition.trim();

        if (trimmed.equals("true") || trimmed.equals("false")) {
            return false;
        }

        if (trimmed.endsWith(";") || trimmed.endsWith("{") || trimmed.endsWith("}") || trimmed.endsWith("=>")) {
            return false;
        }

        if (trimmed.startsWith("?") || trimmed.startsWith(":")) {
            return false;
        }

        return true;
    }

    private static boolean isReturnValueMutation(Mutator.MutatorType type) {
        return type == Mutator.MutatorType.RVM_TRUE_TO_FALSE
                || type == Mutator.MutatorType.RVM_FALSE_TO_TRUE
                || type == Mutator.MutatorType.RVM_ZERO_TO_ONE
                || type == Mutator.MutatorType.RVM_NONZERO_TO_ZERO
                || type == Mutator.MutatorType.RVM_OBJECT_TO_NULL
                || type == Mutator.MutatorType.RVM_PRIMITIVE_TO_ZERO
                || type == Mutator.MutatorType.RVM_STRING_TO_EMPTY
                || type == Mutator.MutatorType.EMPTY_RETURNS;
    }

    private static List<MutationPoint> scanReturnValueMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        String currentReturnType = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String detectedReturnType = detectMethodReturnType(line);
            if (detectedReturnType != null) {
                currentReturnType = detectedReturnType;
            }
            int searchFrom = 0;

            while (searchFrom < line.length()) {
                int returnIndex = findKeywordOutsideStringOrComment(line, "return", searchFrom);
                if (returnIndex < 0) {
                    break;
                }

                int expressionStart = skipLeadingTrivia(line, returnIndex + "return".length());
                if (expressionStart >= line.length() || line.charAt(expressionStart) == ';') {
                    searchFrom = returnIndex + "return".length();
                    continue;
                }

                int semicolon = findSemicolonOutsideStringOrComment(line, expressionStart);
                if (semicolon < 0) {
                    searchFrom = returnIndex + "return".length();
                    continue;
                }

                String expression = line.substring(expressionStart, semicolon).trim();
                int column = line.indexOf(expression, expressionStart);
                String replacement = getReturnValueReplacement(expression, type, currentReturnType);

                if (replacement != null && column >= 0) {
                    points.add(new MutationPoint(file, i + 1, column, expression, replacement, type));
                }

                searchFrom = semicolon + 1;
            }
        }

        return points;
    }

    private static String getReturnValueReplacement(String expression, Mutator.MutatorType type, String returnType) {
        String value = stripOuterParentheses(expression.trim());

        return switch (type) {
            case RVM_TRUE_TO_FALSE -> value.equals("true") ? "false" : null;
            case RVM_FALSE_TO_TRUE -> value.equals("false") ? "true" : null;
            case RVM_ZERO_TO_ONE -> isZeroLiteral(value) ? "1" : null;
            case RVM_NONZERO_TO_ZERO -> isNonZeroNumericLiteral(value) ? "0" : null;
            case RVM_PRIMITIVE_TO_ZERO -> isNonZeroNumericLiteral(value) ? "0" : null;
            case RVM_OBJECT_TO_NULL -> isReferenceReturnType(returnType) && isObjectLikeReturn(value) ? "null" : null;
            case RVM_STRING_TO_EMPTY -> isNonEmptyStringLiteral(value) ? "\"\"" : null;
            case EMPTY_RETURNS -> getEmptyReturnReplacement(value, returnType);
            default -> null;
        };
    }

    private static String getEmptyReturnReplacement(String value, String returnType) {
        if (returnType == null || returnType.isBlank() || returnType.equals("void")) {
            return null;
        }

        String normalizedType = returnType.replace("?", "").trim();
        String emptyValue = defaultValueForType(normalizedType, true);
        if (emptyValue == null || value.equals(emptyValue)) {
            return null;
        }

        if (normalizedType.equals("bool") && (value.equals("true") || value.equals("false"))) {
            return emptyValue;
        }
        if (isNumericType(normalizedType) && !isZeroLiteral(value)) {
            return emptyValue;
        }
        if (normalizedType.equals("string") && !value.equals("\"\"")) {
            return emptyValue;
        }
        if (isEmptyCollectionReturnType(normalizedType) && !isAlreadyEmptyCollection(value)) {
            return emptyValue;
        }

        return null;
    }

    private static String detectMethodReturnType(String line) {
        String trimmed = line.trim();
        Pattern methodPattern = Pattern.compile(
                "^(?:public|private|protected|internal|static|sealed|override|virtual|async|extern|new|partial|readonly|unsafe|\\s)+\\s*([A-Za-z_][A-Za-z0-9_<>?\\[\\]\\.]*|void)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*\\("
        );
        Matcher matcher = methodPattern.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static boolean isReferenceReturnType(String returnType) {
        if (returnType == null || returnType.isBlank()) {
            return false;
        }

        String normalized = returnType.replace("?", "").trim();
        return !normalized.equals("void")
                && !normalized.equals("bool")
                && !normalized.equals("byte")
                && !normalized.equals("sbyte")
                && !normalized.equals("short")
                && !normalized.equals("ushort")
                && !normalized.equals("int")
                && !normalized.equals("uint")
                && !normalized.equals("long")
                && !normalized.equals("ulong")
                && !normalized.equals("float")
                && !normalized.equals("double")
                && !normalized.equals("decimal")
                && !normalized.equals("char");
    }

    private static List<MutationPoint> scanVoidMethodCallMutations(File file, String sourceCode) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        Pattern invocationStatement = Pattern.compile(
                "^(\\s*)(await\\s+)?[A-Za-z_][A-Za-z0-9_\\.<>]*\\s*\\(.*\\)\\s*;\\s*$"
        );

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = invocationStatement.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            String statement = line.trim();
            if (startsWithControlKeyword(statement)) {
                continue;
            }

            int column = line.indexOf(statement);
            points.add(new MutationPoint(
                    file,
                    i + 1,
                    column,
                    statement,
                    ";",
                    Mutator.MutatorType.VML_VOID_METHOD_CALL,
                    "Void method call mutation"
            ));
        }

        return points;
    }

    private static List<MutationPoint> scanConstructorCallMutations(File file, String sourceCode) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int searchFrom = 0;

            while (searchFrom < line.length()) {
                int newIndex = findKeywordOutsideStringOrComment(line, "new", searchFrom);
                if (newIndex < 0) {
                    break;
                }

                int typeStart = skipLeadingTrivia(line, newIndex + "new".length());
                ConstructorCall call = readConstructorCall(line, newIndex, typeStart);
                if (call == null) {
                    searchFrom = newIndex + "new".length();
                    continue;
                }

                if (!isValueTypeConstructor(call.typeName())) {
                    points.add(new MutationPoint(
                            file,
                            i + 1,
                            newIndex,
                            call.text(),
                            "null",
                            Mutator.MutatorType.CONSTRUCTOR_CALLS,
                            "Constructor call mutation"
                    ));
                }

                searchFrom = newIndex + call.text().length();
            }
        }

        return points;
    }

    private static List<MutationPoint> scanNonVoidMethodCallMutations(File file, String sourceCode) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        String currentReturnType = "";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String detectedReturnType = detectMethodReturnType(line);
            if (detectedReturnType != null) {
                currentReturnType = detectedReturnType;
            }

            addReturnMethodCallMutation(points, file, line, i + 1, currentReturnType);
            addAssignmentMethodCallMutations(points, file, line, i + 1);
        }

        return points;
    }

    private static void addReturnMethodCallMutation(List<MutationPoint> points, File file, String line, int lineNumber,
                                                    String currentReturnType) {
        int returnIndex = findKeywordOutsideStringOrComment(line, "return", 0);
        if (returnIndex < 0) {
            return;
        }

        int expressionStart = skipLeadingTrivia(line, returnIndex + "return".length());
        int semicolon = findSemicolonOutsideStringOrComment(line, expressionStart);
        if (expressionStart >= line.length() || semicolon < 0) {
            return;
        }

        String expression = line.substring(expressionStart, semicolon).trim();
        MethodCall call = findSingleMethodCallExpression(line, expressionStart, semicolon);
        String replacement = defaultValueForType(currentReturnType, false);
        if (call != null && replacement != null && expression.equals(call.text())) {
            points.add(new MutationPoint(
                    file,
                    lineNumber,
                    call.column(),
                    call.text(),
                    replacement,
                    Mutator.MutatorType.NON_VOID_METHOD_CALLS,
                    "Non-void method call mutation"
            ));
        }
    }

    private static void addAssignmentMethodCallMutations(List<MutationPoint> points, File file, String line, int lineNumber) {
        Pattern declarationPattern = Pattern.compile(
                "\\b(?<type>[A-Za-z_][A-Za-z0-9_<>?\\[\\]\\.]*|bool|byte|sbyte|short|ushort|int|uint|long|ulong|float|double|decimal|char|string)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*=\\s*"
        );
        Matcher matcher = declarationPattern.matcher(line);

        while (matcher.find()) {
            if (isInStringOrComment(line, matcher.start())) {
                continue;
            }

            int expressionStart = matcher.end();
            int semicolon = findSemicolonOutsideStringOrComment(line, expressionStart);
            if (semicolon < 0) {
                continue;
            }

            MethodCall call = findSingleMethodCallExpression(line, expressionStart, semicolon);
            String replacement = defaultValueForType(matcher.group("type"), false);
            if (call != null && replacement != null) {
                points.add(new MutationPoint(
                        file,
                        lineNumber,
                        call.column(),
                        call.text(),
                        replacement,
                        Mutator.MutatorType.NON_VOID_METHOD_CALLS,
                        "Non-void method call mutation"
                ));
            }
        }
    }

    private static ConstructorCall readConstructorCall(String line, int newIndex, int typeStart) {
        if (typeStart >= line.length() || isInStringOrComment(line, newIndex)) {
            return null;
        }

        int typeEnd = typeStart;
        int genericDepth = 0;
        while (typeEnd < line.length()) {
            char c = line.charAt(typeEnd);
            if (c == '<') {
                genericDepth++;
            } else if (c == '>' && genericDepth > 0) {
                genericDepth--;
            } else if (genericDepth == 0 && !(Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '?' || c == '[' || c == ']')) {
                break;
            }
            typeEnd++;
        }

        if (typeEnd == typeStart) {
            return null;
        }

        int openParen = skipLeadingTrivia(line, typeEnd);
        if (openParen >= line.length() || line.charAt(openParen) != '(' || isInStringOrComment(line, openParen)) {
            return null;
        }

        int closeParen = findMatchingParen(line, openParen);
        if (closeParen < 0) {
            return null;
        }

        String text = line.substring(newIndex, closeParen + 1);
        String typeName = line.substring(typeStart, typeEnd);
        return new ConstructorCall(text, typeName);
    }

    private static MethodCall findSingleMethodCallExpression(String line, int expressionStart, int expressionEnd) {
        int start = skipLeadingTrivia(line, expressionStart);
        int end = expressionEnd;
        while (end > start && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }

        if (start >= end || line.startsWith("new ", start)) {
            return null;
        }

        int openParen = -1;
        for (int i = start; i < end; i++) {
            if (line.charAt(i) == '(' && !isInStringOrComment(line, i)) {
                openParen = i;
                break;
            }
        }

        if (openParen < 0) {
            return null;
        }

        int closeParen = findMatchingParen(line, openParen);
        if (closeParen != end - 1) {
            return null;
        }

        String callee = line.substring(start, openParen).trim();
        if (!callee.matches("^(await\\s+)?[A-Za-z_][A-Za-z0-9_\\.<>]*$") || startsWithControlKeyword(callee)) {
            return null;
        }

        return new MethodCall(line.substring(start, end), start);
    }

    private static List<MutationPoint> scanIncrementSwapMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        String original = type == Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT ? "++" : "--";
        String replacement = type == Mutator.MutatorType.AOR_INCREMENT_TO_DECREMENT ? "--" : "++";

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            for (int col = 0; col < line.length() - 1; col++) {
                if (isInStringOrComment(line, col)) {
                    continue;
                }

                if (line.startsWith(original, col)) {
                    points.add(new MutationPoint(file, i + 1, col, original, replacement, type));
                    col++;
                }
            }
        }

        return points;
    }

    private static List<MutationPoint> scanInvertNegativeMutations(File file, String sourceCode) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '-' || isInStringOrComment(line, col)) {
                    continue;
                }

                if (isUnaryMinus(line, col)) {
                    points.add(new MutationPoint(
                            file,
                            i + 1,
                            col,
                            "-",
                            "",
                            Mutator.MutatorType.INEG_INVERT_NEGATIVE
                    ));
                }
            }
        }

        return points;
    }

    private static List<MutationPoint> scanRemoveIncrementMutations(File file, String sourceCode) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            for (int col = 0; col < line.length() - 1; col++) {
                if (isInStringOrComment(line, col)) {
                    continue;
                }

                String two = line.substring(col, col + 2);

                if (two.equals("++")) {
                    points.add(new MutationPoint(
                            file,
                            i + 1,
                            col,
                            "++",
                            "__INC_NOOP__",
                            Mutator.MutatorType.REMOVE_INCREMENT
                    ));
                    col++;
                } else if (two.equals("--")) {
                    points.add(new MutationPoint(
                            file,
                            i + 1,
                            col,
                            "--",
                            "__DEC_NOOP__",
                            Mutator.MutatorType.REMOVE_INCREMENT
                    ));
                    col++;
                }
            }
        }

        return points;
    }

    private static List<MutationPoint> scanStatementRemovalMutations(File file, String sourceCode) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);
        Pattern invocationStatement = Pattern.compile(
                "^(\\s*)(await\\s+)?[A-Za-z_][A-Za-z0-9_\\.<>]*\\s*\\(.*\\)\\s*;\\s*$"
        );

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = invocationStatement.matcher(line);
            if (!matcher.matches()) {
                continue;
            }

            String statement = line.trim();
            int column = line.indexOf(statement);
            if (column < 0) {
                continue;
            }

            points.add(new MutationPoint(
                    file,
                    i + 1,
                    column,
                    statement,
                    ";",
                    Mutator.MutatorType.STATEMENT_MUTATION,
                    "Statement mutation"
            ));
        }

        return points;
    }

    private static List<MutationPoint> scanStringIsNullOrEmptyMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int searchFrom = 0;

            while (searchFrom < line.length()) {
                int callIndex = line.indexOf("string.IsNullOrEmpty", searchFrom);
                if (callIndex < 0) {
                    break;
                }

                if (isInStringOrComment(line, callIndex)) {
                    searchFrom = callIndex + "string.IsNullOrEmpty".length();
                    continue;
                }

                int openParen = line.indexOf('(', callIndex + "string.IsNullOrEmpty".length());
                if (openParen < 0 || isInStringOrComment(line, openParen)) {
                    searchFrom = callIndex + "string.IsNullOrEmpty".length();
                    continue;
                }

                int closeParen = findMatchingParen(line, openParen);
                if (closeParen < 0) {
                    searchFrom = openParen + 1;
                    continue;
                }

                String fullCall = line.substring(callIndex, closeParen + 1);
                String argument = line.substring(openParen + 1, closeParen).trim();
                String replacement = type == Mutator.MutatorType.STRING_MUTATION_TO_NOT_NULL
                        ? "(" + argument + " != null)"
                        : "(" + argument + " != \"\")";

                if (!argument.isEmpty()) {
                    points.add(new MutationPoint(
                            file,
                            i + 1,
                            callIndex,
                            fullCall,
                            replacement,
                            type,
                            "String mutation"
                    ));
                }

                searchFrom = closeParen + 1;
            }
        }

        return points;
    }

    private static int findSemicolonOutsideStringOrComment(String line, int from) {
        for (int i = from; i < line.length(); i++) {
            if (line.charAt(i) == ';' && !isInStringOrComment(line, i)) {
                return i;
            }
        }
        return -1;
    }

    private static String stripOuterParentheses(String expression) {
        String value = expression;
        while (value.startsWith("(") && value.endsWith(")") && findMatchingParen(value, 0) == value.length() - 1) {
            value = value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private static boolean isZeroLiteral(String value) {
        return value.matches("^[+-]?0+(\\.0+)?([fFdDmM])?$");
    }

    private static boolean isNonZeroNumericLiteral(String value) {
        if (!value.matches("^[+-]?(\\d+\\.?\\d*|\\d*\\.\\d+)([eE][+-]?\\d+)?([fFdDmM])?$")) {
            return false;
        }

        try {
            String normalized = value.replaceAll("[fFdDmM]$", "");
            return Double.parseDouble(normalized) != 0.0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static boolean isNonEmptyStringLiteral(String value) {
        return value.length() >= 2
                && value.startsWith("\"")
                && value.endsWith("\"")
                && !value.equals("\"\"");
    }

    private static boolean isObjectLikeReturn(String value) {
        if (value.isBlank()
                || value.equals("true")
                || value.equals("false")
                || value.equals("null")
                || isZeroLiteral(value)
                || isNonZeroNumericLiteral(value)) {
            return false;
        }

        return isNonEmptyStringLiteral(value)
                || value.equals("\"\"")
                || value.startsWith("new ")
                || value.startsWith("this")
                || value.matches("^@?[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*$");
    }

    private static String defaultValueForType(String type, boolean emptyReturn) {
        if (type == null || type.isBlank()) {
            return null;
        }

        String normalized = type.replace("?", "").trim();
        if (normalized.equals("void")) {
            return null;
        }

        if (normalized.equals("bool")) {
            return "false";
        }
        if (isNumericType(normalized)) {
            return normalized.equals("float") ? "0.0f"
                    : normalized.equals("double") ? "0.0"
                    : normalized.equals("decimal") ? "0.0m"
                    : normalized.equals("char") ? "'\\0'"
                    : "0";
        }
        if (normalized.equals("string")) {
            return emptyReturn ? "\"\"" : "null";
        }
        if (normalized.endsWith("[]")) {
            String elementType = normalized.substring(0, normalized.length() - 2);
            return emptyReturn ? "System.Array.Empty<" + elementType + ">()" : "null";
        }
        if (isListType(normalized)) {
            return emptyReturn ? "new " + concreteGenericCollectionType(normalized, "List") : "null";
        }
        if (isSetType(normalized)) {
            return emptyReturn ? "new " + concreteGenericCollectionType(normalized, "HashSet") : "null";
        }
        if (isEnumerableType(normalized)) {
            String elementType = genericArgument(normalized);
            return emptyReturn && elementType != null ? "System.Linq.Enumerable.Empty<" + elementType + ">()" : "null";
        }

        return emptyReturn ? null : "null";
    }

    private static boolean isNumericType(String type) {
        return type.equals("byte")
                || type.equals("sbyte")
                || type.equals("short")
                || type.equals("ushort")
                || type.equals("int")
                || type.equals("uint")
                || type.equals("long")
                || type.equals("ulong")
                || type.equals("float")
                || type.equals("double")
                || type.equals("decimal")
                || type.equals("char");
    }

    private static boolean isEmptyCollectionReturnType(String type) {
        return type.endsWith("[]") || isListType(type) || isSetType(type) || isEnumerableType(type);
    }

    private static boolean isAlreadyEmptyCollection(String value) {
        return value.startsWith("System.Array.Empty<")
                || value.startsWith("System.Linq.Enumerable.Empty<")
                || value.matches("^new\\s+(?:System\\.Collections\\.Generic\\.)?(?:List|HashSet)<.*>\\s*\\(\\s*\\)$");
    }

    private static boolean isListType(String type) {
        return type.matches("^(?:System\\.Collections\\.Generic\\.)?(?:IList|List|IReadOnlyList|ICollection|Collection)<.*>$");
    }

    private static boolean isSetType(String type) {
        return type.matches("^(?:System\\.Collections\\.Generic\\.)?(?:ISet|HashSet|IReadOnlySet|Set)<.*>$");
    }

    private static boolean isEnumerableType(String type) {
        return type.matches("^(?:System\\.Collections\\.Generic\\.)?IEnumerable<.*>$");
    }

    private static String concreteGenericCollectionType(String type, String concreteName) {
        String argument = genericArgument(type);
        return "System.Collections.Generic." + concreteName + "<" + (argument == null ? "object" : argument) + ">()";
    }

    private static String genericArgument(String type) {
        int open = type.indexOf('<');
        int close = type.lastIndexOf('>');
        if (open < 0 || close <= open) {
            return null;
        }
        return type.substring(open + 1, close).trim();
    }

    private static boolean isValueTypeConstructor(String typeName) {
        String normalized = typeName.replace("?", "").trim();
        return normalized.equals("bool")
                || normalized.equals("byte")
                || normalized.equals("sbyte")
                || normalized.equals("short")
                || normalized.equals("ushort")
                || normalized.equals("int")
                || normalized.equals("uint")
                || normalized.equals("long")
                || normalized.equals("ulong")
                || normalized.equals("float")
                || normalized.equals("double")
                || normalized.equals("decimal")
                || normalized.equals("char");
    }

    private static boolean startsWithControlKeyword(String statement) {
        return statement.startsWith("if ")
                || statement.startsWith("if(")
                || statement.startsWith("while ")
                || statement.startsWith("while(")
                || statement.startsWith("for ")
                || statement.startsWith("for(")
                || statement.startsWith("foreach ")
                || statement.startsWith("foreach(")
                || statement.startsWith("switch ")
                || statement.startsWith("switch(")
                || statement.startsWith("using ")
                || statement.startsWith("using(")
                || statement.startsWith("lock ")
                || statement.startsWith("lock(")
                || statement.startsWith("catch ")
                || statement.startsWith("catch(");
    }

    private static boolean isUnaryMinus(String line, int minusIndex) {
        if (minusIndex + 1 >= line.length()) {
            return false;
        }

        char next = line.charAt(minusIndex + 1);
        if (next == '-' || next == '>' || next == '=') {
            return false;
        }

        int nextNonWhitespace = skipLeadingTrivia(line, minusIndex + 1);
        if (nextNonWhitespace >= line.length()) {
            return false;
        }

        char nextChar = line.charAt(nextNonWhitespace);
        if (!Character.isLetterOrDigit(nextChar) && nextChar != '_' && nextChar != '(') {
            return false;
        }

        int prev = previousNonWhitespace(line, minusIndex - 1);
        if (prev < 0) {
            return true;
        }

        char prevChar = line.charAt(prev);
        if ("([{=,:;!&|?+-*/%^~<>".indexOf(prevChar) >= 0) {
            return true;
        }

        String before = line.substring(0, minusIndex).trim();
        return before.endsWith("return")
                || before.endsWith("case")
                || before.endsWith("=>")
                || before.endsWith("throw");
    }

    private static boolean isInStringOrComment(String line, int position) {
        boolean inString = false;
        char stringDelimiter = '\0';

        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);

            if (i > 0 && line.charAt(i - 1) == '\\') {
                continue;
            }

            if (c == '"' || c == '\'') {
                if (!inString) {
                    inString = true;
                    stringDelimiter = c;
                } else if (c == stringDelimiter) {
                    inString = false;
                    stringDelimiter = '\0';
                }
            }
        }

        if (inString) {
            return true;
        }

        int commentStart = line.indexOf("//");
        return commentStart >= 0 && commentStart < position;
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private record ConditionSpan(String text, int startColumn) {}
    private record ConstructorCall(String text, String typeName) {}
    private record MethodCall(String text, int column) {}

    private static boolean isTypeSwapMutation(Mutator.MutatorType type) {
        return type == Mutator.MutatorType.TYPE_INT_TO_DOUBLE
                || type == Mutator.MutatorType.TYPE_INT_TO_FLOAT
                || type == Mutator.MutatorType.TYPE_DOUBLE_TO_INT
                || type == Mutator.MutatorType.TYPE_FLOAT_TO_INT;
    }

    private static String removeBom(String sourceCode) {
        if (sourceCode != null && !sourceCode.isEmpty() && sourceCode.charAt(0) == '\uFEFF') {
            return sourceCode.substring(1);
        }
        return sourceCode;
    }

    private static List<MutationPoint> scanTypeSwapMutations(File file, String sourceCode, Mutator.MutatorType type) {
        List<MutationPoint> points = new ArrayList<>();
        String[] lines = sourceCode.split("\n", -1);

        Pattern pattern = Pattern.compile(
                "^(\\s*)(int|double|float)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+?)(;\\s*)$"
        );

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = pattern.matcher(line);

            if (!matcher.find()) {
                continue;
            }

            String declaredType = matcher.group(2);
            String varName = matcher.group(3);
            String exprText = matcher.group(4).trim();

            String original = declaredType + " " + varName + " = " + exprText;
            String replacement = getTypeSwapReplacement(declaredType, varName, exprText, type);

            if (replacement == null || replacement.equals(original)) {
                continue;
            }

            int column = line.indexOf(original);
            if (column < 0) {
                continue;
            }

            points.add(new MutationPoint(
                    file,
                    i + 1,
                    column,
                    original,
                    replacement,
                    type
            ));
        }

        return points;
    }

    private static String getTypeSwapReplacement(String declaredType, String varName, String exprText, Mutator.MutatorType type) {
        switch (type) {
            case TYPE_INT_TO_DOUBLE:
                if (declaredType.equals("int")) {
                    return "double " + varName + " = " + convertIntExpressionToDouble(exprText);
                }
                break;

            case TYPE_INT_TO_FLOAT:
                if (declaredType.equals("int")) {
                    return "float " + varName + " = " + convertIntExpressionToFloat(exprText);
                }
                break;

            case TYPE_DOUBLE_TO_INT:
                if (declaredType.equals("double")) {
                    return "int " + varName + " = (int)(" + exprText + ")";
                }
                break;

            case TYPE_FLOAT_TO_INT:
                if (declaredType.equals("float")) {
                    return "int " + varName + " = (int)(" + exprText + ")";
                }
                break;

            default:
                break;
        }

        return null;
    }

    private static String convertIntExpressionToDouble(String exprText) {
        return exprText.replaceAll("(?<![\\w.])(\\d+)(?![\\w.])", "$1.0");
    }

    private static String convertIntExpressionToFloat(String exprText) {
        return exprText.replaceAll("(?<![\\w.])(\\d+)(?![\\w.])", "$1.0f");
    }

    private static class CSharpMutationVisitor extends CSharpParserBaseVisitor<Void> {

        private final File sourceFile;
        private final Mutator.MutatorType mutationType;
        private final List<MutationPoint> mutationPoints;

        public CSharpMutationVisitor(File sourceFile, Mutator.MutatorType mutationType) {
            this.sourceFile = sourceFile;
            this.mutationType = mutationType;
            this.mutationPoints = new ArrayList<>();
        }

        @Override
        public Void visitAdditive_expression(CSharpParser.Additive_expressionContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("+") && mutationType == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                        if (isStringConcatenationExpression(ctx)) {
                            continue;
                        }
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "+",
                                "-",
                                mutationType
                        ));
                    } else if (operator.equals("-") && mutationType == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "-",
                                "+",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        private boolean isStringConcatenationExpression(CSharpParser.Additive_expressionContext ctx) {
            return ctx.getText().contains("\"");
        }

        @Override
        public Void visitRelational_expression(CSharpParser.Relational_expressionContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals(">") && mutationType == Mutator.MutatorType.ROR_GT_TO_GE) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                ">",
                                ">=",
                                mutationType
                        ));
                    } else if (operator.equals(">=") && mutationType == Mutator.MutatorType.ROR_GE_TO_GT) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                ">=",
                                ">",
                                mutationType
                        ));
                    } else if (operator.equals("<") && mutationType == Mutator.MutatorType.ROR_LT_TO_LE) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "<",
                                "<=",
                                mutationType
                        ));
                    } else if (operator.equals("<=") && mutationType == Mutator.MutatorType.ROR_LE_TO_LT) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "<=",
                                "<",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitEquality_expression(CSharpParser.Equality_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.EQ_NEGATE) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("==")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "==",
                                "!=",
                                mutationType
                        ));
                    } else if (operator.equals("!=")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "!=",
                                "==",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitConditional_and_expression(CSharpParser.Conditional_and_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.LOGICAL_AND_TO_OR) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("&&")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "&&",
                                "||",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitConditional_or_expression(CSharpParser.Conditional_or_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.LOGICAL_OR_TO_AND) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("||")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "||",
                                "&&",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitMultiplicative_expression(CSharpParser.Multiplicative_expressionContext ctx) {
            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("*") && mutationType == Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "*",
                                "/",
                                mutationType
                        ));
                    } else if (operator.equals("/") && mutationType == Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "/",
                                "*",
                                mutationType
                        ));
                    } else if (operator.equals("%") && mutationType == Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "%",
                                "*",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitAnd_expression(CSharpParser.And_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.BOR_AND_TO_OR) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("&")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "&",
                                "|",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        @Override
        public Void visitInclusive_or_expression(CSharpParser.Inclusive_or_expressionContext ctx) {
            if (mutationType != Mutator.MutatorType.BOR_OR_TO_AND) {
                return visitChildren(ctx);
            }

            for (int i = 0; i < ctx.getChildCount(); i++) {
                ParseTree child = ctx.getChild(i);

                if (child instanceof TerminalNode) {
                    String operator = child.getText();
                    Token token = ((TerminalNode) child).getSymbol();

                    if (operator.equals("|")) {
                        mutationPoints.add(new MutationPoint(
                                sourceFile,
                                token.getLine(),
                                token.getCharPositionInLine(),
                                "|",
                                "&",
                                mutationType
                        ));
                    }
                }
            }

            return visitChildren(ctx);
        }

        public List<MutationPoint> getMutationPoints() {
            return mutationPoints;
        }
    }

    public static MSLScannerAdapter createAdapter(File file) throws IOException {
        String sourceCode = Files.readString(file.toPath());
        sourceCode = removeBom(sourceCode);
        final String sourceText = sourceCode;
        final String[] sourceLines = sourceCode.split("\n", -1);
        CharStream input = CharStreams.fromString(sourceCode);
        CSharpLexer lexer = new CSharpLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        CSharpParser parser = new CSharpParser(tokens);
        ParseTree tree = parser.prog();

        return new MSLScannerAdapter() {
            private final Pattern invocationStatement = Pattern.compile(
                    "^(\\s*)(await\\s+)?[A-Za-z_][A-Za-z0-9_\\.<>]*\\s*\\(.*\\)\\s*;\\s*$"
            );
            private final Pattern csharpIdentifier = Pattern.compile("@?[A-Za-z_][A-Za-z0-9_]*");

            @Override
            public boolean isBinaryExpressionFor(ParseTree ctx, String mslOperator) {
                if (ctx.getChildCount() == 3) {
                    return ctx.getChild(1).getText().equals(mslOperator);
                }
                return false;
            }

            @Override
            public boolean isUpdateExpressionFor(ParseTree ctx, String mslOperator) {
                if (ctx instanceof CSharpParser.Post_increment_expressionContext
                        || ctx instanceof CSharpParser.Pre_increment_expressionContext) {
                    return "++".equals(mslOperator);
                }
                if (ctx instanceof CSharpParser.Post_decrement_expressionContext
                        || ctx instanceof CSharpParser.Pre_decrement_expressionContext) {
                    return "--".equals(mslOperator);
                }
                return false;
            }

            @Override
            public ParseTree getLeftOperand(ParseTree ctx) {
                return ctx.getChild(0);
            }

            @Override
            public ParseTree getRightOperand(ParseTree ctx) {
                return ctx.getChild(2);
            }

            @Override
            public ParseTree getOperatorToken(ParseTree ctx) {
                if (ctx instanceof CSharpParser.Post_increment_expressionContext
                        || ctx instanceof CSharpParser.Post_decrement_expressionContext) {
                    return ctx.getChild(ctx.getChildCount() - 1);
                }
                if (ctx instanceof CSharpParser.Pre_increment_expressionContext
                        || ctx instanceof CSharpParser.Pre_decrement_expressionContext) {
                    return ctx.getChild(0);
                }
                if (ctx.getChildCount() >= 3) return ctx.getChild(1);
                return null;
            }

            @Override
            public boolean isQuantity(ParseTree ctx) {
                if (ctx == null) return false;
                if (ctx instanceof TerminalNode tn) {
                    Token symbol = tn.getSymbol();
                    int type = symbol.getType();
                    return type == CSharpLexer.Integer_Literal ||
                            type == CSharpLexer.Real_Literal ||
                            type == CSharpLexer.Simple_Identifier ||
                            csharpIdentifier.matcher(symbol.getText()).matches();
                }
                if (ctx.getChildCount() == 1) return isQuantity(ctx.getChild(0));
                return false;
            }

            @Override
            public String getSourceLine(ParseTree ctx) {
                ParseTree statement = ctx;
                while (statement != null && !(statement instanceof CSharpParser.StatementContext)) {
                    statement = statement.getParent();
                }
                if (statement instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null
                            && start.getStartIndex() >= 0
                            && stop.getStopIndex() >= start.getStartIndex()
                            && stop.getStopIndex() < sourceText.length()) {
                        return sourceText.substring(start.getStartIndex(), stop.getStopIndex() + 1);
                    }
                }

                int[] pos = getTokenPosition(ctx);
                int lineIndex = pos[0] - 1;
                if (lineIndex >= 0 && lineIndex < sourceLines.length) {
                    return sourceLines[lineIndex];
                }
                return "";
            }

            @Override
            public String getSingleArgumentText(ParseTree ctx, String functionName) {
                String text = getNodeText(ctx);
                String prefix = functionName + "(";
                if (!text.startsWith(prefix) || !text.endsWith(")")) {
                    return null;
                }

                String inner = text.substring(prefix.length(), text.length() - 1).trim();
                if (inner.isEmpty() || hasTopLevelComma(inner)) {
                    return null;
                }

                return inner;
            }

            @Override
            public boolean isInvocationStatement(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null && start.getLine() != stop.getLine()) {
                        return false;
                    }
                }

                String text = getNodeText(ctx);
                if (text == null || text.isBlank()) {
                    return false;
                }
                String trimmed = text.stripLeading();
                if (trimmed.startsWith("return") || trimmed.startsWith("yieldreturn")
                        || trimmed.startsWith("yield return")) {
                    return false;
                }
                return invocationStatement.matcher(text).matches();
            }

            @Override
            public String getNodeText(ParseTree ctx) {
                if (ctx instanceof ParserRuleContext prc) {
                    Token start = prc.getStart();
                    Token stop = prc.getStop();
                    if (start != null && stop != null
                            && start.getStartIndex() >= 0
                            && stop.getStopIndex() >= start.getStartIndex()
                            && stop.getStopIndex() < sourceText.length()) {
                        return sourceText.substring(start.getStartIndex(), stop.getStopIndex() + 1);
                    }
                }
                return ctx.getText();
            }

            @Override
            public int[] getTokenPosition(ParseTree ctx) {
                if (ctx instanceof TerminalNode tn) {
                    Token s = tn.getSymbol();
                    return new int[]{s.getLine(), s.getCharPositionInLine()};
                }
                if (ctx instanceof ParserRuleContext prc) {
                    Token s = prc.getStart();
                    if (s != null) {
                        return new int[]{s.getLine(), s.getCharPositionInLine()};
                    }
                    for (int i = 0; i < prc.getChildCount(); i++) {
                        int[] childPos = getTokenPosition(prc.getChild(i));
                        if (childPos[0] != 0 || childPos[1] != 0) {
                            return childPos;
                        }
                    }
                }
                return new int[]{0, 0};
            }

            @Override
            public ParseTree getRootContext() {
                return tree;
            }
        };
    }

    private static boolean hasTopLevelComma(String text) {
        int parenDepth = 0;
        int bracketDepth = 0;
        int braceDepth = 0;
        boolean inString = false;
        char stringDelimiter = '\0';

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i > 0 && text.charAt(i - 1) == '\\') {
                continue;
            }

            if (inString) {
                if (c == stringDelimiter) {
                    inString = false;
                    stringDelimiter = '\0';
                }
                continue;
            }

            if (c == '"' || c == '\'') {
                inString = true;
                stringDelimiter = c;
                continue;
            }

            switch (c) {
                case '(' -> parenDepth++;
                case ')' -> parenDepth--;
                case '[' -> bracketDepth++;
                case ']' -> bracketDepth--;
                case '{' -> braceDepth++;
                case '}' -> braceDepth--;
                case ',' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        return true;
                    }
                }
                default -> {
                }
            }
        }

        return false;
    }
}
