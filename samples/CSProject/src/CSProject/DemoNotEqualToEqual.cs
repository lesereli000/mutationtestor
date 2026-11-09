using System;

class DemoNotEqualToEqual {

    static bool IsNotEqual(int a, int b) {
        return a != b; // return a == b;
    }

    static bool ChainedInequality(int a, int b, int c) {
        return (a != b) && (b != c); // return (a == b) && (b == c);
    }

    static bool ArrayNotEquals(int[] arr, int i, int val) {
        return arr[i] != val; // return arr[i] == val;
    }

    static bool NotNullCheck(string s) {
        return s != null; // return s == null;
    }

    static void TestBasicInequality() {
        int x = 5;
        int y = 10;
        
        if (x != y) {
            Console.WriteLine("TestBasicInequality: fails (should pass)");
        } else {
            Console.WriteLine("TestBasicInequality: passes (should fail)");
        }
    }

    static void TestEqualityCheck() {
        int x = 5;
        int y = 5;
        
        if (x != y) {
            Console.WriteLine("TestEqualityCheck: passes");
        } else {
            Console.WriteLine("TestEqualityCheck: fails");
        }
    }

    static void TestMultipleConditions() {
        int a = 5;
        int b = 10;
        int c = 15;
        
        if ((a != b) && (b != c)) {
            Console.WriteLine("TestMultipleConditions: fails (all different)");
        } else {
            Console.WriteLine("TestMultipleConditions: passes");
        }
    }

    static void Main() {
        Console.WriteLine("string literal has !=: a!=b"); // same (inside string)
        Console.WriteLine("concat with bool: " + IsNotEqual(5, 10)); // stays, but IsNotEqual returns opposite
        

        Console.WriteLine(IsNotEqual(5, 10)); // True -> False
        Console.WriteLine(IsNotEqual(5, 5)); // False -> True
        Console.WriteLine(ChainedInequality(5, 10, 15)); // True -> False
        Console.WriteLine(ChainedInequality(5, 5, 10)); // False -> False
        Console.WriteLine(ArrayNotEquals(new int[]{10, 20, 30}, 1, 25)); // True -> False
        Console.WriteLine(NotNullCheck(null)); // False -> True
        Console.WriteLine(NotNullCheck("test")); // True -> False

        Console.WriteLine(5 != 10); // 5 == 10 -> False
        Console.WriteLine(5 != 5); // 5 == 5 -> True
        Console.WriteLine((5 != 10) && (10 != 15)); // (5 == 10) && (10 == 15) -> False

        TestBasicInequality();
        TestEqualityCheck();
        TestMultipleConditions();
    }
}
