# Quick Start: Choosing Your Mutation Testing Strategy

## Option 1: Let the Tool Decide (Recommended)

**Config file:**
mutation_strategy=AUTO
mutator_type=NLT_INTEGER_TO_FLOAT

**What happens:** Tool picks AST for this numeric literal mutation

---

## Option 2: Force Dictionary (Fast)

**Config file:**

mutation_strategy=DICTIONARY
mutator_type=AOR_PLUS_TO_MINUS

**What happens:** Regex finds all + and mutates to -

---

## Option 3: Force AST (Accurate)

**Config file:**

mutation_strategy=AST
mutator_type=ROR_GT_TO_LT

**What happens:** Parses code, finds > operators in expressions only

---

## Option 4: Use MSL (Custom Rules)

**Config file:**

mutation_strategy=MSL
msl_spec_file=specs/math-operations.spec

**Spec file** (specs/math-operations.spec):

Math Operations
Replace: {quantity} + {quantity}
With: {quantity} - {quantity}
And: Inverse
End

---

## Testing All Strategies

**Run comparison:**
```bash
.\test-all-strategies.bat
```

This runs the same mutations with all 4 strategies and compares results.

