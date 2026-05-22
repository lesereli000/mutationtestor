Boundary Mutations

	Replace: {quantity} <= {quantity}
	With: {quantity} < {quantity}
	Except: if ({quantity} <= 0) {quantity} = 0
	Except: if {quantity} <= 0: {quantity} = 0
	Except: (if (<= {quantity} 0) 0
	Except: while ({quantity} < 0) { {quantity} = 0
	And: Inverse

	Replace: {quantity} >= {quantity}
	With: {quantity} > {quantity}
	Except: if ({quantity} >= 0) {quantity} = 0
	Except: if {quantity} >= 0: {quantity} = 0
	Except: (if (>= {quantity} 0) 0
	Except: while ({quantity} > 0) { {quantity} = 0
	And: Inverse

End
