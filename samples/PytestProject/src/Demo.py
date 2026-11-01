def add(a, b):
    return a + b


print(add(2, 3))

print("2 + 3")

def GTtoGE():
    a = 5
    equal = a
    if(a > equal):
        print("GTtoGE Passes")
    else:
        print("GTtoGE Fails")

def GEtoGT():
    a = 5
    equal = a
    if(a >= equal):
        print("GEtoGT Fails")
    else:
        print("GEtoGT Passes")

GTtoGE()
GEtoGT()