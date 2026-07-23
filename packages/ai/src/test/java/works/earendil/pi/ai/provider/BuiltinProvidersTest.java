package works.earendil.pi.ai.provider;

import org.junit.jupiter.api.Test;
import works.earendil.pi.ai.model.CacheRetention;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.model.ToolChoice;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.common.json.JsonCodec;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.io.IOException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
                    .contains("grok-4.3", "grok-4.5")
                    .doesNotContain("grok-code-fast-1");
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
    void testOpenAiCompatibleProviderSendsToolChoiceAndSafeEmptyToolOutput() {
        GroqProvider provider = new GroqProvider();
        Model model = provider.models().getFirst();
        Context context = new Context(List.of(
                new Message.ToolResult("call-empty", "bash", List.of(), false, null, Instant.now()),
                new Message.ToolResult("call-image", "read",
                        List.of(new Content.Image("image/png", "aW1hZ2U=", null)), false, null, Instant.now())),
                null, List.of(), ThinkingLevel.OFF);
        StreamOptions options = new StreamOptions(null, null, null, null, null, null, Map.of(),
                Duration.ofSeconds(5), 1, Map.of(), Map.of(), null, new ToolChoice.Named("bash"));

        var body = OpenAiCompatibleProvider.buildChatCompletionsBody(model, context, options);

        assertThat(body.at("/tool_choice/type").asText()).isEqualTo("function");
        assertThat(body.at("/tool_choice/function/name").asText()).isEqualTo("bash");
        assertThat(body.at("/messages/0/content").asText()).isEqualTo("(no tool output)");
        assertThat(body.at("/messages/1/content").asText()).isEqualTo("(see attached image)");
    }

    @Test
    void testXaiGrok45BuildsAndParsesResponsesApiPayloads() throws Exception {
        XaiProvider provider = new XaiProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("grok-4.5"))
                .findFirst().orElseThrow();
        Context context = new Context(List.of(new Message.User(List.of(new Content.Text("hello")), Instant.now())),
                "Be careful.", List.of(), ThinkingLevel.MAX);
        StreamOptions options = new StreamOptions(null, null, "test-key", null, CacheRetention.LONG,
                "pi-session-123", Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());

        var body = OpenAiResponsesSupport.buildRequestBody(model, context, options);

        assertThat(model.api()).isEqualTo("openai-responses");
        assertThat(body.path("store").asBoolean()).isFalse();
        assertThat(body.path("prompt_cache_key").asText()).isEqualTo("pi-session-123");
        assertThat(body.at("/reasoning/effort").asText()).isEqualTo("high");
        assertThat(body.at("/include/0").asText()).isEqualTo("reasoning.encrypted_content");
        assertThat(body.at("/input/0/role").asText()).isEqualTo("developer");

        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        EventCollector collector = subscribe(stream);
        OpenAiResponsesSupport.ResponsesAccumulator accumulator = new OpenAiResponsesSupport.ResponsesAccumulator();
        OpenAiResponsesSupport.handleEvent(JsonCodec.parse("""
                {"type":"response.output_item.added","output_index":0,"item":{"type":"message","id":"msg_1"}}
                """), accumulator, stream);
        OpenAiResponsesSupport.handleEvent(JsonCodec.parse("""
                {"type":"response.output_text.delta","output_index":0,"delta":"done"}
                """), accumulator, stream);
        OpenAiResponsesSupport.handleEvent(JsonCodec.parse("""
                {"type":"response.output_item.added","output_index":1,
                 "item":{"type":"function_call","id":"fc_1","call_id":"call_1","name":"bash"}}
                """), accumulator, stream);
        OpenAiResponsesSupport.handleEvent(JsonCodec.parse("""
                {"type":"response.function_call_arguments.done","output_index":1,"arguments":"{\\\"command\\\":\\\"pwd\\\"}"}
                """), accumulator, stream);
        OpenAiResponsesSupport.handleEvent(JsonCodec.parse("""
                {"type":"response.completed","response":{"id":"resp_1","status":"completed","output":[],
                 "usage":{"input_tokens":20,"output_tokens":8,
                 "input_tokens_details":{"cached_tokens":5,"cache_write_tokens":2},
                 "output_tokens_details":{"reasoning_tokens":3}}}}
                """), accumulator, stream);
        stream.close();
        collector.await();

        assertThat(accumulator.hasTerminalEvent()).isTrue();
        assertThat(accumulator.usage()).isEqualTo(new Usage(13, 8, 2, 5, 3));
        assertThat(accumulator.stopReason()).isEqualTo(StopReason.TOOL_USE);
        assertThat(accumulator.finalContents()).contains(new Content.Text("done"));
        assertThat(accumulator.finalContents()).anySatisfy(content -> {
            assertThat(content).isInstanceOf(Content.ToolCall.class);
            Content.ToolCall call = (Content.ToolCall) content;
            assertThat(call.id()).isEqualTo("call_1");
            assertThat(call.input().path("command").asText()).isEqualTo("pwd");
        });
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
    void testOpenAiCompatibleProviderParsesCacheReasoningAndChoiceUsage() throws Exception {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        EventCollector collector = subscribe(stream);
        OpenAiCompatibleProvider.OpenAiStreamAccumulator accumulator = new OpenAiCompatibleProvider.OpenAiStreamAccumulator();

        OpenAiCompatibleProvider.handleChatCompletionsChunk(JsonCodec.parse("""
                {
                  "choices": [{
                    "delta": {"content":"ok"},
                    "usage": {
                      "prompt_tokens": 30,
                      "completion_tokens": 9,
                      "prompt_tokens_details": {"cached_tokens": 10, "cache_write_tokens": 4},
                      "completion_tokens_details": {"reasoning_tokens": 3}
                    }
                  }]
                }
                """), accumulator, stream);
        stream.close();
        collector.await();

        assertThat(accumulator.usage()).isEqualTo(new Usage(16, 9, 4, 10, 3));
        assertThat(accumulator.usage().totalTokens()).isEqualTo(39);
        assertThat(collector.events()).hasAtLeastOneElementOfType(AssistantMessageEvent.UsageDelta.class);
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
        assertThat(ProviderHttpSupport.isRetryableStatus(524)).isTrue();
        assertThat(ProviderHttpSupport.isRetryableStatus(400)).isFalse();
    }

    @Test
    void testAssistantCallRetryHandlesTransientFailuresAndStopsOnPermanentFailure() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        String result = AssistantCallRetry.execute(attempt -> {
            if (calls.getAndIncrement() < 2) {
                throw new IOException("early EOF");
            }
            return "ok";
        }, new AssistantCallRetry.Policy(2, Duration.ZERO, Duration.ZERO), null);

        assertThat(result).isEqualTo("ok");
        assertThat(calls.get()).isEqualTo(3);
        assertThatThrownBy(() -> AssistantCallRetry.execute(attempt -> {
            throw new IllegalArgumentException("invalid request");
        }, new AssistantCallRetry.Policy(4, Duration.ZERO, Duration.ZERO), null))
                .isInstanceOf(IllegalArgumentException.class);
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
        assertThat(registry.provider("moonshotai")).isPresent();
        assertThat(registry.provider("moonshotai-cn")).isPresent();
        assertThat(registry.provider("qwen-token-plan")).hasValueSatisfying(provider ->
                assertThat(provider.models()).extracting(Model::modelId)
                        .contains("qwen3.8-max-preview", "kimi-k2.7-code", "deepseek-v4-pro"));
        assertThat(registry.provider("qwen-token-plan-cn")).isPresent();
        assertThat(registry.provider("zai")).isPresent();

        assertThat(registry.provider("kimi")).isPresent();
        assertThat(registry.provider("zhipu")).isPresent();
        assertThat(registry.provider("glm")).isPresent();
    }

    @Test
    void testBedrockProviderDoesNotReturnStubbedAssistantMessage() throws Exception {
        BedrockProvider provider = new BedrockProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-5-haiku-20241022-v1:0"))
                .findFirst()
                .orElseThrow();
        Context context = new Context(List.of(new Message.User(List.of(new Content.Text("hello")), Instant.now())),
                "system", List.of(), ThinkingLevel.OFF);
        StreamOptions options = new StreamOptions(null, null, "aws-access-key-id", null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());

        List<AssistantMessageEvent> events = collectEvents(provider.stream(model, context, options));

        assertThat(events).hasAtLeastOneElementOfType(AssistantMessageEvent.Start.class);
        assertThat(events).hasAtLeastOneElementOfType(AssistantMessageEvent.Error.class);
        assertThat(events).noneMatch(event -> event instanceof AssistantMessageEvent.ContentDelta contentDelta
                && contentDelta.content() instanceof Content.Text text
                && text.text().contains("Hello from AWS Bedrock"));
        assertThat(events).noneMatch(event -> event instanceof AssistantMessageEvent.End);
        assertThat(events.stream()
                .filter(AssistantMessageEvent.Error.class::isInstance)
                .map(AssistantMessageEvent.Error.class::cast)
                .map(AssistantMessageEvent.Error::message))
                .anyMatch(message -> message.contains("Bedrock Converse API is not implemented"));
    }

    @Test
    void testBedrockProviderDetectsCredentialSources() throws Exception {
        BedrockProvider provider = new BedrockProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-5-haiku-20241022-v1:0"))
                .findFirst()
                .orElseThrow();
        Context context = new Context(List.of(new Message.User(List.of(new Content.Text("hello")), Instant.now())),
                "system", List.of(), ThinkingLevel.OFF);
        StreamOptions profileOptions = new StreamOptions(null, null, null, null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, Map.of("AWS_PROFILE", "dev"), Map.of());
        StreamOptions blockedAmbientOptions = new StreamOptions(null, null, null, null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, emptyAwsCredentialEnv(), Map.of());

        assertThat(BedrockProvider.hasBedrockCredentials(profileOptions)).isTrue();
        assertThat(BedrockProvider.hasBedrockCredentials(new StreamOptions(null, null, null, null, null,
                null, Map.of(), Duration.ofSeconds(5), 1,
                Map.of("AWS_ACCESS_KEY_ID", "key", "AWS_SECRET_ACCESS_KEY", "secret"), Map.of()))).isTrue();
        assertThat(BedrockProvider.hasBedrockCredentials(new StreamOptions(null, null, null, null, null,
                null, Map.of(), Duration.ofSeconds(5), 1,
                Map.of("AWS_BEARER_TOKEN_BEDROCK", "token"), Map.of()))).isTrue();
        assertThat(BedrockProvider.hasBedrockCredentials(new StreamOptions(null, null, null, null, null,
                null, Map.of(), Duration.ofSeconds(5), 1,
                Map.of("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", "/v2/credentials/id"), Map.of()))).isTrue();
        assertThat(BedrockProvider.hasBedrockCredentials(new StreamOptions(null, null, null, null, null,
                null, Map.of(), Duration.ofSeconds(5), 1,
                Map.of("AWS_WEB_IDENTITY_TOKEN_FILE", "/token"), Map.of()))).isTrue();
        assertThat(BedrockProvider.hasBedrockCredentials(new StreamOptions(null, null, null, null, null,
                null, Map.of(), Duration.ofSeconds(5), 1,
                Map.of("AWS_BEDROCK_SKIP_AUTH", "1"), Map.of()))).isTrue();
        assertThat(BedrockProvider.hasBedrockCredentials(blockedAmbientOptions)).isFalse();

        List<AssistantMessageEvent> profileEvents = collectEvents(provider.stream(model, context, profileOptions));
        assertThat(profileEvents.stream()
                .filter(AssistantMessageEvent.Error.class::isInstance)
                .map(AssistantMessageEvent.Error.class::cast)
                .map(AssistantMessageEvent.Error::message))
                .anyMatch(message -> message.contains("Bedrock Converse API is not implemented"));
        assertThat(profileEvents.stream()
                .filter(AssistantMessageEvent.Error.class::isInstance)
                .map(AssistantMessageEvent.Error.class::cast)
                .map(AssistantMessageEvent.Error::message))
                .noneMatch(message -> message.contains("Missing AWS credentials"));
    }

    @Test
    void testBedrockProviderBuildsConverseRequestBody() throws Exception {
        BedrockProvider provider = new BedrockProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-7-sonnet-20250219-v1:0"))
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
        Context context = new Context(List.of(
                new Message.User(List.of(
                        new Content.Text("Inspect this"),
                        new Content.Image("image/png", "aW1hZ2U=", null)
                ), Instant.now()),
                new Message.Assistant(List.of(
                        new Content.Text("I'll read it."),
                        new Content.ToolCall("call:1", "read", JsonCodec.parse("{\"path\":\"README.md\"}"), null)
                ), "amazon-bedrock", model.modelId(), StopReason.TOOL_USE,
                        new Usage(0, 0, 0, 0, 0), null, Instant.now()),
                new Message.ToolResult("call:1", "read", List.of(new Content.Text("contents")), false, null, Instant.now())
        ), "Be concise.", List.of(tool), ThinkingLevel.OFF);
        StreamOptions options = new StreamOptions(0.2, 2048, "aws-access-key-id", null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, Map.of(),
                Map.of("requestMetadata", Map.of("project", "pi-java", "cost-center", 42)));

        var body = BedrockProvider.buildConverseRequestBody(model, context, options);

        assertThat(body.path("modelId").asText()).isEqualTo(model.modelId());
        assertThat(body.at("/system/0/text").asText()).isEqualTo("Be concise.");
        assertThat(body.at("/messages/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/0/content/0/text").asText()).isEqualTo("Inspect this");
        assertThat(body.at("/messages/0/content/1/image/format").asText()).isEqualTo("png");
        assertThat(body.at("/messages/0/content/1/image/source/bytes").asText()).isEqualTo("aW1hZ2U=");
        assertThat(body.at("/messages/1/role").asText()).isEqualTo("assistant");
        assertThat(body.at("/messages/1/content/1/toolUse/toolUseId").asText()).isEqualTo("call_1");
        assertThat(body.at("/messages/1/content/1/toolUse/name").asText()).isEqualTo("read");
        assertThat(body.at("/messages/1/content/1/toolUse/input/path").asText()).isEqualTo("README.md");
        assertThat(body.at("/messages/2/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/2/content/0/toolResult/toolUseId").asText()).isEqualTo("call_1");
        assertThat(body.at("/messages/2/content/0/toolResult/status").asText()).isEqualTo("success");
        assertThat(body.at("/messages/2/content/0/toolResult/content/0/text").asText()).isEqualTo("contents");
        assertThat(body.at("/inferenceConfig/temperature").asDouble()).isEqualTo(0.2);
        assertThat(body.at("/inferenceConfig/maxTokens").asInt()).isEqualTo(2048);
        assertThat(body.at("/toolConfig/tools/0/toolSpec/name").asText()).isEqualTo("read");
        assertThat(body.at("/toolConfig/tools/0/toolSpec/inputSchema/json/properties/path/type").asText())
                .isEqualTo("string");
        assertThat(body.at("/toolConfig/toolChoice/auto").isObject()).isTrue();
        assertThat(body.at("/requestMetadata/project").asText()).isEqualTo("pi-java");
        assertThat(body.at("/requestMetadata/cost-center").asText()).isEqualTo("42");
    }

    @Test
    void testBedrockProviderCombinesConsecutiveToolResults() {
        BedrockProvider provider = new BedrockProvider();
        Model model = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-7-sonnet-20250219-v1:0"))
                .findFirst()
                .orElseThrow();
        Context context = new Context(List.of(
                new Message.User(List.of(new Content.Text("run tools")), Instant.now()),
                new Message.Assistant(List.of(
                        new Content.ToolCall("call:1", "read", JsonCodec.parse("{\"path\":\"a\"}"), null),
                        new Content.ToolCall("call:2", "read", JsonCodec.parse("{\"path\":\"b\"}"), null)
                ), "amazon-bedrock", model.modelId(), StopReason.TOOL_USE,
                        new Usage(0, 0, 0, 0, 0), null, Instant.now()),
                new Message.ToolResult("call:1", "read", List.of(new Content.Text("a")), false, null, Instant.now()),
                new Message.ToolResult("call:2", "read", List.of(new Content.Text("b")), true, null, Instant.now()),
                new Message.Assistant(List.of(new Content.Text("after")), "amazon-bedrock", model.modelId(),
                        StopReason.STOP, new Usage(0, 0, 0, 0, 0), null, Instant.now()),
                new Message.ToolResult("call:3", "read", List.of(new Content.Text("c")), false, null, Instant.now())
        ), null, List.of(), ThinkingLevel.OFF);

        var body = BedrockProvider.buildConverseRequestBody(model, context,
                new StreamOptions(null, null, "aws-access-key-id", null, CacheRetention.NONE,
                        null, Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of()));

        assertThat(body.path("messages").size()).isEqualTo(5);
        assertThat(body.at("/messages/2/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/2/content/0/toolResult/toolUseId").asText()).isEqualTo("call_1");
        assertThat(body.at("/messages/2/content/0/toolResult/status").asText()).isEqualTo("success");
        assertThat(body.at("/messages/2/content/1/toolResult/toolUseId").asText()).isEqualTo("call_2");
        assertThat(body.at("/messages/2/content/1/toolResult/status").asText()).isEqualTo("error");
        assertThat(body.at("/messages/4/role").asText()).isEqualTo("user");
        assertThat(body.at("/messages/4/content/0/toolResult/toolUseId").asText()).isEqualTo("call_3");
        assertThat(body.at("/messages/4/content/1/toolResult/toolUseId").isMissingNode()).isTrue();
    }

    @Test
    void testBedrockProviderBuildsThinkingRequestFieldsForClaudeModels() {
        BedrockProvider provider = new BedrockProvider();
        Model claudeModel = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-7-sonnet-20250219-v1:0"))
                .findFirst()
                .orElseThrow();
        Model titanModel = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("amazon.titan-text-premier-v1:0"))
                .findFirst()
                .orElseThrow();
        Context thinkingContext = new Context(List.of(
                new Message.User(List.of(new Content.Text("Think carefully")), Instant.now())
        ), null, List.of(), ThinkingLevel.MEDIUM);
        StreamOptions options = new StreamOptions(null, null, "aws-access-key-id", null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, Map.of(),
                Map.of("thinkingDisplay", "omitted"));

        var claudeBody = BedrockProvider.buildConverseRequestBody(claudeModel, thinkingContext, options);
        var titanBody = BedrockProvider.buildConverseRequestBody(titanModel, thinkingContext, options);

        assertThat(claudeModel.options()).containsEntry("reasoning", true);
        assertThat(titanModel.options()).doesNotContainKey("reasoning");
        assertThat(claudeBody.at("/additionalModelRequestFields/thinking/type").asText()).isEqualTo("enabled");
        assertThat(claudeBody.at("/additionalModelRequestFields/thinking/budget_tokens").asInt()).isEqualTo(8192);
        assertThat(claudeBody.at("/additionalModelRequestFields/thinking/display").asText()).isEqualTo("omitted");
        assertThat(claudeBody.at("/additionalModelRequestFields/anthropic_beta/0").asText())
                .isEqualTo("interleaved-thinking-2025-05-14");
        assertThat(titanBody.has("additionalModelRequestFields")).isFalse();
    }

    @Test
    void testBedrockProviderFallsBackMissingClaudeThinkingSignatureToText() {
        BedrockProvider provider = new BedrockProvider();
        Model claudeModel = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-7-sonnet-20250219-v1:0"))
                .findFirst()
                .orElseThrow();
        Model titanModel = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("amazon.titan-text-premier-v1:0"))
                .findFirst()
                .orElseThrow();
        Context claudeContext = new Context(List.of(
                new Message.Assistant(List.of(
                        new Content.Thinking("partial thinking", null),
                        new Content.Thinking("signed thinking", "sig-1")
                ), "amazon-bedrock", claudeModel.modelId(), StopReason.STOP,
                        new Usage(0, 0, 0, 0, 0), null, Instant.now())
        ), null, List.of(), ThinkingLevel.OFF);
        Context titanContext = new Context(List.of(
                new Message.Assistant(List.of(new Content.Thinking("plain reasoning", null)),
                        "amazon-bedrock", titanModel.modelId(), StopReason.STOP,
                        new Usage(0, 0, 0, 0, 0), null, Instant.now())
        ), null, List.of(), ThinkingLevel.OFF);

        var claudeBody = BedrockProvider.buildConverseRequestBody(claudeModel, claudeContext, StreamOptions.defaults());
        var titanBody = BedrockProvider.buildConverseRequestBody(titanModel, titanContext, StreamOptions.defaults());

        assertThat(claudeBody.at("/messages/0/content/0/text").asText()).isEqualTo("partial thinking");
        assertThat(claudeBody.at("/messages/0/content/0/reasoningContent").isMissingNode()).isTrue();
        assertThat(claudeBody.at("/messages/0/content/1/reasoningContent/reasoningText/text").asText())
                .isEqualTo("signed thinking");
        assertThat(claudeBody.at("/messages/0/content/1/reasoningContent/reasoningText/signature").asText())
                .isEqualTo("sig-1");
        assertThat(titanBody.at("/messages/0/content/0/reasoningContent/reasoningText/text").asText())
                .isEqualTo("plain reasoning");
        assertThat(titanBody.at("/messages/0/content/0/reasoningContent/reasoningText/signature").isMissingNode())
                .isTrue();
    }

    @Test
    void testBedrockProviderBuildsSystemCachePointForSupportedClaudeModels() {
        BedrockProvider provider = new BedrockProvider();
        Model claude37 = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-7-sonnet-20250219-v1:0"))
                .findFirst()
                .orElseThrow();
        Model claude35Sonnet = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-5-sonnet-20241022-v2:0"))
                .findFirst()
                .orElseThrow();
        Context context = new Context(List.of(
                new Message.User(List.of(new Content.Text("hello")), Instant.now())
        ), "Cache this system prompt.", List.of(), ThinkingLevel.OFF);
        StreamOptions longCache = new StreamOptions(null, null, "aws-access-key-id", null, CacheRetention.LONG,
                null, Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());
        StreamOptions noCache = new StreamOptions(null, null, "aws-access-key-id", null, CacheRetention.NONE,
                null, Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());
        StreamOptions forcedCache = new StreamOptions(null, null, "aws-access-key-id", null, CacheRetention.SHORT,
                null, Map.of(), Duration.ofSeconds(5), 1, Map.of("AWS_BEDROCK_FORCE_CACHE", "1"), Map.of());

        var longCacheBody = BedrockProvider.buildConverseRequestBody(claude37, context, longCache);
        var noCacheBody = BedrockProvider.buildConverseRequestBody(claude37, context, noCache);
        var forcedCacheBody = BedrockProvider.buildConverseRequestBody(claude35Sonnet, context, forcedCache);

        assertThat(longCacheBody.at("/system/0/text").asText()).isEqualTo("Cache this system prompt.");
        assertThat(longCacheBody.at("/system/1/cachePoint/type").asText()).isEqualTo("default");
        assertThat(longCacheBody.at("/system/1/cachePoint/ttl").asText()).isEqualTo("1h");
        assertThat(noCacheBody.path("system").size()).isEqualTo(1);
        assertThat(noCacheBody.at("/system/0/text").asText()).isEqualTo("Cache this system prompt.");
        assertThat(forcedCacheBody.at("/system/1/cachePoint/type").asText()).isEqualTo("default");
        assertThat(forcedCacheBody.at("/system/1/cachePoint/ttl").isMissingNode()).isTrue();
    }

    @Test
    void testBedrockProviderAddsCachePointToLastUserMessage() {
        BedrockProvider provider = new BedrockProvider();
        Model claude37 = provider.models().stream()
                .filter(candidate -> candidate.modelId().equals("us.anthropic.claude-3-7-sonnet-20250219-v1:0"))
                .findFirst()
                .orElseThrow();
        Context endsWithUser = new Context(List.of(
                new Message.User(List.of(new Content.Text("first")), Instant.now()),
                new Message.Assistant(List.of(new Content.Text("middle")), "amazon-bedrock",
                        claude37.modelId(), StopReason.STOP, new Usage(0, 0, 0, 0, 0), null, Instant.now()),
                new Message.User(List.of(new Content.Text("last")), Instant.now())
        ), null, List.of(), ThinkingLevel.OFF);
        Context endsWithAssistant = new Context(List.of(
                new Message.User(List.of(new Content.Text("first")), Instant.now()),
                new Message.Assistant(List.of(new Content.Text("last")), "amazon-bedrock",
                        claude37.modelId(), StopReason.STOP, new Usage(0, 0, 0, 0, 0), null, Instant.now())
        ), null, List.of(), ThinkingLevel.OFF);
        StreamOptions longCache = new StreamOptions(null, null, "aws-access-key-id", null, CacheRetention.LONG,
                null, Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());
        StreamOptions noCache = new StreamOptions(null, null, "aws-access-key-id", null, CacheRetention.NONE,
                null, Map.of(), Duration.ofSeconds(5), 1, Map.of(), Map.of());

        var userBody = BedrockProvider.buildConverseRequestBody(claude37, endsWithUser, longCache);
        var noCacheBody = BedrockProvider.buildConverseRequestBody(claude37, endsWithUser, noCache);
        var assistantBody = BedrockProvider.buildConverseRequestBody(claude37, endsWithAssistant, longCache);

        assertThat(userBody.at("/messages/0/content/1/cachePoint/type").isMissingNode()).isTrue();
        assertThat(userBody.at("/messages/2/content/0/text").asText()).isEqualTo("last");
        assertThat(userBody.at("/messages/2/content/1/cachePoint/type").asText()).isEqualTo("default");
        assertThat(userBody.at("/messages/2/content/1/cachePoint/ttl").asText()).isEqualTo("1h");
        assertThat(noCacheBody.at("/messages/2/content/1/cachePoint/type").isMissingNode()).isTrue();
        assertThat(assistantBody.at("/messages/1/content/1/cachePoint/type").isMissingNode()).isTrue();
    }

    @Test
    void testBedrockProviderResolvesRegionForFutureConverseClient() {
        Model arnModel = new Model("amazon-bedrock",
                "arn:aws-us-gov:bedrock:us-gov-west-1:123456789012:inference-profile/example",
                "ARN model", "https://bedrock-runtime.us-east-1.amazonaws.com",
                200000, 64000, true, true, Map.of());
        StreamOptions explicitRegion = new StreamOptions(null, null, "aws-access-key-id", null, null, null,
                Map.of(), Duration.ofSeconds(5), 1, Map.of("AWS_REGION", "eu-central-1"),
                Map.of("region", "ap-southeast-2"));
        Model endpointModel = new Model("amazon-bedrock", "anthropic.claude-sonnet-4-20250514-v1:0",
                "Endpoint model", "https://bedrock-runtime.eu-west-3.amazonaws.com",
                200000, 64000, true, true, Map.of());
        Model customEndpointModel = new Model("amazon-bedrock", "anthropic.claude-sonnet-4-20250514-v1:0",
                "Custom endpoint model", "https://example.internal",
                200000, 64000, true, true, Map.of());
        StreamOptions noAmbientRegion = new StreamOptions(null, null, "aws-access-key-id", null, null, null,
                Map.of(), Duration.ofSeconds(5), 1,
                Map.of("AWS_REGION", "", "AWS_DEFAULT_REGION", ""), Map.of());

        assertThat(BedrockProvider.resolveBedrockRegion(arnModel, explicitRegion)).isEqualTo("us-gov-west-1");
        assertThat(BedrockProvider.resolveBedrockRegion(endpointModel, explicitRegion)).isEqualTo("ap-southeast-2");
        assertThat(BedrockProvider.resolveBedrockRegion(endpointModel, new StreamOptions(null, null,
                "aws-access-key-id", null, null, null, Map.of(), Duration.ofSeconds(5), 1,
                Map.of("AWS_DEFAULT_REGION", "sa-east-1"), Map.of()))).isEqualTo("sa-east-1");
        assertThat(BedrockProvider.resolveBedrockRegion(endpointModel, noAmbientRegion)).isEqualTo("eu-west-3");
        assertThat(BedrockProvider.resolveBedrockRegion(customEndpointModel, noAmbientRegion)).isEqualTo("us-east-1");
    }

    private static Map<String, String> emptyAwsCredentialEnv() {
        return Map.of(
                "AWS_PROFILE", "",
                "AWS_ACCESS_KEY_ID", "",
                "AWS_SECRET_ACCESS_KEY", "",
                "AWS_BEARER_TOKEN_BEDROCK", "",
                "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", "",
                "AWS_CONTAINER_CREDENTIALS_FULL_URI", "",
                "AWS_WEB_IDENTITY_TOKEN_FILE", "",
                "AWS_BEDROCK_SKIP_AUTH", ""
        );
    }

    private record EventCollector(List<AssistantMessageEvent> events, CountDownLatch latch) {
        void await() throws InterruptedException {
            latch.await(5, TimeUnit.SECONDS);
        }
    }
}
