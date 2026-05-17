from src.BoundaryCheck import clamp_to_zero, is_under_limit, clamp_to_floor, is_over_limit


def test_clamp_to_zero_returns_0_for_negative():
    assert clamp_to_zero(-5) == 0
    assert clamp_to_zero(-1) == 0


def test_clamp_to_zero_returns_0_at_boundary():
    assert clamp_to_zero(0) == 0


def test_clamp_to_zero_returns_positive_unchanged():
    assert clamp_to_zero(5) == 5
    assert clamp_to_zero(100) == 100


def test_is_under_limit_true_when_below():
    assert is_under_limit(3, 10) == True
    assert is_under_limit(0, 5) == True


def test_is_under_limit_true_at_boundary():
    assert is_under_limit(10, 10) == True


def test_is_under_limit_false_when_above():
    assert is_under_limit(11, 10) == False
    assert is_under_limit(100, 50) == False


def test_clamp_to_floor_returns_0_for_positive():
    assert clamp_to_floor(5) == 0
    assert clamp_to_floor(1) == 0


def test_clamp_to_floor_returns_0_at_boundary():
    assert clamp_to_floor(0) == 0


def test_clamp_to_floor_returns_negative_unchanged():
    assert clamp_to_floor(-5) == -5
    assert clamp_to_floor(-100) == -100


def test_is_over_limit_true_when_above():
    assert is_over_limit(10, 5) == True
    assert is_over_limit(100, 50) == True


def test_is_over_limit_true_at_boundary():
    assert is_over_limit(10, 10) == True


def test_is_over_limit_false_when_below():
    assert is_over_limit(3, 10) == False
    assert is_over_limit(0, 5) == False
