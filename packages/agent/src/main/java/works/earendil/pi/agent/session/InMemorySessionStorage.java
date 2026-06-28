package works.earendil.pi.agent.session;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class InMemorySessionStorage implements SessionStorage {
    private final Map<String, SessionEntry> entries = new LinkedHashMap<>();
    private String leafId;

    @Override
    public String createEntryId() {
        return UuidV7.create();
    }

    @Override
    public Optional<String> leafId() {
        return Optional.ofNullable(leafId);
    }

    @Override
    public void setLeafId(String id) throws IOException {
        if (id != null && !entries.containsKey(id)) {
            throw new IllegalArgumentException("Entry " + id + " not found");
        }
        SessionEntry.LeafEntry entry = new SessionEntry.LeafEntry(createEntryId(), leafId, java.time.Instant.now(), id);
        entries.put(entry.id(), entry);
        leafId = id;
    }

    @Override
    public Optional<SessionEntry> entry(String id) {
        return Optional.ofNullable(entries.get(id));
    }

    @Override
    public List<SessionEntry> entries() {
        return List.copyOf(entries.values());
    }

    @Override
    public List<SessionEntry> pathToRoot(String leafId) {
        LinkedList<SessionEntry> result = new LinkedList<>();
        String current = leafId;
        while (current != null) {
            SessionEntry entry = entries.get(current);
            if (entry == null) {
                break;
            }
            result.addFirst(entry);
            current = entry.parentId();
        }
        return List.copyOf(result);
    }

    @Override
    public void append(SessionEntry entry) throws IOException {
        entries.put(entry.id(), entry);
        leafId = entry instanceof SessionEntry.LeafEntry leaf ? leaf.targetId() : entry.id();
    }
}
