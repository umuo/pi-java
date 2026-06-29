package works.earendil.pi.tui.component;

import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.tui.style.TerminalTheme;
import works.earendil.pi.tui.text.DiffSplitRenderer;
import works.earendil.pi.tui.text.DiffSplitRenderer.SplitLine;

import java.util.ArrayList;
import java.util.List;

public final class Diff implements Component {
    private String text;
    private final TerminalTheme theme;

    private String cachedText;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public Diff(String text) {
        this(text, TerminalTheme.standard());
    }

    public Diff(String text, TerminalTheme theme) {
        this.text = text == null ? "" : text;
        this.theme = theme == null ? TerminalTheme.standard() : theme;
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
        invalidate();
    }

    public void invalidate() {
        cachedText = null;
        cachedWidth = -1;
        cachedLines = null;
    }

    public List<String> renderLines(int width) {
        int safeWidth = Math.max(1, width);
        if (cachedLines != null && cachedText.equals(text) && cachedWidth == safeWidth) {
            return cachedLines;
        }

        int columnWidth = Math.max(1, (safeWidth - 3) / 2);
        List<String> lines = new ArrayList<>();
        for (SplitLine line : DiffSplitRenderer.render(text.replace("\t", "   "), safeWidth)) {
            String left = padColumn(theme.styleLeft(line.type(), line.left()), columnWidth);
            String right = padColumn(theme.styleRight(line.type(), line.right()), columnWidth);
            lines.add(padToWidth(left + theme.separator(" | ") + right, safeWidth));
        }

        cachedText = text;
        cachedWidth = safeWidth;
        cachedLines = List.copyOf(lines);
        return cachedLines;
    }

    @Override
    public void render(RenderContext context) {
        List<String> lines = renderLines(context.width());
        for (int y = 0; y < lines.size() && y < context.height(); y++) {
            context.write(0, y, Ansi.strip(lines.get(y)));
        }
    }

    private static String padColumn(String text, int width) {
        int visible = EastAsianWidth.visibleWidth(Ansi.strip(text));
        if (visible >= width) {
            return text;
        }
        return text + " ".repeat(width - visible);
    }

    private static String padToWidth(String text, int width) {
        int visible = EastAsianWidth.visibleWidth(Ansi.strip(text));
        if (visible >= width) {
            return text;
        }
        return text + " ".repeat(width - visible);
    }
}
