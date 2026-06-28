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

    public Optional<Provider> provider(String id) {
        return Optional.ofNullable(providers.get(id));
    }

    public List<Model> models() {
        List<Model> result = new ArrayList<>();
        for (Provider provider : providers.values()) {
            result.addAll(provider.models());
        }
        return List.copyOf(result);
    }

    public Optional<Model> findModel(String providerId, String modelId) {
        return provider(providerId).flatMap(provider -> provider.models().stream()
                .filter(model -> model.modelId().equals(modelId))
                .findFirst());
    }
}
