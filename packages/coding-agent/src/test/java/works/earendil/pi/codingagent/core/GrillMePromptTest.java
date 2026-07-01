package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GrillMePromptTest {
    @Test
    void buildsInterviewPromptWithTopic() {
        String prompt = GrillMePrompt.build("checkout redesign");

        assertThat(prompt)
                .contains("/grill-me")
                .contains("checkout redesign")
                .contains("Ask one concise question at a time")
                .doesNotContain("null");
    }

    @Test
    void usesFallbackTopicWhenBlank() {
        assertThat(GrillMePrompt.build(" "))
                .contains("current product, design, or implementation decision");
    }

    @Test
    void buildsStatefulInterviewPromptWithAnswerHistory() {
        String prompt = GrillMePrompt.build("checkout redesign", "constraints",
                java.util.List.of("Mobile conversion is dropping.", "Legal review is required."),
                "Use /grill-me answer <answer>.");

        assertThat(prompt)
                .contains("phase: constraints")
                .contains("answers recorded: 2")
                .contains("1. Mobile conversion is dropping.")
                .contains("2. Legal review is required.")
                .contains("/grill-me answer");

        String promptWithQuestions = GrillMePrompt.build("checkout redesign", "constraints",
                List.of("Mobile conversion is dropping."),
                List.of("Which payment step loses the most users?"),
                "Use /grill-me answer <answer>.");

        assertThat(promptWithQuestions)
                .contains("Previous assistant question summaries:")
                .contains("Which payment step loses the most users?");
    }

    @Test
    void tracksInterviewPhaseStatusAndReset() {
        GrillMeInterview interview = new GrillMeInterview();

        String start = interview.start("checkout redesign");
        assertThat(start)
                .contains("checkout redesign")
                .contains("phase: discovery")
                .contains("answers recorded: 0");
        assertThat(interview.status())
                .contains("status: active")
                .contains("phase: discovery")
                .contains("answers: 0");

        String next = interview.answer("Mobile checkout has high drop-off on payment.");
        assertThat(next)
                .contains("answers recorded: 1")
                .contains("Mobile checkout has high drop-off on payment.");
        assertThat(interview.status())
                .contains("answers: 1")
                .contains("1. Mobile checkout has high drop-off on payment.");

        interview.answer("Payment provider cannot change this quarter.");
        assertThat(interview.phase()).isEqualTo("constraints");
        assertThat(interview.reset())
                .contains("status: reset")
                .contains("topic: checkout redesign");
        assertThat(interview.status())
                .contains("status: none")
                .contains("start: /grill-me <topic>");
    }

    @Test
    void persistsRestoresAndSummarizesAssistantQuestions() throws Exception {
        SessionManager manager = SessionManager.inMemory(Path.of("/tmp/project"));
        GrillMeInterview interview = new GrillMeInterview();
        interview.start("checkout redesign");
        manager.appendMessage(assistantMessage("Quick context.\nWhich payment step loses the most users?"));

        interview.captureLatestAssistantQuestion(manager);
        interview.answer("The payment authorization screen loses the most users.");
        interview.persist(manager);

        GrillMeInterview restored = GrillMeInterview.fromSession(manager);

        assertThat(restored.active()).isTrue();
        assertThat(restored.topic()).isEqualTo("checkout redesign");
        assertThat(restored.answers()).containsExactly("The payment authorization screen loses the most users.");
        assertThat(restored.assistantQuestions()).containsExactly("Which payment step loses the most users?");
        assertThat(restored.answer("Fraud review cannot change this quarter."))
                .contains("Previous assistant question summaries:")
                .contains("Which payment step loses the most users?");

        restored.reset();
        restored.persist(manager);
        assertThat(GrillMeInterview.fromSession(manager).status())
                .contains("status: none");
    }

    private static ObjectNode assistantMessage(String text) {
        ObjectNode message = JsonCodec.mapper().createObjectNode();
        message.put("role", "assistant");
        message.set("content", JsonCodec.mapper().valueToTree(List.of(new works.earendil.pi.ai.model.Content.Text(text))));
        return message;
    }
}
