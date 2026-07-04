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

        for (String arg : args) {
            if ("-l".equals(arg) || "--local".equals(arg)) {
                local = true;
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
                    System.out.println(PackageManager.update(source == null ? "all" : source, local, cwd));
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
