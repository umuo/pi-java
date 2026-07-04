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

abstract class OpenAiCompatibleProvider implements Provider {
    private final String id;
    private final String defaultApi;
    private final String apiKeyEnvVar;
    private volatile List<Model> models;

    OpenAiCompatibleProvider(String id, String defaultApi, String apiKeyEnvVar, List<Model> models) {
        this.id = id;
        this.defaultApi = defaultApi;
        this.apiKeyEnvVar = apiKeyEnvVar;
        this.models = List.copyOf(models);
    }

    @Override
    public final String id() {
        return id;
    }

    @Override
    public final List<Model> models() {
        return models;
    }

    protected final void setModels(List<Model> models) {
        this.models = List.copyOf(models);
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> {
            try {
                stream.emit(new AssistantMessageEvent.Start(id, model.modelId()));

                String apiKey = resolveApiKey(options);
                if (apiKey == null || apiKey.isBlank()) {
                    stream.emit(new AssistantMessageEvent.Error("Missing API key for provider: " + id,
                            new IllegalStateException("Missing " + apiKeyEnvVar)));
                    return;
                }

                String endpoint = chatCompletionsEndpoint(model, defaultApi);
                JsonNode bodyNode = ProviderHttpSupport.applyBeforeProviderRequest(options, model,
                        buildChatCompletionsBody(model, context, options));
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(ProviderHttpSupport.requestTimeout(id, options))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(JsonCodec.stringify(bodyNode), StandardCharsets.UTF_8));

                if (options != null && options.headers() != null) {
                    options.headers().forEach((k, v) -> {
                        if (k != null && v != null
                                && !k.equalsIgnoreCase("authorization")
                                && !k.equalsIgnoreCase("content-type")) {
                            reqBuilder.header(k, v);
                        }
                    });
                }

                HttpClient client = ProviderHttpSupport.client();
                HttpResponse<InputStream> response = ProviderHttpSupport.sendWithRetries(id, reqBuilder.build(),
                        request -> client.send(request, HttpResponse.BodyHandlers.ofInputStream()),
                        ProviderHttpSupport.retryPolicy(id, options));
                ProviderHttpSupport.emitAfterProviderResponse(options, model, response);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("HTTP " + response.statusCode() + " from " + endpoint + ": " + errBody);
                }

                OpenAiStreamAccumulator accumulator = new OpenAiStreamAccumulator();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        if (line.equals("data: [DONE]")) {
                            break;
                        }
                        if (line.startsWith("data: ")) {
                            String jsonStr = line.substring(6).trim();
                            if (jsonStr.equals("[DONE]")) {
                                break;
                            }
                            handleChatCompletionsChunk(JsonCodec.parse(jsonStr), accumulator, stream);
                        }
                    }
                }

                stream.emit(new AssistantMessageEvent.End(new Message.Assistant(
                        accumulator.finalContents(),
                        model.provider(),
                        model.modelId(),
                        accumulator.stopReason(),
                        accumulator.usage(),
                        null,
                        Instant.now(),
                        accumulator.responseId
                )));
            } catch (Exception e) {
                stream.emit(new AssistantMessageEvent.Error(e.getMessage(), e));
            } finally {
                stream.close();
            }
        });
        return stream;
    }

    static ObjectNode buildChatCompletionsBody(Model model, Context context, StreamOptions options) {
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
                    msgObj.put("content", textFromContents(tr.content()));
                } else if (msg instanceof Message.Assistant assistant) {
                    appendAssistantContent(msgObj, assistant);
                } else {
                    String text = msg instanceof Message.User user ? textFromContents(user.content()) : "";
                    msgObj.put("content", text);
                }
            }
        }
        return bodyNode;
    }

    static void handleChatCompletionsChunk(JsonNode chunkNode, OpenAiStreamAccumulator accumulator,
                                           AssistantMessageEventStream stream) {
        if (chunkNode.has("usage") && !chunkNode.get("usage").isNull()) {
            JsonNode uNode = chunkNode.get("usage");
            Usage usage = new Usage(
                    uNode.path("prompt_tokens").asInt(0),
                    uNode.path("completion_tokens").asInt(0),
                    0,
                    0,
                    0
            );
            accumulator.usage = usage;
            stream.emit(new AssistantMessageEvent.UsageDelta(usage));
        }

        if (chunkNode.hasNonNull("id")) {
            accumulator.responseId = chunkNode.get("id").asText();
        }

        JsonNode choices = chunkNode.get("choices");
        if (choices == null || !choices.isArray() || choices.isEmpty()) {
            return;
        }
        JsonNode choice = choices.get(0);
        JsonNode delta = choice.get("delta");
        if (delta != null) {
            if (delta.has("reasoning_content") && !delta.get("reasoning_content").isNull()) {
                String reasoning = delta.get("reasoning_content").asText();
                if (!reasoning.isEmpty()) {
                    accumulator.thinking.append(reasoning);
                    stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Thinking(reasoning, null)));
                }
            }
            if (delta.has("content") && !delta.get("content").isNull()) {
                String chunkText = delta.get("content").asText();
                if (!chunkText.isEmpty()) {
                    accumulator.text.append(chunkText);
                    stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Text(chunkText)));
                }
            }
            JsonNode toolCallsNode = delta.get("tool_calls");
            if (toolCallsNode != null && toolCallsNode.isArray()) {
                for (JsonNode tcNode : toolCallsNode) {
                    int idx = tcNode.path("index").asInt(0);
                    ToolCallAccumulator acc = accumulator.toolCallBuilders.computeIfAbsent(idx, ignored -> new ToolCallAccumulator());
                    if (tcNode.hasNonNull("id")) {
                        acc.id = tcNode.get("id").asText();
                    }
                    JsonNode funcNode = tcNode.get("function");
                    if (funcNode != null) {
                        if (funcNode.hasNonNull("name")) {
                            acc.name = funcNode.get("name").asText();
                        }
                        if (funcNode.hasNonNull("arguments")) {
                            acc.arguments.append(funcNode.get("arguments").asText());
                        }
                    }
                }
            }
        }

        String finishReason = textValue(choice.get("finish_reason"));
        if (finishReason != null) {
            accumulator.stopReason = mapStopReason(finishReason);
        }
    }

    static final class OpenAiStreamAccumulator {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final Map<Integer, ToolCallAccumulator> toolCallBuilders = new HashMap<>();
        private Usage usage = new Usage(0, 0, 0, 0, 0);
        private StopReason stopReason = StopReason.STOP;
        private String responseId;

        List<Content> finalContents() {
            List<Content> finalContents = new ArrayList<>();
            if (!thinking.isEmpty()) {
                finalContents.add(new Content.Thinking(thinking.toString(), null));
            }
            if (!text.isEmpty()) {
                finalContents.add(new Content.Text(text.toString()));
            }
            List<Integer> sortedIndices = new ArrayList<>(toolCallBuilders.keySet());
            Collections.sort(sortedIndices);
            for (Integer idx : sortedIndices) {
                ToolCallAccumulator acc = toolCallBuilders.get(idx);
                JsonNode inputNode;
                try {
                    inputNode = JsonCodec.parse(acc.arguments.toString());
                } catch (Exception ex) {
                    inputNode = JsonCodec.mapper().createObjectNode();
                }
                finalContents.add(new Content.ToolCall(
                        acc.id != null ? acc.id : "call_" + idx,
                        acc.name != null ? acc.name : "unknown_tool",
                        inputNode,
                        List.of()));
            }
            return List.copyOf(finalContents);
        }

        Usage usage() {
            return usage;
        }

        StopReason stopReason() {
            return toolCallBuilders.isEmpty() ? stopReason : StopReason.TOOL_USE;
        }
    }

    private String resolveApiKey(StreamOptions options) {
        if (options != null && options.apiKey() != null && !options.apiKey().isBlank()) {
            return options.apiKey();
        }
        if (options != null && options.env() != null && options.env().get(apiKeyEnvVar) != null) {
            return options.env().get(apiKeyEnvVar);
        }
        return System.getenv(apiKeyEnvVar);
    }

    private static String chatCompletionsEndpoint(Model model, String defaultApi) {
        String baseUrl = defaultApi;
        if (model.options() != null && model.options().get("baseUrl") instanceof String s && !s.isBlank()) {
            baseUrl = s;
        } else if (model.api() != null && model.api().startsWith("http")) {
            baseUrl = model.api();
        }
        return baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";
    }

    private static void appendAssistantContent(ObjectNode msgObj, Message.Assistant assistant) {
        StringBuilder textBuf = new StringBuilder();
        var toolCallsArray = JsonCodec.mapper().createArrayNode();
        if (assistant.content() != null) {
            for (Content c : assistant.content()) {
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
        if (!textBuf.isEmpty() || toolCallsArray.isEmpty()) {
            msgObj.put("content", textBuf.toString());
        } else {
            msgObj.putNull("content");
        }
        if (!toolCallsArray.isEmpty()) {
            msgObj.set("tool_calls", toolCallsArray);
        }
    }

    private static String textFromContents(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        StringBuilder textBuf = new StringBuilder();
        for (Content c : contents) {
            if (c instanceof Content.Text t) {
                textBuf.append(t.text());
            }
        }
        return textBuf.toString();
    }

    private static StopReason mapStopReason(String reason) {
        return switch (reason) {
            case "length" -> StopReason.LENGTH;
            case "tool_calls", "function_call" -> StopReason.TOOL_USE;
            default -> StopReason.STOP;
        };
    }

    private static String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private static final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();
    }
}
