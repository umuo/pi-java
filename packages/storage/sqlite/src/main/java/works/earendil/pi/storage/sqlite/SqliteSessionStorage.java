package works.earendil.pi.storage.sqlite;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.agent.session.SessionEntryCodec;
import works.earendil.pi.agent.session.SessionStorage;
import works.earendil.pi.agent.session.UuidV7;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;

public final class SqliteSessionStorage implements SessionStorage, AutoCloseable {
    private static final int SCHEMA_VERSION = 1;

    private final Path databasePath;
    private final String sessionId;
    private final Connection connection;

    private SqliteSessionStorage(Path databasePath, String sessionId, Connection connection) {
        this.databasePath = databasePath;
        this.sessionId = sessionId;
        this.connection = connection;
    }

    public record Metadata(String id, Instant createdAt, Path cwd, Path parentSessionPath, JsonNode customMetadata) {
    }

    public static SqliteSessionStorage create(Path databasePath, Path cwd, String sessionId,
                                              Path parentSessionPath, JsonNode customMetadata) throws IOException {
        if (databasePath == null || cwd == null || sessionId == null || sessionId.isBlank()) {
            throw new IllegalArgumentException("Database path, cwd, and session id are required");
        }
        Connection connection = connect(databasePath);
        try {
            migrate(connection);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO sessions(id, created_at, cwd, parent_session, metadata, leaf_id, name)
                    VALUES (?, ?, ?, ?, ?, NULL, NULL)
                    """)) {
                statement.setString(1, sessionId);
                statement.setString(2, Instant.now().toString());
                statement.setString(3, cwd.toAbsolutePath().normalize().toString());
                statement.setString(4, parentSessionPath == null ? null : parentSessionPath.toString());
                statement.setString(5, customMetadata == null ? null : JsonCodec.stringify(customMetadata));
                statement.executeUpdate();
            }
            return new SqliteSessionStorage(databasePath.toAbsolutePath().normalize(), sessionId, connection);
        } catch (SQLException | RuntimeException error) {
            closeQuietly(connection);
            throw io("Unable to create SQLite session " + sessionId, error);
        }
    }

    public static SqliteSessionStorage create(Path databasePath, Path cwd, String sessionId) throws IOException {
        return create(databasePath, cwd, sessionId, null, null);
    }

    public static SqliteSessionStorage open(Path databasePath, String sessionId) throws IOException {
        Connection connection = connect(databasePath);
        try {
            migrate(connection);
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT 1 FROM sessions WHERE id = ?")) {
                statement.setString(1, sessionId);
                if (!statement.executeQuery().next()) {
                    throw new IllegalArgumentException("Session " + sessionId + " not found");
                }
            }
            return new SqliteSessionStorage(databasePath.toAbsolutePath().normalize(), sessionId, connection);
        } catch (SQLException | RuntimeException error) {
            closeQuietly(connection);
            if (error instanceof IllegalArgumentException illegalArgument) {
                throw illegalArgument;
            }
            throw io("Unable to open SQLite session " + sessionId, error);
        }
    }

    public Path databasePath() {
        return databasePath;
    }

    public String sessionId() {
        return sessionId;
    }

    public synchronized Metadata metadata() throws IOException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT created_at, cwd, parent_session, metadata FROM sessions WHERE id = ?
                """)) {
            statement.setString(1, sessionId);
            ResultSet result = statement.executeQuery();
            if (!result.next()) {
                throw new IllegalStateException("Session " + sessionId + " not found");
            }
            String parent = result.getString("parent_session");
            String metadata = result.getString("metadata");
            return new Metadata(sessionId, Instant.parse(result.getString("created_at")),
                    Path.of(result.getString("cwd")), parent == null ? null : Path.of(parent),
                    metadata == null ? null : JsonCodec.parse(metadata));
        } catch (SQLException error) {
            throw io("Unable to read SQLite session metadata", error);
        }
    }

    @Override
    public synchronized String createEntryId() {
        for (int i = 0; i < 100; i++) {
            String uuid = UuidV7.create();
            String id = uuid.substring(uuid.length() - 8);
            if (entry(id).isEmpty()) {
                return id;
            }
        }
        return UuidV7.create();
    }

    @Override
    public synchronized Optional<String> leafId() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT leaf_id FROM sessions WHERE id = ?")) {
            statement.setString(1, sessionId);
            ResultSet result = statement.executeQuery();
            return result.next() ? Optional.ofNullable(result.getString(1)) : Optional.empty();
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to read SQLite session leaf", error);
        }
    }

    @Override
    public synchronized void setLeafId(String id) throws IOException {
        if (id != null && entry(id).isEmpty()) {
            throw new IllegalArgumentException("Entry " + id + " not found");
        }
        append(new SessionEntry.LeafEntry(createEntryId(), leafId().orElse(null), Instant.now(), id));
    }

    @Override
    public synchronized Optional<SessionEntry> entry(String id) {
        if (id == null) {
            return Optional.empty();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT payload, sequence FROM entries WHERE session_id = ? AND id = ?")) {
            statement.setString(1, sessionId);
            statement.setString(2, id);
            ResultSet result = statement.executeQuery();
            return result.next()
                    ? Optional.of(SessionEntryCodec.decode(result.getString("payload"), databasePath.toString(),
                    result.getInt("sequence")))
                    : Optional.empty();
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to read SQLite session entry", error);
        }
    }

    @Override
    public synchronized List<SessionEntry> entries() {
        return queryEntries("SELECT payload, sequence FROM entries WHERE session_id = ? ORDER BY sequence", null, 0);
    }

    @Override
    public synchronized List<SessionEntry> entriesAfter(String cursor, int limit) {
        if (cursor == null) {
            List<SessionEntry> all = entries();
            return limit <= 0 || all.size() <= limit ? all : List.copyOf(all.subList(0, limit));
        }
        return queryEntries("""
                SELECT payload, sequence FROM entries
                WHERE session_id = ?
                  AND sequence > COALESCE((SELECT sequence FROM entries WHERE session_id = ? AND id = ?), 0)
                ORDER BY sequence
                """, cursor, limit);
    }

    @Override
    public synchronized List<SessionEntry> pathToRoot(String leafId) {
        if (leafId == null) {
            return List.of();
        }
        LinkedList<SessionEntry> path = new LinkedList<>();
        String current = leafId;
        while (current != null) {
            Optional<SessionEntry> stored = entry(current);
            if (stored.isEmpty()) {
                throw new IllegalArgumentException("Entry " + current + " not found");
            }
            SessionEntry value = stored.get();
            path.addFirst(value);
            current = value.parentId();
        }
        return List.copyOf(path);
    }

    @Override
    public synchronized void append(SessionEntry entry) throws IOException {
        try {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO entries(session_id, id, parent_id, timestamp, type, payload)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, sessionId);
                statement.setString(2, entry.id());
                statement.setString(3, entry.parentId());
                statement.setString(4, entry.timestamp().toString());
                statement.setString(5, entry.type());
                statement.setString(6, SessionEntryCodec.encode(entry));
                statement.executeUpdate();
            }
            String nextLeaf = entry instanceof SessionEntry.LeafEntry leaf ? leaf.targetId() : entry.id();
            try (PreparedStatement statement = connection.prepareStatement(
                    "UPDATE sessions SET leaf_id = ?, name = COALESCE(?, name) WHERE id = ?")) {
                statement.setString(1, nextLeaf);
                statement.setString(2, entry instanceof SessionEntry.SessionInfoEntry info ? info.name() : null);
                statement.setString(3, sessionId);
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException error) {
            rollbackQuietly();
            throw io("Unable to append SQLite session entry", error);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
    }

    @Override
    public synchronized Optional<String> sessionName() {
        try (PreparedStatement statement = connection.prepareStatement("SELECT name FROM sessions WHERE id = ?")) {
            statement.setString(1, sessionId);
            ResultSet result = statement.executeQuery();
            String name = result.next() ? result.getString(1) : null;
            return name == null || name.isBlank() ? Optional.empty() : Optional.of(name.trim());
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to read SQLite session name", error);
        }
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            connection.close();
        } catch (SQLException error) {
            throw io("Unable to close SQLite session storage", error);
        }
    }

    private List<SessionEntry> queryEntries(String sql, String cursor, int limit) {
        String effectiveSql = limit > 0 ? sql + " LIMIT " + limit : sql;
        try (PreparedStatement statement = connection.prepareStatement(effectiveSql)) {
            statement.setString(1, sessionId);
            if (cursor != null) {
                statement.setString(2, sessionId);
                statement.setString(3, cursor);
            }
            ResultSet result = statement.executeQuery();
            java.util.ArrayList<SessionEntry> entries = new java.util.ArrayList<>();
            while (result.next()) {
                entries.add(SessionEntryCodec.decode(result.getString("payload"), databasePath.toString(),
                        result.getInt("sequence")));
            }
            return List.copyOf(entries);
        } catch (SQLException error) {
            throw new IllegalStateException("Unable to query SQLite session entries", error);
        }
    }

    private static Connection connect(Path path) throws IOException {
        try {
            Path absolute = path.toAbsolutePath().normalize();
            if (absolute.getParent() != null) {
                Files.createDirectories(absolute.getParent());
            }
            Connection connection = DriverManager.getConnection("jdbc:sqlite:" + absolute);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA journal_mode = WAL");
                statement.execute("PRAGMA busy_timeout = 5000");
            }
            return connection;
        } catch (SQLException error) {
            throw io("Unable to connect to SQLite database " + path, error);
        }
    }

    private static void migrate(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        version INTEGER PRIMARY KEY,
                        applied_at TEXT NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS sessions (
                        id TEXT PRIMARY KEY,
                        created_at TEXT NOT NULL,
                        cwd TEXT NOT NULL,
                        parent_session TEXT,
                        metadata TEXT,
                        leaf_id TEXT,
                        name TEXT
                    )
                    """);
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS entries (
                        sequence INTEGER PRIMARY KEY AUTOINCREMENT,
                        session_id TEXT NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
                        id TEXT NOT NULL,
                        parent_id TEXT,
                        timestamp TEXT NOT NULL,
                        type TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        UNIQUE(session_id, id)
                    )
                    """);
            statement.execute("CREATE INDEX IF NOT EXISTS idx_entries_session_sequence "
                    + "ON entries(session_id, sequence)");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO schema_migrations(version, applied_at) VALUES (?, ?)")) {
            statement.setInt(1, SCHEMA_VERSION);
            statement.setString(2, Instant.now().toString());
            statement.executeUpdate();
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void closeQuietly(Connection connection) {
        try {
            connection.close();
        } catch (SQLException ignored) {
        }
    }

    private static IOException io(String message, Exception cause) {
        return new IOException(message, cause);
    }
}
