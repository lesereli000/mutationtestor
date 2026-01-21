using System;

public class Demo {

    static int Add(int a, int b) {
        return a + b; // mutant: return a - b;
    }

    static int Chain(int a, int b, int c) {
        return (a + b) + c; // mutant: return (a - b) + c; (outer + NOT mutated for now because left is ')')
    }

    static int ArrayPlus(int[] arr, int i) {
        return arr[i] + 1; // mutant: return arr[i] - 1;
    }

    static int UnaryPlus(int a) {
        return +a; // mutant: return -a;
    }

    static int PlusEquals() {
        int x = 2;
        x += 3; // mutant: x += 3; (+= should NOT be mutated)
        return x;
    }

    static int PlusPlus() {
        int x = 2;
        x++; // mutant: x++; (++ should NOT be mutated)
        return x;
    }

    static void GreaterToGE() {
        int x = 6;
        int equal = x;

        if(x > equal){
            Console.WriteLine("GreaterToGE: passes");
        } else {
            Console.WriteLine("GreaterToGE: fails");
        }
    }

    static void GEToGreater() {
        int x = 6;
        int equal = x;

        if(x >= equal){
            Console.WriteLine("GEToGreater: fails");
        } else {
            Console.WriteLine("GEToGreater: passes");
        }
    }

    public static void Main(String[] args) {
        Console.WriteLine("string literal has +: 2+3"); // mutant: same (inside string)
        Console.WriteLine("concat with int: " + Add(2, 3)); // mutant: same '+' (string concat), but add(2,3) changes 5 -> -1
        Console.WriteLine("concat with char: " + '+'); // mutant: same (string concat and char literal)

        // line comment with + should stay: a + b  // mutant: same (comment)
        /*
          block comment with + should stay: a + b  // mutant: same (comment)
        */

        Console.WriteLine(Add(2, 3)); // output mutant: 5 -> -1
        Console.WriteLine(Chain(1, 2, 3)); // output mutant (with current rules): 6 -> 2
        Console.WriteLine(ArrayPlus(new int[]{10, 20, 30}, 1)); // output mutant: 21 -> 19
        Console.WriteLine(UnaryPlus(7)); // output mutant: 7 -> -7
        Console.WriteLine(PlusEquals()); // output mutant: 5 -> 5
        Console.WriteLine(PlusPlus()); // output mutant: 3 -> 3

        Console.WriteLine(1 + 2 + 3); // mutant: 1 - 2 - 3  (left-associative) output: 6 -> -4
        Console.WriteLine((1 + 2) + 3); // mutant: (1 - 2) + 3 (outer + NOT mutated for now because left is ')') output: 6 -> 2

        GreaterToGE();
        GEToGreater();
    }
}
