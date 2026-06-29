package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.Tool;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

                if (context != null && context.tools() != null && !context.tools().isEmpty()) {
                    var toolsArray = bodyNode.putArray("tools");
                    for (Tool t : context.tools()) {
                        var toolObj = toolsArray.addObject();
                        toolObj.put("type", "function");
                        var funcObj = toolObj.putObject("function");
                        funcObj.put("name", t.name());
                        funcObj.put("description", t.description() != null ? t.description() : "");
                        funcObj.set("parameters", t.parameters() != null ? t.parameters() : JsonCodec.mapper().createObjectNode());
                    }
                }

                var messagesArray = bodyNode.putArray("messages");
                if (context != null && context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
                    var sysObj = messagesArray.addObject();
                    sysObj.put("role", "system");
                    sysObj.put("content", context.systemPrompt());
                }
                if (context != null && context.messages() != null) {
                    for (Message msg : context.messages()) {
                        var msgObj = messagesArray.addObject();
                        msgObj.put("role", msg.role());
                        if (msg instanceof Message.ToolResult tr) {
                            msgObj.put("tool_call_id", tr.toolCallId());
                            StringBuilder textBuf = new StringBuilder();
                            if (tr.content() != null) {
                                for (Content c : tr.content()) {
                                    if (c instanceof Content.Text t) textBuf.append(t.text());
                                }
                            }
                            msgObj.put("content", textBuf.toString());
                        } else if (msg instanceof Message.Assistant a) {
                            StringBuilder textBuf = new StringBuilder();
                            var toolCallsArray = JsonCodec.mapper().createArrayNode();
                            if (a.content() != null) {
                                for (Content c : a.content()) {
                                    if (c instanceof Content.Text t) {
                                        textBuf.append(t.text());
                                    } else if (c instanceof Content.ToolCall tc) {
                                        var tcObj = toolCallsArray.addObject();
                                        tcObj.put("id", tc.id());
                                        tcObj.put("type", "function");
                                        var fObj = tcObj.putObject("function");
                                        fObj.put("name", tc.name());
                                        fObj.put("arguments", tc.input() != null ? JsonCodec.stringify(tc.input()) : "{}");
                                    }
                                }
                            }
                            if (textBuf.length() > 0 || toolCallsArray.isEmpty()) {
                                msgObj.put("content", textBuf.toString());
                            } else {
                                msgObj.putNull("content");
                            }
                            if (!toolCallsArray.isEmpty()) {
                                msgObj.set("tool_calls", toolCallsArray);
                            }
                        } else {
                            StringBuilder textBuf = new StringBuilder();
                            if (msg instanceof Message.User u && u.content() != null) {
                                for (Content c : u.content()) {
                                    if (c instanceof Content.Text t) textBuf.append(t.text());
                                }
                            }
                            msgObj.put("content", textBuf.toString());
                        }
                    }
                }

                String jsonBody = JsonCodec.stringify(bodyNode);
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(ProviderHttpSupport.requestTimeout(ID, options))
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

                HttpClient client = ProviderHttpSupport.client();
                HttpResponse<InputStream> response = ProviderHttpSupport.sendWithRetries(ID, reqBuilder.build(),
                        request -> client.send(request, HttpResponse.BodyHandlers.ofInputStream()),
                        ProviderHttpSupport.retryPolicy(ID, options));
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("HTTP " + response.statusCode() + " from " + endpoint + ": " + errBody);
                }

                class ToolCallAccumulator {
                    String id;
                    String name;
                    StringBuilder arguments = new StringBuilder();
                }
                Map<Integer, ToolCallAccumulator> toolCallBuilders = new HashMap<>();
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
                                    if (delta != null) {
                                        if (delta.has("content") && !delta.get("content").isNull()) {
                                            String chunkText = delta.get("content").asText();
                                            if (!chunkText.isEmpty()) {
                                                fullContent.append(chunkText);
                                                stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Text(chunkText)));
                                            }
                                        }
                                        JsonNode toolCallsNode = delta.get("tool_calls");
                                        if (toolCallsNode != null && toolCallsNode.isArray()) {
                                            for (JsonNode tcNode : toolCallsNode) {
                                                int idx = tcNode.path("index").asInt(0);
                                                ToolCallAccumulator acc = toolCallBuilders.computeIfAbsent(idx, k -> new ToolCallAccumulator());
                                                if (tcNode.hasNonNull("id")) acc.id = tcNode.get("id").asText();
                                                JsonNode funcNode = tcNode.get("function");
                                                if (funcNode != null) {
                                                    if (funcNode.hasNonNull("name")) acc.name = funcNode.get("name").asText();
                                                    if (funcNode.hasNonNull("arguments")) acc.arguments.append(funcNode.get("arguments").asText());
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                List<Content> finalContents = new ArrayList<>();
                if (fullContent.length() > 0) {
                    finalContents.add(new Content.Text(fullContent.toString()));
                }
                List<Integer> sortedIndices = new ArrayList<>(toolCallBuilders.keySet());
                Collections.sort(sortedIndices);
                for (Integer idx : sortedIndices) {
                    ToolCallAccumulator acc = toolCallBuilders.get(idx);
                    String id = acc.id != null ? acc.id : "call_" + idx;
                    String name = acc.name != null ? acc.name : "unknown_tool";
                    JsonNode inputNode;
                    try {
                        inputNode = JsonCodec.parse(acc.arguments.toString());
                    } catch (Exception ex) {
                        inputNode = JsonCodec.mapper().createObjectNode();
                    }
                    Content.ToolCall tc = new Content.ToolCall(id, name, inputNode, List.of());
                    finalContents.add(tc);
                    stream.emit(new AssistantMessageEvent.ContentDelta(tc));
                }

                stream.emit(new AssistantMessageEvent.End(new Message.Assistant(
                        finalContents,
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
