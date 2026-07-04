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
        this(markdownLineStyles(), markdownSpanStyles(), diffLineStyles(), Style.of(Ansi.Color.BLACK, false, true));
    }

    private TerminalTheme(Map<MarkdownRenderer.LineKind, Style> markdownLines,
                          Map<MarkdownRenderer.SpanStyle, Style> markdownSpans,
                          Map<DiffSplitRenderer.LineType, DiffStyle> diffLines,
                          Style separatorStyle) {
        this.markdownLines = Map.copyOf(markdownLines);
        this.markdownSpans = Map.copyOf(markdownSpans);
        this.diffLines = Map.copyOf(diffLines);
        this.separatorStyle = separatorStyle;
    }

    public static TerminalTheme standard() {
        return DEFAULT;
    }

    public static TerminalTheme fromTokenColors(Map<String, Ansi.Color> tokenColors) {
        if (tokenColors == null || tokenColors.isEmpty()) {
            return standard();
        }
        Map<String, String> tokenSgr = new java.util.LinkedHashMap<>();
        tokenColors.forEach((token, color) -> {
            String foreground = foregroundSgr(color);
            if (foreground != null) {
                tokenSgr.put(token, foreground);
            }
        });
        return fromTokenSgr(tokenSgr);
    }

    public static TerminalTheme fromTokenSgr(Map<String, String> tokenSgr) {
        if (tokenSgr == null || tokenSgr.isEmpty()) {
            return standard();
        }
        Map<MarkdownRenderer.LineKind, Style> lineStyles = markdownLineStyles();
        Map<MarkdownRenderer.SpanStyle, Style> spanStyles = markdownSpanStyles();
        Map<DiffSplitRenderer.LineType, DiffStyle> diffStyles = diffLineStyles();

        apply(lineStyles, MarkdownRenderer.LineKind.HEADING, tokenSgr.get("mdHeading"));
        apply(lineStyles, MarkdownRenderer.LineKind.BLOCK_QUOTE, tokenSgr.get("mdQuote"));
        apply(lineStyles, MarkdownRenderer.LineKind.HORIZONTAL_RULE, tokenSgr.get("mdHr"));
        apply(lineStyles, MarkdownRenderer.LineKind.CODE_FENCE, tokenSgr.get("mdCodeBlock"));

        apply(spanStyles, MarkdownRenderer.SpanStyle.CODE_FENCE, tokenSgr.get("mdCodeBlock"));
        apply(spanStyles, MarkdownRenderer.SpanStyle.CODE_KEYWORD, tokenSgr.get("syntaxKeyword"));
        apply(spanStyles, MarkdownRenderer.SpanStyle.CODE_IDENTIFIER, tokenSgr.get("syntaxVariable"));
        apply(spanStyles, MarkdownRenderer.SpanStyle.CODE_STRING, tokenSgr.get("syntaxString"));
        apply(spanStyles, MarkdownRenderer.SpanStyle.CODE_COMMENT, tokenSgr.get("syntaxComment"));
        apply(spanStyles, MarkdownRenderer.SpanStyle.CODE_NUMBER, tokenSgr.get("syntaxNumber"));
        apply(spanStyles, MarkdownRenderer.SpanStyle.CODE_PUNCTUATION, tokenSgr.get("syntaxPunctuation"));

        String context = tokenSgr.get("toolDiffContext");
        String removed = tokenSgr.get("toolDiffRemoved");
        String added = tokenSgr.get("toolDiffAdded");
        String hunk = tokenSgr.get("borderAccent");
        if (context != null) {
            diffStyles.put(DiffSplitRenderer.LineType.FILE_HEADER,
                    new DiffStyle(diffStyles.get(DiffSplitRenderer.LineType.FILE_HEADER).left().withForeground(context),
                            diffStyles.get(DiffSplitRenderer.LineType.FILE_HEADER).right().withForeground(context)));
            diffStyles.put(DiffSplitRenderer.LineType.CONTEXT,
                    new DiffStyle(diffStyles.get(DiffSplitRenderer.LineType.CONTEXT).left().withForeground(context),
                            diffStyles.get(DiffSplitRenderer.LineType.CONTEXT).right().withForeground(context)));
        }
        if (hunk != null) {
            diffStyles.put(DiffSplitRenderer.LineType.HUNK,
                    new DiffStyle(diffStyles.get(DiffSplitRenderer.LineType.HUNK).left().withForeground(hunk),
                            diffStyles.get(DiffSplitRenderer.LineType.HUNK).right().withForeground(hunk)));
        }
        if (removed != null) {
            DiffStyle style = diffStyles.get(DiffSplitRenderer.LineType.REMOVED);
            diffStyles.put(DiffSplitRenderer.LineType.REMOVED,
                    new DiffStyle(style.left().withForeground(removed), style.right()));
        }
        if (added != null) {
            DiffStyle style = diffStyles.get(DiffSplitRenderer.LineType.ADDED);
            diffStyles.put(DiffSplitRenderer.LineType.ADDED,
                    new DiffStyle(style.left(), style.right().withForeground(added)));
        }
        if (removed != null || added != null) {
            DiffStyle style = diffStyles.get(DiffSplitRenderer.LineType.CHANGED);
            diffStyles.put(DiffSplitRenderer.LineType.CHANGED, new DiffStyle(
                    removed == null ? style.left() : style.left().withForeground(removed),
                    added == null ? style.right() : style.right().withForeground(added)));
        }

        Style separator = Style.of(Ansi.Color.BLACK, false, true)
                .withForeground(tokenSgr.get("borderMuted"));
        return new TerminalTheme(lineStyles, spanStyles, diffStyles, separator);
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

    private static <T> void apply(Map<T, Style> styles, T key, String foregroundSgr) {
        if (foregroundSgr != null) {
            styles.put(key, styles.getOrDefault(key, Style.PLAIN).withForeground(foregroundSgr));
        }
    }

    private record DiffStyle(Style left, Style right) {
        private static final DiffStyle PLAIN = new DiffStyle(Style.PLAIN, Style.PLAIN);
    }

    private record Style(String foregroundSgr, boolean bold, boolean dim) {
        private static final Style PLAIN = new Style(null, false, false);

        private static Style of(Ansi.Color color, boolean bold, boolean dim) {
            return new Style(TerminalTheme.foregroundSgr(color), bold, dim);
        }

        private Style withForeground(String foregroundSgr) {
            return new Style(foregroundSgr == null ? this.foregroundSgr : foregroundSgr, bold, dim);
        }

        private String apply(String text) {
            if (text == null || text.isEmpty() || this.equals(PLAIN)) {
                return text;
            }
            StringBuilder ansi = new StringBuilder();
            if (bold) {
                ansi.append("\u001B[1m");
            }
            if (dim) {
                ansi.append("\u001B[2m");
            }
            if (foregroundSgr != null) {
                ansi.append(foregroundSgr);
            }
            return ansi.append(text).append("\u001B[0m").toString();
        }
    }

    private static String foregroundSgr(Ansi.Color color) {
        if (color == null || color == Ansi.Color.DEFAULT) {
            return null;
        }
        return switch (color) {
            case BLACK -> "\u001B[30m";
            case RED -> "\u001B[31m";
            case GREEN -> "\u001B[32m";
            case YELLOW -> "\u001B[33m";
            case BLUE -> "\u001B[34m";
            case MAGENTA -> "\u001B[35m";
            case CYAN -> "\u001B[36m";
            case WHITE -> "\u001B[37m";
            default -> null;
        };
    }
}
