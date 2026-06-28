package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.provider.Provider;
import works.earendil.pi.ai.provider.ProviderRegistry;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentSessionRuntimeTest {
    @TempDir
    Path tempDir;

    @Test
    void createsServicesAndSessionThatPersistsPromptMessages() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.writeString(cwd.resolve("AGENTS.md"), "Keep project instructions in context.");
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions"));
        AtomicReference<Context> capturedContext = new AtomicReference<>();

        AgentSessionServices.CreateSessionResult result = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        List.of("read"), null, null, null, assistant("hello back", capturedContext)));
        List<AgentSession.AgentSessionEvent> events = new ArrayList<>();
        result.session().subscribe(events::add);
        result.session().prompt("hello");

        assertThat(result.session().model().provider()).isEqualTo("openai");
        assertThat(result.session().tools()).extracting(AgentTool::name).containsExactly("read");
        assertThat(result.session().stats().userMessages()).isEqualTo(1);
        assertThat(result.session().stats().assistantMessages()).isEqualTo(1);
        assertThat(sessionManager.entries().stream().filter(e -> e.type().equals("message"))).hasSize(2);
        assertThat(events).anyMatch(event -> event.type().equals("agent_end"));
        assertThat(capturedContext.get().systemPrompt()).contains("Available tools:\n- read: Read file contents");
        assertThat(capturedContext.get().systemPrompt()).contains("Keep project instructions in context.");
    }

    @Test
    void cyclesScopedModelsAndPersistsThinkingLevel() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir);
        Model openai = services.modelRegistry().find("openai", "gpt-5.5").orElseThrow();
        Model anthropic = services.modelRegistry().find("anthropic", "claude-sonnet-4").orElseThrow();
        SessionManager sessionManager = SessionManager.inMemory(cwd);

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, openai, ThinkingLevel.MEDIUM,
                        List.of(new ModelResolver.ScopedModel(openai, ThinkingLevel.LOW),
                                new ModelResolver.ScopedModel(anthropic, ThinkingLevel.HIGH)),
                        null, null, null, null, assistant("ok"))).session();

        AgentSession.ModelCycleResult cycle = session.cycleModel().orElseThrow();

        assertThat(cycle.model()).isEqualTo(anthropic);
        assertThat(cycle.thinkingLevel()).isEqualTo(ThinkingLevel.HIGH);
        assertThat(sessionManager.entries().stream().map(e -> e.type())).contains("model_change", "thinking_level_change");
    }

    @Test
    void runtimeSwitchesNewImportsAndForksSessions() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path sessionDir = tempDir.resolve("sessions");
        Files.createDirectories(cwd);
        RuntimeHarness harness = new RuntimeHarness(cwd, agentDir, sessionDir);
        SessionManager initialManager = SessionManager.create(cwd, sessionDir);
        AgentSessionRuntime runtime = AgentSessionRuntime.create(harness::create,
                new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, initialManager, "startup"));
        AtomicInteger rebinds = new AtomicInteger();
        runtime.setRebindSession(session -> rebinds.incrementAndGet());

        runtime.session().prompt("first");
        Path firstSessionFile = runtime.session().sessionFile().orElseThrow();
        String userEntryId = runtime.session().sessionManager().entries().stream()
                .filter(e -> e.type().equals("message"))
                .findFirst()
                .orElseThrow()
                .id();

        AgentSessionRuntime.ReplacementResult forked = runtime.fork(userEntryId, AgentSessionRuntime.ForkPosition.BEFORE);
        AgentSessionRuntime.ReplacementResult created = runtime.newSession(firstSessionFile);
        AgentSessionRuntime.ReplacementResult switched = runtime.switchSession(firstSessionFile, null);
        Path importedSource = tempDir.resolve("imported.jsonl");
        runtime.session().sessionManager().copySessionFile(importedSource);
        AgentSessionRuntime.ReplacementResult imported = runtime.importFromJsonl(importedSource, null);

        assertThat(forked.selectedText()).isEqualTo("first");
        assertThat(created.previousSessionFile()).isNotNull();
        assertThat(switched.currentSessionFile()).isEqualTo(firstSessionFile);
        assertThat(imported.currentSessionFile()).isEqualTo(sessionDir.resolve(importedSource.getFileName()));
        assertThat(rebinds.get()).isEqualTo(4);
        assertThat(harness.creations()).isGreaterThanOrEqualTo(5);
    }

    @Test
    void importReportsMissingFile() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path sessionDir = tempDir.resolve("sessions");
        Files.createDirectories(cwd);
        RuntimeHarness harness = new RuntimeHarness(cwd, agentDir, sessionDir);
        AgentSessionRuntime runtime = AgentSessionRuntime.create(harness::create,
                new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, SessionManager.create(cwd, sessionDir), "startup"));

        assertThatThrownBy(() -> runtime.importFromJsonl(tempDir.resolve("missing.jsonl"), null))
                .isInstanceOf(AgentSessionRuntime.SessionImportFileNotFoundException.class);
    }

    private AgentSessionServices services(Path cwd, Path agentDir) throws Exception {
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("key", null));
        authStorage.set("anthropic", new AuthStorage.ApiKeyCredential("key", null));
        return AgentSessionServices.create(new AgentSessionServices.CreateOptions(cwd, agentDir, authStorage,
                null, null, providers(), null, true));
    }

    private static works.earendil.pi.agent.core.AgentLoop.StreamFunction assistant(String text) {
        return (model, context, options) -> new Message.Assistant(List.of(new Content.Text(text)),
                model.provider(), model.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now());
    }

    private static works.earendil.pi.agent.core.AgentLoop.StreamFunction assistant(String text,
                                                                                   AtomicReference<Context> capturedContext) {
        return (model, context, options) -> {
            capturedContext.set(context);
            return new Message.Assistant(List.of(new Content.Text(text)),
                    model.provider(), model.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now());
        };
    }

    private static ProviderRegistry providers() {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new StaticProvider("openai", List.of(
                new Model("openai", "gpt-5.5", "GPT", "openai-chat", 1000, 100, true, false,
                        Map.of("baseUrl", "https://openai.example")))));
        registry.register(new StaticProvider("anthropic", List.of(
                new Model("anthropic", "claude-sonnet-4", "Claude", "anthropic", 1000, 100, true, false,
                        Map.of("baseUrl", "https://anthropic.example")))));
        return registry;
    }

    private record StaticProvider(String id, List<Model> models) implements Provider {
        @Override
        public AssistantMessageEventStream stream(Model model, Context context, StreamOptions options) {
            return null;
        }
    }

    private final class RuntimeHarness {
        private final Path cwd;
        private final Path agentDir;
        private int creations;

        private RuntimeHarness(Path cwd, Path agentDir, Path sessionDir) {
            this.cwd = cwd;
            this.agentDir = agentDir;
        }

        AgentSessionRuntime.CreateRuntimeResult create(AgentSessionRuntime.CreateRuntimeOptions options) throws Exception {
            creations++;
            AgentSessionServices services = services(options.cwd(), options.agentDir());
            AgentSessionServices.CreateSessionResult session = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(services, options.sessionManager(), null, null,
                            List.of(), null, null, null, null, assistant("runtime")));
            return new AgentSessionRuntime.CreateRuntimeResult(session.session(), services, services.diagnostics(),
                    session.modelFallbackMessage());
        }

        int creations() {
            return creations;
        }
    }
}
