package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.server.service.ServerLogTailer;
import works.earendil.pi.server.service.ServerSupervisor;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.resources.ResourceLoader;
import works.earendil.pi.codingagent.resources.ThemeResource;
import works.earendil.pi.tui.style.TerminalTheme;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveOutputRendererTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersAssistantMarkdownWithAnsiStyles() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderAssistantText(out, """
                    # Heading
                    ```java
                    public record User(String name) {}
                    ```
                    """, 50);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(rendered).contains("# Heading").contains("\u001B[");
        assertThat(Ansi.strip(rendered)).contains("public record User");
        for (String line : rendered.split("\\R")) {
            if (!line.isEmpty()) {
                assertThat(EastAsianWidth.visibleWidth(Ansi.strip(line))).isEqualTo(50);
            }
        }
    }

    @Test
    void rendersAssistantMarkdownWithConfiguredTheme() throws Exception {
        ThemeResource themeResource = new ThemeResource("custom", JsonCodec.parse("""
                {
                  "name": "custom",
                  "vars": {
                    "heading": "#ff0000",
                    "added": "#0000ff"
                  },
                  "colors": {
                    "mdHeading": "heading",
                    "toolDiffAdded": "added",
                    "syntaxKeyword": 196
                  }
                }
                """), null, tempDir.resolve("custom.json"));
        TerminalTheme theme = TerminalThemeResolver.fromThemeResource(themeResource).orElseThrow();
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderAssistantText(out, """
                    # Heading
                    ```java
                    public class A {}
                    ```
                    """, 40, theme);
            InteractiveOutputRenderer.renderToolResult(out, new Message.ToolResult("call-1", "edit",
                    List.of(new Content.Text("""
                            @@ -1 +1 @@
                            -old
                            +new
                            """)), false, Map.of(), Instant.now()), 40, theme);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(rendered)
                .contains("\u001B[38;2;255;0;0m")
                .contains("\u001B[38;2;0;0;255m")
                .contains("\u001B[38;5;196m");
        assertThat(Ansi.strip(rendered)).contains("# Heading").contains("public class A").contains("old").contains("new");
    }

    @Test
    void resolvesConfiguredThemeFromLoadedResources() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path project = tempDir.resolve("project");
        Path themes = tempDir.resolve("themes");
        Files.createDirectories(themes);
        Files.writeString(themes.resolve("custom.json"), """
                {
                  "name": "custom",
                  "colors": {
                    "mdHeading": "#ff0000"
                  }
                }
                """);
        ResourceLoader loader = new ResourceLoader(project, agentDir, true, List.of(), List.of(),
                List.of(themes), true, false, null, null);
        loader.reload();
        TerminalTheme theme = TerminalThemeResolver.resolve(SettingsManager.inMemory(Map.of("theme", "custom")),
                loader);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderAssistantText(out, "# Heading", 40, theme);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains("\u001B[1m\u001B[38;2;255;0;0m");
    }

    @Test
    void rendersEditToolStartWithPreviewDiff() throws Exception {
        Files.writeString(tempDir.resolve("File.java"), """
                class File {
                    String value = "old";
                }
                """);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolStart(out, "edit", JsonCodec.parse("""
                    {
                      "path": "File.java",
                      "edits": [
                        { "oldText": "String value = \\"old\\";", "newText": "String value = \\"new\\";" }
                      ]
                    }
                    """), tempDir, 52);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        String plain = Ansi.strip(rendered);
        assertThat(plain).contains("Tool started")
                .contains("edit")
                .contains("path: `File.java`")
                .contains("edits: 1")
                .contains("String value = \"old")
                .contains("String value = \"new");
        assertThat(rendered).contains(" | ").contains("\u001B[");
    }

    @Test
    void rendersEditToolStartPreviewError() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolStart(out, "edit", Map.of(
                    "path", "missing.txt",
                    "oldText", "before",
                    "newText", "after"), tempDir, 60);
        }

        String plain = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(plain).contains("Tool started")
                .contains("edit")
                .contains("edit preview unavailable:")
                .contains("missing.txt");
    }

    @Test
    void keepsToolStartRenderingNonBlockingWhenArgsPreviewFails() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolStart(out, "edit", Map.of(
                    "path", "File.java",
                    "edits", "not-json"), tempDir, 80);
        }

        String plain = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(plain).contains("Tool started")
                .contains("args preview unavailable:")
                .contains("edit preview unavailable:");
    }

    @Test
    void rendersWriteToolStartWithPreviewDiff() throws Exception {
        Files.writeString(tempDir.resolve("notes.txt"), "old\n");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolStart(out, "write", Map.of(
                    "path", "notes.txt",
                    "content", "new\n",
                    "overwrite", true), tempDir, 44);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        String plain = Ansi.strip(rendered);
        assertThat(plain).contains("Tool started")
                .contains("write")
                .contains("path: `notes.txt`")
                .contains("content chars: 4")
                .contains("old")
                .contains("new");
        assertThat(rendered).contains(" | ").contains("\u001B[");
    }

    @Test
    void rendersWriteToolStartPreviewErrorForOverwriteFalse() throws Exception {
        Files.writeString(tempDir.resolve("exists.txt"), "old\n");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolStart(out, "write", Map.of(
                    "path", "exists.txt",
                    "content", "new\n",
                    "overwrite", false), tempDir, 70);
        }

        String plain = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(plain).contains("Tool started")
                .contains("write preview unavailable:")
                .contains("File already exists: exists.txt");
    }

    @Test
    void rendersUnifiedDiffToolResultAsSplitView() throws Exception {
        String unifiedDiff = """
                        --- a/File.java
                        +++ b/File.java
                        @@ -1 +1 @@
                        -old value
                        +new value
                        """;
        Message.ToolResult result = new Message.ToolResult("call-1", "edit",
                List.of(new Content.Text(unifiedDiff)),
                false, Map.of("path", "File.java"), Instant.now());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolResult(out, result, 44);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(rendered).contains("Tool finished: edit")
                .contains("old value")
                .contains("new value")
                .contains(" | ")
                .contains("\u001B[");
        assertThat(InteractiveOutputRenderer.looksLikeUnifiedDiff(unifiedDiff)).isTrue();
    }

    @Test
    void prefersDiffFromToolDetailsOverSuccessText() throws Exception {
        String unifiedDiff = """
                --- a/File.java
                +++ b/File.java
                @@ -1 +1 @@
                -old value
                +new value
                """;
        Message.ToolResult result = new Message.ToolResult("call-1", "edit",
                List.of(new Content.Text("Successfully replaced text in File.java")),
                false, Map.of("diff", unifiedDiff, "path", "File.java"), Instant.now());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolResult(out, result, 44);
        }

        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(rendered).contains("Tool finished: edit")
                .contains("old value")
                .contains("new value")
                .doesNotContain("Successfully replaced text in File.java");
    }

    @Test
    void collapsesLongToolOutputPreview() throws Exception {
        StringBuilder longOutput = new StringBuilder();
        for (int i = 1; i <= 25; i++) {
            longOutput.append("line ").append(i).append('\n');
        }
        Message.ToolResult result = new Message.ToolResult("call-1", "bash",
                List.of(new Content.Text(longOutput.toString())),
                false, Map.of(), Instant.now());
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderToolResult(out, result, 60);
        }

        String rendered = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(rendered.lines().map(String::strip).toList()).doesNotContain("line 1");
        assertThat(rendered).contains("line 25");
        assertThat(rendered).contains("... 5 earlier output lines hidden in collapsed preview");
    }

    @Test
    void rendersServerEventPanelWithinTerminalWidth() throws Exception {
        ServerSupervisor.RpcEvent event = new ServerSupervisor.RpcEvent(
                7,
                "agent-1",
                "99",
                "{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\"}}",
                "2026-07-01T12:00:00Z");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderServerEvent(out, event, 88);
        }

        String rendered = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(rendered)
                .contains("Server event")
                .contains("seq: 7")
                .contains("instance: agent-1")
                .contains("request: 99")
                .contains("\"method\":\"event\"")
                .contains("\"content_delta\"");
        for (String line : rendered.split("\\R")) {
            assertThat(EastAsianWidth.visibleWidth(line)).isLessThanOrEqualTo(88);
        }
    }

    @Test
    void rendersServerLogPanelWithinTerminalWidth() throws Exception {
        ServerLogTailer.LogLine line = new ServerLogTailer.LogLine(
                "agent-1",
                Path.of("logs/agent-1.stderr.log"),
                19,
                "new stderr line",
                "2026-07-01T12:00:00Z");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderServerLogLine(out, line, 72);
        }

        String rendered = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(rendered)
                .contains("Server stderr")
                .contains("instance: agent-1")
                .contains("agent-1.stderr.log")
                .contains("line: new stderr line");
        for (String outputLine : rendered.split("\\R")) {
            assertThat(EastAsianWidth.visibleWidth(outputLine)).isLessThanOrEqualTo(72);
        }
    }

    @Test
    void rendersSkillTriggerDiagnosticPanelWithinTerminalWidth() throws Exception {
        AgentSession.AgentSessionEvent.SkillTriggerDiagnostic diagnostic =
                new AgentSession.AgentSessionEvent.SkillTriggerDiagnostic(List.of(
                        new SkillLoader.SkillTriggerMatch("diagnose",
                                Path.of("/workspace/.agents/skills/diagnose/SKILL.md"),
                                true,
                                List.of("term:flaky", "glob:**/*Test.java")),
                        new SkillLoader.SkillTriggerMatch("manual-audit",
                                Path.of("/workspace/.agents/skills/manual-audit/SKILL.md"),
                                false,
                                List.of("pattern:security.*review"))));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderSkillTriggerDiagnostic(out, diagnostic, 86);
        }

        String rendered = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(rendered)
                .contains("Skill trigger diagnostic")
                .contains("skill: diagnose")
                .contains("model: visible")
                .contains("term:flaky")
                .contains("glob:**/*Test.java")
                .contains("skill: manual-audit")
                .contains("model: manual")
                .contains("security.*review")
                .contains("SKILL.md");
        for (String outputLine : rendered.split("\\R")) {
            assertThat(EastAsianWidth.visibleWidth(outputLine)).isLessThanOrEqualTo(86);
        }
    }

    @Test
    void rendersQueueUpdatePanelWithImageMetadata() throws Exception {
        AgentSession.AgentSessionEvent.QueueUpdate update = new AgentSession.AgentSessionEvent.QueueUpdate(
                List.of("steer text"),
                List.of("follow text"),
                List.of(new AgentSession.AgentSessionEvent.QueueUpdate.QueueItem("steer text",
                        "extension",
                        List.of(new AgentSession.AgentSessionEvent.QueueUpdate.QueueImage(
                                "image/png", "data", 5, null)))),
                List.of(new AgentSession.AgentSessionEvent.QueueUpdate.QueueItem("follow text",
                        "extension",
                        List.of(
                                new AgentSession.AgentSessionEvent.QueueUpdate.QueueImage(
                                        "image/png", "data", 9, null),
                                new AgentSession.AgentSessionEvent.QueueUpdate.QueueImage(
                                        "image/jpeg", "url", 0, "https://example.test/follow-up.jpg")))));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderQueueUpdate(out, update, 120);
        }

        String rendered = Ansi.strip(output.toString(StandardCharsets.UTF_8));
        assertThat(rendered)
                .contains("Queued user messages")
                .contains("steer[1] source=extension: steer text")
                .contains("images: image/png data 5 bytes")
                .contains("follow-up[1] source=extension: follow text")
                .contains("image/png data 9 bytes")
                .contains("image/jpeg url https://example.test/follow-up.jpg");
        for (String outputLine : rendered.split("\\R")) {
            assertThat(EastAsianWidth.visibleWidth(outputLine)).isLessThanOrEqualTo(120);
        }
    }

    @Test
    void skipsEmptyQueueUpdatePanel() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderQueueUpdate(out,
                    new AgentSession.AgentSessionEvent.QueueUpdate(List.of(), List.of()), 80);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void rendersSplitDiffHelper() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderSplitDiff(out, "Old", "New", "line1", "line1\nline2", 60, 5);
        }
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(rendered).contains("Old").contains("New").contains("line1").contains("line2");
    }

    @Test
    void rendersCollapsibleToolOutputHelper() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderCollapsibleToolOutput(out, "read", "call_1", "line1\nline2", true, 5, 60);
        }
        String rendered = output.toString(StandardCharsets.UTF_8);
        assertThat(rendered).contains("[+] Tool: read (call_1)").contains("line1");
    }
}
