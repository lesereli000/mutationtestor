package mutationtester.scanner;

import mutationtester.msl.CstNodes;

/**
 * PIT-style empty and default values for Java and C#, chosen from a declared
 * type, or from the returned expression when the type isn't known.
 */
final class TypedEmptyValues {

    private TypedEmptyValues() {
    }

    /**
     * Empty value for a declared type: 0 for numbers, false for booleans, ""
     * for strings, Optional.empty() for Java Optionals, and null (C#: default)
     * otherwise. Returns null for void (and C#'s Task).
     */
    static String forType(String type, boolean csharp) {
        String t = type.strip();
        if (csharp) {
            String inner = taskResultType(t);
            if (inner != null) t = inner;
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
                if (!csharp && isOptional(t)) yield "Optional.empty()";
                yield csharp ? "default" : "null";
            }
        };
    }

    /** T for Task&lt;T&gt; or ValueTask&lt;T&gt;, optionally namespace-qualified; else null. */
    private static String taskResultType(String t) {
        String rest = t.startsWith("System.Threading.Tasks.") ? t.substring("System.Threading.Tasks.".length()) : t;
        for (String prefix : new String[]{"ValueTask<", "Task<"}) {
            if (rest.startsWith(prefix) && rest.endsWith(">") && rest.length() > prefix.length() + 1) {
                return rest.substring(prefix.length(), rest.length() - 1).strip();
            }
        }
        return null;
    }

    private static boolean isOptional(String t) {
        String rest = t.startsWith("java.util.") ? t.substring("java.util.".length()) : t;
        return rest.equals("Optional") || (rest.startsWith("Optional<") && rest.endsWith(">"));
    }

    /** Empty value judged from the returned expression itself, when no type is known. */
    static String forExpression(String expr, String nullLiteral, String falseLiteral) {
        String e = expr.strip();
        if (e.equalsIgnoreCase("true") || e.equalsIgnoreCase("false")) return falseLiteral;
        if (CstNodes.isNumberText(e)) return "0";
        if (CstNodes.isQuoted(e)) return "\"\"";
        return nullLiteral;
    }
}
