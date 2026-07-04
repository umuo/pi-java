package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.ImageGenModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ImageGenerationRegistry {
    private final Map<String, ImageGenerationProvider> providers = new LinkedHashMap<>();

    public void registerDefaults() {
        register(new OpenRouterImagesProvider());
    }

    public void register(ImageGenerationProvider provider) {
        providers.put(provider.id(), provider);
    }

    public Optional<ImageGenerationProvider> provider(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public List<ImageGenerationProvider> providers() {
        return List.copyOf(providers.values());
    }

    public List<ImageGenModel> imageModels() {
        List<ImageGenModel> result = new ArrayList<>();
        for (ImageGenerationProvider provider : providers.values()) {
            result.addAll(provider.imageModels());
        }
        return List.copyOf(result);
    }

    public Optional<ImageGenModel> findModel(String providerId, String modelId) {
        ImageGenerationProvider provider = providers.get(providerId);
        if (provider == null) {
            return Optional.empty();
        }
        return provider.imageModels().stream()
                .filter(model -> model.modelId().equals(modelId))
                .findFirst();
    }

    public ImageGenModel.Response generateImages(ImageGenModel model, ImageGenModel.Request request,
                                                 ImageGenerationOptions options) {
        ImageGenerationProvider provider = providers.get(model.provider());
        if (provider == null) {
            throw new IllegalArgumentException("Unknown image generation provider: " + model.provider());
        }
        return provider.generateImages(model, request, options);
    }
}
