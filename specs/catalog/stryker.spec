ArithmeticOperator

	Replace: {quantity} + {quantity}
	With: {quantity} - {quantity}
	And: Inverse

End

EqualityOperator

	Replace: {quantity} == {quantity}
	With: {quantity} != {quantity}
	And: Inverse

	Replace: {quantity} === {quantity}
	With: {quantity} !== {quantity}
	And: Inverse

End

LogicalOperator

	Replace: {quantity} && {quantity}
	With: {quantity} || {quantity}
	And: Inverse

	Replace: {quantity a} ?? {quantity b}
	With: {quantity a} && {quantity b}

End

UpdateOperator

	Replace: {quantity}++
	With: {quantity}--
	And: Inverse

End

UnaryOperator

	Replace: -{quantity}
	With: {quantity}

End

AssignmentOperator

	Replace: {quantity} += {quantity}
	With: {quantity} -= {quantity}
	And: Inverse

End

BooleanLiteral

	Replace: true
	With: false
	And: Inverse

	Replace: !{quantity}
	With: {quantity}

End

ConditionalExpression

	Replace: {condition}
	With: true

	Replace: {condition}
	With: false

End

BlockStatement

	Replace: {block}
	With: {empty_block}

End

ArrayDeclaration

	Replace: [{quantity}, {arguments}]
	With: {empty_collection}

End

ObjectLiteral

	Replace: { {quantity k}: {quantity v}, {arguments} }
	With: {empty_collection}

End

StringLiteral

	Replace: {string}
	With: {empty_string}

End

ArrowFunction

	Replace: ({arguments}) => {quantity}
	With: ({arguments}) => null

End

OptionalChaining

	Replace: {quantity}?.{name}
	With: {quantity}.{name}

End
