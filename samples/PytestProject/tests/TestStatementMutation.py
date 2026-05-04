from src.StatementMutationDemo import killed, survived


def test_statement_mutation_killed():
    assert killed() == 1


def test_statement_mutation_survived():
    assert survived() == 1
