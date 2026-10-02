def condition(flag):
    if flag:
        return 1
    return 0

def negate(flag):
    return not flag

def empty_value():
    return "value"

def returned_call():
    return compute()

def void_call():
    notify()

def constructor_call():
    return Widget()
