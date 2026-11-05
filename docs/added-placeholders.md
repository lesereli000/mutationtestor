# Placeholders added in the spec-coverage work

Placeholders added on top of the original MSL tree rules. Before
that, specs could use `{quantity}`, `{statement}`, `{invocation_statement}`,
`{return_value}`, `{constructor_call}`, `{non_void_method_call}`,
`{equality_condition}`, `{order_condition}`, `{match}`, `{empty_value}` and
`{default_value}`.

| Placeholder | Kind |
|-------------|------|
| `{condition}`, `{name}`, `{block}`, `{arguments}` | structure |
| `{collection}`, `{lambda}`, `{type}`, `{field}` | structure |
| `{number}`, `{integer}`, `{float}`, `{exponential}`, `{based_integer}`, `{rational}`, `{complex}` | numeric literals (and, for `{integer}`/`{float}`, type names) |
| `{string}`, `{char}`, `{bytes}`, `{boolean}` | other literals |
| `{contains: pattern}` | `Except:` only |
| `{empty_string}`, `{empty_collection}`, `{empty_block}` | output |

Placeholders also gained:

- names: `{quantity a}`
- constraint words: `{integer: int64}`, `{quantity: literal}`
- conversion between kinds: `{integer}` → `{float}`
