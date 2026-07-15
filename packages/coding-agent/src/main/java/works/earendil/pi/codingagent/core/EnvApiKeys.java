package works.earendil.pi.codingagent.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EnvApiKeys {
    private static final Map<String, List<String>> API_KEY_ENV_VARS = apiKeyEnvVars();
    private static Boolean cachedVertexAdcCredentialsExists;

    private EnvApiKeys() {
    }

    public static List<String> findEnvKeys(String provider) {
        return findEnvKeys(provider, Map.of());
    }

    public static List<String> findEnvKeys(String provider, Map<String, String> env) {
        List<String> envVars = API_KEY_ENV_VARS.get(provider);
        if (envVars == null) {
            return null;
        }
        List<String> found = envVars.stream()
                .filter(envVar -> getProviderEnvValue(envVar, env).isPresent())
                .toList();
        return found.isEmpty() ? null : found;
    }

    public static Optional<String> getEnvApiKey(String provider) {
        return getEnvApiKey(provider, Map.of());
    }

    public static Optional<String> findEnvAuthLabel(String provider, Map<String, String> env) {
        List<String> envKeys = findEnvKeys(provider, env);
        if (envKeys != null && !envKeys.isEmpty()) {
            return Optional.of(envKeys.getFirst());
        }
        if ("google-vertex".equals(provider) && getEnvApiKey(provider, env).isPresent()) {
            return Optional.of("Google ADC");
        }
        if ("amazon-bedrock".equals(provider) && getEnvApiKey(provider, env).isPresent()) {
            return Optional.of("AWS credentials");
        }
        return Optional.empty();
    }

    public static Map<String, String> findEnvAuthProviders(Map<String, String> env) {
        Map<String, String> providers = new LinkedHashMap<>();
        for (String provider : API_KEY_ENV_VARS.keySet()) {
            findEnvAuthLabel(provider, env).ifPresent(label -> providers.put(provider, label));
        }
        findEnvAuthLabel("amazon-bedrock", env).ifPresent(label -> providers.put("amazon-bedrock", label));
        return providers;
    }

    public static Optional<String> getEnvApiKey(String provider, Map<String, String> env) {
        List<String> envKeys = findEnvKeys(provider, env);
        if (envKeys != null && !envKeys.isEmpty()) {
            return getProviderEnvValue(envKeys.getFirst(), env);
        }
        if ("google-vertex".equals(provider)) {
            boolean hasCredentials = hasVertexAdcCredentials(env);
            boolean hasProject = getProviderEnvValue("GOOGLE_CLOUD_PROJECT", env).isPresent()
                    || getProviderEnvValue("GCLOUD_PROJECT", env).isPresent();
            boolean hasLocation = getProviderEnvValue("GOOGLE_CLOUD_LOCATION", env).isPresent();
            if (hasCredentials && hasProject && hasLocation) {
                return Optional.of("<authenticated>");
            }
        }
        if ("amazon-bedrock".equals(provider)
                && (getProviderEnvValue("AWS_PROFILE", env).isPresent()
                || (getProviderEnvValue("AWS_ACCESS_KEY_ID", env).isPresent()
                && getProviderEnvValue("AWS_SECRET_ACCESS_KEY", env).isPresent())
                || getProviderEnvValue("AWS_BEARER_TOKEN_BEDROCK", env).isPresent()
                || getProviderEnvValue("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", env).isPresent()
                || getProviderEnvValue("AWS_CONTAINER_CREDENTIALS_FULL_URI", env).isPresent()
                || getProviderEnvValue("AWS_WEB_IDENTITY_TOKEN_FILE", env).isPresent())) {
            return Optional.of("<authenticated>");
        }
        return Optional.empty();
    }

    public static void clearVertexAdcCache() {
        cachedVertexAdcCredentialsExists = null;
    }

    private static Optional<String> getProviderEnvValue(String name, Map<String, String> env) {
        if (env != null && env.containsKey(name) && env.get(name) != null && !env.get(name).isEmpty()) {
            return Optional.of(env.get(name));
        }
        String systemValue = System.getenv(name);
        return systemValue == null || systemValue.isEmpty() ? Optional.empty() : Optional.of(systemValue);
    }

    private static boolean hasVertexAdcCredentials(Map<String, String> env) {
        Optional<String> explicitCredentialsPath = getProviderEnvValue("GOOGLE_APPLICATION_CREDENTIALS", env);
        if (explicitCredentialsPath.isPresent()) {
            return Files.exists(Path.of(explicitCredentialsPath.get()));
        }
        if (cachedVertexAdcCredentialsExists == null) {
            cachedVertexAdcCredentialsExists = Files.exists(Path.of(System.getProperty("user.home"),
                    ".config", "gcloud", "application_default_credentials.json"));
        }
        return cachedVertexAdcCredentialsExists;
    }

    private static Map<String, List<String>> apiKeyEnvVars() {
        Map<String, List<String>> env = new LinkedHashMap<>();
        env.put("github-copilot", List.of("COPILOT_GITHUB_TOKEN"));
        env.put("anthropic", List.of("ANTHROPIC_OAUTH_TOKEN", "ANTHROPIC_API_KEY"));
        env.put("ant-ling", List.of("ANT_LING_API_KEY"));
        env.put("openai", List.of("OPENAI_API_KEY"));
        env.put("ollama", List.of("OLLAMA_API_KEY"));
        env.put("azure-openai-responses", List.of("AZURE_OPENAI_API_KEY"));
        env.put("nvidia", List.of("NVIDIA_API_KEY"));
        env.put("deepseek", List.of("DEEPSEEK_API_KEY"));
        env.put("google", List.of("GEMINI_API_KEY"));
        env.put("google-vertex", List.of("GOOGLE_CLOUD_API_KEY"));
        env.put("groq", List.of("GROQ_API_KEY"));
        env.put("cerebras", List.of("CEREBRAS_API_KEY"));
        env.put("xai", List.of("XAI_API_KEY"));
        env.put("openrouter", List.of("OPENROUTER_API_KEY"));
        env.put("vercel-ai-gateway", List.of("AI_GATEWAY_API_KEY"));
        env.put("zai", List.of("ZAI_API_KEY"));
        env.put("zai-coding-cn", List.of("ZAI_CODING_CN_API_KEY"));
        env.put("mistral", List.of("MISTRAL_API_KEY"));
        env.put("minimax", List.of("MINIMAX_API_KEY"));
        env.put("minimax-cn", List.of("MINIMAX_CN_API_KEY"));
        env.put("moonshotai", List.of("MOONSHOT_API_KEY"));
        env.put("moonshotai-cn", List.of("MOONSHOT_API_KEY"));
        env.put("huggingface", List.of("HF_TOKEN"));
        env.put("fireworks", List.of("FIREWORKS_API_KEY"));
        env.put("together", List.of("TOGETHER_API_KEY"));
        env.put("opencode", List.of("OPENCODE_API_KEY"));
        env.put("opencode-go", List.of("OPENCODE_API_KEY"));
        env.put("kimi-coding", List.of("KIMI_API_KEY"));
        env.put("cloudflare-workers-ai", List.of("CLOUDFLARE_API_KEY"));
        env.put("cloudflare-ai-gateway", List.of("CLOUDFLARE_API_KEY"));
        env.put("xiaomi", List.of("XIAOMI_API_KEY"));
        env.put("xiaomi-token-plan-cn", List.of("XIAOMI_TOKEN_PLAN_CN_API_KEY"));
        env.put("xiaomi-token-plan-ams", List.of("XIAOMI_TOKEN_PLAN_AMS_API_KEY"));
        env.put("xiaomi-token-plan-sgp", List.of("XIAOMI_TOKEN_PLAN_SGP_API_KEY"));
        return Collections.unmodifiableMap(env);
    }
}
