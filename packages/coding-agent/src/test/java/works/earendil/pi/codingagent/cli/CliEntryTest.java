package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
            System.setIn(new java.io.ByteArrayInputStream("/models refresh ollama\n/help\nhello\n/exit\n".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            int exitCode = InteractiveModeRunner.run(runtime, args);
            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("Models refreshed for provider: ollama").contains("Available models:")
                    .contains("Available commands:").contains("Goodbye!");
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
}
