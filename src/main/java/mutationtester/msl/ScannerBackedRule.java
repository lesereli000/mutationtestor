package mutationtester.msl;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;
import mutationtester.scanner.CSharpMutationScanner;
import mutationtester.scanner.JavaScriptMutationScanner;
import mutationtester.scanner.PythonMutationScanner;
import mutationtester.scanner.RacketMutationScanner;

import org.antlr.v4.runtime.tree.ParseTree;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MSL rule for patterns that are easier to find with an existing language scanner
 * than with a single parse-tree node pattern.
 */
public class ScannerBackedRule implements MutationRule {

    private final Mutator.MutatorType mutatorType;
    private final String label;

    public ScannerBackedRule(Mutator.MutatorType mutatorType, String label) {
        this.mutatorType = mutatorType;
        this.label = label;
    }

    @Override
    public boolean matches(MSLScannerAdapter scanner, ParseTree ctx) {
        return false;
    }

    @Override
    public MutationPoint apply(MSLScannerAdapter scanner, ParseTree ctx, File sourceFile) {
        throw new UnsupportedOperationException("Scanner-backed rules scan whole files directly.");
    }

    @Override
    public List<MutationPoint> findMutationPoints(MSLScannerAdapter scanner, File sourceFile) {
        try {
            List<MutationPoint> points = new ArrayList<>();
            // Native scanners don't know which spec rule asked for them;
            // label their points so reports group them under this rule.
            for (MutationPoint p : scanWithNativeScanner(sourceFile)) {
                points.add(new MutationPoint(p.getSourceFile(), p.getLineNumber(),
                        p.getColumnNumber(), p.getOperator(), p.getReplacement(),
                        p.getType(), label));
            }
            // Supplement the native points with portable ones for sites the
            // native scanner doesn't cover, so coverage doesn't depend on
            // whether an unrelated site happened to be found natively.
            for (MutationPoint portable : scanPortable(sourceFile)) {
                if (points.stream().noneMatch(n -> overlaps(n, portable))) {
                    points.add(portable);
                }
            }
            return points;
        } catch (IOException e) {
            System.err.println("Warning: Failed to apply MSL scanner-backed rule "
                    + label + " to " + sourceFile + ": " + e.getMessage());
            return Collections.emptyList();
        }
    }

    private static boolean overlaps(MutationPoint a, MutationPoint b) {
        if (a.getLineNumber() != b.getLineNumber()) {
            return multiLineOverlaps(a, b);
        }
        int aEnd = a.getColumnNumber() + a.getOperator().length();
        int bEnd = b.getColumnNumber() + b.getOperator().length();
        return a.getColumnNumber() < bEnd && b.getColumnNumber() < aEnd;
    }

    private static boolean multiLineOverlaps(MutationPoint a, MutationPoint b) {
        MutationPoint first = a.getLineNumber() < b.getLineNumber() ? a : b;
        MutationPoint second = first == a ? b : a;
        String[] lines = first.getOperator().split("\n", -1);
        return first.getLineNumber() + lines.length - 1 >= second.getLineNumber();
    }

    @Override
    public String getLabel() {
        return label;
    }

    private List<MutationPoint> scanWithNativeScanner(File sourceFile) throws IOException {
        String lower = sourceFile.getName().toLowerCase();

        if (lower.endsWith(".cs")) {
            return CSharpMutationScanner.scanFile(sourceFile, mutatorType);
        }
        if (lower.endsWith(".js")) {
            return JavaScriptMutationScanner.scanFile(sourceFile, mutatorType);
        }
        if (lower.endsWith(".py")) {
            return PythonMutationScanner.scanFile(sourceFile, mutatorType);
        }
        if (lower.endsWith(".rkt")) {
            return RacketMutationScanner.scanFile(sourceFile, mutatorType);
        }

        return Collections.emptyList();
    }

    private List<MutationPoint> scanPortable(File sourceFile) throws IOException {
        String lower = sourceFile.getName().toLowerCase();
        String source = Files.readString(sourceFile.toPath());
        String[] lines = source.split("\n", -1);

        List<MutationPoint> found = switch (mutatorType) {
            case EMPTY_RETURNS -> scanEmptyReturns(sourceFile, lines, lower);
            case CONSTRUCTOR_CALLS -> scanConstructorCalls(sourceFile, lines, lower);
            case NON_VOID_METHOD_CALLS -> scanNonVoidMethodCalls(sourceFile, lines, lower);
            case REMOVE_CONDITIONALS_EQUAL_IF,
                 REMOVE_CONDITIONALS_EQUAL_ELSE,
                 REMOVE_CONDITIONALS_ORDER_IF,
                 REMOVE_CONDITIONALS_ORDER_ELSE -> scanConditionalRemovals(sourceFile, lines, lower);
            default -> Collections.emptyList();
        };
        if (found.isEmpty()) {
            return found;
        }

        // Drop matches that sit inside comments or string literals, and inside
        // Racket macro definitions, which the native scanner skips too.
        boolean[] skipped = nonCodeMask(source, lower);
        if (lower.endsWith(".rkt")) {
            maskRacketMacroDefinitions(source, skipped);
        }
        int[] lineStarts = new int[lines.length + 1];
        for (int i = 0; i < lines.length; i++) {
            lineStarts[i + 1] = lineStarts[i] + lines[i].length() + 1;
        }
        List<MutationPoint> code = new ArrayList<>();
        for (MutationPoint point : found) {
            int line = point.getLineNumber() - 1;
            int offset = line >= 0 && line < lines.length ? lineStarts[line] + point.getColumnNumber() : -1;
            if (offset < 0 || offset >= skipped.length || !skipped[offset]) {
                code.add(point);
            }
        }
        return code;
    }

    /**
     * Marks every offset of each (define-syntax-rule ...) or other macro
     * definition, using the same form names as RacketMutationScanner. Parens
     * in comments and strings (already marked) don't count toward nesting.
     */
    private static void maskRacketMacroDefinitions(String src, boolean[] mask) {
        for (int open = 0; open < src.length(); open++) {
            if (src.charAt(open) != '(' || mask[open]) continue;
            int headEnd = open + 1;
            while (headEnd < src.length() && !Character.isWhitespace(src.charAt(headEnd))
                    && "()[]".indexOf(src.charAt(headEnd)) < 0) headEnd++;
            if (!RacketMutationScanner.isMacroDefinitionForm(src.substring(open + 1, headEnd))) continue;

            int depth = 0;
            int close = open;
            for (; close < src.length(); close++) {
                if (mask[close]) continue;
                char ch = src.charAt(close);
                if (ch == '(' || ch == '[') depth++;
                else if ((ch == ')' || ch == ']') && --depth == 0) break;
            }
            int stop = Math.min(close + 1, src.length());
            java.util.Arrays.fill(mask, open, stop, true);
            open = stop - 1;
        }
    }

    /**
     * Marks every offset inside a comment or inside a string literal's quotes
     * (the quote characters themselves count as code, so an expression that
     * is a string literal can still be mutated).
     */
    private static boolean[] nonCodeMask(String src, String extension) {
        boolean[] mask = new boolean[src.length()];
        boolean racket = extension.endsWith(".rkt");
        boolean python = extension.endsWith(".py");
        boolean js = extension.endsWith(".js");
        int i = 0;
        int n = src.length();
        while (i < n) {
            char ch = src.charAt(i);
            if (racket && ch == '#' && i + 1 < n && src.charAt(i + 1) == '\\') {
                i += 3;
            } else if (racket && ch == '#' && i + 1 < n && src.charAt(i + 1) == '|') {
                int end = src.indexOf("|#", i + 2);
                int stop = end < 0 ? n : end + 2;
                java.util.Arrays.fill(mask, i, stop, true);
                i = stop;
            } else if ((racket && ch == ';') || (python && ch == '#')
                    || (!racket && !python && ch == '/' && i + 1 < n && src.charAt(i + 1) == '/')) {
                int end = src.indexOf('\n', i);
                int stop = end < 0 ? n : end;
                java.util.Arrays.fill(mask, i, stop, true);
                i = stop;
            } else if (!racket && !python && ch == '/' && i + 1 < n && src.charAt(i + 1) == '*') {
                int end = src.indexOf("*/", i + 2);
                int stop = end < 0 ? n : end + 2;
                java.util.Arrays.fill(mask, i, stop, true);
                i = stop;
            } else if (ch == '"' || (!racket && ch == '\'') || (js && ch == '`')) {
                String delimiter = String.valueOf(ch);
                if (!racket && ch != '`' && src.startsWith(delimiter.repeat(3), i)) {
                    delimiter = delimiter.repeat(3);
                }
                int j = i + delimiter.length();
                while (j < n && !src.startsWith(delimiter, j)) {
                    if (src.charAt(j) == '\\') {
                        j++;
                    } else if (delimiter.length() == 1 && ch != '`' && src.charAt(j) == '\n') {
                        break;
                    }
                    j++;
                }
                j = Math.min(j, n);
                java.util.Arrays.fill(mask, Math.min(i + delimiter.length(), n), j, true);
                i = Math.min(j + delimiter.length(), n);
                if (j < n && !src.startsWith(delimiter, j)) {
                    i = j;
                }
            } else {
                i++;
            }
        }
        return mask;
    }

    private List<MutationPoint> scanEmptyReturns(File sourceFile, String[] lines, String extension)
            throws IOException {
        if (extension.endsWith(".rkt")) {
            // Each returned value becomes the empty value of its inferred type,
            // as Java and C# use the declared return type.
            List<MutationPoint> points = new ArrayList<>();
            for (MutationPoint p : RacketMutationScanner.scanTypedEmptyReturns(sourceFile)) {
                points.add(new MutationPoint(p.getSourceFile(), p.getLineNumber(), p.getColumnNumber(),
                        p.getOperator(), p.getReplacement(), mutatorType, label));
            }
            return points;
        }

        List<MutationPoint> points = new ArrayList<>();
        Pattern pyReturn = Pattern.compile("^(\\s*)return\\s+(.+?)\\s*$");
        Pattern cStyleReturn = Pattern.compile("\\breturn\\s+(.+?)\\s*;");
        boolean typed = extension.endsWith(".java") || extension.endsWith(".cs");
        String source = String.join("\n", lines);
        int lineOffset = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher matcher = extension.endsWith(".py") ? pyReturn.matcher(line) : cStyleReturn.matcher(line);

            while (matcher.find()) {
                String expr = matcher.group(extension.endsWith(".py") ? 2 : 1).trim();
                if (expr.isEmpty()) {
                    continue;
                }

                String replacement;
                if (typed) {
                    // Java and C# need a value of the method's declared return type.
                    String returnType = enclosingReturnType(source, lineOffset + matcher.start());
                    replacement = returnType == null
                            ? emptyValueForExpression(expr, extension)
                            : emptyValueForType(returnType, extension);
                } else {
                    replacement = emptyValueForExpression(expr, extension);
                }
                if (replacement == null || replacement.equals(expr)) {
                    continue;
                }

                points.add(new MutationPoint(
                        sourceFile,
                        i + 1,
                        matcher.start(extension.endsWith(".py") ? 2 : 1),
                        expr,
                        replacement,
                        mutatorType,
                        label
                ));
            }
            lineOffset += line.length() + 1;
        }
        return points;
    }

    // Modifiers that may precede a Java or C# method's return type.
    private static final String METHOD_MODIFIERS =
            "public|private|protected|internal|static|final|abstract|synchronized|native|strictfp"
            + "|default|override|virtual|sealed|async|extern|unsafe|new|partial|readonly";

    private static final Pattern METHOD_HEADER = Pattern.compile(
            "(?:^|[\\s;{}])(?:(?:" + METHOD_MODIFIERS + "|@\\w+)\\s+)*"
            + "([A-Za-z_][\\w.]*(?:<[^(){};]*>)?(?:\\[\\])*\\??)\\s+[A-Za-z_]\\w*\\s*"
            + "(?:<[^(){};]*>\\s*)?\\([^(){};]*\\)\\s*(?:throws\\s+[\\w.,\\s]+)?\\{");

    /** Racket syntax that looks like a call but isn't a function application. */
    private static final Set<String> RACKET_SPECIAL_FORMS = Set.of(
            "and", "or", "if", "cond", "case", "when", "unless", "let", "let*", "letrec",
            "lambda", "λ", "begin", "quote", "quasiquote", "match", "for", "for/list", "do");

    /** Keywords that can look like a call after '=' but are not one. */
    private static final java.util.Set<String> CALL_LIKE_KEYWORDS = java.util.Set.of(
            "function", "switch", "if", "while", "for", "typeof", "sizeof", "nameof",
            "lambda", "not", "await", "yield", "checked", "unchecked", "default", "stackalloc");

    private static final java.util.Set<String> NOT_A_TYPE = java.util.Set.of(
            "else", "return", "new", "throw", "case", "await", "yield", "if", "while", "for",
            "switch", "catch", "using", "lock", "foreach", "do");

    /** Declared return type of the Java/C# method enclosing offset, or null. */
    private static String enclosingReturnType(String source, int offset) {
        Matcher m = METHOD_HEADER.matcher(source.substring(0, offset));
        String type = null;
        while (m.find()) {
            String candidate = m.group(1);
            if (!NOT_A_TYPE.contains(candidate) && !candidate.matches(METHOD_MODIFIERS)) {
                type = candidate;
            }
        }
        return type;
    }

    /**
     * PIT-style empty value for a declared type: 0 for numbers, false for
     * booleans, "" for strings, Optional.empty() for Java Optionals, and
     * null (C#: default) otherwise. Returns null for void.
     */
    private static String emptyValueForType(String type, String extension) {
        String t = type.strip();
        boolean csharp = extension.endsWith(".cs");
        if (csharp) {
            Matcher task = Pattern.compile("^(?:System\\.Threading\\.Tasks\\.)?(?:Value)?Task<(.+)>$").matcher(t);
            if (task.matches()) t = task.group(1).strip();
            if (t.equals("Task") || t.equals("ValueTask")) return null;
            if (t.endsWith("?")) return "null";
        }
        return switch (t) {
            case "void" -> null;
            case "int", "short", "byte", "Integer", "Short", "Byte",
                 "sbyte", "uint", "ushort", "ulong", "decimal", "nint", "nuint",
                 "Int16", "Int32", "Int64", "UInt16", "UInt32", "UInt64", "Decimal", "SByte" -> "0";
            case "long", "Long" -> csharp ? "0" : "0L";
            case "float", "Float", "Single" -> csharp ? "0" : "0.0f";
            case "double", "Double" -> csharp ? "0" : "0.0";
            case "char", "Character", "Char" -> "'\\0'";
            case "boolean", "Boolean", "bool" -> "false";
            case "String", "string", "java.lang.String" -> "\"\"";
            default -> {
                if (!csharp && t.matches("(?:java\\.util\\.)?Optional(?:<.*>)?")) yield "Optional.empty()";
                yield csharp ? "default" : "null";
            }
        };
    }

    private List<MutationPoint> scanConstructorCalls(File sourceFile, String[] lines, String extension) {
        if (extension.endsWith(".rkt")) {
            List<MutationPoint> points = new ArrayList<>();
            // Racket constructors are ordinary calls: a struct's own name or
            // make-name for (struct name ...) / (define-struct name ...) in the
            // file, any (make-thing ...), and racket/class's (new thing% ...).
            Set<String> structs = new TreeSet<>();
            Matcher def = Pattern.compile("\\((?:struct|define-struct)\\s+([^\\s()\\[\\]]+)")
                    .matcher(String.join("\n", lines));
            while (def.find()) structs.add(Pattern.quote(def.group(1)));
            String structCalls = structs.isEmpty() ? "" : "|" + String.join("|", structs);
            Pattern p = Pattern.compile("\\((?:make-[A-Za-z_][A-Za-z0-9_-]*|new" + structCalls + ")(?=[\\s)])");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher m = p.matcher(line);
                while (m.find()) {
                    int open = line.lastIndexOf('(', m.start() + 1);
                    int close = findMatchingParen(line, open);
                    if (open < 0 || close < 0) continue;
                    String call = line.substring(open, close + 1);
                    // '() matches what RacketMutationScanner uses for constructor calls
                    points.add(new MutationPoint(
                            sourceFile,
                            i + 1,
                            open,
                            call,
                            "'()",
                            mutatorType,
                            label
                    ));
                }
            }
            return points;
        }

        if (extension.endsWith(".py")) {
            List<MutationPoint> points = new ArrayList<>();
            // A call to a capitalised name, including its module prefix
            // (threading.Lock()), but not a decorator or a class header's bases.
            Pattern p = Pattern.compile("(?<![\\w.@])(?:[A-Za-z_]\\w*\\.)*[A-Z][A-Za-z0-9_]*\\s*\\(");
            Pattern classHeader = Pattern.compile("^\\s*class\\s");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                if (classHeader.matcher(line).find()) continue;
                Matcher m = p.matcher(line);
                while (m.find()) {
                    int open = line.indexOf('(', m.end() - 1);
                    int close = findMatchingParen(line, open);
                    if (open < 0 || close < 0) continue;
                    String call = line.substring(m.start(), close + 1);
                    points.add(new MutationPoint(
                            sourceFile,
                            i + 1,
                            m.start(),
                            call,
                            "None",
                            mutatorType,
                            label
                    ));
                }
            }
            return points;
        }

        if (extension.endsWith(".py") || extension.endsWith(".rkt")) {
            return Collections.emptyList();
        }

        List<MutationPoint> points = new ArrayList<>();
        Pattern p = Pattern.compile("\\bnew\\s+[A-Za-z_][A-Za-z0-9_$.<>]*\\s*\\(");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = p.matcher(line);
            while (m.find()) {
                int open = line.indexOf('(', m.end() - 1);
                int close = findMatchingParen(line, open);
                if (open < 0 || close < 0) continue;
                // Skip anonymous classes (new T() { ... }) and thrown
                // exceptions (throw new E(...)), which can't become null.
                if (line.substring(close + 1).strip().startsWith("{")
                        || line.substring(0, m.start()).strip().endsWith("throw")) continue;
                String call = line.substring(m.start(), close + 1);
                points.add(new MutationPoint(
                        sourceFile,
                        i + 1,
                        m.start(),
                        call,
                        nullLiteral(extension),
                        mutatorType,
                        label
                ));
            }
        }
        return points;
    }

    private List<MutationPoint> scanNonVoidMethodCalls(File sourceFile, String[] lines, String extension) {
        if (extension.endsWith(".rkt")) {
            List<MutationPoint> points = new ArrayList<>();
            Pattern defineCall = Pattern.compile("\\(define\\s+[A-Za-z_][A-Za-z0-9_-]*\\s+(\\([A-Za-z_][A-Za-z0-9_-]*.*\\))\\s*\\)");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                Matcher m = defineCall.matcher(line.trim());
                if (m.find()) {
                    String call = m.group(1).trim();
                    // (or ...), (if ...), (let ...) etc. are syntax, not calls.
                    String head = call.substring(1).split("[\\s()\\[\\]]", 2)[0];
                    if (RACKET_SPECIAL_FORMS.contains(head)) continue;
                    int col = line.indexOf(call);
                    points.add(new MutationPoint(
                            sourceFile,
                            i + 1,
                            Math.max(col, 0),
                            call,
                            "#f",
                            mutatorType,
                            label
                    ));
                }
            }
            return points;
        }

        List<MutationPoint> points = new ArrayList<>();
        // A plain assignment: not part of ==, <=, +=, etc.
        Pattern assignCall = Pattern.compile("(?<![=!<>+\\-*/%&|^])=(?!=)\\s*([A-Za-z_][A-Za-z0-9_$.]*)\\s*\\(");
        boolean typed = extension.endsWith(".java") || extension.endsWith(".cs");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = assignCall.matcher(line);
            while (m.find()) {
                // "= function() {", "= switch (x) {" etc. aren't calls.
                if (CALL_LIKE_KEYWORDS.contains(m.group(1))) continue;
                int callStart = m.start(1);
                int open = line.indexOf('(', m.end(1));
                int close = findMatchingParen(line, open);
                if (open < 0 || close < 0) continue;
                String call = line.substring(callStart, close + 1);
                String replacement = defaultValueLiteral(extension);
                if (typed) {
                    // With var the type comes from the call itself; "var x = null" doesn't compile.
                    if (Pattern.compile("\\bvar\\s+\\w+\\s*$").matcher(line.substring(0, m.start())).find()) continue;
                    String type = assignedType(lines, i, line.substring(0, m.start()));
                    if (type != null) replacement = emptyValueForType(type, extension);
                    if (replacement == null) continue;
                    // A number is the default for numbers, not an "empty" value.
                    if (replacement.equals("\"\"") || replacement.equals("Optional.empty()")) {
                        replacement = extension.endsWith(".cs") ? "default" : "null";
                    }
                }
                points.add(new MutationPoint(
                        sourceFile,
                        i + 1,
                        callStart,
                        call,
                        replacement,
                        mutatorType,
                        label
                ));
            }
        }
        return points;
    }

    /**
     * Declared type of the variable assigned on a Java/C# line, from the text
     * left of '=' ("int v" -> int) or, for a plain "v = ...", from an earlier
     * declaration of v. Returns null when unknown or declared with var.
     */
    private static String assignedType(String[] lines, int lineIndex, String leftOfEquals) {
        Matcher decl = Pattern.compile(
                "(?:^|[\\s;{(])(?:final\\s+|readonly\\s+|const\\s+)*([A-Za-z_][\\w.]*(?:<[^=;]*>)?(?:\\[\\])*\\??)\\s+([A-Za-z_]\\w*)\\s*$")
                .matcher(leftOfEquals);
        if (decl.find()) {
            String type = decl.group(1);
            return type.equals("var") || NOT_A_TYPE.contains(type) ? null : type;
        }
        Matcher target = Pattern.compile("([A-Za-z_]\\w*)\\s*$").matcher(leftOfEquals);
        if (!target.find()) return null;
        Pattern earlier = Pattern.compile(
                "(?:^|[\\s;{(,])([A-Za-z_][\\w.]*(?:<[^=;()]*>)?(?:\\[\\])*\\??)\\s+"
                + Pattern.quote(target.group(1)) + "\\s*[=;,)]");
        for (int i = lineIndex; i >= 0; i--) {
            Matcher m = earlier.matcher(lines[i]);
            String type = null;
            while (m.find()) type = m.group(1);
            if (type != null && !type.equals("var") && !NOT_A_TYPE.contains(type)) return type;
        }
        return null;
    }

    private List<MutationPoint> scanConditionalRemovals(File sourceFile, String[] lines, String extension) {
        List<MutationPoint> points = new ArrayList<>();
        String replacement = toBooleanLiteral(extension,
                mutatorType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                        || mutatorType == Mutator.MutatorType.REMOVE_CONDITIONALS_ORDER_IF);

        if (extension.endsWith(".rkt")) {
            return scanRacketConditionalRemovals(sourceFile, lines, replacement);
        }

        // javac rejects while (false) and code after while (true) as
        // unreachable, so Java only gets if-conditions.
        Pattern cStyle = Pattern.compile(extension.endsWith(".java") ? "\\b(if)\\s*\\(" : "\\b(if|while)\\s*\\(");
        Pattern pyStyle = Pattern.compile("^(\\s*)(if|while)\\s+(.+?)\\s*:\\s*$");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (extension.endsWith(".py")) {
                Matcher py = pyStyle.matcher(line);
                if (py.find()) {
                    String condition = py.group(3).trim();
                    if (matchesConditionalFamily(condition)) {
                        points.add(new MutationPoint(
                                sourceFile,
                                i + 1,
                                py.start(3),
                                condition,
                                replacement,
                                mutatorType,
                                label
                        ));
                    }
                }
                continue;
            }

            Matcher c = cStyle.matcher(line);
            while (c.find()) {
                int open = line.indexOf('(', c.end() - 1);
                int close = findMatchingParen(line, open);
                if (open < 0 || close < 0) continue;
                String condition = line.substring(open + 1, close).trim();
                if (matchesConditionalFamily(condition)) {
                    int start = line.indexOf(condition, open + 1);
                    points.add(new MutationPoint(
                            sourceFile,
                            i + 1,
                            Math.max(start, open + 1),
                            condition,
                            replacement,
                            mutatorType,
                            label
                    ));
                }
            }
        }

        return points;
    }

    /**
     * Racket conditions are whole forms: the test of (if test ...), (when test ...),
     * (unless test ...) and each [test ...] cond clause. The test is classified by
     * its function name and replaced whole, so (if (string<? s t) ...) becomes
     * (if #t ...). It backs up the native scanner, e.g. for multi-line forms.
     */
    private List<MutationPoint> scanRacketConditionalRemovals(File sourceFile, String[] lines,
                                                              String replacement) {
        boolean equality = mutatorType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                || mutatorType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE;
        // The '(' that opens a test: after if/when/unless, or first in a cond clause
        Pattern testStart = Pattern.compile("(?:\\((?:if|when|unless)\\s+|\\[\\s*)\\(");
        List<MutationPoint> points = new ArrayList<>();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = testStart.matcher(line);
            while (m.find()) {
                int open = m.end() - 1;
                int close = findMatchingParen(line, open);
                if (close < 0) continue;
                String test = line.substring(open, close + 1);
                String head = test.substring(1).split("[\\s()\\[\\]]", 2)[0];
                boolean matches = equality
                        ? RacketMutationScanner.isEqualityTest(head)
                        : RacketMutationScanner.isOrderTest(head);
                if (matches) {
                    points.add(new MutationPoint(sourceFile, i + 1, open, test, replacement, mutatorType, label));
                }
            }
        }
        return points;
    }

    private boolean matchesConditionalFamily(String condition) {
        boolean isEquality = condition.contains("==") || condition.contains("!=");
        boolean isOrder = condition.contains("<") || condition.contains(">");

        if (mutatorType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_IF
                || mutatorType == Mutator.MutatorType.REMOVE_CONDITIONALS_EQUAL_ELSE) {
            return isEquality;
        }
        return isOrder;
    }

    private String emptyValueForExpression(String expr, String extension) {
        if (expr.matches("(?i)true|false")) return extension.endsWith(".py") ? "False" : "false";
        if (expr.matches("-?\\d+(?:\\.\\d+)?")) return "0";
        if (expr.matches("\".*\"|'.*'")) return extension.endsWith(".py") ? "\"\"" : "\"\"";

        if (extension.endsWith(".py")) return "None";
        if (extension.endsWith(".rkt")) return "#f";
        if (extension.endsWith(".js")) return "null";
        return "null";
    }

    private String nullLiteral(String extension) {
        if (extension.endsWith(".py")) return "None";
        if (extension.endsWith(".rkt")) return "#f";
        return "null";
    }

    private String defaultValueLiteral(String extension) {
        if (extension.endsWith(".py")) return "None";
        if (extension.endsWith(".rkt")) return "#f";
        if (extension.endsWith(".js")) return "null";
        return "null";
    }

    private String toBooleanLiteral(String extension, boolean value) {
        if (extension.endsWith(".py")) return value ? "True" : "False";
        if (extension.endsWith(".rkt")) return value ? "#t" : "#f";
        return value ? "true" : "false";
    }

    private int findMatchingParen(String text, int openIndex) {
        if (openIndex < 0 || openIndex >= text.length() || text.charAt(openIndex) != '(') {
            return -1;
        }
        int depth = 0;
        for (int i = openIndex; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '(') depth++;
            else if (ch == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
}
