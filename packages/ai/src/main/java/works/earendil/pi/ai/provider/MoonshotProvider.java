package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class MoonshotProvider extends OpenAiCompatibleProvider {
    private static final String ID = "moonshot";
    private static final String DEFAULT_API = "https://api.moonshot.cn/v1";

    public MoonshotProvider() {
        super(ID, DEFAULT_API, "MOONSHOT_API_KEY", List.of(
                model("moonshot-v1-8k", "Moonshot V1 8K", 8192, 4096),
                model("moonshot-v1-32k", "Moonshot V1 32K", 32768, 8192),
                model("moonshot-v1-128k", "Moonshot V1 128K", 131072, 8192),
                model("kimi-latest", "Kimi Latest", 131072, 8192)
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
