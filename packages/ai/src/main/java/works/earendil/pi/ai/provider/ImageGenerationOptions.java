package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.ImageGenModel;

import java.time.Duration;
import java.util.Map;

public record ImageGenerationOptions(
        String apiKey,
        Map<String, String> headers,
        Duration timeout,
        Integer maxRetries,
        Map<String, String> env,
        Map<String, Object> metadata,
        ImageGenerationHooks providerHooks
) {
    public ImageGenerationOptions(String apiKey, Map<String, String> headers, Duration timeout, Integer maxRetries,
                                  Map<String, String> env, Map<String, Object> metadata) {
        this(apiKey, headers, timeout, maxRetries, env, metadata, null);
    }

    public static ImageGenerationOptions defaults() {
        return new ImageGenerationOptions(null, Map.of(), Duration.ofMinutes(10), 2, Map.of(), Map.of(), null);
    }

    public record ImageGenerationHooks(ImagePayloadHook beforeRequest, ImageResponseHook afterResponse) {
    }

    @FunctionalInterface
    public interface ImagePayloadHook {
        Object beforeRequest(Object payload, ImageGenModel model) throws Exception;
    }

    @FunctionalInterface
    public interface ImageResponseHook {
        void afterResponse(int status, Map<String, String> headers, ImageGenModel model) throws Exception;
    }
}
