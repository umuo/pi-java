package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class MoonshotChinaProvider extends OpenAiCompatibleProvider {
    private static final String ID = "moonshotai-cn";
    private static final String DEFAULT_API = "https://api.moonshot.cn/v1";

    public MoonshotChinaProvider() {
        super(ID, DEFAULT_API, "MOONSHOT_API_KEY", List.of(
                model("kimi-k2.6", "Kimi K2.6", 262_144, 32_768),
                model("kimi-k2.7-code", "Kimi K2.7 Code", 262_144, 32_768),
                model("kimi-k2.7-code-highspeed", "Kimi K2.7 Code HighSpeed", 262_144, 32_768),
                model("kimi-k3", "Kimi K3", 1_048_576, 131_072)));
    }

    private static Model model(String id, String name, int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "openai-completions", contextWindow, maxTokens, true, true,
                Map.of("baseUrl", DEFAULT_API, "reasoning", true, "input", List.of("text", "image")));
    }
}
