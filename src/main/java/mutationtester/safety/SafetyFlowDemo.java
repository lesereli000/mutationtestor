package mutationtester.safety;

import java.util.List;

/** Prints proposed safety prompts without executing any mutants. */
public final class SafetyFlowDemo {
    private SafetyFlowDemo() {
    }

    public static void main(String[] args) {
        List<SafetyAssessment> examples = List.of(
                new SafetyAssessment(
                        SafetyLevel.SAFE,
                        "PURE_ARITHMETIC",
                        "The mutation changes arithmetic inside a side-effect-free expression.",
                        0.98,
                        "prototype-rule"),
                new SafetyAssessment(
                        SafetyLevel.REVIEW,
                        "NETWORK_CALL",
                        "The mutation may change whether an external request is sent.",
                        0.82,
                        "prototype-rule"),
                new SafetyAssessment(
                        SafetyLevel.BLOCK,
                        "DATABASE_DESTRUCTION",
                        "The mutation broadens a targeted delete into an unbounded delete.",
                        1.0,
                        "prototype-rule"));

        System.out.println("Safety-aware mutation flow prototype");
        System.out.println("No mutants are executed by this demo.\n");

        for (SafetyAssessment assessment : examples) {
            System.out.println(SafetyGate.consolePrompt(assessment));
            System.out.println("Unattended action: " + SafetyGate.unattendedAction(assessment));
            System.out.println("----------------------------------------");
        }
    }
}
