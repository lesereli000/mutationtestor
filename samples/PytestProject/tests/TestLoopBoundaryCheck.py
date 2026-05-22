
from src.LoopBoundaryCheck import while_loop_boundary, while_loop_sum

def test_while_loop_boundary():
    assert while_loop_boundary(3) == 3
    assert while_loop_boundary(0) == 0
    assert while_loop_boundary(-2) == 0

def test_while_loop_sum():
    assert while_loop_sum(4) == 6  # 0+1+2+3
    assert while_loop_sum(0) == 0
    assert while_loop_sum(1) == 0
