package samples.MavenProject.src.test.java;

import org.junit.jupiter.api.Test;
import samples.MavenProject.src.main.java.NonVoidCallDemo;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestNonVoidCall {

    @Test
    public void testRead() {
        assertEquals(9, NonVoidCallDemo.read());
    }

    @Test
    public void testAssign() {
        assertEquals(9, NonVoidCallDemo.assign());
    }
}
