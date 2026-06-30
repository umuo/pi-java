package works.earendil.pi.codingagent.cli;

import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.agent.core.AgentMessage;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Message;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.FooterDataProvider;
import works.earendil.pi.codingagent.core.GrillMePrompt;
import works.earendil.pi.codingagent.core.SlashCommands;
import works.earendil.pi.codingagent.core.TeamworkPreview;
import works.earendil.pi.codingagent.core.Timings;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.common.text.EastAsianWidth;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.service.OrchestratorStatusReporter;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Locale;
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
             BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            refreshFooterProviderCount(runtime, footer);
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
                        executePrompt(runtime, session, trimmed);
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
                        System.out.println("Starting /grill-me interview...");
                        executePrompt(runtime, session, GrillMePrompt.build(commandArguments));
                        continue;
                    }
                    if ("orchestrator-status".equals(commandName)) {
                        System.out.println(renderOrchestratorStatus());
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

                executePrompt(runtime, session, trimmed);
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Interactive session error: " + e.getMessage());
            return 1;
        }
    }

    static void executePrompt(AgentSessionRuntime runtime, AgentSession session, String prompt) {
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
        System.out.println("  /teamwork-preview [compact] Preview planned sub-agent roles");
        System.out.println("  /teamwork-preview run <objective> Execute planned sub-agents");
        System.out.println("  /orchestrator-status Show instances, logs, settings, and event stream status");
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

    private static String renderOrchestratorStatus() {
        try {
            OrchestratorStorage storage = new OrchestratorStorage(new OrchestratorConfig());
            return new OrchestratorStatusReporter(storage).snapshot().render();
        } catch (IOException | RuntimeException e) {
            return "Orchestrator status\nerror: " + e.getMessage();
        }
    }

    private static void printModels(AgentSessionRuntime runtime) {
        System.out.println("Available models:");
        for (Model model : runtime.services().modelRegistry().getAll()) {
            System.out.println(" - [" + model.provider() + "] " + model.modelId() + " (" + model.displayName() + ")");
        }
    }
}
