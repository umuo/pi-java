package works.earendil.pi.ai.provider;

import org.junit.jupiter.api.Test;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinProvidersTest {

    @Test
    void testOpenAiProviderModelsAndStream() throws Exception {
        OpenAiProvider provider = new OpenAiProvider();
        assertThat(provider.id()).isEqualTo("openai");
        assertThat(provider.models()).isNotEmpty();

        Model model = provider.models().get(0);
        Context ctx = new Context(List.of(), "system", List.of(), ThinkingLevel.OFF);
        StreamOptions opts = new StreamOptions(null, null, "test-key", null, null, null, Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());

        List<AssistantMessageEvent> events = collectEvents(provider.stream(model, ctx, opts));
        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(AssistantMessageEvent.Start.class);
    }

    @Test
    void testAnthropicProviderModelsAndStream() throws Exception {
        AnthropicProvider provider = new AnthropicProvider();
        assertThat(provider.id()).isEqualTo("anthropic");
        assertThat(provider.models()).isNotEmpty();

        Model model = provider.models().get(0);
        Context ctx = new Context(List.of(), "system", List.of(), ThinkingLevel.OFF);
        StreamOptions opts = new StreamOptions(null, null, "test-key", null, null, null, Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());

        List<AssistantMessageEvent> events = collectEvents(provider.stream(model, ctx, opts));
        assertThat(events).isNotEmpty();
        assertThat(events.get(0)).isInstanceOf(AssistantMessageEvent.Start.class);
    }

    private List<AssistantMessageEvent> collectEvents(AssistantMessageEventStream stream) throws InterruptedException {
        List<AssistantMessageEvent> events = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        stream.publisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AssistantMessageEvent item) {
                events.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
        return events;
    }
}
