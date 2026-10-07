import pytest
from src.prototypeSample import MutationTestDemo, Dog, Box

def test_mutation_demo(capsys):
    # Call main
    MutationTestDemo.main()
    
    # Cover other methods
    MutationTestDemo.static_method()
    
    demo = MutationTestDemo()
    assert demo.sum_nums(1, 2, 3) == 6
    
    dog = Dog.create_dog("Golden")
    assert dog.breed == "Golden"
    
    b = Box(1)
    b.set(2)
    assert b.get() == 2
