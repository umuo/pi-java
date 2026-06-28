package works.earendil.pi.codingagent.core;

import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ModelResolver {
    public static final Map<String, String> DEFAULT_MODEL_PER_PROVIDER = defaultModelPerProvider();

    private ModelResolver() {
    }

    public record ScopedModel(Model model, ThinkingLevel thinkingLevel) {
    }

    public record ParsedModelResult(Model model, ThinkingLevel thinkingLevel, String warning) {
    }

    public record ResolveCliModelResult(Model model, ThinkingLevel thinkingLevel, String warning, String error) {
    }

    public record InitialModelResult(Model model, ThinkingLevel thinkingLevel, String fallbackMessage) {
    }

    public record RestoreModelResult(Model model, String fallbackMessage) {
    }

    public static Optional<Model> findExactModelReferenceMatch(String modelReference, List<Model> availableModels) {
        String trimmed = modelReference.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        List<Model> canonicalMatches = availableModels.stream()
                .filter(model -> (model.provider() + "/" + model.modelId()).toLowerCase(Locale.ROOT).equals(normalized))
                .toList();
        if (canonicalMatches.size() == 1) {
            return Optional.of(canonicalMatches.getFirst());
        }
        if (canonicalMatches.size() > 1) {
            return Optional.empty();
        }
        int slashIndex = trimmed.indexOf('/');
        if (slashIndex != -1) {
            String provider = trimmed.substring(0, slashIndex).trim();
            String modelId = trimmed.substring(slashIndex + 1).trim();
            if (!provider.isEmpty() && !modelId.isEmpty()) {
                List<Model> providerMatches = availableModels.stream()
                        .filter(model -> model.provider().equalsIgnoreCase(provider)
                                && model.modelId().equalsIgnoreCase(modelId))
                        .toList();
                if (providerMatches.size() == 1) {
                    return Optional.of(providerMatches.getFirst());
                }
                if (providerMatches.size() > 1) {
                    return Optional.empty();
                }
            }
        }
        List<Model> idMatches = availableModels.stream()
                .filter(model -> model.modelId().equalsIgnoreCase(trimmed))
                .toList();
        return idMatches.size() == 1 ? Optional.of(idMatches.getFirst()) : Optional.empty();
    }

    public static ParsedModelResult parseModelPattern(String pattern, List<Model> availableModels) {
        return parseModelPattern(pattern, availableModels, true);
    }

    public static ParsedModelResult parseModelPattern(String pattern, List<Model> availableModels,
                                                       boolean allowInvalidThinkingLevelFallback) {
        Optional<Model> exactMatch = tryMatchModel(pattern, availableModels);
        if (exactMatch.isPresent()) {
            return new ParsedModelResult(exactMatch.get(), null, null);
        }
        int lastColonIndex = pattern.lastIndexOf(':');
        if (lastColonIndex == -1) {
            return new ParsedModelResult(null, null, null);
        }
        String prefix = pattern.substring(0, lastColonIndex);
        String suffix = pattern.substring(lastColonIndex + 1);
        Optional<ThinkingLevel> thinkingLevel = parseThinkingLevel(suffix);
        if (thinkingLevel.isPresent()) {
            ParsedModelResult result = parseModelPattern(prefix, availableModels, allowInvalidThinkingLevelFallback);
            if (result.model() != null) {
                return new ParsedModelResult(result.model(), result.warning() == null ? thinkingLevel.get() : null,
                        result.warning());
            }
            return result;
        }
        if (!allowInvalidThinkingLevelFallback) {
            return new ParsedModelResult(null, null, null);
        }
        ParsedModelResult result = parseModelPattern(prefix, availableModels, allowInvalidThinkingLevelFallback);
        if (result.model() != null) {
            return new ParsedModelResult(result.model(), null,
                    "Invalid thinking level \"" + suffix + "\" in pattern \"" + pattern + "\". Using default instead.");
        }
        return result;
    }

    public static List<ScopedModel> resolveModelScope(List<String> patterns, ModelRegistry modelRegistry) {
        List<Model> availableModels = modelRegistry.getAvailable();
        List<ScopedModel> scopedModels = new ArrayList<>();
        for (String pattern : patterns) {
            if (containsGlob(pattern)) {
                int colonIndex = pattern.lastIndexOf(':');
                String globPattern = pattern;
                ThinkingLevel thinkingLevel = null;
                if (colonIndex != -1) {
                    Optional<ThinkingLevel> suffix = parseThinkingLevel(pattern.substring(colonIndex + 1));
                    if (suffix.isPresent()) {
                        thinkingLevel = suffix.get();
                        globPattern = pattern.substring(0, colonIndex);
                    }
                }
                Pattern regex = globToRegex(globPattern);
                for (Model model : availableModels) {
                    String fullId = model.provider() + "/" + model.modelId();
                    if ((regex.matcher(fullId).matches() || regex.matcher(model.modelId()).matches())
                            && scopedModels.stream().noneMatch(scoped -> modelsAreEqual(scoped.model(), model))) {
                        scopedModels.add(new ScopedModel(model, thinkingLevel));
                    }
                }
                continue;
            }
            ParsedModelResult result = parseModelPattern(pattern, availableModels);
            if (result.model() != null
                    && scopedModels.stream().noneMatch(scoped -> modelsAreEqual(scoped.model(), result.model()))) {
                scopedModels.add(new ScopedModel(result.model(), result.thinkingLevel()));
            }
        }
        return List.copyOf(scopedModels);
    }

    public static ResolveCliModelResult resolveCliModel(String cliProvider, String cliModel, ThinkingLevel cliThinking,
                                                        ModelRegistry modelRegistry) {
        if (cliModel == null || cliModel.isBlank()) {
            return new ResolveCliModelResult(null, null, null, null);
        }
        List<Model> availableModels = modelRegistry.getAll();
        if (availableModels.isEmpty()) {
            return new ResolveCliModelResult(null, null, null,
                    "No models available. Check your installation or add models to models.json.");
        }
        Map<String, String> providerMap = new LinkedHashMap<>();
        for (Model model : availableModels) {
            providerMap.put(model.provider().toLowerCase(Locale.ROOT), model.provider());
        }
        String provider = cliProvider == null ? null : providerMap.get(cliProvider.toLowerCase(Locale.ROOT));
        if (cliProvider != null && provider == null) {
            return new ResolveCliModelResult(null, null, null,
                    "Unknown provider \"" + cliProvider + "\". Use --list-models to see available providers/models.");
        }
        String pattern = cliModel;
        boolean inferredProvider = false;
        if (provider == null) {
            int slashIndex = cliModel.indexOf('/');
            if (slashIndex != -1) {
                String maybeProvider = cliModel.substring(0, slashIndex);
                String canonical = providerMap.get(maybeProvider.toLowerCase(Locale.ROOT));
                if (canonical != null) {
                    provider = canonical;
                    pattern = cliModel.substring(slashIndex + 1);
                    inferredProvider = true;
                }
            }
        }
        if (provider == null) {
            String lower = cliModel.toLowerCase(Locale.ROOT);
            Optional<Model> exact = availableModels.stream()
                    .filter(model -> model.modelId().equalsIgnoreCase(lower)
                            || (model.provider() + "/" + model.modelId()).equalsIgnoreCase(lower))
                    .findFirst();
            if (exact.isPresent()) {
                return new ResolveCliModelResult(exact.get(), null, null, null);
            }
        }
        if (cliProvider != null && provider != null) {
            String prefix = provider + "/";
            if (cliModel.toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                pattern = cliModel.substring(prefix.length());
            }
        }
        String resolvedProvider = provider;
        List<Model> candidates = resolvedProvider == null
                ? availableModels
                : availableModels.stream().filter(model -> model.provider().equals(resolvedProvider)).toList();
        ParsedModelResult parsed = parseModelPattern(pattern, candidates, false);
        if (parsed.model() != null) {
            if (inferredProvider && !modelRegistry.hasConfiguredAuth(parsed.model())) {
                List<Model> rawExactMatches = availableModels.stream()
                        .filter(model -> model.modelId().equalsIgnoreCase(cliModel)
                                && !modelsAreEqual(model, parsed.model()))
                        .toList();
                List<Model> authenticated = rawExactMatches.stream().filter(modelRegistry::hasConfiguredAuth).toList();
                if (authenticated.size() == 1) {
                    return new ResolveCliModelResult(authenticated.getFirst(), null, null, null);
                }
            }
            return new ResolveCliModelResult(parsed.model(), parsed.thinkingLevel(), parsed.warning(), null);
        }
        if (inferredProvider) {
            String lower = cliModel.toLowerCase(Locale.ROOT);
            Optional<Model> exact = availableModels.stream()
                    .filter(model -> model.modelId().toLowerCase(Locale.ROOT).equals(lower)
                            || (model.provider() + "/" + model.modelId()).toLowerCase(Locale.ROOT).equals(lower))
                    .findFirst();
            if (exact.isPresent()) {
                return new ResolveCliModelResult(exact.get(), null, null, null);
            }
            ParsedModelResult fallback = parseModelPattern(cliModel, availableModels, false);
            if (fallback.model() != null) {
                return new ResolveCliModelResult(fallback.model(), fallback.thinkingLevel(), fallback.warning(), null);
            }
        }
        if (provider != null) {
            String fallbackPattern = pattern;
            ThinkingLevel fallbackThinking = null;
            if (cliThinking == null) {
                int lastColon = pattern.lastIndexOf(':');
                if (lastColon != -1) {
                    Optional<ThinkingLevel> suffix = parseThinkingLevel(pattern.substring(lastColon + 1));
                    if (suffix.isPresent()) {
                        fallbackPattern = pattern.substring(0, lastColon);
                        fallbackThinking = suffix.get();
                    }
                }
            }
            Optional<Model> fallbackModel = buildFallbackModel(provider, fallbackPattern, availableModels);
            if (fallbackModel.isPresent()) {
                Model model = fallbackModel.get();
                if ((cliThinking != null ? cliThinking : fallbackThinking) != null
                        && (cliThinking != null ? cliThinking : fallbackThinking) != ThinkingLevel.OFF) {
                    Map<String, Object> options = new LinkedHashMap<>(model.options());
                    options.put("reasoning", true);
                    model = new Model(model.provider(), model.modelId(), model.displayName(), model.api(),
                            model.contextWindow(), model.maxOutputTokens(), model.supportsTools(),
                            model.supportsImages(), Map.copyOf(options));
                }
                String warning = "Model \"" + fallbackPattern + "\" not found for provider \"" + provider
                        + "\". Using custom model id.";
                return new ResolveCliModelResult(model, fallbackThinking, warning, null);
            }
        }
        String display = provider == null ? cliModel : provider + "/" + pattern;
        return new ResolveCliModelResult(null, null, parsed.warning(),
                "Model \"" + display + "\" not found. Use --list-models to see available models.");
    }

    public static InitialModelResult findInitialModel(String cliProvider, String cliModel, List<ScopedModel> scopedModels,
                                                       boolean isContinuing, String defaultProvider, String defaultModelId,
                                                       ThinkingLevel defaultThinkingLevel, ModelRegistry modelRegistry) {
        if (cliProvider != null && cliModel != null) {
            ResolveCliModelResult resolved = resolveCliModel(cliProvider, cliModel, null, modelRegistry);
            if (resolved.error() != null) {
                return new InitialModelResult(null, Defaults.DEFAULT_THINKING_LEVEL, resolved.error());
            }
            if (resolved.model() != null) {
                return new InitialModelResult(resolved.model(), Defaults.DEFAULT_THINKING_LEVEL, null);
            }
        }
        if (!scopedModels.isEmpty() && !isContinuing) {
            ScopedModel first = scopedModels.getFirst();
            return new InitialModelResult(first.model(),
                    first.thinkingLevel() == null
                            ? (defaultThinkingLevel == null ? Defaults.DEFAULT_THINKING_LEVEL : defaultThinkingLevel)
                            : first.thinkingLevel(), null);
        }
        if (defaultProvider != null && defaultModelId != null) {
            Optional<Model> found = modelRegistry.find(defaultProvider, defaultModelId);
            if (found.isPresent()) {
                return new InitialModelResult(found.get(),
                        defaultThinkingLevel == null ? Defaults.DEFAULT_THINKING_LEVEL : defaultThinkingLevel, null);
            }
        }
        List<Model> availableModels = modelRegistry.getAvailable();
        if (!availableModels.isEmpty()) {
            for (Map.Entry<String, String> entry : DEFAULT_MODEL_PER_PROVIDER.entrySet()) {
                Optional<Model> match = availableModels.stream()
                        .filter(model -> model.provider().equals(entry.getKey()) && model.modelId().equals(entry.getValue()))
                        .findFirst();
                if (match.isPresent()) {
                    return new InitialModelResult(match.get(), Defaults.DEFAULT_THINKING_LEVEL, null);
                }
            }
            return new InitialModelResult(availableModels.getFirst(), Defaults.DEFAULT_THINKING_LEVEL, null);
        }
        return new InitialModelResult(null, Defaults.DEFAULT_THINKING_LEVEL, null);
    }

    public static RestoreModelResult restoreModelFromSession(String savedProvider, String savedModelId,
                                                             Model currentModel, ModelRegistry modelRegistry) {
        Optional<Model> restoredModel = modelRegistry.find(savedProvider, savedModelId);
        boolean hasConfiguredAuth = restoredModel.isPresent() && modelRegistry.hasConfiguredAuth(restoredModel.get());
        if (restoredModel.isPresent() && hasConfiguredAuth) {
            return new RestoreModelResult(restoredModel.get(), null);
        }
        String reason = restoredModel.isEmpty() ? "model no longer exists" : "no auth configured";
        if (currentModel != null) {
            return new RestoreModelResult(currentModel, "Could not restore model " + savedProvider + "/" + savedModelId
                    + " (" + reason + "). Using " + currentModel.provider() + "/" + currentModel.modelId() + ".");
        }
        List<Model> availableModels = modelRegistry.getAvailable();
        if (!availableModels.isEmpty()) {
            Model fallback = null;
            for (Map.Entry<String, String> entry : DEFAULT_MODEL_PER_PROVIDER.entrySet()) {
                Optional<Model> match = availableModels.stream()
                        .filter(model -> model.provider().equals(entry.getKey()) && model.modelId().equals(entry.getValue()))
                        .findFirst();
                if (match.isPresent()) {
                    fallback = match.get();
                    break;
                }
            }
            if (fallback == null) {
                fallback = availableModels.getFirst();
            }
            return new RestoreModelResult(fallback, "Could not restore model " + savedProvider + "/" + savedModelId
                    + " (" + reason + "). Using " + fallback.provider() + "/" + fallback.modelId() + ".");
        }
        return new RestoreModelResult(null, null);
    }

    private static Optional<Model> tryMatchModel(String pattern, List<Model> availableModels) {
        Optional<Model> exact = findExactModelReferenceMatch(pattern, availableModels);
        if (exact.isPresent()) {
            return exact;
        }
        String lower = pattern.toLowerCase(Locale.ROOT);
        List<Model> matches = availableModels.stream()
                .filter(model -> model.modelId().toLowerCase(Locale.ROOT).contains(lower)
                        || model.displayName().toLowerCase(Locale.ROOT).contains(lower))
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        List<Model> aliases = matches.stream().filter(model -> isAlias(model.modelId()))
                .sorted(Comparator.comparing(Model::modelId).reversed()).toList();
        if (!aliases.isEmpty()) {
            return Optional.of(aliases.getFirst());
        }
        return matches.stream().sorted(Comparator.comparing(Model::modelId).reversed()).findFirst();
    }

    private static boolean isAlias(String id) {
        return id.endsWith("-latest") || !id.matches(".*-\\d{8}$");
    }

    private static Optional<Model> buildFallbackModel(String provider, String modelId, List<Model> availableModels) {
        List<Model> providerModels = availableModels.stream().filter(model -> model.provider().equals(provider)).toList();
        if (providerModels.isEmpty()) {
            return Optional.empty();
        }
        String defaultId = DEFAULT_MODEL_PER_PROVIDER.get(provider);
        Model base = defaultId == null
                ? providerModels.getFirst()
                : providerModels.stream().filter(model -> model.modelId().equals(defaultId)).findFirst()
                .orElse(providerModels.getFirst());
        return Optional.of(new Model(base.provider(), modelId, modelId, base.api(), base.contextWindow(),
                base.maxOutputTokens(), base.supportsTools(), base.supportsImages(), base.options()));
    }

    private static Optional<ThinkingLevel> parseThinkingLevel(String value) {
        try {
            return Optional.of(ThinkingLevel.fromWireName(value));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static boolean containsGlob(String pattern) {
        return pattern.contains("*") || pattern.contains("?") || pattern.contains("[");
    }

    private static Pattern globToRegex(String glob) {
        StringBuilder regex = new StringBuilder("(?i)^");
        for (int i = 0; i < glob.length(); i++) {
            char ch = glob.charAt(i);
            switch (ch) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '+', '|', '^', '$', '@', '%' -> regex.append('\\').append(ch);
                case '\\' -> regex.append("\\\\");
                default -> regex.append(ch);
            }
        }
        regex.append('$');
        return Pattern.compile(regex.toString());
    }

    private static boolean modelsAreEqual(Model a, Model b) {
        return a.provider().equals(b.provider()) && a.modelId().equals(b.modelId());
    }

    private static Map<String, String> defaultModelPerProvider() {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("amazon-bedrock", "us.anthropic.claude-opus-4-6-v1");
        defaults.put("ant-ling", "Ring-2.6-1T");
        defaults.put("anthropic", "claude-opus-4-8");
        defaults.put("openai", "gpt-5.5");
        defaults.put("azure-openai-responses", "gpt-5.4");
        defaults.put("openai-codex", "gpt-5.5");
        defaults.put("nvidia", "nvidia/nemotron-3-super-120b-a12b");
        defaults.put("deepseek", "deepseek-v4-pro");
        defaults.put("google", "gemini-3.1-pro-preview");
        defaults.put("google-vertex", "gemini-3.1-pro-preview");
        defaults.put("github-copilot", "gpt-5.4");
        defaults.put("openrouter", "moonshotai/kimi-k2.6");
        defaults.put("vercel-ai-gateway", "zai/glm-5.1");
        defaults.put("xai", "grok-4.20-0309-reasoning");
        defaults.put("groq", "openai/gpt-oss-120b");
        defaults.put("cerebras", "zai-glm-4.7");
        defaults.put("zai", "glm-5.1");
        defaults.put("zai-coding-cn", "glm-5.1");
        defaults.put("mistral", "devstral-medium-latest");
        defaults.put("minimax", "MiniMax-M2.7");
        defaults.put("minimax-cn", "MiniMax-M2.7");
        defaults.put("moonshotai", "kimi-k2.6");
        defaults.put("moonshotai-cn", "kimi-k2.6");
        defaults.put("huggingface", "moonshotai/Kimi-K2.6");
        defaults.put("fireworks", "accounts/fireworks/models/kimi-k2p6");
        defaults.put("together", "moonshotai/Kimi-K2.6");
        defaults.put("opencode", "kimi-k2.6");
        defaults.put("opencode-go", "kimi-k2.6");
        defaults.put("kimi-coding", "kimi-for-coding");
        defaults.put("cloudflare-workers-ai", "@cf/moonshotai/kimi-k2.6");
        defaults.put("cloudflare-ai-gateway", "workers-ai/@cf/moonshotai/kimi-k2.6");
        defaults.put("xiaomi", "mimo-v2.5-pro");
        defaults.put("xiaomi-token-plan-cn", "mimo-v2.5-pro");
        defaults.put("xiaomi-token-plan-ams", "mimo-v2.5-pro");
        defaults.put("xiaomi-token-plan-sgp", "mimo-v2.5-pro");
        return Map.copyOf(defaults);
    }
}
