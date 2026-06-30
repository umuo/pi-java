package works.earendil.pi.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.model.InstanceRecord;
import works.earendil.pi.orchestrator.model.InstanceStatus;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorStatusReporterTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersInstancesLogsRuntimeSettingsHeartbeatAndEventStream() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);
        Files.writeString(config.getRuntimeSettingsPath(), """
                {
                  "restart": {
                    "maxAttempts": 5,
                    "baseBackoffMs": 1000,
                    "maxBackoffMs": 8000
                  },
                  "logRotation": {
                    "enabled": true,
                    "maxBytes": 2048,
                    "maxBackups": 4
                  }
                }
                """);
        storage.upsertInstance(new InstanceRecord("agent-1", InstanceStatus.ONLINE, "/workspace",
                "2026-06-30T00:00:00Z", "2026-06-30T00:01:00Z", "reviewer",
                null, null, null));
        Files.createDirectories(config.getLogsDir());
        Files.writeString(config.getLogsDir().resolve("agent-1.stderr.log"), "latest log");
        Files.writeString(config.getLogsDir().resolve("agent-1.stderr.log.1"), "older log");
        Clock clock = Clock.fixed(Instant.parse("2026-06-30T00:02:30Z"), ZoneOffset.UTC);

        OrchestratorStatusReporter.StatusReport report = new OrchestratorStatusReporter(storage, clock).snapshot();

        assertThat(report.instances()).hasSize(1);
        assertThat(report.logs()).hasSize(2);
        assertThat(report.instances().getFirst().heartbeatAge()).isEqualTo("1m");
        assertThat(report.render())
                .contains("Orchestrator status")
                .contains("settings: " + config.getRuntimeSettingsPath())
                .contains("restart: maxAttempts=5, baseBackoff=1000ms, maxBackoff=8000ms")
                .contains("log rotation: enabled=true, maxBytes=2048, maxBackups=4")
                .contains("agent-1 [online] label=reviewer")
                .contains("heartbeat: lastSeen=2026-06-30T00:01:00Z, age=1m")
                .contains("logs: 2")
                .contains("event stream: available via OrchestratorSupervisor.subscribeRpcEvents(instanceId, listener)");
    }
}
