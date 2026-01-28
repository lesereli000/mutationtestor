public class DemoLogicalOrToAnd {
    public static boolean eitherPositive(int a, int b) {
        // should mutate here
        return a > 0 || b > 0;
    }

    public static void main(String[] args) {
        System.out.println("Either positive? " + eitherPositive(2, -3));
        // should not mutate inside this string: "a || b"
    }
}
