package works.earendil.pi.tui.component;

import works.earendil.pi.common.text.EastAsianWidth;

public final class Text implements Component {
    private final String text;

    public Text(String text) {
        this.text = text;
    }

    @Override
    public void render(RenderContext context) {
        String[] lines = text.split("\\R", -1);
        for (int y = 0; y < lines.length && y < context.height(); y++) {
            context.write(0, y, EastAsianWidth.truncateToWidth(lines[y], context.width()));
        }
    }
}
