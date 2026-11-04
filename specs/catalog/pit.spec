CONDITIONALS_BOUNDARY

	Replace: {quantity} < {quantity}
	With: {quantity} <= {quantity}
	And: Inverse

	Replace: {quantity} > {quantity}
	With: {quantity} >= {quantity}
	And: Inverse

End

INCREMENTS

	Replace: {quantity}++
	With: {quantity}--
	And: Inverse

End

INVERT_NEGS

	Replace: -{quantity}
	With: {quantity}

End

MATH

	Replace: {quantity} + {quantity}
	With: {quantity} - {quantity}
	And: Inverse

	Replace: {quantity} * {quantity}
	With: {quantity} / {quantity}
	And: Inverse

	Replace: {quantity} % {quantity}
	With: {quantity} * {quantity}

End

NEGATE_CONDITIONALS

	Replace: {quantity} == {quantity}
	With: {quantity} != {quantity}
	And: Inverse

	Replace: {quantity} < {quantity}
	With: {quantity} >= {quantity}

	Replace: {quantity} > {quantity}
	With: {quantity} <= {quantity}

End

VOID_METHOD_CALLS

	Delete: {invocation_statement}

End

EMPTY_RETURNS

	Replace: {return_value}
	With: {empty_value}

End

FALSE_RETURNS

	Replace: return {quantity: boolean}
	With: return false

End

TRUE_RETURNS

	Replace: return {quantity: boolean}
	With: return true

End

NULL_RETURNS

	Replace: return {quantity}
	With: return null

End

PRIMITIVE_RETURNS

	Replace: return {number}
	With: return 0

End

REMOVE_CONDITIONALS

	Replace: {equality_condition}
	With: true

	Replace: {order_condition}
	With: false

	Replace: if ({quantity}) {statement a} else {statement b}
	With: {statement a}

End

CONSTRUCTOR_CALLS

	Replace: {constructor_call}
	With: null

End

NON_VOID_METHOD_CALLS

	Replace: {non_void_method_call}
	With: {default_value}

End

INLINE_CONSTS

	Replace: {integer: literal}
	With: {integer} + 1

End

EXPERIMENTAL_SWITCH

	Delete: case {quantity}: {statement}

End

EXPERIMENTAL_ARGUMENT_PROPAGATION

	Replace: {name}({quantity}, {arguments})
	With: {quantity}

End

EXPERIMENTAL_NAKED_RECEIVER

	Replace: {quantity}.{name}({arguments})
	With: {quantity}

End

EXPERIMENTAL_MEMBER_VARIABLE

	Replace: field {name} = {quantity}
	With: field {name}

End

AOD

	Replace: {quantity a} + {quantity b}
	With: {quantity b}

	Replace: {quantity a} - {quantity b}
	With: {quantity a}

End
