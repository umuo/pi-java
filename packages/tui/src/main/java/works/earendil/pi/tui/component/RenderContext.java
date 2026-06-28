package works.earendil.pi.tui.component;

public interface RenderContext {
    int width();

    int height();

    void write(int x, int y, String text);
}
