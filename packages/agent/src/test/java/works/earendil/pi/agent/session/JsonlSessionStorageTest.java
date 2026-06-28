package works.earendil.pi.agent.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonlSessionStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsEntriesLeafMovesAndLabels() throws Exception {
        Path sessionFile = tempDir.resolve("session.jsonl");
        JsonlSessionStorage storage = JsonlSessionStorage.create(sessionFile, tempDir, "session-1", null);

        SessionEntry.SessionInfoEntry root = new SessionEntry.SessionInfoEntry(
                storage.createEntryId(), null, Instant.now(), "Root");
        storage.append(root);
        SessionEntry.LabelEntry label = new SessionEntry.LabelEntry(
                storage.createEntryId(), root.id(), Instant.now(), root.id(), "important");
        storage.append(label);
        storage.setLeafId(root.id());

        JsonlSessionStorage reopened = JsonlSessionStorage.open(sessionFile);

        assertThat(reopened.metadata().id()).isEqualTo("session-1");
        assertThat(reopened.leafId()).contains(root.id());
        assertThat(reopened.label(root.id())).contains("important");
        assertThat(reopened.pathToRoot(root.id()).stream().map(SessionEntry::id)).containsExactly(root.id());
        assertThat(reopened.entries().stream().map(SessionEntry::type)).containsExactly(
                "session_info", "label", "leaf");
    }

    @Test
    void inMemoryStorageMirrorsLeafEntryBehavior() throws Exception {
        InMemorySessionStorage storage = new InMemorySessionStorage();
        SessionEntry.SessionInfoEntry root = new SessionEntry.SessionInfoEntry(
                storage.createEntryId(), null, Instant.now(), "Root");
        storage.append(root);
        storage.setLeafId(root.id());

        assertThat(storage.leafId()).contains(root.id());
        assertThat(storage.entries().stream().map(SessionEntry::type)).containsExactly("session_info", "leaf");
        assertThat(storage.pathToRoot(root.id()).stream().map(SessionEntry::id)).containsExactly(root.id());
    }
}
