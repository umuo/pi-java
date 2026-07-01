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
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.AgentSessionServices;
import works.earendil.pi.codingagent.core.AuthStorage;
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
    void testInteractiveModeRunnerExecution() throws Exception {
        Path cwd = tempDir.resolve("project_int");
        Path agentDir = tempDir.resolve("agent_int");
        Files.createDirectories(cwd);
        Files.createDirectories(agentDir.resolve("skills").resolve("demo"));
        Files.writeString(agentDir.resolve("skills").resolve("demo").resolve("SKILL.md"),
                "---\nname: demo\ndescription: Demo skill\n---\nUse demo.");

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
            System.setIn(new java.io.ByteArrayInputStream("/models refresh ollama\n/help\n/orchestrator-status tail agent-1 nope\n/skill:missing now\n/teamwork-preview compact\n/grill-me checkout\nhello\n/exit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            int exitCode = InteractiveModeRunner.run(runtime, args);
            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("Models refreshed for provider: ollama").contains("Available models:")
                    .contains("Available commands:").contains("Goodbye!");
            assertThat(output).contains("/orchestrator-status dashboard [instanceId] [events] Show instances, stderr, and recent RPC events")
                    .contains("/orchestrator-status tail [instanceId] [lines] Show recent stderr log lines")
                    .contains("/orchestrator-status tail --follow [instanceId] Subscribe to stderr log lines")
                    .contains("/orchestrator-status events [instanceId|stop] Subscribe to live RPC events")
                    .contains("Orchestrator status\nerror: tail lines must be a positive integer: nope");
            assertThat(output).contains("Loaded skills:")
                    .contains("/skill:demo Demo skill")
                    .contains("Skill not found: missing");
            assertThat(output).contains("Teamwork preview")
                    .contains("Planned sub-agents:")
                    .contains("Starting /grill-me interview...");
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
                .contains("usage: /orchestrator-status [dashboard [instanceId] [events] | tail [instanceId] [lines] | tail --follow [instanceId] | tail --stop | events [instanceId|stop]]");
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

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));

        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_rpc"));
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
            System.setIn(new java.io.ByteArrayInputStream(("""
                    {"id":1,"method":"list_models"}
                    {"id":2,"method":"refresh_models","params":{"provider":"ollama"}}
                    {"id":3,"method":"exit"}
                    """).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            int exitCode = RpcModeRunner.run(runtime, args);
            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"models\":")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"refreshed\":true,\"provider\":\"ollama\",\"models\":")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"status\":\"exiting\"}");
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
