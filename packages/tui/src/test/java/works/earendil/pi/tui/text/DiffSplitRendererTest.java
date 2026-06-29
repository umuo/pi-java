package works.earendil.pi.tui.text;

import org.junit.jupiter.api.Test;
import works.earendil.pi.common.text.EastAsianWidth;

import static org.assertj.core.api.Assertions.assertThat;

class DiffSplitRendererTest {
    @Test
    void pairsRemovedAndAddedLinesForSplitView() {
        var lines = DiffSplitRenderer.render("""
                --- a/File.java
                +++ b/File.java
                @@ -1,2 +1,2 @@
                -old value
                +new value
                 unchanged
                +extra
                """, 40);

        assertThat(lines).extracting(DiffSplitRenderer.SplitLine::type)
                .contains(
                        DiffSplitRenderer.LineType.FILE_HEADER,
                        DiffSplitRenderer.LineType.HUNK,
                        DiffSplitRenderer.LineType.CHANGED,
                        DiffSplitRenderer.LineType.CONTEXT,
                        DiffSplitRenderer.LineType.ADDED);
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.type()).isEqualTo(DiffSplitRenderer.LineType.CHANGED);
            assertThat(line.left()).isEqualTo("old value");
            assertThat(line.right()).isEqualTo("new value");
        });
        assertThat(lines).anySatisfy(line -> {
            assertThat(line.type()).isEqualTo(DiffSplitRenderer.LineType.ADDED);
            assertThat(line.left()).isEmpty();
            assertThat(line.right()).isEqualTo("extra");
        });
    }

    @Test
    void truncatesColumnsUsingVisibleWidth() {
        var lines = DiffSplitRenderer.render("""
                -删除一个很长的旧值
                +添加一个很长的新值
                """, 15);

        DiffSplitRenderer.SplitLine changed = lines.getFirst();
        assertThat(changed.type()).isEqualTo(DiffSplitRenderer.LineType.CHANGED);
        assertThat(EastAsianWidth.visibleWidth(changed.left())).isLessThanOrEqualTo(6);
        assertThat(EastAsianWidth.visibleWidth(changed.right())).isLessThanOrEqualTo(6);
    }
}
