package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.ai.model.CacheRetention;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.model.ToolChoice;
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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;

final class OpenAiResponsesSupport {
    private OpenAiResponsesSupport() {
    }

    static AssistantMessageEventStream stream(String providerId, String defaultApi, String apiKeyEnvVar,
                                               Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> {
            try {
                stream.emit(new AssistantMessageEvent.Start(providerId, model.modelId()));
                String apiKey = resolveApiKey(apiKeyEnvVar, options);
                if (apiKey == null || apiKey.isBlank()) {
                    throw new IllegalStateException("Missing " + apiKeyEnvVar);
                }

                String endpoint = responsesEndpoint(model, defaultApi);
                JsonNode body = ProviderHttpSupport.applyBeforeProviderRequest(options, model,
                        buildRequestBody(model, context, options));
                HttpRequest.Builder request = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(ProviderHttpSupport.requestTimeout(providerId, options))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(JsonCodec.stringify(body), StandardCharsets.UTF_8));
                if (options != null && options.sessionId() != null && !options.sessionId().isBlank()) {
                    request.header("session_id", options.sessionId());
                    request.header("x-client-request-id", options.sessionId());
                }
                if (options != null && options.headers() != null) {
                    options.headers().forEach((key, value) -> {
                        if (key != null && value != null && !key.equalsIgnoreCase("authorization")
                                && !key.equalsIgnoreCase("content-type")
                                && !key.equalsIgnoreCase("session_id")
                                && !key.equalsIgnoreCase("x-client-request-id")) {
                            request.header(key, value);
                        }
                    });
                }

                HttpClient client = ProviderHttpSupport.client();
                HttpResponse<InputStream> response = ProviderHttpSupport.sendWithRetries(providerId, request.build(),
                        req -> client.send(req, HttpResponse.BodyHandlers.ofInputStream()),
                        ProviderHttpSupport.retryPolicy(providerId, options));
                ProviderHttpSupport.emitAfterProviderResponse(options, model, response);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new IllegalStateException("HTTP " + response.statusCode() + " from " + endpoint + ": " + errorBody);
                }

                ResponsesAccumulator accumulator = new ResponsesAccumulator();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.equals("data: [DONE]")) {
                            break;
                        }
                        if (line.startsWith("data: ")) {
                            handleEvent(JsonCodec.parse(line.substring(6).trim()), accumulator, stream);
                        }
                    }
                }
                if (!accumulator.terminalEvent) {
                    throw new IllegalStateException("OpenAI Responses stream ended before a terminal response event");
                }
                stream.emit(new AssistantMessageEvent.End(new Message.Assistant(
                        accumulator.finalContents(), model.provider(), model.modelId(), accumulator.stopReason(),
                        accumulator.usage, null, Instant.now(), accumulator.responseId)));
            } catch (Exception error) {
                stream.emit(new AssistantMessageEvent.Error(error.getMessage(), error));
            } finally {
                stream.close();
            }
        });
        return stream;
    }

    static ObjectNode buildRequestBody(Model model, Context context, StreamOptions options) {
        ObjectNode body = JsonCodec.mapper().createObjectNode();
        body.put("model", model.modelId());
        body.put("stream", true);
        body.put("store", false);
        if (options != null && options.maxTokens() != null) {
            body.put("max_output_tokens", options.maxTokens());
        }
        if (options != null && options.temperature() != null) {
            body.put("temperature", options.temperature());
        }
        if (options != null && options.sessionId() != null && !options.sessionId().isBlank()
                && options.cacheRetention() != CacheRetention.NONE) {
            body.put("prompt_cache_key", options.sessionId());
        }

        ArrayNode input = body.putArray("input");
        if (context != null && context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            ObjectNode system = input.addObject();
            system.put("role", "developer");
            system.put("content", context.systemPrompt());
        }
        if (context != null && context.messages() != null) {
            context.messages().forEach(message -> appendMessage(input, message));
        }

        if (context != null && context.tools() != null && !context.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            for (Tool tool : context.tools()) {
                ObjectNode value = tools.addObject();
                value.put("type", "function");
                value.put("name", tool.name());
                value.put("description", tool.description() == null ? "" : tool.description());
                value.set("parameters", tool.parameters() == null
                        ? JsonCodec.mapper().createObjectNode() : tool.parameters());
                value.put("strict", false);
            }
        }
        appendToolChoice(body, options == null ? null : options.toolChoice());

        ThinkingLevel thinking = context == null ? null : context.thinkingLevel();
        if (thinking != null && thinking != ThinkingLevel.OFF) {
            String effort = switch (thinking) {
                case MINIMAL, LOW -> "low";
                case MEDIUM -> "medium";
                case HIGH, XHIGH, MAX -> "high";
                case OFF -> "none";
            };
            body.putObject("reasoning").put("effort", effort);
        }
        if (model.options() != null && Boolean.TRUE.equals(model.options().get("reasoning"))) {
            body.putArray("include").add("reasoning.encrypted_content");
        }
        return body;
    }

    private static void appendMessage(ArrayNode input, Message message) {
        if (message instanceof Message.ToolResult result) {
            ObjectNode value = input.addObject();
            value.put("type", "function_call_output");
            value.put("call_id", result.toolCallId());
            String text = Content.text(result.content());
            if (text.isEmpty()) {
                boolean hasImage = result.content().stream().anyMatch(Content.Image.class::isInstance);
                text = hasImage ? "(see attached image)" : "(no tool output)";
            }
            value.put("output", text);
            return;
        }
        if (message instanceof Message.Assistant assistant) {
            String text = Content.text(assistant.content());
            if (!text.isEmpty()) {
                ObjectNode value = input.addObject();
                value.put("role", "assistant");
                value.put("content", text);
            }
            for (Content content : assistant.content()) {
                if (content instanceof Content.ToolCall call) {
                    ObjectNode value = input.addObject();
                    value.put("type", "function_call");
                    value.put("call_id", call.id());
                    value.put("name", call.name());
                    value.put("arguments", call.input() == null ? "{}" : JsonCodec.stringify(call.input()));
                }
            }
            return;
        }
        ObjectNode value = input.addObject();
        value.put("role", message.role());
        ArrayNode parts = value.putArray("content");
        if (message instanceof Message.User user) {
            for (Content content : user.content()) {
                if (content instanceof Content.Text text) {
                    parts.addObject().put("type", "input_text").put("text", text.text());
                } else if (content instanceof Content.Image image) {
                    ObjectNode part = parts.addObject();
                    part.put("type", "input_image");
                    part.put("detail", "auto");
                    part.put("image_url", image.url() != null && !image.url().isBlank()
                            ? image.url() : "data:" + image.mimeType() + ";base64," + image.data());
                }
            }
        }
    }

    private static void appendToolChoice(ObjectNode body, ToolChoice choice) {
        if (choice == null) {
            return;
        }
        if (choice instanceof ToolChoice.Named named) {
            body.putObject("tool_choice").put("type", "function").put("name", named.name());
        } else {
            body.put("tool_choice", choice.wireName());
        }
    }

    static void handleEvent(JsonNode event, ResponsesAccumulator accumulator, AssistantMessageEventStream stream) {
        String type = event.path("type").asText();
        int outputIndex = event.path("output_index").asInt(-1);
        switch (type) {
            case "response.created" -> accumulator.responseId = event.path("response").path("id").asText(null);
            case "response.output_item.added" -> accumulator.ensureSlot(outputIndex, event.path("item"));
            case "response.output_text.delta", "response.refusal.delta" -> {
                String delta = event.path("delta").asText("");
                accumulator.text(outputIndex).append(delta);
                if (!delta.isEmpty()) {
                    stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Text(delta)));
                }
            }
            case "response.reasoning_summary_text.delta", "response.reasoning_text.delta" -> {
                String delta = event.path("delta").asText("");
                accumulator.thinking(outputIndex).text.append(delta);
                if (!delta.isEmpty()) {
                    stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Thinking(delta, null)));
                }
            }
            case "response.reasoning_summary_part.done" -> accumulator.thinking(outputIndex).text.append("\n\n");
            case "response.function_call_arguments.delta" ->
                    accumulator.tool(outputIndex, event.path("item_id").asText(null), null).arguments
                            .append(event.path("delta").asText(""));
            case "response.function_call_arguments.done" -> {
                ToolSlot slot = accumulator.tool(outputIndex, event.path("item_id").asText(null), null);
                slot.arguments.setLength(0);
                slot.arguments.append(event.path("arguments").asText("{}"));
            }
            case "response.output_item.done" -> accumulator.finalizeSlot(outputIndex, event.path("item"));
            case "response.completed", "response.incomplete" -> {
                JsonNode response = event.path("response");
                accumulator.responseId = response.path("id").asText(accumulator.responseId);
                accumulator.usage = parseUsage(response.path("usage"));
                stream.emit(new AssistantMessageEvent.UsageDelta(accumulator.usage));
                accumulator.stopReason = "incomplete".equals(response.path("status").asText())
                        ? StopReason.LENGTH : StopReason.STOP;
                accumulator.terminalEvent = true;
                JsonNode output = response.path("output");
                if (output.isArray()) {
                    for (int i = 0; i < output.size(); i++) {
                        accumulator.finalizeSlot(i, output.get(i));
                    }
                }
            }
            case "response.failed" -> throw new IllegalStateException(responseFailure(event.path("response")));
            case "error" -> throw new IllegalStateException(event.path("message").asText("Responses API error"));
            default -> {
                // Ignore lifecycle events that do not carry user-visible content.
            }
        }
    }

    private static Usage parseUsage(JsonNode usage) {
        int input = usage.path("input_tokens").asInt(0);
        int cacheRead = usage.path("input_tokens_details").path("cached_tokens").asInt(0);
        int cacheWrite = usage.path("input_tokens_details").path("cache_write_tokens").asInt(0);
        int output = usage.path("output_tokens").asInt(0);
        int reasoning = usage.path("output_tokens_details").path("reasoning_tokens").asInt(0);
        return new Usage(Math.max(0, input - cacheRead - cacheWrite), output, cacheWrite, cacheRead, reasoning);
    }

    private static String responseFailure(JsonNode response) {
        JsonNode error = response.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            return error.path("code").asText("unknown") + ": " + error.path("message").asText("no message");
        }
        return "Responses API request failed";
    }

    private static String resolveApiKey(String envVar, StreamOptions options) {
        if (options != null && options.apiKey() != null && !options.apiKey().isBlank()) {
            return options.apiKey();
        }
        if (options != null && options.env() != null && options.env().get(envVar) != null) {
            return options.env().get(envVar);
        }
        return System.getenv(envVar);
    }

    private static String responsesEndpoint(Model model, String defaultApi) {
        String baseUrl = model.options() != null && model.options().get("baseUrl") instanceof String value
                ? value : defaultApi;
        return baseUrl.endsWith("/") ? baseUrl + "responses" : baseUrl + "/responses";
    }

    static final class ResponsesAccumulator {
        private final TreeMap<Integer, Slot> slots = new TreeMap<>();
        private Usage usage = new Usage(0, 0, 0, 0, 0);
        private StopReason stopReason = StopReason.STOP;
        private String responseId;
        private boolean terminalEvent;

        private void ensureSlot(int index, JsonNode item) {
            if (index < 0 || slots.containsKey(index)) {
                return;
            }
            String type = item.path("type").asText();
            switch (type) {
                case "reasoning" -> slots.put(index, new ThinkingSlot(new StringBuilder(), null));
                case "function_call" -> slots.put(index, new ToolSlot(
                        item.path("call_id").asText(item.path("id").asText("call_" + index)),
                        item.path("name").asText("unknown_tool"), new StringBuilder()));
                default -> slots.put(index, new TextSlot(new StringBuilder()));
            }
        }

        private StringBuilder text(int index) {
            slots.computeIfAbsent(index, ignored -> new TextSlot(new StringBuilder()));
            return ((TextSlot) slots.get(index)).text;
        }

        private ThinkingSlot thinking(int index) {
            slots.computeIfAbsent(index, ignored -> new ThinkingSlot(new StringBuilder(), null));
            return (ThinkingSlot) slots.get(index);
        }

        private ToolSlot tool(int index, String id, String name) {
            slots.computeIfAbsent(index, ignored -> new ToolSlot(
                    id == null ? "call_" + index : id, name == null ? "unknown_tool" : name,
                    new StringBuilder()));
            return (ToolSlot) slots.get(index);
        }

        private void finalizeSlot(int index, JsonNode item) {
            ensureSlot(index, item);
            Slot slot = slots.get(index);
            if (slot instanceof TextSlot text) {
                String full = responseText(item);
                if (!full.isEmpty()) {
                    text.text.setLength(0);
                    text.text.append(full);
                }
            } else if (slot instanceof ThinkingSlot thinking) {
                String full = reasoningText(item);
                if (!full.isEmpty()) {
                    thinking.text.setLength(0);
                    thinking.text.append(full);
                }
                thinking.signature = JsonCodec.stringify(item);
            } else if (slot instanceof ToolSlot tool) {
                tool.id = item.path("call_id").asText(tool.id);
                tool.name = item.path("name").asText(tool.name);
                String args = item.path("arguments").asText("");
                if (!args.isEmpty()) {
                    tool.arguments.setLength(0);
                    tool.arguments.append(args);
                }
            }
        }

        List<Content> finalContents() {
            List<Content> contents = new ArrayList<>();
            for (Slot slot : slots.values()) {
                if (slot instanceof TextSlot text && !text.text.isEmpty()) {
                    contents.add(new Content.Text(text.text.toString()));
                } else if (slot instanceof ThinkingSlot thinking && !thinking.text.isEmpty()) {
                    contents.add(new Content.Thinking(thinking.text.toString(), thinking.signature));
                } else if (slot instanceof ToolSlot tool) {
                    JsonNode arguments;
                    try {
                        arguments = JsonCodec.parse(tool.arguments.isEmpty() ? "{}" : tool.arguments.toString());
                    } catch (Exception ignored) {
                        arguments = JsonCodec.mapper().createObjectNode();
                    }
                    contents.add(new Content.ToolCall(tool.id, tool.name, arguments, List.of()));
                }
            }
            if (contents.stream().anyMatch(Content.ToolCall.class::isInstance) && stopReason == StopReason.STOP) {
                stopReason = StopReason.TOOL_USE;
            }
            return List.copyOf(contents);
        }

        Usage usage() {
            return usage;
        }

        StopReason stopReason() {
            return finalContents().stream().anyMatch(Content.ToolCall.class::isInstance)
                    ? StopReason.TOOL_USE : stopReason;
        }

        boolean hasTerminalEvent() {
            return terminalEvent;
        }

        private static String responseText(JsonNode item) {
            StringBuilder text = new StringBuilder();
            JsonNode content = item.path("content");
            if (content.isArray()) {
                content.forEach(part -> {
                    String type = part.path("type").asText();
                    if ("output_text".equals(type)) {
                        text.append(part.path("text").asText(""));
                    } else if ("refusal".equals(type)) {
                        text.append(part.path("refusal").asText(""));
                    }
                });
            }
            return text.toString();
        }

        private static String reasoningText(JsonNode item) {
            StringBuilder text = new StringBuilder();
            JsonNode summary = item.path("summary");
            if (summary.isArray()) {
                summary.forEach(part -> {
                    if (!text.isEmpty()) text.append("\n\n");
                    text.append(part.path("text").asText(""));
                });
            }
            return text.toString();
        }
    }

    private sealed interface Slot permits TextSlot, ThinkingSlot, ToolSlot {
    }

    private static final class TextSlot implements Slot {
        private final StringBuilder text;

        private TextSlot(StringBuilder text) {
            this.text = text;
        }
    }

    private static final class ThinkingSlot implements Slot {
        private final StringBuilder text;
        private String signature;

        private ThinkingSlot(StringBuilder text, String signature) {
            this.text = text;
            this.signature = signature;
        }
    }

    private static final class ToolSlot implements Slot {
        private String id;
        private String name;
        private final StringBuilder arguments;

        private ToolSlot(String id, String name, StringBuilder arguments) {
            this.id = id;
            this.name = name;
            this.arguments = arguments;
        }
    }
}
