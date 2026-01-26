public class DemoLogicalAndToOr {
    public static boolean bothPositive(int a, int b) {
        // should mutate here
        return a > 0 && b > 0;
    }

    public static void main(String[] args) {
        System.out.println("Both positive? " + bothPositive(2, 3));
        // should not mutate inside this string: "a && b"
    }
}
