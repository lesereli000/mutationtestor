public class DemoEqualToNotEqual {

    static boolean isEqual(int a, int b) {
        return a == b; // return a != b;
    }

    static boolean chainedEquality(int a, int b, int c) {
        return (a == b) && (b == c); // return (a != b) && (b != c);
    }

    static boolean arrayEquals(int[] arr, int i, int val) {
        return arr[i] == val; // return arr[i] != val;
    }

    static boolean nullCheck(String s) {
        return s == null; // return s != null;
    }

    static void testBasicEquality() {
        int x = 5;
        int y = 5;
        
        if (x == y) {
            System.out.println("testBasicEquality: fails (should pass)");
        } else {
            System.out.println("testBasicEquality: passes (should fail)");
        }
    }

    static void testInequalityCheck() {
        int x = 5;
        int y = 10;
        
        if (x == y) {
            System.out.println("testInequalityCheck: passes");
        } else {
            System.out.println("testInequalityCheck: fails");
        }
    }

    static void testMultipleConditions() {
        int a = 5;
        int b = 5;
        int c = 5;
        
        if ((a == b) && (b == c)) {
            System.out.println("testMultipleConditions: fails (all equal)");
        } else {
            System.out.println("testMultipleConditions: passes");
        }
    }

    public static void main(String[] args) {
        System.out.println("string literal has ==: a==b"); // same (inside string)
        System.out.println("concat with bool: " + isEqual(5, 5)); // '+' stays, but isEqual returns opposite
        System.out.println("concat with char: " + '='); // same (string concat and char literal)


        System.out.println(isEqual(5, 5)); // true -> false
        System.out.println(isEqual(5, 10)); // false -> true
        System.out.println(chainedEquality(5, 5, 5)); // true -> false
        System.out.println(chainedEquality(5, 5, 10)); // false -> false (still false)
        System.out.println(arrayEquals(new int[]{10, 20, 30}, 1, 20)); // true -> false
        System.out.println(nullCheck(null)); // true -> false
        System.out.println(nullCheck("test")); // false -> true
        System.out.println(5 == 5); // 5 != 5 -> false
        System.out.println(5 == 10); // 5 != 10 -> true
        System.out.println((5 == 5) && (10 == 10)); // (5 != 5) && (10 != 10) -> false

        testBasicEquality();
        testInequalityCheck();
        testMultipleConditions();
    }
}
