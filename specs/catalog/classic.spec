AOR (arithmetic operator replacement)

	Replace: {quantity} + {quantity}
	With: {quantity} - {quantity}
	And: Inverse

	Replace: {quantity} * {quantity}
	With: {quantity} / {quantity}
	And: Inverse

End

ROR (relational operator replacement)

	Replace: {quantity} < {quantity}
	With: {quantity} <= {quantity}
	And: Inverse

	Replace: {quantity} == {quantity}
	With: {quantity} != {quantity}
	And: Inverse

End

COR (conditional operator replacement)

	Replace: {quantity} && {quantity}
	With: {quantity} || {quantity}
	And: Inverse

End

LCR (logical connector replacement)

	Replace: {quantity} & {quantity}
	With: {quantity} | {quantity}
	And: Inverse

End

SOR (shift operator replacement)

	Replace: {quantity} << {quantity}
	With: {quantity} >> {quantity}
	And: Inverse

End

SDL (statement deletion)

	Delete: {invocation_statement}

End

UOI (unary operator insertion)

	Replace: {quantity}++
	With: {quantity}
	And: Inverse

End

ABS (absolute value insertion)

	Replace: -{quantity}
	With: {quantity}

End

ASR (assignment operator replacement)

	Replace: {quantity} += {quantity}
	With: {quantity} -= {quantity}
	And: Inverse

End
