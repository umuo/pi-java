package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ProviderRegistry {
    private final Map<String, Provider> providers = new LinkedHashMap<>();

    public void register(Provider provider) {
        providers.put(provider.id(), provider);
    }

    public void registerDefaults() {
        register(new OpenAiProvider());
        register(new AnthropicProvider());
        register(new GeminiProvider());
        register(new GroqProvider());
        register(new MistralProvider());
        register(new OllamaProvider());
        register(new XaiProvider());
    }

    public Optional<Provider> provider(String id) {
        if (id == null) return Optional.empty();
        Optional<Provider> exact = Optional.ofNullable(providers.get(id));
        if (exact.isPresent()) return exact;
        String normalized = id.toLowerCase();
        if (normalized.startsWith("openai")) return Optional.ofNullable(providers.get("openai"));
        if (normalized.startsWith("anthropic")) return Optional.ofNullable(providers.get("anthropic"));
        if (normalized.equals("gemini") || normalized.equals("google-generative-ai")) {
            return Optional.ofNullable(providers.get("google"));
        }
        return Optional.empty();
    }

    public List<Model> models() {
        List<Model> result = new ArrayList<>();
        for (Provider provider : providers.values()) {
            result.addAll(provider.models());
        }
        return List.copyOf(result);
    }

    public void refreshModels() {
        for (Provider provider : providers.values()) {
            provider.refreshModels();
        }
    }

    public Optional<List<Model>> refreshModels(String providerId) {
        return provider(providerId).map(Provider::refreshModels);
    }

    public Optional<Model> findModel(String providerId, String modelId) {
        return provider(providerId).flatMap(provider -> provider.models().stream()
                .filter(model -> model.modelId().equals(modelId))
                .findFirst());
    }
}
