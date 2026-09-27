from src.ConstructorCallDemo import create


def test_create():
    widget = create()
    assert widget is not None
    assert widget.name == "demo"
