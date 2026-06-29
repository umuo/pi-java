package works.earendil.pi.codingagent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import works.earendil.pi.codingagent.core.HttpDispatcher;
import works.earendil.pi.codingagent.util.JsonUtils;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SettingsManager {
    private final Path globalSettingsPath;
    private final Path projectSettingsPath;
    private boolean projectTrusted;
    private ObjectNode globalSettings = JsonCodec.mapper().createObjectNode();
    private ObjectNode projectSettings = JsonCodec.mapper().createObjectNode();
    private ObjectNode settings = JsonCodec.mapper().createObjectNode();
    private final List<SettingsError> errors = new ArrayList<>();

    public SettingsManager(Path cwd, Path agentDir, boolean projectTrusted) {
        this.globalSettingsPath = agentDir.resolve("settings.json");
        this.projectSettingsPath = cwd.resolve(".pi").resolve("settings.json");
        this.projectTrusted = projectTrusted;
        reload();
    }

    public static SettingsManager inMemory(Map<String, Object> settings) {
        SettingsManager manager = new SettingsManager(Path.of("."), Path.of(System.getProperty("java.io.tmpdir")), false);
        manager.globalSettings = migrate(JsonCodec.mapper().valueToTree(settings == null ? Map.of() : settings));
        manager.projectSettings = JsonCodec.mapper().createObjectNode();
        manager.recompute();
        return manager;
    }

    public JsonNode load() {
        return settings.deepCopy();
    }

    public void update(Scope scope, JsonNode patch) throws IOException {
        if (scope == Scope.PROJECT && !projectTrusted) {
            throw new IllegalStateException("Project is not trusted; refusing to write project settings");
        }
        Path path = scope == Scope.GLOBAL ? globalSettingsPath : projectSettingsPath;
        Files.createDirectories(path.getParent());
        try (FileChannel channel = FileChannel.open(path,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {
            String current = channel.size() > 0 ? Files.readString(path, StandardCharsets.UTF_8) : "{}";
            JsonNode merged = deepMerge(parseObject(current), patch);
            channel.truncate(0);
            channel.position(0);
            channel.write(StandardCharsets.UTF_8.encode(JsonCodec.stringify(merged)));
        }
        reload();
    }

    public void reload() {
        LoadResult global = tryReadObject(globalSettingsPath);
        if (global.error() == null) {
            globalSettings = migrate(global.node());
        } else {
            errors.add(new SettingsError(Scope.GLOBAL, global.error()));
        }
        if (projectTrusted) {
            LoadResult project = tryReadObject(projectSettingsPath);
            if (project.error() == null) {
                projectSettings = migrate(project.node());
            } else {
                errors.add(new SettingsError(Scope.PROJECT, project.error()));
            }
        } else {
            projectSettings = JsonCodec.mapper().createObjectNode();
        }
        recompute();
    }

    public void flush() {
    }

    public List<SettingsError> drainErrors() {
        List<SettingsError> drained = List.copyOf(errors);
        errors.clear();
        return drained;
    }

    public JsonNode getGlobalSettings() {
        return globalSettings.deepCopy();
    }

    public JsonNode getProjectSettings() {
        return projectSettings.deepCopy();
    }

    public boolean isProjectTrusted() {
        return projectTrusted;
    }

    public void setProjectTrusted(boolean trusted) {
        if (projectTrusted != trusted) {
            projectTrusted = trusted;
            reload();
        }
    }

    public void applyOverrides(Map<String, Object> overrides) {
        settings = (ObjectNode) deepMerge(settings, JsonCodec.mapper().valueToTree(overrides == null ? Map.of() : overrides));
    }

    public String getDefaultProvider() {
        return text("defaultProvider");
    }

    public String getDefaultModel() {
        return text("defaultModel");
    }

    public String getThemeSetting() {
        return text("theme");
    }

    public String getTheme() {
        String theme = getThemeSetting();
        return theme != null && theme.contains("/") ? null : theme;
    }

    public void setTheme(String theme) throws IOException {
        ObjectNode patch = JsonCodec.mapper().createObjectNode();
        patch.put("theme", theme);
        globalSettings.set("theme", TextNode.valueOf(theme));
        recompute();
        update(Scope.GLOBAL, patch);
    }

    public String getDefaultThinkingLevel() {
        return text("defaultThinkingLevel");
    }

    public void setDefaultThinkingLevel(String level) throws IOException {
        ObjectNode patch = JsonCodec.mapper().createObjectNode();
        patch.put("defaultThinkingLevel", level);
        globalSettings.put("defaultThinkingLevel", level);
        recompute();
        update(Scope.GLOBAL, patch);
    }

    public String getTransport() {
        String transport = text("transport");
        return transport == null ? "auto" : transport;
    }

    public String getSteeringMode() {
        String mode = text("steeringMode");
        return mode == null ? "one-at-a-time" : mode;
    }

    public String getFollowUpMode() {
        String mode = text("followUpMode");
        return mode == null ? "one-at-a-time" : mode;
    }

    public boolean getCompactionEnabled() {
        return settings.path("compaction").path("enabled").isMissingNode()
                || settings.path("compaction").path("enabled").asBoolean(true);
    }

    public int getCompactionReserveTokens() {
        return settings.path("compaction").path("reserveTokens").asInt(16_384);
    }

    public int getCompactionKeepRecentTokens() {
        return settings.path("compaction").path("keepRecentTokens").asInt(20_000);
    }

    public int getHttpIdleTimeoutMs() {
        JsonNode node = settings.get("httpIdleTimeoutMs");
        if (node == null || node.isNull()) {
            return HttpDispatcher.DEFAULT_HTTP_IDLE_TIMEOUT_MS;
        }
        Object raw = node.isTextual() ? node.asText() : node.isNumber() ? node.numberValue() : null;
        return HttpDispatcher.parseHttpIdleTimeoutMs(raw)
                .orElseThrow(() -> new IllegalArgumentException("Invalid httpIdleTimeoutMs setting: " + node.asText()));
    }

    public Integer getWebSocketConnectTimeoutMs() {
        JsonNode node = settings.get("websocketConnectTimeoutMs");
        if (node == null || node.isNull()) {
            return null;
        }
        Object raw = node.isTextual() ? node.asText() : node.isNumber() ? node.numberValue() : null;
        return HttpDispatcher.parseHttpIdleTimeoutMs(raw)
                .orElseThrow(() -> new IllegalArgumentException("Invalid websocketConnectTimeoutMs setting: " + node.asText()));
    }

    public boolean getRetryEnabled() {
        return settings.path("retry").path("enabled").asBoolean(true);
    }

    public Integer getRetryMaxRetries() {
        return nonNegativeInt(settings.path("retry").get("maxRetries"));
    }

    public Integer getRetryBaseDelayMs() {
        return positiveInt(settings.path("retry").get("baseDelayMs"));
    }

    public Integer getProviderRetryTimeoutMs() {
        return positiveInt(settings.path("retry").path("provider").get("timeoutMs"));
    }

    public Integer getProviderRetryMaxRetries() {
        return nonNegativeInt(settings.path("retry").path("provider").get("maxRetries"));
    }

    public Integer getProviderMaxRetryDelayMs() {
        return positiveInt(settings.path("retry").path("provider").get("maxRetryDelayMs"));
    }

    public Integer getProviderMaxConcurrentRequests() {
        return positiveInt(settings.path("retry").path("provider").get("maxConcurrentRequests"));
    }

    public Map<String, Map<String, Integer>> getProviderRetryOverrides() {
        JsonNode providers = settings.path("retry").path("providers");
        if (!providers.isObject()) {
            return Map.of();
        }
        Map<String, Map<String, Integer>> overrides = new LinkedHashMap<>();
        providers.fields().forEachRemaining(entry -> {
            if (entry.getKey() == null || entry.getKey().isBlank() || !entry.getValue().isObject()) {
                return;
            }
            Map<String, Integer> provider = new LinkedHashMap<>();
            putPositive(provider, "timeoutMs", entry.getValue().get("timeoutMs"));
            putNonNegative(provider, "maxRetries", entry.getValue().get("maxRetries"));
            putPositive(provider, "baseDelayMs", entry.getValue().get("baseDelayMs"));
            putPositive(provider, "maxRetryDelayMs", entry.getValue().get("maxRetryDelayMs"));
            putPositive(provider, "maxConcurrentRequests", entry.getValue().get("maxConcurrentRequests"));
            if (!provider.isEmpty()) {
                overrides.put(entry.getKey(), Map.copyOf(provider));
            }
        });
        return Map.copyOf(overrides);
    }

    public String getExternalEditorCommand() {
        String configured = text("externalEditor");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String visual = System.getenv("VISUAL");
        if (visual != null && !visual.isBlank()) {
            return visual;
        }
        String editor = System.getenv("EDITOR");
        if (editor != null && !editor.isBlank()) {
            return editor;
        }
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "notepad" : "nano";
    }

    public String getShellCommandPrefix() {
        return text("shellCommandPrefix");
    }

    public String getSessionDir() {
        String sessionDir = text("sessionDir");
        if (sessionDir != null && sessionDir.startsWith("~/")) {
            return Path.of(System.getProperty("user.home")).resolve(sessionDir.substring(2)).toString();
        }
        return sessionDir;
    }

    public String getDefaultProjectTrust() {
        String value = globalSettings.path("defaultProjectTrust").asText("ask");
        return value.equals("always") || value.equals("never") ? value : "ask";
    }

    public List<JsonNode> getPackages() {
        return arrayValues("packages");
    }

    public List<String> getExtensionPaths() {
        return stringArray("extensions");
    }

    public List<String> getSkillPaths() {
        return stringArray("skills");
    }

    public List<String> getPromptTemplatePaths() {
        return stringArray("prompts");
    }

    public List<String> getThemePaths() {
        return stringArray("themes");
    }

    public boolean getEnableSkillCommands() {
        return settings.path("enableSkillCommands").asBoolean(true);
    }

    public List<String> getEnabledModels() {
        return stringArray("enabledModels");
    }

    public boolean getShowImages() {
        return settings.path("terminal").path("showImages").asBoolean(true);
    }

    public int getImageWidthCells() {
        JsonNode width = settings.path("terminal").path("imageWidthCells");
        return width.isNumber() ? Math.max(1, (int) Math.floor(width.asDouble())) : 60;
    }

    public boolean getImageAutoResize() {
        return settings.path("images").path("autoResize").asBoolean(true);
    }

    public boolean getBlockImages() {
        return settings.path("images").path("blockImages").asBoolean(false);
    }

    public String getDoubleEscapeAction() {
        String action = text("doubleEscapeAction");
        return action == null ? "tree" : action;
    }

    public String getTreeFilterMode() {
        String mode = text("treeFilterMode");
        return List.of("default", "no-tools", "user-only", "labeled-only", "all").contains(mode) ? mode : "default";
    }

    public int getEditorPaddingX() {
        return settings.path("editorPaddingX").asInt(0);
    }

    public int getAutocompleteMaxVisible() {
        return settings.path("autocompleteMaxVisible").asInt(5);
    }

    public String getCodeBlockIndent() {
        return settings.path("markdown").path("codeBlockIndent").asText("  ");
    }

    public void setProjectPackages(List<?> packages) throws IOException {
        if (!projectTrusted) {
            throw new IllegalStateException("Project is not trusted; refusing to write project settings");
        }
        ObjectNode patch = JsonCodec.mapper().createObjectNode();
        patch.set("packages", JsonCodec.mapper().valueToTree(packages));
        update(Scope.PROJECT, patch);
    }

    public record SettingsError(Scope scope, Exception error) {
    }

    public enum Scope {
        GLOBAL,
        PROJECT
    }

    private void recompute() {
        settings = (ObjectNode) deepMerge(globalSettings, projectSettings);
    }

    private String text(String field) {
        JsonNode node = settings.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private List<String> stringArray(String field) {
        JsonNode node = settings.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> {
            if (item.isTextual()) {
                values.add(item.asText());
            }
        });
        return List.copyOf(values);
    }

    private List<JsonNode> arrayValues(String field) {
        JsonNode node = settings.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        node.forEach(item -> values.add(item.deepCopy()));
        return List.copyOf(values);
    }

    private static Integer positiveInt(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        int value = node.asInt();
        return value > 0 ? value : null;
    }

    private static Integer nonNegativeInt(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        int value = node.asInt();
        return value >= 0 ? value : null;
    }

    private static void putPositive(Map<String, Integer> target, String key, JsonNode node) {
        Integer value = positiveInt(node);
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putNonNegative(Map<String, Integer> target, String key, JsonNode node) {
        Integer value = nonNegativeInt(node);
        if (value != null) {
            target.put(key, value);
        }
    }

    private LoadResult tryReadObject(Path path) {
        try {
            return new LoadResult(readObject(path), null);
        } catch (Exception e) {
            return new LoadResult(JsonCodec.mapper().createObjectNode(), e);
        }
    }

    private ObjectNode readObject(Path path) throws IOException {
        if (!Files.exists(path)) {
            return JsonCodec.mapper().createObjectNode();
        }
        JsonNode node = parseObject(Files.readString(path, StandardCharsets.UTF_8));
        if (!node.isObject()) {
            throw new IllegalArgumentException("Settings root must be an object: " + path);
        }
        return (ObjectNode) node;
    }

    private static ObjectNode parseObject(String content) {
        JsonNode node = JsonCodec.parse(JsonUtils.stripJsonComments(content == null || content.isBlank() ? "{}" : content));
        if (!node.isObject()) {
            throw new IllegalArgumentException("Settings root must be an object");
        }
        return (ObjectNode) node;
    }

    private static JsonNode deepMerge(JsonNode base, JsonNode override) {
        ObjectNode result = base.deepCopy();
        override.fields().forEachRemaining(entry -> {
            JsonNode overrideValue = entry.getValue();
            JsonNode baseValue = result.get(entry.getKey());
            if (baseValue != null && baseValue.isObject() && overrideValue.isObject()) {
                result.set(entry.getKey(), deepMerge(baseValue, overrideValue));
            } else if (!overrideValue.isNull()) {
                result.set(entry.getKey(), overrideValue);
            }
        });
        return result;
    }

    private static ObjectNode migrate(ObjectNode input) {
        ObjectNode settings = input.deepCopy();
        if (settings.has("queueMode") && !settings.has("steeringMode")) {
            settings.set("steeringMode", settings.get("queueMode"));
            settings.remove("queueMode");
        }
        if (!settings.has("transport") && settings.path("websockets").isBoolean()) {
            settings.put("transport", settings.path("websockets").asBoolean() ? "websocket" : "sse");
            settings.remove("websockets");
        }
        JsonNode skills = settings.get("skills");
        if (skills != null && skills.isObject()) {
            if (skills.has("enableSkillCommands") && !settings.has("enableSkillCommands")) {
                settings.set("enableSkillCommands", skills.get("enableSkillCommands"));
            }
            if (skills.path("customDirectories").isArray() && skills.path("customDirectories").size() > 0) {
                settings.set("skills", skills.get("customDirectories"));
            } else {
                settings.remove("skills");
            }
        }
        JsonNode retry = settings.get("retry");
        if (retry instanceof ObjectNode retryObject && retryObject.path("maxDelayMs").isNumber()) {
            ObjectNode provider = retryObject.path("provider").isObject()
                    ? (ObjectNode) retryObject.path("provider").deepCopy()
                    : JsonCodec.mapper().createObjectNode();
            if (!provider.hasNonNull("maxRetryDelayMs")) {
                provider.set("maxRetryDelayMs", retryObject.get("maxDelayMs"));
            }
            retryObject.set("provider", provider);
            retryObject.remove("maxDelayMs");
        }
        return settings;
    }

    private record LoadResult(ObjectNode node, Exception error) {
    }
}
