def fibonacci(n: int) -> int:
    if n < 0:
        raise ValueError("n must be non-negative")
    if n <= 1:
        return n

    previous = 0
    current = 1
    for _ in range(2, n + 1):
        previous, current = current, previous + current
    return current


def fibonacci_sum(n: int) -> int:
    if n < 0:
        raise ValueError("n must be non-negative")

    total = 0
    for index in range(n + 1):
        total = total + fibonacci(index)
    return total