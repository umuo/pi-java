package works.earendil.pi.codingagent.core;

public final class GrillMePrompt {
    private GrillMePrompt() {
    }

    public static String build(String arguments) {
        String topic = arguments == null || arguments.isBlank()
                ? "the user's current product, design, or implementation decision"
                : arguments.trim();
        return """
                You are running the /grill-me command.

                Interview me before proposing a solution for: %s

                Ask one concise question at a time. Focus on requirements, constraints, users, tradeoffs,
                success criteria, and failure modes. Do not propose an implementation until the important
                unknowns have been answered. When enough information is available, summarize the decision
                space and recommend a concrete next step.
                """.formatted(topic).trim();
    }
}
