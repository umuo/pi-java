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

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CodingToolFactoryTest {
    private static final String TINY_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFBQIAX8jx0gAAAABJRU5ErkJggg==";

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
    void readToolReturnsSupportedImagesAsAttachments() throws Exception {
        Files.write(tempDir.resolve("tiny.png"), Base64.getDecoder().decode(TINY_PNG_BASE64));
        Map<String, AgentTool> tools = CodingToolFactory.createAllTools(tempDir);

        AgentTool.AgentToolResult read = tools.get("read").execute(Map.of("path", "tiny.png"));

        assertThat(read.content()).hasSize(2);
        assertThat(read.content().get(0)).isEqualTo(new Content.Text("Read image file [image/png]"));
        assertThat(read.content().get(1)).isEqualTo(new Content.Image("image/png", TINY_PNG_BASE64, null));
        assertThat(read.details()).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) read.details();
        assertThat(details).containsEntry("mimeType", "image/png")
                .containsEntry("image", true);
    }

    @Test
    void readToolResizesLargePngImagesWhenAutoResizeIsEnabled() throws Exception {
        Files.write(tempDir.resolve("large.png"), imageBytes("png", 2101, 100));
        AgentTool readTool = CodingToolFactory.read(tempDir);

        AgentTool.AgentToolResult read = readTool.execute(Map.of("path", "large.png"));

        assertThat(read.content()).hasSize(2);
        String text = ((Content.Text) read.content().getFirst()).text();
        assertThat(text).contains("Read image file [image/png]")
                .contains("[Image: original 2101x100, displayed at 2000x95.");
        Content.Image image = (Content.Image) read.content().get(1);
        BufferedImage decoded = decodeImage(image);
        assertThat(decoded.getWidth()).isEqualTo(2000);
        assertThat(decoded.getHeight()).isEqualTo(95);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) read.details();
        assertThat(details).containsEntry("originalWidth", 2101)
                .containsEntry("width", 2000)
                .containsEntry("autoResizeImages", true);
    }

    @Test
    void readToolKeepsLargePngImagesWhenAutoResizeIsDisabled() throws Exception {
        byte[] original = imageBytes("png", 2101, 100);
        Files.write(tempDir.resolve("large.png"), original);
        AgentTool readTool = CodingToolFactory.read(tempDir, false);

        AgentTool.AgentToolResult read = readTool.execute(Map.of("path", "large.png"));

        assertThat(read.content()).hasSize(2);
        assertThat(((Content.Text) read.content().getFirst()).text()).isEqualTo("Read image file [image/png]");
        Content.Image image = (Content.Image) read.content().get(1);
        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(image.data()).isEqualTo(Base64.getEncoder().encodeToString(original));
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) read.details();
        assertThat(details).containsEntry("autoResizeImages", false);
    }

    @Test
    void readToolConvertsBmpImagesToPngAttachments() throws Exception {
        Files.write(tempDir.resolve("pixel.bmp"), imageBytes("bmp", 2, 2));
        AgentTool readTool = CodingToolFactory.read(tempDir, false);

        AgentTool.AgentToolResult read = readTool.execute(Map.of("path", "pixel.bmp"));

        assertThat(read.content()).hasSize(2);
        String text = ((Content.Text) read.content().getFirst()).text();
        assertThat(text).contains("Read image file [image/png]")
                .contains("[Image converted from image/bmp to image/png.]");
        Content.Image image = (Content.Image) read.content().get(1);
        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(decodeImage(image).getWidth()).isEqualTo(2);
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) read.details();
        assertThat(details).containsEntry("originalMimeType", "image/bmp")
                .containsEntry("mimeType", "image/png");
    }

    @Test
    void bashToolAppliesConfiguredCommandPrefix() throws Exception {
        Map<String, AgentTool> tools = CodingToolFactory.createAllTools(tempDir,
                new CodingToolFactory.BashConfig("export PI_TOOL_PREFIX=tool-prefix", "/bin/sh"));

        AgentTool.AgentToolResult bash = tools.get("bash").execute(Map.of("command", "printf $PI_TOOL_PREFIX"));

        assertThat(((Content.Text) bash.content().getFirst()).text()).isEqualTo("tool-prefix");
    }

    @Test
    void bashToolExposesExplicitSessionEnvironmentAndRejectsUnsafeTimeouts() throws Exception {
        Map<String, AgentTool> tools = CodingToolFactory.createAllTools(tempDir,
                new CodingToolFactory.BashConfig(null, "/bin/sh", Map.of(
                        "PI_SESSION_ID", "session-123",
                        "PI_PROVIDER", "openai",
                        "PI_MODEL", "gpt-test",
                        "PI_REASONING_LEVEL", "max")));

        AgentTool.AgentToolResult bash = tools.get("bash").execute(Map.of(
                "command", "printf '%s|%s|%s|%s' \"$PI_SESSION_ID\" \"$PI_PROVIDER\" \"$PI_MODEL\" \"$PI_REASONING_LEVEL\""));

        assertThat(((Content.Text) bash.content().getFirst()).text())
                .isEqualTo("session-123|openai|gpt-test|max");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                tools.get("bash").execute(Map.of("command", "true", "timeout", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("finite positive number");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                tools.get("bash").execute(Map.of("command", "true", "timeout", 2_147_484)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maximum");
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

    private static byte[] imageBytes(String format, int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, ((x + y) % 2 == 0 ? Color.RED : Color.BLUE).getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, format, output)).isTrue();
        return output.toByteArray();
    }

    private static BufferedImage decodeImage(Content.Image image) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image.data())));
    }
}
