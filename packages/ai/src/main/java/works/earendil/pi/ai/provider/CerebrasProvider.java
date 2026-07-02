package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class CerebrasProvider extends OpenAiCompatibleProvider {
    private static final String ID = "cerebras";
    private static final String DEFAULT_API = "https://api.cerebras.ai/v1";

    public CerebrasProvider() {
        super(ID, DEFAULT_API, "CEREBRAS_API_KEY", List.of(
                model("llama3.1-8b", "Llama 3.1 8B (Cerebras)", 131072, 8192),
                model("llama3.1-70b", "Llama 3.1 70B (Cerebras)", 131072, 8192)
        ));
    }

    private static Model model(String id, String name, int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "openai-completions", contextWindow, maxTokens, true, false,
                Map.of(
                        "baseUrl", DEFAULT_API,
                        "reasoning", false,
                        "input", List.of("text")
                ));
    }
}
