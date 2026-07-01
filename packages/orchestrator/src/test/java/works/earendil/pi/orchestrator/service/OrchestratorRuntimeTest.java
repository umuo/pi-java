package works.earendil.pi.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesSharedStorageSupervisorAndStatusReporter() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);
        OrchestratorSupervisor supervisor = new OrchestratorSupervisor(storage, request -> {
            throw new IOException("not used");
        });

        OrchestratorRuntime runtime = new OrchestratorRuntime(storage, supervisor);

        assertThat(runtime.storage()).isSameAs(storage);
        assertThat(runtime.supervisor()).isSameAs(supervisor);
        assertThat(runtime.statusReporter().snapshot().orchestratorDir())
                .isEqualTo(config.getOrchestratorDir());
    }
}
