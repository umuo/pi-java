package works.earendil.pi.ai.provider;

import org.junit.jupiter.api.Test;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.common.json.JsonCodec;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BuiltinProvidersTest {

    @Test
    void testOpenAiProviderModelsAndStream() throws Exception {
        OpenAiProvider provider = new OpenAiProvider();
        assertThat(provider.id()).isEqualTo("openai");
        assertThat(provider.models()).isNotEmpty();
        assertThat(provider.models()).extracting(Model::modelId).contains("gpt-4o", "gpt-4o-mini");
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

    @Test
    void testGeminiProviderModelsAndDefaultRegistry() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.registerDefaults();

        assertThat(registry.provider("google")).hasValueSatisfying(provider -> {
            assertThat(provider).isInstanceOf(GeminiProvider.class);
            assertThat(provider.models())
                    .extracting(Model::modelId)
                    .contains("gemini-2.5-flash", "gemini-3.1-pro-preview");
        });
        assertThat(registry.provider("google-generative-ai")).hasValueSatisfying(provider ->
                assertThat(provider).isInstanceOf(GeminiProvider.class));
        assertThat(registry.provider("google-vertex")).isEmpty();
        assertThat(registry.findModel("google", "gemini-3.1-pro-preview")).isPresent();
    }

    @Test
    void testGeminiProviderBuildsNativeRequestBody() throws Exception {
        GeminiProvider provider = new GeminiProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("gemini-2.5-flash"))
                .findFirst()
                .orElseThrow();
        Tool tool = new Tool("read", "Read a file",
                JsonCodec.parse("""
                        {
                          "type": "object",
                          "properties": { "path": { "type": "string" } },
                          "required": ["path"]
                        }
                        """), null);
        Context context = new Context(List.of(new Message.User(List.of(
                new Content.Text("Inspect this"),
                new Content.Image("image/png", "aW1hZ2U=", null)
        ), Instant.now())), "You are concise.", List.of(tool), ThinkingLevel.MEDIUM);
        StreamOptions options = new StreamOptions(0.2, 2048, "test-key", null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());

        var body = GeminiProvider.buildRequestBody(model, context, options);

        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/contents/0/parts/0/text").asText()).isEqualTo("Inspect this");
        assertThat(body.at("/contents/0/parts/1/inlineData/mimeType").asText()).isEqualTo("image/png");
        assertThat(body.at("/systemInstruction/parts/0/text").asText()).isEqualTo("You are concise.");
        assertThat(body.at("/generationConfig/temperature").asDouble()).isEqualTo(0.2);
        assertThat(body.at("/generationConfig/maxOutputTokens").asInt()).isEqualTo(2048);
        assertThat(body.at("/generationConfig/thinkingConfig/includeThoughts").asBoolean()).isTrue();
        assertThat(body.at("/generationConfig/thinkingConfig/thinkingBudget").asInt()).isEqualTo(8192);
        assertThat(body.at("/tools/0/functionDeclarations/0/name").asText()).isEqualTo("read");
        assertThat(body.at("/tools/0/functionDeclarations/0/parametersJsonSchema/properties/path/type").asText())
                .isEqualTo("string");
    }

    @Test
    void testGeminiProviderParsesStreamChunk() throws Exception {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        EventCollector collector = subscribe(stream);
        GeminiProvider.GeminiAccumulator accumulator = new GeminiProvider.GeminiAccumulator();

        GeminiProvider.handleChunk(JsonCodec.parse("""
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          { "text": "Hi " },
                          { "functionCall": { "name": "read", "args": { "path": "README.md" } } }
                        ]
                      },
                      "finishReason": "STOP"
                    }
                  ],
                  "usageMetadata": {
                    "promptTokenCount": 12,
                    "cachedContentTokenCount": 3,
                    "candidatesTokenCount": 5,
                    "thoughtsTokenCount": 2,
                    "totalTokenCount": 19
                  }
                }
                """), accumulator, stream);
        stream.close();
        collector.await();

        assertThat(collector.events()).hasAtLeastOneElementOfType(AssistantMessageEvent.UsageDelta.class);
        assertThat(collector.events()).hasAtLeastOneElementOfType(AssistantMessageEvent.ContentDelta.class);
        assertThat(accumulator.usage().inputTokens()).isEqualTo(9);
        assertThat(accumulator.usage().cacheReadInputTokens()).isEqualTo(3);
        assertThat(accumulator.usage().outputTokens()).isEqualTo(5);
        assertThat(accumulator.usage().reasoningTokens()).isEqualTo(2);
        assertThat(accumulator.stopReason()).isEqualTo(StopReason.TOOL_USE);
        assertThat(accumulator.finalContents()).anySatisfy(content ->
                assertThat(content).isEqualTo(new Content.Text("Hi ")));
        assertThat(accumulator.finalContents()).anySatisfy(content -> {
            assertThat(content).isInstanceOf(Content.ToolCall.class);
            Content.ToolCall toolCall = (Content.ToolCall) content;
            assertThat(toolCall.name()).isEqualTo("read");
            assertThat(toolCall.input().path("path").asText()).isEqualTo("README.md");
        });
    }

    @Test
    void testOpenAiCompatibleProvidersRegisterGroqMistralOllamaAndXai() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.registerDefaults();

        assertThat(registry.provider("groq")).hasValueSatisfying(provider -> {
            assertThat(provider).isInstanceOf(GroqProvider.class);
            assertThat(provider.models())
                    .extracting(Model::modelId)
                    .contains("openai/gpt-oss-120b", "llama-3.3-70b-versatile");
        });
        assertThat(registry.provider("mistral")).hasValueSatisfying(provider -> {
            assertThat(provider).isInstanceOf(MistralProvider.class);
            assertThat(provider.models())
                    .extracting(Model::modelId)
                    .contains("devstral-medium-latest", "mistral-large-latest", "pixtral-large-latest");
        });
        assertThat(registry.provider("ollama")).hasValueSatisfying(provider -> {
            assertThat(provider).isInstanceOf(OllamaProvider.class);
            assertThat(provider.models())
                    .extracting(Model::modelId)
                    .contains("llama3.1:8b", "qwen2.5-coder:7b", "gpt-oss:20b");
        });
        assertThat(registry.provider("xai")).hasValueSatisfying(provider -> {
            assertThat(provider).isInstanceOf(XaiProvider.class);
            assertThat(provider.models())
                    .extracting(Model::modelId)
                    .contains("grok-code-fast-1", "grok-4.3");
        });
        assertThat(registry.findModel("groq", "openai/gpt-oss-120b"))
                .hasValueSatisfying(model -> {
                    assertThat(model.api()).isEqualTo("openai-completions");
                    assertThat(model.supportsTools()).isTrue();
                    assertThat(model.options()).containsEntry("baseUrl", "https://api.groq.com/openai/v1");
                });
        assertThat(registry.findModel("mistral", "devstral-medium-latest"))
                .hasValueSatisfying(model -> {
                    assertThat(model.api()).isEqualTo("mistral-conversations");
                    assertThat(model.contextWindow()).isEqualTo(262144);
                    assertThat(model.options()).containsEntry("baseUrl", "https://api.mistral.ai/v1");
                });
        assertThat(registry.findModel("ollama", "gpt-oss:20b"))
                .hasValueSatisfying(model -> {
                    assertThat(model.api()).isEqualTo("openai-completions");
                    assertThat(model.options()).containsEntry("baseUrl", "http://localhost:11434/v1");
                    assertThat(model.options()).containsEntry("reasoning", true);
                });
        assertThat(registry.findModel("xai", "grok-4.3"))
                .hasValueSatisfying(model -> {
                    assertThat(model.supportsImages()).isTrue();
                    assertThat(model.options()).containsEntry("baseUrl", "https://api.x.ai/v1");
                });
    }

    @Test
    void testOpenAiCompatibleProviderBuildsChatCompletionsBody() throws Exception {
        GroqProvider provider = new GroqProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("openai/gpt-oss-120b"))
                .findFirst()
                .orElseThrow();
        Tool tool = new Tool("bash", "Run shell",
                JsonCodec.parse("""
                        {
                          "type": "object",
                          "properties": { "command": { "type": "string" } },
                          "required": ["command"]
                        }
                        """), null);
        Context context = new Context(List.of(new Message.User(List.of(new Content.Text("List files")),
                Instant.now())), "Use tools carefully.", List.of(tool), ThinkingLevel.OFF);
        StreamOptions options = new StreamOptions(0.1, 4096, "test-key", null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());

        var body = OpenAiCompatibleProvider.buildChatCompletionsBody(model, context, options);

        assertThat(body.path("model").asText()).isEqualTo("openai/gpt-oss-120b");
        assertThat(body.path("stream").asBoolean()).isTrue();
        assertThat(body.path("temperature").asDouble()).isEqualTo(0.1);
        assertThat(body.path("max_tokens").asInt()).isEqualTo(4096);
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(body.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/1/content").asText()).isEqualTo("List files");
        assertThat(body.at("/tools/0/function/name").asText()).isEqualTo("bash");
        assertThat(body.at("/tools/0/function/parameters/properties/command/type").asText()).isEqualTo("string");
    }

    @Test
    void testMistralProviderUsesChatCompletionsBodyShape() throws Exception {
        MistralProvider provider = new MistralProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("mistral-large-latest"))
                .findFirst()
                .orElseThrow();
        Context context = new Context(List.of(new Message.User(List.of(
                new Content.Text("Describe this"),
                new Content.Image("image/png", "aW1hZ2U=", null)
        ), Instant.now())), "Be direct.", List.of(), ThinkingLevel.OFF);

        var body = OpenAiCompatibleProvider.buildChatCompletionsBody(model, context, StreamOptions.defaults());

        assertThat(model.api()).isEqualTo("mistral-conversations");
        assertThat(model.supportsImages()).isTrue();
        assertThat(body.path("model").asText()).isEqualTo("mistral-large-latest");
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("system");
        assertThat(body.at("/messages/1/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/1/content").asText()).isEqualTo("Describe this");
    }

    @Test
    void testOpenAiCompatibleProviderParsesStreamChunk() throws Exception {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        EventCollector collector = subscribe(stream);
        OpenAiCompatibleProvider.OpenAiStreamAccumulator accumulator = new OpenAiCompatibleProvider.OpenAiStreamAccumulator();

        OpenAiCompatibleProvider.handleChatCompletionsChunk(JsonCodec.parse("""
                {
                  "choices": [
                    {
                      "delta": {
                        "content": "Done",
                        "tool_calls": [
                          {
                            "index": 0,
                            "id": "call_1",
                            "function": {
                              "name": "bash",
                              "arguments": "{\\"command\\":\\"pwd\\"}"
                            }
                          }
                        ]
                      },
                      "finish_reason": "tool_calls"
                    }
                  ],
                  "usage": {
                    "prompt_tokens": 7,
                    "completion_tokens": 4,
                    "total_tokens": 11
                  }
                }
                """), accumulator, stream);
        stream.close();
        collector.await();

        assertThat(collector.events()).hasAtLeastOneElementOfType(AssistantMessageEvent.UsageDelta.class);
        assertThat(collector.events()).hasAtLeastOneElementOfType(AssistantMessageEvent.ContentDelta.class);
        assertThat(accumulator.usage().inputTokens()).isEqualTo(7);
        assertThat(accumulator.usage().outputTokens()).isEqualTo(4);
        assertThat(accumulator.stopReason()).isEqualTo(StopReason.TOOL_USE);
        assertThat(accumulator.finalContents()).anySatisfy(content ->
                assertThat(content).isEqualTo(new Content.Text("Done")));
        assertThat(accumulator.finalContents()).anySatisfy(content -> {
            assertThat(content).isInstanceOf(Content.ToolCall.class);
            Content.ToolCall toolCall = (Content.ToolCall) content;
            assertThat(toolCall.id()).isEqualTo("call_1");
            assertThat(toolCall.name()).isEqualTo("bash");
            assertThat(toolCall.input().path("command").asText()).isEqualTo("pwd");
        });
    }

    @Test
    void testProviderHttpSupportRetriesRetryableResponses() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://example.test/chat/completions"))
                .GET()
                .build();
        AtomicInteger calls = new AtomicInteger();
        ProviderHttpSupport.RetryPolicy policy = new ProviderHttpSupport.RetryPolicy(
                2, Duration.ofMillis(1), Duration.ofMillis(1));

        HttpResponse<InputStream> response = ProviderHttpSupport.sendWithRetries("test", request, ignored -> {
            int call = calls.incrementAndGet();
            return fakeResponse(call < 3 ? 503 : 200, "{}", HttpHeaders.of(Map.of(), (k, v) -> true));
        }, policy);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(calls.get()).isEqualTo(3);
        assertThat(ProviderHttpSupport.isRetryableStatus(429)).isTrue();
        assertThat(ProviderHttpSupport.isRetryableStatus(400)).isFalse();
    }

    @Test
    void testProviderHttpSupportCapsRetryAfterDelay() {
        ProviderHttpSupport.RetryPolicy policy = new ProviderHttpSupport.RetryPolicy(
                2, Duration.ofMillis(50), Duration.ofMillis(500));
        HttpHeaders headers = HttpHeaders.of(Map.of("Retry-After", List.of("2")), (k, v) -> true);

        assertThat(ProviderHttpSupport.retryDelay(0, policy, headers)).isEqualTo(Duration.ofMillis(500));
        assertThat(ProviderHttpSupport.retryDelay(2, policy, HttpHeaders.of(Map.of(), (k, v) -> true)))
                .isEqualTo(Duration.ofMillis(200));
    }

    @Test
    void testProviderHttpSupportAppliesProviderSpecificRetryOverrides() {
        StreamOptions options = new StreamOptions(null, null, null, null, null, null, Map.of(),
                Duration.ofSeconds(9), 2, Map.of(), Map.of(
                "retryInitialDelayMs", 100,
                "maxRetryDelayMs", 2_000,
                "maxConcurrentRequests", 4,
                "providerRetryOverrides", Map.of(
                        "openai", Map.of(
                                "timeoutMs", 1_500,
                                "maxRetries", 6,
                                "baseDelayMs", 25,
                                "maxRetryDelayMs", 500,
                                "maxConcurrentRequests", 1
                        ),
                        "ollama", Map.of("maxRetries", 0)
                )
        ));

        ProviderHttpSupport.RetryPolicy openai = ProviderHttpSupport.retryPolicy("openai", options);
        ProviderHttpSupport.RetryPolicy ollama = ProviderHttpSupport.retryPolicy("ollama", options);
        ProviderHttpSupport.RetryPolicy other = ProviderHttpSupport.retryPolicy("anthropic", options);

        assertThat(openai.maxRetries()).isEqualTo(6);
        assertThat(openai.initialDelay()).isEqualTo(Duration.ofMillis(25));
        assertThat(openai.maxDelay()).isEqualTo(Duration.ofMillis(500));
        assertThat(openai.maxConcurrentRequests()).isEqualTo(1);
        assertThat(ProviderHttpSupport.requestTimeout("openai", options)).isEqualTo(Duration.ofMillis(1_500));
        assertThat(ollama.maxRetries()).isZero();
        assertThat(other.maxRetries()).isEqualTo(2);
        assertThat(other.initialDelay()).isEqualTo(Duration.ofMillis(100));
        assertThat(other.maxDelay()).isEqualTo(Duration.ofMillis(2_000));
        assertThat(other.maxConcurrentRequests()).isEqualTo(4);
        assertThat(ProviderHttpSupport.requestTimeout("anthropic", options)).isEqualTo(Duration.ofSeconds(9));
    }

    @Test
    void testOllamaProviderRefreshesLocalTags() {
        AtomicReference<URI> requestedUri = new AtomicReference<>();
        OllamaProvider provider = new OllamaProvider("http://localhost:11434/v1", request -> {
            requestedUri.set(request.uri());
            return fakeResponse(200, """
                    {
                      "models": [
                        {
                          "name": "local-qwen:latest",
                          "details": {
                            "family": "qwen",
                            "parameter_size": "14B"
                          }
                        },
                        { "model": "deepseek-r1:8b" }
                      ]
                    }
                    """, HttpHeaders.of(Map.of(), (k, v) -> true));
        });

        List<Model> refreshed = provider.refreshModels();

        assertThat(requestedUri.get()).hasToString("http://localhost:11434/api/tags");
        assertThat(refreshed).extracting(Model::modelId)
                .contains("llama3.1:8b", "local-qwen:latest", "deepseek-r1:8b");
        assertThat(refreshed).anySatisfy(model -> {
            assertThat(model.modelId()).isEqualTo("local-qwen:latest");
            assertThat(model.displayName()).isEqualTo("qwen 14B (local-qwen:latest)");
            assertThat(model.options()).containsEntry("discovered", true);
        });
        assertThat(refreshed).anySatisfy(model -> {
            assertThat(model.modelId()).isEqualTo("deepseek-r1:8b");
            assertThat(model.options()).containsEntry("reasoning", true);
        });
    }

    private List<AssistantMessageEvent> collectEvents(AssistantMessageEventStream stream) throws InterruptedException {
        EventCollector collector = subscribe(stream);
        collector.await();
        return collector.events();
    }

    private EventCollector subscribe(AssistantMessageEventStream stream) {
        EventCollector collector = new EventCollector(new ArrayList<>(), new CountDownLatch(1));
        stream.publisher().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(AssistantMessageEvent item) {
                collector.events().add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                collector.latch().countDown();
            }

            @Override
            public void onComplete() {
                collector.latch().countDown();
            }
        });
        return collector;
    }

    private HttpResponse<InputStream> fakeResponse(int status, String body, HttpHeaders headers) {
        return new HttpResponse<>() {
            @Override
            public int statusCode() {
                return status;
            }

            @Override
            public HttpRequest request() {
                return null;
            }

            @Override
            public Optional<HttpResponse<InputStream>> previousResponse() {
                return Optional.empty();
            }

            @Override
            public HttpHeaders headers() {
                return headers;
            }

            @Override
            public InputStream body() {
                return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
            }

            @Override
            public Optional<SSLSession> sslSession() {
                return Optional.empty();
            }

            @Override
            public URI uri() {
                return URI.create("https://example.test");
            }

            @Override
            public HttpClient.Version version() {
                return HttpClient.Version.HTTP_1_1;
            }
        };
    }

    @Test
    void testResponseIdAndThinkingInStreamAccumulators() {
        OpenAiCompatibleProvider.OpenAiStreamAccumulator acc = new OpenAiCompatibleProvider.OpenAiStreamAccumulator();
        assertThat(acc.finalContents()).isEmpty();

        GeminiProvider.GeminiAccumulator gAcc = new GeminiProvider.GeminiAccumulator();
        assertThat(gAcc.finalContents()).isEmpty();
    }

    @Test
    void testProviderRegistryNewDefaults() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.registerDefaults();

        assertThat(registry.provider("deepseek")).isPresent();
        assertThat(registry.provider("together")).isPresent();
        assertThat(registry.provider("openrouter")).isPresent();
        assertThat(registry.provider("cerebras")).isPresent();
        assertThat(registry.provider("fireworks")).isPresent();
        assertThat(registry.provider("moonshot")).isPresent();
        assertThat(registry.provider("zai")).isPresent();

        assertThat(registry.provider("kimi")).isPresent();
        assertThat(registry.provider("zhipu")).isPresent();
        assertThat(registry.provider("glm")).isPresent();
    }

    private record EventCollector(List<AssistantMessageEvent> events, CountDownLatch latch) {
        void await() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
        }
    }
}
