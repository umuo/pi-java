package works.earendil.pi.tui.component;

import org.junit.jupiter.api.Test;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownTest {
    @Test
    void rendersMarkdownAsAnsiStyledTerminalLines() {
        Markdown markdown = new Markdown("""
                # Title
                ```java
                public record User(String name) {}
                ```
                """, 1, 0, null);

        var lines = markdown.renderLines(48);

        assertThat(lines).anySatisfy(line -> {
            assertThat(Ansi.strip(line)).contains("# Title");
            assertThat(line).contains("\u001B[");
        });
        assertThat(lines).anySatisfy(line -> {
            assertThat(Ansi.strip(line)).contains("public record User");
            assertThat(line).contains("\u001B[");
        });
        assertThat(lines).allSatisfy(line ->
                assertThat(EastAsianWidth.visibleWidth(Ansi.strip(line))).isEqualTo(48));
    }

    @Test
    void componentRenderWritesPlainTextToSurface() {
        Markdown markdown = new Markdown("""
                # 标题
                text
                """, 0, 0, null);
        Surface surface = new Surface(12, 2);

        markdown.render(surface);

        assertThat(surface.frame()).doesNotContain("\u001B[");
        assertThat(surface.frame()).contains("# 标题");
    }
}
