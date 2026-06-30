package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;

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
}
