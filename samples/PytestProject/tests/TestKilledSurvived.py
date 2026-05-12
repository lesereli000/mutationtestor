from src.Add3 import add3
from src.IncrementKilledDemo import test as increment_test
from src.ModuloKilledDemo import test as modulo_test
from src.BitwiseAndKilledDemo import test as bitwise_and_test
from src.BitwiseOrKilledDemo import test as bitwise_or_test
from src.ConditionalDemo import killed as conditional_killed
from src.FloorDivDemo import floor_divide
from src.PowerDemo import power
from src.InvertNegDemo import is_nonzero
from src.NullReturnDemo import add


def test_add3():
    assert add3(2) == 5


def test_increment_killed():
    assert increment_test() == 2


def test_modulo_killed():
    assert modulo_test() == 1


def test_bitwise_and_killed():
    assert bitwise_and_test() == 2


def test_bitwise_or_killed():
    assert bitwise_or_test() == 7


def test_conditional_killed_above_threshold():
    assert conditional_killed(101) == 1


def test_conditional_killed_at_boundary():
    assert conditional_killed(100) == 0


def test_conditional_killed_below_threshold():
    assert conditional_killed(99) == 0


def test_floor_divide_killed():
    # 7 // 2 == 3; if // is mutated to /, result is 3.5, which != 3
    assert floor_divide(7, 2) == 3


def test_power_killed():
    # 2 ** 3 == 8; if ** is mutated to *, result is 6, which != 8
    assert power(2, 3) == 8


def test_invert_negs_killed():
    # is_nonzero(5) returns not (5 == 0) == True; if `not` is removed, returns (5 == 0) == False
    assert is_nonzero(5) == True
    assert is_nonzero(0) == False


def test_null_returns_killed():
    # add(2, 3) == 5; if return value is mutated to None, the assertion fails
    assert add(2, 3) == 5
