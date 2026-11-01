public class DemoMinusToPlus {

    static int sub(int a, int b) {
        return a - b; // mutant: return a + b;
    }

    static int chain(int a, int b, int c) {
        return (a - b) - c; // mutant: return (a + b) + c;
    }

    static int arrayMinus(int[] arr, int i) {
        return arr[i] - 1; // mutant: return arr[i] + 1;
    }

    static int unaryMinus(int a) {
        return -a; // mutant: return +a;
    }

    static int minusEquals() {
        int x = 5;
        x -= 3; // mutant: x -= 3; (-= should NOT be mutated)
        return x;
    }

    static int minusMinus() {
        int x = 3;
        x--; // mutant: x--; (-- should NOT be mutated)
        return x;
    }

    public static void main(String[] args) {
        System.out.println("string literal has -: 2-3"); // mutant: same (inside string)
        System.out.println("concat with int: " + sub(5, 3)); // mutant: same '-' (string concat), but sub(5,3) changes 2 -> 8
        System.out.println("concat with char: " + '-'); // mutant: same (string concat and char literal)

        // line comment with - should stay: a - b  // mutant: same (comment)
        /*
          block comment with - should stay: a - b  // mutant: same (comment)
        */

        System.out.println(sub(5, 3)); // output mutant: 2 -> 8
        System.out.println(chain(10, 3, 2)); // output mutant: 5 -> 15
        System.out.println(arrayMinus(new int[]{10, 20, 30}, 1)); // output mutant: 19 -> 21
        System.out.println(unaryMinus(7)); // output mutant: -7 -> 7
        System.out.println(minusEquals()); // output mutant: 2 -> 2
        System.out.println(minusMinus()); // output mutant: 2 -> 2

        System.out.println(10 - 2 - 3); // mutant: 10 + 2 + 3 (left-associative) output: 5 -> 15
        System.out.println((10 - 2) - 3); // mutant: (10 + 2) + 3 output: 5 -> 15

    }
}
