public class DemoNotEqualToEqual {

    static boolean isNotEqual(int a, int b) {
        return a != b; // return a == b;
    }

    static boolean chainedInequality(int a, int b, int c) {
        return (a != b) && (b != c); // return (a == b) && (b == c);
    }

    static boolean arrayNotEquals(int[] arr, int i, int val) {
        return arr[i] != val; // return arr[i] == val;
    }

    static boolean notNullCheck(String s) {
        return s != null; // return s == null;
    }

    static void testBasicInequality() {
        int x = 5;
        int y = 10;
        
        if (x != y) {
            System.out.println("testBasicInequality: fails (should pass)");
        } else {
            System.out.println("testBasicInequality: passes (should fail)");
        }
    }

    static void testEqualityCheck() {
        int x = 5;
        int y = 5;
        
        if (x != y) {
            System.out.println("testEqualityCheck: passes");
        } else {
            System.out.println("testEqualityCheck: fails");
        }
    }

    static void testMultipleConditions() {
        int a = 5;
        int b = 10;
        int c = 15;
        
        if ((a != b) && (b != c)) {
            System.out.println("testMultipleConditions: fails (all different)");
        } else {
            System.out.println("testMultipleConditions: passes");
        }
    }

    public static void main(String[] args) {
        System.out.println("string literal has !=: a!=b"); // same (inside string)
        System.out.println("concat with bool: " + isNotEqual(5, 10)); // stays, but isNotEqual returns opposite


        System.out.println(isNotEqual(5, 10)); // true -> false
        System.out.println(isNotEqual(5, 5)); // false -> true
        System.out.println(chainedInequality(5, 10, 15)); // true -> false
        System.out.println(chainedInequality(5, 5, 10)); // false -> false (still false)
        System.out.println(arrayNotEquals(new int[]{10, 20, 30}, 1, 25)); // true -> false
        System.out.println(notNullCheck(null)); // false -> true
        System.out.println(notNullCheck("test")); // output mutant: true -> false

        System.out.println(5 != 10); // 5 == 10 -> false
        System.out.println(5 != 5); // 5 == 5 -> true
        System.out.println((5 != 10) && (10 != 15)); // (5 == 10) && (10 == 15) -> false

        testBasicInequality();
        testEqualityCheck();
        testMultipleConditions();
    }
