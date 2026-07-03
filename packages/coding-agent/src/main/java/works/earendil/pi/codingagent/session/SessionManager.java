package works.earendil.pi.codingagent.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.session.InMemorySessionStorage;
import works.earendil.pi.agent.session.JsonlSessionStorage;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.agent.session.SessionEntryCodec;
import works.earendil.pi.agent.session.SessionStorage;
import works.earendil.pi.agent.session.UuidV7;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class SessionManager {
    public static final int CURRENT_SESSION_VERSION = 3;

    private static final Pattern VALID_SESSION_ID =
            Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$");

    private final Path cwd;
    private final Path sessionDir;
    private final Path sessionFile;
    private final boolean persisted;
    private final SessionStorage storage;
    private final Map<String, SessionEntry> byId = new LinkedHashMap<>();
    private final Map<String, String> labelsById = new HashMap<>();
    private final Map<String, Instant> labelTimestampsById = new HashMap<>();
    private String sessionId;
    private String leafId;

    private SessionManager(Path cwd, Path sessionDir, Path sessionFile, boolean persisted, SessionStorage storage,
                           String sessionId) {
        this.cwd = cwd.toAbsolutePath().normalize();
        this.sessionDir = sessionDir == null ? null : sessionDir.toAbsolutePath().normalize();
        this.sessionFile = sessionFile == null ? null : sessionFile.toAbsolutePath().normalize();
        this.persisted = persisted;
        this.storage = storage;
        this.sessionId = sessionId;
        rebuildIndex();
    }

    public record NewSessionOptions(String id, Path parentSession) {
        public static NewSessionOptions empty() {
            return new NewSessionOptions(null, null);
        }
    }

    public record SessionHeader(String id, Instant timestamp, Path cwd, Path parentSession) {
    }

    public record SessionTreeNode(SessionEntry entry, List<SessionTreeNode> children, String label,
                                  Instant labelTimestamp) {
    }

    public static void assertValidSessionId(String id) {
        if (id == null || !VALID_SESSION_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Invalid session id: " + id + ". Use letters, numbers, dashes, underscores, and dots.");
        }
    }

    public static String createSessionId() {
        return UuidV7.create();
    }

    public static SessionManager create(Path cwd) throws IOException {
        return create(cwd, SessionPaths.defaultSessionDir(cwd), NewSessionOptions.empty());
    }

    public static SessionManager create(Path cwd, Path sessionDir) throws IOException {
        return create(cwd, sessionDir, NewSessionOptions.empty());
    }

    public static SessionManager create(Path cwd, Path sessionDir, NewSessionOptions options) throws IOException {
        NewSessionOptions resolvedOptions = options == null ? NewSessionOptions.empty() : options;
        if (resolvedOptions.id() != null) {
            assertValidSessionId(resolvedOptions.id());
        }
        String id = resolvedOptions.id() == null ? createSessionId() : resolvedOptions.id();
        Path dir = sessionDir.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Path file = dir.resolve(fileTimestamp(Instant.now()) + "_" + id + ".jsonl");
        JsonlSessionStorage storage = JsonlSessionStorage.create(file, cwd.toAbsolutePath().normalize(), id,
                resolvedOptions.parentSession());
        return new SessionManager(cwd, dir, file, true, storage, id);
    }

    public static SessionManager open(Path path) throws IOException {
        return open(path, null, null);
    }

    public static SessionManager open(Path path, Path sessionDir, Path cwdOverride) throws IOException {
        Path resolvedPath = path.toAbsolutePath().normalize();
        migrateSessionFileIfNeeded(resolvedPath);
        JsonlSessionStorage storage = JsonlSessionStorage.open(resolvedPath);
        Path cwd = cwdOverride == null ? storage.metadata().cwd() : cwdOverride.toAbsolutePath().normalize();
        Path dir = sessionDir == null ? resolvedPath.getParent() : sessionDir.toAbsolutePath().normalize();
        return new SessionManager(cwd, dir, resolvedPath, true, storage, storage.metadata().id());
    }

    public static SessionManager continueRecent(Path cwd) throws IOException {
        return continueRecent(cwd, SessionPaths.defaultSessionDir(cwd));
    }

    public static SessionManager continueRecent(Path cwd, Path sessionDir) throws IOException {
        Optional<Path> recent = findMostRecentSession(sessionDir, cwd);
        return recent.isPresent() ? open(recent.get(), sessionDir, cwd) : create(cwd, sessionDir);
    }

    public static SessionManager inMemory(Path cwd) {
        return inMemory(cwd, NewSessionOptions.empty());
    }

    public static SessionManager inMemory(Path cwd, NewSessionOptions options) {
        NewSessionOptions resolvedOptions = options == null ? NewSessionOptions.empty() : options;
        if (resolvedOptions.id() != null) {
            assertValidSessionId(resolvedOptions.id());
        }
        String id = resolvedOptions.id() == null ? createSessionId() : resolvedOptions.id();
        return new SessionManager(cwd, null, null, false, new InMemorySessionStorage(), id);
    }

    public static SessionManager forkFrom(Path sourcePath, Path targetCwd) throws IOException {
        return forkFrom(sourcePath, targetCwd, SessionPaths.defaultSessionDir(targetCwd), NewSessionOptions.empty());
    }

    public static SessionManager forkFrom(Path sourcePath, Path targetCwd, Path sessionDir, NewSessionOptions options)
            throws IOException {
        ParsedSession source = parseSessionFile(sourcePath.toAbsolutePath().normalize());
        if (source.header() == null) {
            throw new IllegalArgumentException("Cannot fork: source session file is empty or invalid: " + sourcePath);
        }
        NewSessionOptions resolvedOptions = options == null ? NewSessionOptions.empty() : options;
        if (resolvedOptions.id() != null) {
            assertValidSessionId(resolvedOptions.id());
        }
        String id = resolvedOptions.id() == null ? createSessionId() : resolvedOptions.id();
        Path dir = sessionDir.toAbsolutePath().normalize();
        Files.createDirectories(dir);
        Instant now = Instant.now();
        Path file = dir.resolve(fileTimestamp(now) + "_" + id + ".jsonl");
        ObjectNode header = JsonCodec.mapper().createObjectNode();
        header.put("type", "session");
        header.put("version", CURRENT_SESSION_VERSION);
        header.put("id", id);
        header.put("timestamp", now.toString());
        header.put("cwd", targetCwd.toAbsolutePath().normalize().toString());
        header.put("parentSession", sourcePath.toAbsolutePath().normalize().toString());

        List<String> lines = new ArrayList<>();
        lines.add(JsonCodec.stringify(header));
        for (SessionEntry entry : source.entries()) {
            lines.add(SessionEntryCodec.encode(entry));
        }
        Files.write(file, lines, StandardCharsets.UTF_8);
        return open(file, dir, targetCwd);
    }

    public static Optional<Path> findMostRecentSession(Path sessionDir) throws IOException {
        return findMostRecentSession(sessionDir, null);
    }

    public static Optional<Path> findMostRecentSession(Path sessionDir, Path cwd) throws IOException {
        if (!Files.isDirectory(sessionDir)) {
            return Optional.empty();
        }
        Path resolvedCwd = cwd == null ? null : cwd.toAbsolutePath().normalize();
        List<Path> files;
        try (Stream<Path> stream = Files.list(sessionDir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList();
        }
        return files.stream()
                .map(path -> sessionInfoQuietly(path).orElse(null))
                .filter(info -> info != null)
                .filter(info -> resolvedCwd == null || SessionPaths.cwdMatches(info.cwd(), resolvedCwd))
                .max(Comparator.comparing(SessionFileInfo::modified))
                .map(SessionFileInfo::path);
    }

    public static List<SessionFileInfo> list(Path cwd) throws IOException {
        return list(cwd, SessionPaths.defaultSessionDir(cwd), null);
    }

    public static List<SessionFileInfo> list(Path cwd, Path sessionDir, BiConsumer<Integer, Integer> onProgress)
            throws IOException {
        Path resolvedCwd = cwd.toAbsolutePath().normalize();
        List<SessionFileInfo> sessions = listSessionsFromDir(sessionDir, onProgress).stream()
                .filter(session -> SessionPaths.cwdMatches(session.cwd(), resolvedCwd))
                .sorted(Comparator.comparing(SessionFileInfo::modified).reversed())
                .toList();
        return List.copyOf(sessions);
    }

    public static List<SessionFileInfo> listAll(Path sessionsRoot, BiConsumer<Integer, Integer> onProgress)
            throws IOException {
        if (!Files.isDirectory(sessionsRoot)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(sessionsRoot)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                try (Stream<Path> stream = Files.list(dir)) {
                    stream.filter(path -> path.getFileName().toString().endsWith(".jsonl")).forEach(files::add);
                }
            }
        }
        return buildSessionInfos(files, onProgress);
    }

    public static List<SessionFileInfo> listSessionsFromDir(Path sessionDir, BiConsumer<Integer, Integer> onProgress)
            throws IOException {
        if (!Files.isDirectory(sessionDir)) {
            return List.of();
        }
        List<Path> files;
        try (Stream<Path> stream = Files.list(sessionDir)) {
            files = stream.filter(path -> path.getFileName().toString().endsWith(".jsonl")).toList();
        }
        return buildSessionInfos(files, onProgress);
    }

    public static Optional<SessionHeader> readSessionHeader(Path path) {
        try {
            ParsedSession parsed = parseSessionFile(path);
            return parsed.headerNode() == null ? Optional.empty() : Optional.of(toHeader(parsed.headerNode()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public static boolean migrateSessionFileIfNeeded(Path path) throws IOException {
        List<ObjectNode> nodes = parseObjectLines(path);
        if (nodes.isEmpty() || !"session".equals(nodes.getFirst().path("type").asText())) {
            return false;
        }
        ObjectNode header = nodes.getFirst();
        int version = header.has("version") ? header.path("version").asInt(CURRENT_SESSION_VERSION) : 1;
        if (version >= CURRENT_SESSION_VERSION) {
            return false;
        }
        if (version < 2) {
            migrateV1ToV2(nodes);
        }
        if (version < 3) {
            migrateV2ToV3(nodes);
        }
        List<String> lines = nodes.stream().map(JsonCodec::stringify).toList();
        Files.write(path, lines, StandardCharsets.UTF_8);
        return true;
    }

    public static Optional<SessionFileInfo> buildSessionInfo(Path path) {
        try {
            ParsedSession parsed = parseSessionFile(path);
            if (parsed.headerNode() == null) {
                return Optional.empty();
            }
            SessionHeader header = toHeader(parsed.headerNode());
            String name = null;
            String firstMessage = null;
            StringBuilder allMessages = new StringBuilder();
            int messageCount = 0;
            Instant latestMessageTime = null;
            for (SessionEntry entry : parsed.entries()) {
                if (entry instanceof SessionEntry.SessionInfoEntry info) {
                    String candidate = info.name() == null ? "" : info.name().trim();
                    name = candidate.isEmpty() ? null : candidate;
                } else if (entry instanceof SessionEntry.MessageEntry message) {
                    messageCount++;
                    String text = messageText(message.message());
                    if (!text.isBlank()) {
                        if (allMessages.length() > 0) {
                            allMessages.append('\n');
                        }
                        allMessages.append(text);
                    }
                    if (firstMessage == null && "user".equals(message.message().path("role").asText())) {
                        firstMessage = text.isBlank() ? null : text;
                    }
                    String role = message.message().path("role").asText();
                    if ("user".equals(role) || "assistant".equals(role)) {
                        latestMessageTime = latest(latestMessageTime, message.timestamp());
                    }
                }
            }
            FileTime mtime = Files.getLastModifiedTime(path);
            Instant modified = latestMessageTime == null ? latest(header.timestamp(), mtime.toInstant()) : latestMessageTime;
            return Optional.of(new SessionFileInfo(path.toAbsolutePath().normalize(), header.id(), header.cwd(), name,
                    header.parentSession(), header.timestamp(), modified, messageCount, firstMessage,
                    allMessages.toString()));
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    public Path cwd() {
        return cwd;
    }

    public Path sessionDir() {
        return sessionDir;
    }

    public boolean usesDefaultSessionDir() {
        return persisted && sessionDir != null && sessionDir.equals(SessionPaths.defaultSessionDirPath(cwd));
    }

    public String sessionId() {
        return sessionId;
    }

    public Optional<Path> sessionFile() {
        return Optional.ofNullable(sessionFile);
    }

    public boolean isPersisted() {
        return persisted;
    }

    public List<SessionEntry> entries() {
        return storage.entries();
    }

    public Optional<SessionEntry> entry(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Optional<String> leafId() {
        return Optional.ofNullable(leafId);
    }

    public Optional<SessionEntry> leafEntry() {
        return leafId == null ? Optional.empty() : Optional.ofNullable(byId.get(leafId));
    }

    public String appendMessage(JsonNode message) throws IOException {
        return append(new SessionEntry.MessageEntry(nextId(), leafId, Instant.now(), message));
    }

    public String appendThinkingLevelChange(String thinkingLevel) throws IOException {
        return append(new SessionEntry.ThinkingLevelChangeEntry(nextId(), leafId, Instant.now(), thinkingLevel));
    }

    public String appendModelChange(String provider, String modelId) throws IOException {
        return append(new SessionEntry.ModelChangeEntry(nextId(), leafId, Instant.now(), provider, modelId));
    }

    public String appendCompaction(String summary, String firstKeptEntryId, int tokensBefore, JsonNode details,
                                   boolean fromHook) throws IOException {
        return append(new SessionEntry.CompactionEntry(nextId(), leafId, Instant.now(), summary, firstKeptEntryId,
                tokensBefore, details, fromHook));
    }

    public String appendCustomEntry(String customType, JsonNode data) throws IOException {
        return append(new SessionEntry.CustomEntry(nextId(), leafId, Instant.now(), customType, data));
    }

    public String appendCustomMessage(String customType, JsonNode content, boolean display, JsonNode details) throws IOException {
        return append(new SessionEntry.CustomMessageEntry(nextId(), leafId, Instant.now(), customType, content,
                display, details));
    }

    public String appendSessionInfo(String name) throws IOException {
        String sanitized = name == null ? "" : name.replaceAll("[\\r\\n]+", " ").trim();
        return append(new SessionEntry.SessionInfoEntry(nextId(), leafId, Instant.now(), sanitized));
    }

    public Optional<String> sessionName() {
        List<SessionEntry> entries = entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            if (entries.get(i) instanceof SessionEntry.SessionInfoEntry info) {
                String name = info.name() == null ? "" : info.name().trim();
                return name.isEmpty() ? Optional.empty() : Optional.of(name);
            }
        }
        return Optional.empty();
    }

    public String appendLabelChange(String targetId, String label) throws IOException {
        if (!byId.containsKey(targetId)) {
            throw new IllegalArgumentException("Entry " + targetId + " not found");
        }
        return append(new SessionEntry.LabelEntry(nextId(), leafId, Instant.now(), targetId, label));
    }

    public Optional<String> label(String id) {
        return Optional.ofNullable(labelsById.get(id));
    }

    public List<SessionEntry> branch() {
        return branch(leafId);
    }

    public List<SessionEntry> branch(String fromId) {
        LinkedList<SessionEntry> path = new LinkedList<>();
        SessionEntry current = fromId == null ? null : byId.get(fromId);
        while (current != null) {
            path.addFirst(current);
            current = current.parentId() == null ? null : byId.get(current.parentId());
        }
        return List.copyOf(path);
    }

    public List<SessionTreeNode> tree() {
        Map<String, SessionTreeNode> nodes = new LinkedHashMap<>();
        List<SessionTreeNode> roots = new ArrayList<>();
        for (SessionEntry entry : entries()) {
            nodes.put(entry.id(), new SessionTreeNode(entry, new ArrayList<>(), labelsById.get(entry.id()),
                    labelTimestampsById.get(entry.id())));
        }
        for (SessionEntry entry : entries()) {
            SessionTreeNode node = nodes.get(entry.id());
            if (entry.parentId() == null || entry.parentId().equals(entry.id())) {
                roots.add(node);
            } else {
                SessionTreeNode parent = nodes.get(entry.parentId());
                if (parent == null) {
                    roots.add(node);
                } else {
                    parent.children().add(node);
                }
            }
        }
        sortTree(roots);
        return List.copyOf(roots);
    }

    public void branchFrom(String branchFromId) {
        if (!byId.containsKey(branchFromId)) {
            throw new IllegalArgumentException("Entry " + branchFromId + " not found");
        }
        leafId = branchFromId;
    }

    public void resetLeaf() {
        leafId = null;
    }

    public String branchWithSummary(String branchFromId, String summary, JsonNode details, boolean fromHook)
            throws IOException {
        if (branchFromId != null && !byId.containsKey(branchFromId)) {
            throw new IllegalArgumentException("Entry " + branchFromId + " not found");
        }
        leafId = branchFromId;
        return append(new SessionEntry.BranchSummaryEntry(nextId(), branchFromId, Instant.now(),
                summary, branchFromId == null ? "root" : branchFromId, details, fromHook));
    }

    public Path createBranchedSession(String targetLeafId) throws IOException {
        if (!persisted || sessionDir == null) {
            throw new IllegalStateException("Cannot create branched file for an in-memory session");
        }
        List<SessionEntry> sourceBranch = branch(targetLeafId);
        if (sourceBranch.isEmpty()) {
            throw new IllegalArgumentException("Entry " + targetLeafId + " not found");
        }
        String id = createSessionId();
        Instant now = Instant.now();
        Path branchedFile = sessionDir.resolve(fileTimestamp(now) + "_" + id + ".jsonl");
        JsonlSessionStorage branched = JsonlSessionStorage.create(branchedFile, cwd, id, sessionFile);
        String parentId = null;
        Set<String> retainedIds = new HashSet<>();
        for (SessionEntry entry : sourceBranch) {
            if (entry instanceof SessionEntry.LabelEntry) {
                continue;
            }
            SessionEntry reparented = withParent(entry, parentId);
            branched.append(reparented);
            parentId = entry.id();
            retainedIds.add(entry.id());
        }
        for (Map.Entry<String, String> label : labelsById.entrySet()) {
            if (retainedIds.contains(label.getKey())) {
                branched.append(new SessionEntry.LabelEntry(branched.createEntryId(),
                        branched.leafId().orElse(null), Instant.now(), label.getKey(), label.getValue()));
            }
        }
        return branchedFile;
    }

    public void copySessionFile(Path target) throws IOException {
        if (sessionFile == null) {
            throw new IllegalStateException("In-memory sessions do not have a session file");
        }
        Files.copy(sessionFile, target, StandardCopyOption.REPLACE_EXISTING);
    }

    private String append(SessionEntry entry) throws IOException {
        storage.append(entry);
        byId.put(entry.id(), entry);
        updateLabel(entry);
        leafId = leafIdAfter(entry);
        return entry.id();
    }

    private String nextId() {
        String id;
        do {
            id = storage.createEntryId();
        } while (byId.containsKey(id));
        return id;
    }

    private void rebuildIndex() {
        byId.clear();
        labelsById.clear();
        labelTimestampsById.clear();
        leafId = storage.leafId().orElse(null);
        for (SessionEntry entry : storage.entries()) {
            byId.put(entry.id(), entry);
            updateLabel(entry);
            leafId = leafIdAfter(entry);
        }
    }

    private void updateLabel(SessionEntry entry) {
        if (entry instanceof SessionEntry.LabelEntry labelEntry) {
            String label = labelEntry.label() == null ? "" : labelEntry.label().trim();
            if (label.isEmpty()) {
                labelsById.remove(labelEntry.targetId());
                labelTimestampsById.remove(labelEntry.targetId());
            } else {
                labelsById.put(labelEntry.targetId(), label);
                labelTimestampsById.put(labelEntry.targetId(), labelEntry.timestamp());
            }
        }
    }

    private static List<SessionFileInfo> buildSessionInfos(List<Path> files, BiConsumer<Integer, Integer> onProgress) {
        int total = files.size();
        List<SessionFileInfo> sessions = new ArrayList<>();
        for (int i = 0; i < files.size(); i++) {
            buildSessionInfo(files.get(i)).ifPresent(sessions::add);
            if (onProgress != null) {
                onProgress.accept(i + 1, total);
            }
        }
        sessions.sort(Comparator.comparing(SessionFileInfo::modified).reversed());
        return List.copyOf(sessions);
    }

    private static Optional<SessionFileInfo> sessionInfoQuietly(Path path) {
        return buildSessionInfo(path);
    }

    private static ParsedSession parseSessionFile(Path path) throws IOException {
        JsonNode header = null;
        List<SessionEntry> entries = new ArrayList<>();
        if (!Files.isRegularFile(path)) {
            return new ParsedSession(null, List.of());
        }
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            JsonNode node;
            try {
                node = JsonCodec.parse(line);
            } catch (RuntimeException e) {
                continue;
            }
            if (header == null) {
                if ("session".equals(node.path("type").asText()) && node.path("id").isTextual()) {
                    header = node;
                }
                continue;
            }
            try {
                entries.add(SessionEntryCodec.decode(line, path.toString(), i + 1));
            } catch (RuntimeException ignored) {
                // TS loadEntriesFromFile skips malformed lines so one bad append does not hide the session.
            }
        }
        return new ParsedSession(header, List.copyOf(entries));
    }

    private static List<ObjectNode> parseObjectLines(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        List<ObjectNode> nodes = new ArrayList<>();
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                JsonNode node = JsonCodec.parse(line);
                if (node instanceof ObjectNode objectNode) {
                    nodes.add(objectNode);
                }
            } catch (RuntimeException ignored) {
                // Match TS parsing behavior: malformed JSONL lines are ignored.
            }
        }
        return nodes;
    }

    private static void migrateV1ToV2(List<ObjectNode> nodes) {
        Set<String> ids = new HashSet<>();
        String previousId = null;
        for (ObjectNode node : nodes) {
            if ("session".equals(node.path("type").asText())) {
                node.put("version", 2);
                continue;
            }
            String id = generateMigrationId(ids);
            ids.add(id);
            node.put("id", id);
            if (previousId == null) {
                node.putNull("parentId");
            } else {
                node.put("parentId", previousId);
            }
            previousId = id;
        }
        for (ObjectNode node : nodes) {
            if ("compaction".equals(node.path("type").asText()) && node.path("firstKeptEntryIndex").isNumber()) {
                int index = node.path("firstKeptEntryIndex").asInt();
                if (index >= 0 && index < nodes.size()) {
                    JsonNode target = nodes.get(index);
                    if (!"session".equals(target.path("type").asText()) && target.path("id").isTextual()) {
                        node.put("firstKeptEntryId", target.path("id").asText());
                    }
                }
                node.remove("firstKeptEntryIndex");
            }
        }
    }

    private static void migrateV2ToV3(List<ObjectNode> nodes) {
        for (ObjectNode node : nodes) {
            if ("session".equals(node.path("type").asText())) {
                node.put("version", 3);
                continue;
            }
            if ("message".equals(node.path("type").asText()) && node.get("message") instanceof ObjectNode message
                    && "hookMessage".equals(message.path("role").asText())) {
                message.put("role", "custom");
            }
        }
    }

    private static String generateMigrationId(Set<String> existing) {
        for (int i = 0; i < 100; i++) {
            String id = UUID.randomUUID().toString().substring(0, 8);
            if (!existing.contains(id)) {
                return id;
            }
        }
        return UUID.randomUUID().toString();
    }

    private static SessionHeader toHeader(JsonNode header) {
        String id = header.path("id").asText();
        Instant timestamp = Instant.parse(header.path("timestamp").asText());
        Path cwd = Path.of(header.path("cwd").asText()).toAbsolutePath().normalize();
        Path parentSession = header.hasNonNull("parentSession")
                ? Path.of(header.path("parentSession").asText()).toAbsolutePath().normalize()
                : null;
        return new SessionHeader(id, timestamp, cwd, parentSession);
    }

    private static String messageText(JsonNode message) {
        JsonNode content = message.get("content");
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            List<String> parts = new ArrayList<>();
            for (JsonNode item : content) {
                if (item.isTextual()) {
                    parts.add(item.asText());
                } else if ("text".equals(item.path("type").asText()) && item.has("text")) {
                    parts.add(item.path("text").asText());
                }
            }
            return String.join("\n", parts);
        }
        return content.toString();
    }

    private static Instant latest(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAfter(b) ? a : b;
    }

    private static String fileTimestamp(Instant instant) {
        return instant.toString().replace(':', '-').replace('.', '-');
    }

    private static String leafIdAfter(SessionEntry entry) {
        return entry instanceof SessionEntry.LeafEntry leaf ? leaf.targetId() : entry.id();
    }

    private static void sortTree(List<SessionTreeNode> roots) {
        List<SessionTreeNode> stack = new ArrayList<>(roots);
        while (!stack.isEmpty()) {
            SessionTreeNode node = stack.removeLast();
            node.children().sort(Comparator.comparing(child -> child.entry().timestamp()));
            stack.addAll(node.children());
        }
    }

    private static SessionEntry withParent(SessionEntry entry, String parentId) {
        return switch (entry) {
            case SessionEntry.MessageEntry e -> new SessionEntry.MessageEntry(e.id(), parentId, e.timestamp(), e.message());
            case SessionEntry.ThinkingLevelChangeEntry e ->
                    new SessionEntry.ThinkingLevelChangeEntry(e.id(), parentId, e.timestamp(), e.thinkingLevel());
            case SessionEntry.ModelChangeEntry e ->
                    new SessionEntry.ModelChangeEntry(e.id(), parentId, e.timestamp(), e.provider(), e.modelId());
            case SessionEntry.ActiveToolsChangeEntry e ->
                    new SessionEntry.ActiveToolsChangeEntry(e.id(), parentId, e.timestamp(), e.activeToolNames());
            case SessionEntry.CompactionEntry e -> new SessionEntry.CompactionEntry(e.id(), parentId, e.timestamp(),
                    e.summary(), e.firstKeptEntryId(), e.tokensBefore(), e.details(), e.fromHook());
            case SessionEntry.BranchSummaryEntry e -> new SessionEntry.BranchSummaryEntry(e.id(), parentId,
                    e.timestamp(), e.summary(), e.fromId(), e.details(), e.fromHook());
            case SessionEntry.CustomEntry e ->
                    new SessionEntry.CustomEntry(e.id(), parentId, e.timestamp(), e.customType(), e.data());
            case SessionEntry.CustomMessageEntry e -> new SessionEntry.CustomMessageEntry(e.id(), parentId,
                    e.timestamp(), e.customType(), e.content(), e.display(), e.details());
            case SessionEntry.LabelEntry e ->
                    new SessionEntry.LabelEntry(e.id(), parentId, e.timestamp(), e.targetId(), e.label());
            case SessionEntry.SessionInfoEntry e ->
                    new SessionEntry.SessionInfoEntry(e.id(), parentId, e.timestamp(), e.name());
            case SessionEntry.LeafEntry e -> new SessionEntry.LeafEntry(e.id(), parentId, e.timestamp(), e.targetId());
        };
    }

    private record ParsedSession(JsonNode headerNode, List<SessionEntry> entries) {
        JsonNode header() {
            return headerNode;
        }
    }
}
