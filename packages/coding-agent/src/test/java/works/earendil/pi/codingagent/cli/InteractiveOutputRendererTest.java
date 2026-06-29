package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class InteractiveOutputRendererTest {
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
