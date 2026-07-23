package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;

import java.util.List;
import java.util.Map;

public final class XaiProvider extends OpenAiCompatibleProvider {
    private static final String ID = "xai";
    private static final String DEFAULT_API = "https://api.x.ai/v1";
    private static final Map<String, Object> COMPAT = Map.of(
            "supportsStore", false,
            "supportsDeveloperRole", false,
            "supportsReasoningEffort", false
    );

    public XaiProvider() {
        super(ID, DEFAULT_API, "XAI_API_KEY", List.of(
                model("grok-4.3", "Grok 4.3", true, true, 1000000, 30000),
                responsesModel("grok-4.5", "Grok 4.5", 500000, 500000)
        ));
    }

    @Override
    public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
        if ("openai-responses".equals(model.api())) {
            return OpenAiResponsesSupport.stream(ID, DEFAULT_API, "XAI_API_KEY", model, context, options);
        }
        return super.stream(model, context, options);
    }

    private static Model model(String id, String name, boolean reasoning, boolean supportsImages,
                               int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "openai-completions", contextWindow, maxTokens, true, supportsImages,
                Map.of(
                        "baseUrl", DEFAULT_API,
                        "reasoning", reasoning,
                        "input", supportsImages ? List.of("text", "image") : List.of("text"),
                        "compat", COMPAT
                ));
    }

    private static Model responsesModel(String id, String name, int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "openai-responses", contextWindow, maxTokens, true, true,
                Map.of(
                        "baseUrl", DEFAULT_API,
                        "reasoning", true,
                        "input", List.of("text", "image"),
                        "thinkingLevels", List.of("low", "medium", "high"),
                        "compat", COMPAT
                ));
    }
}
