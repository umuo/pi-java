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
import java.util.List;
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

    @Test
    void tailsLatestInstanceLog() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);
        Files.createDirectories(config.getLogsDir());
        Files.writeString(config.getLogsDir().resolve("agent-1.stderr.log"), """
                one
                two
                three
                four
                """);
        Files.writeString(config.getLogsDir().resolve("agent-1.stderr.log.1"), "rotated");

        OrchestratorStatusReporter.LogTailView tail = new OrchestratorStatusReporter(storage)
                .tailLatestLog("agent-1", 2);

        assertThat(tail.rotation()).isZero();
        assertThat(tail.lines()).isEqualTo(2);
        assertThat(tail.content()).isEqualTo("three" + System.lineSeparator() + "four");
        assertThat(tail.render())
                .contains("Orchestrator log tail")
                .contains("instance: agent-1")
                .contains("rotation: 0")
                .contains("---\nthree" + System.lineSeparator() + "four");
    }

    @Test
    void rendersEmptyTailWhenNoLogsExist() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);

        OrchestratorStatusReporter.LogTailView tail = new OrchestratorStatusReporter(storage)
                .tailLatestLog("agent-missing", 20);

        assertThat(tail.lines()).isZero();
        assertThat(tail.render())
                .contains("Orchestrator log tail")
                .contains("message: no logs found for agent-missing");
    }

    @Test
    void rendersDashboardWithInstancesEventsAndStderrColumns() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);
        storage.upsertInstance(new InstanceRecord("agent-1", InstanceStatus.ONLINE, "/workspace",
                "2026-06-30T00:00:00Z", "2026-06-30T00:01:00Z", "reviewer",
                null, null, null));
        storage.upsertInstance(new InstanceRecord("agent-2", InstanceStatus.ONLINE, "/workspace",
                "2026-06-30T00:00:00Z", "2026-06-30T00:01:00Z", "writer",
                null, null, null));
        Files.createDirectories(config.getLogsDir());
        Files.writeString(config.getLogsDir().resolve("agent-1.stderr.log"), """
                old
                current stderr
                """);
        Files.writeString(config.getLogsDir().resolve("agent-2.stderr.log"), "filtered out\n");
        List<OrchestratorSupervisor.RpcEvent> events = List.of(
                new OrchestratorSupervisor.RpcEvent(7, "agent-1", "99",
                        "{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\"}}",
                        "2026-06-30T00:01:10Z"),
                new OrchestratorSupervisor.RpcEvent(8, "agent-2", "100",
                        "{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"ignored\"}}",
                        "2026-06-30T00:01:11Z"));

        OrchestratorStatusReporter.DashboardView dashboard = new OrchestratorStatusReporter(storage)
                .dashboard(events, "agent-1", 10, 2);

        String rendered = dashboard.render();
        assertThat(rendered)
                .contains("Orchestrator dashboard")
                .contains("scope: agent-1")
                .contains("instances: 1 | logs: 1 | recent events: 1")
                .contains("agent-1 [online] label=reviewer")
                .contains("event stream")
                .contains("| stderr")
                .contains("seq=7 agent-1 request=99")
                .contains("agent-1: current stderr")
                .doesNotContain("agent-2")
                .doesNotContain("filtered out");
    }
}
