package works.earendil.pi.tui.component;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class AdvancedTuiComponentsTest {

    @Test
    void testCollapsibleToolPanel() {
        CollapsibleToolPanel panel = new CollapsibleToolPanel("edit", "call_1", "line1\nline2\nline3\nline4", true, 2);
        assertThat(panel.isCollapsed()).isTrue();

        Surface surf = new Surface(40, 5);
        panel.render(surf);
        String frame = surf.frame();
        assertThat(frame).contains("[+] Tool: edit (call_1)");
        assertThat(frame).contains("line1");
        assertThat(frame).contains("line2");
        assertThat(frame).contains("more lines hidden");

        panel.toggleCollapsed();
        assertThat(panel.isCollapsed()).isFalse();
    }

    @Test
    void testSplitDiffPanel() {
        SplitDiffPanel diff = new SplitDiffPanel("Before", "After", "old1\nold2", "new1\nnew2\nnew3");
        Surface surf = new Surface(40, 4);
        diff.render(surf);
        String frame = surf.frame();
        assertThat(frame).contains("Before");
        assertThat(frame).contains("After");
        assertThat(frame).contains("old1");
        assertThat(frame).contains("new1");

        diff.scrollDown(1);
        assertThat(diff.getScrollY()).isEqualTo(1);
    }

    @Test
    void testFooterStatusBar() {
        FooterStatusBar footer = new FooterStatusBar("feature/tui", "gemini-2.5-pro", 15, 1200, 450, 0.0125, 350);
        Surface surf = new Surface(80, 1);
        footer.render(surf);
        String frame = surf.frame();
        assertThat(frame).contains("[git:feature/tui]");
        assertThat(frame).contains("gemini-2.5-pro");
        assertThat(frame).contains("In:1200 Out:450");
    }

    @Test
    void testFullScreenPanelManager() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        FullScreenPanelManager manager = new FullScreenPanelManager(ps, 50, 10);

        manager.enterFullScreen();
        assertThat(manager.isActive()).isTrue();
        assertThat(baos.toString()).contains("\u001b[?1049h");

        manager.setHeader(new FooterStatusBar("main", "model", 1, 10, 5, 0.0, 10));
        manager.setBody(new CollapsibleToolPanel("test", "id", "out", false, 5));
        manager.render();

        assertThat(baos.toString()).contains("Tool: test");
        manager.exitFullScreen();
        assertThat(manager.isActive()).isFalse();
    }
}
