package works.earendil.pi.tui.component;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

public class ListSelector {
    public static class Item {
        public String id;
        public String label;
        public String description;
        public boolean selected;
        public boolean disabled;

        public Item(String id, String label, String description, boolean selected, boolean disabled) {
            this.id = id;
            this.label = label;
            this.description = description;
            this.selected = selected;
            this.disabled = disabled;
        }
    }

    private final String title;
    private final List<Item> items;
    private final boolean multiSelect;
    private int cursorIndex = 0;
    private boolean canceled = false;
    private boolean submitted = false;

    public ListSelector(String title, List<Item> items, boolean multiSelect) {
        this.title = title;
        this.items = items;
        this.multiSelect = multiSelect;
    }

    public List<String> show() throws IOException {
        try (Terminal terminal = TerminalBuilder.builder().system(true).jna(false).jansi(true).build()) {
            Attributes prevAttr = terminal.enterRawMode();
            FullScreenPanelManager panelManager = new FullScreenPanelManager(new PrintStream(terminal.output()), terminal.getWidth(), terminal.getHeight());
            try {
                panelManager.enterFullScreen();
                NonBlockingReader reader = terminal.reader();
                
                while (!canceled && !submitted) {
                    panelManager.resize(terminal.getWidth(), terminal.getHeight());
                    render(panelManager);
                    
                    int c = reader.read(100);
                    if (c >= 0) {
                        handleKey(c, reader);
                    }
                }
                
                if (canceled) {
                    return null;
                }
                
                List<String> result = new ArrayList<>();
                for (Item item : items) {
                    if (multiSelect) {
                        if (item.selected) result.add(item.id);
                    }
                }
                if (!multiSelect) {
                    if (!items.isEmpty() && !items.get(cursorIndex).disabled) {
                        result.add(items.get(cursorIndex).id);
                    }
                }
                return result;
            } finally {
                panelManager.exitFullScreen();
                terminal.setAttributes(prevAttr);
            }
        }
    }

    private void handleKey(int c, NonBlockingReader reader) throws IOException {
        if (c == 27) { // ESC or escape sequence
            int next1 = reader.read(10);
            if (next1 == -1) {
                // Just ESC
                canceled = true;
            } else if (next1 == 91) { // '['
                int next2 = reader.read(10);
                if (next2 == 65) { // Up
                    moveCursor(-1);
                } else if (next2 == 66) { // Down
                    moveCursor(1);
                }
            }
        } else if (c == 13 || c == 10) { // Enter
            if (multiSelect) {
                submitted = true;
            } else {
                if (!items.isEmpty() && !items.get(cursorIndex).disabled) {
                    submitted = true;
                }
            }
        } else if (c == 32) { // Space
            if (multiSelect && !items.isEmpty() && !items.get(cursorIndex).disabled) {
                items.get(cursorIndex).selected = !items.get(cursorIndex).selected;
            }
        } else if (c == 3 || c == 4) { // Ctrl+C or Ctrl+D
            canceled = true;
        }
    }

    private void moveCursor(int delta) {
        if (items.isEmpty()) return;
        cursorIndex += delta;
        if (cursorIndex < 0) cursorIndex = 0;
        if (cursorIndex >= items.size()) cursorIndex = items.size() - 1;
    }

    private void render(FullScreenPanelManager panel) {
        StringBuilder body = new StringBuilder();
        int displayHeight = panel.height() - 4; // Reserve for header/footer
        if (displayHeight < 5) displayHeight = 5;
        
        int startIdx = Math.max(0, cursorIndex - displayHeight / 2);
        int endIdx = Math.min(items.size(), startIdx + displayHeight);
        if (endIdx - startIdx < displayHeight && startIdx > 0) {
            startIdx = Math.max(0, endIdx - displayHeight);
        }

        for (int i = startIdx; i < endIdx; i++) {
            Item item = items.get(i);
            String prefix = (i == cursorIndex) ? "> " : "  ";
            String check = multiSelect ? (item.selected ? "[x] " : "[ ] ") : "";
            
            if (i == cursorIndex) {
                body.append("\u001b[36m").append(prefix).append(check).append(item.label).append("\u001b[0m");
            } else if (item.disabled) {
                body.append("\u001b[90m").append(prefix).append(check).append(item.label).append("\u001b[0m");
            } else {
                body.append(prefix).append(check).append(item.label);
            }
            if (item.description != null && !item.description.isBlank()) {
                body.append(" - \u001b[90m").append(item.description).append("\u001b[0m");
            }
            body.append("\n");
        }

        panel.setHeader(new Component() {
            @Override
            public void render(RenderContext context) {
                context.write(0, 0, "\u001b[1m" + title + "\u001b[0m");
            }
        });
        panel.setBody(new Component() {
            @Override
            public void render(RenderContext context) {
                String[] lines = body.toString().split("\n");
                for (int i = 0; i < lines.length && i < context.height(); i++) {
                    context.write(0, i, lines[i]);
                }
            }
        });
        panel.setFooter(new Component() {
            @Override
            public void render(RenderContext context) {
                String msg = multiSelect ? "[\u2191\u2193] Navigate  [Space] Toggle  [Enter] Confirm  [ESC] Cancel" 
                                         : "[\u2191\u2193] Navigate  [Enter] Select  [ESC] Cancel";
                context.write(0, 0, "\u001b[90m" + msg + "\u001b[0m");
            }
        });
        panel.render();
    }
}
