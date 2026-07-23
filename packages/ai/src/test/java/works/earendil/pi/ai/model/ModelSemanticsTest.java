package works.earendil.pi.ai.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelSemanticsTest {
    @Test
    void joinsTextBlocksAndTreatsReasoningAsPartOfOutputUsage() {
        assertThat(Content.text(List.of(
                new Content.Text("first"),
                new Content.Image("image/png", "aW1hZ2U=", null),
                new Content.Text("second"))))
                .isEqualTo("first\nsecond");
        assertThat(Content.text(null)).isEmpty();

        Usage usage = new Usage(10, 8, 2, 3, 4);
        assertThat(usage.totalInputTokens()).isEqualTo(15);
        assertThat(usage.totalTokens()).isEqualTo(23);
    }

    @Test
    void normalizesNullContentCollectionsAndSupportsMaxThinking() {
        assertThat(new Message.User(null, null).content()).isEmpty();
        assertThat(new Content.Text(null).text()).isEmpty();
        assertThat(ThinkingLevel.fromWireName("max")).isEqualTo(ThinkingLevel.MAX);
    }
}
