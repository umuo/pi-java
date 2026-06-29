package works.earendil.pi.tui.component;

import works.earendil.pi.common.text.EastAsianWidth;

import java.util.Arrays;

public final class Surface implements RenderContext {
    private final int width;
    private final int height;
    private final StringBuilder[] lines;

    public Surface(int width, int height) {
        this.width = width;
        this.height = height;
        this.lines = new StringBuilder[height];
        for (int i = 0; i < height; i++) {
            char[] chars = new char[width];
            Arrays.fill(chars, ' ');
            lines[i] = new StringBuilder(new String(chars));
        }
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void write(int x, int y, String text) {
        if (y < 0 || y >= height || x >= width) {
            return;
        }
        String visible = EastAsianWidth.truncateToWidth(text, width - Math.max(0, x));
        int column = Math.max(0, x);
        String existing = lines[y].toString();
        String next = takeColumns(existing, column)
                + visible
                + dropColumns(existing, column + EastAsianWidth.visibleWidth(visible));
        lines[y] = new StringBuilder(padToWidth(EastAsianWidth.truncateToWidth(next, width), width));
    }

    public String frame() {
        return String.join("\n", Arrays.stream(lines).map(StringBuilder::toString).toList());
    }

    private static String takeColumns(String text, int columns) {
        StringBuilder out = new StringBuilder();
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String value = new String(Character.toChars(cp));
            int nextWidth = EastAsianWidth.visibleWidth(value);
            if (width + nextWidth > columns) {
                break;
            }
            out.append(value);
            width += nextWidth;
            i += Character.charCount(cp);
        }
        return out + " ".repeat(Math.max(0, columns - width));
    }

    private static String dropColumns(String text, int columns) {
        int width = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String value = new String(Character.toChars(cp));
            int nextWidth = EastAsianWidth.visibleWidth(value);
            i += Character.charCount(cp);
            width += nextWidth;
            if (width >= columns) {
                return text.substring(i);
            }
        }
        return "";
    }

    private static String padToWidth(String text, int columns) {
        int width = EastAsianWidth.visibleWidth(text);
        if (width >= columns) {
            return text;
        }
        return text + " ".repeat(columns - width);
    }
}
