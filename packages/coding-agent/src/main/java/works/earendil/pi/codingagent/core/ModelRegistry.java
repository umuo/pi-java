package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.provider.ProviderRegistry;
import works.earendil.pi.codingagent.util.JsonUtils;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ModelRegistry {
    private final ProviderRegistry builtInRegistry;
    private final AuthStorage authStorage;
    private final Path modelsJsonPath;
    private final Map<String, ProviderRequestConfig> providerRequestConfigs = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> providerOverrides = new LinkedHashMap<>();
    private final Map<String, Map<String, Map<String, Object>>> modelOverrides = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> modelRequestHeaders = new LinkedHashMap<>();
    private final Map<String, ProviderConfigInput> registeredProviders = new LinkedHashMap<>();
    private List<Model> models = List.of();
    private String loadError;

    private ModelRegistry(ProviderRegistry builtInRegistry, AuthStorage authStorage, Path modelsJsonPath) {
        this.builtInRegistry = builtInRegistry;
        this.authStorage = authStorage;
        this.modelsJsonPath = modelsJsonPath;
        loadModels();
    }

    public record ProviderRequestConfig(String apiKey, Map<String, String> headers, boolean authHeader) {
    }

    public sealed interface ResolvedRequestAuth permits ResolvedRequestAuth.Ok, ResolvedRequestAuth.Error {
        boolean ok();

        record Ok(String apiKey, Map<String, String> headers, Map<String, String> env) implements ResolvedRequestAuth {
            @Override
            public boolean ok() {
                return true;
            }
        }

        record Error(String error) implements ResolvedRequestAuth {
            @Override
            public boolean ok() {
                return false;
            }
        }
    }

    public record ProviderConfigInput(
            String name,
            String baseUrl,
            String apiKey,
            String api,
            Map<String, String> headers,
            boolean authHeader,
            List<ModelDefinition> models) {
    }

    public record ModelDefinition(
            String id,
            String name,
            String api,
            String baseUrl,
            boolean reasoning,
            List<String> input,
            int contextWindow,
            int maxTokens,
            Map<String, String> headers,
            Map<String, Object> options) {
    }

    public static ModelRegistry create(ProviderRegistry builtInRegistry, AuthStorage authStorage, Path modelsJsonPath) {
        return new ModelRegistry(builtInRegistry, authStorage, modelsJsonPath);
    }

    public static ModelRegistry inMemory(ProviderRegistry builtInRegistry, AuthStorage authStorage) {
        return new ModelRegistry(builtInRegistry, authStorage, null);
    }

    public void refresh() {
        providerRequestConfigs.clear();
        providerOverrides.clear();
        modelOverrides.clear();
        modelRequestHeaders.clear();
        loadError = null;
        loadModels();
        for (Map.Entry<String, ProviderConfigInput> entry : registeredProviders.entrySet()) {
            applyProviderConfig(entry.getKey(), entry.getValue());
        }
    }

    public Optional<String> error() {
        return Optional.ofNullable(loadError);
    }

    public List<Model> getAll() {
        return models;
    }

    public List<Model> getAvailable() {
        return models.stream().filter(this::hasConfiguredAuth).toList();
    }

    public Optional<Model> find(String provider, String modelId) {
        return models.stream()
                .filter(model -> model.provider().equals(provider) && model.modelId().equals(modelId))
                .findFirst();
    }

    public boolean hasConfiguredAuth(Model model) {
        ProviderRequestConfig requestConfig = providerRequestConfigs.get(model.provider());
        return authStorage.hasAuth(model.provider())
                || (requestConfig != null
                && requestConfig.apiKey() != null
                && ConfigValueResolver.isConfigValueConfigured(requestConfig.apiKey(), authStorage.getProviderEnv(model.provider())));
    }

    public ResolvedRequestAuth getApiKeyAndHeaders(Model model) {
        try {
            ProviderRequestConfig providerConfig = providerRequestConfigs.get(model.provider());
            Map<String, String> providerEnv = authStorage.getProviderEnv(model.provider());
            Optional<String> apiKeyFromAuthStorage = authStorage.getApiKey(model.provider(),
                    new AuthStorage.GetApiKeyOptions(false));
            String apiKey = apiKeyFromAuthStorage.orElse(null);
            if (apiKey == null && providerConfig != null && providerConfig.apiKey() != null) {
                apiKey = ConfigValueResolver.resolveConfigValueOrThrow(providerConfig.apiKey(),
                        "API key for provider \"" + model.provider() + "\"", providerEnv);
            }

            Map<String, String> headers = new LinkedHashMap<>();
            headers.putAll(stringMap(model.options().get("headers")));
            if (providerConfig != null && providerConfig.headers() != null) {
                Map<String, String> providerHeaders = ConfigValueResolver.resolveHeadersOrThrow(
                        providerConfig.headers(), "provider \"" + model.provider() + "\"", providerEnv);
                if (providerHeaders != null) {
                    headers.putAll(providerHeaders);
                }
            }
            Map<String, String> modelHeaders = modelRequestHeaders.get(modelRequestKey(model.provider(), model.modelId()));
            if (modelHeaders != null) {
                Map<String, String> resolvedModelHeaders = ConfigValueResolver.resolveHeadersOrThrow(
                        modelHeaders, "model \"" + model.provider() + "/" + model.modelId() + "\"", providerEnv);
                if (resolvedModelHeaders != null) {
                    headers.putAll(resolvedModelHeaders);
                }
            }
            if (providerConfig != null && providerConfig.authHeader()) {
                if (apiKey == null || apiKey.isBlank()) {
                    return new ResolvedRequestAuth.Error("No API key found for \"" + model.provider() + "\"");
                }
                headers.put("Authorization", "Bearer " + apiKey);
            }
            return new ResolvedRequestAuth.Ok(apiKey, headers.isEmpty() ? null : Map.copyOf(headers),
                    providerEnv == null || providerEnv.isEmpty() ? null : providerEnv);
        } catch (RuntimeException e) {
            return new ResolvedRequestAuth.Error(e.getMessage());
        }
    }

    public AuthStorage.AuthStatus getProviderAuthStatus(String provider) {
        AuthStorage.AuthStatus authStatus = authStorage.getAuthStatus(provider);
        if (authStatus.source() != null) {
            return authStatus;
        }
        ProviderRequestConfig requestConfig = providerRequestConfigs.get(provider);
        String providerApiKey = requestConfig == null ? null : requestConfig.apiKey();
        if (providerApiKey == null) {
            return authStatus;
        }
        if (ConfigValueResolver.isCommandConfigValue(providerApiKey)) {
            return new AuthStorage.AuthStatus(true, AuthStorage.AuthStatus.Source.MODELS_JSON_COMMAND, null);
        }
        List<String> envVarNames = ConfigValueResolver.getConfigValueEnvVarNames(providerApiKey);
        if (!envVarNames.isEmpty()) {
            boolean configured = ConfigValueResolver.isConfigValueConfigured(providerApiKey, authStorage.getProviderEnv(provider));
            return configured
                    ? new AuthStorage.AuthStatus(true, AuthStorage.AuthStatus.Source.ENVIRONMENT,
                    String.join(", ", envVarNames))
                    : new AuthStorage.AuthStatus(false, null, null);
        }
        return new AuthStorage.AuthStatus(true, AuthStorage.AuthStatus.Source.MODELS_JSON_KEY, null);
    }

    public String getProviderDisplayName(String provider) {
        ProviderConfigInput registered = registeredProviders.get(provider);
        if (registered != null && registered.name() != null) {
            return registered.name();
        }
        Object configuredName = providerOverrides.getOrDefault(provider, Map.of()).get("name");
        if (configuredName instanceof String name && !name.isBlank()) {
            return name;
        }
        return ProviderDisplayNames.displayName(provider).orElse(provider);
    }

    public Optional<String> getApiKeyForProvider(String provider) {
        Optional<String> apiKey = authStorage.getApiKey(provider);
        if (apiKey.isPresent()) {
            return apiKey;
        }
        ProviderRequestConfig config = providerRequestConfigs.get(provider);
        return config == null || config.apiKey() == null
                ? Optional.empty()
                : ConfigValueResolver.resolveConfigValueUncached(config.apiKey(), authStorage.getProviderEnv(provider));
    }

    public boolean isUsingOAuth(Model model) {
        return authStorage.get(model.provider()).orElse(null) instanceof AuthStorage.OAuthCredential;
    }

    public ProviderRegistry builtInRegistry() {
        return builtInRegistry;
    }

    public void registerProvider(String providerName, ProviderConfigInput config) {
        validateProviderConfig(providerName, config);
        applyProviderConfig(providerName, config);
        registeredProviders.merge(providerName, config, ModelRegistry::mergeProviderConfig);
    }

    public void unregisterProvider(String providerName) {
        if (registeredProviders.remove(providerName) != null) {
            refresh();
        }
    }

    private void loadModels() {
        CustomModelsResult custom = modelsJsonPath == null ? CustomModelsResult.empty() : loadCustomModels(modelsJsonPath);
        if (custom.error() != null) {
            loadError = custom.error();
        }
        providerOverrides.putAll(custom.providerOverrides());
        modelOverrides.putAll(custom.modelOverrides());
        List<Model> builtIns = loadBuiltInModels();
        models = mergeCustomModels(builtIns, custom.models());
    }

    private List<Model> loadBuiltInModels() {
        List<Model> loaded = new ArrayList<>();
        for (Model model : builtInRegistry.models()) {
            Model current = applyProviderOverride(model, providerOverrides.get(model.provider()));
            Map<String, Map<String, Object>> perModel = modelOverrides.get(current.provider());
            if (perModel != null && perModel.containsKey(current.modelId())) {
                current = applyModelOverride(current, perModel.get(current.modelId()));
            }
            loaded.add(current);
        }
        return List.copyOf(loaded);
    }

    private List<Model> mergeCustomModels(List<Model> builtIns, List<Model> customModels) {
        List<Model> merged = new ArrayList<>(builtIns);
        for (Model customModel : customModels) {
            int index = -1;
            for (int i = 0; i < merged.size(); i++) {
                Model existing = merged.get(i);
                if (existing.provider().equals(customModel.provider())
                        && existing.modelId().equals(customModel.modelId())) {
                    index = i;
                    break;
                }
            }
            if (index >= 0) {
                merged.set(index, customModel);
            } else {
                merged.add(customModel);
            }
        }
        return List.copyOf(merged);
    }

    private CustomModelsResult loadCustomModels(Path path) {
        if (!Files.exists(path)) {
            return CustomModelsResult.empty();
        }
        try {
            JsonNode root = JsonCodec.parse(JsonUtils.stripJsonComments(Files.readString(path, StandardCharsets.UTF_8)));
            if (!root.path("providers").isObject()) {
                return CustomModelsResult.empty("Invalid models.json schema:\n  - providers: Expected object\n\nFile: " + path);
            }
            Map<String, Map<String, Object>> overrides = new LinkedHashMap<>();
            Map<String, Map<String, Map<String, Object>>> perModelOverrides = new LinkedHashMap<>();
            List<Model> customModels = new ArrayList<>();
            List<String> builtInProviders = builtInRegistry.models().stream().map(Model::provider).distinct().toList();
            root.path("providers").fields().forEachRemaining(providerEntry -> {
                String providerName = providerEntry.getKey();
                JsonNode providerConfig = providerEntry.getValue();
                validateProviderConfigNode(providerName, providerConfig, builtInProviders);
                Map<String, Object> providerOverride = new LinkedHashMap<>();
                putText(providerOverride, "name", providerConfig.get("name"));
                putText(providerOverride, "baseUrl", providerConfig.get("baseUrl"));
                if (providerConfig.has("compat")) {
                    providerOverride.put("compat", JsonCodec.mapper().convertValue(providerConfig.get("compat"), Map.class));
                }
                if (!providerOverride.isEmpty()) {
                    overrides.put(providerName, providerOverride);
                }
                storeProviderRequestConfig(providerName, providerConfig);
                if (providerConfig.path("modelOverrides").isObject()) {
                    providerConfig.path("modelOverrides").fields().forEachRemaining(modelOverrideEntry -> {
                        Map<String, Object> override = mapFromNode(modelOverrideEntry.getValue());
                        perModelOverrides.computeIfAbsent(providerName, ignored -> new LinkedHashMap<>())
                                .put(modelOverrideEntry.getKey(), override);
                        storeModelHeaders(providerName, modelOverrideEntry.getKey(), stringMap(override.get("headers")));
                    });
                }
                JsonNode modelDefs = providerConfig.get("models");
                if (modelDefs != null && modelDefs.isArray()) {
                    for (JsonNode modelDef : modelDefs) {
                        Model parsed = parseModel(providerName, providerConfig, modelDef);
                        if (parsed != null) {
                            customModels.add(parsed);
                        }
                    }
                }
            });
            return new CustomModelsResult(List.copyOf(customModels), overrides, perModelOverrides, null);
        } catch (RuntimeException | IOException e) {
            return CustomModelsResult.empty("Failed to load models.json: " + e.getMessage() + "\n\nFile: " + path);
        }
    }

    private void validateProviderConfigNode(String providerName, JsonNode providerConfig, List<String> builtInProviders) {
        boolean isBuiltIn = builtInProviders.contains(providerName);
        JsonNode modelsNode = providerConfig.get("models");
        int modelCount = modelsNode != null && modelsNode.isArray() ? modelsNode.size() : 0;
        boolean hasModelOverrides = providerConfig.path("modelOverrides").isObject()
                && providerConfig.path("modelOverrides").size() > 0;
        if (modelCount == 0 && !providerConfig.hasNonNull("baseUrl") && !providerConfig.has("headers")
                && !providerConfig.has("compat") && !hasModelOverrides) {
            throw new IllegalArgumentException("Provider " + providerName
                    + ": must specify \"baseUrl\", \"headers\", \"compat\", \"modelOverrides\", or \"models\".");
        }
        if (modelCount > 0 && !isBuiltIn && !providerConfig.hasNonNull("baseUrl")) {
            throw new IllegalArgumentException("Provider " + providerName
                    + ": \"baseUrl\" is required when defining custom models.");
        }
        if (modelsNode != null && modelsNode.isArray()) {
            for (JsonNode modelDef : modelsNode) {
                if (!modelDef.hasNonNull("id") || modelDef.path("id").asText().isBlank()) {
                    throw new IllegalArgumentException("Provider " + providerName + ": model missing \"id\"");
                }
                if (!isBuiltIn && !providerConfig.hasNonNull("api") && !modelDef.hasNonNull("api")) {
                    throw new IllegalArgumentException("Provider " + providerName + ", model "
                            + modelDef.path("id").asText() + ": no \"api\" specified. Set at provider or model level.");
                }
                if (modelDef.has("contextWindow") && modelDef.path("contextWindow").asInt() <= 0) {
                    throw new IllegalArgumentException("Provider " + providerName + ", model "
                            + modelDef.path("id").asText() + ": invalid contextWindow");
                }
                if (modelDef.has("maxTokens") && modelDef.path("maxTokens").asInt() <= 0) {
                    throw new IllegalArgumentException("Provider " + providerName + ", model "
                            + modelDef.path("id").asText() + ": invalid maxTokens");
                }
            }
        }
    }

    private Model parseModel(String providerName, JsonNode providerConfig, JsonNode modelDef) {
        Optional<Model> providerDefault = builtInRegistry.models().stream()
                .filter(model -> model.provider().equals(providerName))
                .findFirst();
        String api = text(modelDef.get("api"), text(providerConfig.get("api"),
                providerDefault.map(Model::api).orElse(null)));
        String baseUrl = text(modelDef.get("baseUrl"), text(providerConfig.get("baseUrl"),
                providerDefault.map(model -> optionText(model, "baseUrl")).orElse(null)));
        if (api == null || baseUrl == null) {
            return null;
        }
        String id = modelDef.path("id").asText();
        Map<String, Object> options = mapFromNode(modelDef);
        options.put("baseUrl", baseUrl);
        options.put("reasoning", modelDef.path("reasoning").asBoolean(false));
        options.put("input", stringList(modelDef.get("input"), List.of("text")));
        options.put("headers", stringMapFromNode(modelDef.get("headers")));
        options.put("compat", mergeMap(stringMapObject(providerConfig.get("compat")), stringMapObject(modelDef.get("compat"))));
        storeModelHeaders(providerName, id, stringMapFromNode(modelDef.get("headers")));
        return new Model(providerName, id, text(modelDef.get("name"), id), api,
                modelDef.path("contextWindow").asInt(128000),
                modelDef.path("maxTokens").asInt(16384),
                true,
                stringList(modelDef.get("input"), List.of("text")).contains("image"),
                Map.copyOf(options));
    }

    private Model applyProviderOverride(Model model, Map<String, Object> override) {
        if (override == null || override.isEmpty()) {
            return model;
        }
        Map<String, Object> options = new LinkedHashMap<>(model.options());
        if (override.get("baseUrl") != null) {
            options.put("baseUrl", override.get("baseUrl"));
        }
        if (override.get("compat") != null) {
            options.put("compat", mergeMap(mapObject(options.get("compat")), mapObject(override.get("compat"))));
        }
        return copyModel(model, null, null, null, null, null, options);
    }

    private Model applyModelOverride(Model model, Map<String, Object> override) {
        Map<String, Object> options = new LinkedHashMap<>(model.options());
        options.putAll(override);
        if (override.get("compat") != null) {
            options.put("compat", mergeMap(mapObject(model.options().get("compat")), mapObject(override.get("compat"))));
        }
        String displayName = override.get("name") instanceof String name ? name : model.displayName();
        Integer contextWindow = numberValue(override.get("contextWindow"));
        Integer maxTokens = numberValue(override.get("maxTokens"));
        Boolean supportsImages = override.get("input") instanceof List<?> list
                ? list.stream().anyMatch(value -> "image".equals(String.valueOf(value)))
                : null;
        return copyModel(model, displayName, null, contextWindow, maxTokens, supportsImages, options);
    }

    private void storeProviderRequestConfig(String providerName, JsonNode providerConfig) {
        String apiKey = text(providerConfig.get("apiKey"), null);
        Map<String, String> headers = stringMapFromNode(providerConfig.get("headers"));
        boolean authHeader = providerConfig.path("authHeader").asBoolean(false);
        if (apiKey != null || !headers.isEmpty() || authHeader) {
            providerRequestConfigs.put(providerName, new ProviderRequestConfig(apiKey, headers.isEmpty() ? null : headers, authHeader));
        }
    }

    private void storeModelHeaders(String providerName, String modelId, Map<String, String> headers) {
        String key = modelRequestKey(providerName, modelId);
        if (headers == null || headers.isEmpty()) {
            modelRequestHeaders.remove(key);
        } else {
            modelRequestHeaders.put(key, Map.copyOf(headers));
        }
    }

    private void applyProviderConfig(String providerName, ProviderConfigInput config) {
        if (config.name() != null || config.baseUrl() != null) {
            providerOverrides.put(providerName, providerConfigInputOptions(config));
        }
        if (config.apiKey() != null || config.headers() != null || config.authHeader()) {
            providerRequestConfigs.put(providerName, new ProviderRequestConfig(config.apiKey(), config.headers(), config.authHeader()));
        }
        if (config.models() != null && !config.models().isEmpty()) {
            List<Model> next = new ArrayList<>(models.stream().filter(model -> !model.provider().equals(providerName)).toList());
            for (ModelDefinition def : config.models()) {
                storeModelHeaders(providerName, def.id(), def.headers());
                Map<String, Object> options = new LinkedHashMap<>(def.options() == null ? Map.of() : def.options());
                options.put("baseUrl", def.baseUrl() == null ? config.baseUrl() : def.baseUrl());
                options.put("headers", def.headers() == null ? Map.of() : def.headers());
                options.put("reasoning", def.reasoning());
                next.add(new Model(providerName, def.id(), def.name(), def.api() == null ? config.api() : def.api(),
                        def.contextWindow(), def.maxTokens(), true,
                        def.input() != null && def.input().contains("image"), Map.copyOf(options)));
            }
            models = List.copyOf(next);
        } else if (config.baseUrl() != null) {
            models = models.stream()
                    .map(model -> model.provider().equals(providerName)
                            ? applyProviderOverride(model, Map.of("baseUrl", config.baseUrl()))
                            : model)
                    .toList();
        }
    }

    private void validateProviderConfig(String providerName, ProviderConfigInput config) {
        if (config.models() == null || config.models().isEmpty()) {
            return;
        }
        if (config.baseUrl() == null) {
            throw new IllegalArgumentException("Provider " + providerName + ": \"baseUrl\" is required when defining models.");
        }
        if (config.apiKey() == null) {
            throw new IllegalArgumentException("Provider " + providerName + ": \"apiKey\" or \"oauth\" is required when defining models.");
        }
        for (ModelDefinition model : config.models()) {
            if (model.api() == null && config.api() == null) {
                throw new IllegalArgumentException("Provider " + providerName + ", model " + model.id() + ": no \"api\" specified.");
            }
        }
    }

    private static ProviderConfigInput mergeProviderConfig(ProviderConfigInput existing, ProviderConfigInput incoming) {
        return new ProviderConfigInput(
                incoming.name() != null ? incoming.name() : existing.name(),
                incoming.baseUrl() != null ? incoming.baseUrl() : existing.baseUrl(),
                incoming.apiKey() != null ? incoming.apiKey() : existing.apiKey(),
                incoming.api() != null ? incoming.api() : existing.api(),
                incoming.headers() != null ? incoming.headers() : existing.headers(),
                incoming.authHeader() || existing.authHeader(),
                incoming.models() != null ? incoming.models() : existing.models());
    }

    private static Map<String, Object> providerConfigInputOptions(ProviderConfigInput config) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (config.name() != null) {
            values.put("name", config.name());
        }
        if (config.baseUrl() != null) {
            values.put("baseUrl", config.baseUrl());
        }
        return values;
    }

    private static String modelRequestKey(String provider, String modelId) {
        return provider + ":" + modelId;
    }

    private static Model copyModel(Model model, String displayName, String api, Integer contextWindow,
                                   Integer maxTokens, Boolean supportsImages, Map<String, Object> options) {
        return new Model(model.provider(), model.modelId(),
                displayName == null ? model.displayName() : displayName,
                api == null ? model.api() : api,
                contextWindow == null ? model.contextWindow() : contextWindow,
                maxTokens == null ? model.maxOutputTokens() : maxTokens,
                model.supportsTools(),
                supportsImages == null ? model.supportsImages() : supportsImages,
                Map.copyOf(options));
    }

    private static String optionText(Model model, String key) {
        Object value = model.options().get(key);
        return value == null ? null : value.toString();
    }

    private static String text(JsonNode node, String fallback) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : fallback;
    }

    private static void putText(Map<String, Object> map, String key, JsonNode node) {
        if (node != null && node.isTextual()) {
            map.put(key, node.asText());
        }
    }

    private static Integer numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private static List<String> stringList(JsonNode node, List<String> fallback) {
        if (node == null || !node.isArray()) {
            return fallback;
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private static Map<String, String> stringMapFromNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return values;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        map.forEach((key, val) -> values.put(String.valueOf(key), String.valueOf(val)));
        return values;
    }

    private static Map<String, Object> mapFromNode(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        return JsonCodec.mapper().convertValue(node, Map.class);
    }

    private static Map<String, Object> stringMapObject(JsonNode node) {
        return mapFromNode(node);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapObject(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static Map<String, Object> mergeMap(Map<String, Object> base, Map<String, Object> override) {
        Map<String, Object> merged = new LinkedHashMap<>(base);
        merged.putAll(override);
        return Map.copyOf(merged);
    }

    private record CustomModelsResult(
            List<Model> models,
            Map<String, Map<String, Object>> providerOverrides,
            Map<String, Map<String, Map<String, Object>>> modelOverrides,
            String error) {
        static CustomModelsResult empty() {
            return empty(null);
        }

        static CustomModelsResult empty(String error) {
            return new CustomModelsResult(List.of(), Map.of(), Map.of(), error);
        }
    }
}
