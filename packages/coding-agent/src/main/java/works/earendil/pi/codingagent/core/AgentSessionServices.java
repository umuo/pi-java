package works.earendil.pi.codingagent.core;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Transport;
import works.earendil.pi.ai.provider.ProviderRegistry;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.core.extensions.ExtensionCommandContext;
import works.earendil.pi.codingagent.core.extensions.ExtensionPlugin;
import works.earendil.pi.codingagent.core.extensions.ExtensionRunner;
import works.earendil.pi.codingagent.resources.ResourceLoader;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.codingagent.tools.CodingToolFactory;
import works.earendil.pi.codingagent.tools.PathUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AgentSessionServices(
        Path cwd,
        Path agentDir,
        AuthStorage authStorage,
        SettingsManager settingsManager,
        ModelRegistry modelRegistry,
        ResourceLoader resourceLoader,
        List<Diagnostic> diagnostics) {

    public record Diagnostic(Type type, String message) {
        public enum Type {
            INFO,
            WARNING,
            ERROR
        }
    }

    public record CreateOptions(
            Path cwd,
            Path agentDir,
            AuthStorage authStorage,
            SettingsManager settingsManager,
            ModelRegistry modelRegistry,
            ProviderRegistry providerRegistry,
            ResourceLoader resourceLoader,
            boolean projectTrusted) {
    }

    public record CreateSessionOptions(
            AgentSessionServices services,
            SessionManager sessionManager,
            Model model,
            ThinkingLevel thinkingLevel,
            List<ModelResolver.ScopedModel> scopedModels,
            List<String> tools,
            List<String> excludeTools,
            String noTools,
            List<AgentTool> customTools,
            works.earendil.pi.agent.core.AgentLoop.StreamFunction streamFunction,
            ExtensionRunner extensionRunner,
            String resourcesDiscoverReason) {
        public CreateSessionOptions(
                AgentSessionServices services,
                SessionManager sessionManager,
                Model model,
                ThinkingLevel thinkingLevel,
                List<ModelResolver.ScopedModel> scopedModels,
                List<String> tools,
                List<String> excludeTools,
                String noTools,
                List<AgentTool> customTools,
                works.earendil.pi.agent.core.AgentLoop.StreamFunction streamFunction) {
            this(services, sessionManager, model, thinkingLevel, scopedModels, tools, excludeTools, noTools,
                    customTools, streamFunction, null, "startup");
        }

        public CreateSessionOptions(
                AgentSessionServices services,
                SessionManager sessionManager,
                Model model,
                ThinkingLevel thinkingLevel,
                List<ModelResolver.ScopedModel> scopedModels,
                List<String> tools,
                List<String> excludeTools,
                String noTools,
                List<AgentTool> customTools,
                works.earendil.pi.agent.core.AgentLoop.StreamFunction streamFunction,
                ExtensionRunner extensionRunner) {
            this(services, sessionManager, model, thinkingLevel, scopedModels, tools, excludeTools, noTools,
                    customTools, streamFunction, extensionRunner, "startup");
        }
    }

    public record CreateSessionResult(AgentSession session, String modelFallbackMessage) {
    }

    public static AgentSessionServices create(CreateOptions options) throws IOException {
        Path cwd = PathUtils.resolvePath(options.cwd().toString());
        Path agentDir = options.agentDir() == null
                ? Path.of(System.getProperty("user.home"), ".pi", "agent").toAbsolutePath().normalize()
                : PathUtils.resolvePath(options.agentDir().toString());
        AuthStorage authStorage = options.authStorage() == null
                ? AuthStorage.create(agentDir.resolve("auth.json"))
                : options.authStorage();
        SettingsManager settingsManager = options.settingsManager() == null
                ? new SettingsManager(cwd, agentDir, options.projectTrusted())
                : options.settingsManager();
        ProviderRegistry providerRegistry = options.providerRegistry();
        if (providerRegistry == null) {
            providerRegistry = new ProviderRegistry();
            providerRegistry.registerDefaults();
        }
        ModelRegistry modelRegistry = options.modelRegistry() == null
                ? ModelRegistry.create(providerRegistry, authStorage, agentDir.resolve("models.json"))
                : options.modelRegistry();
        ResourceLoader resourceLoader = options.resourceLoader() == null
                ? new ResourceLoader(cwd, agentDir, options.projectTrusted(), pathsFromStrings(settingsManager.getSkillPaths()),
                pathsFromStrings(settingsManager.getPromptTemplatePaths()), pathsFromStrings(settingsManager.getThemePaths()),
                packageEntries(settingsManager.getGlobalSettings()), packageEntries(settingsManager.getProjectSettings()),
                true, false, null, null)
                : options.resourceLoader();
        resourceLoader.reload();
        return new AgentSessionServices(cwd, agentDir, authStorage, settingsManager, modelRegistry,
                resourceLoader, List.of());
    }

    public static CreateSessionResult createAgentSessionFromServices(CreateSessionOptions options) {
        AgentSessionServices services = options.services();
        SessionManager sessionManager = options.sessionManager();
        List<ModelResolver.ScopedModel> scopedModels = options.scopedModels() == null ? List.of() : options.scopedModels();
        Model model = options.model();
        String fallbackMessage = null;
        if (model == null) {
            ModelResolver.InitialModelResult initial = ModelResolver.findInitialModel(null, null, scopedModels,
                    !sessionManager.entries().isEmpty(), null, null, options.thinkingLevel(), services.modelRegistry());
            model = initial.model();
            fallbackMessage = initial.fallbackMessage();
        }
        ThinkingLevel thinkingLevel = options.thinkingLevel() == null ? Defaults.DEFAULT_THINKING_LEVEL : options.thinkingLevel();
        extendResourcesFromExtensions(services, options.extensionRunner(), options.resourcesDiscoverReason());
        List<AgentTool> tools = resolveTools(services.cwd(), services.settingsManager(), sessionManager, model,
                thinkingLevel, options.tools(), options.excludeTools(), options.noTools(), options.customTools());
        String systemPrompt = buildSystemPrompt(services, tools);
        AgentSession session = new AgentSession(new AgentSession.Config(sessionManager, services.modelRegistry(),
                model, thinkingLevel, scopedModels, tools, systemPrompt,
                options.streamFunction(), buildStreamOptions(services.settingsManager()), services.agentDir(),
                services.resourceLoader().skills().skills(), services.settingsManager().getEnableSkillCommands(),
                options.extensionRunner(), services.settingsManager().getShellCommandPrefix(),
                services.settingsManager().getShellPath(), services.settingsManager().getBlockImages()));
        return new CreateSessionResult(session, fallbackMessage);
    }

    private static void extendResourcesFromExtensions(AgentSessionServices services, ExtensionRunner runner,
                                                      String reason) {
        if (runner == null) {
            return;
        }
        String effectiveReason = reason == null || reason.isBlank() ? "startup" : reason;
        ExtensionPlugin.ResourcesDiscoverResult result = runner.emitResourcesDiscover(services.cwd(), effectiveReason,
                new ExtensionCommandContext(services.cwd()));
        if (result.skillPaths().isEmpty() && result.promptPaths().isEmpty() && result.themePaths().isEmpty()) {
            return;
        }
        services.resourceLoader().extendResources(result.skillPaths(), result.promptPaths(), result.themePaths());
    }

    private static List<Path> pathsFromStrings(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(Path::of)
                .toList();
    }

    private static List<JsonNode> packageEntries(JsonNode settings) {
        JsonNode packages = settings == null ? null : settings.path("packages");
        if (packages == null || !packages.isArray()) {
            return List.of();
        }
        List<JsonNode> entries = new ArrayList<>();
        packages.forEach(entry -> {
            if (entry.isTextual() || entry.isObject()) {
                entries.add(entry.deepCopy());
            }
        });
        return List.copyOf(entries);
    }

    static StreamOptions buildStreamOptions(SettingsManager settings) {
        StreamOptions defaults = StreamOptions.defaults();
        if (settings == null) {
            return defaults;
        }
        Map<String, Object> metadata = new LinkedHashMap<>(defaults.metadata());
        Integer baseDelayMs = settings.getRetryBaseDelayMs();
        if (baseDelayMs != null) {
            metadata.put("retryInitialDelayMs", baseDelayMs);
        }
        Integer maxRetryDelayMs = settings.getProviderMaxRetryDelayMs();
        if (maxRetryDelayMs != null) {
            metadata.put("maxRetryDelayMs", maxRetryDelayMs);
        }
        Integer maxConcurrentRequests = settings.getProviderMaxConcurrentRequests();
        if (maxConcurrentRequests != null) {
            metadata.put("maxConcurrentRequests", maxConcurrentRequests);
        }
        Map<String, Map<String, Integer>> providerRetryOverrides = settings.getProviderRetryOverrides();
        if (!providerRetryOverrides.isEmpty()) {
            metadata.put("providerRetryOverrides", providerRetryOverrides);
        }
        Integer timeoutMs = settings.getProviderRetryTimeoutMs();
        Integer providerRetries = settings.getProviderRetryMaxRetries();
        Integer globalRetries = settings.getRetryMaxRetries();
        return new StreamOptions(
                defaults.temperature(),
                defaults.maxTokens(),
                defaults.apiKey(),
                parseTransport(settings.getTransport(), defaults.transport()),
                defaults.cacheRetention(),
                defaults.sessionId(),
                defaults.headers(),
                timeoutMs == null ? defaults.timeout() : Duration.ofMillis(timeoutMs),
                settings.getRetryEnabled() ? firstNonNull(providerRetries, globalRetries, defaults.maxRetries()) : 0,
                defaults.env(),
                Map.copyOf(metadata)
        );
    }

    private static Transport parseTransport(String value, Transport fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return switch (value.toLowerCase().replace('-', '_')) {
            case "sse" -> Transport.SSE;
            case "websocket" -> Transport.WEBSOCKET;
            case "websocket_cached" -> Transport.WEBSOCKET_CACHED;
            case "auto" -> Transport.AUTO;
            default -> fallback;
        };
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... values) {
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String buildSystemPrompt(AgentSessionServices services, List<AgentTool> tools) {
        List<String> selectedTools = tools.stream().map(AgentTool::name).toList();
        Map<String, String> toolSnippets = new LinkedHashMap<>();
        List<String> promptGuidelines = new ArrayList<>();
        for (AgentTool tool : tools) {
            if (tool.definition().description() != null && !tool.definition().description().isBlank()) {
                toolSnippets.put(tool.name(), tool.definition().description());
            }
            if (tool.definition().promptGuidelines() != null && !tool.definition().promptGuidelines().isBlank()) {
                promptGuidelines.add(tool.definition().promptGuidelines());
            }
        }
        ResourceLoader resources = services.resourceLoader();
        String appendSystemPrompt = resources.appendSystemPrompt().isEmpty()
                ? null
                : String.join("\n\n", resources.appendSystemPrompt());
        Path packageRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return SystemPromptBuilder.build(new SystemPromptBuilder.BuildOptions(
                resources.systemPrompt(),
                selectedTools,
                toolSnippets,
                promptGuidelines,
                appendSystemPrompt,
                services.cwd(),
                resources.contextFiles(),
                resources.skills().skills(),
                packageRoot.resolve("README.md"),
                packageRoot.resolve("docs"),
                packageRoot.resolve("examples"),
                services.agentDir(),
                LocalDate.now()
        ));
    }

    private static List<AgentTool> resolveTools(Path cwd, SettingsManager settings, SessionManager sessionManager,
                                                Model model, ThinkingLevel thinkingLevel, List<String> allow,
                                                List<String> exclude, String noTools, List<AgentTool> customTools) {
        Map<String, String> sessionEnvironment = new LinkedHashMap<>();
        sessionEnvironment.put("PI_SESSION_ID", sessionManager.sessionId());
        sessionManager.sessionFile().ifPresent(path ->
                sessionEnvironment.put("PI_SESSION_FILE", path.toAbsolutePath().normalize().toString()));
        if (model != null) {
            sessionEnvironment.put("PI_PROVIDER", model.provider());
            sessionEnvironment.put("PI_MODEL", model.modelId());
        }
        if (thinkingLevel != null) {
            sessionEnvironment.put("PI_REASONING_LEVEL", thinkingLevel.wireName());
        }
        CodingToolFactory.BashConfig bashConfig = settings == null
                ? new CodingToolFactory.BashConfig(null, null, sessionEnvironment)
                : new CodingToolFactory.BashConfig(settings.getShellCommandPrefix(), settings.getShellPath(),
                        sessionEnvironment);
        boolean autoResizeImages = settings == null || settings.getImageAutoResize();
        Map<String, AgentTool> builtIns = CodingToolFactory.createAllTools(cwd, bashConfig, autoResizeImages);
        Map<String, AgentTool> selected = new LinkedHashMap<>();
        List<String> defaultTools = List.of("read", "bash", "edit", "write");
        if (allow != null && !allow.isEmpty()) {
            for (String name : allow) {
                if (builtIns.containsKey(name)) {
                    selected.put(name, builtIns.get(name));
                }
            }
        } else if (!"all".equals(noTools) && !"/".equals(noTools) && !"*".equals(noTools)) {
            if (!"builtin".equals(noTools)) {
                for (String name : defaultTools) {
                    selected.put(name, builtIns.get(name));
                }
            }
        }
        if (customTools != null) {
            for (AgentTool tool : customTools) {
                selected.put(tool.name(), tool);
            }
        }
        if (exclude != null) {
            exclude.forEach(selected::remove);
        }
        return List.copyOf(new ArrayList<>(selected.values()));
    }
}
