package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.ai.model.ImageGenModel;
import works.earendil.pi.common.json.JsonCodec;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OpenRouterImagesProvider implements ImageGenerationProvider {
    private static final String DEFAULT_API = "https://openrouter.ai/api/v1";
    private static final Pattern DATA_URL = Pattern.compile("^data:([^;]+);base64,(.+)$");
    private final List<ImageGenModel> imageModels;

    public OpenRouterImagesProvider() {
        this(List.of(
                model("google/gemini-3.1-flash-image-preview", "Gemini 3.1 Flash Image Preview"),
                model("black-forest-labs/flux.2-pro", "FLUX.2 Pro")
        ));
    }

    OpenRouterImagesProvider(List<ImageGenModel> imageModels) {
        this.imageModels = List.copyOf(imageModels);
    }

    @Override
    public String id() {
        return "openrouter";
    }

    @Override
    public List<ImageGenModel> imageModels() {
        return imageModels;
    }

    @Override
    public ImageGenModel.Response generateImages(ImageGenModel model, ImageGenModel.Request request,
                                                 ImageGenerationOptions options) {
        try {
            String apiKey = resolveApiKey(options);
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalStateException("Missing API key for image generation provider: " + id());
            }

            JsonNode payload = applyBeforeImageRequest(options, model, buildRequestBody(model, request));
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(chatCompletionsEndpoint(model)))
                    .timeout(requestTimeout(options))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(JsonCodec.stringify(payload), StandardCharsets.UTF_8));

            if (options != null && options.headers() != null) {
                options.headers().forEach((key, value) -> {
                    if (key != null && value != null
                            && !key.equalsIgnoreCase("authorization")
                            && !key.equalsIgnoreCase("content-type")) {
                        reqBuilder.header(key, value);
                    }
                });
            }

            HttpClient client = ProviderHttpSupport.client();
            HttpResponse<InputStream> response = ProviderHttpSupport.sendWithRetries(id(), reqBuilder.build(),
                    httpRequest -> client.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream()),
                    ProviderHttpSupport.retryPolicy(id(), toStreamOptions(options)));
            emitAfterImageResponse(options, model, response);
            String responseBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new RuntimeException("HTTP " + response.statusCode() + " from " + chatCompletionsEndpoint(model)
                        + ": " + responseBody);
            }
            return parseResponse(model, JsonCodec.parse(responseBody));
        } catch (Exception e) {
            throw new RuntimeException("Image generation failed for " + model.provider() + "/" + model.modelId(), e);
        }
    }

    static ObjectNode buildRequestBody(ImageGenModel model, ImageGenModel.Request request) {
        ObjectNode body = JsonCodec.mapper().createObjectNode();
        body.put("model", model.modelId());
        body.put("stream", false);
        body.putArray("modalities").add("image").add("text");
        var messages = body.putArray("messages");
        var user = messages.addObject();
        user.put("role", "user");
        var content = user.putArray("content");
        content.addObject()
                .put("type", "text")
                .put("text", request == null || request.prompt() == null ? "" : request.prompt());
        if (request != null && request.n() > 0) {
            body.put("n", request.n());
        }
        if (request != null && request.aspectRatio() != null && !request.aspectRatio().isBlank()) {
            body.put("aspect_ratio", request.aspectRatio());
        }
        if (request != null && request.resolution() != null && !request.resolution().isBlank()) {
            body.put("size", request.resolution());
        }
        if (request != null && request.options() != null) {
            request.options().forEach((key, value) -> body.set(key, JsonCodec.mapper().valueToTree(value)));
        }
        return body;
    }

    static ImageGenModel.Response parseResponse(ImageGenModel model, JsonNode response) {
        List<ImageGenModel.GeneratedImage> images = new ArrayList<>();
        JsonNode choice = response.path("choices").isArray() && !response.path("choices").isEmpty()
                ? response.path("choices").get(0)
                : null;
        if (choice == null) {
            return new ImageGenModel.Response(List.of(), model.provider(), model.modelId());
        }

        String revisedPrompt = choice.path("message").path("content").asText(null);
        JsonNode imageNodes = choice.path("message").path("images");
        if (imageNodes.isArray()) {
            for (JsonNode imageNode : imageNodes) {
                String imageUrl = imageUrl(imageNode);
                if (imageUrl == null || imageUrl.isBlank()) {
                    continue;
                }
                Matcher matcher = DATA_URL.matcher(imageUrl);
                if (matcher.matches()) {
                    images.add(new ImageGenModel.GeneratedImage(matcher.group(2), matcher.group(1), null,
                            revisedPrompt));
                } else {
                    images.add(new ImageGenModel.GeneratedImage(null, null, imageUrl, revisedPrompt));
                }
            }
        }
        return new ImageGenModel.Response(List.copyOf(images), model.provider(), model.modelId());
    }

    private static ImageGenModel model(String modelId, String displayName) {
        return new ImageGenModel("openrouter", modelId, displayName, List.of(), List.of(),
                Map.of("baseUrl", DEFAULT_API, "api", "openrouter-images"));
    }

    private static String imageUrl(JsonNode imageNode) {
        JsonNode imageUrl = imageNode.get("image_url");
        if (imageUrl == null || imageUrl.isNull()) {
            return null;
        }
        if (imageUrl.isTextual()) {
            return imageUrl.asText();
        }
        return imageUrl.path("url").asText(null);
    }

    private static String resolveApiKey(ImageGenerationOptions options) {
        if (options != null && options.apiKey() != null && !options.apiKey().isBlank()) {
            return options.apiKey();
        }
        if (options != null && options.env() != null) {
            String fromEnvMap = options.env().get("OPENROUTER_API_KEY");
            if (fromEnvMap != null && !fromEnvMap.isBlank()) {
                return fromEnvMap;
            }
        }
        return System.getenv("OPENROUTER_API_KEY");
    }

    private static String chatCompletionsEndpoint(ImageGenModel model) {
        String baseUrl = model.defaultOptions() != null && model.defaultOptions().get("baseUrl") instanceof String value
                ? value
                : DEFAULT_API;
        return baseUrl.replaceAll("/+$", "") + "/chat/completions";
    }

    private static Duration requestTimeout(ImageGenerationOptions options) {
        return options != null && options.timeout() != null ? options.timeout() : Duration.ofMinutes(10);
    }

    private static StreamOptions toStreamOptions(ImageGenerationOptions options) {
        if (options == null) {
            return StreamOptions.defaults();
        }
        return new StreamOptions(null, null, options.apiKey(), null, null, null,
                options.headers() == null ? Map.of() : options.headers(), options.timeout(), options.maxRetries(),
                options.env() == null ? Map.of() : options.env(),
                options.metadata() == null ? Map.of() : options.metadata());
    }

    private static JsonNode applyBeforeImageRequest(ImageGenerationOptions options, ImageGenModel model,
                                                    JsonNode payload) throws Exception {
        if (options == null || options.providerHooks() == null || options.providerHooks().beforeRequest() == null) {
            return payload;
        }
        Object result = options.providerHooks().beforeRequest().beforeRequest(payload, model);
        if (result == null) {
            return payload;
        }
        if (result instanceof JsonNode node) {
            return node;
        }
        return JsonCodec.mapper().valueToTree(result);
    }

    private static void emitAfterImageResponse(ImageGenerationOptions options, ImageGenModel model,
                                               HttpResponse<?> response) throws Exception {
        if (options == null || options.providerHooks() == null || options.providerHooks().afterResponse() == null
                || response == null) {
            return;
        }
        options.providerHooks().afterResponse().afterResponse(response.statusCode(),
                response.headers().map().entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey,
                                entry -> String.join(", ", entry.getValue()))),
                model);
    }
}
