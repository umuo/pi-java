package works.earendil.pi.codingagent.core;

import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.codingagent.tools.PathUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.function.Consumer;

public final class AgentSessionRuntime {
    private final RuntimeFactory createRuntime;
    private Consumer<AgentSession> rebindSession;
    private Runnable beforeSessionInvalidate;
    private AgentSession session;
    private AgentSessionServices services;
    private List<AgentSessionServices.Diagnostic> diagnostics;
    private String modelFallbackMessage;

    public AgentSessionRuntime(AgentSession session, AgentSessionServices services, RuntimeFactory createRuntime,
                               List<AgentSessionServices.Diagnostic> diagnostics, String modelFallbackMessage) {
        this.session = session;
        this.services = services;
        this.createRuntime = createRuntime;
        this.diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        this.modelFallbackMessage = modelFallbackMessage;
    }

    @FunctionalInterface
    public interface RuntimeFactory {
        CreateRuntimeResult create(CreateRuntimeOptions options) throws Exception;
    }

    public record CreateRuntimeOptions(Path cwd, Path agentDir, SessionManager sessionManager, String reason) {
    }

    public record CreateRuntimeResult(AgentSession session, AgentSessionServices services,
                                      List<AgentSessionServices.Diagnostic> diagnostics,
                                      String modelFallbackMessage) {
    }

    public static final class SessionImportFileNotFoundException extends RuntimeException {
        private final Path filePath;

        public SessionImportFileNotFoundException(Path filePath) {
            super("File not found: " + filePath);
            this.filePath = filePath;
        }

        public Path filePath() {
            return filePath;
        }
    }

    public static AgentSessionRuntime create(RuntimeFactory factory, CreateRuntimeOptions options) throws Exception {
        SessionCwd.assertSessionCwdExists(new SessionSource(options.sessionManager()), options.cwd());
        CreateRuntimeResult result = factory.create(options);
        return new AgentSessionRuntime(result.session(), result.services(), factory,
                result.diagnostics(), result.modelFallbackMessage());
    }

    public ReplacementResult switchSession(Path sessionPath, Path cwdOverride) throws Exception {
        Path previousSessionFile = session.sessionFile().orElse(null);
        SessionManager nextManager = SessionManager.open(sessionPath, null, cwdOverride);
        SessionCwd.assertSessionCwdExists(new SessionSource(nextManager), services.cwd());
        teardown();
        apply(createRuntime.create(new CreateRuntimeOptions(nextManager.cwd(), services.agentDir(), nextManager, "resume")));
        finishReplacement();
        return new ReplacementResult(false, previousSessionFile, session.sessionFile().orElse(null), null);
    }

    public ReplacementResult newSession(Path parentSession) throws Exception {
        Path previousSessionFile = session.sessionFile().orElse(null);
        SessionManager nextManager = session.sessionManager().isPersisted()
                ? SessionManager.create(services.cwd(), session.sessionManager().sessionDir(),
                new SessionManager.NewSessionOptions(null, parentSession))
                : SessionManager.inMemory(services.cwd(), new SessionManager.NewSessionOptions(null, parentSession));
        teardown();
        apply(createRuntime.create(new CreateRuntimeOptions(services.cwd(), services.agentDir(), nextManager, "new")));
        finishReplacement();
        return new ReplacementResult(false, previousSessionFile, session.sessionFile().orElse(null), null);
    }

    public ReplacementResult fork(String entryId, ForkPosition position) throws Exception {
        Path previousSessionFile = session.sessionFile().orElse(null);
        SessionManager current = session.sessionManager();
        String targetLeafId = entryId;
        String selectedText = null;
        if (position == ForkPosition.BEFORE) {
            var entry = current.entry(entryId).orElseThrow(() -> new IllegalArgumentException("Invalid entry ID for forking"));
            if (!(entry instanceof works.earendil.pi.agent.session.SessionEntry.MessageEntry messageEntry)
                    || !"user".equals(messageEntry.message().path("role").asText())) {
                throw new IllegalArgumentException("Invalid entry ID for forking");
            }
            targetLeafId = entry.parentId();
            selectedText = extractUserMessageText(messageEntry.message().get("content"));
        }
        SessionManager nextManager;
        if (targetLeafId == null) {
            nextManager = current.isPersisted()
                    ? SessionManager.create(services.cwd(), current.sessionDir(),
                    new SessionManager.NewSessionOptions(null, current.sessionFile().orElse(null)))
                    : SessionManager.inMemory(services.cwd(), new SessionManager.NewSessionOptions(null,
                    current.sessionFile().orElse(null)));
        } else if (current.isPersisted()) {
            Path forkedPath = current.createBranchedSession(targetLeafId);
            nextManager = SessionManager.open(forkedPath, current.sessionDir(), services.cwd());
        } else {
            nextManager = current;
            nextManager.branchFrom(targetLeafId);
        }
        teardown();
        apply(createRuntime.create(new CreateRuntimeOptions(nextManager.cwd(), services.agentDir(), nextManager, "fork")));
        finishReplacement();
        return new ReplacementResult(false, previousSessionFile, session.sessionFile().orElse(null), selectedText);
    }

    public ReplacementResult importFromJsonl(Path inputPath, Path cwdOverride) throws Exception {
        Path resolvedPath = PathUtils.resolvePath(inputPath.toString());
        if (!Files.exists(resolvedPath)) {
            throw new SessionImportFileNotFoundException(resolvedPath);
        }
        Files.createDirectories(session.sessionManager().sessionDir());
        Path destination = session.sessionManager().sessionDir().resolve(resolvedPath.getFileName());
        if (!destination.toAbsolutePath().normalize().equals(resolvedPath)) {
            Files.copy(resolvedPath, destination, StandardCopyOption.REPLACE_EXISTING);
        }
        return switchSession(destination, cwdOverride);
    }

    public ReplacementResult reloadCurrent(String reason) throws Exception {
        Path currentSessionFile = session.sessionFile().orElse(null);
        SessionManager currentManager = session.sessionManager();
        teardown();
        apply(createRuntime.create(new CreateRuntimeOptions(currentManager.cwd(), services.agentDir(), currentManager,
                reason == null || reason.isBlank() ? "reload" : reason)));
        finishReplacement();
        return new ReplacementResult(false, currentSessionFile, session.sessionFile().orElse(null), null);
    }

    public void dispose() {
        teardown();
    }

    public void setRebindSession(Consumer<AgentSession> rebindSession) {
        this.rebindSession = rebindSession;
    }

    public void setBeforeSessionInvalidate(Runnable beforeSessionInvalidate) {
        this.beforeSessionInvalidate = beforeSessionInvalidate;
    }

    public AgentSession session() {
        return session;
    }

    public AgentSessionServices services() {
        return services;
    }

    public List<AgentSessionServices.Diagnostic> diagnostics() {
        return diagnostics;
    }

    public String modelFallbackMessage() {
        return modelFallbackMessage;
    }

    public enum ForkPosition {
        BEFORE,
        AT
    }

    public record ReplacementResult(boolean cancelled, Path previousSessionFile, Path currentSessionFile,
                                    String selectedText) {
    }

    private void teardown() {
        if (beforeSessionInvalidate != null) {
            beforeSessionInvalidate.run();
        }
        session.dispose();
    }

    private void apply(CreateRuntimeResult result) {
        session = result.session();
        services = result.services();
        diagnostics = result.diagnostics() == null ? List.of() : List.copyOf(result.diagnostics());
        modelFallbackMessage = result.modelFallbackMessage();
    }

    private void finishReplacement() {
        if (rebindSession != null) {
            rebindSession.accept(session);
        }
    }

    private static String extractUserMessageText(com.fasterxml.jackson.databind.JsonNode content) {
        if (content == null || content.isNull()) {
            return null;
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (com.fasterxml.jackson.databind.JsonNode part : content) {
                if (part.isTextual()) {
                    text.append(part.asText());
                } else if (part.has("text")) {
                    text.append(part.path("text").asText());
                }
            }
            return text.toString();
        }
        return content.asText();
    }

    private record SessionSource(SessionManager manager) implements SessionCwd.SessionCwdSource {
        @Override
        public Path cwd() {
            return manager.cwd();
        }

        @Override
        public java.util.Optional<Path> sessionFile() {
            return manager.sessionFile();
        }
    }
}
