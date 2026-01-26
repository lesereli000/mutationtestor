def both_positive(a, b):
    # should mutate here
    return a > 0 and b > 0

if __name__ == "__main__":
    print("Both positive?", both_positive(2, 3))
    # should not mutate in this string: "foo and bar"
