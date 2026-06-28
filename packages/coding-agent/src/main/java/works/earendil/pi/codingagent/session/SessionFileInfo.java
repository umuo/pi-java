package works.earendil.pi.codingagent.session;

import java.nio.file.Path;
import java.time.Instant;

public record SessionFileInfo(
        Path path,
        String id,
        Path cwd,
        String name,
        Path parentSessionPath,
        Instant created,
        Instant modified,
        int messageCount,
        String firstMessage,
        String allMessagesText) {
}
