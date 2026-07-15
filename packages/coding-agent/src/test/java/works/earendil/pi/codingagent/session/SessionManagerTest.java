package works.earendil.pi.codingagent.session;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.agent.session.SessionEntryCodec;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void encodesDefaultSessionDirectoryLikeTypeScript() {
        Path cwd = Path.of("/Users/example/project");
        Path agentDir = tempDir.resolve("agent");

        Path sessionDir = SessionPaths.defaultSessionDirPath(cwd, agentDir);

        assertThat(sessionDir).isEqualTo(agentDir.toAbsolutePath().normalize()
                .resolve("sessions")
                .resolve("--Users-example-project--"));
    }

    @Test
    void validatesSessionIds() {
        SessionManager.assertValidSessionId("abc.DEF-123_ok");

        assertThatThrownBy(() -> SessionManager.assertValidSessionId("-bad"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SessionManager.assertValidSessionId("bad/also"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customMessageEntriesPersistOptionalSource() {
        Instant timestamp = Instant.parse("2026-07-15T00:00:00Z");
        SessionEntry.CustomMessageEntry entry = new SessionEntry.CustomMessageEntry("c1", null, timestamp,
                "extension.context", JsonCodec.mapper().getNodeFactory().textNode("hello"),
                true, null, " extension ");

        String encoded = SessionEntryCodec.encode(entry);
        SessionEntry.CustomMessageEntry decoded = (SessionEntry.CustomMessageEntry)
                SessionEntryCodec.decode(encoded, "memory", 1);
        SessionEntry.CustomMessageEntry legacyDecoded = (SessionEntry.CustomMessageEntry)
                SessionEntryCodec.decode("""
                        {"type":"custom_message","id":"c2","parentId":null,"timestamp":"2026-07-15T00:00:01Z","customType":"legacy","content":"old","display":true,"details":null}
                        """, "memory", 2);

        assertThat(JsonCodec.parse(encoded).path("source").asText()).isEqualTo("extension");
        assertThat(decoded.source()).isEqualTo("extension");
        assertThat(legacyDecoded.source()).isNull();
    }

    @Test
    void createsReopensNamesLabelsAndListsSessions() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path sessionDir = tempDir.resolve("sessions");
        Files.createDirectories(cwd);
        SessionManager manager = SessionManager.create(cwd, sessionDir,
                new SessionManager.NewSessionOptions("session-1", null));

        String messageId = manager.appendMessage(userMessage("hello world"));
        manager.appendSessionInfo("  Project\nSession  ");
        manager.appendLabelChange(messageId, "checkpoint");
        Path file = manager.sessionFile().orElseThrow();

        SessionManager reopened = SessionManager.open(file);
        List<SessionFileInfo> infos = SessionManager.list(cwd, sessionDir, null);

        assertThat(reopened.sessionId()).isEqualTo("session-1");
        assertThat(reopened.sessionName()).contains("Project Session");
        assertThat(reopened.label(messageId)).contains("checkpoint");
        assertThat(reopened.branch().stream().map(SessionEntry::id)).contains(messageId);
        assertThat(infos).hasSize(1);
        assertThat(infos.getFirst().name()).isEqualTo("Project Session");
        assertThat(infos.getFirst().firstMessage()).isEqualTo("hello world");
        assertThat(infos.getFirst().allMessagesText()).contains("hello world");
    }

    @Test
    void skipsMalformedJsonlLinesWhenBuildingSessionInfo() throws Exception {
        Path cwd = tempDir.resolve("project");
        Files.createDirectories(cwd);
        Path file = tempDir.resolve("session.jsonl");
        ObjectNode header = JsonCodec.mapper().createObjectNode();
        header.put("type", "session");
        header.put("version", SessionManager.CURRENT_SESSION_VERSION);
        header.put("id", "session-2");
        header.put("timestamp", Instant.now().toString());
        header.put("cwd", cwd.toString());

        SessionEntry.MessageEntry message = new SessionEntry.MessageEntry("m1", null, Instant.now(),
                userMessage("kept"));
        Files.write(file, List.of(
                JsonCodec.stringify(header),
                "{not-json",
                SessionEntryCodec.encode(message)),
                StandardCharsets.UTF_8);

        assertThat(SessionManager.buildSessionInfo(file)).hasValueSatisfying(info -> {
            assertThat(info.id()).isEqualTo("session-2");
            assertThat(info.messageCount()).isEqualTo(1);
            assertThat(info.firstMessage()).isEqualTo("kept");
        });
    }

    @Test
    void migratesOlderSessionFilesOnOpen() throws Exception {
        Path cwd = tempDir.resolve("project");
        Files.createDirectories(cwd);
        Path file = tempDir.resolve("legacy.jsonl");
        ObjectNode header = JsonCodec.mapper().createObjectNode();
        header.put("type", "session");
        header.put("version", 2);
        header.put("id", "legacy");
        header.put("timestamp", Instant.now().toString());
        header.put("cwd", cwd.toString());
        ObjectNode message = userMessage("from hook");
        message.put("role", "hookMessage");
        SessionEntry.MessageEntry entry = new SessionEntry.MessageEntry("m1", null, Instant.now(), message);
        Files.write(file, List.of(JsonCodec.stringify(header), SessionEntryCodec.encode(entry)), StandardCharsets.UTF_8);

        SessionManager manager = SessionManager.open(file);

        assertThat(manager.sessionId()).isEqualTo("legacy");
        assertThat(((SessionEntry.MessageEntry) manager.entries().getFirst()).message().path("role").asText())
                .isEqualTo("custom");
        assertThat(JsonCodec.parse(Files.readAllLines(file).getFirst()).path("version").asInt())
                .isEqualTo(SessionManager.CURRENT_SESSION_VERSION);
    }

    @Test
    void continuesMostRecentSessionAndForksHistory() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path otherCwd = tempDir.resolve("other");
        Path sessionDir = tempDir.resolve("sessions");
        Files.createDirectories(cwd);
        Files.createDirectories(otherCwd);

        SessionManager oldSession = SessionManager.create(cwd, sessionDir,
                new SessionManager.NewSessionOptions("old", null));
        oldSession.appendMessage(userMessage("old"));
        Thread.sleep(5);
        SessionManager latest = SessionManager.create(cwd, sessionDir,
                new SessionManager.NewSessionOptions("latest", null));
        latest.appendMessage(userMessage("latest"));

        SessionManager continued = SessionManager.continueRecent(cwd, sessionDir);
        SessionManager forked = SessionManager.forkFrom(latest.sessionFile().orElseThrow(), otherCwd, sessionDir,
                new SessionManager.NewSessionOptions("forked", null));

        assertThat(continued.sessionId()).isEqualTo("latest");
        assertThat(forked.sessionId()).isEqualTo("forked");
        assertThat(SessionManager.buildSessionInfo(forked.sessionFile().orElseThrow()))
                .hasValueSatisfying(info -> {
                    assertThat(info.cwd()).isEqualTo(otherCwd.toAbsolutePath().normalize());
                    assertThat(info.parentSessionPath()).isEqualTo(latest.sessionFile().orElseThrow());
                    assertThat(info.allMessagesText()).contains("latest");
                });
    }

    private static ObjectNode userMessage(String text) {
        ObjectNode message = JsonCodec.mapper().createObjectNode();
        message.put("role", "user");
        message.put("content", text);
        return message;
    }
}
