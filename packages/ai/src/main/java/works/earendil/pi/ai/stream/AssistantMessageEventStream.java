package works.earendil.pi.ai.stream;

import works.earendil.pi.common.event.EventStream;

import java.util.concurrent.Flow;

public final class AssistantMessageEventStream implements AutoCloseable {
    private final EventStream<AssistantMessageEvent> delegate = new EventStream<>();

    public Flow.Publisher<AssistantMessageEvent> publisher() {
        return delegate.publisher();
    }

    public void emit(AssistantMessageEvent event) {
        delegate.emit(event);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
