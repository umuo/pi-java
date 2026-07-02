package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class ZaiProvider extends OpenAiCompatibleProvider {
    private static final String ID = "zai";
    private static final String DEFAULT_API = "https://open.bigmodel.cn/api/paas/v4";

    public ZaiProvider() {
        super(ID, DEFAULT_API, "ZAI_API_KEY", List.of(
                model("glm-4-plus", "GLM-4 Plus", 131072, 4096),
                model("glm-4-flash", "GLM-4 Flash", 131072, 4096)
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
