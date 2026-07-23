package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.common.json.JsonCodec;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class GeminiProvider implements Provider {
    private static final String ID = "google";
    private static final String DEFAULT_API = "https://generativelanguage.googleapis.com/v1beta";

    private final List<Model> models = List.of(
            model("gemini-2.0-flash", "Gemini 2.0 Flash", false, 1048576, 8192),
            model("gemini-2.0-flash-lite", "Gemini 2.0 Flash-Lite", false, 1048576, 8192),
            model("gemini-2.5-flash", "Gemini 2.5 Flash", true, 1048576, 65536),
            model("gemini-2.5-flash-lite", "Gemini 2.5 Flash-Lite", true, 1048576, 65536),
            model("gemini-2.5-pro", "Gemini 2.5 Pro", true, 1048576, 65536),
            model("gemini-3-flash-preview", "Gemini 3 Flash Preview", true, 1048576, 65536),
            model("gemini-3.1-flash-lite", "Gemini 3.1 Flash Lite", true, 1048576, 65536),
            model("gemini-3.1-flash-lite-preview", "Gemini 3.1 Flash Lite Preview", true, 1048576, 65536),
            model("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", true, 1048576, 65536),
            model("gemini-3.1-pro-preview-customtools", "Gemini 3.1 Pro Preview Custom Tools", true, 1048576, 65536),
            model("gemini-flash-latest", "Gemini Flash Latest", true, 1048576, 65536),
            model("gemini-flash-lite-latest", "Gemini Flash-Lite Latest", true, 1048576, 65536)
    );

    private static Model model(String id, String name, boolean reasoning, int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "google-generative-ai", contextWindow, maxTokens, true, true,
                Map.of("baseUrl", DEFAULT_API, "reasoning", reasoning, "input", List.of("text", "image")));
    }

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

                String apiKey = resolveApiKey(options);
                if (apiKey == null || apiKey.isBlank()) {
                    stream.emit(new AssistantMessageEvent.Error("Missing API key for provider: " + ID,
                            new IllegalStateException("Missing GEMINI_API_KEY")));
                    return;
                }

                String endpoint = endpoint(model);
                JsonNode bodyNode = ProviderHttpSupport.applyBeforeProviderRequest(options, model,
                        buildRequestBody(model, context, options));
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(endpoint))
                        .timeout(ProviderHttpSupport.requestTimeout(ID, options))
                        .header("Content-Type", "application/json")
                        .header("x-goog-api-key", apiKey)
                        .POST(HttpRequest.BodyPublishers.ofString(JsonCodec.stringify(bodyNode), StandardCharsets.UTF_8));

                if (options != null && options.headers() != null) {
                    options.headers().forEach((k, v) -> {
                        if (k != null && v != null
                                && !k.equalsIgnoreCase("content-type")
                                && !k.equalsIgnoreCase("x-goog-api-key")) {
                            reqBuilder.header(k, v);
                        }
                    });
                }

                HttpClient client = ProviderHttpSupport.client();
                HttpResponse<InputStream> response = ProviderHttpSupport.sendWithRetries(ID, reqBuilder.build(),
                        request -> client.send(request, HttpResponse.BodyHandlers.ofInputStream()),
                        ProviderHttpSupport.retryPolicy(ID, options));
                ProviderHttpSupport.emitAfterProviderResponse(options, model, response);
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                    throw new RuntimeException("HTTP " + response.statusCode() + " from " + endpoint + ": " + errBody);
                }

                GeminiAccumulator accumulator = new GeminiAccumulator();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty()) {
                            continue;
                        }
                        if (line.startsWith("data: ")) {
                            String jsonStr = line.substring(6).trim();
                            if (!jsonStr.isEmpty() && !"[DONE]".equals(jsonStr)) {
                                handleChunk(JsonCodec.parse(jsonStr), accumulator, stream);
                            }
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

    static ObjectNode buildRequestBody(Model model, Context context, StreamOptions options) {
        ObjectNode body = JsonCodec.mapper().createObjectNode();
        ArrayNode contents = body.putArray("contents");
        if (context != null && context.messages() != null) {
            for (Message message : context.messages()) {
                ObjectNode converted = convertMessage(model, message);
                if (converted != null) {
                    contents.add(converted);
                }
            }
        }

        if (context != null && context.systemPrompt() != null && !context.systemPrompt().isBlank()) {
            ObjectNode systemInstruction = body.putObject("systemInstruction");
            ArrayNode parts = systemInstruction.putArray("parts");
            parts.addObject().put("text", context.systemPrompt());
        }

        ObjectNode generationConfig = JsonCodec.mapper().createObjectNode();
        if (options != null && options.temperature() != null) {
            generationConfig.put("temperature", options.temperature());
        }
        if (options != null && options.maxTokens() != null) {
            generationConfig.put("maxOutputTokens", options.maxTokens());
        }
        applyThinkingConfig(model, context, generationConfig);
        if (!generationConfig.isEmpty()) {
            body.set("generationConfig", generationConfig);
        }

        if (context != null && context.tools() != null && !context.tools().isEmpty()) {
            ArrayNode tools = body.putArray("tools");
            ObjectNode declarationsGroup = tools.addObject();
            ArrayNode declarations = declarationsGroup.putArray("functionDeclarations");
            for (Tool tool : context.tools()) {
                ObjectNode declaration = declarations.addObject();
                declaration.put("name", tool.name());
                declaration.put("description", tool.description() != null ? tool.description() : "");
                declaration.set("parametersJsonSchema",
                        tool.parameters() != null ? tool.parameters() : JsonCodec.mapper().createObjectNode());
            }
        }

        return body;
    }

    static void handleChunk(JsonNode chunk, GeminiAccumulator accumulator, AssistantMessageEventStream stream) {
        JsonNode usageMetadata = chunk.get("usageMetadata");
        if (usageMetadata != null && usageMetadata.isObject()) {
            int cached = usageMetadata.path("cachedContentTokenCount").asInt(0);
            int prompt = Math.max(0, usageMetadata.path("promptTokenCount").asInt(0) - cached);
            Usage usage = new Usage(prompt, usageMetadata.path("candidatesTokenCount").asInt(0),
                    0, cached, usageMetadata.path("thoughtsTokenCount").asInt(0));
            accumulator.usage = usage;
            stream.emit(new AssistantMessageEvent.UsageDelta(usage));
        }

        String rid = textValue(chunk.get("responseId"));
        if (rid != null && !rid.isBlank()) {
            accumulator.responseId = rid;
        } else if (accumulator.responseId == null) {
            String cid = textValue(chunk.get("id"));
            if (cid != null && !cid.isBlank()) {
                accumulator.responseId = cid;
            }
        }

        JsonNode candidates = chunk.get("candidates");
        if (candidates == null || !candidates.isArray() || candidates.isEmpty()) {
            return;
        }

        JsonNode candidate = candidates.get(0);
        JsonNode parts = candidate.path("content").path("parts");
        if (parts.isArray()) {
            for (JsonNode part : parts) {
                if (part.has("text") && !part.get("text").isNull()) {
                    String text = part.get("text").asText();
                    if (!text.isEmpty()) {
                        if (part.path("thought").asBoolean(false)) {
                            accumulator.thinking.append(text);
                            String sig = textValue(part.get("thoughtSignature"));
                            if (sig != null && !sig.isBlank()) {
                                accumulator.thinkingSignature = sig;
                            }
                            stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Thinking(text, sig)));
                        } else {
                            accumulator.text.append(text);
                            stream.emit(new AssistantMessageEvent.ContentDelta(new Content.Text(text)));
                        }
                    }
                }

                JsonNode functionCall = part.get("functionCall");
                if (functionCall != null && functionCall.isObject()) {
                    String id = textValue(functionCall.get("id"));
                    if (id == null || id.isBlank()) {
                        id = "gemini_call_" + (++accumulator.generatedToolCallIds);
                    }
                    JsonNode args = functionCall.get("args");
                    Content.ToolCall toolCall = new Content.ToolCall(
                            id,
                            textValue(functionCall.get("name")) != null ? textValue(functionCall.get("name")) : "",
                            args != null && !args.isNull() ? args.deepCopy() : JsonCodec.mapper().createObjectNode(),
                            List.of());
                    accumulator.toolCalls.add(toolCall);
                    stream.emit(new AssistantMessageEvent.ContentDelta(toolCall));
                }
            }
        }

        String finishReason = textValue(candidate.get("finishReason"));
        if (finishReason != null) {
            accumulator.stopReason = mapStopReason(finishReason);
        }
    }

    static final class GeminiAccumulator {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final List<Content.ToolCall> toolCalls = new ArrayList<>();
        private Usage usage = new Usage(0, 0, 0, 0, 0);
        private StopReason stopReason = StopReason.STOP;
        private int generatedToolCallIds;
        private String responseId;
        private String thinkingSignature;

        List<Content> finalContents() {
            List<Content> result = new ArrayList<>();
            if (!thinking.isEmpty()) {
                result.add(new Content.Thinking(thinking.toString(), thinkingSignature));
            }
            if (!text.isEmpty()) {
                result.add(new Content.Text(text.toString()));
            }
            result.addAll(toolCalls);
            return List.copyOf(result);
        }

        Usage usage() {
            return usage;
        }

        StopReason stopReason() {
            return toolCalls.isEmpty() ? stopReason : StopReason.TOOL_USE;
        }
    }

    private static ObjectNode convertMessage(Model model, Message message) {
        if (message instanceof Message.User user) {
            ObjectNode node = contentNode("user");
            ArrayNode parts = (ArrayNode) node.get("parts");
            for (Content content : user.content()) {
                addUserPart(parts, content);
            }
            return parts.isEmpty() ? null : node;
        }
        if (message instanceof Message.Assistant assistant) {
            ObjectNode node = contentNode("model");
            ArrayNode parts = (ArrayNode) node.get("parts");
            for (Content content : assistant.content()) {
                if (content instanceof Content.Text text && !text.text().isBlank()) {
                    parts.addObject().put("text", text.text());
                } else if (content instanceof Content.Thinking thinking && !thinking.text().isBlank()) {
                    ObjectNode part = parts.addObject();
                    part.put("thought", true);
                    part.put("text", thinking.text());
                    if (sameModel(model, assistant) && thinking.signature() != null && !thinking.signature().isBlank()) {
                        part.put("thoughtSignature", thinking.signature());
                    }
                } else if (content instanceof Content.ToolCall toolCall) {
                    ObjectNode part = parts.addObject();
                    ObjectNode functionCall = part.putObject("functionCall");
                    functionCall.put("name", toolCall.name());
                    functionCall.set("args", toolCall.input() != null ? toolCall.input() : JsonCodec.mapper().createObjectNode());
                    if (toolCall.id() != null && !toolCall.id().isBlank() && requiresToolCallId(model.modelId())) {
                        functionCall.put("id", normalizeToolCallId(toolCall.id()));
                    }
                }
            }
            return parts.isEmpty() ? null : node;
        }
        if (message instanceof Message.ToolResult result) {
            ObjectNode node = contentNode("user");
            ArrayNode parts = (ArrayNode) node.get("parts");
            ObjectNode responsePart = parts.addObject();
            ObjectNode functionResponse = responsePart.putObject("functionResponse");
            functionResponse.put("name", result.toolName());
            if (requiresToolCallId(model.modelId()) && result.toolCallId() != null && !result.toolCallId().isBlank()) {
                functionResponse.put("id", normalizeToolCallId(result.toolCallId()));
            }
            ObjectNode response = functionResponse.putObject("response");
            response.put(result.error() ? "error" : "output", textFromContents(result.content()));
            return node;
        }
        return null;
    }

    private static ObjectNode contentNode(String role) {
        ObjectNode node = JsonCodec.mapper().createObjectNode();
        node.put("role", role);
        node.putArray("parts");
        return node;
    }

    private static void addUserPart(ArrayNode parts, Content content) {
        if (content instanceof Content.Text text) {
            parts.addObject().put("text", text.text());
        } else if (content instanceof Content.Image image) {
            ObjectNode part = parts.addObject();
            if (image.data() != null && !image.data().isBlank()) {
                ObjectNode inlineData = part.putObject("inlineData");
                inlineData.put("mimeType", image.mimeType() != null ? image.mimeType() : "image/png");
                inlineData.put("data", image.data());
            } else if (image.url() != null && !image.url().isBlank()) {
                ObjectNode fileData = part.putObject("fileData");
                fileData.put("mimeType", image.mimeType() != null ? image.mimeType() : "image/png");
                fileData.put("fileUri", image.url());
            } else {
                parts.remove(parts.size() - 1);
            }
        }
    }

    private static void applyThinkingConfig(Model model, Context context, ObjectNode generationConfig) {
        if (!Boolean.TRUE.equals(model.options().get("reasoning")) || context == null || context.thinkingLevel() == null) {
            return;
        }
        ObjectNode thinkingConfig = JsonCodec.mapper().createObjectNode();
        if (context.thinkingLevel() == ThinkingLevel.OFF) {
            if (isGemini3FlashModel(model.modelId())) {
                thinkingConfig.put("thinkingLevel", "MINIMAL");
            } else if (isGemini3ProModel(model.modelId())) {
                thinkingConfig.put("thinkingLevel", "LOW");
            } else {
                thinkingConfig.put("thinkingBudget", 0);
            }
        } else if (isGemini3Model(model.modelId())) {
            thinkingConfig.put("includeThoughts", true);
            thinkingConfig.put("thinkingLevel", googleThinkingLevel(model.modelId(), context.thinkingLevel()));
        } else {
            thinkingConfig.put("includeThoughts", true);
            thinkingConfig.put("thinkingBudget", googleThinkingBudget(model.modelId(), context.thinkingLevel()));
        }
        generationConfig.set("thinkingConfig", thinkingConfig);
    }

    private static String endpoint(Model model) {
        String baseUrl = DEFAULT_API;
        if (model.options() != null && model.options().get("baseUrl") instanceof String optionBaseUrl && !optionBaseUrl.isBlank()) {
            baseUrl = optionBaseUrl;
        } else if (model.api() != null && model.api().startsWith("http")) {
            baseUrl = model.api();
        }
        String encodedModel = URLEncoder.encode(model.modelId(), StandardCharsets.UTF_8);
        return (baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                + "/models/" + encodedModel + ":streamGenerateContent?alt=sse";
    }

    private static String resolveApiKey(StreamOptions options) {
        if (options != null && options.apiKey() != null && !options.apiKey().isBlank()) {
            return options.apiKey();
        }
        if (options != null && options.env() != null && options.env().get("GEMINI_API_KEY") != null) {
            return options.env().get("GEMINI_API_KEY");
        }
        return System.getenv("GEMINI_API_KEY");
    }

    private static StopReason mapStopReason(String reason) {
        return switch (reason) {
            case "STOP" -> StopReason.STOP;
            case "MAX_TOKENS" -> StopReason.LENGTH;
            default -> StopReason.ERROR;
        };
    }

    private static String textFromContents(List<Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        for (Content content : contents) {
            if (content instanceof Content.Text text) {
                if (!result.isEmpty()) {
                    result.append('\n');
                }
                result.append(text.text());
            }
        }
        return result.toString();
    }

    private static boolean sameModel(Model model, Message.Assistant assistant) {
        return model.provider().equals(assistant.provider()) && model.modelId().equals(assistant.model());
    }

    private static boolean requiresToolCallId(String modelId) {
        return modelId.startsWith("claude-") || modelId.startsWith("gpt-oss-");
    }

    private static String normalizeToolCallId(String id) {
        return id.replaceAll("[^a-zA-Z0-9_-]", "_").substring(0, Math.min(64, id.length()));
    }

    private static boolean isGemini3Model(String modelId) {
        return isGemini3ProModel(modelId) || isGemini3FlashModel(modelId);
    }

    private static boolean isGemini3ProModel(String modelId) {
        return modelId.toLowerCase().matches(".*gemini-3(?:\\.\\d+)?-pro.*");
    }

    private static boolean isGemini3FlashModel(String modelId) {
        String id = modelId.toLowerCase();
        return id.matches(".*gemini-3(?:\\.\\d+)?-flash.*")
                || id.equals("gemini-flash-latest")
                || id.equals("gemini-flash-lite-latest");
    }

    private static String googleThinkingLevel(String modelId, ThinkingLevel level) {
        if (isGemini3ProModel(modelId)) {
            return switch (level) {
                case MINIMAL, LOW -> "LOW";
                case MEDIUM, HIGH, XHIGH, MAX -> "HIGH";
                case OFF -> "LOW";
            };
        }
        return switch (level) {
            case MINIMAL -> "MINIMAL";
            case LOW -> "LOW";
            case MEDIUM -> "MEDIUM";
            case HIGH, XHIGH, MAX -> "HIGH";
            case OFF -> "MINIMAL";
        };
    }

    private static int googleThinkingBudget(String modelId, ThinkingLevel level) {
        String id = modelId.toLowerCase();
        if (id.contains("2.5-pro")) {
            return switch (level) {
                case MINIMAL -> 128;
                case LOW -> 2048;
                case MEDIUM -> 8192;
                case HIGH, XHIGH, MAX -> 32768;
                case OFF -> 0;
            };
        }
        if (id.contains("2.5-flash-lite")) {
            return switch (level) {
                case MINIMAL -> 512;
                case LOW -> 2048;
                case MEDIUM -> 8192;
                case HIGH, XHIGH, MAX -> 24576;
                case OFF -> 0;
            };
        }
        if (id.contains("2.5-flash")) {
            return switch (level) {
                case MINIMAL -> 128;
                case LOW -> 2048;
                case MEDIUM -> 8192;
                case HIGH, XHIGH, MAX -> 24576;
                case OFF -> 0;
            };
        }
        return -1;
    }

    private static String textValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
