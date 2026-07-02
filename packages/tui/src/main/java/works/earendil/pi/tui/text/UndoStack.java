package works.earendil.pi.tui.text;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public final class UndoStack {
    private final int maxHistory;
    private final Deque<EditState> undoStack = new ArrayDeque<>();
    private final Deque<EditState> redoStack = new ArrayDeque<>();
    private EditState current;

    public record EditState(String text, int cursorPosition) {
        public EditState {
            if (text == null) text = "";
            cursorPosition = Math.max(0, Math.min(cursorPosition, text.length()));
        }
    }

    public UndoStack(int maxHistory) {
        this.maxHistory = Math.max(1, maxHistory);
        this.current = new EditState("", 0);
    }

    public UndoStack() {
        this(100);
    }

    public EditState current() {
        return current;
    }

    public synchronized void push(EditState state) {
        if (state == null || state.equals(current)) {
            return;
        }
        undoStack.push(current);
        if (undoStack.size() > maxHistory) {
            undoStack.removeLast();
        }
        current = state;
        redoStack.clear();
    }

    public synchronized void push(String text, int cursor) {
        push(new EditState(text, cursor));
    }

    public synchronized Optional<EditState> undo() {
        if (undoStack.isEmpty()) {
            return Optional.empty();
        }
        redoStack.push(current);
        current = undoStack.pop();
        return Optional.of(current);
    }

    public synchronized Optional<EditState> redo() {
        if (redoStack.isEmpty()) {
            return Optional.empty();
        }
        undoStack.push(current);
        current = redoStack.pop();
        return Optional.of(current);
    }

    public synchronized void clear() {
        undoStack.clear();
        redoStack.clear();
        current = new EditState("", 0);
    }
}
