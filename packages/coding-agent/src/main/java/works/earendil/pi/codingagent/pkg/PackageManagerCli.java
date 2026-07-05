package works.earendil.pi.codingagent.pkg;

import works.earendil.pi.codingagent.config.SettingsManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class PackageManagerCli {

    private PackageManagerCli() {}

    public static int handleCommand(String command, String[] args) {
        Path cwd = Paths.get(".").toAbsolutePath().normalize();
        boolean local = false;
        String source = null;
        boolean updateSelf = false;
        boolean updateExtensions = false;
        boolean updateAll = false;
        String extensionSource = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-l".equals(arg) || "--local".equals(arg)) {
                local = true;
            } else if ("update".equalsIgnoreCase(command) && "--self".equals(arg)) {
                updateSelf = true;
            } else if ("update".equalsIgnoreCase(command) && "--extensions".equals(arg)) {
                updateExtensions = true;
            } else if ("update".equalsIgnoreCase(command) && "--all".equals(arg)) {
                updateAll = true;
            } else if ("update".equalsIgnoreCase(command) && "--extension".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
                    System.err.println("Usage: pi update [source|self|pi] [--self|--extensions|--all] [--extension <source>] [-l]");
                    return 1;
                }
                extensionSource = args[++i];
            } else if (!arg.startsWith("-") && source == null) {
                source = arg;
            }
        }

        try {
            Path agentDir = Paths.get(System.getProperty("user.home"), ".pi", "agent").toAbsolutePath().normalize();
            SettingsManager settingsManager = new SettingsManager(cwd, agentDir, local);
            switch (command.toLowerCase()) {
                case "config":
                    String action = source == null ? "list" : source.toLowerCase();
                    if ("list".equals(action)) {
                        System.out.println(PackageManager.listConfiguredPackages(local, settingsManager));
                        return 0;
                    }
                    if (!"enable".equals(action) && !"disable".equals(action)) {
                        System.err.println("Usage: pi config [list|enable|disable] [source type path] [-l]");
                        return 1;
                    }
                    List<String> positional = positionalArgs(args);
                    if (positional.size() < 4) {
                        System.err.println("Usage: pi config " + action + " <source> <extensions|skills|prompts|themes> <path> [-l]");
                        return 1;
                    }
                    System.out.println(PackageManager.configurePackageResource(positional.get(1), positional.get(2),
                            positional.get(3), "enable".equals(action), local, settingsManager));
                    return 0;
                case "install":
                    if (source == null) {
                        System.err.println("Usage: pi install <source> [-l]");
                        return 1;
                    }
                    System.out.println(PackageManager.installAndPersist(source, local, cwd, agentDir, settingsManager));
                    return 0;
                case "remove":
                case "uninstall":
                    if (source == null) {
                        System.err.println("Usage: pi remove <source> [-l]");
                        return 1;
                    }
                    System.out.println(PackageManager.removeAndPersist(source, local, cwd, agentDir, settingsManager));
                    return 0;
                case "list":
                    List<String> pkgs = PackageManager.list(local, cwd);
                    System.out.println("Installed packages (" + (local ? "local" : "global") + "):");
                    if (pkgs.isEmpty()) {
                        System.out.println("  (none)");
                    } else {
                        pkgs.forEach(p -> System.out.println("  - " + p));
                    }
                    return 0;
                case "update":
                    UpdateRequest updateRequest = parseUpdateRequest(source, updateSelf, updateExtensions,
                            updateAll, extensionSource);
                    if (updateRequest.error() != null) {
                        System.err.println(updateRequest.error());
                        System.err.println("Usage: pi update [source|self|pi] [--self|--extensions|--all] [--extension <source>] [-l]");
                        return 1;
                    }
                    System.out.println(runUpdate(updateRequest, local, cwd, agentDir, settingsManager));
                    return 0;
                default:
                    System.err.println("Unknown package command: " + command);
                    return 1;
            }
        } catch (Exception e) {
            System.err.println("Error processing command " + command + ": " + e.getMessage());
            return 1;
        }
    }

    private static UpdateRequest parseUpdateRequest(String source, boolean self, boolean extensions,
                                                    boolean all, String extensionSource) {
        int explicitTargets = (self ? 1 : 0) + (extensions ? 1 : 0) + (all ? 1 : 0)
                + (extensionSource == null ? 0 : 1);
        if (all && explicitTargets > 1) {
            return UpdateRequest.error("--all cannot be combined with --self, --extensions, or --extension");
        }
        if (extensionSource != null && (self || extensions || all)) {
            return UpdateRequest.error("--extension cannot be combined with --self, --extensions, or --all");
        }
        if (source != null && (all || extensionSource != null || self)) {
            return UpdateRequest.error("positional update targets cannot be combined with --self, --all, or --extension");
        }
        if (source != null && extensions && !isSelfSource(source)) {
            return UpdateRequest.error("positional package sources cannot be combined with --extensions");
        }
        if (extensionSource != null) {
            return new UpdateRequest(false, true, extensionSource, null);
        }
        if (source != null) {
            if (isSelfSource(source)) {
                return new UpdateRequest(true, extensions, null, null);
            }
            return new UpdateRequest(false, true, source, null);
        }
        if (all || (self && extensions)) {
            return new UpdateRequest(true, true, null, null);
        }
        if (extensions) {
            return new UpdateRequest(false, true, null, null);
        }
        return new UpdateRequest(true, false, null, null);
    }

    private static boolean isSelfSource(String source) {
        return "self".equalsIgnoreCase(source) || "pi".equalsIgnoreCase(source);
    }

    private static String runUpdate(UpdateRequest request, boolean local, Path cwd, Path agentDir,
                                    SettingsManager settingsManager) throws Exception {
        List<String> parts = new java.util.ArrayList<>();
        if (request.self()) {
            parts.add(PackageManager.update("self", local, cwd, agentDir, settingsManager));
        }
        if (request.extensions()) {
            String target = request.extensionSource() == null ? "all" : request.extensionSource();
            parts.add(PackageManager.update(target, local, cwd, agentDir, settingsManager));
        } else if (request.self()) {
            parts.add("Packages are skipped. Run pi update --extensions to update packages.");
        }
        return String.join("\n", parts);
    }

    private record UpdateRequest(boolean self, boolean extensions, String extensionSource, String error) {
        static UpdateRequest error(String message) {
            return new UpdateRequest(false, false, null, message);
        }
    }

    private static List<String> positionalArgs(String[] args) {
        List<String> values = new java.util.ArrayList<>();
        for (String arg : args) {
            if (!"-l".equals(arg) && !"--local".equals(arg)) {
                values.add(arg);
            }
        }
        return values;
    }
}
