package works.earendil.pi.codingagent.core;

import java.util.List;

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

    public static String build(String topic, String phase, List<String> answers, String controls) {
        return build(topic, phase, answers, List.of(), controls);
    }

    public static String build(String topic, String phase, List<String> answers, List<String> assistantQuestions,
                               String controls) {
        String base = build(topic);
        List<String> safeAnswers = answers == null ? List.of() : List.copyOf(answers);
        List<String> safeQuestions = assistantQuestions == null ? List.of() : List.copyOf(assistantQuestions);
        StringBuilder prompt = new StringBuilder(base);
        prompt.append("\n\nInterview state:\n");
        prompt.append("phase: ").append(phase == null || phase.isBlank() ? "discovery" : phase).append("\n");
        prompt.append("answers recorded: ").append(safeAnswers.size()).append("\n");
        if (!safeAnswers.isEmpty()) {
            prompt.append("Answer history:\n");
            for (int i = 0; i < safeAnswers.size(); i++) {
                prompt.append(i + 1).append(". ").append(safeAnswers.get(i)).append("\n");
            }
        }
        if (!safeQuestions.isEmpty()) {
            prompt.append("Previous assistant question summaries:\n");
            for (int i = 0; i < safeQuestions.size(); i++) {
                prompt.append(i + 1).append(". ").append(safeQuestions.get(i)).append("\n");
            }
        }
        if (controls != null && !controls.isBlank()) {
            prompt.append("\n").append(controls.trim());
        }
        return prompt.toString().trim();
    }
}
