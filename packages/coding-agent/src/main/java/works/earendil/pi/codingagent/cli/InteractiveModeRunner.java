package works.earendil.pi.codingagent.cli;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.FooterDataProvider;
import works.earendil.pi.codingagent.core.GrillMeInterview;
import works.earendil.pi.codingagent.core.SkillDiagnosticHistory;
import works.earendil.pi.codingagent.core.SlashCommands;
import works.earendil.pi.codingagent.core.TeamworkPreview;
import works.earendil.pi.codingagent.core.Timings;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.codingagent.resources.SkillLoader;
import works.earendil.pi.common.json.JsonCodec;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.orchestrator.service.OrchestratorLogTailer;
import works.earendil.pi.orchestrator.service.OrchestratorRuntime;
import works.earendil.pi.orchestrator.service.OrchestratorStatusReporter;
import works.earendil.pi.orchestrator.service.OrchestratorSupervisor;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;

public final class InteractiveModeRunner {
    private static final int DEFAULT_TERMINAL_COLUMNS = 120;

    private InteractiveModeRunner() {
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
        System.out.println("  /grill-me [topic] Start an interview before design/implementation");
        System.out.println("  /grill-me answer <text> Record an interview answer and continue");
        System.out.println("  /grill-me status|reset Show or clear the active interview");
        System.out.println("  /skill-diagnostics [history|json|sources|picker|clear] [branch=<entryId>] [filters] Show, export, or clear skill trigger diagnostics");
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
            System.out.println("Skill trigger diagnostics\nerror: unknown argument: " + command
                    + "\nusage: /skill-diagnostics [history|json|sources|picker|clear] [branch=<entryId>] [skill=<name>] [model=visible|manual] [reason=<text>]");
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
        String rendered = runtime.statusReporter()
                .dashboard(runtime.supervisor().recentRpcEvents(request.instanceId(), request.events()),
                        request.instanceId(), request.events(), 8)
                .render();
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
        int index = 1;
        if (parts.length > index && !isFilterToken(parts[index])) {
            Integer parsedEvents = parsePositiveInt(parts[index]);
            if (parsedEvents == null) {
                instanceId = parts[index];
            } else {
                events = parsedEvents;
            }
            index++;
        }
        if (parts.length > index && !isFilterToken(parts[index])) {
            Integer parsedEvents = parsePositiveInt(parts[index]);
            if (parsedEvents == null) {
                throw new IllegalArgumentException("dashboard events must be a positive integer: " + parts[index]);
            }
            events = parsedEvents;
            index++;
        }
        FilterParseResult filter = parseSkillDiagnosticFilter(parts, index,
                "usage: /orchestrator-status dashboard [instanceId] [events] [skill=<name>] [model=visible|manual] [reason=<text>]");
        if (filter.error() != null) {
            throw new IllegalArgumentException(filter.error().replace('\n', ' '));
        }
        if (filter.branch() != null && !filter.branch().isBlank()) {
            throw new IllegalArgumentException("Skill trigger diagnostics branch filter is not supported in dashboard");
        }
        return new DashboardRequest(instanceId, Math.min(200, events), filter.filter());
    }

    private static boolean isFilterToken(String value) {
        return value != null && value.contains("=");
    }

    private record DashboardRequest(String instanceId, int events, SkillDiagnosticHistory.Filter skillFilter) {
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
