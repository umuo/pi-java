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
        register(new DeepSeekProvider());
        register(new TogetherProvider());
        register(new OpenRouterProvider());
        register(new CerebrasProvider());
        register(new FireworksProvider());
        register(new MoonshotProvider());
        register(new MoonshotChinaProvider());
        register(new QwenTokenPlanProvider());
        register(new QwenTokenPlanCnProvider());
        register(new ZaiProvider());
        register(new BedrockProvider());
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
        if (normalized.startsWith("deepseek")) return Optional.ofNullable(providers.get("deepseek"));
        if (normalized.startsWith("together")) return Optional.ofNullable(providers.get("together"));
        if (normalized.startsWith("openrouter")) return Optional.ofNullable(providers.get("openrouter"));
        if (normalized.startsWith("cerebras")) return Optional.ofNullable(providers.get("cerebras"));
        if (normalized.startsWith("fireworks")) return Optional.ofNullable(providers.get("fireworks"));
        if (normalized.equals("moonshot") || normalized.equals("kimi")) {
            return Optional.ofNullable(providers.get("moonshotai"));
        }
        if (normalized.startsWith("moonshotai-cn")) return Optional.ofNullable(providers.get("moonshotai-cn"));
        if (normalized.startsWith("moonshot")) return Optional.ofNullable(providers.get("moonshotai"));
        if (normalized.startsWith("qwen-token-plan-cn")) return Optional.ofNullable(providers.get("qwen-token-plan-cn"));
        if (normalized.startsWith("qwen-token-plan")) return Optional.ofNullable(providers.get("qwen-token-plan"));
        if (normalized.startsWith("zai") || normalized.startsWith("zhipu") || normalized.startsWith("glm")) return Optional.ofNullable(providers.get("zai"));
        if (normalized.startsWith("bedrock") || normalized.startsWith("amazon")) return Optional.ofNullable(providers.get("amazon-bedrock"));
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
