from src.EdgeCaseMath import normalize_and_sum, rolling_total, difference, distance, shrinking_total
from src.FibonacciDemo import fibonacci, fibonacci_sum
from src.SearchDemo import binary_search, insertion_point


def test_fibonacci_values():
    assert fibonacci(0) == 0
    assert fibonacci(1) == 1
    assert fibonacci(7) == 13


def test_fibonacci_sum_values():
    assert fibonacci_sum(0) == 0
    assert fibonacci_sum(3) == 4
    assert fibonacci_sum(7) == 33


def test_binary_search_hits_and_misses():
    values = [1, 3, 5, 7, 9, 11]
    assert binary_search(values, 1) == 0
    assert binary_search(values, 7) == 3
    assert binary_search(values, 10) == -1
    assert binary_search([], 4) == -1


def test_insertion_point_edge_cases():
    values = [1, 3, 5, 7]
    assert insertion_point(values, 0) == 0
    assert insertion_point(values, 4) == 2
    assert insertion_point(values, 9) == 4


def test_normalize_and_sum():
    assert normalize_and_sum([1, -2, 3, 0]) == 4
    assert normalize_and_sum([-5, -1]) == 0


def test_rolling_total():
    assert rolling_total([2, 3, -1, 4]) == [2, 5, 4, 8]
    assert rolling_total([]) == []


def test_difference():
    assert difference(10, 3) == 7
    assert difference(3, 10) == -7
    assert difference(5, 5) == 0


def test_distance():
    assert distance(10, 3) == 7
    assert distance(3, 10) == 7
    assert distance(5, 5) == 0


def test_shrinking_total():
    assert shrinking_total(100, [10, 20, 30]) == 40
    assert shrinking_total(0, [5]) == -5
    assert shrinking_total(50, []) == 50