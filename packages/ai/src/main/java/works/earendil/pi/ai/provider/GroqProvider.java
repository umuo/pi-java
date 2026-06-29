package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class GroqProvider extends OpenAiCompatibleProvider {
    private static final String ID = "groq";
    private static final String DEFAULT_API = "https://api.groq.com/openai/v1";

    public GroqProvider() {
        super(ID, DEFAULT_API, "GROQ_API_KEY", List.of(
                model("llama-3.1-8b-instant", "Llama 3.1 8B", false, false, 131072, 131072),
                model("llama-3.3-70b-versatile", "Llama 3.3 70B", false, false, 131072, 32768),
                model("meta-llama/llama-4-scout-17b-16e-instruct", "Llama 4 Scout 17B 16E", false, true, 131072, 8192),
                model("openai/gpt-oss-120b", "GPT OSS 120B", true, false, 131072, 65536),
                model("openai/gpt-oss-20b", "GPT OSS 20B", true, false, 131072, 65536),
                model("qwen/qwen3-32b", "Qwen3-32B", true, false, 131072, 40960)
        ));
    }

    private static Model model(String id, String name, boolean reasoning, boolean supportsImages,
                               int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "openai-completions", contextWindow, maxTokens, true, supportsImages,
                Map.of(
                        "baseUrl", DEFAULT_API,
                        "reasoning", reasoning,
                        "input", supportsImages ? List.of("text", "image") : List.of("text")
                ));
    }
}
