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

End
```

The first line names the group; after `End`, the next line names the next
group. Labels in reports are `<group>: <from> → <to>` (`Math Operators: + → -`),
`<group>: delete <target>` or `<group>: insert <tree> before|after <anchor>`.

## The three rule types

| Rule | What happens to each CST node the first pattern matches |
|------|----------------------------------------------------------|
| `Replace:` / `With:` | Rewritten as the `With:` tree. An operator swap (`{quantity} + {quantity}` → `{quantity} - {quantity}`) changes only the operator; anything else replaces the matched text. A `With:` that is just one of the operands (`With: {quantity}`) is skipped where the value is thrown away (`x++;`, a `for` loop's update): there it would only delete the statement, which Java and C# reject. |
| `Delete:` | Replaced by the language's empty statement (`;`, `pass`, `(void)`). `With: ;` on a `Replace:` does the same. When the pattern is an expression (`Delete: {quantity}++`), the statement it makes up is removed; a match inside a larger expression (`a[i++]`) or a `for` loop header is left alone. |
| `Insert:` + `Before:` / `After:` | The `Insert:` tree is written as a new statement on its own line before or after the anchor, at its indentation. |

## Patterns

Patterns use a C-like syntax that every adapter reads the same way:

| Written | Tree | Notes |
|---------|------|-------|
| `{quantity} + {quantity}` | binary operator | `+ - * / % // ** < <= > >= == != && \|\| & \| ^ << >> >>>`, usual precedence; parentheses only group |
| `!{quantity}`, `-{quantity}` | unary operator | |
| `{quantity}++`, `{quantity}--` | increment / decrement | add (subtract) one to the variable; `++{quantity}` is the same node. Each adapter matches its language's forms: `x++` and `++x` in Java, C# and JavaScript (rewrites keep the prefix or postfix form), `x += 1` in Python, `(add1 x)` in Racket |
| `{quantity} is null`, `{quantity} is not null` | plain-English check | `is null` means null **or empty**; each adapter decides what that is in its language (`string.IsNullOrEmpty(s)`, `s is None or s == ""`, `(or (not s) (string=? s ""))`, ...) |
| `max({quantity}, 0)` | call | a name or dotted name; matches calls to that function |
| `0`, `"s"`, `true`, `false`, `null` | literal | each adapter spells them (`True`, `#t`, `None`, `'()`) |
| `if (c) s`, `while (c) s`, `for (i; c; u) s` | statements | a missing `for` part is left empty: `for (; c; )` |
| `{quantity} = 0` | assignment | |
| `{ s1 s2 }` | block | matches a block whose statements **start with** `s1 s2` |
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

Output placeholders don't read a match; the adapter computes them for the
matched node: `{empty_value}` (the empty value of the returned type) and
`{default_value}` (the default value of the call's type).

A placeholder or construct a language doesn't have simply matches nothing
there. Each run lists the rules that weren't applied to a language's files
because its adapter doesn't know something they use.

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
