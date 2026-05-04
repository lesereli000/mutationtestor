from src.NoCoverageDemo import covered_by_tests


def test_covered_method_is_executed_by_tests():
    assert covered_by_tests(5) == 6
