package mutationtester;

public enum MutationStrategy {
    AUTO,       // Smart selection (AST for NLT, Dictionary for operators)
    DICTIONARY, // Force Dictionary approach (regex-based)
    AST,        // Force AST approach (parse tree)
    MSL         // Use MSL specification files
}