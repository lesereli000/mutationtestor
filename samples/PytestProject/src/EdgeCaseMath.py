def normalize_and_sum(values):
    total = 0
    for value in values:
        adjusted = value if value > 0 else 0
        total = total + adjusted
    return total


def rolling_total(values):
    totals = []
    current = 0
    for value in values:
        current = current + value
        totals.append(current)
    return totals


def difference(a, b):
    return a - b


def distance(a, b):
    result = a - b
    if result < 0:
        result = 0 - result
    return result


def shrinking_total(start, values):
    current = start
    for value in values:
        current = current - value
    return current