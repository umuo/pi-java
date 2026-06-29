package works.earendil.pi.tui.component;

import works.earendil.pi.common.text.Ansi;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.tui.style.TerminalTheme;
import works.earendil.pi.tui.text.MarkdownRenderer;
import works.earendil.pi.tui.text.MarkdownRenderer.RenderedLine;
import works.earendil.pi.tui.text.MarkdownRenderer.Span;

import java.util.ArrayList;
import java.util.List;

public final class Markdown implements Component {
    private String text;
    private final int paddingX;
    private final int paddingY;
    private final TerminalTheme theme;

    private String cachedText;
    private int cachedWidth = -1;
    private List<String> cachedLines;

    public Markdown(String text) {
        this(text, 1, 0, TerminalTheme.standard());
    }

    public Markdown(String text, int paddingX, int paddingY, TerminalTheme theme) {
        this.text = text == null ? "" : text;
        this.paddingX = Math.max(0, paddingX);
        this.paddingY = Math.max(0, paddingY);
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

        List<String> lines = new ArrayList<>();
        String blank = " ".repeat(safeWidth);
        for (int i = 0; i < paddingY; i++) {
            lines.add(blank);
        }

        int contentWidth = Math.max(1, safeWidth - paddingX * 2);
        String margin = " ".repeat(paddingX);
        String normalized = text.replace("\t", "   ");
        for (RenderedLine line : MarkdownRenderer.render(normalized)) {
            String rendered = renderLine(line, contentWidth);
            lines.add(padToWidth(margin + rendered + margin, safeWidth));
        }

        for (int i = 0; i < paddingY; i++) {
            lines.add(blank);
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

    private String renderLine(RenderedLine line, int width) {
        String text = EastAsianWidth.truncateToWidth(line.text(), width);
        return switch (line.kind()) {
            case CODE -> renderCodeLine(text, line.spans());
            case CODE_FENCE -> theme.style(MarkdownRenderer.LineKind.CODE_FENCE, text);
            case HEADING, LIST_ITEM, BLOCK_QUOTE, HORIZONTAL_RULE, TEXT -> theme.style(line.kind(), text);
        };
    }

    private String renderCodeLine(String text, List<Span> spans) {
        if (text.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder();
        for (Span span : spans) {
            if (span.start() >= text.length()) {
                continue;
            }
            int end = Math.min(span.end(), text.length());
            if (span.start() < end) {
                out.append(theme.style(span.style(), text.substring(span.start(), end)));
            }
        }
        return out.toString();
    }

    private static String padToWidth(String text, int width) {
        int visible = EastAsianWidth.visibleWidth(Ansi.strip(text));
        if (visible >= width) {
            return text;
        }
        return text + " ".repeat(width - visible);
    }
}
