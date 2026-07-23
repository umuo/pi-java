package works.earendil.pi.server.service;

import java.io.IOException;

public interface AgentProcessLauncher {
    AgentProcess start(StartRequest request) throws IOException;

    record StartRequest(String instanceId, String cwd, String label) {
    }
}
