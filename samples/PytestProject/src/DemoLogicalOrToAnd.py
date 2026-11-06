def either_positive(a, b):
    # should mutate here
    return a > 0 or b > 0

if __name__ == "__main__":
    print("Either positive?", either_positive(2, -3))
    # should not mutate in this string: "foo or bar"
