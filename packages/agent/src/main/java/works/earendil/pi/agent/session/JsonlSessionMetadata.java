package works.earendil.pi.agent.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.nio.file.Path;

public record JsonlSessionMetadata(
        String id,
        Instant createdAt,
        Path path,
        Path cwd,
        Path parentSessionPath,
        JsonNode customMetadata
) {
    public JsonlSessionMetadata(String id, Instant createdAt, Path path, Path cwd, Path parentSessionPath) {
        this(id, createdAt, path, cwd, parentSessionPath, null);
    }
}
