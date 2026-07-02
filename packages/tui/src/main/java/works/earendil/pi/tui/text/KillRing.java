package works.earendil.pi.tui.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class KillRing {
    private final int capacity;
    private final List<String> ring = new ArrayList<>();
    private int yankIndex = -1;

    public KillRing(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public KillRing() {
        this(60);
    }

    public synchronized void push(String text, boolean appendToLast) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (appendToLast && !ring.isEmpty()) {
            String last = ring.removeFirst();
            ring.addFirst(last + text);
        } else {
            ring.addFirst(text);
            if (ring.size() > capacity) {
                ring.removeLast();
            }
        }
        yankIndex = 0;
    }

    public synchronized void push(String text) {
        push(text, false);
    }

    public synchronized Optional<String> yank() {
        if (ring.isEmpty()) {
            return Optional.empty();
        }
        yankIndex = 0;
        return Optional.of(ring.get(yankIndex));
    }

    public synchronized Optional<String> yankPop() {
        if (ring.isEmpty() || yankIndex < 0) {
            return Optional.empty();
        }
        yankIndex = (yankIndex + 1) % ring.size();
        return Optional.of(ring.get(yankIndex));
    }

    public synchronized List<String> entries() {
        return List.copyOf(ring);
    }

    public synchronized void clear() {
        ring.clear();
        yankIndex = -1;
    }
}
