package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class TogetherProvider extends OpenAiCompatibleProvider {
    private static final String ID = "together";
    private static final String DEFAULT_API = "https://api.together.xyz/v1";

    public TogetherProvider() {
        super(ID, DEFAULT_API, "TOGETHER_API_KEY", List.of(
                model("meta-llama/Llama-3.3-70B-Instruct-Turbo", "Llama 3.3 70B Turbo", false, 131072, 4096),
                model("deepseek-ai/DeepSeek-R1", "DeepSeek R1", true, 131072, 8192),
                model("Qwen/Qwen2.5-Coder-32B-Instruct", "Qwen 2.5 Coder 32B", false, 131072, 8192)
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
