package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.ai.model.CacheRetention;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.common.json.JsonCodec;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BedrockProvider implements Provider {
    private static final String ID = "amazon-bedrock";
    private static final String DEFAULT_API = "https://bedrock-runtime.us-east-1.amazonaws.com";
    private static final String EMPTY_TEXT_PLACEHOLDER = "<empty>";
    private static final Pattern BEDROCK_ARN_REGION =
            Pattern.compile("^arn:aws(?:-[a-z0-9-]+)?:bedrock:([a-z0-9-]+):.*");
    private static final Pattern STANDARD_BEDROCK_ENDPOINT =
            Pattern.compile("^bedrock-runtime(?:-fips)?\\.([a-z0-9-]+)\\.amazonaws\\.com(?:\\.cn)?$");
    private final List<Model> models = List.of(
            new Model(ID, "us.anthropic.claude-3-7-sonnet-20250219-v1:0", "Claude 3.7 Sonnet (Bedrock)", DEFAULT_API, 200000, 64000, true, true, Map.of("reasoning", true)),
            new Model(ID, "us.anthropic.claude-3-5-sonnet-20241022-v2:0", "Claude 3.5 Sonnet v2 (Bedrock)", DEFAULT_API, 200000, 8192, true, true, Map.of("reasoning", true)),
            new Model(ID, "us.anthropic.claude-3-5-haiku-20241022-v1:0", "Claude 3.5 Haiku (Bedrock)", DEFAULT_API, 200000, 8192, true, false, Map.of("reasoning", true)),
            new Model(ID, "amazon.titan-text-premier-v1:0", "Amazon Titan Text Premier", DEFAULT_API, 32000, 4096, true, false, Map.of())
    );

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<Model> models() {
        return models;
    }

    static ObjectNode buildConverseRequestBody(Model model, Context context, StreamOptions options) {
        StreamOptions effectiveOptions = options == null ? StreamOptions.defaults() : options;
        ObjectNode root = JsonCodec.mapper().createObjectNode();
        root.put("modelId", model.modelId());

        ArrayNode messages = JsonCodec.mapper().createArrayNode();
        if (context != null && context.messages() != null) {
            for (int index = 0; index < context.messages().size(); index++) {
                Message message = context.messages().get(index);
                ObjectNode converted;
                if (message instanceof Message.ToolResult toolResult) {
                    ToolResultConversion conversion = convertToolResults(context.messages(), index, toolResult);
                    converted = conversion.message();
                    index = conversion.lastIndex();
                } else {
                    converted = convertMessage(model, message);
                }
                if (converted != null) {
                    messages.add(converted);
                }
            }
        }
        appendCachePointToLastUserMessage(messages, model, effectiveOptions);
        root.set("messages", messages);

        ArrayNode system = buildSystemPrompt(model, context, effectiveOptions);
        if (!system.isEmpty()) {
            root.set("system", system);
        }

        ObjectNode inferenceConfig = JsonCodec.mapper().createObjectNode();
        if (effectiveOptions.maxTokens() != null) {
            inferenceConfig.put("maxTokens", effectiveOptions.maxTokens());
        }
        if (effectiveOptions.temperature() != null) {
            inferenceConfig.put("temperature", effectiveOptions.temperature());
        }
        if (!inferenceConfig.isEmpty()) {
            root.set("inferenceConfig", inferenceConfig);
        }

        if (context != null && context.tools() != null && !context.tools().isEmpty()) {
            root.set("toolConfig", convertToolConfig(context.tools()));
        }
        ObjectNode additionalModelRequestFields = buildAdditionalModelRequestFields(model, context, effectiveOptions);
        if (!additionalModelRequestFields.isEmpty()) {
            root.set("additionalModelRequestFields", additionalModelRequestFields);
        }
        ObjectNode requestMetadata = convertRequestMetadata(effectiveOptions.metadata());
        if (!requestMetadata.isEmpty()) {
            root.set("requestMetadata", requestMetadata);
        }
        return root;
    }

    static String resolveBedrockRegion(Model model, StreamOptions options) {
        Matcher arnRegion = BEDROCK_ARN_REGION.matcher(model.modelId());
        if (arnRegion.matches()) {
            return arnRegion.group(1);
        }

        String configured = optionString(options, "region");
        if (configured != null) {
            return configured;
        }

        String envRegion = envValue(options, "AWS_REGION");
        if (envRegion != null) {
            return envRegion;
        }
        envRegion = envValue(options, "AWS_DEFAULT_REGION");
        if (envRegion != null) {
            return envRegion;
        }

        String endpointRegion = standardEndpointRegion(modelBaseUrl(model));
        return endpointRegion == null ? "us-east-1" : endpointRegion;
    }

    static boolean hasBedrockCredentials(StreamOptions options) {
        if (options != null && options.apiKey() != null && !options.apiKey().isBlank()) {
            return true;
        }
        if ("1".equals(envValue(options, "AWS_BEDROCK_SKIP_AUTH"))) {
            return true;
        }
        if (optionString(options, "profile") != null || envValue(options, "AWS_PROFILE") != null) {
            return true;
        }
        if (optionString(options, "bearerToken") != null || envValue(options, "AWS_BEARER_TOKEN_BEDROCK") != null) {
            return true;
        }
        if (envValue(options, "AWS_ACCESS_KEY_ID") != null && envValue(options, "AWS_SECRET_ACCESS_KEY") != null) {
            return true;
        }
        return envValue(options, "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI") != null
                || envValue(options, "AWS_CONTAINER_CREDENTIALS_FULL_URI") != null
                || envValue(options, "AWS_WEB_IDENTITY_TOKEN_FILE") != null;
    }

    private static ArrayNode buildSystemPrompt(Model model, Context context, StreamOptions options) {
        ArrayNode system = JsonCodec.mapper().createArrayNode();
        if (context == null || context.systemPrompt() == null || context.systemPrompt().isBlank()) {
            return system;
        }

        ObjectNode textBlock = JsonCodec.mapper().createObjectNode();
        textBlock.put("text", context.systemPrompt());
        system.add(textBlock);

        CacheRetention retention = resolveCacheRetention(options);
        if (retention != CacheRetention.NONE && supportsPromptCaching(model, options)) {
            system.add(createCachePointBlock(retention));
        }
        return system;
    }

    private static void appendCachePointToLastUserMessage(ArrayNode messages, Model model, StreamOptions options) {
        CacheRetention retention = resolveCacheRetention(options);
        if (retention == CacheRetention.NONE || !supportsPromptCaching(model, options) || messages.isEmpty()) {
            return;
        }
        JsonNode lastMessage = messages.get(messages.size() - 1);
        if (!(lastMessage instanceof ObjectNode messageNode) || !"user".equals(messageNode.path("role").asText())) {
            return;
        }
        JsonNode content = messageNode.get("content");
        if (content instanceof ArrayNode contentArray) {
            contentArray.add(createCachePointBlock(retention));
        }
    }

    private static ObjectNode createCachePointBlock(CacheRetention retention) {
        ObjectNode cachePointBlock = JsonCodec.mapper().createObjectNode();
        ObjectNode cachePoint = JsonCodec.mapper().createObjectNode();
        cachePoint.put("type", "default");
        if (retention == CacheRetention.LONG) {
            cachePoint.put("ttl", "1h");
        }
        cachePointBlock.set("cachePoint", cachePoint);
        return cachePointBlock;
    }

    private static ObjectNode convertMessage(Model model, Message message) {
        if (message instanceof Message.User user) {
            return messageNode("user", convertContentBlocks(model, user.content(), true));
        }
        if (message instanceof Message.Assistant assistant) {
            ArrayNode content = convertContentBlocks(model, assistant.content(), false);
            return content.isEmpty() ? null : messageNode("assistant", content);
        }
        if (message instanceof Message.ToolResult toolResult) {
            return convertToolResults(List.of(toolResult), 0, toolResult).message();
        }
        return null;
    }

    private static ToolResultConversion convertToolResults(List<Message> messages, int startIndex,
                                                           Message.ToolResult first) {
        ArrayNode content = JsonCodec.mapper().createArrayNode();
        int index = startIndex;
        Message.ToolResult current = first;
        while (true) {
            content.add(convertToolResultBlock(current));
            int nextIndex = index + 1;
            if (messages == null || nextIndex >= messages.size()
                    || !(messages.get(nextIndex) instanceof Message.ToolResult next)) {
                return new ToolResultConversion(messageNode("user", content), index);
            }
            index = nextIndex;
            current = next;
        }
    }

    private static ObjectNode convertToolResultBlock(Message.ToolResult toolResult) {
        ObjectNode block = JsonCodec.mapper().createObjectNode();
        ObjectNode result = JsonCodec.mapper().createObjectNode();
        result.put("toolUseId", normalizeToolCallId(toolResult.toolCallId()));
        result.set("content", convertContentBlocks(null, toolResult.content(), true));
        result.put("status", toolResult.error() ? "error" : "success");
        block.set("toolResult", result);
        return block;
    }

    private record ToolResultConversion(ObjectNode message, int lastIndex) {
    }

    private static ObjectNode messageNode(String role, ArrayNode content) {
        ObjectNode node = JsonCodec.mapper().createObjectNode();
        node.put("role", role);
        node.set("content", content);
        return node;
    }

    private static ArrayNode convertContentBlocks(Model model, List<Content> contents, boolean requireContent) {
        ArrayNode blocks = JsonCodec.mapper().createArrayNode();
        if (contents != null) {
            for (Content content : contents) {
                ObjectNode block = convertContentBlock(model, content);
                if (block != null) {
                    blocks.add(block);
                }
            }
        }
        if (requireContent && blocks.isEmpty()) {
            ObjectNode placeholder = JsonCodec.mapper().createObjectNode();
            placeholder.put("text", EMPTY_TEXT_PLACEHOLDER);
            blocks.add(placeholder);
        }
        return blocks;
    }

    private static ObjectNode convertContentBlock(Model model, Content content) {
        if (content instanceof Content.Text text) {
            if (text.text() == null || text.text().isBlank()) {
                return null;
            }
            ObjectNode block = JsonCodec.mapper().createObjectNode();
            block.put("text", text.text());
            return block;
        }
        if (content instanceof Content.Image image) {
            ObjectNode block = JsonCodec.mapper().createObjectNode();
            block.set("image", createImageBlock(image));
            return block;
        }
        if (content instanceof Content.ToolCall toolCall) {
            ObjectNode block = JsonCodec.mapper().createObjectNode();
            ObjectNode use = JsonCodec.mapper().createObjectNode();
            use.put("toolUseId", normalizeToolCallId(toolCall.id()));
            use.put("name", toolCall.name());
            use.set("input", toolCall.input() == null ? JsonCodec.mapper().createObjectNode() : toolCall.input());
            block.set("toolUse", use);
            return block;
        }
        if (content instanceof Content.Thinking thinking) {
            if (thinking.text() == null || thinking.text().isBlank()) {
                return null;
            }
            if (model != null && isAnthropicClaudeModel(model)
                    && (thinking.signature() == null || thinking.signature().isBlank())) {
                ObjectNode block = JsonCodec.mapper().createObjectNode();
                block.put("text", thinking.text());
                return block;
            }
            ObjectNode block = JsonCodec.mapper().createObjectNode();
            ObjectNode reasoningContent = JsonCodec.mapper().createObjectNode();
            ObjectNode reasoningText = JsonCodec.mapper().createObjectNode();
            reasoningText.put("text", thinking.text());
            if (thinking.signature() != null && !thinking.signature().isBlank()) {
                reasoningText.put("signature", thinking.signature());
            }
            reasoningContent.set("reasoningText", reasoningText);
            block.set("reasoningContent", reasoningContent);
            return block;
        }
        return null;
    }

    private static ObjectNode createImageBlock(Content.Image image) {
        ObjectNode imageNode = JsonCodec.mapper().createObjectNode();
        imageNode.put("format", bedrockImageFormat(image.mimeType()));
        ObjectNode source = JsonCodec.mapper().createObjectNode();
        source.put("bytes", image.data());
        imageNode.set("source", source);
        return imageNode;
    }

    private static String bedrockImageFormat(String mimeType) {
        return switch (mimeType == null ? "" : mimeType.toLowerCase()) {
            case "image/jpeg", "image/jpg" -> "jpeg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw new IllegalArgumentException("Unknown image type: " + mimeType);
        };
    }

    private static ObjectNode convertToolConfig(List<Tool> tools) {
        ObjectNode toolConfig = JsonCodec.mapper().createObjectNode();
        ArrayNode bedrockTools = JsonCodec.mapper().createArrayNode();
        for (Tool tool : tools) {
            ObjectNode wrapper = JsonCodec.mapper().createObjectNode();
            ObjectNode spec = JsonCodec.mapper().createObjectNode();
            spec.put("name", tool.name());
            spec.put("description", tool.description());
            ObjectNode inputSchema = JsonCodec.mapper().createObjectNode();
            JsonNode parameters = tool.parameters() == null ? JsonCodec.mapper().createObjectNode() : tool.parameters();
            inputSchema.set("json", parameters);
            spec.set("inputSchema", inputSchema);
            wrapper.set("toolSpec", spec);
            bedrockTools.add(wrapper);
        }
        toolConfig.set("tools", bedrockTools);
        ObjectNode choice = JsonCodec.mapper().createObjectNode();
        choice.set("auto", JsonCodec.mapper().createObjectNode());
        toolConfig.set("toolChoice", choice);
        return toolConfig;
    }

    private static ObjectNode convertRequestMetadata(Map<String, Object> metadata) {
        ObjectNode requestMetadata = JsonCodec.mapper().createObjectNode();
        if (metadata == null || !(metadata.get("requestMetadata") instanceof Map<?, ?> raw)) {
            return requestMetadata;
        }
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() instanceof String key && entry.getValue() != null) {
                requestMetadata.put(key, String.valueOf(entry.getValue()));
            }
        }
        return requestMetadata;
    }

    private static ObjectNode buildAdditionalModelRequestFields(Model model, Context context, StreamOptions options) {
        ObjectNode fields = JsonCodec.mapper().createObjectNode();
        if (!Boolean.TRUE.equals(model.options().get("reasoning")) || context == null
                || context.thinkingLevel() == null || context.thinkingLevel() == ThinkingLevel.OFF
                || !isAnthropicClaudeModel(model)) {
            return fields;
        }

        String display = optionString(options, "thinkingDisplay");
        if (display == null) {
            display = "summarized";
        }
        if (supportsAdaptiveThinking(model)) {
            ObjectNode thinking = fields.putObject("thinking");
            thinking.put("type", "adaptive");
            thinking.put("display", display);
            ObjectNode outputConfig = fields.putObject("output_config");
            outputConfig.put("effort", thinkingEffort(model, context.thinkingLevel()));
        } else {
            ObjectNode thinking = fields.putObject("thinking");
            thinking.put("type", "enabled");
            thinking.put("budget_tokens", thinkingBudget(context.thinkingLevel()));
            thinking.put("display", display);
            ArrayNode beta = JsonCodec.mapper().createArrayNode();
            beta.add("interleaved-thinking-2025-05-14");
            fields.set("anthropic_beta", beta);
        }
        return fields;
    }

    private static String normalizeToolCallId(String id) {
        if (id == null || id.isBlank()) {
            return "tool";
        }
        String sanitized = id.replaceAll("[^a-zA-Z0-9_-]", "_");
        return sanitized.length() > 64 ? sanitized.substring(0, 64) : sanitized;
    }

    private static boolean isAnthropicClaudeModel(Model model) {
        String candidates = (model.modelId() + " " + model.displayName()).toLowerCase();
        return candidates.contains("anthropic.claude") || candidates.contains("claude");
    }

    private static boolean supportsPromptCaching(Model model, StreamOptions options) {
        String candidates = (model.modelId() + " " + model.displayName()).toLowerCase();
        if ("1".equals(envValue(options, "AWS_BEDROCK_FORCE_CACHE"))) {
            return true;
        }
        if (!candidates.contains("claude")) {
            return false;
        }
        return candidates.contains("-4-")
                || candidates.contains("claude-3-7-sonnet")
                || candidates.contains("claude-3-5-haiku");
    }

    private static CacheRetention resolveCacheRetention(StreamOptions options) {
        if (options != null && options.cacheRetention() != null) {
            return options.cacheRetention();
        }
        if ("long".equalsIgnoreCase(envValue(options, "PI_CACHE_RETENTION"))) {
            return CacheRetention.LONG;
        }
        return CacheRetention.SHORT;
    }

    private static boolean supportsAdaptiveThinking(Model model) {
        String candidates = (model.modelId() + " " + model.displayName()).toLowerCase();
        return candidates.contains("opus-4-6")
                || candidates.contains("opus-4-7")
                || candidates.contains("opus-4-8")
                || candidates.contains("sonnet-4-6")
                || candidates.contains("fable-5");
    }

    private static int thinkingBudget(ThinkingLevel level) {
        return switch (level) {
            case MINIMAL -> 1024;
            case LOW -> 2048;
            case MEDIUM -> 8192;
            case HIGH, XHIGH -> 16384;
            case OFF -> 0;
        };
    }

    private static String thinkingEffort(Model model, ThinkingLevel level) {
        if (level == ThinkingLevel.XHIGH && supportsNativeXhighEffort(model)) {
            return "xhigh";
        }
        return switch (level) {
            case MINIMAL, LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH, XHIGH, OFF -> "high";
        };
    }

    private static boolean supportsNativeXhighEffort(Model model) {
        String candidates = (model.modelId() + " " + model.displayName()).toLowerCase();
        return candidates.contains("opus-4-7")
                || candidates.contains("opus-4-8")
                || candidates.contains("fable-5");
    }

    private static String optionString(StreamOptions options, String key) {
        if (options == null || options.metadata() == null || !(options.metadata().get(key) instanceof String value)
                || value.isBlank()) {
            return null;
        }
        return value;
    }

    private static String envValue(StreamOptions options, String key) {
        if (options != null && options.env() != null) {
            if (options.env().containsKey(key)) {
                String value = options.env().get(key);
                return value == null || value.isBlank() ? null : value;
            }
        }
        String value = System.getenv(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static String modelBaseUrl(Model model) {
        if (model.options() != null && model.options().get("baseUrl") instanceof String baseUrl && !baseUrl.isBlank()) {
            return baseUrl;
        }
        return model.api();
    }

    private static String standardEndpointRegion(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return null;
        }
        try {
            String host = URI.create(baseUrl).getHost();
            if (host == null) {
                return null;
            }
            Matcher matcher = STANDARD_BEDROCK_ENDPOINT.matcher(host.toLowerCase());
            return matcher.matches() ? matcher.group(1) : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        AssistantMessageEventStream stream = new AssistantMessageEventStream();
        CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(50);
                stream.emit(new AssistantMessageEvent.Start(ID, model.modelId()));

                if (!hasBedrockCredentials(options)) {
                    stream.emit(new AssistantMessageEvent.Error("Missing AWS credentials for provider: " + ID,
                            new IllegalStateException("Missing AWS credentials: set AWS_PROFILE, AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY, AWS_BEARER_TOKEN_BEDROCK, ECS/IRSA credentials, or AWS_BEDROCK_SKIP_AUTH=1 for local proxy testing")));
                    return;
                }

                stream.emit(new AssistantMessageEvent.Error("AWS Bedrock Converse API is not implemented in the Java provider yet",
                        new UnsupportedOperationException("amazon-bedrock requires AWS SigV4 signing and Bedrock Converse streaming support")));
            } catch (Exception e) {
                stream.emit(new AssistantMessageEvent.Error(e.getMessage(), e));
            } finally {
                stream.close();
            }
        });
        return stream;
    }
}
