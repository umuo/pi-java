package works.earendil.pi.tui.component;

import works.earendil.pi.common.text.EastAsianWidth;

public final class FooterStatusBar implements Component {
    private final String branch;
    private final String model;
    private final int messageCount;
    private final long promptTokens;
    private final long completionTokens;
    private final double cost;
    private final long turnMillis;

    public FooterStatusBar(String branch, String model, int messageCount, long promptTokens, long completionTokens, double cost, long turnMillis) {
        this.branch = branch != null ? branch : "main";
        this.model = model != null ? model : "unknown";
        this.messageCount = Math.max(0, messageCount);
        this.promptTokens = Math.max(0, promptTokens);
        this.completionTokens = Math.max(0, completionTokens);
        this.cost = Math.max(0.0, cost);
        this.turnMillis = Math.max(0, turnMillis);
    }

    @Override
    public void render(RenderContext context) {
        int width = context.width();
        if (width <= 0) {
            return;
        }

        String left = String.format(" [git:%s] %s | Msgs:%d ", branch, model, messageCount);
        String right = String.format(" In:%d Out:%d ($%.4f) | %dms ", promptTokens, completionTokens, cost, turnMillis);

        int leftLen = EastAsianWidth.visibleWidth(left);
        int rightLen = EastAsianWidth.visibleWidth(right);

        String line;
        if (leftLen + rightLen <= width) {
            int spaces = width - leftLen - rightLen;
            line = left + " ".repeat(spaces) + right;
        } else if (leftLen <= width) {
            line = EastAsianWidth.truncateToWidth(left, width);
        } else {
            line = EastAsianWidth.truncateToWidth(left, width);
        }

        context.write(0, 0, line);
    }
}
