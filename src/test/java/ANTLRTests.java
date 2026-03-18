import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ANTLRTests {

    @Test
    public void testANTLRwithJava() throws IOException {
        MyParser parser = new MyParser("samples/MavenProject/src/main/java/Add1.java");
    }

}
