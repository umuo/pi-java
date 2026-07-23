package works.earendil.pi.server.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

public final class ServerConfig {
    public static final String VERSION = "0.81.1";
    private static final String CONFIG_DIR_NAME = ".pi";
    private static final String ENV_SERVER_DIR = "PI_SERVER_DIR";
    private static final String ENV_PI_CONFIG_DIR = "PI_CONFIG_DIR";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, String> env;

    public ServerConfig() {
        this(System.getenv());
    }

    public ServerConfig(Map<String, String> env) {
        this.env = env != null ? Map.copyOf(env) : Map.of();
    }

    public Path getServerDir() {
        String envDir = env.get(ENV_SERVER_DIR);
        if (envDir != null && !envDir.isBlank()) {
            return Paths.get(envDir).toAbsolutePath().normalize();
        }

        String piDir = env.get(ENV_PI_CONFIG_DIR);
        if (piDir == null || piDir.isBlank()) {
            piDir = Paths.get(System.getProperty("user.home"), CONFIG_DIR_NAME).toString();
        }
        return Paths.get(piDir, "server").toAbsolutePath().normalize();
    }

    public Path getAuthPath() {
        return getServerDir().resolve("auth.json");
    }

    public Path getMachinePath() {
        return getServerDir().resolve("machine.json");
    }

    public Path getInstancesPath() {
        return getServerDir().resolve("instances.json");
    }

    public Path getSocketPath() {
        return getServerDir().resolve("server.sock");
    }

    public Path getRuntimeSettingsPath() {
        return getServerDir().resolve("server.json");
    }

    public Path getLogsDir() {
        return getServerDir().resolve("logs");
    }

    public Path getInstanceStderrLogPath(String instanceId) {
        String safeId = instanceId == null || instanceId.isBlank()
                ? "unknown"
                : instanceId.replaceAll("[^A-Za-z0-9._-]", "_");
        return getLogsDir().resolve(safeId + ".stderr.log");
    }

    public ServerRuntimeSettings getRuntimeSettings() {
        try {
            return loadRuntimeSettings();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load server settings", e);
        }
    }

    public ServerRuntimeSettings loadRuntimeSettings() throws IOException {
        Path path = getRuntimeSettingsPath();
        ServerRuntimeSettings defaults = ServerRuntimeSettings.defaults();
        if (!Files.exists(path)) {
            return defaults;
        }
        JsonNode root = MAPPER.readTree(Files.readString(path, StandardCharsets.UTF_8));
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Server settings root must be an object: " + path);
        }
        return new ServerRuntimeSettings(
                restartSettings(root.path("restart"), defaults.restart()),
                logRotationSettings(root.path("logRotation"), defaults.logRotation()));
    }

    private static ServerRuntimeSettings.RestartSettings restartSettings(
            JsonNode node,
            ServerRuntimeSettings.RestartSettings defaults) {
        if (!node.isObject()) {
            return defaults;
        }
        int maxAttempts = positiveInt(node.get("maxAttempts"), defaults.maxAttempts());
        Duration baseBackoff = durationMs(node.get("baseBackoffMs"), defaults.baseBackoff());
        Duration maxBackoff = durationMs(node.get("maxBackoffMs"), defaults.maxBackoff());
        return new ServerRuntimeSettings.RestartSettings(maxAttempts, baseBackoff, maxBackoff);
    }

    private static ServerRuntimeSettings.LogRotationSettings logRotationSettings(
            JsonNode node,
            ServerRuntimeSettings.LogRotationSettings defaults) {
        if (!node.isObject()) {
            return defaults;
        }
        if (node.path("enabled").isBoolean() && !node.path("enabled").asBoolean()) {
            return new ServerRuntimeSettings.LogRotationSettings(0, 0);
        }
        long maxBytes = nonNegativeLong(node.get("maxBytes"), defaults.maxBytes());
        int maxBackups = nonNegativeInt(node.get("maxBackups"), defaults.maxBackups());
        return new ServerRuntimeSettings.LogRotationSettings(maxBytes, maxBackups);
    }

    private static Duration durationMs(JsonNode node, Duration fallback) {
        Long value = nonNegativeLong(node, null);
        return value == null ? fallback : Duration.ofMillis(value);
    }

    private static int positiveInt(JsonNode node, int fallback) {
        if (node == null || !node.isNumber()) {
            return fallback;
        }
        int value = node.asInt();
        return value > 0 ? value : fallback;
    }

    private static int nonNegativeInt(JsonNode node, int fallback) {
        if (node == null || !node.isNumber()) {
            return fallback;
        }
        int value = node.asInt();
        return value >= 0 ? value : fallback;
    }

    private static Long nonNegativeLong(JsonNode node, Long fallback) {
        if (node == null || !node.isNumber()) {
            return fallback;
        }
        long value = node.asLong();
        return value >= 0 ? value : fallback;
    }
}
