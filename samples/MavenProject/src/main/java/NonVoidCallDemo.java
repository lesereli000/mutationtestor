package samples.MavenProject.src.main.java;

public class NonVoidCallDemo {
    public static int read() {
        return compute();
    }

    public static int assign() {
        int value = compute();
        return value;
    }

    private static int compute() {
        return 9;
    }
}
