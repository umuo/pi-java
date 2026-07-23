package works.earendil.pi.storage.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.agent.session.Session;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SqliteSessionStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void persistsSessionMetadataEntriesNamesCursorsAndLeafMoves() throws Exception {
        Path database = tempDir.resolve("sessions.db");
        String rootId;
        try (SqliteSessionStorage storage = SqliteSessionStorage.create(
                database, tempDir, "session-1", null, JsonCodec.parse("{\"owner\":\"test\"}"))) {
            rootId = storage.createEntryId();
            storage.append(new SessionEntry.SessionInfoEntry(rootId, null, Instant.now(), "Review"));
            String labelId = storage.createEntryId();
            storage.append(new SessionEntry.LabelEntry(labelId, rootId, Instant.now(), rootId, "important"));
            storage.setLeafId(rootId);

            assertThat(storage.entriesAfter(rootId, 1)).singleElement()
                    .extracting(SessionEntry::id).isEqualTo(labelId);
            assertThat(storage.statistics().entries()).isEqualTo(3);
        }

        try (SqliteSessionStorage reopened = SqliteSessionStorage.open(database, "session-1")) {
            assertThat(reopened.metadata().customMetadata().path("owner").asText()).isEqualTo("test");
            assertThat(reopened.sessionName()).contains("Review");
            assertThat(reopened.leafId()).contains(rootId);
            assertThat(reopened.pathToRoot(rootId)).singleElement()
                    .extracting(SessionEntry::id).isEqualTo(rootId);
            assertThat(new Session(reopened).sessionName()).contains("Review");
        }
    }
}
