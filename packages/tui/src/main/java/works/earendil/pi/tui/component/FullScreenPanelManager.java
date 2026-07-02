package works.earendil.pi.tui.component;

import java.io.PrintStream;

public final class FullScreenPanelManager {
    private final PrintStream out;
    private int width;
    private int height;
    private boolean active;
    private Component body;
    private Component header;
    private Component footer;
    private String lastRenderedFrame = "";

    public FullScreenPanelManager(PrintStream out, int width, int height) {
        this.out = out;
        this.width = width;
        this.height = height;
    }

    public synchronized void enterFullScreen() {
        if (!active) {
            active = true;
            out.print("\u001b[?1049h"); // Enable alternate screen buffer
            out.print("\u001b[?25l");   // Hide cursor
            out.print("\u001b[2J\u001b[H"); // Clear screen and move cursor home
            out.flush();
        }
    }

    public synchronized void exitFullScreen() {
        if (active) {
            active = false;
            out.print("\u001b[2J\u001b[H"); // Clear
            out.print("\u001b[?25h");   // Show cursor
            out.print("\u001b[?1049l"); // Disable alternate screen buffer
            out.flush();
        }
    }

    public synchronized void resize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        this.lastRenderedFrame = ""; // force full re-render on resize
    }

    public synchronized void setHeader(Component header) {
        this.header = header;
    }

    public synchronized void setBody(Component body) {
        this.body = body;
    }

    public synchronized void setFooter(Component footer) {
        this.footer = footer;
    }

    public synchronized void render() {
        if (!active || width <= 0 || height <= 0) {
            return;
        }
        Surface surface = new Surface(width, height);
        int currentY = 0;
        int headerHeight = 1;
        if (header != null) {
            Surface headerSurf = new Surface(width, headerHeight);
            header.render(headerSurf);
            for (String line : headerSurf.frame().split("\n", -1)) {
                if (currentY < height) {
                    surface.write(0, currentY++, line);
                }
            }
        }
        int footerHeight = footer != null ? 1 : 0;
        int bodyHeight = Math.max(0, height - currentY - footerHeight);
        if (body != null && bodyHeight > 0) {
            Surface bodySurf = new Surface(width, bodyHeight);
            body.render(bodySurf);
            for (String line : bodySurf.frame().split("\n", -1)) {
                if (currentY < height - footerHeight) {
                    surface.write(0, currentY++, line);
                }
            }
        }
        while (currentY < height - footerHeight) {
            currentY++;
        }
        if (footer != null && currentY < height) {
            Surface footerSurf = new Surface(width, 1);
            footer.render(footerSurf);
            String frame = footerSurf.frame();
            int nl = frame.indexOf('\n');
            String line = nl >= 0 ? frame.substring(0, nl) : frame;
            surface.write(0, currentY, line);
        }

        String newFrame = surface.frame();
        if (!newFrame.equals(lastRenderedFrame)) {
            out.print("\u001b[H"); // move to top left
            out.print(newFrame);
            out.flush();
            lastRenderedFrame = newFrame;
        }
    }

    public boolean isActive() {
        return active;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
