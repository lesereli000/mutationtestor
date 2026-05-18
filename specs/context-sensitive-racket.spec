Boundary Mutations

	Replace: {quantity} <= {quantity}
	With: {quantity} < {quantity}
	Except: (if (<= {quantity} 0) 0
	And: Inverse

	Replace: {quantity} >= {quantity}
	With: {quantity} > {quantity}
	Except: (if (>= {quantity} 0) 0
	And: Inverse

End