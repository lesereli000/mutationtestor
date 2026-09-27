from src.NonVoidCallDemo import assign, read


def test_read():
    assert read() == 9


def test_assign():
    assert assign() == 9
