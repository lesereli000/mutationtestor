package mutationtester;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import mutationtester.msl.MutationRule;
import mutationtester.msl.MutationRuleGenerator;

/** "Group: <name>" nests a group inside another, each closed by its own End. */
class SpecGroupTests {

    @Test
    void nestedGroupsJoinTheirNamesInLabels() throws IOException {
        List<String> labels = labels("""
                Conditionals

                	Replace: {quantity} == {quantity}
                	With: {quantity} != {quantity}

                	Group: Boundary
                		Replace: {quantity} <= {quantity}
                		With: {quantity} < {quantity}

                		Group: Strict
                			Replace: {quantity} < {quantity}
                			With: {quantity} <= {quantity}
                		End
                	End

                	Replace: {quantity} && {quantity}
                	With: {quantity} || {quantity}

                End
                """);
        assertEquals(List.of(
                "Conditionals: == → !=",
                "Conditionals > Boundary: <= → <",
                "Conditionals > Boundary > Strict: < → <=",
                // back in the outer group once the nested one's End closes it
                "Conditionals: && → ||"), labels);
    }

    @Test
    void groupsWithoutNestingKeepTheirLabelsAndNotes() throws IOException {
        List<String> labels = labels("""
                Math Operators:

                	Swaps plus for minus.
                	Replace: {quantity} + {quantity}
                	With: {quantity} - {quantity}
                	And: Inverse

                End

                Logic
                	Replace: {quantity} && {quantity}
                	With: {quantity} || {quantity}
                """);
        // the note stays a note, and the last group can still run to the end of the file
        assertEquals(List.of("Math Operators: + → -", "Math Operators: - → +", "Logic: && → ||"), labels);
    }

    @Test
    void groupLineBetweenGroupsNamesTheNextGroup() throws IOException {
        List<String> labels = labels("""
                Group: Math
                	Replace: {quantity} + {quantity}
                	With: {quantity} - {quantity}
                End

                Group: Logic
                	Replace: {quantity} && {quantity}
                	With: {quantity} || {quantity}
                End
                """);
        assertEquals(List.of("Math: + → -", "Logic: && → ||"), labels);
    }

    @Test
    void nestedGroupWithoutEndNamesItsLine() {
        // A top-level group may run to the end of the file, but a nested one may not
        IOException e = assertThrows(IOException.class, () -> labels("""
                Conditionals
                	Group: Boundary
                		Replace: {quantity} <= {quantity}
                		With: {quantity} < {quantity}
                """));
        assertTrue(e.getMessage().contains("line 2:"), e.getMessage());
        assertTrue(e.getMessage().contains("\"Group: Boundary\" has no End"), e.getMessage());
    }

    @Test
    void groupNeedsAName() {
        IOException e = assertThrows(IOException.class, () -> labels("""
                Conditionals
                	Group:
                End
                """));
        assertTrue(e.getMessage().contains("line 2:"), e.getMessage());
        assertTrue(e.getMessage().contains("needs a name"), e.getMessage());
    }

    private static List<String> labels(String spec) throws IOException {
        return MutationRuleGenerator.generateRules(spec.lines().toList()).stream()
                .map(MutationRule::getLabel)
                .toList();
    }
}
