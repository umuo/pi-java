package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Context;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Tool;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.ai.provider.Provider;
import works.earendil.pi.ai.provider.ProviderRegistry;
import works.earendil.pi.ai.provider.StreamOptions;
import works.earendil.pi.ai.stream.AssistantMessageEventStream;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.core.extensions.ExtensionPlugin;
import works.earendil.pi.codingagent.core.extensions.ExtensionRunner;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
        assertThat(result.session().stats().inputTokens()).isEqualTo(1);
        assertThat(result.session().stats().outputTokens()).isEqualTo(1);
        assertThat(result.session().stats().totalTokens()).isEqualTo(2);
        assertThat(sessionManager.entries().stream().filter(e -> e.type().equals("message"))).hasSize(2);
        assertThat(events).anyMatch(event -> event.type().equals("agent_end"));
        assertThat(capturedContext.get().systemPrompt()).contains("Available tools:\n- read: Read file contents");
        assertThat(capturedContext.get().systemPrompt()).contains("Keep project instructions in context.");
    }

    @Test
    void appliesProviderRetrySettingsToStreamOptions() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        SettingsManager settings = SettingsManager.inMemory(Map.of(
                "transport", "sse",
                "retry", Map.of(
                        "enabled", true,
                        "maxRetries", 6,
                        "baseDelayMs", 150,
                        "provider", Map.of(
                                "timeoutMs", 12_000,
                                "maxRetries", 4,
                                "maxRetryDelayMs", 2_000,
                                "maxConcurrentRequests", 3
                        ),
                        "providers", Map.of(
                                "openai", Map.of(
                                        "timeoutMs", 25_000,
                                        "maxRetries", 7,
                                        "baseDelayMs", 50,
                                        "maxRetryDelayMs", 1_000,
                                        "maxConcurrentRequests", 1
                                )
                        )
                )
        ));
        AgentSessionServices services = services(cwd, agentDir, settings);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        AtomicReference<StreamOptions> capturedOptions = new AtomicReference<>();

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        List.of("read"), null, null, null, assistant("ok", null, capturedOptions))).session();

        session.prompt("hello");

        assertThat(capturedOptions.get().transport()).isEqualTo(works.earendil.pi.ai.model.Transport.SSE);
        assertThat(capturedOptions.get().timeout()).isEqualTo(Duration.ofMillis(12_000));
        assertThat(capturedOptions.get().maxRetries()).isEqualTo(4);
        assertThat(capturedOptions.get().metadata()).containsEntry("retryInitialDelayMs", 150);
        assertThat(capturedOptions.get().metadata()).containsEntry("maxRetryDelayMs", 2_000);
        assertThat(capturedOptions.get().metadata()).containsEntry("maxConcurrentRequests", 3);
        assertThat(capturedOptions.get().metadata()).containsKey("providerRetryOverrides");
        Object providerOverrides = capturedOptions.get().metadata().get("providerRetryOverrides");
        assertThat(providerOverrides).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) providerOverrides).containsKey("openai")).isTrue();
    }

    @Test
    void includesExtensionRegisteredToolsInSessionContext() throws Exception {
        Path cwd = tempDir.resolve("project_extension_tools");
        Path agentDir = tempDir.resolve("agent_extension_tools");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        AtomicReference<Context> capturedContext = new AtomicReference<>();
        Tool extensionTool = new Tool("ext_echo", "Echo text from an extension",
                JsonCodec.parse("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}"),
                "Use ext_echo only when extension behavior is requested.");
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "demo-extension";
            }

            @Override
            public List<Tool> registerTools() {
                return List.of(extensionTool);
            }
        };
        List<AgentTool> extensionTools = new ExtensionRunner(List.of(plugin)).collectAgentTools();

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, extensionTools, assistant("ok", capturedContext))).session();

        session.prompt("hello");
        AgentTool registered = session.tools().stream()
                .filter(tool -> tool.name().equals("ext_echo"))
                .findFirst()
                .orElseThrow();
        AgentTool.AgentToolResult execution = registered.execute(Map.of("text", "hello"));

        assertThat(session.tools()).extracting(AgentTool::name).contains("ext_echo");
        assertThat(capturedContext.get().systemPrompt()).contains("- ext_echo: Echo text from an extension")
                .contains("Use ext_echo only when extension behavior is requested.");
        assertThat(execution.error()).isTrue();
        assertThat(((Content.Text) execution.content().getFirst()).text())
                .contains("Extension tool 'ext_echo' from 'demo-extension' is registered")
                .contains("does not provide a tool executor yet");
    }

    @Test
    void expandsSkillCommandsBeforePersistingUserPrompt() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("demo"));
        Files.writeString(agentDir.resolve("skills").resolve("demo").resolve("SKILL.md"),
                "---\nname: demo\ndescription: Demo skill\n---\nUse the demo skill.");
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"))).session();
        List<AgentSession.AgentSessionEvent> events = new ArrayList<>();
        session.subscribe(events::add);

        session.prompt("/skill:demo apply this");

        assertThat(userText(session.messages().getFirst()))
                .contains("<skill name=\"demo\"")
                .contains("References are relative to " + agentDir.resolve("skills").resolve("demo").toAbsolutePath().normalize())
                .contains("Use the demo skill.\n</skill>")
                .endsWith("apply this");
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(AgentSession.AgentSessionEvent.SkillCommand.class);
            AgentSession.AgentSessionEvent.SkillCommand skillCommand = (AgentSession.AgentSessionEvent.SkillCommand) event;
            assertThat(skillCommand.phase()).isEqualTo("start");
            assertThat(skillCommand.skillName()).isEqualTo("demo");
        });
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(AgentSession.AgentSessionEvent.SkillCommand.class);
            AgentSession.AgentSessionEvent.SkillCommand skillCommand = (AgentSession.AgentSessionEvent.SkillCommand) event;
            assertThat(skillCommand.phase()).isEqualTo("end");
            assertThat(skillCommand.skillName()).isEqualTo("demo");
        });
    }

    @Test
    void emitsSkillCommandErrorWhenLoadedSkillCannotBeRead() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path skillFile = agentDir.resolve("skills").resolve("demo").resolve("SKILL.md");
        Files.createDirectories(cwd);
        Files.createDirectories(skillFile.getParent());
        Files.writeString(skillFile, "---\nname: demo\ndescription: Demo skill\n---\nUse the demo skill.");
        AgentSessionServices services = services(cwd, agentDir);
        Files.delete(skillFile);
        SessionManager sessionManager = SessionManager.inMemory(cwd);

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"))).session();
        List<AgentSession.AgentSessionEvent> events = new ArrayList<>();
        session.subscribe(events::add);

        session.prompt("/skill:demo apply this");

        assertThat(userText(session.messages().getFirst())).isEqualTo("/skill:demo apply this");
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(AgentSession.AgentSessionEvent.SkillCommand.class);
            AgentSession.AgentSessionEvent.SkillCommand skillCommand = (AgentSession.AgentSessionEvent.SkillCommand) event;
            assertThat(skillCommand.phase()).isEqualTo("error");
            assertThat(skillCommand.skillName()).isEqualTo("demo");
            assertThat(skillCommand.message()).contains("Failed to read skill 'demo'");
        });
    }

    @Test
    void preservesSkillCommandsWhenDisabled() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("demo"));
        Files.writeString(agentDir.resolve("skills").resolve("demo").resolve("SKILL.md"),
                "---\nname: demo\ndescription: Demo skill\n---\nUse the demo skill.");
        AgentSessionServices services = services(cwd, agentDir, SettingsManager.inMemory(Map.of(
                "enableSkillCommands", false
        )));
        SessionManager sessionManager = SessionManager.inMemory(cwd);

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"))).session();

        session.prompt("/skill:demo apply this");

        assertThat(userText(session.messages().getFirst())).isEqualTo("/skill:demo apply this");
    }

    @Test
    void expandsManualOnlySkillsWhenExplicitlyInvoked() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("manual"));
        Files.writeString(agentDir.resolve("skills").resolve("manual").resolve("SKILL.md"),
                "---\nname: manual\ndescription: Manual skill\ndisable-model-invocation: true\n---\nManual instructions.");
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"))).session();

        session.prompt("/skill:manual use directly");

        assertThat(userText(session.messages().getFirst()))
                .contains("<skill name=\"manual\"")
                .contains("Manual instructions.\n</skill>")
                .endsWith("use directly");
    }

    @Test
    void emitsSkillTriggerDiagnosticsForMatchingHints() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("diagnose"));
        Files.writeString(agentDir.resolve("skills").resolve("diagnose").resolve("SKILL.md"), """
                ---
                name: diagnose
                description: Diagnose flaky tests
                trigger-terms:
                  - flaky
                trigger-patterns:
                  - "test.*timeout"
                trigger-globs:
                  - "**/*Test.java"
                ---
                Diagnose test failures.
                """);
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"))).session();
        List<AgentSession.AgentSessionEvent> events = new ArrayList<>();
        session.subscribe(events::add);

        session.prompt("Investigate flaky test timeout in src/FooTest.java");

        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(AgentSession.AgentSessionEvent.SkillTriggerDiagnostic.class);
            AgentSession.AgentSessionEvent.SkillTriggerDiagnostic diagnostic =
                    (AgentSession.AgentSessionEvent.SkillTriggerDiagnostic) event;
            assertThat(diagnostic.matches()).singleElement().satisfies(match -> {
                assertThat(match.skillName()).isEqualTo("diagnose");
                assertThat(match.modelVisible()).isTrue();
                assertThat(match.reasons()).contains("term:flaky", "pattern:test.*timeout", "glob:**/*Test.java");
            });
        });

        events.clear();
        session.prompt("/skill:diagnose flaky test timeout in src/FooTest.java");

        assertThat(events).anyMatch(event -> event instanceof AgentSession.AgentSessionEvent.SkillCommand);
        assertThat(events).noneMatch(event -> event instanceof AgentSession.AgentSessionEvent.SkillTriggerDiagnostic);
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
        return services(cwd, agentDir, null);
    }

    private AgentSessionServices services(Path cwd, Path agentDir, SettingsManager settingsManager) throws Exception {
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("key", null));
        authStorage.set("anthropic", new AuthStorage.ApiKeyCredential("key", null));
        return AgentSessionServices.create(new AgentSessionServices.CreateOptions(cwd, agentDir, authStorage,
                settingsManager, null, providers(), null, true));
    }

    private static String userText(AgentMessage message) {
        Message.User user = (Message.User) ((AgentMessage.Llm) message).message();
        return ((Content.Text) user.content().getFirst()).text();
    }

    private static works.earendil.pi.agent.core.AgentLoop.StreamFunction assistant(String text) {
        return (model, context, options) -> new Message.Assistant(List.of(new Content.Text(text)),
                model.provider(), model.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now());
    }

    private static works.earendil.pi.agent.core.AgentLoop.StreamFunction assistant(String text,
                                                                                   AtomicReference<Context> capturedContext) {
        return assistant(text, capturedContext, null);
    }

    private static works.earendil.pi.agent.core.AgentLoop.StreamFunction assistant(String text,
                                                                                   AtomicReference<Context> capturedContext,
                                                                                   AtomicReference<StreamOptions> capturedOptions) {
        return (model, context, options) -> {
            if (capturedContext != null) {
                capturedContext.set(context);
            }
            if (capturedOptions != null) {
                capturedOptions.set(options);
            }
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
