def while_loop_boundary(x):
    count = 0
    while x > 0:
        count += 1
        x -= 1
    return count


def while_loop_sum(n):
    total = 0
    i = 0
    while i < n:
        total += i
        i += 1
    return total

# Simple tests
if __name__ == "__main__":
    print("while_loop_boundary(3):", while_loop_boundary(3))
    print("while_loop_sum(4):", while_loop_sum(4))
