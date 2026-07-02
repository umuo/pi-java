package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class OpenRouterProvider extends OpenAiCompatibleProvider {
    private static final String ID = "openrouter";
    private static final String DEFAULT_API = "https://openrouter.ai/api/v1";

    public OpenRouterProvider() {
        super(ID, DEFAULT_API, "OPENROUTER_API_KEY", List.of(
                model("anthropic/claude-3.7-sonnet", "Claude 3.7 Sonnet (OpenRouter)", false, true, 200000, 16384),
                model("deepseek/deepseek-r1", "DeepSeek R1 (OpenRouter)", true, false, 131072, 8192),
                model("google/gemini-2.5-pro", "Gemini 2.5 Pro (OpenRouter)", true, true, 2000000, 65536)
        ));
    }

    private static Model model(String id, String name, boolean reasoning, boolean supportsImages, int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "openai-completions", contextWindow, maxTokens, true, supportsImages,
                Map.of(
                        "baseUrl", DEFAULT_API,
                        "reasoning", reasoning,
                        "input", supportsImages ? List.of("text", "image") : List.of("text")
                ));
    }
}
