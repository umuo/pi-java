package works.earendil.pi.codingagent.cli;

import picocli.CommandLine;
import works.earendil.pi.agent.core.AgentTool;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.codingagent.core.AgentSessionRuntime;
import works.earendil.pi.codingagent.core.AgentSessionServices;
import works.earendil.pi.codingagent.core.extensions.ExtensionLoader;
import works.earendil.pi.codingagent.core.extensions.ExtensionRunner;
import works.earendil.pi.codingagent.core.export.HtmlExporter;
import works.earendil.pi.codingagent.pkg.PackageManagerCli;
import works.earendil.pi.codingagent.session.SessionManager;
import works.earendil.pi.codingagent.session.SessionPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public final class Main implements Runnable {
    private final CliArgs args;

    public Main(CliArgs args) {
        this.args = args;
    }

    public static void main(String[] rawArgs) {
        if (rawArgs.length > 0) {
            String subCmd = rawArgs[0].toLowerCase();
            if ("install".equals(subCmd) || "remove".equals(subCmd) || "uninstall".equals(subCmd) ||
                    "update".equals(subCmd) || "list".equals(subCmd)) {
                String[] subArgs = Arrays.copyOfRange(rawArgs, 1, rawArgs.length);
                int exitCode = PackageManagerCli.handleCommand(subCmd, subArgs);
                System.exit(exitCode);
                return;
            }
        }

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
            Path defaultSessionDir = cwd.resolve(".pi/sessions");
            if (args.export != null) {
                Path sessionDir = resolveSessionDir(args, defaultSessionDir, null);
                Path sessionPath = args.session != null
                        ? resolveSessionPath(args.session, cwd, sessionDir)
                        : SessionManager.findMostRecentSession(sessionDir, cwd)
                        .orElseThrow(() -> new IllegalArgumentException("No recent session found in " + sessionDir));
                Path outputPath = Paths.get(args.export);
                HtmlExporter.exportToFile(sessionPath, outputPath);
                System.out.println("Session successfully exported to HTML: " + outputPath);
                System.exit(0);
                return;
            }

            // Process @file injections in messages
            List<String> processedMessages = new ArrayList<>();
            for (String msg : args.messages) {
                if (msg.startsWith("@") && msg.length() > 1) {
                    Path filePath = cwd.resolve(msg.substring(1));
                    if (Files.exists(filePath)) {
                        String content = Files.readString(filePath, StandardCharsets.UTF_8);
                        processedMessages.add("File context (" + msg.substring(1) + "):\n```\n" + content + "\n```");
                    } else {
                        processedMessages.add(msg);
                    }
                } else {
                    processedMessages.add(msg);
                }
            }
            args.messages = processedMessages;

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

            Path sessionDir = resolveSessionDir(args, defaultSessionDir,
                    services.settingsManager().getSessionDir());
            SessionManager sessionManager = createStartupSessionManager(args, cwd, sessionDir);
            Model finalModel = selectedModel;
            ThinkingLevel finalThinking = thinking;

            AgentSessionRuntime runtime = AgentSessionRuntime.create(options -> {
                ExtensionRunner extensionRunner = loadExtensionRunner(args, services);
                List<AgentTool> extensionTools = extensionRunner.collectAgentTools();
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
                                extensionTools,
                                null,
                                extensionRunner
                        )
                );
                return new AgentSessionRuntime.CreateRuntimeResult(
                        sessionRes.session(),
                        services,
                        services.diagnostics(),
                        sessionRes.modelFallbackMessage()
                );
            }, new AgentSessionRuntime.CreateRuntimeOptions(cwd, agentDir, sessionManager, args.name != null ? args.name : "initial"));

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

    static Path resolveSessionDir(CliArgs args, Path defaultSessionDir, String settingsSessionDir) {
        if (args != null && args.sessionDir != null && !args.sessionDir.isBlank()) {
            return SessionPaths.normalizePath(args.sessionDir);
        }
        String envSessionDir = System.getenv("PI_CODING_AGENT_SESSION_DIR");
        if (envSessionDir != null && !envSessionDir.isBlank()) {
            return SessionPaths.normalizePath(envSessionDir);
        }
        if (settingsSessionDir != null && !settingsSessionDir.isBlank()) {
            return SessionPaths.normalizePath(settingsSessionDir);
        }
        return defaultSessionDir.toAbsolutePath().normalize();
    }

    static ExtensionRunner loadExtensionRunner(CliArgs args, AgentSessionServices services) {
        List<String> paths = new ArrayList<>();
        if (args == null || !args.noExtensions) {
            paths.addAll(services.settingsManager().getExtensionPaths());
        }
        if (args != null && args.extensions != null) {
            paths.addAll(args.extensions);
        }
        return new ExtensionRunner(ExtensionLoader.loadExtensions(paths, args != null && args.noExtensions,
                services.cwd()));
    }

    static SessionManager createStartupSessionManager(CliArgs args, Path cwd, Path sessionDir) throws Exception {
        SessionManager.NewSessionOptions options = new SessionManager.NewSessionOptions(
                blankToNull(args.sessionId), null);
        if (args.noSession) {
            return SessionManager.inMemory(cwd, options);
        }
        if (args.fork != null && !args.fork.isBlank()) {
            Path sourcePath = resolveSessionPath(args.fork, cwd, sessionDir);
            return SessionManager.forkFrom(sourcePath, cwd, sessionDir, options);
        }
        if (args.session != null && !args.session.isBlank()) {
            Path sessionPath = resolveSessionPath(args.session, cwd, sessionDir);
            return SessionManager.open(sessionPath, sessionDir, cwd);
        }
        if (args.continueSession || args.resume) {
            return SessionManager.continueRecent(cwd, sessionDir);
        }
        if (args.sessionId != null && !args.sessionId.isBlank()) {
            Optional<Path> existing = findLocalSessionByExactId(args.sessionId, cwd, sessionDir);
            if (existing.isPresent()) {
                return SessionManager.open(existing.get(), sessionDir, cwd);
            }
        }
        return SessionManager.create(cwd, sessionDir, options);
    }

    static Path resolveSessionPath(String value, Path cwd, Path sessionDir) throws Exception {
        Path candidate = SessionPaths.normalizePath(value);
        if (Files.isRegularFile(candidate)) {
            return candidate;
        }
        List<SessionManagerLookup> matches = SessionManager.list(cwd, sessionDir, null).stream()
                .filter(info -> info.id().equals(value) || info.id().startsWith(value))
                .map(info -> new SessionManagerLookup(info.id(), info.path()))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("Session not found by path or id: " + value);
        }
        if (matches.size() > 1) {
            String ids = String.join(", ", matches.stream().map(SessionManagerLookup::id).toList());
            throw new IllegalArgumentException("Session id is ambiguous: " + value + " matches " + ids);
        }
        return matches.getFirst().path();
    }

    private static Optional<Path> findLocalSessionByExactId(String sessionId, Path cwd, Path sessionDir)
            throws Exception {
        return SessionManager.list(cwd, sessionDir, null).stream()
                .filter(info -> info.id().equals(sessionId))
                .map(info -> info.path())
                .findFirst();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private record SessionManagerLookup(String id, Path path) {
    }
}
