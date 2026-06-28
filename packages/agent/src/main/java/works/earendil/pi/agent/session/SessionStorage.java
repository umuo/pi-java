package works.earendil.pi.agent.session;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface SessionStorage {
    String createEntryId();

    Optional<String> leafId();

    void setLeafId(String id) throws IOException;

    Optional<SessionEntry> entry(String id);

    List<SessionEntry> entries();

    List<SessionEntry> pathToRoot(String leafId);

    void append(SessionEntry entry) throws IOException;

    default List<SessionEntry> findByType(String type) {
        return entries().stream().filter(entry -> entry.type().equals(type)).toList();
    }
}
