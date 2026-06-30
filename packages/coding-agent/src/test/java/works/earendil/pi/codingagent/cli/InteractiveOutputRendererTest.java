package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;

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
}
