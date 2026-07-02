package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class FireworksProvider extends OpenAiCompatibleProvider {
    private static final String ID = "fireworks";
    private static final String DEFAULT_API = "https://api.fireworks.ai/inference/v1";

    public FireworksProvider() {
        super(ID, DEFAULT_API, "FIREWORKS_API_KEY", List.of(
                model("accounts/fireworks/models/llama-v3p3-70b-instruct", "Llama 3.3 70B (Fireworks)", false, 131072, 16384),
                model("accounts/fireworks/models/deepseek-r1", "DeepSeek R1 (Fireworks)", true, 131072, 8192)
        ));
    }

    private static Model model(String id, String name, boolean reasoning, int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "openai-completions", contextWindow, maxTokens, true, false,
                Map.of(
                        "baseUrl", DEFAULT_API,
                        "reasoning", reasoning,
                        "input", List.of("text")
                ));
    }
}
