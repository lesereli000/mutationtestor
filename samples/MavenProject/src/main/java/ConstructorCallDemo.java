package samples.MavenProject.src.main.java;

public class ConstructorCallDemo {
    public static Widget create() {
        return new Widget("demo");
    }

    public static class Widget {
        public final String name;

        public Widget(String name) {
            this.name = name;
        }
    }
}
