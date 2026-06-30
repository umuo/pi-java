package works.earendil.pi.orchestrator.service;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

public interface AgentProcess extends AutoCloseable {
    void sendLine(String line) throws IOException;

    Optional<String> readLine(Duration timeout) throws IOException;

    boolean isAlive();

    default Optional<Path> stderrLogPath() {
        return Optional.empty();
    }

    void stop(Duration timeout) throws IOException;

    @Override
    default void close() throws IOException {
        stop(Duration.ofSeconds(2));
    }
}
