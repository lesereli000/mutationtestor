Math Operators:

	Replace: {quantity} + {quantity}
	With: {quantity} - {quantity}
	And: Inverse
	
	Replace: {quantity} * {quantity}
	With: {quantity} / {quantity}
	And: Inverse

	Replace: {quantity} % {quantity}
	With: {quantity} * {quantity}

	Replace: {quantity} // {quantity}
	With: {quantity} / {quantity}

	Replace: {quantity} ** {quantity}
	With: {quantity} * {quantity}

	Replace: {quantity} & {quantity}
	With: {quantity} | {quantity}
	And: Inverse

	Replace: {quantity} ^ {quantity}
	With: {quantity} & {quantity}

	Replace: {quantity} >> {quantity}
	With: {quantity} << {quantity}
	And: Inverse

	Replace: {quantity} >>> {quantity}
	With: {quantity} << {quantity}

End

Conditionals Boundary:

	Replace: {quantity} > {quantity}
	With: {quantity} >= {quantity}
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

	Replace: {quantity} >= {quantity}
	With: {quantity} > {quantity}
	Except: if ({match: {quantity} >= 0}) {quantity} = 0
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

	Replace: {quantity} < {quantity}
	With: {quantity} <= {quantity}
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

	Replace: {quantity} <= {quantity}
	With: {quantity} < {quantity}
	Except: if ({match: {quantity} <= 0}) {quantity} = 0
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

End

Negate Conditionals:

	Replace: {quantity} > {quantity}
	With: {quantity} <= {quantity}
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

	Replace: {quantity} <= {quantity}
	With: {quantity} > {quantity}
	Except: if ({match: {quantity} <= 0}) {quantity} = 0
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

	Replace: {quantity} < {quantity}
	With: {quantity} >= {quantity}
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

	Replace: {quantity} >= {quantity}
	With: {quantity} < {quantity}
	Except: if ({match: {quantity} >= 0}) {quantity} = 0
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

	Replace: {quantity} == {quantity}
	With: {quantity} != {quantity}
	And: Inverse

End

Logical Operators:

	Replace: {quantity} && {quantity}
	With: {quantity} || {quantity}
	And: Inverse

End

Logical Operand Removal:

	Replace: {quantity} && {quantity}
	With: {quantity}

	Replace: {quantity} > {quantity}
	With: false
	Except: for ({quantity}; {match}; {quantity}) {statement}
	Except: while ({match}) {statement}

End

Invert Negation

	Replace: !{quantity}
	With: {quantity}

End

Invert and Remove Increments

	Replace: {quantity}++
	With: {quantity}--
	And: Inverse

	Replace: {quantity}++
	With: {quantity}

	Replace: {quantity}--
	With: {quantity}

	Delete: {quantity}++
	Except: for ({quantity}; {quantity}; {match}) {statement}

	Delete: {quantity}--
	Except: for ({quantity}; {quantity}; {match}) {statement}

End