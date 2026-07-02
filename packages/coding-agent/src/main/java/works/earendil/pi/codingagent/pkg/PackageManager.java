package works.earendil.pi.codingagent.pkg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class PackageManager {

    private PackageManager() {}

    public static Path getGlobalPackageDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".pi", "agent", "packages");
    }

    public static Path getLocalPackageDir(Path cwd) {
        return cwd.resolve(".pi").resolve("packages");
    }

    public static String install(String source, boolean local, Path cwd) throws IOException, InterruptedException {
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir();
        Files.createDirectories(targetBase);

        String pkgName = extractPackageName(source);
        Path targetDir = targetBase.resolve(pkgName);

        if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("git@")) {
            if (Files.exists(targetDir)) {
                ProcessBuilder pb = new ProcessBuilder("git", "-C", targetDir.toString(), "pull");
                pb.inheritIO().start().waitFor();
                return "Updated git package " + pkgName + " in " + targetDir;
            } else {
                ProcessBuilder pb = new ProcessBuilder("git", "clone", source, targetDir.toString());
                int code = pb.inheritIO().start().waitFor();
                if (code != 0) {
                    throw new IOException("Failed to clone repository: " + source);
                }
                return "Installed git package " + pkgName + " to " + targetDir;
            }
        } else {
            Path srcPath = Paths.get(source);
            if (!Files.exists(srcPath)) {
                throw new IllegalArgumentException("Source path does not exist: " + source);
            }
            if (Files.isDirectory(srcPath)) {
                copyDirectory(srcPath, targetDir);
            } else {
                Files.createDirectories(targetDir);
                Files.copy(srcPath, targetDir.resolve(srcPath.getFileName()), StandardCopyOption.REPLACE_EXISTING);
            }
            return "Installed local package " + pkgName + " to " + targetDir;
        }
    }

    public static String remove(String source, boolean local, Path cwd) throws IOException {
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir();
        String pkgName = extractPackageName(source);
        Path targetDir = targetBase.resolve(pkgName);

        if (Files.exists(targetDir)) {
            deleteRecursively(targetDir);
            return "Removed package " + pkgName + " from " + targetBase;
        } else {
            return "Package not found: " + pkgName + " in " + targetBase;
        }
    }

    public static List<String> list(boolean local, Path cwd) throws IOException {
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir();
        List<String> pkgs = new ArrayList<>();
        if (Files.exists(targetBase)) {
            try (Stream<Path> stream = Files.list(targetBase)) {
                stream.forEach(p -> {
                    if (Files.isDirectory(p)) {
                        pkgs.add(p.getFileName().toString());
                    }
                });
            }
        }
        return pkgs;
    }

    public static String update(String source, boolean local, Path cwd) throws IOException, InterruptedException {
        if ("self".equalsIgnoreCase(source) || "pi".equalsIgnoreCase(source)) {
            return "Pi Java CLI is managed via git pull && mvn clean install or package distribution.";
        }
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir();
        if ("all".equalsIgnoreCase(source) || source == null || source.isBlank()) {
            StringBuilder sb = new StringBuilder();
            for (String pkg : list(local, cwd)) {
                Path p = targetBase.resolve(pkg);
                if (Files.exists(p.resolve(".git"))) {
                    ProcessBuilder pb = new ProcessBuilder("git", "-C", p.toString(), "pull");
                    pb.start().waitFor();
                    sb.append("Updated ").append(pkg).append("\n");
                }
            }
            return sb.length() == 0 ? "No git packages found to update." : sb.toString().trim();
        }
        return install(source, local, cwd);
    }

    private static String extractPackageName(String source) {
        String name = source;
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        return name.isEmpty() ? "package-" + System.currentTimeMillis() : name;
    }

    private static void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        try (Stream<Path> stream = Files.walk(sourceDir)) {
            stream.forEach(source -> {
                Path dest = targetDir.resolve(sourceDir.relativize(source));
                try {
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> entries = Files.list(path)) {
                for (Path entry : entries.toList()) {
                    deleteRecursively(entry);
                }
            }
        }
        Files.deleteIfExists(path);
    }
}
