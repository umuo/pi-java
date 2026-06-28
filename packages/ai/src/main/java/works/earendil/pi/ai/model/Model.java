package works.earendil.pi.ai.model;

import java.util.Map;

public record Model(
        String provider,
        String modelId,
        String displayName,
        String api,
        int contextWindow,
        int maxOutputTokens,
        boolean supportsTools,
        boolean supportsImages,
        Map<String, Object> options
) {
}
