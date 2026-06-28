package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.provider.Provider;
import works.earendil.pi.ai.provider.ProviderRegistry;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelRegistryTest {
    @TempDir
    Path tempDir;

    @Test
    void loadsJsoncModelsOverridesAndResolvesRequestAuth() throws Exception {
        Path modelsJson = tempDir.resolve("models.json");
        Files.writeString(modelsJson, """
                {
                  "providers": {
                    "openai": {
                      "name": "OpenAI Custom",
                      "baseUrl": "https://override.example/v1",
                      "headers": { "X-Provider": "$PROVIDER_HEADER" },
                      "authHeader": true,
                      "apiKey": "$OPENAI_TOKEN",
                      "modelOverrides": {
                        "gpt-5.5": {
                          "name": "GPT Override",
                          "contextWindow": 222,
                          "headers": { "X-Model": "model-header" },
                        },
                      },
                    },
                    "local": {
                      "name": "Local Provider",
                      "baseUrl": "http://localhost:11434/v1",
                      "api": "openai-chat",
                      "apiKey": "literal-key",
                      "models": [
                        {
                          "id": "llama",
                          "name": "Llama",
                          "input": ["text", "image"],
                          "contextWindow": 4096,
                          "maxTokens": 1024,
                          "headers": { "X-Local": "yes" },
                        },
                      ],
                    },
                  },
                }
                """);
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("$OPENAI_TOKEN",
                Map.of("OPENAI_TOKEN", "from-env", "PROVIDER_HEADER", "provider-header")));

        ModelRegistry registry = ModelRegistry.create(builtIns(), authStorage, modelsJson);

        Model overridden = registry.find("openai", "gpt-5.5").orElseThrow();
        Model local = registry.find("local", "llama").orElseThrow();
        ModelRegistry.ResolvedRequestAuth auth = registry.getApiKeyAndHeaders(overridden);

        assertThat(registry.error()).isEmpty();
        assertThat(overridden.displayName()).isEqualTo("GPT Override");
        assertThat(overridden.contextWindow()).isEqualTo(222);
        assertThat(overridden.options()).containsEntry("baseUrl", "https://override.example/v1");
        assertThat(local.supportsImages()).isTrue();
        assertThat(registry.getProviderDisplayName("openai")).isEqualTo("OpenAI Custom");
        assertThat(registry.getProviderAuthStatus("local").source())
                .isEqualTo(AuthStorage.AuthStatus.Source.MODELS_JSON_KEY);
        assertThat(auth).isInstanceOfSatisfying(ModelRegistry.ResolvedRequestAuth.Ok.class, ok -> {
            assertThat(ok.apiKey()).isEqualTo("from-env");
            assertThat(ok.headers()).containsEntry("X-Provider", "provider-header");
            assertThat(ok.headers()).containsEntry("X-Model", "model-header");
            assertThat(ok.headers()).containsEntry("Authorization", "Bearer from-env");
        });
    }

    @Test
    void customModelsReplaceBuiltInsAndDynamicRegistrationCanUnregister() {
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("dynamic", new AuthStorage.ApiKeyCredential("key", null));
        ModelRegistry registry = ModelRegistry.inMemory(builtIns(), authStorage);
        ModelRegistry.ProviderConfigInput config = new ModelRegistry.ProviderConfigInput(
                "Dynamic",
                "https://dynamic.example",
                "key",
                "openai-chat",
                Map.of("X-Dynamic", "1"),
                false,
                List.of(new ModelRegistry.ModelDefinition(
                        "dyn-1",
                        "Dynamic One",
                        null,
                        null,
                        false,
                        List.of("text"),
                        1000,
                        100,
                        null,
                        Map.of())));

        registry.registerProvider("dynamic", config);

        assertThat(registry.find("dynamic", "dyn-1")).isPresent();
        assertThat(registry.getProviderDisplayName("dynamic")).isEqualTo("Dynamic");

        registry.unregisterProvider("dynamic");

        assertThat(registry.find("dynamic", "dyn-1")).isEmpty();
    }

    @Test
    void modelResolverMatchesExactPartialThinkingGlobAndFallbacks() {
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("key", null));
        authStorage.set("openrouter", new AuthStorage.ApiKeyCredential("key", null));
        ModelRegistry registry = ModelRegistry.inMemory(builtIns(), authStorage);
        List<Model> all = registry.getAll();

        assertThat(ModelResolver.findExactModelReferenceMatch("openai/gpt-5.5", all))
                .hasValueSatisfying(model -> assertThat(model.provider()).isEqualTo("openai"));
        assertThat(ModelResolver.parseModelPattern("sonnet:high", all).thinkingLevel())
                .isEqualTo(ThinkingLevel.HIGH);
        assertThat(ModelResolver.resolveModelScope(List.of("openai/gpt-5*:low"), registry))
                .allSatisfy(scoped -> {
                    assertThat(scoped.model().provider()).isEqualTo("openai");
                    assertThat(scoped.thinkingLevel()).isEqualTo(ThinkingLevel.LOW);
                })
                .hasSize(2);

        ModelResolver.ResolveCliModelResult cli = ModelResolver.resolveCliModel(
                "openai", "new-model:high", null, registry);

        assertThat(cli.model()).isNotNull();
        assertThat(cli.model().modelId()).isEqualTo("new-model");
        assertThat(cli.thinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
        assertThat(cli.warning()).contains("Using custom model id");
    }

    @Test
    void modelResolverInitialAndRestoreUseAvailableDefaults() {
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("key", null));
        ModelRegistry registry = ModelRegistry.inMemory(builtIns(), authStorage);

        ModelResolver.InitialModelResult initial = ModelResolver.findInitialModel(
                null, null, List.of(), false, null, null, null, registry);
        ModelResolver.RestoreModelResult restored = ModelResolver.restoreModelFromSession(
                "openai", "missing", initial.model(), registry);

        assertThat(initial.model().provider()).isEqualTo("openai");
        assertThat(initial.model().modelId()).isEqualTo("gpt-5.5");
        assertThat(initial.thinkingLevel()).isEqualTo(ThinkingLevel.MEDIUM);
        assertThat(restored.model()).isEqualTo(initial.model());
        assertThat(restored.fallbackMessage()).contains("model no longer exists");
    }

    @Test
    void reportsModelsJsonValidationErrorsButKeepsBuiltIns() throws Exception {
        Path modelsJson = tempDir.resolve("models.json");
        Files.writeString(modelsJson, """
                { "providers": { "custom": { "models": [ { "id": "m" } ] } } }
                """);

        ModelRegistry registry = ModelRegistry.create(builtIns(), AuthStorage.inMemory(), modelsJson);

        assertThat(registry.error()).hasValueSatisfying(error -> assertThat(error).contains("baseUrl"));
        assertThat(registry.find("openai", "gpt-5.5")).isPresent();
    }

    @Test
    void rejectsInvalidDynamicProviderDefinitions() {
        ModelRegistry registry = ModelRegistry.inMemory(builtIns(), AuthStorage.inMemory());

        assertThatThrownBy(() -> registry.registerProvider("bad", new ModelRegistry.ProviderConfigInput(
                null, null, "key", "api", null, false,
                List.of(new ModelRegistry.ModelDefinition("m", "M", null, null, false, List.of("text"),
                        1, 1, null, Map.of())))))
                .hasMessageContaining("baseUrl");
    }

    private static ProviderRegistry builtIns() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StaticProvider("openai", List.of(
                model("openai", "gpt-5.5", "GPT 5.5", "https://api.openai.example/v1"),
                model("openai", "gpt-5.5-20250101", "GPT dated", "https://api.openai.example/v1"))));
        registry.register(new StaticProvider("anthropic", List.of(
                model("anthropic", "claude-sonnet-4-20250929", "Claude Sonnet", "https://api.anthropic.example"),
                model("anthropic", "claude-sonnet-4", "Claude Sonnet Alias", "https://api.anthropic.example"))));
        registry.register(new StaticProvider("openrouter", List.of(
                model("openrouter", "openai/gpt-4o:extended", "OpenRouter GPT", "https://openrouter.ai/api/v1"))));
        return registry;
    }

    private static Model model(String provider, String id, String name, String baseUrl) {
        return new Model(provider, id, name, "openai-chat", 128000, 16384, true, false,
                Map.of("baseUrl", baseUrl));
    }

    private record StaticProvider(String id, List<Model> models) implements Provider {
        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            return null;
        }
    }
}
