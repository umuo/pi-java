package works.earendil.pi.codingagent.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;
import works.earendil.pi.agent.session.SessionEntry;
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
import works.earendil.pi.codingagent.core.BashExecutor;
import works.earendil.pi.codingagent.core.SkillDiagnosticHistory;
import works.earendil.pi.codingagent.core.SlashCommands;
import works.earendil.pi.codingagent.core.extensions.ExtensionCommandContext;
import works.earendil.pi.codingagent.core.extensions.ExtensionPlugin;
import works.earendil.pi.codingagent.core.extensions.ExtensionRunner;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.resources.SourceInfo;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliEntryTest {

    @TempDir
    Path tempDir;

    @Test
    void testCliArgsParsing() {
        CliArgs args = new CliArgs();
        new CommandLine(args).parseArgs("--provider", "openai", "--model", "gpt-4o",
                "--session-dir", "/tmp/pi-sessions", "--extension", "ext-a.jar,ext-b.jar",
                "--no-extensions", "-p", "Hello world");

        assertThat(args.provider).isEqualTo("openai");
        assertThat(args.model).isEqualTo("gpt-4o");
        assertThat(args.sessionDir).isEqualTo("/tmp/pi-sessions");
        assertThat(args.extensions).containsExactly("ext-a.jar", "ext-b.jar");
        assertThat(args.noExtensions).isTrue();
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
    void startupSessionManagerHonorsSessionFlags() throws Exception {
        Path cwd = tempDir.resolve("project_startup_sessions");
        Path sessions = tempDir.resolve("startup_sessions");
        Files.createDirectories(cwd);

        SessionManager older = SessionManager.create(cwd, sessions,
                new SessionManager.NewSessionOptions("older-session", null));
        older.appendSessionInfo("older");
        SessionManager target = SessionManager.create(cwd, sessions,
                new SessionManager.NewSessionOptions("target-session", null));
        target.appendSessionInfo("target");
        Files.setLastModifiedTime(older.sessionFile().orElseThrow(),
                FileTime.from(Instant.now().minus(Duration.ofMinutes(5))));
        Files.setLastModifiedTime(target.sessionFile().orElseThrow(),
                FileTime.from(Instant.now()));

        CliArgs byPath = new CliArgs();
        byPath.session = target.sessionFile().orElseThrow().toString();
        assertThat(Main.createStartupSessionManager(byPath, cwd, sessions).sessionId())
                .isEqualTo("target-session");

        CliArgs byPartialId = new CliArgs();
        byPartialId.session = "target-s";
        assertThat(Main.createStartupSessionManager(byPartialId, cwd, sessions).sessionFile())
                .contains(target.sessionFile().orElseThrow());

        CliArgs continueArgs = new CliArgs();
        continueArgs.continueSession = true;
        assertThat(Main.createStartupSessionManager(continueArgs, cwd, sessions).sessionId())
                .isEqualTo("target-session");

        CliArgs resumeArgs = new CliArgs();
        resumeArgs.resume = true;
        assertThat(Main.createStartupSessionManager(resumeArgs, cwd, sessions).sessionId())
                .isEqualTo("target-session");

        CliArgs forkArgs = new CliArgs();
        forkArgs.fork = "target-session";
        forkArgs.sessionId = "forked-session";
        SessionManager forked = Main.createStartupSessionManager(forkArgs, cwd, sessions);
        assertThat(forked.sessionId()).isEqualTo("forked-session");
        assertThat(SessionManager.buildSessionInfo(forked.sessionFile().orElseThrow()).orElseThrow().parentSessionPath())
                .isEqualTo(target.sessionFile().orElseThrow());

        CliArgs existingSessionId = new CliArgs();
        existingSessionId.sessionId = "target-session";
        assertThat(Main.createStartupSessionManager(existingSessionId, cwd, sessions).sessionFile())
                .contains(target.sessionFile().orElseThrow());

        CliArgs newSessionId = new CliArgs();
        newSessionId.sessionId = "fresh-session";
        SessionManager fresh = Main.createStartupSessionManager(newSessionId, cwd, sessions);
        assertThat(fresh.sessionId()).isEqualTo("fresh-session");
        assertThat(fresh.isPersisted()).isTrue();

        CliArgs noSession = new CliArgs();
        noSession.noSession = true;
        noSession.sessionId = "memory-session";
        SessionManager inMemory = Main.createStartupSessionManager(noSession, cwd, sessions);
        assertThat(inMemory.sessionId()).isEqualTo("memory-session");
        assertThat(inMemory.isPersisted()).isFalse();

        assertThat(Main.resolveSessionDir(new CliArgs(), sessions, tempDir.resolve("settings_sessions").toString()))
                .isEqualTo(tempDir.resolve("settings_sessions").toAbsolutePath().normalize());
        CliArgs explicitDir = new CliArgs();
        explicitDir.sessionDir = tempDir.resolve("explicit_sessions").toString();
        assertThat(Main.resolveSessionDir(explicitDir, sessions, tempDir.resolve("settings_sessions").toString()))
                .isEqualTo(tempDir.resolve("explicit_sessions").toAbsolutePath().normalize());
        assertThatThrownBy(() -> Main.resolveSessionPath("missing-session", cwd, sessions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    @Test
    void testPrintModeRunnerExecution() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("stored-key", null));
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
        Files.createDirectories(agentDir.resolve("prompts"));
        Files.createDirectories(agentDir.resolve("themes"));
        Files.writeString(agentDir.resolve("skills").resolve("demo").resolve("SKILL.md"), """
                ---
                name: demo
                description: Demo skill
                trigger-terms:
                  - hello
                ---
                Use demo.
                """);
        Files.writeString(agentDir.resolve("prompts").resolve("fix.md"), """
                ---
                description: Fix an issue
                argument-hint: FILE ISSUE
                ---
                Fix $1 with $2
                All: $@
                """);
        Files.writeString(agentDir.resolve("themes").resolve("ruby.json"), """
                {
                  "name": "ruby",
                  "colors": {
                    "mdHeading": "#ff0000",
                    "toolDiffAdded": "#00ff00",
                    "toolDiffRemoved": "#ff0000"
                  }
                }
                """);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));

        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_int"));
        SessionManager importedSessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_import_source"),
                new SessionManager.NewSessionOptions("imported-session", null));
        importedSessionManager.appendSessionInfo("Imported session");
        Path importPath = importedSessionManager.sessionFile().orElseThrow();
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
        Path exportPath = tempDir.resolve("interactive-session-export.html");
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        AtomicReference<String> copiedText = new AtomicReference<>();
        AtomicReference<String> sharedHtml = new AtomicReference<>();
        AtomicReference<Boolean> sharedPublic = new AtomicReference<>();
        AtomicReference<String> sharedFileName = new AtomicReference<>();
        try {
            InteractiveModeRunner.setClipboardWriterForTesting(copiedText::set);
            InteractiveModeRunner.setClipboardImageReaderForTesting(() -> Optional.of(
                    new InteractiveModeRunner.ClipboardImage("fake-png".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                            "image/png")));
            InteractiveModeRunner.setGistSharerForTesting((htmlFile, publicGist) -> {
                sharedHtml.set(Files.readString(htmlFile));
                sharedPublic.set(publicGist);
                sharedFileName.set(htmlFile.getFileName().toString());
                return new InteractiveModeRunner.GistShareResult("https://gist.github.com/test/pi-session");
            });
            System.setIn(new java.io.ByteArrayInputStream(("/models refresh ollama\n/help\n/settings\n/settings get enableSkillCommands\n/settings set global enableSkillCommands false\n/settings get enableSkillCommands\n/settings unset global enableSkillCommands\n/settings json\n/theme current\n/theme list\n/theme preview ruby\n/theme ruby\n/theme current\n/theme missing\n/login\n/login openai test-key\n/logout\n/logout openai\n/logout openai\n/name\n/name Checkout Session\n/name\n/name clear\n/session\n/prompt list\n/prompt preview fix src/App.java \"quoted bug\"\n/prompt run fix src/App.java bug\n/fix direct.java direct-bug\n/reload\n/skill-diagnostics\n/orchestrator-status tail agent-1 nope\n/skill:missing now\n/teamwork-preview compact\n/grill-me checkout\n/grill-me status\n/grill-me answer conversion drops on payment\n/grill-me reset\n!printf included-shell\n!!printf excluded-shell\nhello\n/copy\n/paste-image pasted-clip\n/tree\n/export " + exportPath + "\n/share\n/skill-diagnostics\n/skill-diagnostics history\n/skill-diagnostics history skill=demo model=visible reason=hello\n/skill-diagnostics json skill=demo\n/skill-diagnostics sources limit=5\n/skill-diagnostics picker limit=5\n/skill-diagnostics inspect 1\n/skill-recommend demo\n/orchestrator-status dashboard skill=demo reason=hello\n/skill-diagnostics clear\n/skill-diagnostics\n/import " + importPath + "\nafter import\n/exit\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            int exitCode = InteractiveModeRunner.run(runtime, args);
            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("Models refreshed for provider: ollama").contains("Available models:")
                    .contains("Available commands:").contains("Goodbye!");
            assertThat(output)
                    .contains("!<cmd>          Run bash command and include the result in context")
                    .contains("!!<cmd>         Run bash command without adding the result to model context");
            assertThat(output).contains("/orchestrator-status dashboard [instanceId] [events] [filters] Show instances, stderr, RPC events, and skill diagnostics")
                    .contains("/orchestrator-status tail [instanceId] [lines] Show recent stderr log lines")
                    .contains("/orchestrator-status tail --follow [instanceId] Subscribe to stderr log lines")
                    .contains("/orchestrator-status events [instanceId|stop] Subscribe to live RPC events")
                    .contains("/grill-me answer <text> Record an interview answer and continue")
                    .contains("/grill-me status|reset Show or clear the active interview")
                    .contains("/settings [json|get|set|unset] View or update settings")
                    .contains("/prompt [list|preview|run] List, preview, or run loaded prompt templates")
                    .contains("/theme [list|current|set|preview] List, switch, or preview loaded themes")
                    .contains("/login <provider> <api-key> Configure provider API key authentication")
                    .contains("/logout <provider> Remove stored or runtime provider authentication")
                    .contains("/export [path]  Export session as HTML")
                    .contains("/share [public|secret] Share session HTML as a GitHub gist via gh")
                    .contains("/copy           Copy the last assistant message to clipboard")
                    .contains("/paste-image [path] Save clipboard image and print an @path")
                    .contains("/name [text|clear] Show, set, or clear the current session name")
                    .contains("/session        Show current session info and stats")
                    .contains("/import <path>  Import a JSONL session file")
                    .contains("/tree           Show the current session branch tree")
                    .contains("/reload         Reload settings, auth, models, resources, and extensions")
                    .contains("/skill-diagnostics [history|json|sources|picker|inspect|clear] [branch=<entryId>] [filters] Show, inspect, export, or clear skill trigger diagnostics")
                    .contains("/skill-recommend [query] [reason=<text>] [limit=<n>] Search and recommend loaded skills")
                    .contains("Orchestrator status\nerror: tail lines must be a positive integer: nope");
            assertThat(output).contains("Settings\nproject trusted: true")
                    .contains("path: enableSkillCommands\nvalue: null")
                    .contains("Settings\nstatus: set\nscope: global\npath: enableSkillCommands\nvalue: false")
                    .contains("path: enableSkillCommands\nvalue: false")
                    .contains("Settings\nstatus: unset\nscope: global\npath: enableSkillCommands")
                    .contains("Settings\nscope: merged\njson:");
            assertThat(output).contains("Loaded prompt templates:")
                    .contains("/fix Fix an issue")
                    .contains("Prompt templates\nstatus: available")
                    .contains("- fix FILE ISSUE - Fix an issue [user]")
                    .contains("Prompt template\nstatus: preview\nname: fix")
                    .contains("Fix src/App.java with quoted bug")
                    .contains("Prompt template\nstatus: running\nname: fix")
                    .contains("Fix src/App.java with bug");
            assertThat(output).contains("Theme\nstatus: current")
                    .contains("setting: standard")
                    .contains("Theme\nstatus: available")
                    .contains("- standard (current)")
                    .contains("- ruby")
                    .contains("Theme preview\nname: ruby")
                    .contains("# Theme Preview")
                    .contains("Theme\nstatus: set\nsetting: ruby")
                    .contains("effective: ruby")
                    .contains("Theme\nerror: unknown theme: missing")
                    .contains("available: standard, ruby");
            assertThat(services.settingsManager().getThemeSetting()).isEqualTo("ruby");
            assertThat(output).contains("Provider authentication\nstatus: choose a provider\nusage: /login <provider> <api-key> | /login <provider> env <ENV_VAR>")
                    .contains("Provider authentication\nstatus: logged in\nprovider: openai\nmethod: api-key")
                    .contains("note: API key stored; it is not printed back to the terminal")
                    .contains("Provider authentication\nstatus: choose a provider\nusage: /logout <provider>\nproviders:\n- openai (stored)")
                    .contains("Provider authentication\nstatus: logged out\nprovider: openai\nremoved: stored")
                    .contains("Provider authentication\nstatus: not configured\nprovider: openai");
            assertThat(output).doesNotContain("test-key");
            assertThat(authStorage.has("openai")).isFalse();
            assertThat(output).contains("Session name\nstatus: current")
                    .contains("name: none")
                    .contains("Session name\nstatus: set\nsession: " + sessionManager.sessionId() + "\nname: Checkout Session")
                    .contains("Session name\nstatus: cleared\nsession: " + sessionManager.sessionId() + "\nname: none");
            assertThat(sessionManager.sessionName()).isEmpty();
            assertThat(output).contains("Session info")
                    .contains("session: " + sessionManager.sessionId())
                    .contains("name: none")
                    .contains("file: " + sessionManager.sessionFile().orElseThrow())
                    .contains("cwd: " + cwd.toAbsolutePath().normalize())
                    .contains("persisted: true")
                    .contains("entries: ")
                    .contains("branch entries: ")
                    .contains("model: ")
                    .contains("thinking: off")
                    .contains("messages: user=0 assistant=0 tool=0 total=0")
                    .contains("tokens: input=0 output=0 cache=0 reasoning=0 total=0")
                    .contains("skills: 1")
                    .contains("tools: ");
            assertThat(output).contains("Session reload")
                    .contains("status: reloaded")
                    .contains("skills: ")
                    .contains("tools: ")
                    .contains("models: ");
            assertThat(output).contains("Session tree")
                    .contains("session: " + sessionManager.sessionId())
                    .contains("entries: ")
                    .contains("custom_message bashExecution")
                    .contains("message user hello")
                    .contains("message assistant # Interactive answer");
            assertThat(output).contains("Bash command\nstatus: completed\ncommand: printf included-shell\ncontext: included")
                    .contains("Bash command\nstatus: completed\ncommand: printf excluded-shell\ncontext: excluded")
                    .contains("output:\nincluded-shell")
                    .contains("output:\nexcluded-shell");
            assertThat(output).contains("Session export")
                    .contains("status: exported")
                    .contains("format: html")
                    .contains("file: " + exportPath);
            assertThat(Files.readString(exportPath)).contains("Interactive answer");
            assertThat(Files.readString(sessionManager.sessionFile().orElseThrow()))
                    .contains("Fix direct.java with direct-bug")
                    .doesNotContain("/fix direct.java direct-bug");
            assertThat(output).contains("Session share")
                    .contains("status: shared")
                    .contains("visibility: secret")
                    .contains("url: https://gist.github.com/test/pi-session")
                    .contains("file: pi-session-");
            assertThat(sharedPublic.get()).isFalse();
            assertThat(sharedFileName.get()).startsWith("pi-session-").endsWith(".html");
            assertThat(sharedHtml.get()).contains("Interactive answer")
                    .contains("public class A");
            assertThat(output).contains("Session copy")
                    .contains("status: copied")
                    .contains("chars: ");
            assertThat(copiedText.get()).contains("Interactive answer")
                    .contains("public class A");
            Path pastedImage = cwd.resolve("pasted-clip.png");
            assertThat(output).contains("Clipboard image")
                    .contains("status: saved")
                    .contains("mimeType: image/png")
                    .contains("submit: @" + pastedImage);
            assertThat(Files.readString(pastedImage)).isEqualTo("fake-png");
            assertThat(output).contains("Session import")
                    .contains("status: imported")
                    .contains("session: imported-session");
            assertThat(runtime.session().sessionManager().sessionId()).isEqualTo("imported-session");
            assertThat(runtime.session().stats().userMessages()).isEqualTo(1);
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
                    .contains("Skill diagnostic inspect")
                    .contains("reason drill-down:")
                    .contains("Skill search & recommendation")
                    .contains("1. demo (score: ")
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
            InteractiveModeRunner.setClipboardWriterForTesting(null);
            InteractiveModeRunner.setClipboardImageReaderForTesting(null);
            InteractiveModeRunner.setGistSharerForTesting(null);
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void interactiveExecutesExtensionCommandsWithoutPromptingModel() throws Exception {
        Path cwd = tempDir.resolve("project_extension_command");
        Path agentDir = tempDir.resolve("agent_extension_command");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_extension_command"));
        Model model = services.modelRegistry().getAll().get(0);
        AtomicReference<String> handledArguments = new AtomicReference<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "command-extension";
            }

            @Override
            public List<SlashCommands.SlashCommandInfo> registerCommands() {
                SourceInfo source = SourceInfo.synthetic(Path.of("command-extension.jar"), "extension", Path.of("."));
                return List.of(
                        new SlashCommands.SlashCommandInfo("testcmd", "Run test extension command",
                                SlashCommands.SlashCommandSource.EXTENSION, source),
                        new SlashCommands.SlashCommandInfo("sendmsg", "Send user message from extension command",
                                SlashCommands.SlashCommandSource.EXTENSION, source),
                        new SlashCommands.SlashCommandInfo("args", "Inspect extension command arguments",
                                SlashCommands.SlashCommandSource.EXTENSION, source));
            }

            @Override
            public String executeCommand(String commandName, String arguments, ExtensionCommandContext context)
                    throws Exception {
                if ("sendmsg".equals(commandName)) {
                    context.sendUserMessage(arguments);
                    return "Extension command\nstatus: sent\ncommand: " + commandName
                            + "\nmessages: " + context.stats().totalMessages();
                }
                if ("args".equals(commandName)) {
                    return "Extension command\nstatus: parsed\ncommand: " + context.commandName()
                            + "\nraw: " + context.arguments()
                            + "\nargv: " + String.join("|", context.argv())
                            + "\nname: " + context.option("name").orElse("")
                            + "\ncount: " + context.option("--count").orElse("")
                            + "\nverbose: " + context.hasFlag("verbose")
                            + "\npositionals: " + String.join("|", context.positionals());
                }
                handledArguments.set(commandName + ":" + arguments);
                context.setSessionName("From Extension");
                String customEntryId = context.appendEntry("extension.command",
                        Map.of("command", commandName, "arguments", arguments));
                context.setLabel(customEntryId, "extension-command-entry");
                return "Extension command\nstatus: handled\ncommand: " + commandName
                        + "\nargs: " + arguments
                        + "\nentry: " + customEntryId
                        + "\nsession: " + context.sessionId()
                        + "\ncwd: " + context.cwd()
                        + "\nmessages: " + context.stats().totalMessages();
            }

            @Override
            public UserBashResult onUserBash(String commandName, boolean excludeFromContext, Path cwd) {
                if ("extension-bash".equals(commandName)) {
                    return UserBashResult.result(new BashExecutor.Result(
                            "bash from extension: " + cwd.getFileName(), 0, false, false, null));
                }
                return null;
            }
        };
        ExtensionRunner extensionRunner = new ExtensionRunner(List.of(plugin));
        AtomicInteger modelCalls = new AtomicInteger();
        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, extensionRunner.collectAgentTools(),
                            (m, ctx, opts) -> {
                                modelCalls.incrementAndGet();
                                return new Message.Assistant(List.of(new Content.Text("model response")),
                                        m.provider(), m.modelId(), StopReason.STOP,
                                        new Usage(1, 1, 0, 0, 0), null, Instant.now());
                            },
                            extensionRunner
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_extension_command"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(("/help\n/testcmd hello world\n"
                    + "/args --name \"Ada Lovelace\" --count=2 bare --verbose\n"
                    + "!extension-bash\n/sendmsg from extension\n/exit\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));

            int exitCode = InteractiveModeRunner.run(runtime, args);

            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("Loaded extension commands:")
                    .contains("/testcmd Run test extension command")
                    .contains("/sendmsg Send user message from extension command")
                    .contains("/args Inspect extension command arguments")
                    .contains("Extension command\nstatus: handled\ncommand: testcmd\nargs: hello world")
                    .contains("session: " + sessionManager.sessionId())
                    .contains("cwd: " + cwd.toAbsolutePath().normalize())
                    .contains("messages: 0")
                    .contains("Extension command\nstatus: parsed\ncommand: args")
                    .contains("argv: --name|Ada Lovelace|--count=2|bare|--verbose")
                    .contains("name: Ada Lovelace")
                    .contains("count: 2")
                    .contains("verbose: true")
                    .contains("positionals: bare")
                    .contains("Bash command\nstatus: completed\ncommand: extension-bash\ncontext: included")
                    .contains("output:\nbash from extension: project_extension_command")
                    .contains("model response")
                    .contains("Extension command\nstatus: sent\ncommand: sendmsg\nmessages: 3")
                    .contains("Goodbye!");
            assertThat(handledArguments).hasValue("testcmd:hello world");
            assertThat(modelCalls).hasValue(1);
            assertThat(runtime.session().stats().userMessages()).isEqualTo(1);
            assertThat(runtime.session().stats().assistantMessages()).isEqualTo(1);
            assertThat(runtime.session().sessionManager().sessionName()).contains("From Extension");
            SessionEntry.CustomEntry customEntry = runtime.session().sessionManager().branch().stream()
                    .filter(SessionEntry.CustomEntry.class::isInstance)
                    .map(SessionEntry.CustomEntry.class::cast)
                    .findFirst()
                    .orElseThrow();
            assertThat(customEntry.customType()).isEqualTo("extension.command");
            assertThat(customEntry.data().path("command").asText()).isEqualTo("testcmd");
            assertThat(customEntry.data().path("arguments").asText()).isEqualTo("hello world");
            assertThat(runtime.session().sessionManager().label(customEntry.id()))
                    .contains("extension-command-entry");
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void interactiveExtensionInputCanTransformOrHandleText() throws Exception {
        Path cwd = tempDir.resolve("project_extension_input");
        Path agentDir = tempDir.resolve("agent_extension_input");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_extension_input"));
        Model model = services.modelRegistry().getAll().get(0);
        AtomicReference<String> modelSaw = new AtomicReference<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "input-extension";
            }

            @Override
            public InputResult onInput(String text, ExtensionCommandContext context) {
                if ("handled input".equals(text)) {
                    return InputResult.handledWithOutput("Extension input\nstatus: handled\ntext: " + text);
                }
                if ("rewrite me".equals(text)) {
                    return InputResult.transform("rewritten prompt");
                }
                return null;
            }
        };
        ExtensionRunner extensionRunner = new ExtensionRunner(List.of(plugin));
        AtomicInteger modelCalls = new AtomicInteger();
        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, extensionRunner.collectAgentTools(),
                            (m, ctx, opts) -> {
                                modelCalls.incrementAndGet();
                                Message.User user = (Message.User) ctx.messages().stream()
                                        .filter(Message.User.class::isInstance)
                                        .reduce((first, second) -> second)
                                        .orElseThrow();
                                String text = ((Content.Text) user.content().getFirst()).text();
                                modelSaw.set(text);
                                return new Message.Assistant(List.of(new Content.Text("assistant saw: " + text)),
                                        m.provider(), m.modelId(), StopReason.STOP,
                                        new Usage(1, 1, 0, 0, 0), null, Instant.now());
                            },
                            extensionRunner
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_extension_input"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(("handled input\nrewrite me\n/exit\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));

            int exitCode = InteractiveModeRunner.run(runtime, args);

            assertThat(exitCode).isEqualTo(0);
            String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(output).contains("Extension input\nstatus: handled\ntext: handled input")
                    .contains("assistant saw: rewritten prompt")
                    .contains("Goodbye!");
            assertThat(modelCalls).hasValue(1);
            assertThat(modelSaw).hasValue("rewritten prompt");
            assertThat(runtime.session().stats().userMessages()).isEqualTo(1);
            assertThat(runtime.session().stats().assistantMessages()).isEqualTo(1);
            Message.User user = (Message.User) runtime.session().messages().stream()
                    .filter(message -> message instanceof works.earendil.pi.agent.core.AgentMessage.Llm)
                    .map(message -> ((works.earendil.pi.agent.core.AgentMessage.Llm) message).message())
                    .filter(Message.User.class::isInstance)
                    .findFirst()
                    .orElseThrow();
            assertThat(((Content.Text) user.content().getFirst()).text()).isEqualTo("rewritten prompt");
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    @Test
    void interactiveLoginStoresApiKeyAndEnvReferences() throws Exception {
        Path cwd = tempDir.resolve("project_login");
        Path agentDir = tempDir.resolve("agent_login");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));

        String listOutput = InteractiveModeRunner.handleLogin(authStorage, services.modelRegistry(), "");
        assertThat(listOutput)
                .contains("Provider authentication")
                .contains("status: choose a provider")
                .contains("usage: /login <provider> <api-key> | /login <provider> env <ENV_VAR>");

        String apiKeyOutput = InteractiveModeRunner.handleLogin(authStorage, services.modelRegistry(), "openai login-key");
        assertThat(apiKeyOutput)
                .contains("status: logged in")
                .contains("provider: openai")
                .contains("method: api-key")
                .doesNotContain("login-key");
        assertThat(authStorage.getApiKey("openai")).contains("login-key");

        String envName = "PI_TEST_KEY_DOES_NOT_EXIST";
        String envOutput = InteractiveModeRunner.handleLogin(authStorage, services.modelRegistry(), "openai env " + envName);
        assertThat(envOutput)
                .contains("status: logged in")
                .contains("method: api-key-env")
                .contains("source: " + envName)
                .contains("warning: environment variable is not set in the current process");
        assertThat(authStorage.get("openai").orElseThrow())
                .isEqualTo(new AuthStorage.ApiKeyCredential("$" + envName, null));

        assertThat(InteractiveModeRunner.handleLogin(authStorage, services.modelRegistry(), "openai"))
                .contains("error: missing API key for provider: openai");
        assertThat(InteractiveModeRunner.handleLogin(authStorage, services.modelRegistry(), "openai env 1BAD"))
                .contains("error: invalid environment variable name: 1BAD");
    }

    @Test
    void interactiveLogoutRemovesStoredAndRuntimeAuth() {
        AuthStorage authStorage = AuthStorage.inMemory();
        authStorage.set("openai", new AuthStorage.ApiKeyCredential("stored-key", null));
        authStorage.setRuntimeApiKey("anthropic", "runtime-key");

        assertThat(InteractiveModeRunner.handleLogout(authStorage, ""))
                .contains("Provider authentication")
                .contains("status: choose a provider")
                .contains("- openai (stored)");
        assertThat(InteractiveModeRunner.handleLogout(authStorage, "openai"))
                .contains("status: logged out")
                .contains("provider: openai")
                .contains("removed: stored");
        assertThat(authStorage.has("openai")).isFalse();

        assertThat(InteractiveModeRunner.handleLogout(authStorage, "anthropic"))
                .contains("status: logged out")
                .contains("provider: anthropic")
                .contains("removed: runtime");
        assertThat(authStorage.getAuthStatus("anthropic").source()).isNull();

        authStorage.setEnvironment(Map.of("OPENAI_API_KEY", "from-env"));
        assertThat(InteractiveModeRunner.handleLogout(authStorage, "openai"))
                .contains("status: environment-only")
                .contains("source: OPENAI_API_KEY")
                .contains("remove the environment variable");
        assertThat(InteractiveModeRunner.handleLogout(authStorage, "openai extra"))
                .contains("error: too many arguments")
                .contains("usage: /logout <provider>");
    }

    @Test
    void interactiveForksFromUserMessageEntry() throws Exception {
        Path cwd = tempDir.resolve("project_fork");
        Path agentDir = tempDir.resolve("agent_fork");
        Path sessionDir = tempDir.resolve("sessions_fork");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, sessionDir);
        com.fasterxml.jackson.databind.node.ObjectNode user = JsonCodec.mapper().createObjectNode();
        user.put("role", "user");
        com.fasterxml.jackson.databind.node.ArrayNode content = JsonCodec.mapper().createArrayNode();
        content.addObject().put("text", "fork me");
        user.set("content", content);
        String userEntryId = sessionManager.appendMessage(user);
        Path originalSessionFile = sessionManager.sessionFile().orElseThrow();
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("fork answer")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_fork"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(("/help\n/fork " + userEntryId + "\n/exit\n")
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(InteractiveModeRunner.run(runtime, args)).isEqualTo(0);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output).contains("/fork [before|at] <entryId> Fork the current session at an entry id")
                .contains("Session fork")
                .contains("status: forked")
                .contains("position: before")
                .contains("selected: fork me")
                .contains("Goodbye!");
        assertThat(runtime.session().sessionManager().sessionId()).isNotEqualTo(sessionManager.sessionId());
        assertThat(SessionManager.buildSessionInfo(runtime.session().sessionFile().orElseThrow()).orElseThrow()
                .parentSessionPath()).isEqualTo(originalSessionFile);
    }

    @Test
    void interactiveClonesCurrentBranch() throws Exception {
        Path cwd = tempDir.resolve("project_clone");
        Path agentDir = tempDir.resolve("agent_clone");
        Path sessionDir = tempDir.resolve("sessions_clone");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, sessionDir);
        Path originalSessionFile = sessionManager.sessionFile().orElseThrow();
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("clone answer")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_clone"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream("/help\nclone source\n/clone\n/exit\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(InteractiveModeRunner.run(runtime, args)).isEqualTo(0);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output).contains("/clone          Clone the current active branch into a new session")
                .contains("Session clone")
                .contains("status: cloned")
                .contains("Goodbye!");
        assertThat(runtime.session().sessionManager().sessionId()).isNotEqualTo(sessionManager.sessionId());
        assertThat(runtime.session().stats().userMessages()).isEqualTo(1);
        assertThat(runtime.session().stats().assistantMessages()).isEqualTo(1);
        assertThat(SessionManager.buildSessionInfo(runtime.session().sessionFile().orElseThrow()).orElseThrow()
                .parentSessionPath()).isEqualTo(originalSessionFile);
    }

    @Test
    void interactiveStartsNewSession() throws Exception {
        Path cwd = tempDir.resolve("project_new");
        Path agentDir = tempDir.resolve("agent_new");
        Path sessionDir = tempDir.resolve("sessions_new");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, sessionDir,
                new SessionManager.NewSessionOptions("source-session", null));
        Path originalSessionFile = sessionManager.sessionFile().orElseThrow();
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("new answer")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_new"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream("/help\nsource prompt\n/new Fresh session\n/exit\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(InteractiveModeRunner.run(runtime, args)).isEqualTo(0);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output).contains("/new [name]     Start a new session, optionally with a display name")
                .contains("Session new")
                .contains("status: created")
                .contains("name: Fresh session")
                .contains("previous: " + originalSessionFile)
                .contains("Goodbye!");
        assertThat(runtime.session().sessionManager().sessionId()).isNotEqualTo(sessionManager.sessionId());
        assertThat(runtime.session().sessionManager().sessionName()).contains("Fresh session");
        assertThat(runtime.session().stats().userMessages()).isZero();
        assertThat(runtime.session().stats().assistantMessages()).isZero();
        assertThat(SessionManager.buildSessionInfo(runtime.session().sessionFile().orElseThrow()).orElseThrow()
                .parentSessionPath()).isEqualTo(originalSessionFile);
    }

    @Test
    void interactiveCompactsCurrentSession() throws Exception {
        Path cwd = tempDir.resolve("project_compact");
        Path agentDir = tempDir.resolve("agent_compact");
        Path sessionDir = tempDir.resolve("sessions_compact");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager sessionManager = SessionManager.create(cwd, sessionDir);
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("compact answer")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(10, 5, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "test_compact"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream("/help\nfirst prompt\nsecond prompt\n/compact\n/exit\n"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(InteractiveModeRunner.run(runtime, args)).isEqualTo(0);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output).contains("/compact        Manually compact the current session context")
                .contains("Session compact")
                .contains("status: compacted")
                .contains("entry: ")
                .contains("first kept: ")
                .contains("summarized messages: ")
                .contains("tokens before: ")
                .contains("summary chars: ")
                .contains("Goodbye!");
        assertThat(runtime.session().sessionManager().entries())
                .anyMatch(entry -> entry instanceof SessionEntry.CompactionEntry compaction
                        && compaction.summary().contains("compact answer"));
    }

    @Test
    void interactiveListsAndResumesSessions() throws Exception {
        Path cwd = tempDir.resolve("project_resume");
        Path agentDir = tempDir.resolve("agent_resume");
        Path sessionDir = tempDir.resolve("sessions_resume");
        Files.createDirectories(cwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, authStorage, null, null, null, null, true
        ));
        SessionManager currentManager = SessionManager.create(cwd, sessionDir,
                new SessionManager.NewSessionOptions("current-session", null));
        currentManager.appendSessionInfo("Current session");
        SessionManager targetManager = SessionManager.create(cwd, sessionDir,
                new SessionManager.NewSessionOptions("target-session", null));
        targetManager.appendSessionInfo("Target session");
        SessionManager deleteManager = SessionManager.create(cwd, sessionDir,
                new SessionManager.NewSessionOptions("delete-session", null));
        deleteManager.appendSessionInfo("Delete session");
        Model model = services.modelRegistry().getAll().get(0);

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("resume answer")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, currentManager, "test_resume"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(("/help\n/resume\n/resume rename "
                    + targetManager.sessionId() + " Renamed target\n/resume\n/resume delete "
                    + deleteManager.sessionId() + "\n/resume "
                    + targetManager.sessionId() + "\n/exit\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(InteractiveModeRunner.run(runtime, args)).isEqualTo(0);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output).contains("/resume [--all|find] List, search, resume, rename, or delete sessions")
                .contains("Session resume\nstatus: choose a session")
                .contains("usage: /resume [--all] [index|id|path] | /resume [--all] find <query> | /resume [--all] rename <target> <name> | /resume [--all] delete <target>")
                .contains("current-session")
                .contains("target-session")
                .contains("Session resume\nstatus: renamed")
                .contains("name: Renamed target")
                .contains("Session resume\nstatus: deleted")
                .contains("session: delete-session")
                .contains("Session resume\nstatus: resumed")
                .contains("session: target-session")
                .contains("Goodbye!");
        assertThat(runtime.session().sessionManager().sessionId()).isEqualTo("target-session");
        assertThat(runtime.session().sessionFile()).contains(targetManager.sessionFile().orElseThrow());
        assertThat(runtime.session().sessionManager().sessionName()).contains("Renamed target");
        assertThat(SessionManager.buildSessionInfo(targetManager.sessionFile().orElseThrow()).orElseThrow().name())
                .isEqualTo("Renamed target");
        assertThat(Files.exists(deleteManager.sessionFile().orElseThrow())).isFalse();
    }

    @Test
    void interactiveSearchesAndResumesAllSessions() throws Exception {
        Path cwd = tempDir.resolve("project_resume_all");
        Path otherCwd = tempDir.resolve("project_resume_all_other");
        Path agentDir = tempDir.resolve("agent_resume_all");
        Path rootSessionDir = tempDir.resolve("sessions_resume_all");
        Path currentSessionDir = rootSessionDir.resolve("current");
        Path otherSessionDir = rootSessionDir.resolve("other");
        Files.createDirectories(cwd);
        Files.createDirectories(otherCwd);

        AuthStorage authStorage = AuthStorage.inMemory();
        SessionManager currentManager = SessionManager.create(cwd, currentSessionDir,
                new SessionManager.NewSessionOptions("current-all-session", null));
        currentManager.appendSessionInfo("Local Filter Session");
        currentManager.appendMessage(userMessage("local-message-keyword"));
        SessionManager otherManager = SessionManager.create(otherCwd, otherSessionDir,
                new SessionManager.NewSessionOptions("other-all-session", null));
        otherManager.appendSessionInfo("Global Search Session");
        otherManager.appendMessage(userMessage("global-message-keyword"));

        AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
            AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                    options.cwd(), agentDir, authStorage, null, null, null, null, true
            ));
            Model model = services.modelRegistry().getAll().get(0);
            AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                    new AgentSessionServices.CreateSessionOptions(
                            services, options.sessionManager(), model, ThinkingLevel.OFF,
                            List.of(), List.of(), List.of(), null, List.of(),
                            (m, ctx, opts) -> new Message.Assistant(List.of(new Content.Text("resume all answer")),
                                    m.provider(), m.modelId(), StopReason.STOP, new Usage(1, 1, 0, 0, 0), null, Instant.now())
                    )
            );
            return new AgentSessionRuntime.CreateRuntimeResult(sessionRes.session(), services, services.diagnostics(), null);
        }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, currentManager, "test_resume_all"));

        CliArgs args = new CliArgs();
        java.io.InputStream originalIn = System.in;
        java.io.PrintStream originalOut = System.out;
        java.io.ByteArrayOutputStream outBuf = new java.io.ByteArrayOutputStream();
        try {
            System.setIn(new java.io.ByteArrayInputStream(("/resume\n/resume --all\n/resume find local-message-keyword\n"
                    + "/resume --all find global-message-keyword\n/resume --all "
                    + otherManager.sessionId() + "\n/exit\n").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            System.setOut(new java.io.PrintStream(outBuf, true, java.nio.charset.StandardCharsets.UTF_8));
            assertThat(InteractiveModeRunner.run(runtime, args)).isEqualTo(0);
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }

        String output = outBuf.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(output).contains("scope: project")
                .contains("current-all-session")
                .contains("scope: all")
                .contains("other-all-session")
                .contains("filter: local-message-keyword")
                .contains("filter: global-message-keyword")
                .contains("cwd=" + otherCwd.toAbsolutePath().normalize())
                .contains("Session resume\nstatus: resumed")
                .contains("session: other-all-session")
                .contains("Goodbye!");
        assertThat(output.indexOf("scope: project")).isLessThan(output.indexOf("other-all-session"));
        assertThat(runtime.session().sessionManager().sessionId()).isEqualTo("other-all-session");
        assertThat(runtime.session().sessionManager().cwd()).isEqualTo(otherCwd.toAbsolutePath().normalize());
        assertThat(runtime.session().sessionFile()).contains(otherManager.sessionFile().orElseThrow());
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
                    {"id":8,"method":"skill_diagnostic_inspect","params":{"index":1}}
                    {"id":9,"method":"skill_recommend","params":{"query":"flaky"}}
                    {"id":10,"method":"exit"}
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
                    .contains("\"reasonDrillDown\":[{\"reason\":\"term:flaky\",\"matches\":1")
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
                    .contains("\"jsonrpc\":\"2.0\",\"id\":8,\"result\":{\"schemaVersion\":1")
                    .contains("\"selectedSource\":{\"index\":1")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":9,\"result\":{\"items\":[{\"skillName\":\"diagnose\"")
                    .contains("\"jsonrpc\":\"2.0\",\"id\":10,\"result\":{\"status\":\"exiting\"}");
        } finally {
            System.setIn(originalIn);
            System.setOut(originalOut);
        }
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode userMessage(String text) {
        com.fasterxml.jackson.databind.node.ObjectNode message = JsonCodec.mapper().createObjectNode();
        message.put("role", "user");
        com.fasterxml.jackson.databind.node.ArrayNode content = JsonCodec.mapper().createArrayNode();
        content.addObject().put("type", "text").put("text", text);
        message.set("content", content);
        return message;
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
