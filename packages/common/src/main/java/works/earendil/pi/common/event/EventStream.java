package works.earendil.pi.common.event;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

public final class EventStream<T> implements AutoCloseable {
    private final SubmissionPublisher<T> publisher = new SubmissionPublisher<>();

    public Flow.Publisher<T> publisher() {
        return publisher;
    }

    public void emit(T event) {
        publisher.submit(event);
    }

    public void emitAll(Iterable<T> events) {
        for (T event : events) {
            emit(event);
        }
    }

    public static <T> EventStream<T> of(List<T> events) {
        EventStream<T> stream = new EventStream<>();
        stream.emitAll(events);
        stream.close();
        return stream;
    }

    public static <T> EventStream<T> of(Iterator<T> iterator) {
        EventStream<T> stream = new EventStream<>();
        while (iterator.hasNext()) {
            stream.emit(iterator.next());
        }
        stream.close();
        return stream;
    }

    @Override
    public void close() {
        publisher.close();
    }
}
