def sub(a, b):
    return a - b  # mutant: return a + b


def chain(a, b, c):
    return (a - b) - c  # mutant: return (a + b) + c


def array_minus(arr, i):
    return arr[i] - 1  # mutant: return arr[i] + 1


def unary_minus(a):
    return -a  # mutant: return +a


def minus_equals():
    x = 5
    x -= 3  # mutant: x -= 3 (should NOT change semantics)
    return x


if __name__ == "__main__":
    print(sub(5, 3))                    # output mutant: 2 -> 8
    print(chain(10, 3, 2))              # output mutant: 5 -> 15
    print(array_minus([10, 20, 30], 1)) # output mutant: 19 -> 21
    print(unary_minus(7))               # output mutant: -7 -> 7
    print(minus_equals())               # output mutant: 2 -> 2

    print(10 - 2 - 3)                   # mutant: 10 + 2 + 3 -> 15
    print((10 - 2) - 3)                 # mutant: (10 + 2) + 3 -> 15

    # ===== tests: '-' inside comments should NOT be mutated =====
    # 10 - 2 - 3
    # (10 - 2) - 3
    # a-b-c
