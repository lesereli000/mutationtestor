package mutationtester.scanner;

import mutationtester.model.MutationPoint;
import mutationtester.model.Mutator;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Dictionary-based (regex/pattern) mutation scanner.
 * Works for any plain-text source file; uses language-aware string/comment
 * detection so that operators inside string literals or comments are skipped.
 *
 * Supported languages with full comment/string awareness:
 *   .py  — Python  (#-comments, single/double/triple-quoted strings)
 *   all others — C-like (//-comments, single/double-quoted strings)
 */
public class DictionaryMutationScanner {

    public static List<MutationPoint> scanFile(File file, Mutator.MutatorType type) throws IOException {
        List<MutationPoint> points = new ArrayList<>();
        String content = Files.readString(file.toPath());
        String[] lines = content.split("\n", -1);
        boolean isPython = file.getName().endsWith(".py");

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];

            // ── AOR: Arithmetic Operator Replacement ──────────────────────────

            if (type == Mutator.MutatorType.AOR_PLUS_TO_MINUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '+') continue;
                    if (inProtected(line, col, isPython)) continue;
                    if (col + 1 < line.length() && (line.charAt(col + 1) == '+' || line.charAt(col + 1) == '=')) continue;
                    if (col > 0 && line.charAt(col - 1) == '+') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "+", "-", type));
                }
            }

            if (type == Mutator.MutatorType.AOR_MINUS_TO_PLUS) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '-') continue;
                    if (inProtected(line, col, isPython)) continue;
                    if (col + 1 < line.length() && (line.charAt(col + 1) == '-' || line.charAt(col + 1) == '=' || line.charAt(col + 1) == '>')) continue;
                    if (col > 0 && line.charAt(col - 1) == '-') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "-", "+", type));
                }
            }

            if (type == Mutator.MutatorType.AOR_MULTIPLY_TO_DIVIDE) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '*') continue;
                    if (inProtected(line, col, isPython)) continue;
                    // Skip ** (power) or *=
                    if (col + 1 < line.length() && (line.charAt(col + 1) == '*' || line.charAt(col + 1) == '=')) continue;
                    if (col > 0 && line.charAt(col - 1) == '*') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "*", "/", type));
                }
            }

            if (type == Mutator.MutatorType.AOR_DIVIDE_TO_MULTIPLY) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '/') continue;
                    if (inProtected(line, col, isPython)) continue;
                    // Skip // (floor div in Python, line comment in C-like) or /= or /*
                    if (col + 1 < line.length() && (line.charAt(col + 1) == '/' || line.charAt(col + 1) == '=' || line.charAt(col + 1) == '*')) continue;
                    if (col > 0 && line.charAt(col - 1) == '/') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "/", "*", type));
                }
            }

            if (type == Mutator.MutatorType.AOR_MODULO_TO_MULTIPLY) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '%') continue;
                    if (inProtected(line, col, isPython)) continue;
                    if (col + 1 < line.length() && line.charAt(col + 1) == '=') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "%", "*", type));
                }
            }

            // Python-only: floor division // → regular division /
            if (type == Mutator.MutatorType.AOR_FLOOR_DIV_TO_DIV && isPython) {
                for (int col = 0; col + 1 < line.length(); col++) {
                    if (line.charAt(col) != '/' || line.charAt(col + 1) != '/') continue;
                    if (inProtected(line, col, true)) continue;
                    // Skip //= (floor-div assignment)
                    if (col + 2 < line.length() && line.charAt(col + 2) == '=') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "//", "/", type));
                    col++; // skip past second '/' so we don't re-examine it
                }
            }

            // Python-only: exponentiation ** → multiply *
            if (type == Mutator.MutatorType.AOR_POWER_TO_MULTIPLY && isPython) {
                for (int col = 0; col + 1 < line.length(); col++) {
                    if (line.charAt(col) != '*' || line.charAt(col + 1) != '*') continue;
                    if (inProtected(line, col, true)) continue;
                    // Skip **= (power-assign)
                    if (col + 2 < line.length() && line.charAt(col + 2) == '=') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "**", "*", type));
                    col++; // skip past second '*'
                }
            }

            // ── BOR: Bitwise Operator Replacement ─────────────────────────────

            if (type == Mutator.MutatorType.BOR_AND_TO_OR) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '&') continue;
                    if (inProtected(line, col, isPython)) continue;
                    if (col + 1 < line.length() && (line.charAt(col + 1) == '&' || line.charAt(col + 1) == '=')) continue;
                    if (col > 0 && line.charAt(col - 1) == '&') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "&", "|", type));
                }
            }

            if (type == Mutator.MutatorType.BOR_OR_TO_AND) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '|') continue;
                    if (inProtected(line, col, isPython)) continue;
                    if (col + 1 < line.length() && (line.charAt(col + 1) == '|' || line.charAt(col + 1) == '=')) continue;
                    if (col > 0 && line.charAt(col - 1) == '|') continue;
                    points.add(new MutationPoint(file, lineNum + 1, col, "|", "&", type));
                }
            }

            // ── ROR: Relational Operator Replacement ──────────────────────────

            if (type == Mutator.MutatorType.ROR_LT_TO_LE) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '<') continue;
                    if (inProtected(line, col, isPython)) continue;
                    char next = col + 1 < line.length() ? line.charAt(col + 1) : '\0';
                    if (next == '=' || next == '<') continue; // already <= or <<
                    points.add(new MutationPoint(file, lineNum + 1, col, "<", "<=", type));
                }
            }

            if (type == Mutator.MutatorType.ROR_LE_TO_LT) {
                for (int idx = 0; (idx = line.indexOf("<=", idx)) >= 0; idx += 2) {
                    if (!inProtected(line, idx, isPython))
                        points.add(new MutationPoint(file, lineNum + 1, idx, "<=", "<", type));
                }
            }

            if (type == Mutator.MutatorType.ROR_GT_TO_GE) {
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) != '>') continue;
                    if (inProtected(line, col, isPython)) continue;
                    char next = col + 1 < line.length() ? line.charAt(col + 1) : '\0';
                    if (next == '=' || next == '>') continue; // already >= or >>
                    if (col > 0 && line.charAt(col - 1) == '-') continue; // skip ->
                    points.add(new MutationPoint(file, lineNum + 1, col, ">", ">=", type));
                }
            }

            if (type == Mutator.MutatorType.ROR_GE_TO_GT) {
                for (int idx = 0; (idx = line.indexOf(">=", idx)) >= 0; idx += 2) {
                    if (!inProtected(line, idx, isPython))
                        points.add(new MutationPoint(file, lineNum + 1, idx, ">=", ">", type));
                }
            }

            // ── EQ: Equality negation (== <-> !=) ─────────────────────────────

            if (type == Mutator.MutatorType.EQ_NEGATE) {
                for (int idx = 0; (idx = line.indexOf("==", idx)) >= 0; idx += 2) {
                    if (idx > 0 && line.charAt(idx - 1) == '!') continue; // part of !==
                    if (!inProtected(line, idx, isPython))
                        points.add(new MutationPoint(file, lineNum + 1, idx, "==", "!=", type));
                }
                for (int idx = 0; (idx = line.indexOf("!=", idx)) >= 0; idx += 2) {
                    if (!inProtected(line, idx, isPython))
                        points.add(new MutationPoint(file, lineNum + 1, idx, "!=", "==", type));
                }
            }

            // ── LOR: Logical Operator Replacement ─────────────────────────────

            if (type == Mutator.MutatorType.LOGICAL_AND_TO_OR) {
                if (isPython) {
                    for (int idx = 0; (idx = line.indexOf("and", idx)) >= 0; ) {
                        if (isKeywordAt(line, idx, 3) && !inProtected(line, idx, true))
                            points.add(new MutationPoint(file, lineNum + 1, idx, "and", "or", type));
                        idx += 3;
                    }
                } else {
                    for (int idx = 0; (idx = line.indexOf("&&", idx)) >= 0; idx += 2) {
                        if (!inProtected(line, idx, false))
                            points.add(new MutationPoint(file, lineNum + 1, idx, "&&", "||", type));
                    }
                }
            }

            if (type == Mutator.MutatorType.LOGICAL_OR_TO_AND) {
                if (isPython) {
                    for (int idx = 0; (idx = line.indexOf("or", idx)) >= 0; ) {
                        if (isKeywordAt(line, idx, 2) && !inProtected(line, idx, true))
                            points.add(new MutationPoint(file, lineNum + 1, idx, "or", "and", type));
                        idx += 2;
                    }
                } else {
                    for (int idx = 0; (idx = line.indexOf("||", idx)) >= 0; idx += 2) {
                        if (!inProtected(line, idx, false))
                            points.add(new MutationPoint(file, lineNum + 1, idx, "||", "&&", type));
                    }
                }
            }

            // Python-only: remove logical `not` (not x → x)
            if (type == Mutator.MutatorType.INVERT_NEGS && isPython) {
                for (int idx = 0; (idx = line.indexOf("not", idx)) >= 0; ) {
                    int end = idx + 3; // length of "not"
                    // must be followed by a space (not x), not "not=" or "note" etc.
                    if (end < line.length() && line.charAt(end) == ' '
                            && isKeywordAt(line, idx, 3)
                            && !inProtected(line, idx, true)) {
                        points.add(new MutationPoint(file, lineNum + 1, idx, "not ", "", type));
                    }
                    idx = end;
                }
            }
        }

        return points;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static boolean inProtected(String line, int position, boolean isPython) {
        return isPython ? isInStringOrCommentPython(line, position) : isInStringOrCommentCLike(line, position);
    }

    private static boolean isKeywordAt(String line, int idx, int len) {
        if (idx > 0 && (Character.isLetterOrDigit(line.charAt(idx - 1)) || line.charAt(idx - 1) == '_')) return false;
        int end = idx + len;
        if (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '_')) return false;
        return true;
    }

    /**
     * String/comment guard for C-like languages (C#, Java, JS).
     * Treats {@code //} as a line-comment start.
     */
    static boolean isInStringOrCommentCLike(String line, int position) {
        boolean inString = false;
        char stringDelimiter = '\0';

        for (int i = 0; i < position; i++) {
            char c = line.charAt(i);
            if (i > 0 && line.charAt(i - 1) == '\\') continue;
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

        if (inString) return true;

        int commentStart = line.indexOf("//");
        return commentStart >= 0 && commentStart < position;
    }

    /**
     * String/comment guard for Python.
     * Uses {@code #} as a line-comment start (not {@code //}, which is floor division in Python).
     * Handles triple-quoted strings within a single line.
     */
    static boolean isInStringOrCommentPython(String line, int position) {
        boolean inString = false;
        char stringDelimiter = '\0';
        boolean inTriple = false;
        int i = 0;

        while (i < position) {
            char c = line.charAt(i);

            if (inString) {
                if (c == '\\' && i + 1 < line.length()) {
                    i += 2;
                    continue;
                }
                if (inTriple) {
                    if (c == stringDelimiter && i + 2 < line.length()
                            && line.charAt(i + 1) == stringDelimiter
                            && line.charAt(i + 2) == stringDelimiter) {
                        inString = false;
                        i += 3;
                        continue;
                    }
                } else {
                    if (c == stringDelimiter) {
                        inString = false;
                    }
                }
            } else {
                if ((c == '"' || c == '\'') && i + 2 < line.length()
                        && line.charAt(i + 1) == c && line.charAt(i + 2) == c) {
                    inString = true;
                    inTriple = true;
                    stringDelimiter = c;
                    i += 3;
                    continue;
                }
                if (c == '"' || c == '\'') {
                    inString = true;
                    inTriple = false;
                    stringDelimiter = c;
                    i++;
                    continue;
                }
                if (c == '#') {
                    return true;
                }
            }
            i++;
        }

        return inString;
    }
}
