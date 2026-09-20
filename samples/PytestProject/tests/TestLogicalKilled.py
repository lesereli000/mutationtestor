from src.LogicalKilledDemo import both


def test_both_false_and_true():
    # False and True == False; if `and` is mutated to `or`, result is True
    assert both(False, True) is False
