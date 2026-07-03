package works.earendil.pi.codingagent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.agent.core.AgentContext;
import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentLoop;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CodingToolFactoryTest {
    @TempDir
    Path tempDir;

    @Test
    void createsReadOnlyToolsUsableByAgentLoop() throws Exception {
        Files.writeString(tempDir.resolve("alpha.txt"), "hello alpha");
        Files.writeString(tempDir.resolve("beta.md"), "hello beta");
        List<AgentTool> tools = CodingToolFactory.createReadOnlyTools(tempDir);
        Model model = new Model("test", "model", "Test", "test", 1000, 1000, true, false, Map.of());
        AtomicInteger calls = new AtomicInteger();
        List<AgentEvent> events = new ArrayList<>();

        List<AgentMessage> messages = AgentLoop.run(
                List.of(new AgentMessage.Llm(new Message.User(List.of(new Content.Text("inspect files")), Instant.now()))),
                new AgentContext(List.of(), "system", tools),
                new AgentLoop.Config(model, StreamOptions.defaults(), List::of, List::of, AgentLoop.ToolExecutionMode.SEQUENTIAL),
                (m, c, o) -> switch (calls.getAndIncrement()) {
                    case 0 -> assistantTool("call-read", "read", "{\"path\":\"alpha.txt\"}");
                    case 1 -> assistantTool("call-find", "find", "{\"pattern\":\"*.md\"}");
                    default -> new Message.Assistant(List.of(new Content.Text("done")),
                            "test", "model", StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now());
                },
                events::add);

        String toolOutput = messages.stream()
                .filter(AgentMessage.Llm.class::isInstance)
                .map(AgentMessage.Llm.class::cast)
                .map(AgentMessage.Llm::message)
                .filter(Message.ToolResult.class::isInstance)
                .map(Message.ToolResult.class::cast)
                .flatMap(result -> result.content().stream())
                .filter(Content.Text.class::isInstance)
                .map(Content.Text.class::cast)
                .map(Content.Text::text)
                .reduce("", (a, b) -> a + "\n" + b);

        assertThat(toolOutput).contains("hello alpha");
        assertThat(toolOutput).contains("beta.md");
        assertThat(events.stream().map(AgentEvent::type)).contains("tool_execution_start", "tool_execution_end");
    }

    @Test
    void createsWriteEditAndBashTools() throws Exception {
        Map<String, AgentTool> tools = CodingToolFactory.createAllTools(tempDir);

        tools.get("write").execute(Map.of("path", "note.txt", "content", "old", "overwrite", true));
        AgentTool.AgentToolResult edit = tools.get("edit").execute(Map.of("path", "note.txt", "oldText", "old", "newText", "new"));
        AgentTool.AgentToolResult bash = tools.get("bash").execute(Map.of("command", "cat note.txt"));

        assertThat(Files.readString(tempDir.resolve("note.txt"))).isEqualTo("new");
        assertThat(((Content.Text) bash.content().getFirst()).text()).isEqualTo("new");
        assertThat(edit.details()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) edit.details();
        assertThat(details).containsEntry("replacements", 1);
        assertThat((String) details.get("diff")).contains("--- ").contains("+++ ").contains("-old").contains("+new");
    }

    @Test
    void bashToolAppliesConfiguredCommandPrefix() throws Exception {
        Map<String, AgentTool> tools = CodingToolFactory.createAllTools(tempDir,
                new CodingToolFactory.BashConfig("export PI_TOOL_PREFIX=tool-prefix", "/bin/sh"));

        AgentTool.AgentToolResult bash = tools.get("bash").execute(Map.of("command", "printf $PI_TOOL_PREFIX"));

        assertThat(((Content.Text) bash.content().getFirst()).text()).isEqualTo("tool-prefix");
    }

    @Test
    void editToolSupportsMultipleEditsArrayAndStringPayload() throws Exception {
        Map<String, AgentTool> tools = CodingToolFactory.createAllTools(tempDir);
        Files.writeString(tempDir.resolve("multi.txt"), """
                alpha
                beta
                gamma
                delta
                """);

        AgentTool.AgentToolResult arrayEdit = tools.get("edit").execute(Map.of(
                "path", "multi.txt",
                "edits", List.of(
                        Map.of("oldText", "alpha", "newText", "one"),
                        Map.of("oldText", "gamma", "newText", "three"))));
        AgentTool.AgentToolResult stringEdit = tools.get("edit").execute(Map.of(
                "path", "multi.txt",
                "edits", """
                        [{"oldText":"beta","newText":"two"}]
                        """));

        assertThat(Files.readString(tempDir.resolve("multi.txt"))).isEqualTo("""
                one
                two
                three
                delta
                """);
        @SuppressWarnings("unchecked")
        Map<String, Object> arrayDetails = (Map<String, Object>) arrayEdit.details();
        @SuppressWarnings("unchecked")
        Map<String, Object> stringDetails = (Map<String, Object>) stringEdit.details();
        assertThat(arrayDetails).containsEntry("replacements", 2);
        assertThat(stringDetails).containsEntry("replacements", 1);
    }

    @Test
    void writeToolReturnsDiffDetailsForCreateAndOverwrite() throws Exception {
        Map<String, AgentTool> tools = CodingToolFactory.createAllTools(tempDir);

        AgentTool.AgentToolResult create = tools.get("write").execute(Map.of(
                "path", "notes/todo.txt",
                "content", "first\n",
                "overwrite", true));
        AgentTool.AgentToolResult overwrite = tools.get("write").execute(Map.of(
                "path", "notes/todo.txt",
                "content", "second\n",
                "overwrite", true));

        assertThat(Files.readString(tempDir.resolve("notes/todo.txt"))).isEqualTo("second\n");
        @SuppressWarnings("unchecked")
        Map<String, Object> createDetails = (Map<String, Object>) create.details();
        @SuppressWarnings("unchecked")
        Map<String, Object> overwriteDetails = (Map<String, Object>) overwrite.details();
        assertThat(createDetails).containsEntry("created", true)
                .containsEntry("bytes", 6);
        assertThat((String) createDetails.get("diff")).contains("+++ ").contains("+first");
        assertThat(overwriteDetails).containsEntry("created", false)
                .containsEntry("bytes", 7);
        assertThat((String) overwriteDetails.get("diff")).contains("-first").contains("+second");
    }

    private static Message.Assistant assistantTool(String id, String name, String input) {
        JsonNode node = JsonCodec.parse(input);
        return new Message.Assistant(List.of(new Content.ToolCall(id, name, node, List.of())),
                "test", "model", StopReason.TOOL_USE, new Usage(1, 1, 0, 0, 0), null, Instant.now());
    }
}
