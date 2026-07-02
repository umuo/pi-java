package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class DeepSeekProvider extends OpenAiCompatibleProvider {
    private static final String ID = "deepseek";
    private static final String DEFAULT_API = "https://api.deepseek.com/v1";

    public DeepSeekProvider() {
        super(ID, DEFAULT_API, "DEEPSEEK_API_KEY", List.of(
                model("deepseek-chat", "DeepSeek V3", false, 65536, 8192),
                model("deepseek-reasoner", "DeepSeek R1", true, 65536, 8192)
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
