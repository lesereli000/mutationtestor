package mutationtester.msl;

/** A literal's or type name's placeholder type, size word (or null), and whether it is a type name. */
public record ValueKind(String type, String size, boolean typeName) {

    /** True when this value fits the placeholder type: {integer} also takes hex, {float} also takes 4.2e1. */
    public boolean fits(String placeholderType) {
        return switch (placeholderType) {
            case "number" -> !typeName && isNumber();
            case "integer" -> type.equals("integer") || type.equals("based_integer");
            case "float" -> type.equals("float") || type.equals("exponential");
            default -> type.equals(placeholderType) && !typeName;
        };
    }

    public boolean isNumber() {
        return switch (type) {
            case "integer", "based_integer", "float", "exponential", "rational", "complex" -> true;
            default -> false;
        };
    }
}
