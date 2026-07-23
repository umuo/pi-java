package works.earendil.pi.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.server.config.ServerConfig;
import works.earendil.pi.server.storage.ServerStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ServerRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesSharedStorageSupervisorAndStatusReporter() throws Exception {
        ServerConfig config = new ServerConfig(Map.of("PI_SERVER_DIR", tempDir.toString()));
        ServerStorage storage = new ServerStorage(config);
        ServerSupervisor supervisor = new ServerSupervisor(storage, request -> {
            throw new IOException("not used");
        });

        ServerRuntime runtime = new ServerRuntime(storage, supervisor);

        assertThat(runtime.storage()).isSameAs(storage);
        assertThat(runtime.supervisor()).isSameAs(supervisor);
        assertThat(runtime.statusReporter().snapshot().serverDir())
                .isEqualTo(config.getServerDir());
    }
}
