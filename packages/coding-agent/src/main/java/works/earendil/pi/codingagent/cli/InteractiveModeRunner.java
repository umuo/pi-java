package works.earendil.pi.codingagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.ImageGenModel;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.provider.ImageGenerationOptions;
import works.earendil.pi.ai.provider.ImageGenerationRegistry;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.AuthStorage;
import works.earendil.pi.codingagent.core.BashExecutor;
import works.earendil.pi.codingagent.core.FooterDataProvider;
import works.earendil.pi.codingagent.core.GrillMeInterview;
import works.earendil.pi.codingagent.core.ModelRegistry;
import works.earendil.pi.codingagent.core.SkillDiagnosticHistory;
import works.earendil.pi.codingagent.core.SlashCommands;
import works.earendil.pi.codingagent.core.TeamworkPreview;
import works.earendil.pi.codingagent.core.Timings;
import works.earendil.pi.codingagent.core.extensions.ExtensionCommandContext;
import works.earendil.pi.codingagent.core.export.HtmlExporter;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.resources.PromptTemplate;
import works.earendil.pi.codingagent.resources.PromptTemplateLoader;
import works.earendil.pi.codingagent.resources.ResourceLoader;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.resources.ThemeResource;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.codingagent.tools.PathUtils;
import works.earendil.pi.codingagent.util.MimeUtils;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.orchestrator.service.OrchestratorLogTailer;
import works.earendil.pi.orchestrator.service.OrchestratorRuntime;
import works.earendil.pi.orchestrator.service.OrchestratorStatusReporter;
import works.earendil.pi.orchestrator.service.OrchestratorSupervisor;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;
import works.earendil.pi.tui.style.TerminalTheme;

import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

public final class InteractiveModeRunner {
    private static final int DEFAULT_TERMINAL_COLUMNS = 120;
    private static final int DEFAULT_TERMINAL_ROWS = 40;
    private static final String LOGIN_USAGE = "/login [provider] [api-key|env <ENV_VAR>]";
    private static final String LOGOUT_USAGE = "/logout [provider]";
    private static volatile ClipboardWriter clipboardWriter = new SystemClipboardWriter();
    private static volatile ClipboardImageReader clipboardImageReader = new SystemClipboardImageReader();
    private static volatile GistSharer gistSharer = new GhCliGistSharer();

    private InteractiveModeRunner() {
    }

    static void setClipboardWriterForTesting(ClipboardWriter writer) {
        clipboardWriter = writer == null ? new SystemClipboardWriter() : writer;
    }

    static void setClipboardImageReaderForTesting(ClipboardImageReader reader) {
        clipboardImageReader = reader == null ? new SystemClipboardImageReader() : reader;
    }

    static void setGistSharerForTesting(GistSharer sharer) {
        gistSharer = sharer == null ? new GhCliGistSharer() : sharer;
    }

    public static int run(AgentSessionRuntime runtime, CliArgs args) {
        AgentSession session = runtime.session();
        System.out.println("======================================================");
        System.out.println("  Pi Coding Agent CLI - Interactive Console (Java Edition)");
        System.out.println("======================================================");
        String currentModelId = session.model() != null ? session.model().modelId() : "none";
        String currentProvider = session.model() != null ? session.model().provider() : "none";
        System.out.println("Model: " + currentModelId + " | Provider: " + currentProvider);
        System.out.println("Type /help for commands, /exit or /quit to leave.\n");

        try (FooterDataProvider footer = new FooterDataProvider(runtime.services().cwd());
             OrchestratorEventTailer orchestratorEvents = new OrchestratorEventTailer(System.out,
                     () -> OrchestratorRuntime.shared().supervisor());
             OrchestratorLogFollowTailer orchestratorLogs = new OrchestratorLogFollowTailer(System.out,
                     () -> OrchestratorRuntime.shared().storage());
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            refreshFooterProviderCount(runtime, footer);
            GrillMeInterview grillMe = GrillMeInterview.fromSession(session.sessionManager());
            SkillDiagnosticHistory skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());
            while (true) {
                int terminalColumns = terminalColumns();
                System.out.println(fitLineToWidth(statusLine(session, footer), terminalColumns));
                System.out.print("pi> ");
                System.out.flush();
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if ("/exit".equalsIgnoreCase(trimmed) || "/quit".equalsIgnoreCase(trimmed)) {
                    System.out.println("Goodbye!");
                    break;
                }
                if ("/help".equalsIgnoreCase(trimmed)) {
                    printHelp(runtime);
                    continue;
                }
                if (trimmed.startsWith("/")) {
                    String extensionCommandName = SlashCommands.invocationName(trimmed).toLowerCase(Locale.ROOT);
                    if (runtime.session().extensionRunner() != null
                            && runtime.session().extensionRunner().hasCommand(extensionCommandName)) {
                        System.out.println(handleExtensionCommand(runtime, extensionCommandName,
                                SlashCommands.invocationArguments(trimmed)));
                        continue;
                    }
                }
                ExtensionInputApplication inputApplication = applyExtensionInput(runtime, trimmed);
                if (inputApplication.output() != null && !inputApplication.output().isBlank()) {
                    System.out.println(inputApplication.output());
                }
                if (inputApplication.handled()) {
                    continue;
                }
                trimmed = inputApplication.text().trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.startsWith("/")) {
                    String commandName = SlashCommands.invocationName(trimmed).toLowerCase(Locale.ROOT);
                    String commandArguments = SlashCommands.invocationArguments(trimmed);
                    if (commandName.startsWith("skill:")) {
                        if (!runtime.services().settingsManager().getEnableSkillCommands()) {
                            System.out.println("Skill commands are disabled.");
                            continue;
                        }
                        String skillName = commandName.substring("skill:".length());
                        if (loadedSkill(runtime, skillName) == null) {
                            System.out.println("Skill not found: " + skillName);
                            continue;
                        }
                        executePrompt(runtime, session, trimmed, skillDiagnostics);
                        continue;
                    }
                    if ("skill-diagnostics".equals(commandName) || "skill-diagnostic".equals(commandName)) {
                        handleSkillDiagnostics(skillDiagnostics, session, commandArguments);
                        continue;
                    }
                    if ("skill-recommend".equals(commandName) || ("skills".equals(commandName) && commandArguments.trim().startsWith("recommend"))) {
                        String query = "skills".equals(commandName)
                                ? commandArguments.trim().substring("recommend".length()).trim()
                                : commandArguments.trim();
                        handleSkillRecommend(runtime, query);
                        continue;
                    }
                    if ("teamwork-preview".equals(commandName)) {
                        if (TeamworkPreview.shouldExecute(commandArguments)) {
                            System.out.println("Starting teamwork sub-agents...");
                            System.out.println(TeamworkPreview.executeFromServices(session, runtime.services(),
                                    commandArguments).render());
                        } else {
                            System.out.println(TeamworkPreview.fromServices(session, runtime.services(), commandArguments).render());
                        }
                        continue;
                    }
                    if ("grill-me".equals(commandName)) {
                        handleGrillMe(runtime, session, grillMe, commandArguments, skillDiagnostics);
                        continue;
                    }
                    if ("orchestrator-status".equals(commandName)) {
                        System.out.println(renderOrchestratorStatus(commandArguments, orchestratorEvents,
                                orchestratorLogs, skillDiagnostics));
                        continue;
                    }
                    if ("export".equals(commandName)) {
                        System.out.println(handleExport(session, runtime.services().cwd(), commandArguments));
                        continue;
                    }
                    if ("share".equals(commandName)) {
                        System.out.println(handleShare(session, commandArguments, gistSharer));
                        continue;
                    }
                    if ("settings".equals(commandName)) {
                        System.out.println(handleSettings(runtime.services().settingsManager(), commandArguments));
                        continue;
                    }
                    if ("theme".equals(commandName)) {
                        System.out.println(handleTheme(runtime.services().settingsManager(),
                                runtime.services().resourceLoader(), commandArguments));
                        continue;
                    }
                    if ("prompt".equals(commandName) || "prompts".equals(commandName)) {
                        PromptCommandResult result = handlePrompt(runtime.services().resourceLoader(), commandArguments);
                        System.out.println(result.message());
                        if (result.promptToExecute() != null && !result.promptToExecute().isBlank()) {
                            executePrompt(runtime, session, result.promptToExecute(), skillDiagnostics);
                        }
                        continue;
                    }
                    if ("login".equals(commandName)) {
                        System.out.println(handleLogin(runtime.services().authStorage(),
                                runtime.services().modelRegistry(), commandArguments));
                        refreshFooterProviderCount(runtime, footer);
                        continue;
                    }
                    if ("logout".equals(commandName)) {
                        System.out.println(handleLogout(runtime.services().authStorage(), commandArguments));
                        refreshFooterProviderCount(runtime, footer);
                        continue;
                    }
                    if ("copy".equals(commandName)) {
                        System.out.println(handleCopy(session, clipboardWriter));
                        continue;
                    }
                    if ("paste-image".equals(commandName) || "pasteimage".equals(commandName)) {
                        System.out.println(handlePasteImage(runtime.services().cwd(), commandArguments,
                                clipboardImageReader));
                        continue;
                    }
                    if ("image".equals(commandName) || "images".equals(commandName)) {
                        System.out.println(handleImageCommand(runtime.services().cwd(),
                                runtime.services().authStorage(), commandArguments, defaultImageGenerationRegistry()));
                        continue;
                    }
                    if ("name".equals(commandName)) {
                        System.out.println(handleName(session, commandArguments));
                        continue;
                    }
                    if ("session".equals(commandName)) {
                        System.out.println(handleSession(session, commandArguments));
                        continue;
                    }
                    if ("import".equals(commandName)) {
                        SessionReplacement replacement = handleImport(runtime, session, commandArguments);
                        System.out.println(replacement.message());
                        if (replacement.session() != null) {
                            session = replacement.session();
                            grillMe = GrillMeInterview.fromSession(session.sessionManager());
                            skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());
                            refreshFooterProviderCount(runtime, footer);
                        }
                        continue;
                    }
                    if ("tree".equals(commandName)) {
                        System.out.println(renderSessionTree(session));
                        continue;
                    }
                    if ("fork".equals(commandName)) {
                        SessionReplacement replacement = handleFork(runtime, commandArguments);
                        System.out.println(replacement.message());
                        if (replacement.session() != null) {
                            session = replacement.session();
                            grillMe = GrillMeInterview.fromSession(session.sessionManager());
                            skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());
                            refreshFooterProviderCount(runtime, footer);
                        }
                        continue;
                    }
                    if ("clone".equals(commandName)) {
                        SessionReplacement replacement = handleClone(runtime, session);
                        System.out.println(replacement.message());
                        if (replacement.session() != null) {
                            session = replacement.session();
                            grillMe = GrillMeInterview.fromSession(session.sessionManager());
                            skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());
                            refreshFooterProviderCount(runtime, footer);
                        }
                        continue;
                    }
                    if ("new".equals(commandName)) {
                        SessionReplacement replacement = handleNew(runtime, session, commandArguments);
                        System.out.println(replacement.message());
                        if (replacement.session() != null) {
                            session = replacement.session();
                            grillMe = GrillMeInterview.fromSession(session.sessionManager());
                            skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());
                            refreshFooterProviderCount(runtime, footer);
                        }
                        continue;
                    }
                    if ("compact".equals(commandName)) {
                        System.out.println(handleCompact(session, commandArguments));
                        continue;
                    }
                    if ("resume".equals(commandName)) {
                        SessionReplacement replacement = handleResume(runtime, session, commandArguments);
                        System.out.println(replacement.message());
                        if (replacement.session() != null) {
                            session = replacement.session();
                            grillMe = GrillMeInterview.fromSession(session.sessionManager());
                            skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());
                            refreshFooterProviderCount(runtime, footer);
                        }
                        continue;
                    }
                    if ("reload".equals(commandName)) {
                        SessionReplacement replacement = handleReload(runtime);
                        System.out.println(replacement.message());
                        if (replacement.session() != null) {
                            session = replacement.session();
                            grillMe = GrillMeInterview.fromSession(session.sessionManager());
                            skillDiagnostics = SkillDiagnosticHistory.fromSession(session.sessionManager());
                            refreshFooterProviderCount(runtime, footer);
                        }
                        continue;
                    }
                }
                String normalized = trimmed.toLowerCase(Locale.ROOT);
                if ("/models".equals(normalized)) {
                    printModels(runtime);
                    continue;
                }
                if ("/models refresh".equals(normalized) || normalized.startsWith("/models refresh ")) {
                    String provider = trimmed.length() > "/models refresh".length()
                            ? trimmed.substring("/models refresh".length()).trim()
                            : "";
                    if (!runtime.services().modelRegistry().refresh(provider)) {
                        System.out.println("Provider not found: " + provider);
                        continue;
                    }
                    refreshFooterProviderCount(runtime, footer);
                    System.out.println(provider.isBlank()
                            ? "Models refreshed."
                            : "Models refreshed for provider: " + provider);
                    printModels(runtime);
                    continue;
                }
                if (trimmed.startsWith("/model ")) {
                    String target = trimmed.substring(7).trim();
                    var found = runtime.services().modelRegistry().getAll().stream()
                            .filter(m -> m.modelId().equals(target) || (m.provider() + "/" + m.modelId()).equals(target) || (m.provider() + ":" + m.modelId()).equals(target))
                            .findFirst();
                    if (found.isPresent()) {
                        session.setModel(found.get());
                        System.out.println("Switched model to: [" + found.get().provider() + "] " + found.get().modelId());
                    } else {
                        System.out.println("Model not found: " + target + ". Type /models to see available list.");
                    }
                    continue;
                }
                if ("/clear".equalsIgnoreCase(trimmed)) {
                    System.out.print("\033[H\033[2J");
                    System.out.flush();
                    continue;
                }
                if (trimmed.startsWith("!")) {
                    System.out.println(handleUserBash(session, trimmed));
                    continue;
                }

                String expandedPrompt = PromptTemplateLoader.expandPromptTemplate(trimmed,
                        runtime.services().resourceLoader().prompts());
                executePrompt(runtime, session, expandedPrompt, skillDiagnostics);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Interactive session error: " + e.getMessage());
            return 1;
        }
    }

    private record ExtensionInputApplication(String text, String output, boolean handled) {}

    private record PromptCommandResult(String message, String promptToExecute) {}

    private static ExtensionInputApplication applyExtensionInput(AgentSessionRuntime runtime, String text) {
        if (runtime.session().extensionRunner() == null) {
            return new ExtensionInputApplication(text, null, false);
        }
        return runtime.session().extensionRunner()
                .emitInput(text, new ExtensionCommandContext(runtime.session(), "", text,
                        ExtensionCommandContext.UiContext.tui(terminalColumns(), terminalRows())))
                .map(result -> new ExtensionInputApplication(
                        result.text() == null ? text : result.text(), result.output(), result.handled()))
                .orElseGet(() -> new ExtensionInputApplication(text, null, false));
    }

    static void executePrompt(AgentSessionRuntime runtime, AgentSession session, String prompt) {
        executePrompt(runtime, session, prompt, null);
    }

    private static void executePrompt(AgentSessionRuntime runtime, AgentSession session, String prompt,
                                      SkillDiagnosticHistory skillDiagnostics) {
        executePrompt(runtime, session, prompt, skillDiagnostics, false);
    }

    private static List<AgentMessage> executePrompt(AgentSessionRuntime runtime, AgentSession session, String prompt,
                                                    SkillDiagnosticHistory skillDiagnostics, boolean rawPrompt) {
        return executePrompt(runtime, session, skillDiagnostics,
                () -> rawPrompt ? session.promptRaw(prompt) : session.prompt(prompt));
    }

    private static List<AgentMessage> executePrompt(AgentSessionRuntime runtime, AgentSession session,
                                                    List<Content> content, String source) {
        return executePrompt(runtime, session, null, () -> session.promptRaw(content, source));
    }

    private static List<AgentMessage> executePrompt(AgentSessionRuntime runtime, AgentSession session,
                                                    SkillDiagnosticHistory skillDiagnostics, PromptRunner runner) {
        StringBuilder assistantBuffer = new StringBuilder();
        TerminalTheme outputTheme = TerminalThemeResolver.resolve(runtime.services().settingsManager(),
                runtime.services().resourceLoader());
        AutoCloseable unsubscribe = session.subscribe(event -> {
            if (event instanceof AgentSession.AgentSessionEvent.AgentEventEnvelope env) {
                if (env.event() instanceof AgentEvent.MessageUpdate mu &&
                        mu.assistantMessageEvent() instanceof AssistantMessageEvent.ContentDelta cd &&
                        cd.content() instanceof Content.Text t) {
                    assistantBuffer.append(t.text());
                } else if (env.event() instanceof AgentEvent.MessageEnd end &&
                        end.message() instanceof AgentMessage.Llm llm &&
                        llm.message() instanceof Message.Assistant assistant) {
                    String text = assistantBuffer.length() == 0
                            ? InteractiveOutputRenderer.textFromContent(assistant.content())
                            : assistantBuffer.toString();
                    InteractiveOutputRenderer.renderAssistantText(System.out, text, terminalColumns(), outputTheme);
                    assistantBuffer.setLength(0);
                } else if (env.event() instanceof AgentEvent.ToolExecutionStart toolStart) {
                    InteractiveOutputRenderer.renderToolStart(System.out, toolStart.toolName(), toolStart.args(),
                            runtime.services().cwd(), terminalColumns(), outputTheme);
                } else if (env.event() instanceof AgentEvent.ToolExecutionEnd toolEnd) {
                    Message.ToolResult result = new Message.ToolResult(toolEnd.toolCallId(), toolEnd.toolName(),
                            toolEnd.result().content(), toolEnd.error(), toolEnd.result().details(),
                            java.time.Instant.now());
                    InteractiveOutputRenderer.renderToolResult(System.out, result, terminalColumns(), outputTheme);
                }
            } else if (event instanceof AgentSession.AgentSessionEvent.SkillTriggerDiagnostic diagnostic) {
                if (skillDiagnostics != null) {
                    skillDiagnostics.record(diagnostic);
                    persistSkillDiagnostics(skillDiagnostics, session);
                }
                InteractiveOutputRenderer.renderSkillTriggerDiagnostic(System.out, diagnostic, terminalColumns());
            } else if (event instanceof AgentSession.AgentSessionEvent.QueueUpdate update) {
                InteractiveOutputRenderer.renderQueueUpdate(System.out, update, terminalColumns());
            }
        });

        try {
            Timings turnTimings = new Timings(true);
            turnTimings.resetTimings("turn");
            long startNanos = System.nanoTime();
            List<AgentMessage> result = runner.run();
            turnTimings.time("agent", "turn");
            System.out.println(turnLine(session.stats(), System.nanoTime() - startNanos,
                    turnTimings.timings("turn"), terminalColumns()));
            return result;
        } catch (Exception e) {
            System.err.println("\nError executing prompt: " + e.getMessage());
            return List.of();
        } finally {
            try {
                unsubscribe.close();
            } catch (Exception ignored) {
            }
        }
    }

    @FunctionalInterface
    private interface PromptRunner {
        List<AgentMessage> run() throws Exception;
    }

    static String statusLine(AgentSession session, FooterDataProvider footer) {
        AgentSession.SessionStats stats = session.stats();
        return "status | branch: " + displayValue(footer.getGitBranch())
                + " | model: " + modelRef(session.model())
                + " | msgs: u" + stats.userMessages() + "/a" + stats.assistantMessages() + "/t" + stats.toolResults()
                + " | tokens: " + stats.totalTokens()
                + " | providers: " + footer.getAvailableProviderCount();
    }

    static String turnLine(AgentSession.SessionStats stats, long elapsedNanos) {
        return turnLine(stats, elapsedNanos, List.of());
    }

    static String turnLine(AgentSession.SessionStats stats, long elapsedNanos, List<Timings.Timing> timings) {
        return turnLine(stats, elapsedNanos, timings, DEFAULT_TERMINAL_COLUMNS);
    }

    static String turnLine(AgentSession.SessionStats stats, long elapsedNanos, List<Timings.Timing> timings,
                           int maxWidth) {
        long elapsedMillis = Math.max(0, TimeUnit.NANOSECONDS.toMillis(elapsedNanos));
        String line = "turn | elapsed: " + elapsedMillis + "ms"
                + " | msgs: u" + stats.userMessages() + "/a" + stats.assistantMessages() + "/t" + stats.toolResults()
                + " | tokens: " + stats.totalTokens()
                + formatTimings(timings);
        return fitLineToWidth(line, maxWidth);
    }

    static String fitLineToWidth(String line, int maxWidth) {
        if (line == null) {
            return "";
        }
        if (maxWidth <= 0 || EastAsianWidth.visibleWidth(line) <= maxWidth) {
            return line;
        }
        if (maxWidth <= 3) {
            return EastAsianWidth.truncateToWidth(line, maxWidth);
        }
        return EastAsianWidth.truncateToWidth(line, maxWidth - 3) + "...";
    }

    private static String formatTimings(List<Timings.Timing> timings) {
        List<Timings.Timing> printable = timings == null ? List.of() : timings.stream()
                .filter(timing -> timing.ms() >= 0)
                .toList();
        if (printable.isEmpty()) {
            return "";
        }
        long total = 0;
        StringBuilder builder = new StringBuilder(" | timings: ");
        for (int i = 0; i < printable.size(); i++) {
            Timings.Timing timing = printable.get(i);
            total += timing.ms();
            if (i > 0) {
                builder.append(",");
            }
            builder.append(timing.label()).append("=").append(timing.ms()).append("ms");
        }
        builder.append(",total=").append(total).append("ms");
        return builder.toString();
    }

    private static String modelRef(Model model) {
        if (model == null) {
            return "none";
        }
        return displayValue(model.provider()) + "/" + displayValue(model.modelId());
    }

    private static String displayValue(String value) {
        return value == null || value.isBlank() ? "none" : value;
    }

    private static void refreshFooterProviderCount(AgentSessionRuntime runtime, FooterDataProvider footer) {
        long providerCount = runtime.services().modelRegistry().getAvailable().stream()
                .map(Model::provider)
                .distinct()
                .count();
        footer.setAvailableProviderCount((int) providerCount);
    }

    private static int terminalColumns() {
        if (System.console() == null) {
            return DEFAULT_TERMINAL_COLUMNS;
        }
        String columns = System.getenv("COLUMNS");
        if (columns == null || columns.isBlank()) {
            return DEFAULT_TERMINAL_COLUMNS;
        }
        try {
            return Math.max(20, Integer.parseInt(columns.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_TERMINAL_COLUMNS;
        }
    }

    private static int terminalRows() {
        if (System.console() == null) {
            return DEFAULT_TERMINAL_ROWS;
        }
        String rows = System.getenv("LINES");
        if (rows == null || rows.isBlank()) {
            return DEFAULT_TERMINAL_ROWS;
        }
        try {
            return Math.max(10, Integer.parseInt(rows.trim()));
        } catch (NumberFormatException ignored) {
            return DEFAULT_TERMINAL_ROWS;
        }
    }

    private static Skill loadedSkill(AgentSessionRuntime runtime, String skillName) {
        return runtime.services().resourceLoader().skills().skills().stream()
                .filter(skill -> skill.name().equals(skillName))
                .findFirst()
                .orElse(null);
    }

    private static void printHelp(AgentSessionRuntime runtime) {
        System.out.println("Available commands:");
        System.out.println("  /help           Show this help message");
        System.out.println("  /models         List available providers and models");
        System.out.println("  /models refresh [provider] Refresh discovered models and list them");
        System.out.println("  /model <id>     Switch model (e.g. /model deepseek-v4-flash)");
        System.out.println("  /settings [json|get|set|unset] View or update settings");
        System.out.println("  /prompt [list|preview|run] List, preview, or run loaded prompt templates");
        System.out.println("  /theme [list|current|set|preview] List, switch, or preview loaded themes");
        System.out.println("  " + LOGIN_USAGE + " Configure API key or registered OAuth authentication");
        System.out.println("  " + LOGOUT_USAGE + " List auth sources or remove stored/runtime provider authentication");
        System.out.println("  /export [path]  Export session as HTML, or copy raw JSONL when path ends with .jsonl");
        System.out.println("  /share [public|secret] Share session HTML as a GitHub gist via gh");
        System.out.println("  /copy           Copy the last assistant message to clipboard");
        System.out.println("  /paste-image [path] Save clipboard image and print an @path you can submit");
        System.out.println("  /image [list|generate] List image models or generate images to files");
        System.out.println("  /name [text|clear] Show, set, or clear the current session name");
        System.out.println("  /session        Show current session info and stats");
        System.out.println("  /import <path>  Import a JSONL session file and resume it in the current project");
        System.out.println("  /tree           Show the current session branch tree and entry ids");
        System.out.println("  /fork [before|at] <entryId> Fork the current session at an entry id");
        System.out.println("  /clone          Clone the current active branch into a new session");
        System.out.println("  /new [name]     Start a new session, optionally with a display name");
        System.out.println("  /compact        Manually compact the current session context");
        System.out.println("  /resume [--all|find] List, search, resume, rename, or delete sessions");
        System.out.println("  /reload         Reload settings, auth, models, resources, and extensions");
        System.out.println("  /grill-me [topic] Start an interview before design/implementation");
        System.out.println("  /grill-me answer <text> Record an interview answer and continue");
        System.out.println("  /grill-me status|reset Show or clear the active interview");
        System.out.println("  /skill-diagnostics [history|json|sources|picker|inspect|clear] [branch=<entryId>] [filters] Show, inspect, export, or clear skill trigger diagnostics");
        System.out.println("  /skill-recommend [query] [reason=<text>] [limit=<n>] Search and recommend loaded skills");
        System.out.println("  /teamwork-preview [compact] Preview planned sub-agent roles");
        System.out.println("  /teamwork-preview run <objective> Execute planned sub-agents");
        System.out.println("  /orchestrator-status Show instances, logs, settings, and event stream status");
        System.out.println("  /orchestrator-status dashboard [instanceId] [events] [filters] Show instances, stderr, RPC events, and skill diagnostics");
        System.out.println("  /orchestrator-status tail [instanceId] [lines] Show recent stderr log lines");
        System.out.println("  /orchestrator-status tail --follow [instanceId] Subscribe to stderr log lines");
        System.out.println("  /orchestrator-status events [instanceId|stop] Subscribe to live RPC events");
        System.out.println("  !<cmd>          Run bash command and include the result in context");
        System.out.println("  !!<cmd>         Run bash command without adding the result to model context");
        System.out.println("  /clear          Clear terminal screen");
        System.out.println("  /exit, /quit    Exit interactive console");
        List<SlashCommands.SlashCommandInfo> extensionCommands = runtime.session().extensionRunner() == null
                ? List.of()
                : runtime.session().extensionRunner().collectCommands();
        if (!extensionCommands.isEmpty()) {
            System.out.println("Loaded extension commands:");
            for (SlashCommands.SlashCommandInfo command : extensionCommands) {
                System.out.println("  /" + command.name() + " " + command.description());
            }
        }
        List<SlashCommands.SlashCommandInfo> promptCommands = SlashCommands.promptCommands(
                runtime.services().resourceLoader().prompts());
        if (!promptCommands.isEmpty()) {
            System.out.println("Loaded prompt templates:");
            for (SlashCommands.SlashCommandInfo command : promptCommands) {
                System.out.println("  /" + command.name() + " " + command.description());
            }
        }
        List<SlashCommands.SlashCommandInfo> skillCommands = SlashCommands.skillCommands(
                runtime.services().resourceLoader().skills().skills());
        if (!skillCommands.isEmpty()) {
            System.out.println("Loaded skills:");
            for (SlashCommands.SlashCommandInfo command : skillCommands) {
                System.out.println("  /" + command.name() + " " + command.description());
            }
        }
    }

    private static String handleExtensionCommand(AgentSessionRuntime runtime, String commandName, String arguments) {
        if (runtime.session().extensionRunner() == null) {
            return "Extension command\nstatus: not found\ncommand: " + commandName;
        }
        try {
            return runtime.session().extensionRunner()
                    .executeCommand(commandName, arguments,
                            new ExtensionCommandContext(runtime.session(), commandName, arguments,
                                    (content, source) -> executePrompt(runtime, runtime.session(), content, source),
                                    ExtensionCommandContext.UiContext.tui(
                                            terminalColumns(), terminalRows())))
                    .orElse("Extension command\nstatus: completed\ncommand: " + commandName);
        } catch (Exception e) {
            return "Extension command\nstatus: error\ncommand: " + commandName + "\nerror: " + e.getMessage();
        }
    }

    private static String handleUserBash(AgentSession session, String line) {
        boolean excludeFromContext = line.startsWith("!!");
        String command = line.substring(excludeFromContext ? 2 : 1).trim();
        if (command.isBlank()) {
            return "Bash command\nstatus: error\nusage: !<cmd> or !!<cmd>";
        }
        try {
            BashExecutor.Result result = session.executeBash(command, null, excludeFromContext);
            StringBuilder out = new StringBuilder();
            out.append("Bash command\n");
            out.append("status: completed\n");
            out.append("command: ").append(command).append("\n");
            out.append("context: ").append(excludeFromContext ? "excluded" : "included").append("\n");
            out.append("exitCode: ").append(result.exitCode() == null ? "none" : result.exitCode()).append("\n");
            out.append("truncated: ").append(result.truncated()).append("\n");
            if (result.fullOutputPath() != null) {
                out.append("fullOutputPath: ").append(result.fullOutputPath()).append("\n");
            }
            out.append("output:\n");
            out.append(result.output() == null || result.output().isEmpty() ? "(no output)" : result.output().stripTrailing());
            return out.toString();
        } catch (Exception e) {
            return "Bash command\nstatus: error\ncommand: " + command + "\nerror: " + e.getMessage();
        }
    }

    private static void handleGrillMe(AgentSessionRuntime runtime, AgentSession session, GrillMeInterview interview,
                                      String arguments, SkillDiagnosticHistory skillDiagnostics) {
        String command = arguments == null ? "" : arguments.trim();
        if ("status".equalsIgnoreCase(command)) {
            System.out.println(interview.status());
            return;
        }
        if ("reset".equalsIgnoreCase(command) || "stop".equalsIgnoreCase(command)) {
            System.out.println(interview.reset());
            persistGrillMe(interview, session);
            return;
        }
        if (command.equalsIgnoreCase("answer") || command.toLowerCase(Locale.ROOT).startsWith("answer ")) {
            String answer = command.length() > "answer".length()
                    ? command.substring("answer".length()).trim()
                    : "";
            try {
                System.out.println("Continuing /grill-me interview...");
                executePrompt(runtime, session, interview.answer(answer), skillDiagnostics);
                interview.captureLatestAssistantQuestion(session.sessionManager());
                persistGrillMe(interview, session);
            } catch (IllegalArgumentException | IllegalStateException e) {
                System.out.println("/grill-me interview\nerror: " + e.getMessage());
            }
            return;
        }
        System.out.println("Starting /grill-me interview...");
        executePrompt(runtime, session, interview.start(command), skillDiagnostics);
        interview.captureLatestAssistantQuestion(session.sessionManager());
        persistGrillMe(interview, session);
    }

    private static void handleSkillDiagnostics(SkillDiagnosticHistory history, AgentSession session, String arguments) {
        String command = arguments == null ? "" : arguments.trim();
        if ("clear".equalsIgnoreCase(command) || "reset".equalsIgnoreCase(command)) {
            history.clear();
            persistSkillDiagnostics(history, session);
            System.out.println("Skill trigger diagnostics\nstatus: cleared");
            return;
        }
        if (!command.isBlank()) {
            String[] parts = command.split("\\s+");
            String subcommand = parts[0].toLowerCase(Locale.ROOT);
            if ("history".equals(subcommand) || "list".equals(subcommand)) {
                FilterParseResult parsed = parseSkillDiagnosticFilter(parts, 1);
                if (parsed.error() != null) {
                    System.out.println(parsed.error());
                    return;
                }
                try {
                    SkillDiagnosticHistory scopedHistory = scopedSkillDiagnostics(history, session, parsed.branch());
                    System.out.println(renderSkillDiagnosticHistory(scopedHistory, parsed.filter(), parsed.branch()));
                } catch (IllegalArgumentException e) {
                    System.out.println("Skill trigger diagnostics\nerror: " + e.getMessage());
                }
                return;
            }
            if ("json".equals(subcommand)) {
                FilterParseResult parsed = parseSkillDiagnosticFilter(parts, 1);
                if (parsed.error() != null) {
                    System.out.println(parsed.error());
                    return;
                }
                try {
                    SkillDiagnosticHistory scopedHistory = scopedSkillDiagnostics(history, session, parsed.branch());
                    System.out.println(JsonCodec.mapper().writeValueAsString(scopedHistory.toJson(
                            new SkillDiagnosticHistory.Query(parsed.filter(), 0, 0, "oldest", false),
                            SkillDiagnosticHistory.Source.from(session.sessionManager(), parsed.branch()))));
                } catch (IllegalArgumentException e) {
                    System.out.println("Skill trigger diagnostics\nerror: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Skill trigger diagnostics\nwarning: could not export history: " + e.getMessage());
                }
                return;
            }
            if ("sources".equals(subcommand)) {
                SourceOptions sourceOptions = parseSkillDiagnosticSourceOptions(parts, 1);
                if (sourceOptions.error() != null) {
                    System.out.println(sourceOptions.error());
                    return;
                }
                try {
                    System.out.println(JsonCodec.mapper().writeValueAsString(SkillDiagnosticHistory.sourceIndex(
                            session.sessionManager(), sourceOptions.limit(), sourceOptions.includeEmpty())));
                } catch (IOException e) {
                    System.out.println("Skill trigger diagnostics\nwarning: could not list sources: " + e.getMessage());
                }
                return;
            }
            if ("picker".equals(subcommand)) {
                SourceOptions sourceOptions = parseSkillDiagnosticSourceOptions(parts, 1);
                if (sourceOptions.error() != null) {
                    System.out.println(sourceOptions.error());
                    return;
                }
                try {
                    System.out.println(renderSkillDiagnosticSourcePicker(SkillDiagnosticHistory.sourcePicker(
                            session.sessionManager(), sourceOptions.limit(), sourceOptions.includeEmpty())));
                } catch (IOException e) {
                    System.out.println("Skill trigger diagnostics\nwarning: could not render source picker: " + e.getMessage());
                }
                return;
            }
            if ("inspect".equals(subcommand) || "drilldown".equals(subcommand)) {
                String selector = "";
                int filterStart = 1;
                if (parts.length > 1) {
                    String candidate = parts[1];
                    if (candidate.matches("\\d+") || candidate.startsWith("index=") || candidate.startsWith("branch=") || candidate.startsWith("session=")) {
                        selector = candidate.startsWith("index=") ? candidate.substring(6).trim() : candidate;
                        filterStart = 2;
                    }
                }
                FilterParseResult parsed = parseSkillDiagnosticFilter(parts, filterStart);
                if (parsed.error() != null) {
                    System.out.println(parsed.error());
                    return;
                }
                try {
                    JsonNode inspectNode = SkillDiagnosticHistory.inspect(
                            session.sessionManager(),
                            selector.isBlank() ? parsed.branch() : selector,
                            new SkillDiagnosticHistory.Query(parsed.filter(), 0, 5, "newest", true),
                            50, false);
                    InteractiveOutputRenderer.renderSkillDiagnosticInspect(System.out, inspectNode, terminalColumns());
                } catch (IllegalArgumentException e) {
                    System.out.println("Skill trigger diagnostics\nerror: " + e.getMessage());
                } catch (IOException e) {
                    System.out.println("Skill trigger diagnostics\nwarning: could not inspect diagnostics: " + e.getMessage());
                }
                return;
            }
            System.out.println("Skill trigger diagnostics\nerror: unknown argument: " + command
                    + "\nusage: /skill-diagnostics [history|json|sources|picker|inspect|clear] [branch=<entryId>] [skill=<name>] [model=visible|manual] [reason=<text>]");
            return;
        }
        AgentSession.AgentSessionEvent.SkillTriggerDiagnostic latest = history.latest();
        if (latest == null || latest.matches().isEmpty()) {
            System.out.println("Skill trigger diagnostics\nstatus: no recent matches");
            return;
        }
        System.out.println("Skill trigger diagnostics\nstatus: latest");
        InteractiveOutputRenderer.renderSkillTriggerDiagnostic(System.out, latest, terminalColumns());
    }

    private static void handleSkillRecommend(AgentSessionRuntime runtime, String arguments) {
        String reasonFilter = "";
        int limit = 10;
        boolean filterByReason = false;
        List<String> queryWords = new ArrayList<>();
        if (arguments != null && !arguments.isBlank()) {
            String[] tokens = arguments.split("\\s+");
            for (String token : tokens) {
                if (token.startsWith("reason=")) {
                    reasonFilter = token.substring(7);
                } else if (token.equalsIgnoreCase("filterByReason=true") || token.equalsIgnoreCase("--filter-by-reason")) {
                    filterByReason = true;
                } else if (token.startsWith("limit=")) {
                    try {
                        limit = Integer.parseInt(token.substring(6));
                    } catch (NumberFormatException ignored) {
                    }
                } else {
                    queryWords.add(token);
                }
            }
        }
        String queryText = String.join(" ", queryWords);
        SkillLoader.SkillRecommendationQuery req = new SkillLoader.SkillRecommendationQuery(
                queryText, reasonFilter, filterByReason, true, limit);
        List<works.earendil.pi.codingagent.resources.Skill> loadedSkills = runtime.services().resourceLoader().skills().skills();
        SkillLoader.SkillRecommendationResult res = SkillLoader.recommendSkills(loadedSkills, req);
        InteractiveOutputRenderer.renderSkillRecommendation(System.out, res, terminalColumns());
    }

    private static String renderSkillDiagnosticHistory(SkillDiagnosticHistory history,
                                                       SkillDiagnosticHistory.Filter filter) {
        return renderSkillDiagnosticHistory(history, filter, "");
    }

    private static String renderSkillDiagnosticHistory(SkillDiagnosticHistory history,
                                                       SkillDiagnosticHistory.Filter filter,
                                                       String branch) {
        SkillDiagnosticHistory.Filter normalized = filter == null ? SkillDiagnosticHistory.Filter.empty() : filter;
        List<SkillDiagnosticHistory.Entry> entries = history.entries(normalized);
        StringBuilder out = new StringBuilder();
        out.append("Skill trigger diagnostics\n");
        out.append("status: history\n");
        if (branch != null && !branch.isBlank()) {
            out.append("branch: ").append(branch).append("\n");
        }
        out.append("filter: ").append(normalized.describe()).append("\n");
        out.append("entries: ").append(entries.size());
        if (entries.isEmpty()) {
            return out.toString();
        }
        for (int i = 0; i < entries.size(); i++) {
            SkillDiagnosticHistory.Entry entry = entries.get(i);
            out.append("\n").append(i + 1).append(". at: ").append(entry.capturedAt());
            for (SkillLoader.SkillTriggerMatch match : entry.matches()) {
                out.append("\n   - ").append(match.skillName())
                        .append(" | model: ").append(match.modelVisible() ? "visible" : "manual")
                        .append(" | reasons: ").append(String.join(", ", match.reasons()));
            }
        }
        return out.toString();
    }

    private static String renderSkillDiagnosticSourcePicker(JsonNode picker) {
        StringBuilder out = new StringBuilder();
        out.append("Skill diagnostic source picker\n");
        out.append("items: ").append(picker.path("totalItems").asInt(0));
        for (JsonNode item : picker.path("items")) {
            out.append("\n").append(item.path("title").asText(""));
            String subtitle = item.path("subtitle").asText("");
            if (!subtitle.isBlank()) {
                out.append("\n   ").append(subtitle);
            }
            out.append("\n   branch=").append(item.path("branch").asText(""))
                    .append(" session=").append(item.path("sessionFile").asText(""));
        }
        return out.toString();
    }

    private static FilterParseResult parseSkillDiagnosticFilter(String[] parts, int startIndex) {
        return parseSkillDiagnosticFilter(parts, startIndex,
                "usage: /skill-diagnostics [history|json] [branch=<entryId>] [skill=<name>] [model=visible|manual] [reason=<text>]");
    }

    private static FilterParseResult parseSkillDiagnosticFilter(String[] parts, int startIndex, String usage) {
        String skill = "";
        String model = "";
        String reason = "";
        String branch = "";
        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i];
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                return FilterParseResult.error("Skill trigger diagnostics\nerror: expected filter key=value: " + part
                        + "\n" + usage);
            }
            String key = part.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = part.substring(separator + 1).trim();
            switch (key) {
                case "skill" -> skill = value;
                case "model" -> {
                    String normalized = value.toLowerCase(Locale.ROOT);
                    if (!List.of("visible", "auto", "model", "manual", "hidden").contains(normalized)) {
                        return FilterParseResult.error("Skill trigger diagnostics\nerror: model filter must be visible or manual: "
                                + value + "\n" + usage);
                    }
                    model = normalized;
                }
                case "reason" -> reason = value;
                case "branch" -> branch = value;
                default -> {
                    return FilterParseResult.error("Skill trigger diagnostics\nerror: unknown filter: " + key
                            + "\n" + usage);
                }
            }
        }
        return new FilterParseResult(new SkillDiagnosticHistory.Filter(skill, model, reason), branch, null);
    }

    private static SkillDiagnosticHistory scopedSkillDiagnostics(SkillDiagnosticHistory current,
                                                                AgentSession session,
                                                                String branch) {
        if (branch == null || branch.isBlank()) {
            return current;
        }
        return SkillDiagnosticHistory.fromSession(session.sessionManager(), branch);
    }

    private static SourceOptions parseSkillDiagnosticSourceOptions(String[] parts, int startIndex) {
        int limit = 20;
        boolean includeEmpty = false;
        String usage = "usage: /skill-diagnostics sources|picker [limit=<n>] [includeEmpty=true|false]";
        for (int i = startIndex; i < parts.length; i++) {
            String part = parts[i];
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                return SourceOptions.error("Skill trigger diagnostics\nerror: expected source option key=value: "
                        + part + "\n" + usage);
            }
            String key = part.substring(0, separator).toLowerCase(Locale.ROOT);
            String value = part.substring(separator + 1).trim();
            switch (key) {
                case "limit" -> {
                    try {
                        limit = Math.max(0, Integer.parseInt(value));
                    } catch (NumberFormatException e) {
                        return SourceOptions.error("Skill trigger diagnostics\nerror: limit must be a non-negative integer: "
                                + value + "\n" + usage);
                    }
                }
                case "includeempty" -> includeEmpty = parseBoolean(value);
                default -> {
                    return SourceOptions.error("Skill trigger diagnostics\nerror: unknown source option: " + key
                            + "\n" + usage);
                }
            }
        }
        return new SourceOptions(limit, includeEmpty, null);
    }

    private static boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "1", "true", "yes", "y", "on" -> true;
            default -> false;
        };
    }

    private static void persistGrillMe(GrillMeInterview interview, AgentSession session) {
        try {
            interview.persist(session.sessionManager());
        } catch (IOException e) {
            System.out.println("/grill-me interview\nwarning: could not persist state: " + e.getMessage());
        }
    }

    private static void persistSkillDiagnostics(SkillDiagnosticHistory history, AgentSession session) {
        if (session == null) {
            return;
        }
        try {
            history.persist(session.sessionManager());
        } catch (IOException e) {
            System.out.println("Skill trigger diagnostics\nwarning: could not persist history: " + e.getMessage());
        }
    }

    static String handleSettings(SettingsManager settingsManager, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        try {
            if (trimmed.isEmpty()) {
                return renderSettingsSummary(settingsManager);
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if ("json".equals(lower)) {
                return "Settings\nscope: merged\njson: " + JsonCodec.stringify(settingsManager.load());
            }
            if ("global".equals(lower)) {
                return "Settings\nscope: global\njson: " + JsonCodec.stringify(settingsManager.getGlobalSettings());
            }
            if ("project".equals(lower)) {
                return "Settings\nscope: project\ntrusted: " + settingsManager.isProjectTrusted()
                        + "\njson: " + JsonCodec.stringify(settingsManager.getProjectSettings());
            }
            if (lower.startsWith("get ")) {
                return handleSettingsGet(settingsManager, trimmed.substring(4).trim());
            }
            if (lower.startsWith("set ")) {
                return handleSettingsSet(settingsManager, trimmed.substring(4).trim());
            }
            if (lower.startsWith("unset ")) {
                return handleSettingsUnset(settingsManager, trimmed.substring(6).trim());
            }
            return settingsUsage("unknown settings command: " + trimmed);
        } catch (Exception e) {
            return "Settings\nerror: " + e.getMessage();
        }
    }

    static String handleTheme(SettingsManager settingsManager,
                              ResourceLoader resourceLoader,
                              String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        try {
            if (trimmed.isEmpty()) {
                java.util.List<String> names = availableThemeNames(resourceLoader);
                java.util.List<works.earendil.pi.tui.component.ListSelector.Item> items = new java.util.ArrayList<>();
                String active = TerminalThemeResolver.activeThemeName(settingsManager == null ? null : settingsManager.getThemeSetting());
                for (String name : names) {
                    works.earendil.pi.codingagent.resources.ThemeResource resource = themeByName(resourceLoader, name);
                    String desc = (resource != null && resource.filePath() != null) ? resource.filePath().toString() : "";
                    boolean isSelected = name.equals(active) || (active == null && name.equals("standard"));
                    items.add(new works.earendil.pi.tui.component.ListSelector.Item(name, name, desc, isSelected, false));
                }
                works.earendil.pi.tui.component.ListSelector selector = new works.earendil.pi.tui.component.ListSelector("Select Theme", items, false);
                java.util.List<String> selected = selector.show();
                if (selected != null && !selected.isEmpty()) {
                    return setTheme(settingsManager, resourceLoader, selected.get(0));
                }
                return "Theme selection canceled.";
            }
            if ("list".equalsIgnoreCase(trimmed)) {
                return renderThemeList(settingsManager, resourceLoader);
            }
            if ("current".equalsIgnoreCase(trimmed)) {
                return renderThemeCurrent(settingsManager, resourceLoader);
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.startsWith("set ")) {
                return setTheme(settingsManager, resourceLoader, trimmed.substring(4).trim());
            }
            if (lower.startsWith("preview ")) {
                return previewTheme(resourceLoader, trimmed.substring(8).trim());
            }
            return setTheme(settingsManager, resourceLoader, trimmed);
        } catch (Exception e) {
            return "Theme\nerror: " + e.getMessage();
        }
    }

    static PromptCommandResult handlePrompt(ResourceLoader resourceLoader, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        List<PromptTemplate> templates = resourceLoader == null ? List.of() : resourceLoader.prompts();
        if (trimmed.isEmpty()) {
            if (templates == null || templates.isEmpty()) {
                return new PromptCommandResult("No prompt templates available.", null);
            }
            java.util.List<works.earendil.pi.tui.component.ListSelector.Item> items = new java.util.ArrayList<>();
            for (PromptTemplate template : templates) {
                items.add(new works.earendil.pi.tui.component.ListSelector.Item(template.name(), template.name(), template.description(), false, false));
            }
            works.earendil.pi.tui.component.ListSelector selector = new works.earendil.pi.tui.component.ListSelector("Select Prompt Template", items, false);
            try {
                java.util.List<String> selected = selector.show();
                if (selected != null && !selected.isEmpty()) {
                    return runPromptTemplate(templates, selected.get(0));
                }
                return new PromptCommandResult("Prompt selection canceled.", null);
            } catch (Exception e) {
                return new PromptCommandResult("Error: " + e.getMessage(), null);
            }
        }
        if ("list".equalsIgnoreCase(trimmed)) {
            return new PromptCommandResult(renderPromptList(templates), null);
        }

        HeadTail command = splitHead(trimmed);
        String action = command.head().toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            return new PromptCommandResult(renderPromptList(templates), null);
        }
        if ("preview".equals(action)) {
            return previewPromptTemplate(templates, command.tail());
        }
        if ("run".equals(action)) {
            return runPromptTemplate(templates, command.tail());
        }
        return runPromptTemplate(templates, trimmed);
    }

    private static String renderPromptList(List<PromptTemplate> templates) {
        StringBuilder out = new StringBuilder("Prompt templates\n");
        if (templates == null || templates.isEmpty()) {
            return out.append("status: none\nusage: /prompt <name> [args] | /prompt preview <name> [args]")
                    .toString();
        }
        out.append("status: available\n");
        for (PromptTemplate template : templates) {
            out.append("- ").append(template.name());
            if (template.argumentHint() != null && !template.argumentHint().isBlank()) {
                out.append(" ").append(template.argumentHint());
            }
            if (template.description() != null && !template.description().isBlank()) {
                out.append(" - ").append(template.description());
            }
            if (template.sourceInfo() != null) {
                out.append(" [").append(template.sourceInfo().scope()).append("]");
            }
            out.append("\n");
        }
        out.append("usage: /prompt <name> [args] | /prompt preview <name> [args] | /prompt run <name> [args]");
        return out.toString();
    }

    private static PromptCommandResult previewPromptTemplate(List<PromptTemplate> templates, String arguments) {
        PromptExpansion expansion = expandPromptTemplate(templates, arguments);
        if (expansion.error() != null) {
            return new PromptCommandResult(expansion.error(), null);
        }
        return new PromptCommandResult("Prompt template\nstatus: preview\nname: " + expansion.template().name()
                + "\nexpanded:\n" + expansion.expanded(), null);
    }

    private static PromptCommandResult runPromptTemplate(List<PromptTemplate> templates, String arguments) {
        PromptExpansion expansion = expandPromptTemplate(templates, arguments);
        if (expansion.error() != null) {
            return new PromptCommandResult(expansion.error(), null);
        }
        return new PromptCommandResult("Prompt template\nstatus: running\nname: " + expansion.template().name()
                + "\nexpanded:\n" + expansion.expanded(), expansion.expanded());
    }

    private static PromptExpansion expandPromptTemplate(List<PromptTemplate> templates, String arguments) {
        HeadTail target = splitHead(arguments == null ? "" : arguments.trim());
        if (target.head().isBlank()) {
            return PromptExpansion.error("Prompt template\nerror: missing template name\n"
                    + "usage: /prompt <name> [args] | /prompt preview <name> [args]");
        }
        PromptTemplate template = findPromptTemplate(templates, target.head());
        if (template == null) {
            return PromptExpansion.error("Prompt template\nerror: unknown template: " + target.head()
                    + "\navailable: " + promptTemplateNames(templates));
        }
        String expanded = PromptTemplateLoader.substituteArgs(template.content(),
                PromptTemplateLoader.parseCommandArgs(target.tail()));
        return new PromptExpansion(template, expanded.strip(), null);
    }

    private static PromptTemplate findPromptTemplate(List<PromptTemplate> templates, String name) {
        if (templates == null || name == null) {
            return null;
        }
        return templates.stream()
                .filter(template -> template.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String promptTemplateNames(List<PromptTemplate> templates) {
        if (templates == null || templates.isEmpty()) {
            return "none";
        }
        return String.join(", ", templates.stream().map(PromptTemplate::name).toList());
    }

    private static HeadTail splitHead(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return new HeadTail("", "");
        }
        int split = -1;
        for (int i = 0; i < trimmed.length(); i++) {
            if (Character.isWhitespace(trimmed.charAt(i))) {
                split = i;
                break;
            }
        }
        if (split < 0) {
            return new HeadTail(trimmed, "");
        }
        return new HeadTail(trimmed.substring(0, split), trimmed.substring(split).trim());
    }

    private record HeadTail(String head, String tail) {}

    private record PromptExpansion(PromptTemplate template, String expanded, String error) {
        private static PromptExpansion error(String message) {
            return new PromptExpansion(null, null, message);
        }
    }

    private static String renderThemeList(SettingsManager settingsManager,
                                          ResourceLoader resourceLoader) {
        String current = themeSettingOrStandard(settingsManager);
        String active = TerminalThemeResolver.activeThemeName(settingsManager == null ? null : settingsManager.getThemeSetting());
        StringBuilder out = new StringBuilder("Theme\nstatus: available\n");
        out.append("current: ").append(current).append("\n");
        out.append("effective: ").append(active == null ? "standard" : active).append("\n");
        out.append("themes:\n");
        for (String name : availableThemeNames(resourceLoader)) {
            out.append("- ").append(name);
            if (themeMatchesSetting(name, current, active)) {
                out.append(" (current)");
            }
            ThemeResource resource = themeByName(resourceLoader, name);
            if (resource != null && resource.filePath() != null) {
                out.append(" | path: ").append(resource.filePath());
            }
            out.append("\n");
        }
        out.append("usage: /theme <name> | /theme set <name> | /theme preview <name> | /theme current");
        return out.toString().stripTrailing();
    }

    private static String renderThemeCurrent(SettingsManager settingsManager,
                                             ResourceLoader resourceLoader) {
        String setting = themeSettingOrStandard(settingsManager);
        String active = TerminalThemeResolver.activeThemeName(settingsManager == null ? null : settingsManager.getThemeSetting());
        ThemeResource resource = themeByName(resourceLoader, active == null ? "standard" : active);
        return "Theme\nstatus: current\nsetting: " + setting
                + "\neffective: " + (active == null ? "standard" : active)
                + "\nsource: " + (resource == null || resource.filePath() == null ? "built-in standard" : resource.filePath());
    }

    private static String setTheme(SettingsManager settingsManager,
                                   ResourceLoader resourceLoader,
                                   String target) throws IOException {
        String theme = target == null ? "" : target.trim();
        if (theme.isBlank()) {
            return themeUsage("missing theme name", resourceLoader);
        }
        if (!isKnownThemeSetting(resourceLoader, theme)) {
            return themeUsage("unknown theme: " + theme, resourceLoader);
        }
        settingsManager.setTheme(theme);
        String active = TerminalThemeResolver.activeThemeName(theme);
        return "Theme\nstatus: set\nsetting: " + theme
                + "\neffective: " + (active == null ? "standard" : active)
                + "\nnote: theme applies to subsequent assistant and tool output";
    }

    private static String previewTheme(ResourceLoader resourceLoader,
                                       String target) {
        String theme = target == null ? "" : target.trim();
        if (theme.isBlank()) {
            return themeUsage("missing theme name", resourceLoader);
        }
        if (!isKnownThemeSetting(resourceLoader, theme)) {
            return themeUsage("unknown theme: " + theme, resourceLoader);
        }
        ThemeResource resource = themeByName(resourceLoader, TerminalThemeResolver.activeThemeName(theme));
        TerminalTheme terminalTheme = resource == null
                ? TerminalTheme.standard()
                : TerminalThemeResolver.fromThemeResource(resource).orElseGet(TerminalTheme::standard);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            InteractiveOutputRenderer.renderAssistantText(out, """
                    # Theme Preview
                    ```java
                    public record ThemeSample(String name) {}
                    ```
                    """, 72, terminalTheme);
            InteractiveOutputRenderer.renderToolResult(out, new Message.ToolResult("theme-preview", "edit",
                    List.of(new Content.Text("""
                            @@ -1 +1 @@
                            -old theme token
                            +new theme token
                            """)), false, Map.of(), java.time.Instant.now()), 72, terminalTheme);
        }
        return "Theme preview\nname: " + theme + "\n" + buffer.toString(StandardCharsets.UTF_8).stripTrailing();
    }

    private static boolean isKnownThemeSetting(ResourceLoader resourceLoader,
                                               String theme) {
        if (theme == null || theme.isBlank()) {
            return false;
        }
        int slash = theme.indexOf('/');
        if (slash >= 0) {
            if (theme.indexOf('/', slash + 1) >= 0) {
                return false;
            }
            String light = theme.substring(0, slash).trim();
            String dark = theme.substring(slash + 1).trim();
            return !light.isBlank() && !dark.isBlank()
                    && isKnownThemeName(resourceLoader, light)
                    && isKnownThemeName(resourceLoader, dark);
        }
        return isKnownThemeName(resourceLoader, theme);
    }

    private static boolean isKnownThemeName(ResourceLoader resourceLoader,
                                            String name) {
        if ("standard".equalsIgnoreCase(name)) {
            return true;
        }
        return themeByName(resourceLoader, name) != null;
    }

    private static String themeUsage(String error, ResourceLoader resourceLoader) {
        return "Theme\nerror: " + error
                + "\nusage: /theme <name> | /theme set <name> | /theme preview <name> | /theme current"
                + "\navailable: " + String.join(", ", availableThemeNames(resourceLoader));
    }

    private static List<String> availableThemeNames(ResourceLoader resourceLoader) {
        Set<String> names = new LinkedHashSet<>();
        names.add("standard");
        if (resourceLoader != null) {
            resourceLoader.themes().themes().stream()
                    .map(ThemeResource::name)
                    .sorted()
                    .forEach(names::add);
        }
        return List.copyOf(names);
    }

    private static ThemeResource themeByName(ResourceLoader resourceLoader,
                                             String name) {
        if (name == null || resourceLoader == null) {
            return null;
        }
        return resourceLoader.themes().themes().stream()
                .filter(theme -> theme.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String themeSettingOrStandard(SettingsManager settingsManager) {
        String setting = settingsManager == null ? null : settingsManager.getThemeSetting();
        return setting == null || setting.isBlank() ? "standard" : setting;
    }

    private static boolean themeMatchesSetting(String name, String setting, String active) {
        if (name.equals(setting)) {
            return true;
        }
        if (active != null && name.equals(active)) {
            return true;
        }
        return active == null && "standard".equals(name);
    }

    private static String renderSettingsSummary(SettingsManager settingsManager) {
        return "Settings\n"
                + "project trusted: " + settingsManager.isProjectTrusted() + "\n"
                + "merged: " + JsonCodec.stringify(settingsManager.load()) + "\n"
                + "global: " + JsonCodec.stringify(settingsManager.getGlobalSettings()) + "\n"
                + "project: " + JsonCodec.stringify(settingsManager.getProjectSettings()) + "\n"
                + "usage: /settings json | /settings get <path> | /settings set [global|project] <path> <json|text> | /settings unset [global|project] <path>";
    }

    private static String handleSettingsGet(SettingsManager settingsManager, String arguments) {
        String pathText = arguments == null ? "" : arguments.trim();
        if (pathText.isEmpty()) {
            return settingsUsage("missing path");
        }
        JsonNode value = settingAtPath(settingsManager.load(), settingPath(pathText));
        return "Settings\npath: " + pathText + "\nvalue: " + (value == null ? "null" : JsonCodec.stringify(value));
    }

    private static String handleSettingsSet(SettingsManager settingsManager, String arguments) throws IOException {
        SettingsMutation mutation = parseSettingsMutation(arguments, true);
        ObjectNode patch = JsonCodec.mapper().createObjectNode();
        putSettingPatch(patch, mutation.path(), mutation.value());
        settingsManager.update(mutation.scope(), patch);
        return "Settings\nstatus: set\nscope: " + mutation.scope().name().toLowerCase(Locale.ROOT)
                + "\npath: " + String.join(".", mutation.path())
                + "\nvalue: " + JsonCodec.stringify(mutation.value())
                + "\nnote: run /reload to rebuild the current session with settings-dependent resources";
    }

    private static String handleSettingsUnset(SettingsManager settingsManager, String arguments) throws IOException {
        SettingsMutation mutation = parseSettingsMutation(arguments, false);
        settingsManager.unset(mutation.scope(), mutation.path());
        return "Settings\nstatus: unset\nscope: " + mutation.scope().name().toLowerCase(Locale.ROOT)
                + "\npath: " + String.join(".", mutation.path())
                + "\nnote: run /reload to rebuild the current session with settings-dependent resources";
    }

    private static String settingsUsage(String error) {
        return "Settings\nerror: " + error
                + "\nusage: /settings json | /settings get <path> | /settings set [global|project] <path> <json|text> | /settings unset [global|project] <path>";
    }

    private static SettingsMutation parseSettingsMutation(String arguments, boolean requireValue) {
        String rest = arguments == null ? "" : arguments.trim();
        SettingsManager.Scope scope = SettingsManager.Scope.GLOBAL;
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("global ")) {
            scope = SettingsManager.Scope.GLOBAL;
            rest = rest.substring("global".length()).trim();
        } else if (lower.startsWith("project ")) {
            scope = SettingsManager.Scope.PROJECT;
            rest = rest.substring("project".length()).trim();
        }
        if (rest.isEmpty()) {
            throw new IllegalArgumentException("missing settings path");
        }
        String pathText;
        String valueText = null;
        int equals = rest.indexOf('=');
        if (equals >= 0) {
            pathText = rest.substring(0, equals).trim();
            valueText = rest.substring(equals + 1).trim();
        } else {
            String[] parts = rest.split("\\s+", 2);
            pathText = parts[0].trim();
            valueText = parts.length > 1 ? parts[1].trim() : null;
        }
        if (pathText.isEmpty()) {
            throw new IllegalArgumentException("missing settings path");
        }
        if (requireValue && (valueText == null || valueText.isEmpty())) {
            throw new IllegalArgumentException("missing settings value");
        }
        return new SettingsMutation(scope, settingPath(pathText),
                requireValue ? parseSettingValue(valueText) : null);
    }

    private static List<String> settingPath(String pathText) {
        List<String> path = java.util.Arrays.stream(pathText.split("\\."))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .toList();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("missing settings path");
        }
        return path;
    }

    private static JsonNode parseSettingValue(String valueText) {
        try {
            return JsonCodec.parse(valueText);
        } catch (Exception ignored) {
            return JsonCodec.mapper().getNodeFactory().textNode(valueText);
        }
    }

    private static void putSettingPatch(ObjectNode root, List<String> path, JsonNode value) {
        ObjectNode current = root;
        for (int i = 0; i < path.size() - 1; i++) {
            ObjectNode next = JsonCodec.mapper().createObjectNode();
            current.set(path.get(i), next);
            current = next;
        }
        current.set(path.getLast(), value);
    }

    private static JsonNode settingAtPath(JsonNode root, List<String> path) {
        JsonNode current = root;
        for (String part : path) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(part);
        }
        return current;
    }

    private record SettingsMutation(SettingsManager.Scope scope, List<String> path, JsonNode value) {
    }

    static String handleLogin(AuthStorage authStorage, ModelRegistry modelRegistry, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty()) {
            return renderLoginProviders(authStorage, modelRegistry);
        }
        String[] parts = trimmed.split("\\s+", 3);
        String provider = parts[0].trim();
        if (provider.isEmpty()) {
            return loginUsage("missing provider");
        }
        if (parts.length == 1) {
            if (authStorage.getOAuthProviders().contains(provider)) {
                try {
                    authStorage.login(provider, new AuthStorage.OAuthLoginCallbacks() {
                    });
                    return "Provider authentication\nstatus: logged in\nprovider: " + provider
                            + "\nmethod: oauth"
                            + "\nnote: run /reload to refresh provider auth status in the current session";
                } catch (Exception e) {
                    return "Provider authentication\nerror: " + e.getMessage() + "\nprovider: " + provider;
                }
            }
            return loginUsage("missing API key for provider: " + provider);
        }
        String modeOrKey = parts[1].trim();
        if ("env".equalsIgnoreCase(modeOrKey) || "--env".equalsIgnoreCase(modeOrKey)) {
            if (parts.length < 3 || parts[2].trim().isEmpty()) {
                return loginUsage("missing environment variable name");
            }
            String envName = parts[2].trim();
            if (!envName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                return loginUsage("invalid environment variable name: " + envName);
            }
            authStorage.set(provider, new AuthStorage.ApiKeyCredential("$" + envName, null));
            boolean currentlySet = System.getenv(envName) != null && !System.getenv(envName).isBlank();
            return "Provider authentication\nstatus: logged in\nprovider: " + provider
                    + "\nmethod: api-key-env"
                    + "\nsource: " + envName
                    + (currentlySet ? "" : "\nwarning: environment variable is not set in the current process")
                    + "\nnote: run /reload to refresh provider auth status in the current session";
        }
        String apiKey = trimmed.substring(provider.length()).trim();
        if (apiKey.isEmpty()) {
            return loginUsage("missing API key for provider: " + provider);
        }
        authStorage.set(provider, new AuthStorage.ApiKeyCredential(apiKey, null));
        return "Provider authentication\nstatus: logged in\nprovider: " + provider
                + "\nmethod: api-key"
                + "\nnote: API key stored; it is not printed back to the terminal"
                + "\nnote: run /reload to refresh provider auth status in the current session";
    }

    private static String renderLoginProviders(AuthStorage authStorage, ModelRegistry modelRegistry) {
        Map<String, String> providers = new java.util.LinkedHashMap<>();
        for (Model model : modelRegistry.getAll()) {
            providers.putIfAbsent(model.provider(), modelRegistry.getProviderDisplayName(model.provider()));
        }
        for (String provider : authStorage.getOAuthProviders()) {
            providers.putIfAbsent(provider, provider);
        }
        for (String provider : authStorage.list()) {
            providers.putIfAbsent(provider, modelRegistry.getProviderDisplayName(provider));
        }
        StringBuilder out = new StringBuilder("Provider authentication\n");
        out.append("status: choose a provider\n");
        out.append("usage: ").append(LOGIN_USAGE);
        if (providers.isEmpty()) {
            return out.append("\nproviders: none discovered").toString();
        }
        out.append("\nproviders:");
        for (Map.Entry<String, String> entry : providers.entrySet()) {
            AuthStorage.AuthStatus status = modelRegistry.getProviderAuthStatus(entry.getKey());
            out.append("\n- ").append(entry.getKey());
            if (!entry.getValue().equals(entry.getKey())) {
                out.append(" (").append(entry.getValue()).append(")");
            }
            out.append(" auth: ").append(authStatusLabel(status));
            if (authStorage.getOAuthProviders().contains(entry.getKey())) {
                out.append(" oauth: available");
            }
        }
        return out.toString();
    }

    private static String loginUsage(String error) {
        return "Provider authentication\nerror: " + error
                + "\nusage: " + LOGIN_USAGE;
    }

    private static String authStatusLabel(AuthStorage.AuthStatus status) {
        if (status == null || status.source() == null) {
            return "not configured";
        }
        String label = switch (status.source()) {
            case STORED -> "stored";
            case RUNTIME -> "runtime";
            case ENVIRONMENT -> "environment";
            case FALLBACK -> "local";
            case MODELS_JSON_KEY -> "models.json key";
            case MODELS_JSON_COMMAND -> "models.json command";
        };
        return status.label() == null || status.label().isBlank()
                ? label
                : label + " (" + status.label() + ")";
    }

    static String handleLogout(AuthStorage authStorage, String arguments) {
        String provider = arguments == null ? "" : arguments.trim();
        if (provider.isEmpty()) {
            return renderLogoutProviders(authStorage);
        }
        if (provider.split("\\s+").length > 1) {
            return "Provider authentication\nerror: too many arguments\nusage: " + LOGOUT_USAGE;
        }
        AuthStorage.AuthStatus before = authStorage.getAuthStatus(provider);
        if (before.source() == AuthStorage.AuthStatus.Source.ENVIRONMENT) {
            return "Provider authentication\nstatus: environment-only\nprovider: " + provider
                    + "\nsource: " + before.label()
                    + "\nnote: remove the environment variable to log out of this provider";
        }
        boolean hadStored = authStorage.has(provider);
        boolean hadRuntime = before.source() == AuthStorage.AuthStatus.Source.RUNTIME;
        if (!hadStored && !hadRuntime) {
            return "Provider authentication\nstatus: not configured\nprovider: " + provider;
        }
        authStorage.logout(provider);
        authStorage.removeRuntimeApiKey(provider);
        return "Provider authentication\nstatus: logged out\nprovider: " + provider
                + "\nremoved: " + (hadStored ? "stored" : "runtime")
                + "\nnote: run /reload to refresh provider auth status in the current session";
    }

    private static String renderLogoutProviders(AuthStorage authStorage) {
        Map<String, AuthStorage.AuthStatus> providers = authStorage.listAuthStatuses();
        StringBuilder out = new StringBuilder("Provider authentication\n");
        if (providers.isEmpty()) {
            return out.append("status: no configured providers\nusage: ").append(LOGOUT_USAGE).toString();
        }
        out.append("status: choose a provider\nusage: ").append(LOGOUT_USAGE).append("\nproviders:");
        for (Map.Entry<String, AuthStorage.AuthStatus> entry : providers.entrySet()) {
            out.append("\n- ").append(entry.getKey()).append(" (")
                    .append(authStatusLabel(entry.getValue())).append(")");
        }
        return out.toString();
    }

    static String handleExport(AgentSession session, Path cwd, String arguments) {
        Path sessionFile = session.sessionFile().orElse(null);
        if (sessionFile == null) {
            return "Session export\nerror: current session is in-memory and cannot be exported";
        }
        try {
            Path outputPath = resolveExportPath(cwd, session, arguments);
            if (outputPath.getParent() != null) {
                Files.createDirectories(outputPath.getParent());
            }
            String format;
            if (isJsonlPath(outputPath)) {
                session.sessionManager().copySessionFile(outputPath);
                format = "jsonl";
            } else {
                HtmlExporter.exportToFile(sessionFile, outputPath);
                format = "html";
            }
            return "Session export\nstatus: exported\nformat: " + format + "\nfile: " + outputPath;
        } catch (Exception e) {
            return "Session export\nerror: " + e.getMessage();
        }
    }

    private static Path resolveExportPath(Path cwd, AgentSession session, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        String defaultName = "pi-session-" + session.sessionManager().sessionId() + ".html";
        if (trimmed.isEmpty()) {
            return cwd.resolve(defaultName).toAbsolutePath().normalize();
        }
        Path requested = PathUtils.resolvePath(trimmed, cwd, PathUtils.PathInputOptions.cli());
        if (Files.isDirectory(requested)) {
            return requested.resolve(defaultName).toAbsolutePath().normalize();
        }
        if (hasExportExtension(requested)) {
            return requested;
        }
        Path fileName = requested.getFileName();
        String name = fileName == null ? defaultName : fileName.toString() + ".html";
        Path parent = requested.getParent();
        return (parent == null ? Path.of(name) : parent.resolve(name)).toAbsolutePath().normalize();
    }

    private static boolean hasExportExtension(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".jsonl");
    }

    private static boolean isJsonlPath(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".jsonl");
    }

    static String handleShare(AgentSession session, String arguments, GistSharer sharer) {
        Path sessionFile = session.sessionFile().orElse(null);
        if (sessionFile == null) {
            return "Session share\nerror: current session is in-memory and cannot be shared";
        }
        ShareVisibility visibility = parseShareVisibility(arguments);
        if (visibility.error() != null) {
            return visibility.error();
        }
        Path tempDir = null;
        Path htmlFile = null;
        try {
            tempDir = Files.createTempDirectory("pi-share-");
            String fileName = "pi-session-" + safeFileName(session.sessionManager().sessionId()) + ".html";
            htmlFile = tempDir.resolve(fileName);
            HtmlExporter.exportToFile(sessionFile, htmlFile);
            GistShareResult result = sharer.share(htmlFile, visibility.isPublic());
            return "Session share\nstatus: shared"
                    + "\nvisibility: " + (visibility.isPublic() ? "public" : "secret")
                    + "\nurl: " + result.url()
                    + "\nfile: " + fileName;
        } catch (Exception e) {
            return "Session share\nerror: " + e.getMessage()
                    + "\nusage: /share [public|secret]";
        } finally {
            if (htmlFile != null) {
                try {
                    Files.deleteIfExists(htmlFile);
                } catch (IOException ignored) {
                }
            }
            if (tempDir != null) {
                try {
                    Files.deleteIfExists(tempDir);
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static ShareVisibility parseShareVisibility(String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty() || "secret".equalsIgnoreCase(trimmed) || "--secret".equalsIgnoreCase(trimmed)
                || "private".equalsIgnoreCase(trimmed) || "--private".equalsIgnoreCase(trimmed)) {
            return new ShareVisibility(false, null);
        }
        if ("public".equalsIgnoreCase(trimmed) || "--public".equalsIgnoreCase(trimmed)) {
            return new ShareVisibility(true, null);
        }
        return new ShareVisibility(false, "Session share\nerror: unknown argument: " + trimmed
                + "\nusage: /share [public|secret]");
    }

    private static String safeFileName(String value) {
        String safe = value == null ? "" : value.replaceAll("[^A-Za-z0-9._-]", "-");
        return safe.isBlank() ? "session" : safe;
    }

    private record ShareVisibility(boolean isPublic, String error) {
    }

    record GistShareResult(String url) {
    }

    @FunctionalInterface
    interface GistSharer {
        GistShareResult share(Path htmlFile, boolean publicGist) throws Exception;
    }

    private static final class GhCliGistSharer implements GistSharer {
        @Override
        public GistShareResult share(Path htmlFile, boolean publicGist) throws Exception {
            Process process = new ProcessBuilder("gh", "gist", "create",
                    "--public=" + publicGist, htmlFile.toString())
                    .redirectErrorStream(false)
                    .start();
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                String detail = stderr.isBlank() ? stdout : stderr;
                throw new IllegalStateException(detail.isBlank()
                        ? "gh gist create failed with exit code " + exitCode
                        : detail);
            }
            String url = lastNonBlankLine(stdout);
            if (url.isBlank()) {
                throw new IllegalStateException("gh gist create did not return a URL");
            }
            return new GistShareResult(url);
        }

        private static String lastNonBlankLine(String text) {
            if (text == null || text.isBlank()) {
                return "";
            }
            String[] lines = text.split("\\R");
            for (int i = lines.length - 1; i >= 0; i--) {
                String line = lines[i].trim();
                if (!line.isEmpty()) {
                    return line;
                }
            }
            return "";
        }
    }

    static String handleCopy(AgentSession session, ClipboardWriter writer) {
        String text = latestAssistantText(session);
        if (text == null || text.isBlank()) {
            return "Session copy\nerror: no assistant message found";
        }
        try {
            writer.write(text);
            return "Session copy\nstatus: copied\nchars: " + text.length();
        } catch (Exception e) {
            return "Session copy\nerror: " + e.getMessage();
        }
    }

    static String handlePasteImage(Path cwd, String arguments, ClipboardImageReader reader) {
        try {
            Optional<ClipboardImage> image = reader.read();
            if (image.isEmpty()) {
                return "Clipboard image\nerror: no supported image found in clipboard";
            }
            Path outputPath = resolveClipboardImagePath(cwd, arguments, image.get().mimeType());
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, image.get().bytes());
            return "Clipboard image"
                    + "\nstatus: saved"
                    + "\nmimeType: " + image.get().mimeType()
                    + "\nbytes: " + image.get().bytes().length
                    + "\nfile: " + outputPath
                    + "\nsubmit: @" + outputPath;
        } catch (Exception e) {
            return "Clipboard image\nerror: " + e.getMessage();
        }
    }

    static String handleImageCommand(Path cwd, AuthStorage authStorage, String arguments,
                                     ImageGenerationRegistry registry) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty() || "list".equalsIgnoreCase(trimmed)) {
            return renderImageModelList(registry);
        }
        HeadTail command = splitHead(trimmed);
        String action = command.head().toLowerCase(Locale.ROOT);
        if (!"generate".equals(action) && !"gen".equals(action)) {
            return "Image generation\nerror: unknown command: " + command.head()
                    + "\nusage: /image list | /image generate [--model provider/model] [--out path] <prompt>";
        }
        return generateImage(cwd, authStorage, registry, command.tail());
    }

    private static String renderImageModelList(ImageGenerationRegistry registry) {
        List<ImageGenModel> models = registry == null ? List.of() : registry.imageModels();
        StringBuilder out = new StringBuilder("Image generation\n");
        if (models.isEmpty()) {
            return out.append("status: no models\nusage: /image list | /image generate <prompt>").toString();
        }
        out.append("status: available\n");
        for (ImageGenModel model : models) {
            out.append("- ").append(model.provider()).append("/").append(model.modelId())
                    .append(" - ").append(model.displayName());
            if (!model.supportedAspectRatios().isEmpty()) {
                out.append(" [aspect: ").append(String.join(", ", model.supportedAspectRatios())).append("]");
            }
            if (!model.supportedResolutions().isEmpty()) {
                out.append(" [size: ").append(String.join(", ", model.supportedResolutions())).append("]");
            }
            out.append("\n");
        }
        out.append("usage: /image generate [--model provider/model] [--out path] [--aspect 1:1] [--size 1024x1024] [--n 1] <prompt>");
        return out.toString();
    }

    private static String generateImage(Path cwd, AuthStorage authStorage, ImageGenerationRegistry registry,
                                        String arguments) {
        try {
            ImageCommandArgs args = parseImageCommandArgs(arguments);
            if (args.prompt().isBlank()) {
                return "Image generation\nerror: missing prompt\n"
                        + "usage: /image generate [--model provider/model] [--out path] <prompt>";
            }
            ImageGenModel model = resolveImageModel(registry, args.modelRef());
            if (model == null) {
                return "Image generation\nerror: image model not found: "
                        + (args.modelRef() == null ? "none" : args.modelRef())
                        + "\nuse /image list to view available models";
            }
            String apiKey = authStorage == null ? null : authStorage.getApiKey(model.provider()).orElse(null);
            if (apiKey == null || apiKey.isBlank()) {
                return "Image generation\nerror: missing API key for provider: " + model.provider()
                        + "\nuse /login " + model.provider() + " <api-key> or set a supported environment variable";
            }
            ImageGenModel.Request request = new ImageGenModel.Request(args.prompt(), args.aspectRatio(),
                    args.resolution(), args.count(), Map.of());
            ImageGenerationOptions options = new ImageGenerationOptions(apiKey, Map.of(), Duration.ofMinutes(10),
                    2, Map.of(), Map.of());
            ImageGenModel.Response response = registry.generateImages(model, request, options);
            return renderGeneratedImages(cwd, args.outputPath(), model, request, response);
        } catch (Exception e) {
            return "Image generation\nerror: " + e.getMessage();
        }
    }

    private static ImageCommandArgs parseImageCommandArgs(String arguments) {
        List<String> tokens = PromptTemplateLoader.parseCommandArgs(arguments == null ? "" : arguments);
        String modelRef = null;
        String outputPath = null;
        String aspectRatio = null;
        String resolution = null;
        int count = 1;
        List<String> prompt = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.startsWith("--model=")) {
                modelRef = token.substring("--model=".length());
            } else if ("--model".equals(token) && i + 1 < tokens.size()) {
                modelRef = tokens.get(++i);
            } else if (token.startsWith("--out=")) {
                outputPath = token.substring("--out=".length());
            } else if ("--out".equals(token) && i + 1 < tokens.size()) {
                outputPath = tokens.get(++i);
            } else if (token.startsWith("--aspect=")) {
                aspectRatio = token.substring("--aspect=".length());
            } else if ("--aspect".equals(token) && i + 1 < tokens.size()) {
                aspectRatio = tokens.get(++i);
            } else if (token.startsWith("--size=")) {
                resolution = token.substring("--size=".length());
            } else if (("--size".equals(token) || "--resolution".equals(token)) && i + 1 < tokens.size()) {
                resolution = tokens.get(++i);
            } else if (token.startsWith("--n=")) {
                count = Math.max(1, Integer.parseInt(token.substring("--n=".length())));
            } else if ("--n".equals(token) && i + 1 < tokens.size()) {
                count = Math.max(1, Integer.parseInt(tokens.get(++i)));
            } else {
                prompt.add(token);
            }
        }
        return new ImageCommandArgs(modelRef, outputPath, aspectRatio, resolution, count, String.join(" ", prompt));
    }

    private static ImageGenModel resolveImageModel(ImageGenerationRegistry registry, String modelRef) {
        if (registry == null) {
            return null;
        }
        List<ImageGenModel> models = registry.imageModels();
        if (modelRef == null || modelRef.isBlank()) {
            return models.isEmpty() ? null : models.getFirst();
        }
        int slash = modelRef.indexOf('/');
        if (slash > 0) {
            String provider = modelRef.substring(0, slash);
            String modelId = modelRef.substring(slash + 1);
            return registry.findModel(provider, modelId).orElse(null);
        }
        List<ImageGenModel> matches = models.stream()
                .filter(model -> model.modelId().equals(modelRef))
                .toList();
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static String renderGeneratedImages(Path cwd, String outputPath, ImageGenModel model,
                                                ImageGenModel.Request request, ImageGenModel.Response response)
            throws IOException {
        StringBuilder out = new StringBuilder("Image generation\nstatus: generated")
                .append("\nmodel: ").append(model.provider()).append("/").append(model.modelId())
                .append("\nprompt: ").append(request.prompt());
        if (response == null || response.images() == null || response.images().isEmpty()) {
            return out.append("\nimages: 0").toString();
        }
        int fileIndex = 0;
        int urlIndex = 0;
        String revisedPrompt = null;
        for (ImageGenModel.GeneratedImage image : response.images()) {
            if (image.revisedPrompt() != null && !image.revisedPrompt().isBlank()) {
                revisedPrompt = image.revisedPrompt();
            }
            if (image.base64Data() != null && !image.base64Data().isBlank()) {
                byte[] bytes = Base64.getDecoder().decode(image.base64Data());
                Path path = resolveGeneratedImagePath(cwd, outputPath, image.mimeType(), fileIndex,
                        countBase64Images(response.images()));
                Files.createDirectories(path.getParent());
                Files.write(path, bytes);
                if (fileIndex == 0) {
                    out.append("\nfiles:");
                }
                out.append("\n- ").append(path)
                        .append(" (").append(image.mimeType() == null ? "application/octet-stream" : image.mimeType())
                        .append(", bytes: ").append(bytes.length).append(")");
                fileIndex++;
            } else if (image.url() != null && !image.url().isBlank()) {
                if (urlIndex == 0) {
                    out.append("\nurls:");
                }
                out.append("\n- ").append(image.url());
                urlIndex++;
            }
        }
        if (revisedPrompt != null) {
            out.append("\nrevisedPrompt: ").append(revisedPrompt);
        }
        out.append("\nimages: ").append(response.images().size());
        return out.toString();
    }

    private static int countBase64Images(List<ImageGenModel.GeneratedImage> images) {
        int count = 0;
        for (ImageGenModel.GeneratedImage image : images) {
            if (image.base64Data() != null && !image.base64Data().isBlank()) {
                count++;
            }
        }
        return count;
    }

    private static Path resolveGeneratedImagePath(Path cwd, String outputPath, String mimeType, int index, int total) {
        String extension = extensionForImageMimeType(mimeType).orElse("png");
        if (outputPath == null || outputPath.isBlank()) {
            return cwd.resolve("pi-image-" + UUID.randomUUID() + "." + extension).toAbsolutePath().normalize();
        }
        Path path = PathUtils.resolvePath(outputPath, cwd, PathUtils.PathInputOptions.cli());
        boolean directoryTarget = total > 1 || Files.isDirectory(path) || !hasFileExtension(path);
        if (directoryTarget) {
            String suffix = total > 1 ? "-" + (index + 1) : "";
            return path.resolve("pi-image-" + UUID.randomUUID() + suffix + "." + extension)
                    .toAbsolutePath().normalize();
        }
        return path.toAbsolutePath().normalize();
    }

    private static ImageGenerationRegistry defaultImageGenerationRegistry() {
        ImageGenerationRegistry registry = new ImageGenerationRegistry();
        registry.registerDefaults();
        return registry;
    }

    private record ImageCommandArgs(String modelRef, String outputPath, String aspectRatio, String resolution,
                                    int count, String prompt) {
    }

    private static Path resolveClipboardImagePath(Path cwd, String arguments, String mimeType) throws IOException {
        String trimmed = arguments == null ? "" : arguments.trim();
        String extension = extensionForImageMimeType(mimeType).orElse("png");
        if (trimmed.isEmpty()) {
            return Files.createTempDirectory("pi-clipboard-")
                    .resolve("pi-clipboard-" + UUID.randomUUID() + "." + extension);
        }
        Path path = PathUtils.resolvePath(trimmed, cwd, PathUtils.PathInputOptions.cli());
        if (Files.isDirectory(path)) {
            return path.resolve("pi-clipboard-" + UUID.randomUUID() + "." + extension);
        }
        if (!hasFileExtension(path)) {
            path = path.resolveSibling(path.getFileName() + "." + extension);
        }
        return path;
    }

    private static boolean hasFileExtension(Path path) {
        Path fileName = path.getFileName();
        if (fileName == null) {
            return false;
        }
        String name = fileName.toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1;
    }

    private static Optional<String> extensionForImageMimeType(String mimeType) {
        String base = mimeType == null ? "" : mimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return switch (base) {
            case "image/png" -> Optional.of("png");
            case "image/jpeg", "image/jpg" -> Optional.of("jpg");
            case "image/gif" -> Optional.of("gif");
            case "image/webp" -> Optional.of("webp");
            case "image/bmp" -> Optional.of("bmp");
            default -> Optional.empty();
        };
    }

    private static String latestAssistantText(AgentSession session) {
        List<AgentMessage> messages = session.messages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            AgentMessage message = messages.get(i);
            if (message instanceof AgentMessage.Llm llm && llm.message() instanceof Message.Assistant assistant) {
                return InteractiveOutputRenderer.textFromContent(assistant.content());
            }
        }
        return null;
    }

    static String handleName(AgentSession session, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty()) {
            return "Session name\nstatus: current"
                    + "\nsession: " + session.sessionManager().sessionId()
                    + "\nname: " + session.sessionManager().sessionName().orElse("none")
                    + "\nusage: /name <text> | /name clear";
        }
        boolean clear = "clear".equalsIgnoreCase(trimmed)
                || "reset".equalsIgnoreCase(trimmed)
                || "--clear".equalsIgnoreCase(trimmed);
        String name = clear ? "" : trimmed;
        try {
            session.sessionManager().appendSessionInfo(name);
            return "Session name\nstatus: " + (clear ? "cleared" : "set")
                    + "\nsession: " + session.sessionManager().sessionId()
                    + "\nname: " + session.sessionManager().sessionName().orElse("none");
        } catch (Exception e) {
            return "Session name\nerror: " + e.getMessage()
                    + "\nusage: /name <text> | /name clear";
        }
    }

    static String handleSession(AgentSession session, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (!trimmed.isEmpty()) {
            return "Session info\nerror: unknown argument: " + trimmed + "\nusage: /session";
        }
        AgentSession.SessionStats stats = session.stats();
        SessionManager manager = session.sessionManager();
        String model = session.model() == null ? "none" : session.model().provider() + "/" + session.model().modelId();
        String sourceSummary = sessionSourceSummary(manager);
        return "Session info"
                + "\nsession: " + manager.sessionId()
                + "\nname: " + manager.sessionName().orElse("none")
                + "\nfile: " + displayPath(stats.sessionFile())
                + "\ncwd: " + manager.cwd()
                + "\npersisted: " + manager.isPersisted()
                + "\nleaf: " + manager.leafId().orElse("root")
                + "\nentries: " + manager.entries().size()
                + "\nbranch entries: " + manager.branch().size()
                + "\nmodel: " + model
                + "\nthinking: " + session.thinkingLevel().wireName()
                + "\nmessages: user=" + stats.userMessages()
                + " assistant=" + stats.assistantMessages()
                + " tool=" + stats.toolResults()
                + " total=" + stats.totalMessages()
                + "\ntokens: input=" + stats.inputTokens()
                + " output=" + stats.outputTokens()
                + " cache=" + stats.cacheInputTokens()
                + " reasoning=" + stats.reasoningTokens()
                + " total=" + stats.totalTokens()
                + sourceSummary
                + "\nskills: " + session.skills().size()
                + "\ntools: " + session.tools().size();
    }

    private static String sessionSourceSummary(SessionManager manager) {
        Map<String, Integer> messageSources = new LinkedHashMap<>();
        Map<String, Integer> customMessageSources = new LinkedHashMap<>();
        for (SessionEntry entry : manager.branch()) {
            if (entry instanceof SessionEntry.MessageEntry message) {
                incrementSource(messageSources, message.message().path("source").asText(""));
            } else if (entry instanceof SessionEntry.CustomMessageEntry customMessage) {
                incrementSource(customMessageSources, customMessage.source());
            }
        }
        List<String> parts = new ArrayList<>();
        sourceCountsLabel("messages", messageSources).ifPresent(parts::add);
        sourceCountsLabel("custom_messages", customMessageSources).ifPresent(parts::add);
        return parts.isEmpty() ? "" : "\nsources: " + String.join(" ", parts);
    }

    private static void incrementSource(Map<String, Integer> counts, String source) {
        if (source == null || source.isBlank()) {
            return;
        }
        counts.merge(source.trim(), 1, Integer::sum);
    }

    private static Optional<String> sourceCountsLabel(String label, Map<String, Integer> counts) {
        if (counts.isEmpty()) {
            return Optional.empty();
        }
        List<String> values = new ArrayList<>();
        counts.forEach((source, count) -> values.add(previewText(source) + "=" + count));
        return Optional.of(label + "[" + String.join(",", values) + "]");
    }

    @FunctionalInterface
    interface ClipboardWriter {
        void write(String text) throws Exception;
    }

    @FunctionalInterface
    interface ClipboardImageReader {
        Optional<ClipboardImage> read() throws Exception;
    }

    record ClipboardImage(byte[] bytes, String mimeType) {
    }

    private static final class SystemClipboardWriter implements ClipboardWriter {
        @Override
        public void write(String text) {
            if (GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("system clipboard is unavailable in headless mode");
            }
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
    }

    private static final class SystemClipboardImageReader implements ClipboardImageReader {
        @Override
        public Optional<ClipboardImage> read() throws Exception {
            if (GraphicsEnvironment.isHeadless()) {
                throw new IllegalStateException("system clipboard is unavailable in headless mode");
            }
            Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
            if (contents == null) {
                return Optional.empty();
            }
            if (contents.isDataFlavorSupported(DataFlavor.imageFlavor)) {
                Image image = (Image) contents.getTransferData(DataFlavor.imageFlavor);
                return Optional.of(new ClipboardImage(encodeClipboardImagePng(image), "image/png"));
            }
            if (contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) contents.getTransferData(DataFlavor.javaFileListFlavor);
                for (File file : files) {
                    Path path = file.toPath();
                    Optional<String> mimeType = MimeUtils.detectSupportedImageMimeTypeFromFile(path);
                    if (mimeType.isPresent()) {
                        return Optional.of(new ClipboardImage(Files.readAllBytes(path), mimeType.get()));
                    }
                }
            }
            return Optional.empty();
        }

        private static byte[] encodeClipboardImagePng(Image image) throws IOException {
            if (image == null) {
                throw new IOException("clipboard image is empty");
            }
            BufferedImage buffered = new BufferedImage(image.getWidth(null), image.getHeight(null),
                    BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D graphics = buffered.createGraphics();
            try {
                graphics.drawImage(image, 0, 0, null);
            } finally {
                graphics.dispose();
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(buffered, "png", output)) {
                throw new IOException("No PNG writer available");
            }
            return output.toByteArray();
        }
    }

    static SessionReplacement handleImport(AgentSessionRuntime runtime, AgentSession currentSession, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty()) {
            return SessionReplacement.error("Session import\nerror: missing path\nusage: /import <session.jsonl>");
        }
        if (!currentSession.sessionManager().isPersisted()) {
            return SessionReplacement.error("Session import\nerror: current session is in-memory and has no session directory");
        }
        try {
            Path inputPath = PathUtils.resolvePath(trimmed, runtime.services().cwd(), PathUtils.PathInputOptions.cli());
            AgentSessionRuntime.ReplacementResult result = runtime.importFromJsonl(inputPath, runtime.services().cwd());
            if (result.cancelled()) {
                return SessionReplacement.error(cancelledReplacementMessage("Session import", result));
            }
            AgentSession importedSession = runtime.session();
            String message = "Session import\nstatus: imported\nsession: "
                    + importedSession.sessionManager().sessionId()
                    + "\nprevious: " + displayPath(result.previousSessionFile())
                    + "\ncurrent: " + displayPath(result.currentSessionFile());
            return new SessionReplacement(importedSession, message);
        } catch (Exception e) {
            return SessionReplacement.error("Session import\nerror: " + e.getMessage());
        }
    }

    private static String displayPath(Path path) {
        return path == null ? "none" : path.toString();
    }

    record SessionReplacement(AgentSession session, String message) {
        static SessionReplacement error(String message) {
            return new SessionReplacement(null, message);
        }
    }

    static SessionReplacement handleFork(AgentSessionRuntime runtime, String arguments) {
        ForkRequest request = parseForkRequest(arguments);
        if (request.error() != null) {
            return SessionReplacement.error(request.error());
        }
        try {
            AgentSessionRuntime.ReplacementResult result = runtime.fork(request.entryId(), request.position());
            if (result.cancelled()) {
                return SessionReplacement.error(cancelledReplacementMessage("Session fork", result));
            }
            AgentSession forkedSession = runtime.session();
            StringBuilder message = new StringBuilder("Session fork\nstatus: forked\nposition: ")
                    .append(request.position().name().toLowerCase(Locale.ROOT))
                    .append("\nsession: ")
                    .append(forkedSession.sessionManager().sessionId())
                    .append("\nprevious: ")
                    .append(displayPath(result.previousSessionFile()))
                    .append("\ncurrent: ")
                    .append(displayPath(result.currentSessionFile()));
            if (result.selectedText() != null && !result.selectedText().isBlank()) {
                message.append("\nselected: ").append(previewText(result.selectedText()));
            }
            return new SessionReplacement(forkedSession, message.toString());
        } catch (Exception e) {
            return SessionReplacement.error("Session fork\nerror: " + e.getMessage()
                    + "\nusage: /fork [before|at] <entryId>");
        }
    }

    private static ForkRequest parseForkRequest(String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (trimmed.isEmpty()) {
            return ForkRequest.error("Session fork\nerror: missing entry id\nusage: /fork [before|at] <entryId>");
        }
        String[] parts = trimmed.split("\\s+", 2);
        String first = parts[0].toLowerCase(Locale.ROOT);
        AgentSessionRuntime.ForkPosition position = AgentSessionRuntime.ForkPosition.BEFORE;
        String entryId = trimmed;
        if ("before".equals(first) || "at".equals(first)) {
            if (parts.length < 2 || parts[1].isBlank()) {
                return ForkRequest.error("Session fork\nerror: missing entry id\nusage: /fork [before|at] <entryId>");
            }
            position = "at".equals(first)
                    ? AgentSessionRuntime.ForkPosition.AT
                    : AgentSessionRuntime.ForkPosition.BEFORE;
            entryId = parts[1].trim();
        }
        return new ForkRequest(position, entryId, null);
    }

    private record ForkRequest(AgentSessionRuntime.ForkPosition position, String entryId, String error) {
        static ForkRequest error(String message) {
            return new ForkRequest(null, null, message);
        }
    }

    static SessionReplacement handleClone(AgentSessionRuntime runtime, AgentSession currentSession) {
        try {
            String leafId = currentSession.sessionManager().leafId().orElse(null);
            AgentSessionRuntime.ReplacementResult result = leafId == null
                    ? runtime.newSession(currentSession.sessionFile().orElse(null))
                    : runtime.fork(leafId, AgentSessionRuntime.ForkPosition.AT);
            if (result.cancelled()) {
                return SessionReplacement.error(cancelledReplacementMessage("Session clone", result));
            }
            AgentSession clonedSession = runtime.session();
            String message = "Session clone\nstatus: cloned\nsession: "
                    + clonedSession.sessionManager().sessionId()
                    + "\nprevious: " + displayPath(result.previousSessionFile())
                    + "\ncurrent: " + displayPath(result.currentSessionFile());
            return new SessionReplacement(clonedSession, message);
        } catch (Exception e) {
            return SessionReplacement.error("Session clone\nerror: " + e.getMessage());
        }
    }

    static SessionReplacement handleNew(AgentSessionRuntime runtime, AgentSession currentSession, String arguments) {
        String name = arguments == null ? "" : arguments.trim();
        try {
            AgentSessionRuntime.ReplacementResult result = runtime.newSession(currentSession.sessionFile().orElse(null));
            if (result.cancelled()) {
                return SessionReplacement.error(cancelledReplacementMessage("Session new", result));
            }
            AgentSession newSession = runtime.session();
            if (!name.isEmpty()) {
                newSession.sessionManager().appendSessionInfo(name);
            }
            String message = "Session new\nstatus: created"
                    + "\nsession: " + newSession.sessionManager().sessionId()
                    + "\nname: " + newSession.sessionManager().sessionName().orElse("none")
                    + "\nprevious: " + displayPath(result.previousSessionFile())
                    + "\ncurrent: " + displayPath(result.currentSessionFile());
            return new SessionReplacement(newSession, message);
        } catch (Exception e) {
            return SessionReplacement.error("Session new\nerror: " + e.getMessage()
                    + "\nusage: /new [name]");
        }
    }

    static String handleCompact(AgentSession session, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (!trimmed.isEmpty()) {
            return "Session compact\nerror: unknown argument: " + trimmed + "\nusage: /compact";
        }
        try {
            AgentSession.CompactionResult result = session.compactNow();
            if (!result.compacted()) {
                return "Session compact\nstatus: skipped"
                        + "\nreason: no compactable history";
            }
            return "Session compact\nstatus: compacted"
                    + "\nentry: " + result.entryId()
                    + "\nfirst kept: " + result.firstKeptEntryId()
                    + "\nsummarized messages: " + result.summarizedMessages()
                    + "\nturn prefix messages: " + result.turnPrefixMessages()
                    + "\ntokens before: " + result.tokensBefore()
                    + "\nsummary chars: " + result.summary().length();
        } catch (Exception e) {
            return "Session compact\nerror: " + e.getMessage()
                    + "\nusage: /compact";
        }
    }

    static SessionReplacement handleResume(AgentSessionRuntime runtime, AgentSession currentSession, String arguments) {
        String trimmed = arguments == null ? "" : arguments.trim();
        if (!currentSession.sessionManager().isPersisted()) {
            return SessionReplacement.error("Session resume\nerror: current session is in-memory and has no session directory");
        }
        try {
            Path sessionDir = currentSession.sessionManager().sessionDir();
            ResumeQuery query = parseResumeQuery(trimmed);
            List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions =
                    loadResumeSessions(runtime.services().cwd(), sessionDir, query.all());
            List<works.earendil.pi.codingagent.session.SessionFileInfo> visibleSessions =
                    filterResumeSessions(sessions, query.filter());
            if (query.command().isEmpty()) {
                return SessionReplacement.error(renderResumeList(currentSession, visibleSessions, query));
            }
            String lower = query.command().toLowerCase(Locale.ROOT);
            if (lower.startsWith("rename ")) {
                return SessionReplacement.error(handleResumeRename(runtime, currentSession, sessionDir, query.all(), sessions,
                        query.command().substring("rename".length()).trim()));
            }
            if (lower.startsWith("delete ")) {
                return SessionReplacement.error(handleResumeDelete(runtime, currentSession, sessions,
                        query.command().substring("delete".length()).trim()));
            }
            Path targetPath = resolveResumeTarget(query.command(), runtime.services().cwd(), sessions);
            AgentSessionRuntime.ReplacementResult result = runtime.switchSession(targetPath,
                    query.all() ? null : runtime.services().cwd());
            if (result.cancelled()) {
                return SessionReplacement.error(cancelledReplacementMessage("Session resume", result));
            }
            AgentSession resumedSession = runtime.session();
            String message = "Session resume\nstatus: resumed\nsession: "
                    + resumedSession.sessionManager().sessionId()
                    + "\nprevious: " + displayPath(result.previousSessionFile())
                    + "\ncurrent: " + displayPath(result.currentSessionFile());
            return new SessionReplacement(resumedSession, message);
        } catch (Exception e) {
            return SessionReplacement.error("Session resume\nerror: " + e.getMessage()
                    + "\nusage: /resume [--all] [index|id|path] | /resume [--all] find <query> | /resume [--all] rename <target> <name> | /resume [--all] delete <target>");
        }
    }

    private static String cancelledReplacementMessage(String title, AgentSessionRuntime.ReplacementResult result) {
        String reason = result.cancelReason() == null || result.cancelReason().isBlank()
                ? "cancelled by extension"
                : result.cancelReason();
        return title + "\nstatus: cancelled\nreason: " + reason
                + "\ncurrent: " + displayPath(result.currentSessionFile());
    }

    private static List<works.earendil.pi.codingagent.session.SessionFileInfo> loadResumeSessions(
            Path cwd, Path sessionDir, boolean all) throws IOException {
        if (!all) {
            return SessionManager.list(cwd, sessionDir, null);
        }
        Map<Path, works.earendil.pi.codingagent.session.SessionFileInfo> byPath = new LinkedHashMap<>();
        for (var info : SessionManager.listSessionsFromDir(sessionDir, null)) {
            byPath.put(info.path(), info);
        }
        Path root = sessionDir == null ? null : sessionDir.getParent();
        if (root != null) {
            for (var info : SessionManager.listAll(root, null)) {
                byPath.put(info.path(), info);
            }
        }
        return byPath.values().stream()
                .sorted(Comparator.comparing(
                        works.earendil.pi.codingagent.session.SessionFileInfo::modified).reversed())
                .toList();
    }

    private static List<works.earendil.pi.codingagent.session.SessionFileInfo> filterResumeSessions(
            List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions, String filter) {
        if (filter == null || filter.isBlank()) {
            return sessions;
        }
        String needle = filter.toLowerCase(Locale.ROOT);
        return sessions.stream()
                .filter(info -> resumeSearchText(info).contains(needle))
                .toList();
    }

    private static String resumeSearchText(works.earendil.pi.codingagent.session.SessionFileInfo info) {
        return String.join("\n",
                info.id() == null ? "" : info.id(),
                info.name() == null ? "" : info.name(),
                info.cwd() == null ? "" : info.cwd().toString(),
                info.firstMessage() == null ? "" : info.firstMessage(),
                info.allMessagesText() == null ? "" : info.allMessagesText()
        ).toLowerCase(Locale.ROOT);
    }

    private static String handleResumeRename(AgentSessionRuntime runtime, AgentSession currentSession, Path sessionDir,
                                             boolean preserveTargetCwd,
                                             List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions,
                                             String arguments) throws IOException {
        String[] parts = splitTargetAndRest(arguments);
        if (parts[0].isBlank() || parts[1].isBlank()) {
            return "Session resume\nerror: missing target or name"
                    + "\nusage: /resume rename <index|id|path> <name>";
        }
        Path targetPath = resolveResumeTarget(parts[0], runtime.services().cwd(), sessions);
        String name = parts[1].trim();
        String entryId;
        Path currentPath = currentSession.sessionFile().orElse(null);
        if (currentPath != null && currentPath.equals(targetPath)) {
            entryId = currentSession.setSessionName(name);
        } else {
            SessionManager targetManager = SessionManager.open(targetPath, sessionDir,
                    preserveTargetCwd ? null : runtime.services().cwd());
            entryId = targetManager.appendSessionInfo(name);
        }
        return "Session resume\nstatus: renamed"
                + "\nsession: " + SessionManager.buildSessionInfo(targetPath)
                .map(works.earendil.pi.codingagent.session.SessionFileInfo::id)
                .orElse(targetPath.getFileName().toString())
                + "\nname: " + name
                + "\nentry: " + entryId
                + "\nfile: " + targetPath;
    }

    private static String handleResumeDelete(AgentSessionRuntime runtime, AgentSession currentSession,
                                             List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions,
                                             String arguments) throws IOException {
        String target = arguments == null ? "" : arguments.trim();
        if (target.isBlank()) {
            return "Session resume\nerror: missing target"
                    + "\nusage: /resume delete <index|id|path>";
        }
        Path targetPath = resolveResumeTarget(target, runtime.services().cwd(), sessions);
        Path currentPath = currentSession.sessionFile().orElse(null);
        if (currentPath != null && currentPath.equals(targetPath)) {
            return "Session resume\nerror: refusing to delete the current session"
                    + "\nusage: switch to another session before deleting this one";
        }
        String sessionId = SessionManager.buildSessionInfo(targetPath)
                .map(works.earendil.pi.codingagent.session.SessionFileInfo::id)
                .orElse(targetPath.getFileName().toString());
        Files.delete(targetPath);
        return "Session resume\nstatus: deleted"
                + "\nsession: " + sessionId
                + "\nfile: " + targetPath;
    }

    private static String renderResumeList(AgentSession currentSession,
                                           List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions,
                                           ResumeQuery query) {
        StringBuilder out = new StringBuilder("Session resume\n");
        if (sessions.isEmpty()) {
            out.append("status: no sessions");
            if (query.all()) {
                out.append("\nscope: all");
            }
            if (query.filter() != null && !query.filter().isBlank()) {
                out.append("\nfilter: ").append(query.filter());
            }
            return out.toString();
        }
        out.append("status: choose a session\n")
                .append("scope: ").append(query.all() ? "all" : "project").append('\n');
        if (query.filter() != null && !query.filter().isBlank()) {
            out.append("filter: ").append(query.filter()).append('\n');
        }
        out.append("usage: /resume [--all] [index|id|path] | /resume [--all] find <query> | /resume [--all] rename <target> <name> | /resume [--all] delete <target>\n");
        Path currentPath = currentSession.sessionFile().orElse(null);
        for (int i = 0; i < sessions.size(); i++) {
            var info = sessions.get(i);
            boolean current = currentPath != null && currentPath.equals(info.path());
            out.append(i + 1)
                    .append(current ? ". * " : ".   ")
                    .append(info.id());
            if (info.name() != null && !info.name().isBlank()) {
                out.append(" \"").append(info.name()).append("\"");
            }
            out.append(" messages=").append(info.messageCount())
                    .append(" modified=").append(info.modified());
            if (query.all() && info.cwd() != null) {
                out.append(" cwd=").append(info.cwd());
            }
            if (info.firstMessage() != null && !info.firstMessage().isBlank()) {
                out.append(" first=").append(previewText(info.firstMessage()));
            }
            out.append('\n');
        }
        return out.toString().stripTrailing();
    }

    private static String[] splitTargetAndRest(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            return new String[]{"", ""};
        }
        String[] parts = trimmed.split("\\s+", 2);
        return new String[]{parts[0], parts.length > 1 ? parts[1] : ""};
    }

    private static ResumeQuery parseResumeQuery(String arguments) {
        String rest = arguments == null ? "" : arguments.trim();
        boolean all = false;
        boolean consumed = true;
        while (consumed && !rest.isEmpty()) {
            consumed = false;
            String lower = rest.toLowerCase(Locale.ROOT);
            if (lower.equals("--all") || lower.equals("all")) {
                all = true;
                rest = "";
                consumed = true;
            } else if (lower.startsWith("--all ")) {
                all = true;
                rest = rest.substring("--all".length()).trim();
                consumed = true;
            } else if (lower.startsWith("all ")) {
                all = true;
                rest = rest.substring("all".length()).trim();
                consumed = true;
            }
        }
        String lower = rest.toLowerCase(Locale.ROOT);
        if (lower.startsWith("find ")) {
            return new ResumeQuery(all, rest.substring("find".length()).trim(), "");
        }
        return new ResumeQuery(all, null, rest);
    }

    private record ResumeQuery(boolean all, String filter, String command) {
        public ResumeQuery {
            filter = filter == null || filter.isBlank() ? null : filter.trim();
            command = command == null ? "" : command.trim();
        }
    }

    private static Path resolveResumeTarget(String target, Path cwd,
                                            List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions) {
        try {
            int index = Integer.parseInt(target);
            if (index < 1 || index > sessions.size()) {
                throw new IllegalArgumentException("Session index out of range: " + target);
            }
            return sessions.get(index - 1).path();
        } catch (NumberFormatException ignored) {
        }
        Path path = PathUtils.resolvePath(target, cwd, PathUtils.PathInputOptions.cli());
        if (Files.isRegularFile(path)) {
            return path;
        }
        List<works.earendil.pi.codingagent.session.SessionFileInfo> matches = sessions.stream()
                .filter(info -> info.id().equals(target) || info.id().startsWith(target))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Session not found by index, path, or id: " + target);
        }
        if (matches.size() > 1) {
            String ids = String.join(", ", matches.stream().map(
                    works.earendil.pi.codingagent.session.SessionFileInfo::id).toList());
            throw new IllegalArgumentException("Session id is ambiguous: " + target + " matches " + ids);
        }
        return matches.getFirst().path();
    }

    static SessionReplacement handleReload(AgentSessionRuntime runtime) {
        try {
            runtime.services().settingsManager().reload();
            runtime.services().authStorage().reload();
            runtime.services().resourceLoader().reload();
            runtime.services().modelRegistry().refresh();
            AgentSessionRuntime.ReplacementResult result = runtime.reloadCurrent("reload");
            AgentSession reloadedSession = runtime.session();
            int skillCount = reloadedSession.skills().size();
            int toolCount = reloadedSession.tools().size();
            int modelCount = runtime.services().modelRegistry().getAll().size();
            String message = "Session reload\nstatus: reloaded"
                    + "\nsession: " + reloadedSession.sessionManager().sessionId()
                    + "\nprevious: " + displayPath(result.previousSessionFile())
                    + "\ncurrent: " + displayPath(result.currentSessionFile())
                    + "\nskills: " + skillCount
                    + "\ntools: " + toolCount
                    + "\nmodels: " + modelCount;
            return new SessionReplacement(reloadedSession, message);
        } catch (Exception e) {
            return SessionReplacement.error("Session reload\nerror: " + e.getMessage());
        }
    }

    static String renderSessionTree(AgentSession session) {
        SessionManager manager = session.sessionManager();
        String currentLeaf = manager.leafId().orElse("root");
        StringBuilder out = new StringBuilder();
        out.append("Session tree\n")
                .append("session: ").append(manager.sessionId()).append('\n')
                .append("current: ").append(currentLeaf).append('\n')
                .append("entries: ").append(manager.entries().size()).append('\n');
        List<SessionManager.SessionTreeNode> roots = manager.tree();
        if (roots.isEmpty()) {
            out.append("(empty)");
            return out.toString();
        }
        for (int i = 0; i < roots.size(); i++) {
            appendTreeNode(out, roots.get(i), "", i == roots.size() - 1, currentLeaf);
        }
        return out.toString();
    }

    private static void appendTreeNode(StringBuilder out, SessionManager.SessionTreeNode node, String prefix,
                                       boolean last, String currentLeaf) {
        SessionEntry entry = node.entry();
        boolean current = entry.id().equals(currentLeaf);
        out.append(prefix)
                .append(last ? "`- " : "|- ")
                .append(current ? "* " : "  ")
                .append(entry.id())
                .append(" ")
                .append(entrySummary(entry));
        if (node.label() != null && !node.label().isBlank()) {
            out.append(" [").append(node.label().trim()).append("]");
        }
        out.append('\n');
        String childPrefix = prefix + (last ? "   " : "|  ");
        List<SessionManager.SessionTreeNode> children = node.children();
        for (int i = 0; i < children.size(); i++) {
            appendTreeNode(out, children.get(i), childPrefix, i == children.size() - 1, currentLeaf);
        }
    }

    private static String entrySummary(SessionEntry entry) {
        return switch (entry) {
            case SessionEntry.MessageEntry message -> "message " + message.message().path("role").asText("unknown")
                    + treeSourceLabel(message.message().path("source").asText(""))
                    + " " + previewMessage(message.message().get("content"));
            case SessionEntry.ThinkingLevelChangeEntry thinking -> "thinking " + thinking.thinkingLevel();
            case SessionEntry.ModelChangeEntry model -> "model " + model.provider() + "/" + model.modelId();
            case SessionEntry.ActiveToolsChangeEntry tools -> "active_tools " + tools.activeToolNames();
            case SessionEntry.CompactionEntry compaction -> "compaction " + previewText(compaction.summary());
            case SessionEntry.BranchSummaryEntry summary -> "branch_summary " + previewText(summary.summary());
            case SessionEntry.CustomEntry custom -> "custom " + custom.customType();
            case SessionEntry.CustomMessageEntry custom -> "custom_message " + custom.customType()
                    + treeSourceLabel(custom.source());
            case SessionEntry.LabelEntry label -> "label " + label.targetId();
            case SessionEntry.SessionInfoEntry info -> "session_info " + previewText(info.name());
            case SessionEntry.LeafEntry leaf -> "leaf " + (leaf.targetId() == null ? "root" : leaf.targetId());
        };
    }

    private static String treeSourceLabel(String source) {
        if (source == null || source.isBlank()) {
            return "";
        }
        return " source=" + previewText(source);
    }

    private static String previewMessage(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return previewText(content.asText());
        }
        if (content.isArray()) {
            StringBuilder text = new StringBuilder();
            for (JsonNode part : content) {
                if (part.isTextual()) {
                    text.append(part.asText());
                } else if (part.has("text")) {
                    text.append(part.path("text").asText());
                }
                if (text.length() > 0) {
                    text.append(' ');
                }
            }
            return previewText(text.toString());
        }
        return previewText(content.toString());
    }

    private static String previewText(String text) {
        String normalized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 80) {
            return normalized;
        }
        return normalized.substring(0, 77) + "...";
    }

    static String renderOrchestratorStatus(String commandArguments) {
        return renderOrchestratorStatus(commandArguments, null, null, null);
    }

    private static String renderOrchestratorStatus(String commandArguments, OrchestratorEventTailer eventTailer,
                                                   OrchestratorLogFollowTailer logTailer,
                                                   SkillDiagnosticHistory skillDiagnostics) {
        try {
            if (commandArguments == null || commandArguments.isBlank()) {
                return OrchestratorRuntime.shared().statusReporter().snapshot().render();
            }
            String[] parts = commandArguments.trim().split("\\s+");
            if ("events".equals(parts[0]) || "subscribe".equals(parts[0])) {
                return renderOrchestratorEventCommand(parts, eventTailer);
            }
            if ("dashboard".equals(parts[0]) || "dash".equals(parts[0])) {
                return renderOrchestratorDashboardCommand(parts, skillDiagnostics);
            }
            if (!"tail".equals(parts[0])) {
                return "Orchestrator status\nerror: unknown argument: " + commandArguments.trim()
                        + "\nusage: /orchestrator-status [dashboard [instanceId] [events] [filters] | tail [instanceId] [lines] | tail --follow [instanceId] | tail --stop | events [instanceId|stop]]";
            }
            if (parts.length >= 2 && ("--follow".equals(parts[1]) || "-f".equals(parts[1]))) {
                return renderOrchestratorLogFollowCommand(parts, logTailer);
            }
            if (parts.length >= 2 && ("--stop".equals(parts[1]) || "stop".equals(parts[1]))) {
                return logTailer == null
                        ? "Orchestrator log follow\nerror: live log tail is only available in interactive mode"
                        : logTailer.stop();
            }
            OrchestratorStatusReporter reporter = OrchestratorRuntime.shared().statusReporter();
            TailRequest tailRequest = parseTailRequest(parts);
            return reporter.tailLatestLog(tailRequest.instanceId(), tailRequest.lines()).render();
        } catch (IOException | RuntimeException e) {
            return "Orchestrator status\nerror: " + e.getMessage();
        }
    }

    private static String renderOrchestratorEventCommand(String[] parts, OrchestratorEventTailer eventTailer) {
        if (eventTailer == null) {
            return "Orchestrator events\nerror: live event subscription is only available in interactive mode";
        }
        String argument = parts.length >= 2 ? parts[1] : "";
        if ("stop".equalsIgnoreCase(argument) || "off".equalsIgnoreCase(argument)
                || "unsubscribe".equalsIgnoreCase(argument)) {
            return eventTailer.stop();
        }
        return eventTailer.start(argument);
    }

    private static String renderOrchestratorDashboardCommand(String[] parts, SkillDiagnosticHistory skillDiagnostics)
            throws IOException {
        DashboardRequest request = parseDashboardRequest(parts);
        OrchestratorRuntime runtime = OrchestratorRuntime.shared();
        var dashView = runtime.statusReporter()
                .dashboard(runtime.supervisor().recentRpcEvents(request.instanceId(), request.events()),
                        request.instanceId(), request.events(), 8);
        String rendered = request.interactive() ? dashView.renderInteractive() : dashView.render();
        if (skillDiagnostics == null) {
            return rendered;
        }
        return rendered + "\n\n" + renderSkillDiagnosticsDashboard(skillDiagnostics, request.skillFilter());
    }

    private static String renderSkillDiagnosticsDashboard(SkillDiagnosticHistory history,
                                                          SkillDiagnosticHistory.Filter filter) {
        SkillDiagnosticHistory.Filter normalized = filter == null ? SkillDiagnosticHistory.Filter.empty() : filter;
        List<SkillDiagnosticHistory.Entry> entries = history.entries(normalized);
        List<SkillLoader.SkillTriggerMatch> matches = entries.stream()
                .flatMap(entry -> entry.matches().stream())
                .toList();
        long visible = matches.stream().filter(SkillLoader.SkillTriggerMatch::modelVisible).count();
        long manual = matches.size() - visible;
        StringBuilder out = new StringBuilder();
        out.append("skill diagnostics\n");
        out.append("filter: ").append(normalized.describe()).append("\n");
        out.append("entries: ").append(entries.size())
                .append(" | matches: ").append(matches.size())
                .append(" | visible: ").append(visible)
                .append(" | manual-only: ").append(manual).append("\n");
        if (matches.isEmpty()) {
            out.append("- none");
            return out.toString();
        }
        out.append("top skills: ").append(summarizeCounts(matches.stream()
                .map(SkillLoader.SkillTriggerMatch::skillName)
                .toList(), 5)).append("\n");
        out.append("top reasons: ").append(summarizeCounts(matches.stream()
                .flatMap(match -> match.reasons().stream())
                .toList(), 5));
        return out.toString();
    }

    private static String summarizeCounts(List<String> values, int maxItems) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            counts.merge(value, 1, Integer::sum);
        }
        if (counts.isEmpty()) {
            return "none";
        }
        Comparator<Map.Entry<String, Integer>> byCountDescThenName = (left, right) -> {
            int count = Integer.compare(right.getValue(), left.getValue());
            return count != 0 ? count : left.getKey().compareTo(right.getKey());
        };
        return counts.entrySet().stream()
                .sorted(byCountDescThenName)
                .limit(Math.max(1, maxItems))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private static String renderOrchestratorLogFollowCommand(String[] parts, OrchestratorLogFollowTailer logTailer)
            throws IOException {
        if (logTailer == null) {
            return "Orchestrator log follow\nerror: live log tail is only available in interactive mode";
        }
        String instanceId = parts.length >= 3 ? parts[2] : "";
        return logTailer.start(instanceId);
    }

    private static TailRequest parseTailRequest(String[] parts) {
        String instanceId = null;
        int lines = 40;
        if (parts.length == 2) {
            Integer parsedLines = parsePositiveInt(parts[1]);
            if (parsedLines == null) {
                instanceId = parts[1];
            } else {
                lines = parsedLines;
            }
        } else if (parts.length >= 3) {
            instanceId = parts[1];
            Integer parsedLines = parsePositiveInt(parts[2]);
            if (parsedLines == null) {
                throw new IllegalArgumentException("tail lines must be a positive integer: " + parts[2]);
            }
            lines = parsedLines;
        }
        return new TailRequest(instanceId, Math.min(500, lines));
    }

    private static Integer parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record TailRequest(String instanceId, int lines) {
    }

    private static DashboardRequest parseDashboardRequest(String[] parts) {
        String instanceId = null;
        int events = 20;
        boolean interactive = false;
        List<String> cleanParts = new java.util.ArrayList<>();
        for (String part : parts) {
            if ("--live".equalsIgnoreCase(part) || "--interactive".equalsIgnoreCase(part)) {
                interactive = true;
            } else {
                cleanParts.add(part);
            }
        }
        String[] p = cleanParts.toArray(new String[0]);
        int index = 1;
        if (p.length > index && !isFilterToken(p[index])) {
            Integer parsedEvents = parsePositiveInt(p[index]);
            if (parsedEvents == null) {
                instanceId = p[index];
            } else {
                events = parsedEvents;
            }
            index++;
        }
        if (p.length > index && !isFilterToken(p[index])) {
            Integer parsedEvents = parsePositiveInt(p[index]);
            if (parsedEvents == null) {
                throw new IllegalArgumentException("dashboard events must be a positive integer: " + p[index]);
            }
            events = parsedEvents;
            index++;
        }
        FilterParseResult filter = parseSkillDiagnosticFilter(p, index,
                "usage: /orchestrator-status dashboard [instanceId] [events] [skill=<name>] [model=visible|manual] [reason=<text>]");
        if (filter.error() != null) {
            throw new IllegalArgumentException(filter.error().replace('\n', ' '));
        }
        if (filter.branch() != null && !filter.branch().isBlank()) {
            throw new IllegalArgumentException("Skill trigger diagnostics branch filter is not supported in dashboard");
        }
        return new DashboardRequest(instanceId, Math.min(200, events), filter.filter(), interactive);
    }

    private static boolean isFilterToken(String value) {
        return value != null && value.contains("=");
    }

    private record DashboardRequest(String instanceId, int events, SkillDiagnosticHistory.Filter skillFilter, boolean interactive) {
    }

    private record FilterParseResult(SkillDiagnosticHistory.Filter filter, String branch, String error) {
        private static FilterParseResult error(String error) {
            return new FilterParseResult(SkillDiagnosticHistory.Filter.empty(), "", error);
        }
    }

    private record SourceOptions(int limit, boolean includeEmpty, String error) {
        private static SourceOptions error(String error) {
            return new SourceOptions(20, false, error);
        }
    }

    static final class OrchestratorEventTailer implements AutoCloseable {
        private final PrintStream out;
        private final Supplier<OrchestratorSupervisor> supervisorSupplier;
        private OrchestratorSupervisor.RpcEventSubscription subscription;
        private String instanceId;

        OrchestratorEventTailer(PrintStream out, Supplier<OrchestratorSupervisor> supervisorSupplier) {
            this.out = out;
            this.supervisorSupplier = supervisorSupplier;
        }

        synchronized String start(String requestedInstanceId) {
            close();
            instanceId = requestedInstanceId == null || requestedInstanceId.isBlank() ? null : requestedInstanceId;
            subscription = supervisorSupplier.get().subscribeRpcEvents(instanceId, event -> {
                synchronized (out) {
                    out.println();
                    InteractiveOutputRenderer.renderOrchestratorEvent(out, event, terminalColumns());
                    out.flush();
                }
            });
            return "Orchestrator events\nsubscribed: "
                    + (instanceId == null ? "all instances" : instanceId)
                    + "\nstop: /orchestrator-status events stop";
        }

        synchronized String stop() {
            if (subscription == null || !subscription.isActive()) {
                subscription = null;
                instanceId = null;
                return "Orchestrator events\nsubscription: none";
            }
            close();
            return "Orchestrator events\nsubscription: stopped";
        }

        @Override
        public synchronized void close() {
            if (subscription != null) {
                subscription.close();
            }
            subscription = null;
            instanceId = null;
        }
    }

    static final class OrchestratorLogFollowTailer implements AutoCloseable {
        private final PrintStream out;
        private final Supplier<OrchestratorStorage> storageSupplier;
        private OrchestratorLogTailer tailer;
        private String instanceId;

        OrchestratorLogFollowTailer(PrintStream out, Supplier<OrchestratorStorage> storageSupplier) {
            this.out = out;
            this.storageSupplier = storageSupplier;
        }

        synchronized String start(String requestedInstanceId) throws IOException {
            close();
            instanceId = requestedInstanceId == null || requestedInstanceId.isBlank() ? null : requestedInstanceId;
            tailer = new OrchestratorLogTailer(storageSupplier.get(), instanceId, line -> {
                synchronized (out) {
                    out.println();
                    InteractiveOutputRenderer.renderOrchestratorLogLine(out, line, terminalColumns());
                    out.flush();
                }
            });
            tailer.start();
            return "Orchestrator log follow\nsubscribed: "
                    + (instanceId == null ? "all current stderr logs" : instanceId)
                    + "\nstop: /orchestrator-status tail --stop";
        }

        synchronized String stop() {
            if (tailer == null) {
                instanceId = null;
                return "Orchestrator log follow\nsubscription: none";
            }
            close();
            return "Orchestrator log follow\nsubscription: stopped";
        }

        synchronized int pollOnce() throws IOException {
            return tailer == null ? 0 : tailer.pollOnce();
        }

        @Override
        public synchronized void close() {
            if (tailer != null) {
                tailer.close();
            }
            tailer = null;
            instanceId = null;
        }
    }

    private static void printModels(AgentSessionRuntime runtime) {
        System.out.println("Available models:");
        for (Model model : runtime.services().modelRegistry().getAll()) {
            System.out.println(" - [" + model.provider() + "] " + model.modelId() + " (" + model.displayName() + ")");
        }
    }
}
