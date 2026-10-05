package mutationtester.demo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class prototypeSampleTest {
    @Test
    public void testMutationDemo() {
        // Run the main method to cover most lines
        MutationTestDemo.main(new String[]{});
        
        // Cover remaining lines
        MutationTestDemo.staticMethod();
        MutationTestDemo demo = new MutationTestDemo();
        assertEquals(6, demo.sum(1, 2, 3));
        
        Dog newDog = Dog.createDog("Poodle");
        assertNotNull(newDog);
        
        Box<Integer> intBox = new Box<>(1);
        intBox.set(2);
        assertEquals(2, intBox.get());
    }
}
