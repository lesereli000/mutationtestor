def clamp_to_zero(n):
    if n <= 0: n = 0   # should NOT mutate - equivalent
    return n


def is_under_limit(x, limit):
    if x <= limit:    # should mutate - real logic
        return True
    return False


def clamp_to_floor(n):
    if n >= 0: n = 0   # should NOT mutate - equivalent
    return n


def is_over_limit(x, limit):
    if x >= limit:    # should mutate - real logic
        return True
    return False
