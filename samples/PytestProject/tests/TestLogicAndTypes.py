from src.GuardLogicDemo import can_schedule, choose_threshold
from src.TypeHintsDemo import blended_score, prefer_float


def test_can_schedule_requires_stream_and_capacity():
    assert can_schedule(8, 10, True) is True
    assert can_schedule(8, 9, True) is False
    assert can_schedule(8, 10, False) is False


def test_choose_threshold_paths():
    assert choose_threshold(0, 4) == 5
    assert choose_threshold(3, -2) == -1
    assert choose_threshold(5, 2) == 7


def test_blended_score_results():
    assert blended_score(4, 1.5) == 7.0
    assert blended_score(1, 0.5) == 3.0


def test_prefer_float_paths():
    assert prefer_float(2.0, 1) == 2.25
    assert prefer_float(0.5, 3) == 4.75
    # boundary: running == modifier (1.75+0.25==1+1==2), catches >= vs >
    assert prefer_float(1.75, 1) == 2.0