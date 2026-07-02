package works.earendil.pi.ai.model;

import java.util.List;
import java.util.Map;

public record ImageGenModel(
        String provider,
        String modelId,
        String displayName,
        List<String> supportedAspectRatios,
        List<String> supportedResolutions,
        Map<String, Object> defaultOptions
) {
    public record Request(
            String prompt,
            String aspectRatio,
            String resolution,
            int n,
            Map<String, Object> options
    ) {}

    public record Response(
            List<GeneratedImage> images,
            String provider,
            String modelId
    ) {}

    public record GeneratedImage(
            String base64Data,
            String mimeType,
            String url,
            String revisedPrompt
    ) {}
}
