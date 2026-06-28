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
        int cursor = Math.max(0, x);
        for (int i = 0; i < visible.length() && cursor < width; ) {
            int cp = visible.codePointAt(i);
            String s = new String(Character.toChars(cp));
            lines[y].replace(cursor, Math.min(width, cursor + 1), s);
            cursor += EastAsianWidth.visibleWidth(s);
            i += Character.charCount(cp);
        }
    }

    public String frame() {
        return String.join("\n", Arrays.stream(lines).map(StringBuilder::toString).toList());
    }
}
