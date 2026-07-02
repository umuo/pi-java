package works.earendil.pi.tui.component;

import java.util.Arrays;
import java.util.List;

public final class CollapsibleToolPanel implements Component {
    private final String toolName;
    private final String toolCallId;
    private final List<String> outputLines;
    private boolean collapsed;
    private final int maxCollapsedLines;

    public CollapsibleToolPanel(String toolName, String toolCallId, String rawOutput, boolean collapsed, int maxCollapsedLines) {
        this.toolName = toolName != null ? toolName : "tool";
        this.toolCallId = toolCallId != null ? toolCallId : "";
        String output = rawOutput != null ? rawOutput : "";
        this.outputLines = Arrays.asList(output.split("\r?\n", -1));
        this.collapsed = collapsed;
        this.maxCollapsedLines = Math.max(1, maxCollapsedLines);
    }

    public void toggleCollapsed() {
        this.collapsed = !this.collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    @Override
    public void render(RenderContext context) {
        int width = context.width();
        int height = context.height();
        if (height <= 0 || width <= 0) {
            return;
        }

        String statusIcon = collapsed ? "[+]" : "[-]";
        String header = String.format("%s Tool: %s (%s) - %d lines", statusIcon, toolName, toolCallId, outputLines.size());
        context.write(0, 0, header);

        if (height <= 1) {
            return;
        }

        int visibleLines = Math.min(outputLines.size(), height - 1);
        if (collapsed && visibleLines > maxCollapsedLines) {
            visibleLines = maxCollapsedLines;
        }

        for (int i = 0; i < visibleLines; i++) {
            context.write(2, i + 1, outputLines.get(i));
        }

        if (collapsed && outputLines.size() > maxCollapsedLines && visibleLines + 1 < height) {
            context.write(2, visibleLines + 1, String.format("... (%d more lines hidden, press toggle to expand) ...", outputLines.size() - maxCollapsedLines));
        }
    }
}
