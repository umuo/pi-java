package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.service.AgentProcess;
import works.earendil.pi.orchestrator.service.AgentProcessLauncher;
import works.earendil.pi.orchestrator.service.OrchestratorSupervisor;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.StopReason;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.ai.model.Usage;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.AgentSessionServices;
import works.earendil.pi.codingagent.core.AuthStorage;
import works.earendil.pi.codingagent.core.SkillDiagnosticHistory;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CliEntryTest {

    @TempDir
    Path tempDir;

    @Test
    void testCliArgsParsing() {
        CliArgs args = new CliArgs();
        new CommandLine(args).parseArgs("--provider", "openai", "--model", "gpt-4o", "-p", "Hello world");

        assertThat(args.provider).isEqualTo("openai");
        assertThat(args.model).isEqualTo("gpt-4o");
        assertThat(args.print).isTrue();
        assertThat(args.messages).containsExactly("Hello world");
    }

    @Test
    void testListModelsFlag() {
        CliArgs args = new CliArgs();
        new CommandLine(args).parseArgs("--list-models");
        assertThat(args.listModels).isTrue();
    }

    @Test
    void testPrintModeRunnerExecution() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));

        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions"));
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("Output from print mode")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test"));

        CliArgs args = new CliArgs();
        args.print = true;
        args.messages.add("Test prompt");

        int exitCode = PrintModeRunner.run(runtime, args);
        assertThat(exitCode).isEqualTo(0);
    }

    @Test
    void printJsonEmitsSkillTriggerDiagnostics() throws Exception {
        Path cwd = tempDir.resolve("project_print_json");
        Path agentDir = tempDir.resolve("agent_print_json");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("diagnose"));
        Files.writeString(agentDir.resolve("skills").resolve("diagnose").resolve("SKILL.md"), """
                ---
                name: diagnose
                description: Diagnose flaky tests
                trigger-terms:
                  - flaky
                ---
                Diagnose.
                """);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_print_json"));
        Model model = services.modelRegistry().getAll().get(0);
        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("ok")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_print_json"));

        CliArgs args = new CliArgs();
        args.print = true;
        args.mode = "json";
        args.messages.add("Investigate flaky checkout tests");
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(PrintModeRunner.run(runtime, args)).isEqualTo(0);
        } finally {
            System.setOut(originalOut);
        }

        assertThat(outBuf.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("\"type\":\"skill_trigger_diagnostic\"")
                .contains("\"skill\":\"diagnose\"")
                .contains("\"modelVisible\":true")
                .contains("\"term:flaky\"");
    }

    @Test
    void testInteractiveModeRunnerExecution() throws Exception {
        Path cwd = tempDir.resolve("project_int");
        Path agentDir = tempDir.resolve("agent_int");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("demo"));
        Files.writeString(agentDir.resolve("skills").resolve("demo").resolve("SKILL.md"), """
                ---
                name: demo
                description: Demo skill
                trigger-terms:
                  - hello
                ---
                Use demo.
                """);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));

        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_int"));
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("""
                                    # Interactive answer
                                    ```java
                                    public class A {}
                                    ```
                                    """)),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_int"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream("/models refresh ollama\n/help\n/skill-diagnostics\n/orchestrator-status tail agent-1 nope\n/skill:missing now\n/teamwork-preview compact\n/grill-me checkout\n/grill-me status\n/grill-me answer conversion drops on payment\n/grill-me reset\nhello\n/skill-diagnostics\n/skill-diagnostics history\n/skill-diagnostics history skill=demo model=visible reason=hello\n/skill-diagnostics json skill=demo\n/skill-diagnostics sources limit=5\n/skill-diagnostics picker limit=5\n/orchestrator-status dashboard skill=demo reason=hello\n/skill-diagnostics clear\n/skill-diagnostics\n/exit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            int exitCode = InteractiveModeRunner.run(runtime, args);
            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("Models refreshed for provider: ollama").contains("Available models:")
                    .contains("Available commands:").contains("Goodbye!");
            assertThat(output).contains("/orchestrator-status dashboard [instanceId] [events] [filters] Show instances, stderr, RPC events, and skill diagnostics")
                    .contains("/orchestrator-status tail [instanceId] [lines] Show recent stderr log lines")
                    .contains("/orchestrator-status tail --follow [instanceId] Subscribe to stderr log lines")
                    .contains("/orchestrator-status events [instanceId|stop] Subscribe to live RPC events")
                    .contains("/grill-me answer <text> Record an interview answer and continue")
                    .contains("/grill-me status|reset Show or clear the active interview")
                    .contains("/skill-diagnostics [history|json|sources|picker|clear] [branch=<entryId>] [filters] Show, export, or clear skill trigger diagnostics")
                    .contains("Orchestrator status\nerror: tail lines must be a positive integer: nope");
            assertThat(output).contains("Loaded skills:")
                    .contains("/skill:demo Demo skill")
                    .contains("Skill not found: missing");
            assertThat(output).contains("Teamwork preview")
                    .contains("Planned sub-agents:")
                    .contains("Starting /grill-me interview...")
                    .contains("/grill-me interview\nstatus: active")
                    .contains("phase: discovery")
                    .contains("assistant questions: 1")
                    .contains("q1. # Interactive answer")
                    .contains("Continuing /grill-me interview...")
                    .contains("status: reset")
                    .contains("topic: checkout");
            assertThat(output).contains("Skill trigger diagnostic")
                    .contains("skill: demo")
                    .contains("model: visible")
                    .contains("term:hello")
                    .contains("SKILL.md");
            assertThat(output).contains("Skill trigger diagnostics\nstatus: no recent matches")
                    .contains("Skill trigger diagnostics\nstatus: latest")
                    .contains("Skill trigger diagnostics\nstatus: history")
                    .contains("filter: skill=demo model=visible reason=hello")
                    .contains("entries: 1")
                    .contains("\"filter\":{\"skill\":\"demo\"")
                    .contains("\"entries\":[{\"capturedAt\"")
                    .contains("\"current\":{\"sessionId\"")
                    .contains("\"branchTree\"")
                    .contains("Skill diagnostic source picker")
                    .contains("top skill: demo=1")
                    .contains("Orchestrator dashboard")
                    .contains("skill diagnostics\nfilter: skill=demo reason=hello")
                    .contains("entries: 1 | matches: 1 | visible: 1 | manual-only: 0")
                    .contains("top skills: demo=1")
                    .contains("top reasons: term:hello=1")
                    .contains("Skill trigger diagnostics\nstatus: cleared");
            assertThat(output).contains("status | branch: none | model: ")
                    .contains(" | msgs: u0/a0/t0 | tokens: 0 | providers: ")
                    .contains("turn | elapsed: ")
                    .contains(" | msgs: u1/a1/t0 | tokens: 2")
                    .contains(" | timings: agent=")
                    .contains(",total=");
            assertThat(output).contains("# Interactive answer")
                    .contains("\u001B[");
            assertThat(works.earendil.pi.common.text.Ansi.strip(output)).contains("public class A");
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void truncatesInteractiveStatusLinesToTerminalWidth() {
        String fitted = InteractiveModeRunner.fitLineToWidth("status | branch: feature/very-long-name | model: openai/gpt", 32);

        assertThat(fitted).endsWith("...");
        assertThat(EastAsianWidth.visibleWidth(fitted)).isLessThanOrEqualTo(32);

        String cjk = InteractiveModeRunner.fitLineToWidth("status | branch: 功能分支 | model: openai/gpt", 28);
        assertThat(cjk).endsWith("...");
        assertThat(EastAsianWidth.visibleWidth(cjk)).isLessThanOrEqualTo(28);
    }

    @Test
    void rendersOrchestratorStatusArgumentErrors() {
        assertThat(InteractiveModeRunner.renderOrchestratorStatus("events"))
                .contains("Orchestrator events")
                .contains("error: live event subscription is only available in interactive mode");
        assertThat(InteractiveModeRunner.renderOrchestratorStatus("unknown"))
                .contains("Orchestrator status")
                .contains("error: unknown argument: unknown")
                .contains("usage: /orchestrator-status [dashboard [instanceId] [events] [filters] | tail [instanceId] [lines] | tail --follow [instanceId] | tail --stop | events [instanceId|stop]]");
        assertThat(InteractiveModeRunner.renderOrchestratorStatus("dashboard agent-1 nope"))
                .contains("Orchestrator status")
                .contains("error: dashboard events must be a positive integer: nope");
        assertThat(InteractiveModeRunner.renderOrchestratorStatus("tail --follow agent-1"))
                .contains("Orchestrator log follow")
                .contains("error: live log tail is only available in interactive mode");
        assertThat(InteractiveModeRunner.renderOrchestratorStatus("tail agent-1 nope"))
                .contains("Orchestrator status")
                .contains("error: tail lines must be a positive integer: nope");
    }

    @Test
    void orchestratorEventTailerPrintsLiveRpcEvents() throws Exception {
        OrchestratorStorage storage = new OrchestratorStorage(new OrchestratorConfig(
                Map.of("PI_ORCHESTRATOR_DIR", tempDir.resolve("orchestrator_events").toString())));
        FakeRpcLauncher launcher = new FakeRpcLauncher();
        OrchestratorSupervisor supervisor = new OrchestratorSupervisor(storage, launcher);
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        java.io.PrintStream out = new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8);

        try (InteractiveModeRunner.OrchestratorEventTailer tailer =
                     new InteractiveModeRunner.OrchestratorEventTailer(out, () -> supervisor)) {
            assertThat(tailer.start("")).contains("subscribed: all instances");
            var instance = supervisor.spawnInstance("/workspace", "agent");
            FakeRpcProcess process = launcher.processes.getFirst();
            process.responses.add("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\"}}");
            process.responses.add("{\"jsonrpc\":\"2.0\",\"id\":99,\"result\":{\"status\":\"ok\"}}");

            supervisor.sendRpcExchange(instance.id(), "{\"jsonrpc\":\"2.0\",\"id\":99,\"method\":\"prompt\"}",
                    Duration.ofMillis(50));
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output)
                .contains("Orchestrator event")
                .contains("seq: 1")
                .contains("request: 99")
                .contains("\"method\":\"event\"")
                .contains("\"content_delta\"");
    }

    @Test
    void orchestratorLogFollowTailerPrintsAppendedStderrLines() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(
                Map.of("PI_ORCHESTRATOR_DIR", tempDir.resolve("orchestrator_logs").toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);
        Files.createDirectories(config.getLogsDir());
        Path logPath = config.getLogsDir().resolve("agent-1.stderr.log");
        Files.writeString(logPath, "existing\n");
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        java.io.PrintStream out = new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8);

        try (InteractiveModeRunner.OrchestratorLogFollowTailer tailer =
                     new InteractiveModeRunner.OrchestratorLogFollowTailer(out, () -> storage)) {
            assertThat(tailer.start("agent-1")).contains("subscribed: agent-1");
            Files.writeString(logPath, "new stderr line\n", StandardOpenOption.APPEND);
            assertThat(tailer.pollOnce()).isEqualTo(1);
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output)
                .contains("Orchestrator stderr")
                .contains("instance: agent-1")
                .contains("agent-1.stderr.log")
                .contains("new stderr line")
                .doesNotContain("existing");
    }

    @Test
    void testRpcModeRunnerExecution() throws Exception {
        Path cwd = tempDir.resolve("project_rpc");
        Path agentDir = tempDir.resolve("agent_rpc");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("diagnose"));
        Files.writeString(agentDir.resolve("skills").resolve("diagnose").resolve("SKILL.md"), """
                ---
                name: diagnose
                description: Diagnose flaky tests
                trigger-terms:
                  - flaky
                ---
                Diagnose.
                """);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));

        Path sessionsDir = tempDir.resolve("sessions_rpc");
        SessionManager externalManager = SessionManager.create(cwd, sessionsDir);
        SkillDiagnosticHistory externalHistory = new SkillDiagnosticHistory();
        externalHistory.record(new AgentSession.AgentSessionEvent.SkillTriggerDiagnostic(List.of(
                new SkillLoader.SkillTriggerMatch("external-diagnose",
                        agentDir.resolve("skills").resolve("diagnose").resolve("SKILL.md"),
                        true,
                        List.of("term:external")))));
        String externalBranch = externalHistory.persist(externalManager);
        Path externalSessionFile = externalManager.sessionFile().orElseThrow();

        SessionManager sessionManager = SessionManager.create(cwd, sessionsDir);
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("RPC answer")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_rpc"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            String externalSessionJson = externalSessionFile.toString()
                    .replace("\\", "\\\\")
                    .replace("\"", "\\\"");
            System.setIn(new java.io.ByteArrayInputStream((("""
                    {"id":1,"method":"list_models"}
                    {"id":2,"method":"refresh_models","params":{"provider":"ollama"}}
                    {"id":3,"method":"prompt","params":{"text":"Investigate flaky checkout tests"}}
                    {"id":4,"method":"skill_diagnostics","params":{"skill":"diagnose","reason":"flaky","limit":1,"sort":"newest"}}
                    {"id":5,"method":"skill_diagnostics","params":{"session":"%s","branch":"%s","skill":"external-diagnose","reason":"external","limit":1}}
                    {"id":6,"method":"skill_diagnostic_sources","params":{"limit":5}}
                    {"id":7,"method":"skill_diagnostic_picker","params":{"limit":5}}
                    {"id":8,"method":"exit"}
                    """).formatted(externalSessionJson, externalBranch)).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            int exitCode = RpcModeRunner.run(runtime, args);
            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"models\":")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"refreshed\":true,\"provider\":\"ollama\",\"models\":")
                    .contains("\"type\":\"skill_trigger_diagnostic\"")
                    .contains("\"skill\":\"diagnose\"")
                    .contains("\"term:flaky\"")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"status\":\"ok\"}")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{\"schemaVersion\":1")
                    .contains("\"filter\":{\"skill\":\"diagnose\",\"model\":\"\",\"reason\":\"flaky\"}")
                    .contains("\"page\":{\"offset\":0,\"limit\":1,\"sort\":\"newest\",\"totalEntries\":1,\"returnedEntries\":1}")
                    .contains("\"summary\":{\"entries\":1,\"matches\":1,\"visible\":1,\"manualOnly\":0")
                    .contains("\"reasons\":[{\"value\":\"term:flaky\",\"count\":1}]")
                    .contains("\"entries\":[{\"capturedAt\"")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":5,\"result\":{\"schemaVersion\":1,\"source\":{\"sessionId\":\""
                            + externalManager.sessionId() + "\",\"sessionFile\":\"" + externalSessionJson
                            + "\",\"branch\":\"" + externalBranch + "\"}")
                    .contains("\"filter\":{\"skill\":\"external-diagnose\",\"model\":\"\",\"reason\":\"external\"}")
                    .contains("\"reasons\":[{\"value\":\"term:external\",\"count\":1}]")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":6,\"result\":{\"schemaVersion\":1,\"current\":{\"sessionId\"")
                    .contains("\"sessions\":[")
                    .contains("\"branchTree\"")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"schemaVersion\":1,\"current\":{\"sessionId\"")
                    .contains("\"items\":[")
                    .contains("\"title\":\"1. ")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":8,\"result\":{\"status\":\"exiting\"}");
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    private static final class FakeRpcLauncher implements AgentProcessLauncher {
        final List<FakeRpcProcess> processes = new ArrayList<>();

        @Override
        public AgentProcess start(StartRequest request) {
            FakeRpcProcess process = new FakeRpcProcess();
            processes.add(process);
            return process;
        }
    }

    private static final class FakeRpcProcess implements AgentProcess {
        final List<String> sentLines = new ArrayList<>();
        final Deque<String> responses = new ArrayDeque<>();
        boolean alive = true;

        @Override
        public void sendLine(String line) {
            sentLines.add(line);
        }

        @Override
        public Optional<String> readLine(Duration timeout) {
            return Optional.ofNullable(responses.pollFirst());
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void stop(Duration timeout) {
            alive = false;
        }
    }
}
