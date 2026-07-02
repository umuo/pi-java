package works.earendil.pi.tui.component;

import java.util.Arrays;
import java.util.List;

public final class SplitDiffPanel implements Component {
    private final String leftTitle;
    private final String rightTitle;
    private final List<String> leftLines;
    private final List<String> rightLines;
    private int scrollY;

    public SplitDiffPanel(String leftTitle, String rightTitle, String leftContent, String rightContent) {
        this.leftTitle = leftTitle != null ? leftTitle : "Before";
        this.rightTitle = rightTitle != null ? rightTitle : "After";
        this.leftLines = Arrays.asList((leftContent != null ? leftContent : "").split("\r?\n", -1));
        this.rightLines = Arrays.asList((rightContent != null ? rightContent : "").split("\r?\n", -1));
        this.scrollY = 0;
    }

    public void scrollDown(int lines) {
        int maxLines = Math.max(leftLines.size(), rightLines.size());
        this.scrollY = Math.min(Math.max(0, maxLines - 1), scrollY + lines);
    }

    public void scrollUp(int lines) {
        this.scrollY = Math.max(0, scrollY - lines);
    }

    public void setScrollY(int scrollY) {
        this.scrollY = Math.max(0, scrollY);
    }

    public int getScrollY() {
        return scrollY;
    }

    @Override
    public void render(RenderContext context) {
        int width = context.width();
        int height = context.height();
        if (width <= 0 || height <= 0) {
            return;
        }
        int halfWidth = width / 2;
        int rightStart = halfWidth + (width % 2);

        String lHeader = String.format("%-" + halfWidth + "s", leftTitle);
        String rHeader = String.format("%-" + (width - rightStart) + "s", rightTitle);
        context.write(0, 0, lHeader.substring(0, Math.min(lHeader.length(), halfWidth)));
        if (rightStart < width) {
            context.write(rightStart, 0, rHeader.substring(0, Math.min(rHeader.length(), width - rightStart)));
        }

        for (int y = 1; y < height; y++) {
            int lineIdx = scrollY + (y - 1);
            String lLine = lineIdx < leftLines.size() ? String.format("%4d | %s", lineIdx + 1, leftLines.get(lineIdx)) : "";
            String rLine = lineIdx < rightLines.size() ? String.format("%4d | %s", lineIdx + 1, rightLines.get(lineIdx)) : "";

            context.write(0, y, lLine);
            if (rightStart < width) {
                context.write(rightStart, y, rLine);
            }
        }
    }
}
