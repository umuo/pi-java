package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class QwenTokenPlanProvider extends OpenAiCompatibleProvider {
    private static final String ID = "qwen-token-plan";
    private static final String DEFAULT_API =
            "https://token-plan.ap-southeast-1.maas.aliyuncs.com/compatible-mode/v1";

    public QwenTokenPlanProvider() {
        super(ID, DEFAULT_API, "QWEN_TOKEN_PLAN_API_KEY", catalog());
    }

    private static List<Model> catalog() {
        return List.of(
                model("MiniMax-M2.5", "MiniMax M2.5", false),
                model("deepseek-v3.2", "DeepSeek V3.2", false),
                model("deepseek-v4-flash", "DeepSeek V4 Flash", true),
                model("deepseek-v4-pro", "DeepSeek V4 Pro", true),
                model("glm-5", "GLM 5", true),
                model("glm-5.1", "GLM 5.1", true),
                model("glm-5.2", "GLM 5.2", true),
                model("kimi-k2.7-code", "Kimi K2.7 Code", true),
                model("qwen3.7-max", "Qwen 3.7 Max", true),
                model("qwen3.8-max-preview", "Qwen 3.8 Max Preview", true));
    }

    private static Model model(String id, String name, boolean reasoning) {
        return new Model(ID, id, name, "openai-completions", 1_000_000, 65_536, true, true,
                Map.of("baseUrl", DEFAULT_API, "reasoning", reasoning, "input", List.of("text", "image")));
    }
}
