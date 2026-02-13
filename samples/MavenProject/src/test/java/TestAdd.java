package samples.MavenProject.src.test.java;

import org.junit.jupiter.api.Test;
import samples.MavenProject.src.main.java.Add1;
import samples.MavenProject.src.main.java.Add2;

import static org.junit.jupiter.api.Assertions.assertEquals;

// run with ' cd .\samples\MavenProject\; mvn test '

public class TestAdd {

    @Test
    public void testAdd1() {

        assertEquals(6, Add1.add1(5));
        assertEquals(0, Add1.add1(-1));
        assertEquals(789, Add1.add1(788));
        assertEquals(12, Add1.add1(11));
        assertEquals(Integer.MAX_VALUE, Add1.add1(Integer.MAX_VALUE - 1));

    }

    @Test
    public void testAdd2() {

        assertEquals(7, Add2.add2(5));
        assertEquals(1, Add2.add2(-1));
        assertEquals(790, Add2.add2(788));
        assertEquals(13, Add2.add2(11));
        assertEquals(Integer.MAX_VALUE, Add2.add2(Integer.MAX_VALUE - 2));

    }

}