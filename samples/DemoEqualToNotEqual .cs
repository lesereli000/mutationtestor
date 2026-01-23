using System;

class DemoEqualToNotEqual {

    static bool IsEqual(int a, int b) {
        return a == b; // return a != b;
    }

    static bool ChainedEquality(int a, int b, int c) {
        return (a == b) && (b == c); // return (a != b) && (b != c);
    }

    static bool ArrayEquals(int[] arr, int i, int val) {
        return arr[i] == val; // return arr[i] != val;
    }

    static bool NullCheck(string s) {
        return s == null; // return s != null;
    }

    static void TestBasicEquality() {
        int x = 5;
        int y = 5;
        
        if (x == y) {
            Console.WriteLine("TestBasicEquality: fails (should pass)");
        } else {
            Console.WriteLine("TestBasicEquality: passes (should fail)");
        }
    }

    static void TestInequalityCheck() {
        int x = 5;
        int y = 10;
        
        if (x == y) {
            Console.WriteLine("TestInequalityCheck: passes");
        } else {
            Console.WriteLine("TestInequalityCheck: fails");
        }
    }

    static void TestMultipleConditions() {
        int a = 5;
        int b = 5;
        int c = 5;
        
        if ((a == b) && (b == c)) {
            Console.WriteLine("TestMultipleConditions: fails (all equal)");
        } else {
            Console.WriteLine("TestMultipleConditions: passes");
        }
    }

    static void Main() {
        Console.WriteLine("string literal has ==: a==b"); // same (inside string)
        Console.WriteLine("concat with bool: " + IsEqual(5, 5)); // stays, but IsEqual returns opposite
        Console.WriteLine("concat with char: " + '='); // same (string concat and char literal)


        Console.WriteLine(IsEqual(5, 5)); // True -> False
        Console.WriteLine(IsEqual(5, 10)); // False -> True
        Console.WriteLine(ChainedEquality(5, 5, 5)); // True -> False
        Console.WriteLine(ChainedEquality(5, 5, 10)); // False -> False
        Console.WriteLine(ArrayEquals(new int[]{10, 20, 30}, 1, 20)); // True -> False
        Console.WriteLine(NullCheck(null)); // True -> False
        Console.WriteLine(NullCheck("test")); // False -> True

        Console.WriteLine(5 == 5); // 5 != 5 -> False
        Console.WriteLine(5 == 10); // 5 != 10 -> True
        Console.WriteLine((5 == 5) && (10 == 10)); // (5 != 5) && (10 != 10) -> False

        TestBasicEquality();
        TestInequalityCheck();
        TestMultipleConditions();
    }
}