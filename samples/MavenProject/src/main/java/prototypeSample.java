package mutationtester.demo;

import java.util.*;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;

// Enum example
enum Status { STARTED, RUNNING, FINISHED }

// Interface example
interface Greeter {
    void greet(String name);
}

// Abstract class example
abstract class Animal {
    abstract void makeSound();
}

// Concrete class with inheritance
class Dog extends Animal {
    private final String breed;
    static int dogCount = 0;
    volatile int energy;

    public Dog(String breed) {
        this.breed = breed;
        dogCount++;
        this.energy = 100;
    }

    @Override
    void makeSound() {
        System.out.println("Woof!");
    }

    public void play() throws InterruptedException {
        synchronized(this) {
            energy -= 10;
        }
    }

    public static Dog createDog(String breed) {
        return new Dog(breed);
    }
}

// Generic class example
class Box<T> {
    T value;
    public Box(T value) { this.value = value; }
    public T get() { return value; }
    public void set(T value) { this.value = value; }
}

class MutationTestDemo {

    // Fields
    static int counter = 0;
    final int max = 10;
    transient String tempValue = "temp";
    volatile boolean flag = true;
    String[] names = {"Alice", "Bob", "Charlie"};

    // Constructor
    public MutationTestDemo() {}

    // Static method
    static void staticMethod() {
        System.out.println("Static method called");
    }

    // Instance method
    int sum(int... nums) {
        int result = 0;
        for(int n : nums) {
            result += n; // arithmetic + assignment
        }
        return result;
    }

    public static void main(String[] args) {
        MutationTestDemo demo = new MutationTestDemo();

        // Variables & assignments
        int a = 5, b = 10;
        double pi = 3.14;
        boolean bool = true;
        char letter = 'X';
        String message = "Hello";

        // ================= OPERATORS (REAL) =================
        int add = a + b;
        int sub = a - b;
        int mul = a * b;
        int div = a / b;
        int mod = a % b;

        a++;
        b--;

        boolean eq = (a == b);
        boolean neq = (a != b);
        boolean gt = (a > b);
        boolean lt = (a < b);
        boolean ge = (a >= b);
        boolean le = (a <= b);

        boolean and = (a > 0 && b > 0);
        boolean or = (a > 0 || b > 0);
        boolean not = !and;

        int bitAnd = a & b;
        int bitOr = a | b;
        int bitXor = a ^ b;
        int bitNot = ~a;
        int shl = a << 1;
        int shr = a >> 1;

        // ================= OPERATORS IN STRINGS =================
        String operatorString = "+ - * / % ++ -- == != > < >= <= && || ! & | ^ ~ << >>";

        // ================= OPERATORS IN COMMENTS =================
        // + - * / % ++ --
        // == != > < >= <=
        // && || !
        // & | ^ ~ << >>


        // Logical operators
        boolean logic = (a > 0 && b > 0) || !bool;

        // Ternary operator
        int ternary = (a > b) ? a : b;

        // Conditional statements
        if(a > 0) {
            System.out.println("a positive");
        } else if(a < 0) {
            System.out.println("a negative");
        } else {
            System.out.println("a zero");
        }

        // Switch statement
        Status status = Status.RUNNING;
        switch(status) {
            case STARTED -> System.out.println("Started");
            case RUNNING -> System.out.println("Running");
            case FINISHED -> System.out.println("Finished");
        }

        // Loops
        for(int i = 0; i < 3; i++) {
            if(i!=1) continue;
            System.out.println("For loop " + i);
        }

        int j = 0;
        while(j < 3) {
            System.out.println("While " + j);
            j++;
        }

        int k = 0;
        do {
            System.out.println("Do-while " + k);
            k++;
            if(k==2) break;
        } while(k<3);

        // Arrays
        for(String n : demo.names) System.out.println("Name: " + n);

        // Collections
        List<String> list = new ArrayList<>();
        list.add("One"); list.add("Two");
        Map<String, Integer> map = new HashMap<>();
        map.put("A",1); map.put("B",2);

        // Lambda
        list.forEach(e -> System.out.println("Lambda: " + e));

        // Try-catch-finally
        try {
            int x = 10 / 2;
            if(x == 5) throw new IOException("Example exception");
        } catch(IOException | ArithmeticException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Finally executed");
        }

        // Try-with-resources
        try(Scanner scanner = new Scanner(System.in)) {
            System.out.println("Scanner ready (no input needed)");
        } catch(Exception e) {
            e.printStackTrace();
        }

        // Object creation
        Dog dog = new Dog("Labrador");
        dog.makeSound();
        try { dog.play(); } catch(InterruptedException e) { }

        // Interface implementation (anonymous class)
        Greeter greeter = new Greeter() {
            public void greet(String name) {
                System.out.println("Hello " + name);
            }
        };
        greeter.greet("World");

        // Generics
        Box<String> box = new Box<>("Generics!");
        System.out.println(box.get());

        // Casting
        double d = 3.5;
        int di = (int)d;
        System.out.println("Casted: " + di);

        // instanceof
        if(dog instanceof Animal) System.out.println("dog is Animal");

        // synchronized
        synchronized(MutationTestDemo.class) { counter++; }

        // assert
        assert counter >= 0;

        // null check
        String s = null;
        if(s == null) System.out.println("s is null");

        // break & continue handled in loops above
    }
}