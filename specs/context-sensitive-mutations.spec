Boundary Mutations

	Replace: {quantity} <= {quantity}
	With: {quantity} < {quantity}
	Except: if ({match: {quantity} <= 0}) {quantity} = 0
	Except: while ({match: {quantity} < 0}) { {quantity} = 0 }
	And: Inverse

	Replace: {quantity} >= {quantity}
	With: {quantity} > {quantity}
	Except: if ({match: {quantity} >= 0}) {quantity} = 0
	Except: while ({match: {quantity} > 0}) { {quantity} = 0 }
	And: Inverse

End
