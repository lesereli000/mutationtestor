Type Mutations

	Changes a number type to another, along with the literals of that type,
	as the old TYPE_* operators did: int x becomes double x, and 2.5 becomes 2.
	The sizes (int, long, float, double) exist in Java and C#. JavaScript, Python
	and Racket have no sized number types; for those, a rule that writes
	{float} in place of {integer} (and the reverse) changes the kind instead.

	Group: Integer and floating point

		int and double
		Replace: {integer: int32}
		With: {float: float64}
		And: Inverse

		int and float
		Replace: {integer: int32}
		With: {float: float32}
		And: Inverse

		long and double
		Replace: {integer: int64}
		With: {float: float64}
		And: Inverse

		long and float
		Replace: {integer: int64}
		With: {float: float32}
		And: Inverse

	End

	Group: Integer sizes

		int and long
		Replace: {integer: int32}
		With: {integer: int64}
		And: Inverse

	End

End
