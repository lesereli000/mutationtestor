def is_not_equal(a, b):
    return a != b  # return a == b


def chained_inequality(a, b, c):
    return (a != b) and (b != c)  # return (a == b) and (b == c)

def array_not_equals(arr, i, val):
    return arr[i] != val  # return arr[i] == val


def not_none_check(s):
    return s != None  # return s == None

def test_basic_inequality():
    x = 5
    y = 10
    
    if x != y:
        print("test_basic_inequality: fails (should pass)")
    else:
        print("test_basic_inequality: passes (should fail)")


def test_equality_check():
    x = 5
    y = 5
    
    if x != y:
        print("test_equality_check: passes")
    else:
        print("test_equality_check: fails")


def test_multiple_conditions():
    a = 5
    b = 10
    c = 15
    
    if (a != b) and (b != c):
        print("test_multiple_conditions: fails (all different)")
    else:
        print("test_multiple_conditions: passes")


if __name__ == "__main__":
    print("string literal has !=: a!=b")  # same (inside string)
    print("concat with bool: " + str(is_not_equal(5, 10)))  # stays, but is_not_equal returns opposite


    print(is_not_equal(5, 10))  # True -> False
    print(is_not_equal(5, 5))  # False -> True
    print(chained_inequality(5, 10, 15))  # True -> False
    print(chained_inequality(5, 5, 10))  # False -> False
    print(array_not_equals([10, 20, 30], 1, 25))  # True -> False
    print(not_none_check(None))  # False -> True
    print(not_none_check("test"))  # True -> False

    print(5 != 10)  # 5 == 10 -> False
    print(5 != 5)  # 5 == 5 -> True
    print((5 != 10) and (10 != 15))  # (5 == 10) and (10 == 15) -> False

    test_basic_inequality()
    test_equality_check()
    test_multiple_conditions()