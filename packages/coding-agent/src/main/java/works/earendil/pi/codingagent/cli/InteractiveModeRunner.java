package works.earendil.pi.codingagent.cli;

import works.earendil.pi.agent.core.AgentEvent;
import works.earendil.pi.ai.model.Content;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.stream.AssistantMessageEvent;
import works.earendil.pi.codingagent.core.AgentSession;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public final class InteractiveModeRunner {

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

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
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
                    printHelp();
                    continue;
                }
                if ("/models".equalsIgnoreCase(trimmed)) {
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

                AutoCloseable unsubscribe = session.subscribe(event -> {
                    if (event instanceof AgentSession.AgentSessionEvent.AgentEventEnvelope env) {
                        if (env.event() instanceof AgentEvent.MessageUpdate mu &&
                                mu.assistantMessageEvent() instanceof AssistantMessageEvent.ContentDelta cd &&
                                cd.content() instanceof Content.Text t) {
                            System.out.print(t.text());
                            System.out.flush();
                        }
                    }
                });

                try {
                    session.prompt(trimmed);
                    System.out.println();
                } catch (Exception e) {
                    System.err.println("\nError executing prompt: " + e.getMessage());
                } finally {
                    try {
                        unsubscribe.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("Interactive session error: " + e.getMessage());
            return 1;
        }
    }

    private static void printHelp() {
        System.out.println("Available commands:");
        System.out.println("  /help           Show this help message");
        System.out.println("  /models         List available providers and models");
        System.out.println("  /model <id>     Switch model (e.g. /model deepseek-v4-flash)");
        System.out.println("  /clear          Clear terminal screen");
        System.out.println("  /exit, /quit    Exit interactive console");
    }

    private static void printModels(AgentSessionRuntime runtime) {
        System.out.println("Available models:");
        for (Model model : runtime.services().modelRegistry().getAll()) {
            System.out.println(" - [" + model.provider() + "] " + model.modelId() + " (" + model.displayName() + ")");
        }
    }
}
