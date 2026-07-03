package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.agent.session.SessionEntry;
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
import works.earendil.pi.codingagent.core.extensions.ExtensionCommandContext;
import works.earendil.pi.codingagent.core.extensions.ExtensionPlugin;
import works.earendil.pi.codingagent.core.extensions.ExtensionRunner;
import works.earendil.pi.codingagent.resources.SourceInfo;
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

            @Override
            public AgentTool.AgentToolResult executeTool(String toolName, Object input) {
                Object text = input instanceof Map<?, ?> map ? map.get("text") : null;
                return new AgentTool.AgentToolResult(
                        List.of(new Content.Text("extension echoed: " + text)),
                        Map.of("extension", name(), "tool", toolName, "input", input),
                        false,
                        false);
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
        assertThat(execution.error()).isFalse();
        assertThat(((Content.Text) execution.content().getFirst()).text())
                .isEqualTo("extension echoed: hello");
        Map<?, ?> details = (Map<?, ?>) execution.details();
        assertThat(details.get("extension")).isEqualTo("demo-extension");
        assertThat(details.get("tool")).isEqualTo("ext_echo");
    }

    @Test
    void extensionRegisteredToolsWithoutExecutorReturnCompatibilityError() throws Exception {
        Tool extensionTool = new Tool("ext_noop", "No executor",
                JsonCodec.parse("{\"type\":\"object\"}"), null);
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "legacy-extension";
            }

            @Override
            public List<Tool> registerTools() {
                return List.of(extensionTool);
            }
        };

        AgentTool registered = new ExtensionRunner(List.of(plugin)).collectAgentTools().getFirst();
        AgentTool.AgentToolResult execution = registered.execute(Map.of("value", "ignored"));

        assertThat(execution.error()).isTrue();
        assertThat(((Content.Text) execution.content().getFirst()).text())
                .contains("Extension tool 'ext_noop' from 'legacy-extension' is registered")
                .contains("does not provide a tool executor yet");
    }

    @Test
    void extensionCommandsAreCollectedExecutedAndFiltered() throws Exception {
        SourceInfo source = SourceInfo.synthetic(Path.of("demo-extension.jar"), "extension", Path.of("."));
        AtomicReference<String> seenArguments = new AtomicReference<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "command-extension";
            }

            @Override
            public List<SlashCommands.SlashCommandInfo> registerCommands() {
                return List.of(
                        new SlashCommands.SlashCommandInfo("extcmd", "Run extension command",
                                SlashCommands.SlashCommandSource.EXTENSION, source),
                        new SlashCommands.SlashCommandInfo("help", "Should not override builtin help",
                                SlashCommands.SlashCommandSource.EXTENSION, source),
                        new SlashCommands.SlashCommandInfo(" ", "Ignored blank command",
                                SlashCommands.SlashCommandSource.EXTENSION, source));
            }

            @Override
            public String executeCommand(String commandName, String arguments) {
                seenArguments.set(commandName + ":" + arguments);
                return "extension command handled: " + arguments;
            }
        };
        ExtensionRunner runner = new ExtensionRunner(List.of(plugin));

        assertThat(runner.collectCommands()).singleElement().satisfies(command -> {
            assertThat(command.name()).isEqualTo("extcmd");
            assertThat(command.description()).isEqualTo("Run extension command");
            assertThat(command.source()).isEqualTo(SlashCommands.SlashCommandSource.EXTENSION);
            assertThat(command.sourceInfo()).isEqualTo(source);
        });
        assertThat(runner.hasCommand("extcmd")).isTrue();
        assertThat(runner.hasCommand("/help")).isFalse();
        assertThat(runner.executeCommand("/extcmd", "hello world"))
                .contains("extension command handled: hello world");
        assertThat(seenArguments).hasValue("extcmd:hello world");
    }

    @Test
    void extensionInputEventsChainTransformsAndHandleInput() throws Exception {
        Path cwd = tempDir.resolve("project_extension_input");
        Path agentDir = tempDir.resolve("agent_extension_input");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"))).session();
        List<String> seen = new ArrayList<>();
        ExtensionPlugin first = new ExtensionPlugin() {
            @Override
            public String name() {
                return "first-input-extension";
            }

            @Override
            public InputResult onInput(String text, ExtensionCommandContext context) {
                seen.add("first:" + text + ":" + context.arguments());
                if ("handled".equals(text)) {
                    return InputResult.handledWithOutput("handled by extension");
                }
                if (text.startsWith("?quick ")) {
                    return InputResult.transform("Respond briefly: " + text.substring("?quick ".length()));
                }
                return null;
            }
        };
        ExtensionPlugin second = new ExtensionPlugin() {
            @Override
            public String name() {
                return "second-input-extension";
            }

            @Override
            public InputResult onInput(String text, ExtensionCommandContext context) {
                seen.add("second:" + text);
                if (text.startsWith("Respond briefly: ")) {
                    return InputResult.transform(text + " Please use one paragraph.");
                }
                return null;
            }
        };
        ExtensionRunner runner = new ExtensionRunner(List.of(first, second));

        ExtensionPlugin.InputResult transformed = runner.emitInput("?quick hello",
                new ExtensionCommandContext(session, "", "?quick hello")).orElseThrow();
        ExtensionPlugin.InputResult handled = runner.emitInput("handled",
                new ExtensionCommandContext(session, "", "handled")).orElseThrow();

        assertThat(transformed.handled()).isFalse();
        assertThat(transformed.text()).isEqualTo("Respond briefly: hello Please use one paragraph.");
        assertThat(handled.handled()).isTrue();
        assertThat(handled.output()).isEqualTo("handled by extension");
        assertThat(seen).containsExactly(
                "first:?quick hello:?quick hello",
                "second:Respond briefly: hello",
                "first:handled:handled");
    }

    @Test
    void executesExtensionRegisteredToolsThroughAgentLoop() throws Exception {
        Path cwd = tempDir.resolve("project_extension_execute");
        Path agentDir = tempDir.resolve("agent_extension_execute");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        Tool extensionTool = new Tool("ext_echo", "Echo text from an extension",
                JsonCodec.parse("{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}"), null);
        AtomicReference<Object> seenInput = new AtomicReference<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "loop-extension";
            }

            @Override
            public List<Tool> registerTools() {
                return List.of(extensionTool);
            }

            @Override
            public AgentTool.AgentToolResult executeTool(String toolName, Object input) {
                seenInput.set(input);
                Object text = input instanceof Map<?, ?> map ? map.get("text") : null;
                return new AgentTool.AgentToolResult(
                        List.of(new Content.Text("loop echoed: " + text)),
                        Map.of("extension", name(), "tool", toolName),
                        false,
                        false);
            }
        };
        ExtensionRunner runner = new ExtensionRunner(List.of(plugin));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Message.ToolResult> capturedToolResult = new AtomicReference<>();
        works.earendil.pi.agent.core.AgentLoop.StreamFunction assistantWithExtensionTool = (model, context, options) -> {
            if (calls.getAndIncrement() == 0) {
                return new Message.Assistant(List.of(new Content.ToolCall("ext-call-1", "ext_echo",
                        JsonCodec.parse("{\"text\":\"from loop\"}"), List.of())),
                        model.provider(), model.modelId(), StopReason.TOOL_USE,
                        new Usage(1, 1, 0, 0, 0), null, Instant.now());
            }
            context.messages().stream()
                    .filter(Message.ToolResult.class::isInstance)
                    .map(Message.ToolResult.class::cast)
                    .filter(result -> result.toolName().equals("ext_echo"))
                    .findFirst()
                    .ifPresent(capturedToolResult::set);
            return new Message.Assistant(List.of(new Content.Text("done after extension")),
                    model.provider(), model.modelId(), StopReason.STOP,
                    new Usage(1, 1, 0, 0, 0), null, Instant.now());
        };

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, runner.collectAgentTools(), assistantWithExtensionTool)).session();
        List<AgentSession.AgentSessionEvent> events = new ArrayList<>();
        session.subscribe(events::add);

        session.prompt("trigger extension tool");

        assertThat(calls.get()).isEqualTo(2);
        assertThat(((Map<?, ?>) seenInput.get()).get("text")).isEqualTo("from loop");
        assertThat(capturedToolResult.get()).isNotNull();
        assertThat(capturedToolResult.get().error()).isFalse();
        assertThat(((Content.Text) capturedToolResult.get().content().getFirst()).text())
                .isEqualTo("loop echoed: from loop");
        assertThat(session.stats().toolResults()).isEqualTo(1);
        assertThat(events).anySatisfy(event -> {
            assertThat(event).isInstanceOf(AgentSession.AgentSessionEvent.AgentEventEnvelope.class);
            works.earendil.pi.agent.core.AgentEvent wrapped =
                    ((AgentSession.AgentSessionEvent.AgentEventEnvelope) event).event();
            assertThat(wrapped).isInstanceOf(works.earendil.pi.agent.core.AgentEvent.ToolExecutionEnd.class);
            works.earendil.pi.agent.core.AgentEvent.ToolExecutionEnd end =
                    (works.earendil.pi.agent.core.AgentEvent.ToolExecutionEnd) wrapped;
            assertThat(end.toolName()).isEqualTo("ext_echo");
            assertThat(end.error()).isFalse();
        });
    }

    @Test
    void emitsExtensionTurnAndToolHooksDuringPrompt() throws Exception {
        Path cwd = tempDir.resolve("project_extension_hooks");
        Path agentDir = tempDir.resolve("agent_extension_hooks");
        Files.createDirectories(cwd);
        Files.writeString(cwd.resolve("note.txt"), "hook evidence");
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        List<String> events = new ArrayList<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "hook-extension";
            }

            @Override
            public void onBeforeTurn(String prompt) {
                events.add("beforeTurn:" + prompt);
            }

            @Override
            public void onAfterTurn(String response) {
                events.add("afterTurn:" + response);
            }

            @Override
            public void onBeforeToolCall(String toolName, String input) {
                events.add("beforeTool:" + toolName + ":" + input);
            }

            @Override
            public void onAfterToolCall(String toolName, String output) {
                events.add("afterTool:" + toolName + ":" + output);
            }
        };
        ExtensionRunner runner = new ExtensionRunner(List.of(plugin));
        AtomicInteger calls = new AtomicInteger();
        works.earendil.pi.agent.core.AgentLoop.StreamFunction toolCallingAssistant = (model, context, options) -> {
            if (calls.getAndIncrement() == 0) {
                return new Message.Assistant(List.of(new Content.ToolCall("call-1", "read",
                        JsonCodec.parse("{\"path\":\"note.txt\"}"), List.of())),
                        model.provider(), model.modelId(), StopReason.TOOL_USE,
                        new Usage(1, 1, 0, 0, 0), null, Instant.now());
            }
            return new Message.Assistant(List.of(new Content.Text("final hook response")),
                    model.provider(), model.modelId(), StopReason.STOP,
                    new Usage(1, 1, 0, 0, 0), null, Instant.now());
        };

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        List.of("read"), null, null, null, toolCallingAssistant, runner)).session();

        session.prompt("trigger hooks");

        assertThat(events).anySatisfy(event -> assertThat(event).isEqualTo("beforeTurn:trigger hooks"));
        assertThat(events).anySatisfy(event -> assertThat(event).contains("beforeTool:read:")
                .contains("note.txt"));
        assertThat(events).anySatisfy(event -> assertThat(event).contains("afterTool:read:")
                .contains("hook evidence"));
        assertThat(events).anySatisfy(event -> assertThat(event).isEqualTo("afterTurn:final hook response"));
    }

    @Test
    void extensionToolCallCanTransformInputAndBlockExecution() throws Exception {
        Path cwd = tempDir.resolve("project_extension_tool_call");
        Path agentDir = tempDir.resolve("agent_extension_tool_call");
        Files.createDirectories(cwd);
        Files.writeString(cwd.resolve("note.txt"), "original content");
        Files.writeString(cwd.resolve("safe.txt"), "rewritten content");
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        List<String> events = new ArrayList<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "tool-call-extension";
            }

            @Override
            public void onBeforeToolCall(String toolName, String input) {
                events.add("before:" + toolName + ":" + input);
            }

            @Override
            public ToolCallResult onToolCall(String toolName, Object input, ExtensionCommandContext context) {
                if (!"read".equals(toolName) || !(input instanceof Map<?, ?> map)) {
                    return null;
                }
                if ("note.txt".equals(map.get("path"))) {
                    java.util.LinkedHashMap<String, Object> replacement = new java.util.LinkedHashMap<>();
                    replacement.put("path", "safe.txt");
                    return ToolCallResult.transform(replacement);
                }
                if ("blocked.txt".equals(map.get("path"))) {
                    return ToolCallResult.block("Blocked read by extension");
                }
                return null;
            }

            @Override
            public void onAfterToolCall(String toolName, String output) {
                events.add("after:" + toolName + ":" + output);
            }
        };
        ExtensionRunner runner = new ExtensionRunner(List.of(plugin));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<List<Message.ToolResult>> capturedResults = new AtomicReference<>(List.of());
        works.earendil.pi.agent.core.AgentLoop.StreamFunction toolCallingAssistant = (model, context, options) -> {
            if (calls.getAndIncrement() == 0) {
                return new Message.Assistant(List.of(
                        new Content.ToolCall("call-1", "read",
                                JsonCodec.parse("{\"path\":\"note.txt\"}"), List.of()),
                        new Content.ToolCall("call-2", "read",
                                JsonCodec.parse("{\"path\":\"blocked.txt\"}"), List.of())),
                        model.provider(), model.modelId(), StopReason.TOOL_USE,
                        new Usage(1, 1, 0, 0, 0), null, Instant.now());
            }
            capturedResults.set(context.messages().stream()
                    .filter(Message.ToolResult.class::isInstance)
                    .map(Message.ToolResult.class::cast)
                    .toList());
            return new Message.Assistant(List.of(new Content.Text("done after tool_call extension")),
                    model.provider(), model.modelId(), StopReason.STOP,
                    new Usage(1, 1, 0, 0, 0), null, Instant.now());
        };

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        List.of("read"), null, null, null, toolCallingAssistant, runner)).session();

        session.prompt("trigger tool_call extension");

        assertThat(calls.get()).isEqualTo(2);
        assertThat(capturedResults.get()).hasSize(2);
        Message.ToolResult rewritten = capturedResults.get().stream()
                .filter(result -> result.toolCallId().equals("call-1"))
                .findFirst()
                .orElseThrow();
        Message.ToolResult blocked = capturedResults.get().stream()
                .filter(result -> result.toolCallId().equals("call-2"))
                .findFirst()
                .orElseThrow();
        assertThat(((Content.Text) rewritten.content().getFirst()).text()).contains("rewritten content")
                .doesNotContain("original content");
        assertThat(rewritten.error()).isFalse();
        assertThat(((Content.Text) blocked.content().getFirst()).text()).isEqualTo("Blocked read by extension");
        assertThat(blocked.error()).isTrue();
        assertThat(blocked.details()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) blocked.details()).get("extensionBlocked")).isEqualTo(true);
        assertThat(events).anySatisfy(event -> assertThat(event).contains("before:read:")
                .contains("note.txt"));
        assertThat(events).anySatisfy(event -> assertThat(event).contains("before:read:")
                .contains("blocked.txt"));
        assertThat(events).anySatisfy(event -> assertThat(event).contains("after:read:")
                .contains("rewritten content"));
        assertThat(events).anySatisfy(event -> assertThat(event).contains("after:read:")
                .contains("Blocked read by extension"));
    }

    @Test
    void extensionToolResultCanPatchContentDetailsAndErrorInOrder() throws Exception {
        Path cwd = tempDir.resolve("project_extension_tool_result");
        Path agentDir = tempDir.resolve("agent_extension_tool_result");
        Files.createDirectories(cwd);
        Files.writeString(cwd.resolve("note.txt"), "original tool output");
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        List<String> events = new ArrayList<>();
        ExtensionPlugin first = new ExtensionPlugin() {
            @Override
            public String name() {
                return "first-tool-result-extension";
            }

            @Override
            public ToolResultPatch onToolResult(String toolName, Object input, AgentTool.AgentToolResult result,
                                                ExtensionCommandContext context) {
                if ("read".equals(toolName)) {
                    return ToolResultPatch.of(
                            List.of(new Content.Text("first patch")),
                            Map.of("stage", "first"),
                            true);
                }
                return null;
            }

            @Override
            public void onAfterToolCall(String toolName, String output) {
                events.add("firstAfter:" + toolName + ":" + output);
            }
        };
        ExtensionPlugin second = new ExtensionPlugin() {
            @Override
            public String name() {
                return "second-tool-result-extension";
            }

            @Override
            public ToolResultPatch onToolResult(String toolName, Object input, AgentTool.AgentToolResult result,
                                                ExtensionCommandContext context) {
                if (!"read".equals(toolName)) {
                    return null;
                }
                String current = ((Content.Text) result.content().getFirst()).text();
                return ToolResultPatch.of(
                        List.of(new Content.Text(current + " -> second patch")),
                        Map.of("stage", "second", "sawError", result.error()),
                        false);
            }

            @Override
            public void onAfterToolCall(String toolName, String output) {
                events.add("secondAfter:" + toolName + ":" + output);
            }
        };
        ExtensionRunner runner = new ExtensionRunner(List.of(first, second));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<Message.ToolResult> capturedResult = new AtomicReference<>();
        works.earendil.pi.agent.core.AgentLoop.StreamFunction toolCallingAssistant = (model, context, options) -> {
            if (calls.getAndIncrement() == 0) {
                return new Message.Assistant(List.of(new Content.ToolCall("call-1", "read",
                        JsonCodec.parse("{\"path\":\"note.txt\"}"), List.of())),
                        model.provider(), model.modelId(), StopReason.TOOL_USE,
                        new Usage(1, 1, 0, 0, 0), null, Instant.now());
            }
            context.messages().stream()
                    .filter(Message.ToolResult.class::isInstance)
                    .map(Message.ToolResult.class::cast)
                    .findFirst()
                    .ifPresent(capturedResult::set);
            return new Message.Assistant(List.of(new Content.Text("done after tool_result extension")),
                    model.provider(), model.modelId(), StopReason.STOP,
                    new Usage(1, 1, 0, 0, 0), null, Instant.now());
        };

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        List.of("read"), null, null, null, toolCallingAssistant, runner)).session();

        session.prompt("trigger tool_result extension");

        assertThat(calls.get()).isEqualTo(2);
        assertThat(capturedResult.get()).isNotNull();
        assertThat(((Content.Text) capturedResult.get().content().getFirst()).text())
                .isEqualTo("first patch -> second patch");
        assertThat(capturedResult.get().error()).isFalse();
        assertThat(capturedResult.get().details()).isInstanceOf(Map.class);
        Map<?, ?> details = (Map<?, ?>) capturedResult.get().details();
        assertThat(details.get("stage")).isEqualTo("second");
        assertThat(details.get("sawError")).isEqualTo(true);
        assertThat(events).containsExactly(
                "firstAfter:read:first patch -> second patch",
                "secondAfter:read:first patch -> second patch");
    }

    @Test
    void emitsExtensionCompactHooksDuringManualCompaction() throws Exception {
        Path cwd = tempDir.resolve("project_extension_compact_hooks");
        Path agentDir = tempDir.resolve("agent_extension_compact_hooks");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        List<String> events = new ArrayList<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "compact-extension";
            }

            @Override
            public void onBeforeCompact(int tokensBefore, int summarizedMessages, int turnPrefixMessages) {
                events.add("beforeCompact:" + tokensBefore + ":" + summarizedMessages + ":" + turnPrefixMessages);
            }

            @Override
            public void onAfterCompact(String entryId, String summary) {
                events.add("afterCompact:" + entryId + ":" + summary);
            }
        };
        ExtensionRunner runner = new ExtensionRunner(List.of(plugin));
        Model largeContextModel = new Model("openai", "gpt-compact", "GPT Compact", "openai-chat",
                128000, 100, true, false, Map.of());

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, largeContextModel, null, List.of(),
                        null, null, null, null, assistant("compact hook answer"), runner)).session();

        session.prompt("first compact prompt");
        session.prompt("second compact prompt");
        AgentSession.CompactionResult result = session.compactNow();

        assertThat(result.compacted()).isTrue();
        assertThat(result.entryId()).isNotBlank();
        assertThat(result.summary()).contains("compact hook answer");
        assertThat(events).contains("beforeCompact:" + result.tokensBefore() + ":"
                + result.summarizedMessages() + ":" + result.turnPrefixMessages());
        assertThat(events).anySatisfy(event -> assertThat(event)
                .startsWith("afterCompact:" + result.entryId() + ":")
                .contains("compact hook answer"));
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
    void rawPromptDoesNotExpandSkillCommands() throws Exception {
        Path cwd = tempDir.resolve("project_raw_prompt");
        Path agentDir = tempDir.resolve("agent_raw_prompt");
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

        session.promptRaw("/skill:demo keep literal");

        assertThat(userText(session.messages().getFirst())).isEqualTo("/skill:demo keep literal");
        assertThat(events).noneSatisfy(event ->
                assertThat(event).isInstanceOf(AgentSession.AgentSessionEvent.SkillCommand.class));
    }

    @Test
    void userBashResultsCanBeIncludedOrExcludedFromContextAfterRestore() throws Exception {
        Path cwd = tempDir.resolve("project_user_bash");
        Path agentDir = tempDir.resolve("agent_user_bash");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.create(cwd, tempDir.resolve("sessions_user_bash"));
        AtomicReference<Context> capturedContext = new AtomicReference<>();

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok", capturedContext))).session();

        session.executeBash("printf included-output", null, false);
        session.executeBash("printf excluded-output", null, true);
        session.prompt("after bash");

        String contextText = contextText(capturedContext.get());
        assertThat(contextText).contains("Ran `printf included-output`").contains("included-output");
        assertThat(contextText).doesNotContain("excluded-output");
        assertThat(sessionManager.entries().stream()
                .filter(SessionEntry.CustomMessageEntry.class::isInstance)
                .map(SessionEntry.CustomMessageEntry.class::cast)
                .toList())
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.customType()).isEqualTo("bashExecution"));

        AtomicReference<Context> restoredContext = new AtomicReference<>();
        AgentSession restored = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services,
                        SessionManager.open(sessionManager.sessionFile().orElseThrow()), null, null, List.of(),
                        null, null, null, null, assistant("restored", restoredContext))).session();

        restored.prompt("after restore");

        String restoredText = contextText(restoredContext.get());
        assertThat(restoredText).contains("Ran `printf included-output`").contains("included-output");
        assertThat(restoredText).doesNotContain("excluded-output");
    }

    @Test
    void extensionCanInterceptUserBashWithResultOrOperations() throws Exception {
        Path cwd = tempDir.resolve("project_user_bash_extension");
        Path agentDir = tempDir.resolve("agent_user_bash_extension");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir);
        SessionManager sessionManager = SessionManager.inMemory(cwd);
        AtomicReference<String> seenEvent = new AtomicReference<>();
        AtomicReference<String> streamedChunk = new AtomicReference<>();
        ExtensionPlugin plugin = new ExtensionPlugin() {
            @Override
            public String name() {
                return "user-bash-extension";
            }

            @Override
            public UserBashResult onUserBash(String command, boolean excludeFromContext, Path cwd) {
                seenEvent.set(command + ":" + excludeFromContext + ":" + cwd.toAbsolutePath().normalize());
                if ("direct".equals(command)) {
                    return UserBashResult.result(new BashExecutor.Result("extension-result", 0, false,
                            false, null));
                }
                if ("wrapped".equals(command)) {
                    return UserBashResult.operations((cmd, workingDir, options) -> {
                        options.onData().accept(("wrapped:" + cmd + ":" + workingDir.getFileName()).getBytes(
                                java.nio.charset.StandardCharsets.UTF_8));
                        return new BashOperations.Result(5);
                    });
                }
                return null;
            }
        };
        ExtensionRunner extensionRunner = new ExtensionRunner(List.of(plugin));

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"), extensionRunner)).session();

        BashExecutor.Result direct = session.executeBash("direct", null, false);
        BashExecutor.Result wrapped = session.executeBash("wrapped", streamedChunk::set, true);

        assertThat(direct.output()).isEqualTo("extension-result");
        assertThat(direct.exitCode()).isEqualTo(0);
        assertThat(wrapped.output()).contains("wrapped:wrapped:project_user_bash_extension");
        assertThat(wrapped.exitCode()).isEqualTo(5);
        assertThat(streamedChunk).hasValue("wrapped:wrapped:project_user_bash_extension");
        assertThat(seenEvent).hasValue("wrapped:true:" + cwd.toAbsolutePath().normalize());
        assertThat(sessionManager.entries().stream()
                .filter(SessionEntry.CustomMessageEntry.class::isInstance)
                .map(SessionEntry.CustomMessageEntry.class::cast)
                .toList())
                .hasSize(2)
                .allSatisfy(entry -> assertThat(entry.customType()).isEqualTo("bashExecution"));
    }

    @Test
    void userBashAppliesShellCommandPrefixButRecordsOriginalCommand() throws Exception {
        Path cwd = tempDir.resolve("project_user_bash_prefix");
        Path agentDir = tempDir.resolve("agent_user_bash_prefix");
        Files.createDirectories(cwd);
        AgentSessionServices services = services(cwd, agentDir, SettingsManager.inMemory(Map.of(
                "shellCommandPrefix", "export PI_PREFIX_MARK=from-prefix"
        )));
        SessionManager sessionManager = SessionManager.inMemory(cwd);

        AgentSession session = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(services, sessionManager, null, null, List.of(),
                        null, null, null, null, assistant("ok"))).session();

        BashExecutor.Result result = session.executeBash("printf $PI_PREFIX_MARK", null, false);

        assertThat(result.output()).isEqualTo("from-prefix");
        SessionEntry.CustomMessageEntry bashEntry = sessionManager.entries().stream()
                .filter(SessionEntry.CustomMessageEntry.class::isInstance)
                .map(SessionEntry.CustomMessageEntry.class::cast)
                .findFirst()
                .orElseThrow();
        assertThat(bashEntry.content().path("command").asText()).isEqualTo("printf $PI_PREFIX_MARK");
        assertThat(bashEntry.content().path("output").asText()).isEqualTo("from-prefix");
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

        AgentSessionRuntime.ReplacementResult reloaded = runtime.reloadCurrent("reload");
        AgentSessionRuntime.ReplacementResult forked = runtime.fork(userEntryId, AgentSessionRuntime.ForkPosition.BEFORE);
        AgentSessionRuntime.ReplacementResult created = runtime.newSession(firstSessionFile);
        AgentSessionRuntime.ReplacementResult switched = runtime.switchSession(firstSessionFile, null);
        Path importedSource = tempDir.resolve("imported.jsonl");
        runtime.session().sessionManager().copySessionFile(importedSource);
        AgentSessionRuntime.ReplacementResult imported = runtime.importFromJsonl(importedSource, null);

        assertThat(reloaded.previousSessionFile()).isEqualTo(firstSessionFile);
        assertThat(reloaded.currentSessionFile()).isEqualTo(firstSessionFile);
        assertThat(forked.selectedText()).isEqualTo("first");
        assertThat(created.previousSessionFile()).isNotNull();
        assertThat(switched.currentSessionFile()).isEqualTo(firstSessionFile);
        assertThat(imported.currentSessionFile()).isEqualTo(sessionDir.resolve(importedSource.getFileName()));
        assertThat(rebinds.get()).isEqualTo(5);
        assertThat(harness.creations()).isGreaterThanOrEqualTo(6);
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

    private static String contextText(Context context) {
        StringBuilder text = new StringBuilder();
        for (Message message : context.messages()) {
            if (message instanceof Message.User user) {
                for (Content content : user.content()) {
                    if (content instanceof Content.Text t) {
                        text.append(t.text()).append('\n');
                    }
                }
            }
        }
        return text.toString();
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
