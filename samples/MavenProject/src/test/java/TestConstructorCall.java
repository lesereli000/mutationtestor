package samples.MavenProject.src.test.java;

import org.junit.jupiter.api.Test;
import samples.MavenProject.src.main.java.ConstructorCallDemo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestConstructorCall {

    @Test
    public void testCreate() {
        ConstructorCallDemo.Widget widget = ConstructorCallDemo.create();
        assertNotNull(widget);
        assertEquals("demo", widget.name);
    }
}
