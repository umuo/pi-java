package works.earendil.pi.orchestrator.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public final class OrchestratorConfig {
    public static final String VERSION = "0.80.2";
    private static final String CONFIG_DIR_NAME = ".pi";
    private static final String ENV_ORCHESTRATOR_DIR = "PI_ORCHESTRATOR_DIR";
    private static final String ENV_PI_CONFIG_DIR = "PI_CONFIG_DIR";

    private final Map<String, String> env;

    public OrchestratorConfig() {
        this(System.getenv());
    }

    public OrchestratorConfig(Map<String, String> env) {
        this.env = env != null ? Map.copyOf(env) : Map.of();
    }

    public Path getOrchestratorDir() {
        String envDir = env.get(ENV_ORCHESTRATOR_DIR);
        if (envDir != null && !envDir.isBlank()) {
            return Paths.get(envDir).toAbsolutePath().normalize();
        }

        String piDir = env.get(ENV_PI_CONFIG_DIR);
        if (piDir == null || piDir.isBlank()) {
            piDir = Paths.get(System.getProperty("user.home"), CONFIG_DIR_NAME).toString();
        }
        return Paths.get(piDir, "orchestrator").toAbsolutePath().normalize();
    }

    public Path getAuthPath() {
        return getOrchestratorDir().resolve("auth.json");
    }

    public Path getMachinePath() {
        return getOrchestratorDir().resolve("machine.json");
    }

    public Path getInstancesPath() {
        return getOrchestratorDir().resolve("instances.json");
    }

    public Path getSocketPath() {
        return getOrchestratorDir().resolve("orchestrator.sock");
    }

    public Path getLogsDir() {
        return getOrchestratorDir().resolve("logs");
    }

    public Path getInstanceStderrLogPath(String instanceId) {
        String safeId = instanceId == null || instanceId.isBlank()
                ? "unknown"
                : instanceId.replaceAll("[^A-Za-z0-9._-]", "_");
        return getLogsDir().resolve(safeId + ".stderr.log");
    }
}
