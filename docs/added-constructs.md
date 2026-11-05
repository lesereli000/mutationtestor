# Constructs added in the spec-coverage work

Constructs added on top of the original MSL tree rules. Before
that, specs could use binary and unary operators, `++`/`--`, `is null`,
calls, literals, names, `if`, `while`, `for`, assignment, blocks and `;`.

| Construct | Written |
|-----------|---------|
| `conditional` | `{c} ? {a} : {b}` |
| `compound_assign` | `{a} += {b}` and the other compound operators |
| `index`, `slice` | `{a}[{i}]`, `{a}[{i}:{j}]` |
| `member`, `member_call`, `safe_member` | `{a}.{name}`, `{a}.{name}({arguments})`, `{a}?.{name}` |
| `membership` | `{a} in {b}`, `{a} not in {b}` |
| `type_check`, `cast` | `{a} is a {type}`, `({type}) {a}` |
| `collection`, `object`, `entry` | `[{a}, {b}]`, `{ {k}: {v} }` |
| `comprehension` | `[{e} for {name} in {xs} if {c}]` |
| `lambda` | `({arguments}) => {a}` |
| `tuple`, `spread` | `({a}, {b})`, `...{a}` |
| `interpolation` | `` `...${a}...` `` |
| `await` | `await {a}` |
| `initialized_new` | `new {type} { {arguments} }` |
| `declare`, `field`, `parameter` | `var {name} = {a}`, `field {name} = {a}`, `parameter {name} = {a}` |
| `function` | `function {name}({arguments}) {block}` |
| `do_while`, `for_each` | `do s while (c)`, `for ({name} in {xs}) s` |
| `when`, `unless` | `when (c) s`, `unless (c) s` |
| `return`, `break`, `continue` | `return {a}`, `break`, `continue` |
| `labeled` | `{name}: s` |
| `case`, `default_case`, `arm` | `case {v}: s`, `default: s`, `case {p} => {v}` |
| `throw`, `try` | `throw {a}`, `try s catch s finally s` |
| `with` | `with ({r}) s` |
| `yield`, `assert`, `delete` | `yield {a}`, `assert {c}`, `delete {a}` |
| `annotation` | `@{name}` |
| `where` | `where {c}` |

New operators: `===`, `!==` and `??`.
