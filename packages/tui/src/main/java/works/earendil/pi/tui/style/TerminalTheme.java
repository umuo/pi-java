package works.earendil.pi.tui.style;

import org.fusesource.jansi.Ansi;
import works.earendil.pi.tui.text.DiffSplitRenderer;
import works.earendil.pi.tui.text.MarkdownRenderer;

import java.util.EnumMap;
import java.util.Map;

public final class TerminalTheme {
    private static final TerminalTheme DEFAULT = new TerminalTheme();

    private final Map<MarkdownRenderer.LineKind, Style> markdownLines;
    private final Map<MarkdownRenderer.SpanStyle, Style> markdownSpans;
    private final Map<DiffSplitRenderer.LineType, DiffStyle> diffLines;
    private final Style separatorStyle;

    private TerminalTheme() {
        this.markdownLines = markdownLineStyles();
        this.markdownSpans = markdownSpanStyles();
        this.diffLines = diffLineStyles();
        this.separatorStyle = Style.of(Ansi.Color.BLACK, false, true);
    }

    public static TerminalTheme standard() {
        return DEFAULT;
    }

    public String style(MarkdownRenderer.LineKind kind, String text) {
        return markdownLines.getOrDefault(kind, Style.PLAIN).apply(text);
    }

    public String style(MarkdownRenderer.SpanStyle style, String text) {
        return markdownSpans.getOrDefault(style, Style.PLAIN).apply(text);
    }

    public String styleLeft(DiffSplitRenderer.LineType type, String text) {
        return diffLines.getOrDefault(type, DiffStyle.PLAIN).left().apply(text);
    }

    public String styleRight(DiffSplitRenderer.LineType type, String text) {
        return diffLines.getOrDefault(type, DiffStyle.PLAIN).right().apply(text);
    }

    public String separator(String text) {
        return separatorStyle.apply(text);
    }

    private static Map<MarkdownRenderer.LineKind, Style> markdownLineStyles() {
        Map<MarkdownRenderer.LineKind, Style> styles = new EnumMap<>(MarkdownRenderer.LineKind.class);
        styles.put(MarkdownRenderer.LineKind.HEADING, Style.of(Ansi.Color.CYAN, true, false));
        styles.put(MarkdownRenderer.LineKind.LIST_ITEM, Style.of(Ansi.Color.DEFAULT, false, false));
        styles.put(MarkdownRenderer.LineKind.BLOCK_QUOTE, Style.of(Ansi.Color.BLUE, false, true));
        styles.put(MarkdownRenderer.LineKind.HORIZONTAL_RULE, Style.of(Ansi.Color.BLACK, false, true));
        styles.put(MarkdownRenderer.LineKind.CODE_FENCE, Style.of(Ansi.Color.BLACK, false, true));
        return styles;
    }

    private static Map<MarkdownRenderer.SpanStyle, Style> markdownSpanStyles() {
        Map<MarkdownRenderer.SpanStyle, Style> styles = new EnumMap<>(MarkdownRenderer.SpanStyle.class);
        styles.put(MarkdownRenderer.SpanStyle.CODE_FENCE, Style.of(Ansi.Color.BLACK, false, true));
        styles.put(MarkdownRenderer.SpanStyle.CODE_KEYWORD, Style.of(Ansi.Color.MAGENTA, true, false));
        styles.put(MarkdownRenderer.SpanStyle.CODE_IDENTIFIER, Style.of(Ansi.Color.DEFAULT, false, false));
        styles.put(MarkdownRenderer.SpanStyle.CODE_STRING, Style.of(Ansi.Color.GREEN, false, false));
        styles.put(MarkdownRenderer.SpanStyle.CODE_COMMENT, Style.of(Ansi.Color.BLACK, false, true));
        styles.put(MarkdownRenderer.SpanStyle.CODE_NUMBER, Style.of(Ansi.Color.YELLOW, false, false));
        styles.put(MarkdownRenderer.SpanStyle.CODE_PUNCTUATION, Style.of(Ansi.Color.CYAN, false, false));
        return styles;
    }

    private static Map<DiffSplitRenderer.LineType, DiffStyle> diffLineStyles() {
        Map<DiffSplitRenderer.LineType, DiffStyle> styles = new EnumMap<>(DiffSplitRenderer.LineType.class);
        Style dim = Style.of(Ansi.Color.BLACK, false, true);
        Style hunk = Style.of(Ansi.Color.CYAN, false, true);
        Style removed = Style.of(Ansi.Color.RED, false, false);
        Style added = Style.of(Ansi.Color.GREEN, false, false);
        styles.put(DiffSplitRenderer.LineType.FILE_HEADER, new DiffStyle(dim, dim));
        styles.put(DiffSplitRenderer.LineType.HUNK, new DiffStyle(hunk, hunk));
        styles.put(DiffSplitRenderer.LineType.CONTEXT, new DiffStyle(dim, dim));
        styles.put(DiffSplitRenderer.LineType.REMOVED, new DiffStyle(removed, Style.PLAIN));
        styles.put(DiffSplitRenderer.LineType.ADDED, new DiffStyle(Style.PLAIN, added));
        styles.put(DiffSplitRenderer.LineType.CHANGED, new DiffStyle(removed, added));
        return styles;
    }

    private record DiffStyle(Style left, Style right) {
        private static final DiffStyle PLAIN = new DiffStyle(Style.PLAIN, Style.PLAIN);
    }

    private record Style(Ansi.Color color, boolean bold, boolean dim) {
        private static final Style PLAIN = new Style(Ansi.Color.DEFAULT, false, false);

        private static Style of(Ansi.Color color, boolean bold, boolean dim) {
            return new Style(color, bold, dim);
        }

        private String apply(String text) {
            if (text == null || text.isEmpty() || this.equals(PLAIN)) {
                return text;
            }
            Ansi ansi = Ansi.ansi();
            if (bold) {
                ansi.bold();
            }
            if (dim) {
                ansi.a(Ansi.Attribute.INTENSITY_FAINT);
            }
            if (color != Ansi.Color.DEFAULT) {
                ansi.fg(color);
            }
            return ansi.a(text).reset().toString();
        }
    }
}
