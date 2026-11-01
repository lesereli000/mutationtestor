def is_equal(a, b):
    return a == b  # return a != b


def chained_equality(a, b, c):
    return (a == b) and (b == c)  # return (a != b) and (b != c)

def array_equals(arr, i, val):
    return arr[i] == val  # return arr[i] != val


def none_check(s):
    return s == None  # return s != None


def test_basic_equality():
    x = 5
    y = 5
    
    if x == y:
        print("test_basic_equality: fails (should pass)")
    else:
        print("test_basic_equality: passes (should fail)")


def test_inequality_check():
    x = 5
    y = 10
    
    if x == y:
        print("test_inequality_check: passes")
    else:
        print("test_inequality_check: fails")


def test_multiple_conditions():
    a = 5
    b = 5
    c = 5
    
    if (a == b) and (b == c):
        print("test_multiple_conditions: fails (all equal)")
    else:
        print("test_multiple_conditions: passes")


if __name__ == "__main__":
    print("string literal has ==: a==b")  # same (inside string)
    print("concat with bool: " + str(is_equal(5, 5)))  # stays, but is_equal returns opposite
    print("concat with char: " + "=")  # same (string concat)


    print(is_equal(5, 5))  # True -> False
    print(is_equal(5, 10))  # False -> True
    print(chained_equality(5, 5, 5))  # True -> False
    print(chained_equality(5, 5, 10))  #  False -> False
    print(array_equals([10, 20, 30], 1, 20))  # True -> False
    print(none_check(None))  # True -> False
    print(none_check("test"))  # False -> True

    print(5 == 5)  # 5 != 5 -> False
    print(5 == 10)  # 5 != 10 -> True
    print((5 == 5) and (10 == 10))  # (5 != 5) and (10 != 10) -> False

    test_basic_equality()
    test_inequality_check()
    test_multiple_conditions()