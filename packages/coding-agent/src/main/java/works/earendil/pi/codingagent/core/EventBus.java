package works.earendil.pi.codingagent.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class EventBus {
    private final Map<String, CopyOnWriteArrayList<EventHandler>> handlersByChannel = new ConcurrentHashMap<>();

    public static EventBus create() {
        return new EventBus();
    }

    public void emit(String channel, Object data) {
        List<EventHandler> handlers = handlersByChannel.get(channel);
        if (handlers == null) {
            return;
        }
        for (EventHandler handler : handlers) {
            try {
                handler.handle(data);
            } catch (Exception e) {
                System.err.println("Event handler error (" + channel + "): " + e.getMessage());
            }
        }
    }

    public Subscription on(String channel, EventHandler handler) {
        handlersByChannel.computeIfAbsent(channel, ignored -> new CopyOnWriteArrayList<>()).add(handler);
        return () -> {
            CopyOnWriteArrayList<EventHandler> handlers = handlersByChannel.get(channel);
            if (handlers != null) {
                handlers.remove(handler);
                if (handlers.isEmpty()) {
                    handlersByChannel.remove(channel, handlers);
                }
            }
        };
    }

    public void clear() {
        handlersByChannel.clear();
    }

    @FunctionalInterface
    public interface EventHandler {
        void handle(Object data) throws Exception;
    }

    @FunctionalInterface
    public interface Subscription extends AutoCloseable {
        @Override
        void close();
    }
}
