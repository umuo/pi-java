package works.earendil.pi.orchestrator.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void runtimeSettingsReturnDefaultsWhenFileIsMissing() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));

        OrchestratorRuntimeSettings settings = config.loadRuntimeSettings();

        assertThat(settings.restart().maxAttempts()).isEqualTo(3);
        assertThat(settings.restart().baseBackoff()).isEqualTo(Duration.ofSeconds(30));
        assertThat(settings.restart().maxBackoff()).isEqualTo(Duration.ofMinutes(5));
        assertThat(settings.logRotation().maxBytes()).isEqualTo(1024L * 1024L);
        assertThat(settings.logRotation().maxBackups()).isEqualTo(3);
    }

    @Test
    void runtimeSettingsParseRestartAndLogRotation() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        Files.createDirectories(tempDir);
        Files.writeString(config.getRuntimeSettingsPath(), """
                {
                  "restart": {
                    "maxAttempts": 5,
                    "baseBackoffMs": 2500,
                    "maxBackoffMs": 45000
                  },
                  "logRotation": {
                    "maxBytes": 4096,
                    "maxBackups": 7
                  }
                }
                """);

        OrchestratorRuntimeSettings settings = config.loadRuntimeSettings();

        assertThat(settings.restart().maxAttempts()).isEqualTo(5);
        assertThat(settings.restart().baseBackoff()).isEqualTo(Duration.ofMillis(2500));
        assertThat(settings.restart().maxBackoff()).isEqualTo(Duration.ofMillis(45000));
        assertThat(settings.logRotation().maxBytes()).isEqualTo(4096);
        assertThat(settings.logRotation().maxBackups()).isEqualTo(7);
    }

    @Test
    void runtimeSettingsCanDisableLogRotationAndClampInvalidRestartBackoff() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        Files.createDirectories(tempDir);
        Files.writeString(config.getRuntimeSettingsPath(), """
                {
                  "restart": {
                    "maxAttempts": -1,
                    "baseBackoffMs": 10000,
                    "maxBackoffMs": 1000
                  },
                  "logRotation": {
                    "enabled": false
                  }
                }
                """);

        OrchestratorRuntimeSettings settings = config.loadRuntimeSettings();

        assertThat(settings.restart().maxAttempts()).isEqualTo(3);
        assertThat(settings.restart().baseBackoff()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.restart().maxBackoff()).isEqualTo(Duration.ofSeconds(10));
        assertThat(settings.logRotation().maxBytes()).isZero();
        assertThat(settings.logRotation().maxBackups()).isZero();
    }
}
