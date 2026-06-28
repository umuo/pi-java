package works.earendil.pi.codingagent.session;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SessionPaths {
    private SessionPaths() {
    }

    public static Path defaultAgentDir() {
        return Path.of(System.getProperty("user.home"), ".pi", "agent");
    }

    public static Path sessionsDir(Path agentDir) {
        return agentDir.toAbsolutePath().normalize().resolve("sessions");
    }

    public static Path defaultSessionDirPath(Path cwd) {
        return defaultSessionDirPath(cwd, defaultAgentDir());
    }

    public static Path defaultSessionDirPath(Path cwd, Path agentDir) {
        String resolvedCwd = cwd.toAbsolutePath().normalize().toString();
        String encoded = resolvedCwd.replaceFirst("^[\\\\/]+", "").replaceAll("[\\\\/:]", "-");
        return sessionsDir(agentDir).resolve("--" + encoded + "--");
    }

    public static Path defaultSessionDir(Path cwd) {
        return defaultSessionDir(cwd, defaultAgentDir());
    }

    public static Path defaultSessionDir(Path cwd, Path agentDir) {
        Path path = defaultSessionDirPath(cwd, agentDir);
        try {
            Files.createDirectories(path);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to create session directory " + path, e);
        }
        return path;
    }

    public static Path normalizePath(String path) {
        String normalized = path
                .trim()
                .replace('\u00a0', ' ')
                .replace('\u2007', ' ')
                .replace('\u202f', ' ');
        if (normalized.startsWith("@")) {
            normalized = normalized.substring(1);
        }
        if (normalized.equals("~")) {
            return Path.of(System.getProperty("user.home")).toAbsolutePath().normalize();
        }
        if (normalized.startsWith("~/")) {
            return Path.of(System.getProperty("user.home"), normalized.substring(2)).toAbsolutePath().normalize();
        }
        if (normalized.startsWith("file://")) {
            return Path.of(URI.create(normalized)).toAbsolutePath().normalize();
        }
        return Path.of(normalized).toAbsolutePath().normalize();
    }

    public static boolean cwdMatches(Path sessionCwd, Path cwd) {
        return sessionCwd.toAbsolutePath().normalize().equals(cwd.toAbsolutePath().normalize());
    }
}
