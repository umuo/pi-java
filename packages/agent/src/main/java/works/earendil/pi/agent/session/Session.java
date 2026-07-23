package works.earendil.pi.agent.session;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class Session {
    private final SessionStorage storage;

    public Session(SessionStorage storage) {
        this.storage = storage;
    }

    public List<SessionEntry> entries() {
        return storage.entries();
    }

    public List<SessionEntry> branch() {
        return storage.leafId().map(storage::pathToRoot).orElse(List.of());
    }

    public Optional<String> sessionName() {
        return storage.sessionName();
    }

    public String appendSessionName(String name) throws IOException {
        String sanitized = name.replaceAll("[\\r\\n]+", " ").trim();
        SessionEntry.SessionInfoEntry entry = new SessionEntry.SessionInfoEntry(
                storage.createEntryId(), storage.leafId().orElse(null), Instant.now(), sanitized);
        storage.append(entry);
        return entry.id();
    }

    public String appendLabel(String targetId, String label) throws IOException {
        if (storage.entry(targetId).isEmpty()) {
            throw new IllegalArgumentException("Entry " + targetId + " not found");
        }
        SessionEntry.LabelEntry entry = new SessionEntry.LabelEntry(
                storage.createEntryId(), storage.leafId().orElse(null), Instant.now(), targetId, label);
        storage.append(entry);
        return entry.id();
    }

    public void moveTo(String entryId) throws IOException {
        if (entryId != null && storage.entry(entryId).isEmpty()) {
            throw new IllegalArgumentException("Entry " + entryId + " not found");
        }
        storage.setLeafId(entryId);
    }
}
