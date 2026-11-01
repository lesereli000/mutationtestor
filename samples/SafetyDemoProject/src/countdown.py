"""A bounded countdown to demonstrate a loop-condition mutation."""


def countdown(count):
    values = []
    while count > 0:
        values.append(count)
        count -= 1
    return values
