package works.earendil.pi.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.common.json.JsonCodec;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OllamaProvider extends OpenAiCompatibleProvider {
    private static final String ID = "ollama";
    private static final String DEFAULT_API = "http://localhost:11434/v1";
    private static final Duration DISCOVERY_TIMEOUT = Duration.ofMillis(750);
    private static final Map<String, Object> COMPAT = Map.of(
            "supportsDeveloperRole", false,
            "supportsReasoningEffort", false
    );
    private static final List<Model> DEFAULT_MODELS = List.of(
            model("llama3.1:8b", "Llama 3.1 8B", false, 131072, 8192, false),
            model("qwen2.5-coder:7b", "Qwen2.5 Coder 7B", false, 32768, 8192, false),
            model("gpt-oss:20b", "GPT OSS 20B", true, 131072, 65536, false)
    );

    private final String tagsEndpoint;
    private final ProviderHttpSupport.Sender discoverySender;

    public OllamaProvider() {
        this(DEFAULT_API, null);
    }

    OllamaProvider(String defaultApi, ProviderHttpSupport.Sender discoverySender) {
        super(ID, defaultApi, "OLLAMA_API_KEY", DEFAULT_MODELS);
        this.tagsEndpoint = tagsEndpoint(defaultApi);
        this.discoverySender = discoverySender;
    }

    @Override
    public List<Model> refreshModels() {
        List<Model> discovered = discoverLocalModels(tagsEndpoint, discoverySender);
        if (!discovered.isEmpty()) {
            setModels(mergeModels(DEFAULT_MODELS, discovered));
        }
        return models();
    }

    private static Model model(String id, String name, boolean reasoning, int contextWindow, int maxTokens) {
        return model(id, name, reasoning, contextWindow, maxTokens, false);
    }

    private static Model model(String id, String name, boolean reasoning, int contextWindow, int maxTokens,
                               boolean discovered) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("baseUrl", DEFAULT_API);
        options.put("reasoning", reasoning);
        options.put("input", List.of("text"));
        options.put("compat", COMPAT);
        if (discovered) {
            options.put("discovered", true);
        }
        return new Model(ID, id, name, "openai-completions", contextWindow, maxTokens, true, false,
                Map.copyOf(options));
    }

    static List<Model> parseTags(JsonNode root) {
        JsonNode modelsNode = root == null ? null : root.get("models");
        if (modelsNode == null || !modelsNode.isArray()) {
            return List.of();
        }
        List<Model> discovered = new ArrayList<>();
        for (JsonNode modelNode : modelsNode) {
            String id = text(modelNode.get("name"));
            if (id == null || id.isBlank()) {
                id = text(modelNode.get("model"));
            }
            if (id == null || id.isBlank()) {
                continue;
            }
            discovered.add(model(id, displayName(id, modelNode), looksReasoningModel(id), 128000, 8192, true));
        }
        return List.copyOf(discovered);
    }

    private static List<Model> discoverLocalModels(String tagsEndpoint, ProviderHttpSupport.Sender sender) {
        try {
            ProviderHttpSupport.Sender effectiveSender = sender;
            if (effectiveSender == null) {
                HttpClient client = ProviderHttpSupport.client();
                effectiveSender = request -> client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tagsEndpoint))
                    .timeout(DISCOVERY_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<InputStream> response = ProviderHttpSupport.sendWithRetries(ID, request, effectiveSender,
                    new ProviderHttpSupport.RetryPolicy(0, Duration.ofMillis(50), Duration.ofMillis(100), 1));
            if (response.statusCode() < 200 || response.statusCode() >= 300 || response.body() == null) {
                return List.of();
            }
            return parseTags(JsonCodec.parse(new String(response.body().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<Model> mergeModels(List<Model> defaults, List<Model> discovered) {
        Map<String, Model> merged = new LinkedHashMap<>();
        for (Model model : defaults) {
            merged.put(model.modelId(), model);
        }
        for (Model model : discovered) {
            merged.putIfAbsent(model.modelId(), model);
        }
        return List.copyOf(merged.values());
    }

    private static String tagsEndpoint(String defaultApi) {
        String base = defaultApi == null || defaultApi.isBlank() ? DEFAULT_API : defaultApi;
        if (base.endsWith("/v1")) {
            base = base.substring(0, base.length() - 3);
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/api/tags";
    }

    private static String displayName(String id, JsonNode modelNode) {
        JsonNode details = modelNode == null ? null : modelNode.get("details");
        String family = details == null ? null : text(details.get("family"));
        String parameters = details == null ? null : text(details.get("parameter_size"));
        if (family != null && parameters != null) {
            return family + " " + parameters + " (" + id + ")";
        }
        return id;
    }

    private static boolean looksReasoningModel(String id) {
        String normalized = id.toLowerCase();
        return normalized.startsWith("gpt-oss") || normalized.contains("deepseek-r1")
                || normalized.contains("qwq") || normalized.contains("reasoning");
    }

    private static String text(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }
}
