package works.earendil.pi.orchestrator.storage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.model.InstanceRecord;
import works.earendil.pi.orchestrator.model.InstanceStatus;
import works.earendil.pi.orchestrator.model.MachineRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorStorageTest {

    @TempDir
    Path tempDir;

    private OrchestratorStorage storage;

    @BeforeEach
    void setUp() {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        storage = new OrchestratorStorage(config);
    }

    @Test
    void machineLifecycle() throws IOException {
        assertThat(storage.loadMachine()).isEmpty();

        MachineRecord record = new MachineRecord("mach-1", "2026-06-28T12:00:00Z", null, "my-machine");
        storage.saveMachine(record);

        Optional<MachineRecord> loaded = storage.loadMachine();
        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo("mach-1");
        assertThat(loaded.get().label()).isEqualTo("my-machine");

        storage.deleteMachine();
        assertThat(storage.loadMachine()).isEmpty();
    }

    @Test
    void instancesLifecycle() throws IOException {
        assertThat(storage.loadInstances()).isEmpty();

        InstanceRecord inst1 = new InstanceRecord("inst-1", InstanceStatus.ONLINE, "/cwd", "2026-06-28T12:00:00Z", null, "worker-1", "sess-1", "file-1", null);
        storage.upsertInstance(inst1);

        List<InstanceRecord> instances = storage.loadInstances();
        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).status()).isEqualTo(InstanceStatus.ONLINE);

        InstanceRecord updated = inst1.withStatus(InstanceStatus.STOPPED);
        storage.upsertInstance(updated);

        assertThat(storage.loadInstances()).hasSize(1);
        assertThat(storage.getInstance("inst-1")).isPresent();
        assertThat(storage.getInstance("inst-1").get().status()).isEqualTo(InstanceStatus.STOPPED);

        storage.removeInstance("inst-1");
        assertThat(storage.loadInstances()).isEmpty();
    }

    @Test
    void configExposesSanitizedInstanceLogPath() {
        Path logPath = storage.config().getInstanceStderrLogPath("agent/one:two");

        assertThat(logPath.getParent()).isEqualTo(tempDir.resolve("logs").toAbsolutePath().normalize());
        assertThat(logPath.getFileName().toString()).isEqualTo("agent_one_two.stderr.log");
    }

    @Test
    void listsInstanceStderrLogs() throws IOException {
        Files.createDirectories(storage.config().getLogsDir());
        Files.writeString(storage.config().getLogsDir().resolve("b.stderr.log"), "two");
        Files.writeString(storage.config().getLogsDir().resolve("a.stderr.log"), "one");
        Files.writeString(storage.config().getLogsDir().resolve("a.stderr.log.1"), "older");
        Files.writeString(storage.config().getLogsDir().resolve("a.stderr.log.2"), "oldest");
        Files.writeString(storage.config().getLogsDir().resolve("a.stderr.log.old"), "ignored");
        Files.writeString(storage.config().getLogsDir().resolve("a.stderr.log.0"), "ignored");
        Files.writeString(storage.config().getLogsDir().resolve("ignore.txt"), "ignored");

        List<OrchestratorStorage.InstanceLogRecord> logs = storage.listInstanceLogs();

        assertThat(logs).extracting(OrchestratorStorage.InstanceLogRecord::instanceId)
                .containsExactly("a", "a", "a", "b");
        assertThat(logs).extracting(OrchestratorStorage.InstanceLogRecord::rotation)
                .containsExactly(0, 1, 2, 0);
        assertThat(logs).allSatisfy(log -> {
            assertThat(log.path()).isAbsolute();
            assertThat(log.bytes()).isPositive();
            assertThat(log.modifiedAt()).isNotBlank();
        });
    }
}
