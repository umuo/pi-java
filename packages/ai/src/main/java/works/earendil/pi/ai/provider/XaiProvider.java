package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

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
                model("grok-3", "Grok 3", false, false, 131072, 8192),
                model("grok-3-fast", "Grok 3 Fast", false, false, 131072, 8192),
                model("grok-4.20-0309-non-reasoning", "Grok 4.20 (Non-Reasoning)", false, true, 1000000, 30000),
                model("grok-4.20-0309-reasoning", "Grok 4.20 (Reasoning)", true, true, 1000000, 30000),
                model("grok-4.3", "Grok 4.3", true, true, 1000000, 30000),
                model("grok-build-0.1", "Grok Build 0.1", true, true, 256000, 256000),
                model("grok-code-fast-1", "Grok Code Fast 1", false, false, 32768, 8192)
        ));
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
}
