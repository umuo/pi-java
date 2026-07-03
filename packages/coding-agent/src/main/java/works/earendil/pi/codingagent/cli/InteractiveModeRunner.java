package works.earendil.pi.codingagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.agent.session.SessionEntry;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.AuthStorage;
import works.earendil.pi.codingagent.core.FooterDataProvider;
import works.earendil.pi.codingagent.core.GrillMeInterview;
import works.earendil.pi.codingagent.core.ModelRegistry;
import works.earendil.pi.codingagent.core.SkillDiagnosticHistory;
import works.earendil.pi.codingagent.core.SlashCommands;
import works.earendil.pi.codingagent.core.TeamworkPreview;
import works.earendil.pi.codingagent.core.Timings;
import works.earendil.pi.codingagent.core.export.HtmlExporter;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.codingagent.tools.PathUtils;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.orchestrator.service.OrchestratorLogTailer;
import works.earendil.pi.orchestrator.service.OrchestratorRuntime;
import works.earendil.pi.orchestrator.service.OrchestratorStatusReporter;
import works.earendil.pi.orchestrator.service.OrchestratorSupervisor;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

public final class InteractiveModeRunner {
    private static final int DEFAULT_TERMINAL_COLUMNS = 120;
    private static volatile ClipboardWriter clipboardWriter = new SystemClipboardWriter();
    private static volatile GistSharer gistSharer = new GhCliGistSharer();

    private InteractiveModeRunner() {
    }

    static void setClipboardWriterForTesting(ClipboardWriter writer) {
        clipboardWriter = writer == null ? new SystemClipboardWriter() : writer;
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

                executePrompt(runtime, session, trimmed, skillDiagnostics);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Interactive session error: " + e.getMessage());
            return 1;
        }
    }

    static void executePrompt(AgentSessionRuntime runtime, AgentSession session, String prompt) {
        executePrompt(runtime, session, prompt, null);
    }

    private static void executePrompt(AgentSessionRuntime runtime, AgentSession session, String prompt,
                                      SkillDiagnosticHistory skillDiagnostics) {
        StringBuilder assistantBuffer = new StringBuilder();
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
                    InteractiveOutputRenderer.renderAssistantText(System.out, text, terminalColumns());
                    assistantBuffer.setLength(0);
                } else if (env.event() instanceof AgentEvent.ToolExecutionStart toolStart) {
                    InteractiveOutputRenderer.renderToolStart(System.out, toolStart.toolName(), toolStart.args(),
                            runtime.services().cwd(), terminalColumns());
                } else if (env.event() instanceof AgentEvent.ToolExecutionEnd toolEnd) {
                    Message.ToolResult result = new Message.ToolResult(toolEnd.toolCallId(), toolEnd.toolName(),
                            toolEnd.result().content(), toolEnd.error(), toolEnd.result().details(),
                            java.time.Instant.now());
                    InteractiveOutputRenderer.renderToolResult(System.out, result, terminalColumns());
                }
            } else if (event instanceof AgentSession.AgentSessionEvent.SkillTriggerDiagnostic diagnostic) {
                if (skillDiagnostics != null) {
                    skillDiagnostics.record(diagnostic);
                    persistSkillDiagnostics(skillDiagnostics, session);
                }
                InteractiveOutputRenderer.renderSkillTriggerDiagnostic(System.out, diagnostic, terminalColumns());
            }
        });

        try {
            Timings turnTimings = new Timings(true);
            turnTimings.resetTimings("turn");
            long startNanos = System.nanoTime();
            session.prompt(prompt);
            turnTimings.time("agent", "turn");
            System.out.println(turnLine(session.stats(), System.nanoTime() - startNanos,
                    turnTimings.timings("turn"), terminalColumns()));
        } catch (Exception e) {
            System.err.println("\nError executing prompt: " + e.getMessage());
        } finally {
            try {
                unsubscribe.close();
            } catch (Exception ignored) {
            }
        }
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
        System.out.println("  /login <provider> <api-key> Configure provider API key authentication");
        System.out.println("  /logout <provider> Remove stored or runtime provider authentication");
        System.out.println("  /export [path]  Export session as HTML, or copy raw JSONL when path ends with .jsonl");
        System.out.println("  /share [public|secret] Share session HTML as a GitHub gist via gh");
        System.out.println("  /copy           Copy the last assistant message to clipboard");
        System.out.println("  /name [text|clear] Show, set, or clear the current session name");
        System.out.println("  /session        Show current session info and stats");
        System.out.println("  /import <path>  Import a JSONL session file and resume it in the current project");
        System.out.println("  /tree           Show the current session branch tree and entry ids");
        System.out.println("  /fork [before|at] <entryId> Fork the current session at an entry id");
        System.out.println("  /clone          Clone the current active branch into a new session");
        System.out.println("  /new [name]     Start a new session, optionally with a display name");
        System.out.println("  /compact        Manually compact the current session context");
        System.out.println("  /resume [index|id|path] List sessions or resume a session");
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
        System.out.println("  /clear          Clear terminal screen");
        System.out.println("  /exit, /quit    Exit interactive console");
        List<SlashCommands.SlashCommandInfo> skillCommands = SlashCommands.skillCommands(
                runtime.services().resourceLoader().skills().skills());
        if (!skillCommands.isEmpty()) {
            System.out.println("Loaded skills:");
            for (SlashCommands.SlashCommandInfo command : skillCommands) {
                System.out.println("  /" + command.name() + " " + command.description());
            }
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
        out.append("usage: /login <provider> <api-key> | /login <provider> env <ENV_VAR>");
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
        }
        return out.toString();
    }

    private static String loginUsage(String error) {
        return "Provider authentication\nerror: " + error
                + "\nusage: /login <provider> <api-key> | /login <provider> env <ENV_VAR>";
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
            return "Provider authentication\nerror: too many arguments\nusage: /logout <provider>";
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
        List<String> providers = authStorage.list();
        StringBuilder out = new StringBuilder("Provider authentication\n");
        if (providers.isEmpty()) {
            return out.append("status: no stored providers\nusage: /logout <provider>").toString();
        }
        out.append("status: choose a provider\nusage: /logout <provider>\nproviders:");
        for (String provider : providers) {
            out.append("\n- ").append(provider).append(" (stored)");
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
                + "\nskills: " + session.skills().size()
                + "\ntools: " + session.tools().size();
    }

    @FunctionalInterface
    interface ClipboardWriter {
        void write(String text) throws Exception;
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
            List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions =
                    SessionManager.list(runtime.services().cwd(), sessionDir, null);
            if (trimmed.isEmpty()) {
                return SessionReplacement.error(renderResumeList(currentSession, sessions));
            }
            Path targetPath = resolveResumeTarget(trimmed, runtime.services().cwd(), sessions);
            AgentSessionRuntime.ReplacementResult result = runtime.switchSession(targetPath, runtime.services().cwd());
            AgentSession resumedSession = runtime.session();
            String message = "Session resume\nstatus: resumed\nsession: "
                    + resumedSession.sessionManager().sessionId()
                    + "\nprevious: " + displayPath(result.previousSessionFile())
                    + "\ncurrent: " + displayPath(result.currentSessionFile());
            return new SessionReplacement(resumedSession, message);
        } catch (Exception e) {
            return SessionReplacement.error("Session resume\nerror: " + e.getMessage()
                    + "\nusage: /resume [index|id|path]");
        }
    }

    private static String renderResumeList(AgentSession currentSession,
                                           List<works.earendil.pi.codingagent.session.SessionFileInfo> sessions) {
        StringBuilder out = new StringBuilder("Session resume\n");
        if (sessions.isEmpty()) {
            return out.append("status: no sessions").toString();
        }
        out.append("status: choose a session\nusage: /resume [index|id|path]\n");
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
            if (info.firstMessage() != null && !info.firstMessage().isBlank()) {
                out.append(" first=").append(previewText(info.firstMessage()));
            }
            out.append('\n');
        }
        return out.toString().stripTrailing();
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
                    + " " + previewMessage(message.message().get("content"));
            case SessionEntry.ThinkingLevelChangeEntry thinking -> "thinking " + thinking.thinkingLevel();
            case SessionEntry.ModelChangeEntry model -> "model " + model.provider() + "/" + model.modelId();
            case SessionEntry.ActiveToolsChangeEntry tools -> "active_tools " + tools.activeToolNames();
            case SessionEntry.CompactionEntry compaction -> "compaction " + previewText(compaction.summary());
            case SessionEntry.BranchSummaryEntry summary -> "branch_summary " + previewText(summary.summary());
            case SessionEntry.CustomEntry custom -> "custom " + custom.customType();
            case SessionEntry.CustomMessageEntry custom -> "custom_message " + custom.customType();
            case SessionEntry.LabelEntry label -> "label " + label.targetId();
            case SessionEntry.SessionInfoEntry info -> "session_info " + previewText(info.name());
            case SessionEntry.LeafEntry leaf -> "leaf " + (leaf.targetId() == null ? "root" : leaf.targetId());
        };
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
