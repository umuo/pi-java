package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventBusTest {
    @Test
    void emitsUnsubscribesAndClearsHandlers() {
        EventBus bus = EventBus.create();
        List<Object> received = new ArrayList<>();
        EventBus.Subscription subscription = bus.on("updates", received::add);

        bus.emit("updates", "one");
        subscription.close();
        bus.emit("updates", "two");
        bus.on("updates", received::add);
        bus.clear();
        bus.emit("updates", "three");

        assertThat(received).containsExactly("one");
    }

    @Test
    void handlerErrorsDoNotStopLaterHandlers() {
        EventBus bus = EventBus.create();
        List<Object> received = new ArrayList<>();
        bus.on("updates", data -> {
            throw new IllegalStateException("broken");
        });
        bus.on("updates", received::add);

        PrintStream originalErr = System.err;
        System.setErr(new PrintStream(new ByteArrayOutputStream()));
        try {
            bus.emit("updates", "still delivered");
        } finally {
            System.setErr(originalErr);
        }

        assertThat(received).containsExactly("still delivered");
    }
}
