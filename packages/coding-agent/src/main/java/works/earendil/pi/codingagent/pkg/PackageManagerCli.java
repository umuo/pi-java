package works.earendil.pi.codingagent.pkg;

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
            switch (command.toLowerCase()) {
                case "install":
                    if (source == null) {
                        System.err.println("Usage: pi install <source> [-l]");
                        return 1;
                    }
                    System.out.println(PackageManager.install(source, local, cwd));
                    return 0;
                case "remove":
                case "uninstall":
                    if (source == null) {
                        System.err.println("Usage: pi remove <source> [-l]");
                        return 1;
                    }
                    System.out.println(PackageManager.remove(source, local, cwd));
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
}
