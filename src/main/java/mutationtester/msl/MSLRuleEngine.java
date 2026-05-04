package mutationtester.msl;

import mutationtester.model.MutationPoint;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MSLRuleEngine {

    public static List<MutationPoint> evaluate(List<MSLNode> dataNodes, List<MutationRule> rules, File sourceFile) {
        List<MutationPoint> points = new ArrayList<>();
        
        for (MSLNode dataNode : dataNodes) {
            for (MutationRule rule : rules) {
                MutationPoint mp = rule.evaluate(dataNode, sourceFile);
                if (mp != null) {
                    points.add(mp);
                }
            }
        }
        
        return points;
    }
}
