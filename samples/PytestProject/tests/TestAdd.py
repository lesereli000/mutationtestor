from src.Add1 import add1
from src.Add2 import add2

# run with ' pytest ./tests/TestAdd.py '

def test_add1():
    assert add1(5) == 6
    assert add1(-1) == 0
    assert add1(788) == 789
    assert add1(11) == 12

def test_add2():
    assert add2(5) == 7
    assert add2(-1) == 1
    assert add2(788) == 790
    assert add2(11) == 13