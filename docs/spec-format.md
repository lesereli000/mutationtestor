# Mutation spec format (MSL)

A spec file describes mutations once, in language-neutral syntax. Each
pattern is parsed into an MSL tree, and each language adapter matches that
tree against its own parse tree (CST). Adding a mutation needs only a spec
change; adding support for a new kind of construct is adapter work.

Every run reads its rules from `custom_mutation_spec=<file>`. The old
`mutator_type` setting is deprecated: it is ignored, with a warning.

## Layout

```
<Group name>

    Replace: <pattern>
    With: <pattern>
    [Except: <pattern with {match}>]    ← any number
    [And: Inverse]                      ← also adds With: → Replace:

    Delete: <pattern>
    [Except: ...]

    Insert: <pattern>
    Before: <pattern>                   ← or After:, never both
    [Except: ...]

    Include: <path>                     ← another spec file; its rules are added here

    Group: <name>                       ← a nested group, closed by its own End
        ...rules, or more Group: lines...
    End

End
```

`Include:` links another spec file so rules can live in grouped files. The
config still names one spec (`custom_mutation_spec`); that file can
`Include:` others. Paths are relative to the including file, then the working
directory, then `specs/` (on disk or bundled in the jar). A file already
pulled in is not read again. Example: [`specs/operators-and-statements.spec`](../specs/operators-and-statements.spec)
includes `OperatorMutations.spec` and `StatementMutation.spec`.

The first line names the group; after `End`, the next line names the next
group. Labels in reports are `<group>: <from> → <to>` (`Math Operators: + → -`),
`<group>: delete <target>` or `<group>: insert <tree> before|after <anchor>`.

Inside a group, `Group: <name>` opens a nested group, which can nest again.
Each nested group needs its own `End`; after it, rules belong to the outer
group again. Labels join the names with `>`:

```
Conditionals

    Replace: {quantity} == {quantity}
    With: {quantity} != {quantity}          ← "Conditionals: == → !="

    Group: Boundary
        Replace: {quantity} <= {quantity}
        With: {quantity} < {quantity}       ← "Conditionals > Boundary: <= → <"
    End

End
```

Other lines inside a group are notes and are skipped, as before; only
`Group:` starts a nested group. A top-level group can still run to the end of
the file without an `End`, but a nested group without one is an error naming
its `Group:` line.

## The three rule types

| Rule | What happens to each CST node the first pattern matches |
|------|----------------------------------------------------------|
| `Replace:` / `With:` | Rewritten as the `With:` tree. An operator swap (`{quantity} + {quantity}` → `{quantity} - {quantity}`) changes only the operator; anything else replaces the matched text. A `With:` that is just one of the operands (`With: {quantity}`) is skipped where the value is thrown away (`x++;`, a `for` loop's update): there it would only delete the statement, which Java and C# reject. |
| `Delete:` | Replaced by the language's empty statement (`;`, `pass`, `(void)`). `With: ;` on a `Replace:` does the same. When the pattern is an expression (`Delete: {quantity}++`), the statement it makes up is removed; a match inside a larger expression (`a[i++]`) or a `for` loop header is left alone. |
| `Insert:` + `Before:` / `After:` | The `Insert:` tree is written as a new statement on its own line before or after the anchor, at its indentation. |

## Patterns

Patterns use a C-like syntax that every adapter reads the same way. Each
adapter matches its own language's forms of a construct and writes
replacements in that language; the [coverage matrix](#coverage-matrix) says
which languages have which constructs.

**Expressions**

| Written | Construct | Notes |
|---------|-----------|-------|
| `{a} + {b}` | binary operator | `+ - * / % // ** < <= > >= == != === !== && \|\| ?? & \| ^ << >> >>>`, usual precedence; parentheses only group |
| `!{a}`, `-{a}`, `~{a}` | unary operator | Python `not x`; Racket `(not x)`, `(- x)` |
| `{a}++`, `{a}--` | increment / decrement | add (subtract) one; `++{a}` is the same node. `x++` and `++x` in Java, C# and JavaScript (rewrites keep the form), `x += 1` in Python, `(add1 x)` in Racket |
| `{a} is null`, `{a} is not null` | plain-English check | `is null` means null **or empty**, in each language's idiom (`string.IsNullOrEmpty(s)`, `s is None or s == ""`, ...) |
| `{c} ? {a} : {b}` | conditional expression | Python `a if c else b`; Racket `(if c a b)` |
| `{a} += {b}` (and `-= *= /= %= &= \|= ^= <<= >>= >>>= **= //=`) | compound assignment | |
| `{a}[{i}]`, `{a}[{i}:{j}]` | indexing, slicing | slices: Python `a[1:3]`, C# `a[1..3]`; a missing bound is left out |
| `{a}.{name}`, `{a}.{name}({arguments})` | member access, method call | a call's callee is not also a member access |
| `max({a}, 0)`, `{name}({arguments})` | call | a name or dotted name, or any callee |
| `{a}?.{name}` | null-safe access | C# and JavaScript |
| `{a} in {b}`, `{a} not in {b}` | membership | Python; JavaScript `in` |
| `{a} is a {type}` | type check | `instanceof`, `is`, `isinstance(x, T)`, Racket predicates `(number? x)` |
| `({type}) {a}` | cast | Java and C# |
| `[{a}, {b}]`, `[{arguments}]` | collection literal | arrays, lists; Java `{1, 2}`, C# `{ 1, 2 }` or `[1, 2]`, Racket `(list ...)` |
| `{ {k}: {v}, ... }`, `{ {arguments} }` | object / dictionary literal | JavaScript objects, Python dicts, Racket `(hash ...)` |
| `[{e} for {name} in {xs} if {c}]` | comprehension | Python |
| `({arguments}) => {a}` | lambda | Java `->`, Python `lambda`, Racket `(lambda (...) ...)` |
| `({a}, {b})` | tuple, destructuring target | `({a}, {b}) = {c}` swaps or reorders targets |
| `...{a}` | spread | JavaScript `...xs`, Python `*xs`, C# `..xs` |
| `` `...${a}...` `` | string interpolation | JavaScript template strings, C# `$"..."`; only the holes are matched, the text between them is kept |
| `await {a}` | await | |
| `new {type} { {arguments} }` | object initializer / anonymous class | C# `new T { A = 1 }`, Java `new T() { ... }`; written with no initializers it becomes `new T()` |
| `0`, `"s"`, `true`, `false`, `null` | literal | each adapter spells them (`True`, `#t`, `None`, `'()`) |

**Statements and declarations**

| Written | Construct | Notes |
|---------|-----------|-------|
| `{a} = {b}` | assignment | Racket `(set! x v)` |
| `var {name} = {a}` | variable declaration | keeps the declared type or keyword (`int n = ...`, `const n = ...`); Python's first assignment; Racket `define` and `let` bindings |
| `field {name} = {a}`, `field {name}` | field declaration | Java, C#, JavaScript class fields, Python class attributes |
| `parameter {name} = {a}`, `parameter {name}` | function parameter | defaults: C#, JavaScript, Python |
| `function {name}({arguments}) {block}` | function / method definition | |
| `if (c) s`, `if (c) s else s` | if | an `if` without an `else` only matches ifs that have none (Racket's is `(when c s)`); an `else` may hold another `if` (`elif`) |
| `while (c) s`, `do s while (c)`, `for (i; c; u) s` | loops | a missing `for` part is left empty: `for (; c; )`; Python `while ... else` |
| `for ({name} in {xs}) s`, `... else s` | for-each loop | `for (T x : xs)`, `foreach`, `for...of`, Python `for x in xs`, Racket `(for ((x xs)) ...)` |
| `when (c) s`, `unless (c) s` | when / unless | Racket |
| `return {a}`, `return` | return | Racket: a function's final expression |
| `break`, `continue`, `break {name}` | jumps | |
| `{name}: s` | labeled statement | Java, JavaScript |
| `case {v}: s`, `default: s` | switch case | |
| `case {p} => {v}` | switch-expression / match arm | Java `case 1 -> v`, C# `1 => v`, Racket `(match v (p e))` |
| `throw {a}` | throw | Python `raise`, Racket `(raise ...)` / `(error ...)` |
| `try s catch s finally s` | try | a missing part is left out: `try s catch s` |
| `with ({r}) s` | resource or lock block | Java try-with-resources and `synchronized`, C# `using` and `lock`, Python `with` |
| `yield {a}`, `yield` | yield | C# `yield return` / `yield break` |
| `assert {c}` | assert | Java, Python |
| `delete {a}` | delete | JavaScript `delete`, Python `del` |
| `@{name}` | annotation | Java annotations, C# attributes, Python decorators |
| `where {c}` | query filter | C# LINQ |
| `{ s1; s2 }` | block | matches a block whose statements **start with** `s1 s2` |
| `;` | empty statement | |

A misspelled rule keyword (`Remove:` for `Delete:`) is an error, not a
skipped note.

Operators must be written canonically: `LE`, `leq` and similar are rejected
with a hint (`Use the canonical operator "<="`).

### Placeholders

A placeholder is a typed hole. In `With:` and `Insert:` trees, the n-th
`{quantity}` means the n-th `{quantity}` the `Replace:` (or anchor) tree
matched.

| Placeholder | Matches |
|-------------|---------|
| `{quantity}` | any expression |
| `{statement}` | any statement |
| `{invocation_statement}` | a statement that is just a call |
| `{return_value}` | a returned value |
| `{constructor_call}` | an object creation (`new T(...)`, `Thing()`, `(make-thing ...)`) |
| `{non_void_method_call}` | a call whose result is used |
| `{equality_condition}` / `{order_condition}` | an `if`/`while` (or `?:`, `cond`) condition that compares for equality / order |
| `{condition}` | any condition: of an `if`, loop, conditional expression, comprehension filter or `where` |
| `{name}` | one identifier |
| `{block}` | a whole block or function body (`{ ... }`, an indented suite, `(begin ...)`) |
| `{arguments}` | zero or more call arguments, list elements, parameters or entries, inside a call, collection, lambda, function or object |
| `{collection}` | a collection literal |
| `{lambda}` | a lambda / anonymous function |
| `{type}` | a type name (in a declaration, cast, type check or annotation) |
| `{field}` | a field declaration |
| `{number}` | any numeric literal |
| `{integer}`, `{float}` | integer / fractional literals, **and** the type names that hold them (`int`, `double`, a cast's type, Python's `int` annotation) |
| `{exponential}`, `{based_integer}`, `{rational}`, `{complex}` | `4.2e1`; `0x2A`; Racket `1/2`; Python `2j` |
| `{string}`, `{char}`, `{bytes}`, `{boolean}` | literals of that kind |
| `{contains: pattern}` | in an `Except:`, a node with a match of `pattern` somewhere inside (`while ({quantity}) {contains: {match}}`) |

Output placeholders don't read a match; the adapter computes them for the
matched node: `{empty_value}` (the empty value of the returned type),
`{default_value}` (the default value of the call's type), `{empty_string}`,
`{empty_collection}` (`[]`, `{}`, `'()`, matching the original) and
`{empty_block}` (`{}`, `pass`, `(void)`).

**Converting between kinds.** A number or text placeholder in `With:` that the
pattern didn't match converts the matched one of its family, by position:
`Replace: {integer}` / `With: {float}` turns `42` into `42.0`, `int count`
into `double count` and `(int) x` into `(double) x`. A size word converts
size: `{integer: int32}` → `{integer: int64}` turns `int x` into `long x` and
`42` into `42L`. The families are numbers (`{number}`, `{integer}`, `{float}`,
`{exponential}`, `{based_integer}`, `{rational}`, `{complex}`) and text
(`{char}`, `{string}`, `{bytes}`, so `'a'` → `"a"`). Java and C# skip a
conversion where the code needs the original type (`int n = 42.0;`).

A placeholder or construct a language doesn't have simply matches nothing
there. Each run lists the rules that weren't applied to a language's files
because its adapter doesn't know something they use. A placeholder name the
tool doesn't know at all (`{quantty}`) is an error when the spec loads.

### Named placeholders

`{quantity a}` gives a placeholder a name. `With:` and `Insert:` refer back
to it by name instead of by position, so they can reorder operands:

```
Replace: {quantity a} - {quantity b}
With: {quantity b} - {quantity a}          ← end - start becomes start - end
```

The same name twice in a pattern must match the same text:
`{quantity x} - {quantity x}` matches `a - a` but not `a - b`. A `With:` name
the pattern doesn't have is an error. Unnamed placeholders keep binding by
position. A mutant that writes the same code back (swapping `a - a`) is
dropped.

### Constraints

`{type: constraint}` narrows what a placeholder matches. The constraint is
either a pattern, as `{match: {quantity} <= 0}` has always been, or one word:

| Word | Matches |
|------|---------|
| `literal` | a number, string, boolean or null literal (`{quantity} * {quantity: literal}` matches `b * 2`, not `b * a`) |
| `type` | a type name |
| `number`, `integer`, `float`, `boolean`, `char`, `string`, `bytes`, `null`, `collection` | a value of that kind |
| `int8` … `int64`, `uint8` … `uint64`, `bigint`, `float32`, `float64`, `decimal` | a number or numeric type of that size |

Names and constraints combine: `{integer n: literal}`.

### Writing statements

`With:` and `Insert:` can write statements: assignments, `if`, `while`,
`for` and blocks, each in the language's own form.

```
Replace: {quantity} = {quantity}
With: {quantity} = null                    ← Java x = null; Python x = None; Racket (set! x '())

Replace: if ({quantity}) {statement}
With: {statement}                          ← removes the if around its body
```

Python bodies are written indented under a colon; Racket uses `(set! ...)`,
`(if c a b)` and `(begin ...)` and has no loops, so a rule that writes a loop
or an `if` without an `else` produces nothing there.

### Except:

An `Except:` tree suppresses a mutation when it matches **any ancestor** of the
mutated node with the mutated node in its `{match}` position. `{match: pattern}`
also requires the mutated node to match `pattern`. Each `Except:` must contain
exactly one `{match}`.

```
Replace: {quantity} <= {quantity}
With: {quantity} < {quantity}
Except: if ({match: {quantity} <= 0}) {quantity} = 0     ← the clamp guard, in every language
Except: for ({quantity}; {match}; {quantity}) {statement} ← a for loop's condition
Except: while ({match}) {statement}                       ← a while loop's condition
```

Because `{match}` pins the position, a comparison elsewhere in the loop body
is still mutated. `Except:` trees are matched structurally, so one line covers
Java's `if (n <= 0) n = 0;`, Python's `if n <= 0: n = 0` and Racket's
`(if (<= n 0) 0 n)`.

## Coverage matrix

What each language's adapter supports. `CoverageMatrixTests` checks this
table against the adapters; run it with `-Dmsl.matrix.update=true` after
adding support.

<!-- coverage-matrix:start -->
| Construct | Java | C# | JS | Python | Racket |
|---|---|---|---|---|---|
| `binary` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `unary` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `increment` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `conditional` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `compound_assign` | ✓ | ✓ | ✓ | ✓ | — |
| `index` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `member` | ✓ | ✓ | ✓ | ✓ | — |
| `member_call` | ✓ | ✓ | ✓ | ✓ | — |
| `predicate` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `call` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `literal` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `identifier` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `if` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `while` | ✓ | ✓ | ✓ | ✓ | — |
| `for` | ✓ | ✓ | ✓ | — | — |
| `assign` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `block` | ✓ | ✓ | ✓ | ✓ | — |
| `collection` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `object` | — | — | ✓ | ✓ | ✓ |
| `entry` | — | — | ✓ | ✓ | ✓ |
| `lambda` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `function` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `parameter` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `type_check` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `cast` | ✓ | ✓ | — | — | — |
| `safe_member` | — | ✓ | ✓ | — | — |
| `field` | ✓ | ✓ | ✓ | ✓ | — |
| `initialized_new` | ✓ | ✓ | — | — | — |
| `await` | — | ✓ | ✓ | ✓ | — |
| `comprehension` | — | — | — | ✓ | — |
| `when` | — | — | — | — | ✓ |
| `unless` | — | — | — | — | ✓ |
| `where` | — | ✓ | — | — | — |
| `assert` | ✓ | — | — | ✓ | — |
| `with` | ✓ | ✓ | — | ✓ | — |
| `yield` | ✓ | ✓ | ✓ | ✓ | — |
| `labeled` | ✓ | — | ✓ | — | — |
| `membership` | — | — | ✓ | ✓ | — |
| `slice` | — | ✓ | — | ✓ | — |
| `interpolation` | — | ✓ | ✓ | — | — |
| `tuple` | — | ✓ | ✓ | ✓ | — |
| `spread` | — | ✓ | ✓ | ✓ | — |
| `arm` | ✓ | ✓ | — | — | ✓ |
| `delete` | — | — | ✓ | ✓ | — |
| `annotation` | ✓ | ✓ | — | ✓ | — |
| `return` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `break` | ✓ | ✓ | ✓ | ✓ | — |
| `continue` | ✓ | ✓ | ✓ | ✓ | — |
| `declare` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `for_each` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `do_while` | ✓ | ✓ | ✓ | — | — |
| `case` | ✓ | ✓ | ✓ | — | — |
| `default_case` | ✓ | ✓ | ✓ | — | — |
| `throw` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `try` | ✓ | ✓ | ✓ | ✓ | ✓ |

| Placeholder | Java | C# | JS | Python | Racket |
|---|---|---|---|---|---|
| `{arguments}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{based_integer}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{block}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{boolean}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{bytes}` | — | — | — | ✓ | — |
| `{char}` | ✓ | ✓ | — | — | ✓ |
| `{collection}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{complex}` | — | — | — | ✓ | — |
| `{condition}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{constructor_call}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{contains}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{equality_condition}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{exponential}` | ✓ | ✓ | ✓ | ✓ | — |
| `{field}` | ✓ | ✓ | ✓ | ✓ | — |
| `{float}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{integer}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{invocation_statement}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{lambda}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{name}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{non_void_method_call}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{number}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{order_condition}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{quantity}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{rational}` | — | — | — | — | ✓ |
| `{return_value}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{statement}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{string}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{type}` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{default_value}` (output) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{empty_block}` (output) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{empty_collection}` (output) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{empty_string}` (output) | ✓ | ✓ | ✓ | ✓ | ✓ |
| `{empty_value}` (output) | ✓ | ✓ | ✓ | ✓ | ✓ |
<!-- coverage-matrix:end -->

**Not applicable (—)**, by reason:

- **The language has no such construct:** Racket has no `while`, C-style
  `for`, `++` statements, compound assignment, `break`/`continue`, member
  access, casts, fields, labels, `assert`, `delete`, `yield` or annotations;
  Python has no C-style `for`, `do`-`while`, `switch`, casts or null-safe
  access; Java has no object literals, parameter defaults, membership
  operator, slicing, interpolation, spread, `delete` or `await`; JavaScript
  has no casts, object initializers or type names in source.
- **The grammar doesn't parse it** (out of scope, decision D2): Java record
  patterns, Python `match` and f-string holes, C# `with` expressions and
  `and`/`or`/`not` patterns, Racket square-bracket bindings in `for` and
  `with-handlers` (the parenthesised forms are supported).
- **Deferred:** library calls (`startsWith`, `Math.max` by meaning, ...) and
  regular-expression literals.

Racket's `cond` clauses and the binding forms `begin0`, `delay`, `shared`
and `recur` have no separate construct: their expressions are matched by
the ordinary patterns.

## The reference catalog

`specs/catalog/` holds the PIT, Stryker, mutmut and classic mutation
operators, written only as spec rules: `pit.spec`, `stryker.spec`,
`mutmut.spec` and `classic.spec`, one group per operator. `CatalogSpecsTests`
checks that every group makes mutants in each language whose fixture has the
construct. Library-call operators (Stryker's MethodExpression) and regex
literals are deferred.

## For adapter authors

An adapter implements `MSLScannerAdapter` for one language, working only from
the CST (node types, tokens and children; no regular expressions):

- `matchNode(MSLNode, ctx)` — is `ctx` this construct, and which CST children
  line up with the pattern's children (the default covers binary operators,
  checks, literals, names, calls, and increments through `incrementOperand`);
- `expressionStatement(ctx)` / `discardsValue(ctx)` — whether an expression
  is a whole statement, or its value is otherwise thrown away (a `for` update);
- `matchesPlaceholder(type, ctx)` — does `ctx` fit a placeholder type;
- `render…`, `replace`, `delete`, `insert` — how a tree is written;
- `supportedKinds()` / `supportedPlaceholders()` — what it understands, for
  the unapplied-rules report.

The shared `MSLMatcher` walks the pattern, records what each placeholder
matched, and runs `Except:` trees against ancestors.

`src/test/resources/msl-baseline` holds every mutant the bundled specs
produced on the sample and external projects before the tree-based adapters;
`MslBaselineTests` fails if one disappears. `mvn test` checks the samples; add
`-Dmsl.baseline.external=true` to check the external projects too (about half
an hour). Losses that were reviewed and explained are listed, with their
reason, in `pending-review-losses.txt`. Run with `-Dmsl.baseline.update=true`
to record new mutants after reviewing them.
