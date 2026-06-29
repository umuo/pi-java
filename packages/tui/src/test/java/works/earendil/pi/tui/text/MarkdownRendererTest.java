package works.earendil.pi.tui.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownRendererTest {
    @Test
    void classifiesMarkdownAndHighlightsCodeFenceContent() {
        var lines = MarkdownRenderer.render("""
                # Title
                - item
                ```java
                public record User(String name) {
                  // comment
                  return "ok";
                }
                ```
                """);

        assertThat(lines).extracting(MarkdownRenderer.RenderedLine::kind)
                .containsExactly(
                        MarkdownRenderer.LineKind.HEADING,
                        MarkdownRenderer.LineKind.LIST_ITEM,
                        MarkdownRenderer.LineKind.CODE_FENCE,
                        MarkdownRenderer.LineKind.CODE,
                        MarkdownRenderer.LineKind.CODE,
                        MarkdownRenderer.LineKind.CODE,
                        MarkdownRenderer.LineKind.CODE,
                        MarkdownRenderer.LineKind.CODE_FENCE,
                        MarkdownRenderer.LineKind.TEXT);
        assertThat(lines.get(2).language()).isEqualTo("java");
        assertThat(lines.get(3).spans()).anyMatch(span -> span.style() == MarkdownRenderer.SpanStyle.CODE_KEYWORD);
        assertThat(lines.get(4).spans()).anyMatch(span -> span.style() == MarkdownRenderer.SpanStyle.CODE_COMMENT);
        assertThat(lines.get(5).spans()).anyMatch(span -> span.style() == MarkdownRenderer.SpanStyle.CODE_STRING);
    }

    @Test
    void keepsStreamingPartialClosingFenceAsCode() {
        var lines = MarkdownRenderer.render("""
                ```ts
                const x = 1;
                ``
                """);

        assertThat(lines).extracting(MarkdownRenderer.RenderedLine::kind)
                .containsExactly(
                        MarkdownRenderer.LineKind.CODE_FENCE,
                        MarkdownRenderer.LineKind.CODE,
                        MarkdownRenderer.LineKind.CODE,
                        MarkdownRenderer.LineKind.CODE);
    }
}
