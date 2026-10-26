# Racket Mutation Operators

Applies to `.rkt` files, scanned by `RacketMutationScanner.java` with the ISL
grammar (`ISL.g4`), adapted for `#lang racket`.

Racket has no `null`, so operators that replace a value with "null" use `'()`,
the empty list. Booleans are `#t` and `#f`.

Nothing inside a macro definition (`define-syntax-rule`, `define-syntax`,
`let-syntax` and similar) is mutated, by built-in operators or spec rules.

## Built-in operators

Use these names in `mutator_type`. `ALL` runs every one of them.

### Arithmetic

| Operator | What it changes | Example |
|----------|-----------------|---------|
| `AOR_PLUS_TO_MINUS` | `+` → `-` | `(+ a b)` → `(- a b)` |
| `AOR_MINUS_TO_PLUS` | `-` → `+` | `(- a b)` → `(+ a b)` |
| `AOR_MULTIPLY_TO_DIVIDE` | `*` → `/` | `(* a b)` → `(/ a b)` |
| `AOR_DIVIDE_TO_MULTIPLY` | `/` → `*` | `(/ a b)` → `(* a b)` |
| `AOR_MODULO_TO_MULTIPLY` | `modulo` or `remainder` → `*` | `(modulo a b)` → `(* a b)` |
| `AOR_INCREMENT_TO_DECREMENT` | `add1` → `sub1` | `(add1 x)` → `(sub1 x)` |
| `AOR_DECREMENT_TO_INCREMENT` | `sub1` → `add1` | `(sub1 x)` → `(add1 x)` |
| `INEG_INVERT_NEGATIVE` | Removes a unary minus | `(- x)` → `x` |

### Bitwise

| Operator | What it changes | Example |
|----------|-----------------|---------|
| `BOR_AND_TO_OR` | `bitwise-and` → `bitwise-ior` | `(bitwise-and a b)` → `(bitwise-ior a b)` |
| `BOR_OR_TO_AND` | `bitwise-ior` → `bitwise-and` | `(bitwise-ior a b)` → `(bitwise-and a b)` |

### Comparisons

| Operator | What it changes | Example |
|----------|-----------------|---------|
| `ROR_GT_TO_GE` | `>` → `>=` | `(> a b)` → `(>= a b)` |
| `ROR_GE_TO_GT` | `>=` → `>` | `(>= a b)` → `(> a b)` |
| `ROR_LT_TO_LE` | `<` → `<=` | `(< a b)` → `(<= a b)` |
| `ROR_LE_TO_LT` | `<=` → `<` | `(<= a b)` → `(< a b)` |
| `EQ_NEGATE` | Negates an equality test: `=`, `equal?`, `eqv?`, `eq?`, `string=?` or `char=?` | `(= a b)` → `(not (= a b))` |
| `JS_STRICT_EQ_TO_LOOSE_EQ` | Identity test → structural equality | `(eq? a b)` → `(equal? a b)` |

### Logic and conditions

| Operator | What it changes | Example |
|----------|-----------------|---------|
| `LOGICAL_AND_TO_OR` | `and` → `or` | `(and p q)` → `(or p q)` |
| `LOGICAL_OR_TO_AND` | `or` → `and` | `(or p q)` → `(and p q)` |
| `REMOVE_LOGICAL_NEGATION` | Removes `not` | `(not p)` → `p` |
| `CONDITIONAL_TRUE` | An `if` condition → `#t` | `(if flag 1 2)` → `(if #t 1 2)` |
| `CONDITIONAL_FALSE` | An `if` condition → `#f` | `(if flag 1 2)` → `(if #f 1 2)` |
| `NEGATE_CONDITIONALS` | Negates an `if` condition | `(if (> x y) 1 2)` → `(if (not (> x y)) 1 2)` |
| `REMOVE_CONDITIONALS_EQUAL_IF` | An equality condition → `#t` | `(if (= n 0) …)` → `(if #t …)` |
| `REMOVE_CONDITIONALS_EQUAL_ELSE` | An equality condition → `#f` | `(if (= n 0) …)` → `(if #f …)` |
| `REMOVE_CONDITIONALS_ORDER_IF` | An order condition → `#t` | `(if (< n 0) …)` → `(if #t …)` |
| `REMOVE_CONDITIONALS_ORDER_ELSE` | An order condition → `#f` | `(if (< n 0) …)` → `(if #f …)` |

The four `REMOVE_CONDITIONALS_*` operators apply to conditions in `if`, `cond`,
`when` and `unless`. Equality conditions use `=`, `eq?`, `eqv?`, `equal?`,
`zero?`, `null?`, `empty?`, `string=?` or `char=?` (and their `-ci` versions);
order conditions use `<`, `>`, `<=`, `>=`, `positive?`, `negative?`, or the
`string` and `char` comparisons such as `string<?` and `char>=?`.

### Statements and calls

| Operator | What it changes | Example |
|----------|-----------------|---------|
| `VML_VOID_METHOD_CALL` | Removes a call whose result is discarded | `(define (f x) (displayln x) x)` → `(define (f x) x)` |
| `STATEMENT_MUTATION` | Replaces that call with `(void)` | `(define (f x) (displayln x) x)` → `(define (f x) (void) x)` |
| `NON_VOID_METHOD_CALLS` | A call whose result is used → a default value | `(add1 x)` → `0` |
| `CONSTRUCTOR_CALLS` | A struct constructor call → `'()` | `(posn 0 0)` → `'()` |

A call's result is discarded when the call is any form but the last in a
function, `let`, `lambda`, `when`, `unless` or `begin` body (in `begin0`, every
form but the first). `VML_VOID_METHOD_CALL`, `STATEMENT_MUTATION` and
`NON_VOID_METHOD_CALLS` all use this, so a call is either a statement or a
used value, never both.

`NON_VOID_METHOD_CALLS` picks the default from the function's name: `#f` for
predicates (names ending in `?`), `0` for numeric functions such as `add1` or
`sqr`, `""` for string functions such as `string-append`, and `'()` otherwise.

`CONSTRUCTOR_CALLS` covers constructors of structs declared in the file
(`(struct posn …)` declares `posn`, `(define-struct posn …)` declares
`make-posn`) and built-in constructors such as `make-hash`, `make-vector`,
`make-string` and `make-posn`. Other functions whose names start with `make-`,
like your own `make-report`, are ordinary calls.

### Return values

These mutate what a function returns, which in Racket is the last expression of
its body. The `RVM_*` operators only look at that expression itself;
`EMPTY_RETURNS` also goes into each branch of an `if` or `cond` there.

| Operator | What it changes | Example |
|----------|-----------------|---------|
| `RVM_TRUE_TO_FALSE` | Returned `#t` → `#f` | `(define (yes) #t)` → `(define (yes) #f)` |
| `RVM_FALSE_TO_TRUE` | Returned `#f` → `#t` | `(define (no) #f)` → `(define (no) #t)` |
| `RVM_ZERO_TO_ONE` | Returned `0` → `1` | `(define (zero) 0)` → `(define (zero) 1)` |
| `RVM_NONZERO_TO_ZERO` | Returned non-zero integer → `0` | `(define (seven) 7)` → `(define (seven) 0)` |
| `RVM_PRIMITIVE_TO_ZERO` | Returned number literal → `0` | `(define (seven) 7)` → `(define (seven) 0)` |
| `RVM_OBJECT_TO_NULL` | Returned expression or variable → `'()` | `(define (add a b) (+ a b))` → `(define (add a b) '())` |
| `EMPTY_RETURNS` | Returned string or list → empty | `"zero"` → `""`, `(list x y)` → `'()` |

### Number literals

| Operator | What it changes | Example |
|----------|-----------------|---------|
| `NLT_INTEGER_TO_FLOAT` | Integer literal → float | `5` → `5.0` |
| `NLT_FLOAT_TO_INTEGER` | Float literal → integer | `2.0` → `2` |

## Spec rules (MSL)

With `mutator_type=CUSTOM`, the rules in a spec file run instead. Spec files
always use the canonical operator, and the Racket adapter matches and writes
Racket's own form.

### Operators (`OperatorMutations.txt`)

| Spec operator | In Racket code |
|---------------|----------------|
| `+` `-` `*` `/` | `+` `-` `*` `/` |
| `%` | `modulo` or `remainder` (writes `modulo`) |
| `//` | `quotient` or `(floor (/ a b))` (writes `quotient`) |
| `**` | `expt` |
| `&` `\|` `^` | `bitwise-and` `bitwise-ior` `bitwise-xor` |
| `<<` | `(arithmetic-shift a b)` |
| `>>` | `(arithmetic-shift a (- b))` |
| `>>>` | `(arithmetic-shift (bitwise-and a #xFFFFFFFF) (- b))` |
| `<` `<=` `>` `>=` | `<` `<=` `>` `>=` |
| `==` | `=`, `equal?`, `eqv?`, `eq?`, `string=?` or `char=?` (writes `=`) |
| `!=` | `(not (= a b))`; `==` ↔ `!=` adds or removes the `not` |
| `&&` `\|\|` | `and` `or` |

### Statements (`StatementMutation.txt`)

| Spec rule | In Racket code |
|-----------|----------------|
| `{invocation_statement}` → `;` | A call whose result is discarded → `(void)` |
| `{return_value}` → `{empty_value}` | Each returned value → the empty value of its type: `0`, `#f`, `""` or `'()` |
| `{constructor_call}` → `null` | `(posn 0 0)` → `'()` |
| `{non_void_method_call}` → `{default_value}` | `(helper a)` → a default chosen by name, as in `NON_VOID_METHOD_CALLS` |
| `{equality_condition}` → `true` / `false` | `(= a b)` → `#t` / `#f` |
| `{order_condition}` → `true` / `false` | `(< a b)` → `#t` / `#f` |

The string rules in `StringMutation.txt` (`string.IsNullOrEmpty`) are C#-only
and find nothing in Racket.

## Known limitations

- **Structs declared in another file** aren't recognized as constructors,
  because the scanner reads one file at a time.
- **`JS_STRICT_EQ_TO_LOOSE_EQ`** keeps its JavaScript name because the same
  operator name is shared across languages.
