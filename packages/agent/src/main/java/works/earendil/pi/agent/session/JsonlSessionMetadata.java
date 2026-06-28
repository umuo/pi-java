package works.earendil.pi.agent.session;

import java.time.Instant;
import java.nio.file.Path;

public record JsonlSessionMetadata(
        String id,
        Instant createdAt,
        Path path,
        Path cwd,
        Path parentSessionPath
) {
}
