package works.earendil.pi.codingagent.cli;

import picocli.CommandLine;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.AgentSessionServices;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Path;
import java.util.List;

public final class Main implements Runnable {
    private final CliArgs args;

    public Main(CliArgs args) {
        this.args = args;
    }

    public static void main(String[] rawArgs) {
        CliArgs cliArgs = new CliArgs();
        CommandLine cmd = new CommandLine(cliArgs);
        try {
            cmd.parseArgs(rawArgs);
            if (cmd.isUsageHelpRequested()) {
                cmd.usage(System.out);
                System.exit(0);
            }
            if (cmd.isVersionHelpRequested()) {
                cmd.printVersionHelp(System.out);
                System.exit(0);
            }
        } catch (CommandLine.ParameterException ex) {
            System.err.println(ex.getMessage());
            ex.getCommandLine().usage(System.err);
            System.exit(2);
        }
        new Main(cliArgs).run();
    }

    @Override
    public void run() {
        try {
            Path cwd = Path.of(".").toAbsolutePath().normalize();
            Path agentDir = cwd.resolve(".pi/agent");
            AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                    cwd, agentDir, null, null, null, null, null, true
            ));

            if (args.listModels) {
                services.modelRegistry().refresh();
                List<Model> models = services.modelRegistry().getAll();
                System.out.println("Available models:");
                for (Model m : models) {
                    System.out.printf(" - [%s] %s (%s)%n", m.provider(), m.modelId(), m.displayName());
                }
                System.exit(0);
                return;
            }

            ThinkingLevel thinking = ThinkingLevel.OFF;
            if (args.thinking != null) {
                try {
                    thinking = ThinkingLevel.valueOf(args.thinking.toUpperCase());
                } catch (IllegalArgumentException e) {
                    System.err.println("Warning: Invalid thinking level '" + args.thinking + "', defaulting to OFF");
                }
            }

            Model selectedModel = null;
            if (args.provider != null && args.model != null) {
                selectedModel = services.modelRegistry().find(args.provider, args.model).orElse(null);
            } else if (!services.modelRegistry().getAll().isEmpty()) {
                selectedModel = services.modelRegistry().getAll().get(0);
            }

            SessionManager sessionManager = SessionManager.create(cwd, cwd.resolve(".pi/sessions"));
            Model finalModel = selectedModel;
            ThinkingLevel finalThinking = thinking;

            AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
                AgentSessionServices.CreateSessionResult sessionRes = AgentSessionServices.createAgentSessionFromServices(
                        new AgentSessionServices.CreateSessionOptions(
                                services,
                                options.sessionManager(),
                                finalModel,
                                finalThinking,
                                List.of(),
                                args.tools,
                                List.of(),
                                args.noTools ? "*" : null,
                                List.of(),
                                null
                        )
                );
                return new AgentSessionRuntime.CreateRuntimeResult(
                        sessionRes.session(),
                        services,
                        services.diagnostics(),
                        sessionRes.modelFallbackMessage()
                );
            }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, "initial"));

            if (args.print || !args.messages.isEmpty()) {
                int code = PrintModeRunner.run(runtime, args);
                System.exit(code);
            } else if ("rpc".equalsIgnoreCase(args.mode)) {
                int code = RpcModeRunner.run(runtime, args);
                System.exit(code);
            } else {
                int code = InteractiveModeRunner.run(runtime, args);
                System.exit(code);
            }
        } catch (Exception e) {
            System.err.println("Fatal error starting Pi CLI: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
