package mutationtester.msl;

import org.junit.jupiter.api.Test;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MSLNodeTest {

    @Test
    void testExactMatch() {
        MSLNode dummy = new MSLNode("Quantity", "5", List.of());
        MSLNode data = new MSLNode("Quantity", "5", 1, 1, List.of());
        
        Map<String, MSLNode> captures = new HashMap<>();
        assertTrue(dummy.matches(data, captures));
    }

    @Test
    void testWildcardCapture() {
        MSLNode dummy = new MSLNode("Quantity", "quant1"); // Wildcard dummy
        MSLNode data = new MSLNode("Quantity", "5", 10, 5, List.of());
        
        Map<String, MSLNode> captures = new HashMap<>();
        assertTrue(dummy.matches(data, captures));
        assertEquals(1, captures.size());
        assertEquals(data, captures.get("quant1"));
    }
    
    @Test
    void testRecursiveMatch() {
        MSLNode dummyLeft = new MSLNode("Quantity", "leftQuant");
        MSLNode dummyRight = new MSLNode("Quantity", "rightQuant");
        MSLNode dummyRoot = new MSLNode("BinaryExpr", "+", List.of(dummyLeft, dummyRight));
        
        MSLNode dataLeft = new MSLNode("Quantity", "x", 1, 1, List.of());
        MSLNode dataRight = new MSLNode("Quantity", "y", 1, 5, List.of());
        MSLNode dataRoot = new MSLNode("BinaryExpr", "+", 1, 3, List.of(dataLeft, dataRight));
        
        Map<String, MSLNode> captures = new HashMap<>();
        assertTrue(dummyRoot.matches(dataRoot, captures));
        assertEquals(2, captures.size());
        assertEquals(dataLeft, captures.get("leftQuant"));
        assertEquals(dataRight, captures.get("rightQuant"));
    }
    
    @Test
    void testMismatchType() {
        MSLNode dummy = new MSLNode("Quantity", "quant");
        MSLNode data = new MSLNode("StringLiteral", "hello", 1, 1, List.of());
        
        Map<String, MSLNode> captures = new HashMap<>();
        assertFalse(dummy.matches(data, captures));
        assertTrue(captures.isEmpty());
    }
}
