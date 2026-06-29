package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.common.json.JsonCodec;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionSupportTest {
    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");

    @Test
    void serializesConversationWithoutContinuingChatShape() {
        Message.User user = new Message.User(List.of(new Content.Text("hello")), NOW);
        Message.Assistant assistant = new Message.Assistant(List.of(
                new Content.Thinking("reasoning", null),
                new Content.Text("answer"),
                new Content.ToolCall("call-1", "read", JsonCodec.parse("{\"path\":\"README.md\"}"), List.of())
        ), "openai", "gpt", StopReason.TOOL_USE, null, null, NOW);
        Message.ToolResult toolResult = new Message.ToolResult("call-1", "read",
                List.of(new Content.Text("x".repeat(2_010))), false, null, NOW);

        String serialized = CompactionSupport.serializeConversation(List.of(user, assistant, toolResult));

        assertThat(serialized).contains("[User]: hello");
        assertThat(serialized).contains("[Assistant thinking]: reasoning");
        assertThat(serialized).contains("[Assistant]: answer");
        assertThat(serialized).contains("[Assistant tool calls]: read(path=\"README.md\")");
        assertThat(serialized).contains("[Tool result]: " + "x".repeat(2_000));
        assertThat(serialized).contains("[... 10 more characters truncated]");
    }

    @Test
    void tracksReadAndModifiedFilesFromAssistantToolCalls() {
        CompactionSupport.FileOperations fileOps = CompactionSupport.createFileOps();
        AgentMessage message = new AgentMessage.Llm(new Message.Assistant(List.of(
                new Content.ToolCall("1", "read", JsonCodec.parse("{\"path\":\"a.txt\"}"), List.of()),
                new Content.ToolCall("2", "write", JsonCodec.parse("{\"path\":\"b.txt\"}"), List.of()),
                new Content.ToolCall("3", "edit", JsonCodec.parse("{\"path\":\"a.txt\"}"), List.of())
        ), "openai", "gpt", StopReason.TOOL_USE, null, null, NOW));

        CompactionSupport.extractFileOpsFromMessage(message, fileOps);
        CompactionSupport.FileLists lists = CompactionSupport.computeFileLists(fileOps);

        assertThat(lists.readFiles()).isEmpty();
        assertThat(lists.modifiedFiles()).containsExactly("a.txt", "b.txt");
        assertThat(CompactionSupport.formatFileOperations(lists.readFiles(), lists.modifiedFiles()))
                .isEqualTo("\n\n<modified-files>\na.txt\nb.txt\n</modified-files>");
    }

    @Test
    void estimatesContextFromLastAssistantUsageAndTrailingMessages() {
        AgentMessage old = new AgentMessage.Llm(new Message.User(List.of(new Content.Text("ignored by usage")), NOW));
        AgentMessage assistant = new AgentMessage.Llm(new Message.Assistant(List.of(new Content.Text("ok")),
                "openai", "gpt", StopReason.STOP, new Usage(100, 20, 10, 5, 3), null, NOW));
        AgentMessage trailing = new AgentMessage.Llm(new Message.User(List.of(new Content.Text("12345678")), NOW));

        CompactionSupport.ContextUsageEstimate estimate = CompactionSupport.estimateContextTokens(List.of(old, assistant, trailing));

        assertThat(estimate.usageTokens()).isEqualTo(138);
        assertThat(estimate.trailingTokens()).isEqualTo(2);
        assertThat(estimate.tokens()).isEqualTo(140);
        assertThat(estimate.lastUsageIndex()).isEqualTo(1);
        assertThat(CompactionSupport.shouldCompact(140, 150, new CompactionSupport.Settings(true, 20, 10))).isTrue();
    }

    @Test
    void buildsContextPreservingAssistantUsageFromJson() {
        ObjectNode assistant = (ObjectNode) assistantText("tokened");
        ObjectNode usage = assistant.putObject("usage");
        usage.put("inputTokens", 100);
        usage.put("outputTokens", 20);
        usage.put("cacheCreationInputTokens", 10);
        usage.put("cacheReadInputTokens", 5);
        usage.put("reasoningTokens", 3);

        List<AgentMessage> context = CompactionSupport.buildSessionContext(List.of(
                message("u1", null, user("one")),
                message("a1", "u1", assistant)));

        assertThat(context).hasSize(2);
        assertThat(context.get(1)).isInstanceOfSatisfying(AgentMessage.Llm.class, llm -> {
            assertThat(llm.message()).isInstanceOfSatisfying(Message.Assistant.class, restored -> {
                assertThat(restored.usage()).isEqualTo(new Usage(100, 20, 10, 5, 3));
            });
        });
        assertThat(CompactionSupport.estimateContextTokens(context).usageTokens()).isEqualTo(138);
    }

    @Test
    void buildsContextFromLatestCompactionOnly() {
        SessionEntry.MessageEntry u1 = message("u1", null, user("one"));
        SessionEntry.MessageEntry a1 = message("a1", "u1", assistantText("alpha"));
        SessionEntry.CompactionEntry c1 = compaction("c1", "a1", "first", "u1");
        SessionEntry.MessageEntry u2 = message("u2", "c1", user("two"));
        SessionEntry.CompactionEntry c2 = compaction("c2", "u2", "second", "u2");
        SessionEntry.MessageEntry u3 = message("u3", "c2", user("three"));

        List<AgentMessage> context = CompactionSupport.buildSessionContext(List.of(u1, a1, c1, u2, c2, u3));
        List<Message> llm = CodingAgentMessages.convertToLlm(context);

        assertThat(llm).hasSize(3);
        assertThat(text(llm.get(0))).contains("second");
        assertThat(text(llm.get(1))).isEqualTo("two");
        assertThat(text(llm.get(2))).isEqualTo("three");
    }

    @Test
    void preparesCompactionWithPreviousSummaryAndSplitTurnPrefix() {
        SessionEntry.MessageEntry u1 = message("u1", null, user("old request"));
        SessionEntry.MessageEntry a1 = message("a1", "u1", assistantText("old answer"));
        ObjectNode details = JsonCodec.mapper().createObjectNode();
        details.putArray("readFiles").add("old-read.txt");
        details.putArray("modifiedFiles").add("old-edit.txt");
        SessionEntry.CompactionEntry previous = new SessionEntry.CompactionEntry("c1", "a1", NOW, "previous summary",
                "u2", 100, details, false);
        SessionEntry.MessageEntry u2 = message("u2", "c1", user("large turn"));
        SessionEntry.MessageEntry a2 = message("a2", "u2", assistantTool("read", "new-read.txt"));
        SessionEntry.MessageEntry a3 = message("a3", "a2", assistantText("recent suffix " + "x".repeat(80)));

        CompactionSupport.CompactionPreparation preparation = CompactionSupport.prepareCompaction(
                List.of(u1, a1, previous, u2, a2, a3), new CompactionSupport.Settings(true, 0, 5));

        assertThat(preparation).isNotNull();
        assertThat(preparation.previousSummary()).isEqualTo("previous summary");
        assertThat(preparation.splitTurn()).isTrue();
        assertThat(preparation.firstKeptEntryId()).isEqualTo("a3");
        assertThat(preparation.messagesToSummarize()).isEmpty();
        assertThat(preparation.turnPrefixMessages()).hasSize(2);
        CompactionSupport.FileLists lists = CompactionSupport.computeFileLists(preparation.fileOperations());
        assertThat(lists.readFiles()).containsExactly("new-read.txt", "old-read.txt");
        assertThat(lists.modifiedFiles()).containsExactly("old-edit.txt");
    }

    private static SessionEntry.MessageEntry message(String id, String parentId, JsonNode message) {
        return new SessionEntry.MessageEntry(id, parentId, NOW, message);
    }

    private static SessionEntry.CompactionEntry compaction(String id, String parentId, String summary, String firstKeptId) {
        return new SessionEntry.CompactionEntry(id, parentId, NOW, summary, firstKeptId, 100, null, false);
    }

    private static JsonNode user(String text) {
        return JsonCodec.parse("""
                {"role":"user","content":[{"type":"text","text":%s}],"timestamp":"2026-06-28T00:00:00Z"}
                """.formatted(JsonCodec.stringify(text)));
    }

    private static JsonNode assistantText(String text) {
        return JsonCodec.parse("""
                {"role":"assistant","content":[{"type":"text","text":%s}],"timestamp":"2026-06-28T00:00:00Z"}
                """.formatted(JsonCodec.stringify(text)));
    }

    private static JsonNode assistantTool(String name, String path) {
        return JsonCodec.parse("""
                {"role":"assistant","content":[{"type":"toolCall","id":"call","name":%s,"input":{"path":%s}}],"timestamp":"2026-06-28T00:00:00Z"}
                """.formatted(JsonCodec.stringify(name), JsonCodec.stringify(path)));
    }

    private static String text(Message message) {
        return message instanceof Message.User user && user.content().getFirst() instanceof Content.Text text ? text.text() : "";
    }
}
