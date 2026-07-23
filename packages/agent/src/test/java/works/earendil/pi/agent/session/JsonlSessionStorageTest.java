package works.earendil.pi.agent.session;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.common.json.JsonCodec;

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

    @Test
    void preservesCustomMetadataUsageAndShortRandomEntryIds() throws Exception {
        Path sessionFile = tempDir.resolve("metadata.jsonl");
        JsonlSessionStorage storage = JsonlSessionStorage.create(sessionFile, tempDir, "session-meta", null,
                JsonCodec.parse("{\"workspace\":\"java\"}"));
        String compactionId = storage.createEntryId();
        String branchId = storage.createEntryId();
        Usage usage = new Usage(11, 7, 2, 3, 1);
        storage.append(new SessionEntry.CompactionEntry(compactionId, null, Instant.now(), "summary", "kept",
                42, null, usage, false));
        storage.append(new SessionEntry.BranchSummaryEntry(branchId, compactionId, Instant.now(), "branch",
                compactionId, null, usage, false));

        JsonlSessionStorage reopened = JsonlSessionStorage.open(sessionFile);

        assertThat(compactionId).hasSize(8).isNotEqualTo(branchId);
        assertThat(reopened.metadata().customMetadata().path("workspace").asText()).isEqualTo("java");
        assertThat(((SessionEntry.CompactionEntry) reopened.entries().getFirst()).usage()).isEqualTo(usage);
        assertThat(((SessionEntry.BranchSummaryEntry) reopened.entries().get(1)).usage()).isEqualTo(usage);
        assertThat(reopened.statistics().cachedTokens()).isEqualTo(6);
        assertThat(reopened.statistics().uncachedTokens()).isEqualTo(26);
        assertThat(reopened.statistics().totalTokens()).isEqualTo(46);
    }
}
