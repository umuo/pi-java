package works.earendil.pi.tui.component;

import org.junit.jupiter.api.Test;
import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;

import static org.assertj.core.api.Assertions.assertThat;

class DiffTest {
    @Test
    void rendersSplitDiffWithAnsiStyles() {
        Diff diff = new Diff("""
                --- a/File.java
                +++ b/File.java
                @@ -1 +1 @@
                -old value
                +new value
                 same
                """);

        var lines = diff.renderLines(42);

        assertThat(lines).anySatisfy(line -> {
            assertThat(Ansi.strip(line)).contains("old value").contains("new value").contains(" | ");
            assertThat(line).contains("\u001B[");
        });
        assertThat(lines).allSatisfy(line ->
                assertThat(EastAsianWidth.visibleWidth(Ansi.strip(line))).isEqualTo(42));
    }

    @Test
    void componentRenderWritesPlainTextToSurface() {
        Diff diff = new Diff("""
                -删除旧值
                +添加新值
                """);
        Surface surface = new Surface(20, 1);

        diff.render(surface);

        assertThat(surface.frame()).doesNotContain("\u001B[");
        assertThat(surface.frame()).contains("删除").contains("添加");
    }
}
