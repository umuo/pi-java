package works.earendil.pi.orchestrator.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.model.InstanceRecord;
import works.earendil.pi.orchestrator.model.InstanceStatus;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorSupervisorTest {

    @TempDir
    Path tempDir;

    private OrchestratorStorage storage;
    private OrchestratorSupervisor supervisor;

    @BeforeEach
    void setUp() {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        storage = new OrchestratorStorage(config);
        supervisor = new OrchestratorSupervisor(storage);
    }

    @Test
    void spawnAndStopInstance() throws IOException {
        InstanceRecord spawned = supervisor.spawnInstance("/workspace", "test-agent");
        assertThat(spawned.status()).isEqualTo(InstanceStatus.ONLINE);
        assertThat(spawned.cwd()).isEqualTo("/workspace");
        assertThat(spawned.label()).isEqualTo("test-agent");

        assertThat(supervisor.listLiveInstances()).hasSize(1);
        assertThat(supervisor.listInstances()).hasSize(1);

        Optional<InstanceRecord> stopped = supervisor.stopInstance(spawned.id());
        assertThat(stopped).isPresent();
        assertThat(stopped.get().status()).isEqualTo(InstanceStatus.STOPPED);

        assertThat(supervisor.listLiveInstances()).isEmpty();
        assertThat(storage.loadInstances()).isEmpty();
    }

    @Test
    void recoverAfterRestart() throws IOException {
        InstanceRecord inst = new InstanceRecord("old-id", InstanceStatus.ONLINE, "/dir", "2026-06-28T10:00:00Z", null, "worker", null, null, null);
        storage.upsertInstance(inst);

        supervisor.recoverAfterRestart();

        List<InstanceRecord> instances = storage.loadInstances();
        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).status()).isEqualTo(InstanceStatus.STOPPED);
    }
}
