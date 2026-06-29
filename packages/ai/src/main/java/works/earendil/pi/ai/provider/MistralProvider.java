package works.earendil.pi.ai.provider;

import works.earendil.pi.ai.model.Model;

import java.util.List;
import java.util.Map;

public final class MistralProvider extends OpenAiCompatibleProvider {
    private static final String ID = "mistral";
    private static final String DEFAULT_API = "https://api.mistral.ai/v1";

    public MistralProvider() {
        super(ID, DEFAULT_API, "MISTRAL_API_KEY", List.of(
                model("codestral-latest", "Codestral (latest)", false, false, 256000, 4096),
                model("devstral-latest", "Devstral 2", false, false, 262144, 262144),
                model("devstral-medium-latest", "Devstral 2 (latest)", false, false, 262144, 262144),
                model("devstral-small-2507", "Devstral Small", false, false, 128000, 128000),
                model("magistral-medium-latest", "Magistral Medium (latest)", true, false, 128000, 16384),
                model("magistral-small", "Magistral Small", true, false, 128000, 128000),
                model("ministral-3b-latest", "Ministral 3B (latest)", false, false, 128000, 128000),
                model("ministral-8b-latest", "Ministral 8B (latest)", false, false, 128000, 128000),
                model("mistral-large-latest", "Mistral Large (latest)", false, true, 262144, 262144),
                model("mistral-medium-latest", "Mistral Medium (latest)", false, true, 262144, 262144),
                model("mistral-nemo", "Mistral Nemo", false, false, 128000, 128000),
                model("mistral-small-latest", "Mistral Small (latest)", true, true, 256000, 256000),
                model("open-mistral-nemo", "Open Mistral Nemo", false, false, 128000, 128000),
                model("pixtral-12b", "Pixtral 12B", false, true, 128000, 128000),
                model("pixtral-large-latest", "Pixtral Large (latest)", false, true, 128000, 128000)
        ));
    }

    private static Model model(String id, String name, boolean reasoning, boolean supportsImages,
                               int contextWindow, int maxTokens) {
        return new Model(ID, id, name, "mistral-conversations", contextWindow, maxTokens, true, supportsImages,
                Map.of(
                        "baseUrl", DEFAULT_API,
                        "reasoning", reasoning,
                        "input", supportsImages ? List.of("text", "image") : List.of("text")
                ));
    }
}
