package works.earendil.pi.codingagent.pkg;

import works.earendil.pi.codingagent.config.SettingsManager;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class PackageManagerCli {

    private PackageManagerCli() {}

    public static int handleCommand(String command, String[] args) {
        Path cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        boolean local = false;
        Boolean projectTrustOverride = null;
        String source = null;
        boolean updateSelf = false;
        boolean updateExtensions = false;
        boolean updateAll = false;
        boolean updateForce = false;
        boolean topLevelConfig = false;
        boolean jsonOutput = false;
        boolean resolvedOutput = false;
        boolean help = false;
        String invalidOption = null;
        String invalidArgument = null;
        String conflictingOption = null;
        String extensionSource = null;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-h".equals(arg) || "--help".equals(arg)) {
                help = true;
            } else if ("-l".equals(arg) || "--local".equals(arg)) {
                local = true;
            } else if ("-a".equals(arg) || "--approve".equals(arg)) {
                projectTrustOverride = true;
            } else if ("-na".equals(arg) || "--no-approve".equals(arg)) {
                projectTrustOverride = false;
            } else if ("config".equalsIgnoreCase(command) && "--json".equals(arg)) {
                jsonOutput = true;
            } else if ("config".equalsIgnoreCase(command) && "--resolved".equals(arg)) {
                resolvedOutput = true;
            } else if ("config".equalsIgnoreCase(command)
                    && ("--top-level".equals(arg) || "--resource".equals(arg))) {
                topLevelConfig = true;
            } else if ("update".equalsIgnoreCase(command) && "--self".equals(arg)) {
                updateSelf = true;
            } else if ("update".equalsIgnoreCase(command) && "--extensions".equals(arg)) {
                updateExtensions = true;
            } else if ("update".equalsIgnoreCase(command) && "--all".equals(arg)) {
                updateAll = true;
            } else if ("update".equalsIgnoreCase(command) && "--force".equals(arg)) {
                updateForce = true;
            } else if ("update".equalsIgnoreCase(command) && "--extension".equals(arg)) {
                if (i + 1 >= args.length || args[i + 1].startsWith("-")) {
                    System.err.println("Usage: pi update [source|self|pi] [--self|--extensions|--all] [--extension <source>] [--force] [-l] [--approve|--no-approve]");
                    return 1;
                }
                if (extensionSource != null) {
                    conflictingOption = conflictingOption == null
                            ? "--extension can only be provided once"
                            : conflictingOption;
                    i++;
                } else {
                    extensionSource = args[++i];
                }
            } else if (arg.startsWith("-")) {
                invalidOption = invalidOption == null ? arg : invalidOption;
            } else if (!arg.startsWith("-") && source == null) {
                source = arg;
            } else if (!"config".equalsIgnoreCase(command)) {
                invalidArgument = invalidArgument == null ? arg : invalidArgument;
            }
        }

        String normalizedCommand = command.toLowerCase();
        if ("uninstall".equals(normalizedCommand)) {
            normalizedCommand = "remove";
        }
        if (help) {
            System.out.println(helpText(normalizedCommand));
            return 0;
        }
        if (invalidOption != null) {
            System.err.println("Unknown option " + invalidOption + " for \"" + command + "\".");
            System.err.println("Usage: " + usage(normalizedCommand));
            return 1;
        }
        if (invalidArgument != null) {
            System.err.println("Unexpected argument " + invalidArgument + " for \"" + command + "\".");
            System.err.println("Usage: " + usage(normalizedCommand));
            return 1;
        }
        if (conflictingOption != null) {
            System.err.println(conflictingOption);
            System.err.println("Usage: " + usage(normalizedCommand));
            return 1;
        }

        try {
            Path agentDir = Paths.get(System.getProperty("user.home"), ".pi", "agent").toAbsolutePath().normalize();
            boolean projectTrusted = projectTrustOverride == null ? local : projectTrustOverride;
            SettingsManager settingsManager = new SettingsManager(cwd, agentDir, projectTrusted);
            switch (normalizedCommand) {
                case "config":
                    String action = source == null ? "list" : source.toLowerCase();
                    if ("list".equals(action)) {
                        if (jsonOutput) {
                            System.out.println(topLevelConfig
                                    ? PackageManager.listConfiguredResourcesJson(local, settingsManager, cwd, agentDir,
                                            resolvedOutput)
                                    : PackageManager.listConfiguredPackagesJson(local, settingsManager, cwd, agentDir,
                                            resolvedOutput));
                        } else {
                            System.out.println(topLevelConfig
                                    ? PackageManager.listConfiguredResources(local, settingsManager)
                                    : PackageManager.listConfiguredPackages(local, settingsManager));
                        }
                        return 0;
                    }
                    if (!"enable".equals(action) && !"disable".equals(action)) {
                        System.err.println("Usage: pi config [list|enable|disable] [source type path] [--top-level type path] [--json] [--resolved] [-l] [--approve|--no-approve]");
                        return 1;
                    }
                    List<String> positional = positionalArgs(args);
                    if (topLevelConfig) {
                        if (positional.size() < 3) {
                            System.err.println("Usage: pi config " + action + " --top-level <extensions|skills|prompts|themes> <path> [-l] [--approve|--no-approve]");
                            return 1;
                        }
                        System.out.println(PackageManager.configureTopLevelResource(positional.get(1),
                                positional.get(2), "enable".equals(action), local, settingsManager));
                        return 0;
                    }
                    if (positional.size() < 4) {
                        System.err.println("Usage: pi config " + action + " <source> <extensions|skills|prompts|themes> <path> [-l] [--approve|--no-approve]");
                        return 1;
                    }
                    System.out.println(PackageManager.configurePackageResource(positional.get(1), positional.get(2),
                            positional.get(3), "enable".equals(action), local, cwd, agentDir, settingsManager));
                    return 0;
                case "install":
                    if (source == null) {
                        System.err.println("Usage: pi install <source> [-l] [--approve|--no-approve]");
                        return 1;
                    }
                    System.out.println(PackageManager.installAndPersist(source, local, cwd, agentDir, settingsManager));
                    return 0;
                case "remove":
                case "uninstall":
                    if (source == null) {
                        System.err.println("Usage: pi remove <source> [-l] [--approve|--no-approve]");
                        return 1;
                    }
                    System.out.println(PackageManager.removeAndPersist(source, local, cwd, agentDir, settingsManager));
                    return 0;
                case "list":
                    System.out.println(PackageManager.listConfiguredPackagesForCommand(local, settingsManager,
                            cwd, agentDir));
                    return 0;
                case "update":
                    UpdateRequest updateRequest = parseUpdateRequest(source, updateSelf, updateExtensions,
                            updateAll, extensionSource, updateForce);
                    if (updateRequest.error() != null) {
                        System.err.println(updateRequest.error());
                        System.err.println("Usage: pi update [source|self|pi] [--self|--extensions|--all] [--extension <source>] [--force] [-l] [--approve|--no-approve]");
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
                                                    boolean all, String extensionSource, boolean force) {
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
            return new UpdateRequest(false, true, extensionSource, force, null);
        }
        if (source != null) {
            if (isSelfSource(source)) {
                return new UpdateRequest(true, extensions, null, force, null);
            }
            return new UpdateRequest(false, true, source, force, null);
        }
        if (all || (self && extensions)) {
            return new UpdateRequest(true, true, null, force, null);
        }
        if (extensions) {
            return new UpdateRequest(false, true, null, force, null);
        }
        return new UpdateRequest(true, false, null, force, null);
    }

    private static boolean isSelfSource(String source) {
        return "self".equalsIgnoreCase(source) || "pi".equalsIgnoreCase(source);
    }

    private static String usage(String command) {
        return switch (command) {
            case "install" -> "pi install <source> [-l] [--approve|--no-approve]";
            case "remove", "uninstall" -> "pi remove <source> [-l] [--approve|--no-approve]";
            case "list" -> "pi list [-l] [--approve|--no-approve]";
            case "config" -> "pi config [list|enable|disable] [source type path] [--top-level type path] "
                    + "[--json] [--resolved] [-l] [--approve|--no-approve]";
            case "update" -> "pi update [source|self|pi] [--self|--extensions|--all] "
                    + "[--extension <source>] [--force] [-l] [--approve|--no-approve]";
            default -> "pi <install|remove|update|list|config> [--help]";
        };
    }

    private static String helpText(String command) {
        return switch (command) {
            case "install" -> """
                    Usage: pi install <source> [-l] [--approve|--no-approve]

                    Install a package and add it to settings.

                    Options:
                      -l, --local       Install project-locally (.pi/settings.json)
                      -a, --approve     Trust project-local files for this command
                      -na, --no-approve Ignore project-local files for this command
                    """.stripTrailing();
            case "remove", "uninstall" -> """
                    Usage: pi remove <source> [-l] [--approve|--no-approve]

                    Remove a package and its source from settings.
                    Alias: pi uninstall <source> [-l]

                    Options:
                      -l, --local       Remove from project settings (.pi/settings.json)
                      -a, --approve     Trust project-local files for this command
                      -na, --no-approve Ignore project-local files for this command
                    """.stripTrailing();
            case "update" -> """
                    Usage: pi update [source|self|pi] [--self|--extensions|--all] [--extension <source>] [--force] [-l] [--approve|--no-approve]

                    Update pi and installed packages.

                    Options:
                      --self                  Update pi only (default when no target is given)
                      --extensions            Update installed packages only
                      --all                   Update pi and installed packages
                      --extension <source>    Update one package only
                      --force                 Reinstall pi even if the current version is latest
                      -l, --local             Use project-local package scope
                      -a, --approve           Trust project-local files for this command
                      -na, --no-approve       Ignore project-local files for this command
                    """.stripTrailing();
            case "list" -> """
                    Usage: pi list [-l] [--approve|--no-approve]

                    List configured packages from user and project settings.

                    Options:
                      -l, --local        List project-local packages only
                      -a, --approve      Trust project-local files for this command
                      -na, --no-approve  Ignore project-local files for this command
                    """.stripTrailing();
            case "config" -> """
                    Usage: pi config [list|enable|disable] [source type path] [--top-level type path] [--json] [--resolved] [-l] [--approve|--no-approve]

                    List or modify package resource filters.

                    Options:
                      --top-level, --resource  Configure top-level resource filters
                      --json                   Print a structured JSON snapshot
                      --resolved               Include resolved resource candidates with --json
                      -l, --local              Use project-local settings
                      -a, --approve            Trust project-local files for this command
                      -na, --no-approve        Ignore project-local files for this command
                    """.stripTrailing();
            default -> "Usage: " + usage(command);
        };
    }

    private static String runUpdate(UpdateRequest request, boolean local, Path cwd, Path agentDir,
                                    SettingsManager settingsManager) throws Exception {
        List<String> parts = new java.util.ArrayList<>();
        if (request.self()) {
            parts.add(PackageManager.update("self", local, cwd, agentDir, settingsManager, request.force()));
        }
        if (request.extensions()) {
            String target = request.extensionSource() == null ? "all" : request.extensionSource();
            parts.add(PackageManager.update(target, local, cwd, agentDir, settingsManager));
        } else if (request.self()) {
            parts.add("Packages are skipped. Run pi update --extensions to update packages.");
        }
        return String.join("\n", parts);
    }

    private record UpdateRequest(boolean self, boolean extensions, String extensionSource, boolean force, String error) {
        static UpdateRequest error(String message) {
            return new UpdateRequest(false, false, null, false, message);
        }
    }

    private static List<String> positionalArgs(String[] args) {
        List<String> values = new java.util.ArrayList<>();
        for (String arg : args) {
            if (!"-l".equals(arg) && !"--local".equals(arg)
                    && !"-a".equals(arg) && !"--approve".equals(arg)
                    && !"-na".equals(arg) && !"--no-approve".equals(arg)
                    && !"--top-level".equals(arg) && !"--resource".equals(arg)
                    && !"--json".equals(arg) && !"--resolved".equals(arg)
                    && !"--force".equals(arg)) {
                values.add(arg);
            }
        }
        return values;
    }
}
