package works.earendil.pi.codingagent.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ProviderDisplayNames {
    public static final Map<String, String> BUILT_IN_PROVIDER_DISPLAY_NAMES = builtIns();

    private ProviderDisplayNames() {
    }

    public static Optional<String> displayName(String provider) {
        return Optional.ofNullable(BUILT_IN_PROVIDER_DISPLAY_NAMES.get(provider));
    }

    private static Map<String, String> builtIns() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("anthropic", "Anthropic");
        names.put("amazon-bedrock", "Amazon Bedrock");
        names.put("ant-ling", "Ant Ling");
        names.put("azure-openai-responses", "Azure OpenAI Responses");
        names.put("cerebras", "Cerebras");
        names.put("cloudflare-ai-gateway", "Cloudflare AI Gateway");
        names.put("cloudflare-workers-ai", "Cloudflare Workers AI");
        names.put("deepseek", "DeepSeek");
        names.put("fireworks", "Fireworks");
        names.put("google", "Google Gemini");
        names.put("google-vertex", "Google Vertex AI");
        names.put("groq", "Groq");
        names.put("huggingface", "Hugging Face");
        names.put("kimi-coding", "Kimi For Coding");
        names.put("mistral", "Mistral");
        names.put("minimax", "MiniMax");
        names.put("minimax-cn", "MiniMax (China)");
        names.put("moonshotai", "Moonshot AI");
        names.put("moonshotai-cn", "Moonshot AI (China)");
        names.put("nvidia", "NVIDIA NIM");
        names.put("opencode", "OpenCode Zen");
        names.put("opencode-go", "OpenCode Go");
        names.put("ollama", "Ollama");
        names.put("openai", "OpenAI");
        names.put("openrouter", "OpenRouter");
        names.put("qwen-token-plan", "Qwen Token Plan");
        names.put("qwen-token-plan-cn", "Qwen Token Plan (China)");
        names.put("together", "Together AI");
        names.put("vercel-ai-gateway", "Vercel AI Gateway");
        names.put("xai", "xAI");
        names.put("zai", "ZAI Coding Plan (Global)");
        names.put("zai-coding-cn", "ZAI Coding Plan (China)");
        names.put("xiaomi", "Xiaomi MiMo");
        names.put("xiaomi-token-plan-cn", "Xiaomi MiMo Token Plan (China)");
        names.put("xiaomi-token-plan-ams", "Xiaomi MiMo Token Plan (Amsterdam)");
        names.put("xiaomi-token-plan-sgp", "Xiaomi MiMo Token Plan (Singapore)");
        return Map.copyOf(names);
    }
}
