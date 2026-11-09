IF-ELSE-SWAP

    Replace: if({condition}) {statement a} else {statement b}
    With: if({condition}) {statement b} else {statement a}

End

REMOVE-IF-STATEMENT

	Replace: if ({condition c}) {statement a} else {statement b}
	With: {statement a}

    Replace: if ({condition c}) {statement a} else {statement b}
	With: {statement b}

End

MODIFY-RETURN-VALUES

    Replace: return {quantity}
    With: return {empty-value}

End

OFF-BY-ONE-LT

	Replace: {quantity} <= {quantity}
	With: {quantity} < {quantity}
	Except: if ({match: {quantity} <= 0}) {quantity} = 0

End

TYPE-CHANGING

    Replace: {integer}
    With: {float}
    And: Inverse

End