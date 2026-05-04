package mutationtester.msl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MSLNode {
    private final String type;
    private final List<MSLNode> children;
    
    // Data Context
    private final String text;
    private final int line;
    private final int column;
    
    // Pattern Context
    private final boolean isWildcard;
    private final String captureName;
    
    /**
     * Constructor A: The Data Node (Used by Scanners)
     */
    public MSLNode(String type, String text, int line, int column, List<MSLNode> children) {
        this.type = type;
        this.text = text;
        this.line = line;
        this.column = column;
        this.children = children != null ? children : new ArrayList<>();
        this.isWildcard = false;
        this.captureName = null;
    }
    
    /**
     * Constructor B: The Dummy Wildcard Node (Used by MSL Spec Parser)
     */
    public MSLNode(String type, String captureName) {
        this.type = type;
        this.captureName = captureName;
        this.isWildcard = true;
        this.children = new ArrayList<>();
        this.text = null;
        this.line = 0;
        this.column = 0;
    }
    
    /**
     * Constructor C: The Dummy Exact Node (Used by MSL Spec Parser)
     */
    public MSLNode(String type, String text, List<MSLNode> children) {
        this.type = type;
        this.text = text;
        this.children = children != null ? children : new ArrayList<>();
        this.isWildcard = false;
        this.captureName = null;
        this.line = 0;
        this.column = 0;
    }

    /**
     * The Universal Pattern Matching Algorithm
     */
    public boolean matches(MSLNode dataNode, Map<String, MSLNode> captures) {
        // 1. Strict Type Matching
        if (!Objects.equals(this.type, dataNode.type)) {
            return false;
        }
        
        // 2. Wildcard Binding
        if (this.isWildcard) {
            if (this.captureName != null) {
                captures.put(this.captureName, dataNode);
            }
            return true; // We don't check children or text of wildcards
        }
        
        // 3. Exact Text Matching (if pattern specifies it)
        if (this.text != null && !this.text.equals(dataNode.text)) {
            return false;
        }
        
        // 4. Recursive Child Matching
        if (this.children.size() != dataNode.children.size()) {
            return false;
        }
        
        for (int i = 0; i < children.size(); i++) {
            if (!this.children.get(i).matches(dataNode.children.get(i), captures)) {
                return false; // Fast fail
            }
        }
        
        return true;
    }
    
    // Getters
    public String getType() { return type; }
    public String getText() { return text; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public List<MSLNode> getChildren() { return children; }
    public String getCaptureName() { return captureName; }
    public boolean isWildcard() { return isWildcard; }
}
