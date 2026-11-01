package mutationtester.model;

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

        // Return Value Mutations
        RVM_TRUE_TO_FALSE,
        RVM_FALSE_TO_TRUE,
        RVM_ZERO_TO_ONE,
        RVM_NONZERO_TO_ZERO,

        // Extended Return Value Mutations
        RVM_OBJECT_TO_NULL,
        RVM_PRIMITIVE_TO_ZERO,
        RVM_STRING_TO_EMPTY,
        EMPTY_RETURNS,

        // Void Method Call removal
        VML_VOID_METHOD_CALL,
        CONSTRUCTOR_CALLS,
        NON_VOID_METHOD_CALLS,

        // Increment / Decrement swap
        AOR_INCREMENT_TO_DECREMENT,
        AOR_DECREMENT_TO_INCREMENT,

        // Invert Negatives
        INEG_INVERT_NEGATIVE,

        // JavaScript-focused PIT-style mutations
        REMOVE_LOGICAL_NEGATION,
        JS_STRICT_EQ_TO_LOOSE_EQ,
        JS_STRICT_NE_TO_LOOSE_NE,

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
        REMOVE_CONDITIONALS_EQUAL_IF,
        REMOVE_CONDITIONALS_EQUAL_ELSE,
        REMOVE_CONDITIONALS_ORDER_IF,
        REMOVE_CONDITIONALS_ORDER_ELSE,
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

        CUSTOM  // spec-driven branch; config must also set custom_mutation_spec=<path>

    }
}
