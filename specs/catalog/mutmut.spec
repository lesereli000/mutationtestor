Operator swaps

	Replace: {quantity} + {quantity}
	With: {quantity} - {quantity}
	And: Inverse

	Replace: {quantity} < {quantity}
	With: {quantity} <= {quantity}
	And: Inverse

End

Number literals

	Replace: {number}
	With: {number} + 1

End

String literals

	Replace: {string}
	With: {empty_string}

End

Keywords

	Replace: true
	With: false
	And: Inverse

	Replace: break
	With: continue
	And: Inverse

	Replace: {quantity} in {quantity}
	With: {quantity} not in {quantity}
	And: Inverse

	Replace: {quantity} == {quantity}
	With: {quantity} != {quantity}
	And: Inverse

End

Assignment to None

	Replace: {quantity} = {quantity}
	With: {quantity} = null

End

Lambda to None

	Replace: {lambda}
	With: null

End

Default arguments

	Replace: parameter {name} = {quantity}
	With: parameter {name}

End

Decorators

	Delete: @{name}

End
