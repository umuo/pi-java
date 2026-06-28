package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.common.json.JsonCodec;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class OpenAiProvider implements Provider {
    private static final String ID = "openai";
    private static final String DEFAULT_API = "https://api.openai.com/v1";

    private final List<Model> models = List.of(
            new Model(ID, "gpt-4o", "GPT-4o", DEFAULT_API, 128000, 16384, true, true, Map.of("baseUrl", DEFAULT_API)),
            new Model(ID, "gpt-4o-mini", "GPT-4o Mini", DEFAULT_API, 128000, 16384, true, true, Map.of("baseUrl", DEFAULT_API)),
            new Model(ID, "o3-mini", "o3-mini", DEFAULT_API, 200000, 100000, true, false, Map.of("baseUrl", DEFAULT_API)),
            new Model(ID, "o1", "o1", DEFAULT_API, 200000, 100000, true, true, Map.of("baseUrl", DEFAULT_API))
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Model> models() {
        return models;
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> {
            try {
                stream.emit(new AssistantMessageEvent.Start(ID, model.modelId()));

                String apiKey = options != null ? options.apiKey() : null;
                if (apiKey == null || apiKey.isBlank()) {
                    apiKey = System.getenv("OPENAI_API_KEY");
                }
                if (apiKey == null || apiKey.isBlank()) {
                    stream.emit(new AssistantMessageEvent.Error("Missing API key for provider: " + ID, new IllegalStateException("Missing API key")));
                    return;
                }

                String baseUrl = DEFAULT_API;
                if (model.options() != null && model.options().get("baseUrl") instanceof String s && !s.isBlank()) {
                    baseUrl = s;
                } else if (model.api() != null && model.api().startsWith("http")) {
                    baseUrl = model.api();
                }

                String endpoint = baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

                ObjectNode bodyNode = JsonCodec.mapper().createObjectNode();
                bodyNode.put("model", model.modelId());
                bodyNode.put("stream", true);
                if (options != null && options.temperature() != null) {
                    bodyNode.put("temperature", options.temperature());
                }
                if (options != null && options.maxTokens() != null) {
                    bodyNode.put("max_tokens", options.maxTokens());
                }

                var messagesArray = bodyNode.putArray("messages");
                if (context != null && context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
                    var sysObj = messagesArray.addObject();
                    sysObj.put("role", "system");
                    sysObj.put("content", context.systemPrompt());
                }
                if (context != null && context.messages() != null) {
                    for (Message msg : context.messages()) {
                        StringBuilder textBuf = new StringBuilder();
                        List<Content> contents = switch (msg) {
                            case Message.User u -> u.content();
                            case Message.Assistant a -> a.content();
                            case Message.ToolResult tr -> tr.content();
                        };
                        if (contents != null) {
                            for (Content c : contents) {
                                if (c instanceof Content.Text t) textBuf.append(t.text());
                            }
                        }
                        var msgObj = messagesArray.addObject();
                        msgObj.put("role", msg.role());
                        msgObj.put("content", textBuf.toString());
                    }
                }

                String jsonBody = JsonCodec.stringify(bodyNode);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));

                if (options != null && options.headers() != null) {
                    options.headers().forEach((k, v) -> {
                        if (k != null && v != null && !k.equalsIgnoreCase("authorization") && !k.equalsIgnoreCase("content-type")) {
                            reqBuilder.header(k, v);
                        }
                    });
                }

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                HttpResponse<InputStream> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("HTTP " + response.statusCode() + " from " + endpoint + ": " + errBody);
                }

                StringBuilder fullContent = new StringBuilder();
                Usage finalUsage = new Usage(0, 0, 0, 0, 0);

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) continue;
                        if (line.equals("data: [DONE]")) break;
                        if (line.startsWith("data: ")) {
                            String jsonStr = line.substring(6).trim();
                            if (jsonStr.equals("[DONE]")) break;
                            try {
                                JsonNode chunkNode = JsonCodec.parse(jsonStr);
                                if (chunkNode.has("usage") && !chunkNode.get("usage").isNull()) {
                                    JsonNode uNode = chunkNode.get("usage");
                                    finalUsage = new Usage(
                                            uNode.path("prompt_tokens").asInt(0),
                                            uNode.path("completion_tokens").asInt(0),
                                            uNode.path("total_tokens").asInt(0),
                                            0, 0
                                    );
                                    stream.emit(new AssistantMessageEvent.UsageDelta(finalUsage));
                                }
                                JsonNode choices = chunkNode.get("choices");
                                if (choices != null && choices.isArray() && choices.size() > 0) {
                                    JsonNode delta = choices.get(0).get("delta");
                                    if (delta != null && delta.has("content") && !delta.get("content").isNull()) {
                                        String chunkText = delta.get("content").asText();
                                        if (!chunkText.isEmpty()) {
                                            fullContent.append(chunkText);
                                            stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Text(chunkText)));
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                stream.emit(new AssistantMessageEvent.End(new Message.Assistant(
                        List.of(new Content.Text(fullContent.toString())),
                        model.provider(), model.modelId(), StopReason.STOP, finalUsage, null, Instant.now()
                )));
            } catch (Exception e) {
                stream.emit(new AssistantMessageEvent.Error(e.getMessage(), e));
            } finally {
                stream.close();
            }
        });
        return stream;
    }
}
