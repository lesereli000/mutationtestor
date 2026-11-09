package mutationtester.scanner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import mutationtester.msl.ValueKind;

/** Per-language literal and numeric type spellings: their kind and size, and conversions between them. */
final class Literals {

    enum Lang { JAVA, CSHARP, JAVASCRIPT, PYTHON, RACKET }

    private Literals() {
    }

    // ── Numeric type names ───────────────────────────────────────────────

    private static final Map<String, String> JAVA_TYPES = Map.of(
            "byte", "int8", "short", "int16", "int", "int32", "long", "int64",
            "float", "float32", "double", "float64");

    private static final Map<String, String> CSHARP_TYPES = Map.ofEntries(
            Map.entry("sbyte", "int8"), Map.entry("byte", "uint8"), Map.entry("short", "int16"),
            Map.entry("ushort", "uint16"), Map.entry("int", "int32"), Map.entry("uint", "uint32"),
            Map.entry("long", "int64"), Map.entry("ulong", "uint64"), Map.entry("float", "float32"),
            Map.entry("double", "float64"), Map.entry("decimal", "decimal"));

    private static final Map<String, String> PYTHON_TYPES = Map.of("int", "bigint", "float", "float64");

    private static Map<String, String> typeNames(Lang lang) {
        return switch (lang) {
            case JAVA -> JAVA_TYPES;
            case CSHARP -> CSHARP_TYPES;
            case PYTHON -> PYTHON_TYPES;
            default -> Map.of();
        };
    }

    private static boolean isFloatSize(String size) {
        return size.startsWith("float") || size.equals("decimal");
    }

    // ── Classifying ──────────────────────────────────────────────────────

    /** The literal or numeric type name ctx is (through single-child wrappers), or null. */
    static ValueKind valueKind(Lang lang, ParseTree ctx) {
        ParseTree p = ctx;
        while (p != null && !(p instanceof TerminalNode) && p.getChildCount() == 1) p = p.getChild(0);
        if (!(p instanceof TerminalNode token)) return null;
        String text = token.getText();
        String size = typeNames(lang).get(text);
        if (size != null) {
            return new ValueKind(isFloatSize(size) ? "float" : "integer", size, true);
        }
        return classify(lang, text);
    }

    /** The kind of a literal token's text, or null when it isn't a literal. */
    static ValueKind classify(Lang lang, String text) {
        if (text.isEmpty()) return null;
        ValueKind text_ = textKind(lang, text);
        if (text_ != null) return text_;
        if (isBoolean(lang, text)) return new ValueKind("boolean", null, false);
        Number n = parse(lang, text);
        return n == null ? null : new ValueKind(n.type, n.size, false);
    }

    private static boolean isBoolean(Lang lang, String text) {
        return switch (lang) {
            case PYTHON -> text.equals("True") || text.equals("False");
            case RACKET -> text.equals("#t") || text.equals("#f") || text.equals("#true") || text.equals("#false")
                    || text.equals("#T") || text.equals("#F");
            default -> text.equals("true") || text.equals("false");
        };
    }

    private static ValueKind textKind(Lang lang, String text) {
        char first = text.charAt(0);
        switch (lang) {
            case JAVA, CSHARP -> {
                if (first == '\'' && text.length() >= 3 && text.endsWith("'")) return new ValueKind("char", null, false);
                int i = 0;
                while (i < text.length() && i < 2 && (text.charAt(i) == '@' || text.charAt(i) == '$')) i++;
                if (i < text.length() && text.charAt(i) == '"' && text.endsWith("\"") && text.length() - i >= 2) {
                    return new ValueKind("string", null, false);
                }
            }
            case JAVASCRIPT -> {
                if ((first == '"' || first == '\'') && text.length() >= 2 && text.charAt(text.length() - 1) == first) {
                    return new ValueKind("string", null, false);
                }
            }
            case PYTHON -> {
                int i = 0;
                boolean bytes = false;
                while (i < text.length() && i < 3 && "rRbBuUfF".indexOf(text.charAt(i)) >= 0) {
                    if (text.charAt(i) == 'b' || text.charAt(i) == 'B') bytes = true;
                    i++;
                }
                if (i < text.length() && (text.charAt(i) == '"' || text.charAt(i) == '\'')
                        && text.charAt(text.length() - 1) == text.charAt(i)) {
                    return new ValueKind(bytes ? "bytes" : "string", null, false);
                }
            }
            case RACKET -> {
                if (text.startsWith("#\\") && text.length() > 2) return new ValueKind("char", null, false);
                if (first == '"' && text.length() >= 2 && text.endsWith("\"")) return new ValueKind("string", null, false);
            }
        }
        return null;
    }

    /** A parsed number literal: its kind, size, value and spelling parts. */
    private record Number(String type, String size, BigDecimal value, BigDecimal imaginary) {}

    private static Number parse(Lang lang, String text) {
        String t = text.replace("_", "");
        String sign = "";
        if (lang == Lang.RACKET && (t.startsWith("+") || t.startsWith("-")) && t.length() > 1) {
            sign = t.substring(0, 1);
            t = t.substring(1);
        }
        try {
            Number n = parseUnsigned(lang, t);
            if (n == null || !sign.equals("-")) return n;
            return new Number(n.type, n.size, n.value.negate(), n.imaginary);
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    private static Number parseUnsigned(Lang lang, String t) {
        if (t.isEmpty() || !(Character.isDigit(t.charAt(0)) || t.charAt(0) == '.' || t.charAt(0) == '#')) return null;
        String lower = t.toLowerCase();
        // Bases: 0x / 0o / 0b, Java's and JavaScript's legacy 0777, Racket's #x / #o / #b
        int radix = 0;
        String digits = null;
        if (lang == Lang.RACKET && lower.length() > 2 && lower.charAt(0) == '#') {
            radix = switch (lower.charAt(1)) { case 'x' -> 16; case 'o' -> 8; case 'b' -> 2; default -> 0; };
            digits = lower.substring(2);
            if (radix == 0) return null;
        } else if (lower.startsWith("0x") && !lower.contains("p")) {
            radix = 16;
            digits = lower.substring(2);
        } else if (lower.startsWith("0b") && lang != Lang.RACKET) {
            radix = 2;
            digits = lower.substring(2);
        } else if (lower.startsWith("0o")) {
            radix = 8;
            digits = lower.substring(2);
        } else if ((lang == Lang.JAVA || lang == Lang.JAVASCRIPT) && lower.length() > 1 && lower.charAt(0) == '0'
                && allDigits(stripIntegerSuffix(lang, lower), 8)) {
            radix = 8;
            digits = lower.substring(1);
        }
        if (radix != 0) {
            String suffix = integerSuffix(lang, digits);
            String body = digits.substring(0, digits.length() - suffix.length());
            if (body.isEmpty() || !allDigits(body, radix)) return null;
            return new Number("based_integer", integerSize(lang, suffix),
                    new BigDecimal(new BigInteger(body, radix)), null);
        }
        if (lang == Lang.RACKET && lower.indexOf('/') > 0) {
            String[] parts = { lower.substring(0, lower.indexOf('/')), lower.substring(lower.indexOf('/') + 1) };
            if (!allDigits(parts[0], 10) || !allDigits(parts[1], 10)) return null;
            BigDecimal value = new BigDecimal(parts[0]).divide(new BigDecimal(parts[1]), MathContext.DECIMAL64);
            return new Number("rational", null, value, null);
        }
        if (lang == Lang.PYTHON && lower.endsWith("j")) {
            BigDecimal imaginary = new BigDecimal(lower.substring(0, lower.length() - 1));
            return new Number("complex", null, BigDecimal.ZERO, imaginary);
        }
        String suffix = integerSuffix(lang, lower);
        if (!suffix.isEmpty() || allDigits(lower, 10)) {
            String body = lower.substring(0, lower.length() - suffix.length());
            if (body.isEmpty() || !allDigits(body, 10)) return floatNumber(lang, lower);
            return new Number("integer", integerSize(lang, suffix), new BigDecimal(body), null);
        }
        return floatNumber(lang, lower);
    }

    private static Number floatNumber(Lang lang, String lower) {
        String suffix = "";
        if ((lang == Lang.JAVA || lang == Lang.CSHARP) && !lower.isEmpty()
                && "fdm".indexOf(lower.charAt(lower.length() - 1)) >= 0) {
            suffix = lower.substring(lower.length() - 1);
            if (lang == Lang.JAVA && suffix.equals("m")) return null;
        }
        String body = lower.substring(0, lower.length() - suffix.length());
        if (body.isEmpty() || !isDecimalFloat(body)) return null;
        if (lang == Lang.RACKET && body.indexOf('e') >= 0) return null;
        BigDecimal value = new BigDecimal(body.startsWith(".") ? "0" + body : body);
        String type = body.indexOf('e') >= 0 ? "exponential" : "float";
        String size = switch (lang) {
            case JAVA -> suffix.equals("f") ? "float32" : "float64";
            case CSHARP -> suffix.equals("f") ? "float32" : suffix.equals("m") ? "decimal" : "float64";
            default -> "float64";
        };
        // 4f and 4m are fractional types written without a point.
        if (body.indexOf('.') < 0 && body.indexOf('e') < 0 && suffix.isEmpty()) return null;
        return new Number(type, size, value, null);
    }

    private static boolean isDecimalFloat(String s) {
        int i = 0;
        boolean digits = false;
        while (i < s.length() && Character.isDigit(s.charAt(i))) { i++; digits = true; }
        if (i < s.length() && s.charAt(i) == '.') {
            i++;
            while (i < s.length() && Character.isDigit(s.charAt(i))) { i++; digits = true; }
        }
        if (!digits) return false;
        if (i < s.length() && s.charAt(i) == 'e') {
            i++;
            if (i < s.length() && (s.charAt(i) == '+' || s.charAt(i) == '-')) i++;
            int start = i;
            while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
            if (i == start) return false;
        }
        return i == s.length();
    }

    private static boolean allDigits(String s, int radix) {
        if (s.isEmpty()) return false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.digit(s.charAt(i), radix) < 0) return false;
        }
        return true;
    }

    private static String stripIntegerSuffix(Lang lang, String s) {
        return s.substring(0, s.length() - integerSuffix(lang, s).length());
    }

    /** The size suffix at the end of an integer literal: l, u, ul, n. */
    private static String integerSuffix(Lang lang, String s) {
        return switch (lang) {
            case JAVA -> s.endsWith("l") ? "l" : "";
            case CSHARP -> s.endsWith("ul") || s.endsWith("lu") ? s.substring(s.length() - 2)
                    : s.endsWith("u") || s.endsWith("l") ? s.substring(s.length() - 1) : "";
            case JAVASCRIPT -> s.endsWith("n") ? "n" : "";
            default -> "";
        };
    }

    private static String integerSize(Lang lang, String suffix) {
        return switch (lang) {
            case JAVA -> suffix.isEmpty() ? "int32" : "int64";
            case CSHARP -> switch (suffix) {
                case "u" -> "uint32";
                case "l" -> "int64";
                case "ul", "lu" -> "uint64";
                default -> "int32";
            };
            case JAVASCRIPT -> suffix.isEmpty() ? "float64" : "bigint";
            default -> "bigint";
        };
    }

    /** True when a Java/C# literal must stay an integer: an index, or an integral declaration's value. */
    static boolean needsInteger(ParseTree literal) {
        ParseTree child = literal;
        for (ParseTree p = literal.getParent(); p != null; child = p, p = p.getParent()) {
            String rule = p.getClass().getSimpleName();
            if (rule.equals("StatementContext") || rule.equals("BlockContext") || rule.endsWith("_statementContext")
                    || rule.equals("Embedded_statementContext")) {
                return false;
            }
            for (int i = 1; i < p.getChildCount(); i++) {
                if (p.getChild(i) == child && p.getChild(i - 1).getText().equals("[")) return true;
            }
            if (rule.toLowerCase().contains("declaration")) {
                for (int i = 0; i < p.getChildCount(); i++) {
                    String size = JAVA_TYPES.containsKey(p.getChild(i).getText()) ? JAVA_TYPES.get(p.getChild(i).getText())
                            : CSHARP_TYPES.get(p.getChild(i).getText());
                    if (size != null) return !isFloatSize(size);
                }
            }
        }
        return false;
    }

    // ── Converting ───────────────────────────────────────────────────────

    /** {@code text} written as {@code toType} (and {@code toSize}); null if the language can't. */
    static String convert(Lang lang, String text, ValueKind from, String toType, String toSize) {
        if (from.typeName()) return convertTypeName(lang, from, toType, toSize);
        if (from.isNumber()) {
            Number n = parse(lang, text);
            return n == null ? null : writeNumber(lang, n, toType, toSize);
        }
        return convertText(lang, text, from.type(), toType);
    }

    private static String convertTypeName(Lang lang, ValueKind from, String toType, String toSize) {
        boolean toFloat = toType.equals("float");
        if (!toFloat && !toType.equals("integer")) return null;
        String size = toSize;
        if (size == null) {
            if (from.type().equals(toType)) return null;
            size = switch (lang) {
                case PYTHON -> toFloat ? "float64" : "bigint";
                default -> toFloat ? "float64" : "int64";
            };
        }
        if (isFloatSize(size) != toFloat) return null;
        for (var e : typeNames(lang).entrySet()) {
            if (e.getValue().equals(size)) return e.getKey();
        }
        return null;
    }

    private static String writeNumber(Lang lang, Number n, String toType, String toSize) {
        if (n.imaginary != null && !toType.equals("complex")) return null;
        BigDecimal value = n.value;
        switch (toType) {
            case "integer", "based_integer" -> {
                String size = toSize != null ? toSize : n.type.equals("integer") || n.type.equals("based_integer")
                        ? n.size : defaultIntegerSize(lang);
                if (size == null || isFloatSize(size) && lang != Lang.JAVASCRIPT) return null;
                BigInteger whole = value.setScale(0, RoundingMode.DOWN).toBigIntegerExact();
                String suffix = integerSuffixFor(lang, size);
                if (suffix == null) return null;
                if (toType.equals("based_integer")) {
                    if (whole.signum() < 0) return null;
                    return (lang == Lang.RACKET ? "#x" : "0x") + whole.toString(16).toUpperCase() + suffix;
                }
                return whole + suffix;
            }
            case "float", "exponential" -> {
                if (lang == Lang.RACKET && toType.equals("exponential")) return null;
                String size = toSize != null ? toSize : n.size != null && isFloatSize(n.size) ? n.size : "float64";
                if (!isFloatSize(size)) return null;
                String suffix = floatSuffixFor(lang, size);
                if (suffix == null) return null;
                return (toType.equals("float") ? plainDecimal(value) : scientific(value)) + suffix;
            }
            case "rational" -> {
                if (lang != Lang.RACKET || n.type.equals("integer") || n.type.equals("based_integer")) return null;
                BigDecimal stripped = value.stripTrailingZeros();
                BigInteger numerator = stripped.unscaledValue();
                BigInteger denominator = BigInteger.TEN.pow(Math.max(stripped.scale(), 0));
                BigInteger gcd = numerator.gcd(denominator);
                return numerator.divide(gcd) + "/" + denominator.divide(gcd);
            }
            case "complex" -> {
                if (lang != Lang.PYTHON || n.imaginary != null) return null;
                return plainOrWhole(value) + "j";
            }
            default -> {
                return null;
            }
        }
    }

    private static String defaultIntegerSize(Lang lang) {
        return switch (lang) {
            case JAVA, CSHARP -> "int32";
            case JAVASCRIPT -> "float64";
            default -> "bigint";
        };
    }

    /** The suffix that gives an integer literal this size, or null when the language has none. */
    private static String integerSuffixFor(Lang lang, String size) {
        return switch (lang) {
            case JAVA -> size.equals("int64") ? "L" : size.equals("int32") ? "" : null;
            case CSHARP -> switch (size) {
                case "int32" -> "";
                case "uint32" -> "u";
                case "int64" -> "L";
                case "uint64" -> "UL";
                default -> null;
            };
            case JAVASCRIPT -> size.equals("bigint") ? "n" : size.equals("float64") ? "" : null;
            default -> size.equals("bigint") ? "" : null;
        };
    }

    /** The suffix that gives a fractional literal this size, or null when the language has none. */
    private static String floatSuffixFor(Lang lang, String size) {
        return switch (lang) {
            case JAVA -> size.equals("float32") ? "f" : size.equals("float64") ? "" : null;
            case CSHARP -> size.equals("float32") ? "f" : size.equals("decimal") ? "m" : "";
            default -> size.equals("float64") ? "" : null;
        };
    }

    private static String plainDecimal(BigDecimal value) {
        String s = value.stripTrailingZeros().toPlainString();
        return s.indexOf('.') < 0 ? s + ".0" : s;
    }

    private static String plainOrWhole(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** 42 as 4.2e1, 0.5 as 5.0e-1. */
    private static String scientific(BigDecimal value) {
        if (value.signum() == 0) return "0.0e0";
        BigDecimal stripped = value.stripTrailingZeros();
        String digits = stripped.unscaledValue().abs().toString();
        int exponent = digits.length() - 1 - stripped.scale();
        String mantissa = digits.length() == 1 ? digits + ".0" : digits.charAt(0) + "." + digits.substring(1);
        return (value.signum() < 0 ? "-" : "") + mantissa + "e" + exponent;
    }

    /** Between chars, strings and bytes: 'a' as "a", "a" as 'a', b"x" as "x". */
    private static String convertText(Lang lang, String text, String fromType, String toType) {
        if (fromType.equals(toType)) return null;
        String content = switch (fromType) {
            case "char" -> lang == Lang.RACKET ? racketCharContent(text) : text.substring(1, text.length() - 1);
            case "string" -> stringContent(text);
            case "bytes" -> stringContent(text.substring(1));
            default -> null;
        };
        if (content == null) return null;
        return switch (toType) {
            case "string" -> "\"" + content + "\"";
            case "char" -> {
                if (lang == Lang.JAVASCRIPT || lang == Lang.PYTHON || content.isEmpty()
                        || content.length() > (content.startsWith("\\") ? 2 : 1)) {
                    yield null;
                }
                yield lang == Lang.RACKET ? "#\\" + content : "'" + content + "'";
            }
            case "bytes" -> lang == Lang.PYTHON ? "b\"" + content + "\"" : null;
            default -> null;
        };
    }

    private static String stringContent(String text) {
        int start = 0;
        while (start < text.length() && text.charAt(start) != '"' && text.charAt(start) != '\'') start++;
        if (start >= text.length() - 1) return null;
        char quote = text.charAt(start);
        if (text.startsWith("" + quote + quote + quote, start)) return null;
        return text.substring(start + 1, text.length() - 1);
    }

    private static String racketCharContent(String text) {
        String name = text.substring(2);
        return name.length() == 1 ? name : name.equals("space") ? " " : null;
    }
}
