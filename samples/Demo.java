public class Demo {

    static int add(int a, int b) {
        return a + b; // mutant: return a - b;
    }

    static int chain(int a, int b, int c) {
        return (a + b) + c; // mutant: return (a - b) - c;
    }

    static int arrayPlus(int[] arr, int i) {
        return arr[i] + 1; // mutant: return arr[i] - 1;
    }

    static int unaryPlus(int a) {
        return +a; // mutant: return -a;
    }

    static int plusEquals() {
        int x = 2;
        x += 3; // mutant: x += 3; (+= should NOT be mutated)
        return x;
    }

    static int plusPlus() {
        int x = 2;
        x++; // mutant: x++; (++ should NOT be mutated)
        return x;
    }

    static void GreaterToGE() {
        int x = 6;
        int equal = x;

        if(x > equal){
            System.out.println("GreaterToGE: passes");
        } else {
            System.out.println("GreaterToGE: fails");
        }
    }

    static void GEToGreater() {
        int x = 6;
        int equal = x;

        if (x >= equal) {
            System.out.println("GEToGreater: fails");
        } else {
            System.out.println("GEToGreater: passes");
        }
    }

    public static void main(String[] args) {
        System.out.println("string literal has +: 2+3"); // mutant: same (inside string)
        System.out.println("concat with int: " + add(2, 3)); // mutant: same '+' (string concat), but add(2,3) changes 5 -> -1
        System.out.println("concat with char: " + '+'); // mutant: same (string concat and char literal)

        // line comment with + should stay: a + b  // mutant: same (comment)
        /*
          block comment with + should stay: a + b  // mutant: same (comment)
        */

        System.out.println(add(2, 3)); // output mutant: 5 -> -1
        System.out.println(chain(1, 2, 3)); // output mutant: 6 -> -4
        System.out.println(arrayPlus(new int[]{10, 20, 30}, 1)); // output mutant: 21 -> 19
        System.out.println(unaryPlus(7)); // output mutant: 7 -> -7
        System.out.println(plusEquals()); // output mutant: 5 -> 5
        System.out.println(plusPlus()); // output mutant: 3 -> 3

        System.out.println(1 + 2 + 3); // mutant: 1 - 2 - 3  (left-associative) output: 6 -> -4
        System.out.println((1 + 2) + 3); // mutant: (1 - 2) - 3 output: 6 -> -4

        GreaterToGE();
        GEToGreater();
    }
}
