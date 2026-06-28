package works.earendil.pi.codingagent.tools;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class PathUtils {
    private PathUtils() {
    }

    public record PathInputOptions(
            boolean trim,
            boolean expandTilde,
            Path homeDir,
            boolean stripAtPrefix,
            boolean normalizeUnicodeSpaces) {
        public static PathInputOptions defaults() {
            return new PathInputOptions(false, true, Path.of(System.getProperty("user.home")), false, false);
        }

        public static PathInputOptions cli() {
            return new PathInputOptions(true, true, Path.of(System.getProperty("user.home")), true, true);
        }
    }

    public static Path canonicalizePath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException e) {
            return path;
        }
    }

    public static boolean isLocalPath(String value) {
        String trimmed = value.trim();
        return !(trimmed.startsWith("npm:")
                || trimmed.startsWith("git:")
                || trimmed.startsWith("github:")
                || trimmed.startsWith("http:")
                || trimmed.startsWith("https:")
                || trimmed.startsWith("ssh:"));
    }

    public static String normalizePath(String input) {
        return normalizePath(input, PathInputOptions.defaults());
    }

    public static String normalizePath(String input, PathInputOptions options) {
        PathInputOptions resolvedOptions = options == null ? PathInputOptions.defaults() : options;
        String normalized = resolvedOptions.trim() ? input.trim() : input;
        if (resolvedOptions.normalizeUnicodeSpaces()) {
            normalized = normalized.replaceAll("[\\u00A0\\u2000-\\u200A\\u202F\\u205F\\u3000]", " ");
        }
        if (resolvedOptions.stripAtPrefix() && normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (resolvedOptions.expandTilde()) {
            String home = resolvedOptions.homeDir().toString();
            if (normalized.equals("~")) {
                return home;
            }
            if (normalized.startsWith("~/") || normalized.startsWith("~\\")) {
                return resolvedOptions.homeDir().resolve(normalized.substring(2)).toString();
            }
        }
        if (normalized.startsWith("file://")) {
            return Path.of(URI.create(normalized)).toString();
        }
        return normalized;
    }

    public static Path resolvePath(String input) {
        return resolvePath(input, Path.of(System.getProperty("user.dir")), PathInputOptions.defaults());
    }

    public static Path resolvePath(String input, Path baseDir) {
        return resolvePath(input, baseDir, PathInputOptions.defaults());
    }

    public static Path resolvePath(String input, Path baseDir, PathInputOptions options) {
        Path normalized = Path.of(normalizePath(input, options));
        Path normalizedBaseDir = Path.of(normalizePath(baseDir.toString(), PathInputOptions.defaults()));
        return (normalized.isAbsolute() ? normalized : normalizedBaseDir.resolve(normalized)).toAbsolutePath().normalize();
    }

    public static Optional<String> getCwdRelativePath(Path filePath, Path cwd) {
        Path resolvedCwd = resolvePath(cwd.toString());
        Path resolvedPath = resolvePath(filePath.toString(), resolvedCwd);
        if (!resolvedPath.startsWith(resolvedCwd)) {
            return Optional.empty();
        }
        Path relative = resolvedCwd.relativize(resolvedPath);
        String value = relative.toString();
        return Optional.of(value.isEmpty() ? "." : value);
    }

    public static String formatPathRelativeToCwdOrAbsolute(Path filePath, Path cwd) {
        Path absolutePath = resolvePath(filePath.toString(), cwd);
        String path = getCwdRelativePath(absolutePath, cwd).orElse(absolutePath.toString());
        return path.replace('\\', '/');
    }

    public static Path resolveInside(Path cwd, String input) {
        return resolvePath(input, cwd);
    }

    public static String shorten(Path path, Path cwd) {
        Path abs = path.toAbsolutePath().normalize();
        Path root = cwd.toAbsolutePath().normalize();
        if (abs.startsWith(root)) {
            return root.relativize(abs).toString();
        }
        return abs.toString();
    }

    public static void markPathIgnoredByCloudSync(Path path) {
        String os = System.getProperty("os.name").toLowerCase();
        List<List<String>> commands;
        if (os.contains("mac")) {
            commands = List.of(
                    List.of("xattr", "-w", "com.dropbox.ignored", "1", path.toString()),
                    List.of("xattr", "-w", "com.apple.fileprovider.ignore#P", "1", path.toString()));
        } else if (os.contains("linux")) {
            commands = List.of(List.of("setfattr", "-n", "user.com.dropbox.ignored", "-v", "1", path.toString()));
        } else {
            return;
        }
        for (List<String> command : commands) {
            runIgnoringFailure(command);
        }
    }

    private static void runIgnoringFailure(List<String> command) {
        try {
            Process process = new ProcessBuilder(command)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            process.waitFor();
        } catch (IOException e) {
            if (!Files.exists(Path.of(command.getFirst()))) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
