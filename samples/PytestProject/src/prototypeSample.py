# prototypeSample.py
from enum import Enum
from abc import ABC, abstractmethod
from typing import TypeVar, Generic, List
import threading

# Enum example
class Status(Enum):
    STARTED = "Started"
    RUNNING = "Running"
    FINISHED = "Finished"

# Interface example 
class Greeter(ABC):
    @abstractmethod
    def greet(self, name: str):
        pass

# Abstract class example
class Animal(ABC):
    @abstractmethod
    def make_sound(self):
        pass

# Concrete class with inheritance
class Dog(Animal):
    dog_count = 0  # Class variable (static)
    
    def __init__(self, breed: str):
        self.breed = breed
        Dog.dog_count += 1
        self.energy = 100
    
    def make_sound(self):
        print("Woof!")
    
    def play(self):
        self.energy -= 10
    
    @staticmethod
    def create_dog(breed: str):
        return Dog(breed)

# Generic class example
T = TypeVar('T')
class Box(Generic[T]):
    def __init__(self, value: T):
        self.value = value
    
    def get(self) -> T:
        return self.value
    
    def set(self, value: T):
        self.value = value

class MutationTestDemo:
    # Class variables
    counter = 0
    
    def __init__(self):
        self.max = 10
        self.temp_value = "temp"
        self.flag = True
        self.names = ["Alice", "Bob", "Charlie"]
    
    @staticmethod
    def static_method():
        print("Static method called")
    
    def sum_nums(self, *nums):
        result = 0
        for n in nums:
            result += n  # arithmetic + assignment
        return result
    
    @staticmethod
    def main():
        demo = MutationTestDemo()
        
        # Variables & assignments
        a = 5
        b = 10
        pi = 3.14
        boolean = True
        letter = 'X'
        message = "Hello"
        
        # OPERATORS 
        add = a + b
        sub = a - b
        mul = a * b
        div = a // b  
        mod = a % b
        

        a += 1  # equivalent to a++
        b -= 1  # equivalent to b--
        
        eq = (a == b)
        neq = (a != b)
        gt = (a > b)
        lt = (a < b)
        ge = (a >= b)
        le = (a <= b)
        
        and_op = (a > 0 and b > 0)
        or_op = (a > 0 or b > 0)
        not_op = not and_op
        
        bit_and = a & b
        bit_or = a | b
        bit_xor = a ^ b
        bit_not = ~a
        shl = a << 1
        shr = a >> 1
        
        # OPERATORS IN STRINGS 
        operator_string = "+ - * / % += -= == != > < >= <= and or not & | ^ ~ << >>"
        
        # OPERATORS IN COMMENTS 
        # + - * / % += -=
        # == != > < >= <=
        # and or not
        # & | ^ ~ << >>
        
        # Logical operators
        logic = (a > 0 and b > 0) or not boolean
        
        # Ternary operator 
        ternary = a if a > b else b
        
        # Conditional statements
        if a > 0:
            print("a positive")
        elif a < 0:
            print("a negative")
        else:
            print("a zero")
        
        # Conditional dispatch (equivalent to match/case)
        status = Status.RUNNING
        if status == Status.STARTED:
            print("Started")
        elif status == Status.RUNNING:
            print("Running")
        elif status == Status.FINISHED:
            print("Finished")
        
        # Loops
        for i in range(3):
            if i != 1:
                continue
            print(f"For loop {i}")
        
        j = 0
        while j < 3:
            print(f"While {j}")
            j += 1
        
        k = 0
        while True:
            print(f"Do-while {k}")
            k += 1
            if k == 2:
                break
            if k >= 3:
                break
        
        # Arrays/Lists
        for n in demo.names:
            print(f"Name: {n}")
        
        # Collections
        list_items = ["One", "Two"]
        dict_items = {"A": 1, "B": 2}
        
        # Lambda
        list(map(lambda e: print(f"Lambda: {e}"), list_items))
        
        # List comprehension (Python-specific)
        doubled = [s + s for s in list_items]
        
        # Try-except-finally
        try:
            x = 10 // 2
            if x == 5:
                raise Exception("Example exception")
        except (Exception, ArithmeticError) as e:
            print(f"Exception: {e}")
        finally:
            print("Finally executed")
        
        # Context manager (Python's try-with-resources)
        with open(__file__, 'r') as f:
            first_line = f.readline()
            print("File opened successfully")
        
        # Object creation
        dog = Dog("Labrador")
        dog.make_sound()
        dog.play()
        
        # Interface implementation (Greeter class)
        class ConsoleGreeter(Greeter):
            def greet(self, name: str):
                print(f"Hello {name}")
        
        greeter = ConsoleGreeter()
        greeter.greet("World")
        
        # Generics
        box: Box[str] = Box("Generics!")
        print(box.get())
        
        # Type casting
        d = 3.5
        di = int(d)
        print(f"Casted: {di}")
        
        # isinstance (Python's version of instanceof)
        if isinstance(dog, Animal):
            print("dog is Animal")
        
        # Threading lock (Python's synchronized)
        lock = threading.Lock()
        with lock:
            MutationTestDemo.counter += 1
        
        # Assert
        assert MutationTestDemo.counter >= 0
        
        # None check (Python's null)
        s = None
        if s is None:
            print("s is None")
        
        # Print summary
        print(f"""
Summary:
- flag: {boolean}, letter: {letter}, message: {message}
- add: {add}, sub: {sub}, mul: {mul}, div: {div}, mod: {mod}
- eq: {eq}, neq: {neq}, gt: {gt}, lt: {lt}, ge: {ge}, le: {le}
- and: {and_op}, or: {or_op}, not: {not_op}
- bit_and: {bit_and}, bit_or: {bit_or}, bit_xor: {bit_xor}, bit_not: {bit_not}
- shl: {shl}, shr: {shr}
- ternary: {ternary}
- doubled: {doubled}
- dog_count: {Dog.dog_count}
- max: {demo.max}
- counter: {MutationTestDemo.counter}
        """)

        # Python specific features
        specifics = PythonSpecifics()
        specifics.run_specifics()

# Decorator example
def my_decorator(func):
    def wrapper(*args, **kwargs):
        return func(*args, **kwargs)
    return wrapper

class PythonSpecifics:
    @my_decorator
    def run_specifics(self):
        # Dict comprehension
        squares = {x: x*x for x in range(3)}
        
        # Generator
        def my_gen():
            yield 1
            yield 2
        
        for v in my_gen():
            pass
            
        # Duck typing
        class Duck:
            def quack(self):
                pass
        
        def make_it_quack(obj):
            if hasattr(obj, 'quack'):
                obj.quack()
                
        make_it_quack(Duck())

# Run
if __name__ == "__main__":
    MutationTestDemo.main()