package mutationtester.msl;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MSLPatternFactoryTest {

    @Test
    void testParseQuantity() {
        MSLNode node = MSLPatternFactory.parse("{quantity}");
        assertEquals("Quantity", node.getType());
        assertTrue(node.isWildcard());
        assertNotNull(node.getCaptureName()); // e.g. "quant1"
    }

    @Test
    void testParseBinaryExpr() {
        MSLNode node = MSLPatternFactory.parse("{quantity} + {quantity}");
        assertEquals("BinaryExpr", node.getType());
        assertFalse(node.isWildcard());
        assertEquals("+", node.getText());
        
        List<MSLNode> children = node.getChildren();
        assertEquals(2, children.size());
        
        MSLNode left = children.get(0);
        assertEquals("Quantity", left.getType());
        assertTrue(left.isWildcard());
        
        MSLNode right = children.get(1);
        assertEquals("Quantity", right.getType());
        assertTrue(right.isWildcard());
        
        assertNotEquals(left.getCaptureName(), right.getCaptureName(), "Captures must have distinct names");
    }
}
