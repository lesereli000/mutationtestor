# operators.py - Comprehensive Python sample for mutation testing
# Covers: AOR (+,-,*,/,%), BOR (&,|), ROR (>,>=,<,<=,==,!=), LCR (and,or), TYPE (int,float)


def add(x, y):
    return x + y


def subtract(x, y):
    return x - y


def multiply(x, y):
    return x * y


def divide(x, y):
    return x / y


def int_divide(x, y):
    return x // y


def modulo(x, y):
    return x % y


def power(x, y):
    return x ** y


def compare_gt(x, y):
    return x > y


def compare_ge(x, y):
    return x >= y


def compare_lt(x, y):
    return x < y


def compare_le(x, y):
    return x <= y


def check_equal(x, y):
    return x == y


def check_not_equal(x, y):
    return x != y


def check_logic(x, y):
    if x > 0 and y > 0:
        return True
    return False


def check_logic_or(x, y):
    if x > 0 or y > 0:
        return True
    return False


def typed_add(a: int, b: int) -> int:
    return a + b


def typed_float_add(a: float, b: float) -> float:
    return a + b


def bitwise_and(x: int, y: int) -> int:
    return x & y


def bitwise_or(x: int, y: int) -> int:
    return x | y
