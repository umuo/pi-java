package works.earendil.pi.agent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class JsonlSessionStorage implements SessionStorage {
    private static final int SESSION_VERSION = 3;

    private final Path path;
    private final JsonlSessionMetadata metadata;
    private final List<SessionEntry> entries;
    private final Map<String, SessionEntry> byId;
    private final Map<String, String> labelsById;
    private String leafId;

    private JsonlSessionStorage(Path path, JsonlSessionMetadata metadata, List<SessionEntry> entries, String leafId) {
        this.path = path;
        this.metadata = metadata;
        this.entries = new ArrayList<>(entries);
        this.byId = new LinkedHashMap<>();
        this.labelsById = new LinkedHashMap<>();
        for (SessionEntry entry : entries) {
            byId.put(entry.id(), entry);
            updateLabel(entry);
        }
        this.leafId = leafId;
    }

    public static JsonlSessionStorage create(Path path, Path cwd, String sessionId, Path parentSessionPath) throws IOException {
        return create(path, cwd, sessionId, parentSessionPath, null);
    }

    public static JsonlSessionStorage create(Path path, Path cwd, String sessionId, Path parentSessionPath,
                                             JsonNode customMetadata) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        ObjectNode header = JsonCodec.mapper().createObjectNode();
        header.put("type", "session");
        header.put("version", SESSION_VERSION);
        header.put("id", sessionId);
        header.put("timestamp", Instant.now().toString());
        header.put("cwd", cwd.toString());
        if (parentSessionPath != null) {
            header.put("parentSession", parentSessionPath.toString());
        }
        if (customMetadata != null && !customMetadata.isNull()) {
            header.set("metadata", customMetadata);
        }
        Files.writeString(path, JsonCodec.stringify(header) + "\n", StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return open(path);
    }

    public static JsonlSessionStorage open(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8).stream()
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            throw new IllegalArgumentException("Invalid JSONL session " + path + ": missing header");
        }
        JsonNode header = JsonCodec.parse(lines.getFirst());
        if (!"session".equals(header.path("type").asText()) || header.path("version").asInt() != SESSION_VERSION) {
            throw new IllegalArgumentException("Invalid JSONL session " + path + ": unsupported header");
        }
        JsonlSessionMetadata metadata = new JsonlSessionMetadata(
                header.path("id").asText(),
                Instant.parse(header.path("timestamp").asText()),
                path,
                Path.of(header.path("cwd").asText()),
                header.has("parentSession") ? Path.of(header.path("parentSession").asText()) : null,
                header.get("metadata"));
        List<SessionEntry> entries = new ArrayList<>();
        String leafId = null;
        for (int i = 1; i < lines.size(); i++) {
            SessionEntry entry = SessionEntryCodec.decode(lines.get(i), path.toString(), i + 1);
            entries.add(entry);
            leafId = leafIdAfter(entry);
        }
        return new JsonlSessionStorage(path, metadata, entries, leafId);
    }

    public JsonlSessionMetadata metadata() {
        return metadata;
    }

    @Override
    public String createEntryId() {
        for (int i = 0; i < 100; i++) {
            String uuid = UuidV7.create();
            String id = uuid.substring(uuid.length() - 8);
            if (!byId.containsKey(id)) {
                return id;
            }
        }
        return UuidV7.create();
    }

    @Override
    public Optional<String> leafId() {
        return Optional.ofNullable(leafId);
    }

    @Override
    public void setLeafId(String id) throws IOException {
        if (id != null && !byId.containsKey(id)) {
            throw new IllegalArgumentException("Entry " + id + " not found");
        }
        append(new SessionEntry.LeafEntry(createEntryId(), leafId, Instant.now(), id));
    }

    @Override
    public Optional<SessionEntry> entry(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public List<SessionEntry> entries() {
        return List.copyOf(entries);
    }

    @Override
    public List<SessionEntry> pathToRoot(String leafId) {
        if (leafId == null) {
            return List.of();
        }
        LinkedList<SessionEntry> path = new LinkedList<>();
        SessionEntry current = byId.get(leafId);
        if (current == null) {
            throw new IllegalArgumentException("Entry " + leafId + " not found");
        }
        while (current != null) {
            path.addFirst(current);
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        return List.copyOf(path);
    }

    @Override
    public void append(SessionEntry entry) throws IOException {
        appendLine(SessionEntryCodec.encode(entry));
        entries.add(entry);
        byId.put(entry.id(), entry);
        updateLabel(entry);
        leafId = leafIdAfter(entry);
    }

    public Optional<String> label(String id) {
        return Optional.ofNullable(labelsById.get(id));
    }

    private void appendLine(String line) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND);
             FileLock ignored = channel.lock()) {
            channel.write(StandardCharsets.UTF_8.encode(line + "\n"));
        }
    }

    private void updateLabel(SessionEntry entry) {
        if (entry instanceof SessionEntry.LabelEntry labelEntry) {
            String label = labelEntry.label() == null ? "" : labelEntry.label().trim();
            if (label.isEmpty()) {
                labelsById.remove(labelEntry.targetId());
            } else {
                labelsById.put(labelEntry.targetId(), label);
            }
        }
    }

    private static String leafIdAfter(SessionEntry entry) {
        return entry instanceof SessionEntry.LeafEntry leaf ? leaf.targetId() : entry.id();
    }
}
