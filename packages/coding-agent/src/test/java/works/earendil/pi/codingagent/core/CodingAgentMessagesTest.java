package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CodingAgentMessagesTest {
    @Test
    void formatsBashExecutionLikeTypeScriptImplementation() {
        CodingAgentMessages.BashExecutionMessage message = new CodingAgentMessages.BashExecutionMessage(
                "npm test", "ok", 2, false, true, "/tmp/full.log", Instant.parse("2026-06-28T00:00:00Z"), false);

        assertThat(CodingAgentMessages.bashExecutionToText(message)).isEqualTo(String.join("\n",
                "Ran `npm test`",
                "```",
                "ok",
                "```",
                "",
                "Command exited with code 2",
                "",
                "[Output truncated. Full output: /tmp/full.log]"));
    }

    @Test
    void convertsCodingAgentCustomMessagesToLlmMessages() {
        Instant timestamp = Instant.parse("2026-06-28T01:02:03Z");
        List<AgentMessage> messages = List.of(
                new AgentMessage.Custom("bashExecution",
                        new CodingAgentMessages.BashExecutionMessage("pwd", "/repo", 0, false,
                                false, null, timestamp, false),
                        true, null),
                new AgentMessage.Custom("bashExecution",
                        new CodingAgentMessages.BashExecutionMessage("secret", "hidden", 0, false,
                                false, null, timestamp, true),
                        true, null),
                new AgentMessage.Custom("custom",
                        CodingAgentMessages.createCustomMessage("notice", "hello", true, null, timestamp),
                        true, null),
                new AgentMessage.Custom("custom",
                        CodingAgentMessages.createCustomMessage("notice", "from extension", true, null,
                                "extension", timestamp),
                        true, null),
                new AgentMessage.Custom("branchSummary",
                        CodingAgentMessages.createBranchSummaryMessage("branch notes", "abc", timestamp),
                        true, null),
                new AgentMessage.Custom("compactionSummary",
                        CodingAgentMessages.createCompactionSummaryMessage("old notes", 42, timestamp),
                        true, null)
        );

        List<Message> converted = CodingAgentMessages.convertToLlm(messages);

        assertThat(converted).hasSize(5);
        assertThat(text(converted.get(0))).contains("Ran `pwd`").contains("/repo");
        assertThat(text(converted.get(1))).isEqualTo("hello");
        assertThat(text(converted.get(2))).isEqualTo("from extension");
        assertThat(((Message.User) converted.get(1)).source()).isNull();
        assertThat(((Message.User) converted.get(2)).source()).isEqualTo("extension");
        assertThat(text(converted.get(3))).contains(CodingAgentMessages.BRANCH_SUMMARY_PREFIX).contains("branch notes");
        assertThat(text(converted.get(4))).contains(CodingAgentMessages.COMPACTION_SUMMARY_PREFIX).contains("old notes");
        assertThat(converted).allMatch(message -> message instanceof Message.User);
        assertThat(converted).extracting(Message::timestamp).containsOnly(timestamp);
    }

    @Test
    void passesThroughLlmMessages() {
        Message.User user = new Message.User(List.of(new Content.Text("plain")), Instant.parse("2026-06-28T00:00:00Z"));

        assertThat(CodingAgentMessages.convertToLlm(List.of(new AgentMessage.Llm(user)))).containsExactly(user);
    }

    @Test
    void canFilterImagesWhenBlockImagesIsEnabled() {
        Message.User user = new Message.User(List.of(
                new Content.Text("inspect"),
                new Content.Image("image/png", "aW1hZ2U=", null)
        ), Instant.parse("2026-06-28T00:00:00Z"), "extension");

        List<Message> converted = CodingAgentMessages.convertToLlm(List.of(new AgentMessage.Llm(user)), true);

        assertThat(converted).hasSize(1);
        Message.User filtered = (Message.User) converted.getFirst();
        assertThat(filtered.content()).containsExactly(
                new Content.Text("inspect"),
                new Content.Text("[Image content omitted because images.blockImages is enabled: 1 image]")
        );
        assertThat(filtered.source()).isEqualTo("extension");
    }

    private static String text(Message message) {
        Content first = message instanceof Message.User user ? user.content().get(0) : null;
        return first instanceof Content.Text text ? text.text() : "";
    }
}
