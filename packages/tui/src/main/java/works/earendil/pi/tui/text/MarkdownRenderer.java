package works.earendil.pi.tui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MarkdownRenderer {
    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "async", "await", "boolean", "break", "case", "catch", "class", "const", "continue",
            "default", "else", "enum", "export", "extends", "false", "final", "for", "function", "if",
            "implements", "import", "instanceof", "interface", "let", "new", "null", "private", "protected",
            "public", "record", "return", "static", "switch", "throw", "true", "try", "var", "void", "while");

    private MarkdownRenderer() {
    }

    public static List<RenderedLine> render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return List.of();
        }
        List<RenderedLine> lines = new ArrayList<>();
        boolean inCode = false;
        String fence = null;
        String language = null;
        String[] sourceLines = markdown.split("\\R", -1);
        for (String line : sourceLines) {
            FenceMarker marker = fenceMarker(line);
            if (marker != null) {
                if (inCode && marker.marker().equals(fence)) {
                    lines.add(new RenderedLine(line, LineKind.CODE_FENCE, language,
                            List.of(new Span(0, line.length(), SpanStyle.CODE_FENCE))));
                    inCode = false;
                    fence = null;
                    language = null;
                } else if (!inCode) {
                    inCode = true;
                    fence = marker.marker();
                    language = marker.language();
                    lines.add(new RenderedLine(line, LineKind.CODE_FENCE, language,
                            List.of(new Span(0, line.length(), SpanStyle.CODE_FENCE))));
                } else {
                    lines.add(codeLine(line, language));
                }
                continue;
            }
            if (inCode) {
                lines.add(codeLine(line, language));
            } else {
                lines.add(textLine(line));
            }
        }
        return List.copyOf(lines);
    }

    private static RenderedLine textLine(String line) {
        String trimmed = line.stripLeading();
        LineKind kind = LineKind.TEXT;
        if (trimmed.startsWith("#")) {
            kind = LineKind.HEADING;
        } else if (trimmed.startsWith(">")) {
            kind = LineKind.BLOCK_QUOTE;
        } else if (trimmed.matches("([-+*])\\s+.*") || trimmed.matches("\\d+[.)]\\s+.*")) {
            kind = LineKind.LIST_ITEM;
        } else if (trimmed.matches("[-*_]{3,}\\s*")) {
            kind = LineKind.HORIZONTAL_RULE;
        }
        return new RenderedLine(line, kind, null, List.of(new Span(0, line.length(), SpanStyle.PLAIN)));
    }

    private static RenderedLine codeLine(String line, String language) {
        return new RenderedLine(line, LineKind.CODE, language, highlightCode(line, language));
    }

    private static List<Span> highlightCode(String line, String language) {
        if (line.isEmpty()) {
            return List.of();
        }
        List<Span> spans = new ArrayList<>();
        int i = 0;
        while (i < line.length()) {
            char ch = line.charAt(i);
            if (startsComment(line, i, language)) {
                spans.add(new Span(i, line.length(), SpanStyle.CODE_COMMENT));
                break;
            }
            if (ch == '"' || ch == '\'' || ch == '`') {
                int end = stringEnd(line, i, ch);
                spans.add(new Span(i, end, SpanStyle.CODE_STRING));
                i = end;
                continue;
            }
            if (Character.isDigit(ch)) {
                int end = i + 1;
                while (end < line.length() && (Character.isDigit(line.charAt(end)) || line.charAt(end) == '.')) {
                    end++;
                }
                spans.add(new Span(i, end, SpanStyle.CODE_NUMBER));
                i = end;
                continue;
            }
            if (Character.isJavaIdentifierStart(ch)) {
                int end = i + 1;
                while (end < line.length() && Character.isJavaIdentifierPart(line.charAt(end))) {
                    end++;
                }
                String word = line.substring(i, end).toLowerCase(Locale.ROOT);
                spans.add(new Span(i, end, KEYWORDS.contains(word) ? SpanStyle.CODE_KEYWORD : SpanStyle.CODE_IDENTIFIER));
                i = end;
                continue;
            }
            if (!Character.isWhitespace(ch)) {
                spans.add(new Span(i, i + 1, SpanStyle.CODE_PUNCTUATION));
            } else {
                spans.add(new Span(i, i + 1, SpanStyle.PLAIN));
            }
            i++;
        }
        return List.copyOf(spans);
    }

    private static boolean startsComment(String line, int offset, String language) {
        if (line.startsWith("//", offset)) {
            return true;
        }
        String lang = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return ("sh".equals(lang) || "bash".equals(lang) || "shell".equals(lang))
                && line.charAt(offset) == '#';
    }

    private static int stringEnd(String line, int start, char quote) {
        boolean escaped = false;
        for (int i = start + 1; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (escaped) {
                escaped = false;
            } else if (ch == '\\') {
                escaped = true;
            } else if (ch == quote) {
                return i + 1;
            }
        }
        return line.length();
    }

    private static FenceMarker fenceMarker(String line) {
        String trimmed = line.stripLeading();
        if (!trimmed.startsWith("```") && !trimmed.startsWith("~~~")) {
            return null;
        }
        String marker = trimmed.substring(0, 3);
        int i = 3;
        while (i < trimmed.length() && trimmed.charAt(i) == marker.charAt(0)) {
            i++;
        }
        String info = trimmed.substring(i).trim();
        String language = info.isEmpty() ? null : info.split("\\s+", 2)[0];
        return new FenceMarker(marker, language);
    }

    public enum LineKind {
        TEXT,
        HEADING,
        LIST_ITEM,
        BLOCK_QUOTE,
        HORIZONTAL_RULE,
        CODE_FENCE,
        CODE
    }

    public enum SpanStyle {
        PLAIN,
        CODE_FENCE,
        CODE_KEYWORD,
        CODE_IDENTIFIER,
        CODE_STRING,
        CODE_COMMENT,
        CODE_NUMBER,
        CODE_PUNCTUATION
    }

    public record RenderedLine(String text, LineKind kind, String language, List<Span> spans) {
    }

    public record Span(int start, int end, SpanStyle style) {
    }

    private record FenceMarker(String marker, String language) {
    }
}
