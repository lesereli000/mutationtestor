from src.Add3 import add3
from src.IncrementKilledDemo import test as increment_test
from src.ModuloKilledDemo import test as modulo_test
from src.BitwiseAndKilledDemo import test as bitwise_and_test
from src.BitwiseOrKilledDemo import test as bitwise_or_test
from src.ConditionalDemo import killed as conditional_killed


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
