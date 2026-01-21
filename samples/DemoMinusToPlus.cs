using System;

class DemoMinusToPlus {

    static int Sub(int a, int b) {
        return a - b; // mutant: return a + b;
    }

    static int Chain(int a, int b, int c) {
        return (a - b) - c; // mutant: return (a + b) + c;
    }

    static int ArrayMinus(int[] arr, int i) {
        return arr[i] - 1; // mutant: return arr[i] + 1;
    }

    static int UnaryMinus(int a) {
        return -a; // mutant: return +a;
    }

    static int MinusEquals() {
        int x = 5;
        x -= 3; // mutant: x += 3 (should NOT change semantics)
        return x;
    }

    static int MinusMinus() {
        int x = 3;
        x--;
        return x;
    }

    static void Main() {
        Console.WriteLine(Sub(5, 3));                     // 2 -> 8
        Console.WriteLine(Chain(10, 3, 2));               // 5 -> 15
        Console.WriteLine(ArrayMinus(new int[]{10,20,30}, 1)); // 19 -> 21
        Console.WriteLine(UnaryMinus(7));                 // -7 -> 7
        Console.WriteLine(MinusEquals());                 // 2 -> 2
        Console.WriteLine(MinusMinus());                  // 2 -> 2

        Console.WriteLine(10 - 2 - 3);                     // 5 -> 15
        Console.WriteLine((10 - 2) - 3);                   // 5 -> 15

        // ===== tests: '-' inside comments should NOT be mutated =====
        // 10 - 2 - 3
        // (10 - 2) - 3
        // a-b-c
    }
}
