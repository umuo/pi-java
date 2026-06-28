package works.earendil.pi.codingagent.core;

import works.earendil.pi.common.text.Ansi;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ShellSupport {
    private ShellSupport() {
    }

    public record ShellConfig(String shell, List<String> args, CommandTransport commandTransport) {
    }

    public enum CommandTransport {
        ARGV,
        STDIN
    }

    public static ShellConfig getShellConfig(String customShellPath) {
        if (customShellPath != null && !customShellPath.isBlank()) {
            if (!Files.exists(Path.of(customShellPath))) {
                throw new IllegalArgumentException("Custom shell path not found: " + customShellPath);
            }
            return bashConfig(customShellPath);
        }
        if (Files.exists(Path.of("/bin/bash"))) {
            return bashConfig("/bin/bash");
        }
        String path = System.getenv("PATH");
        if (path != null) {
            for (String entry : path.split(java.io.File.pathSeparator)) {
                Path candidate = Path.of(entry, "bash");
                if (Files.isExecutable(candidate)) {
                    return bashConfig(candidate.toString());
                }
            }
        }
        return new ShellConfig("sh", List.of("-c"), CommandTransport.ARGV);
    }

    public static String sanitizeBinaryOutput(String text) {
        String stripped = Ansi.strip(text);
        StringBuilder out = new StringBuilder();
        stripped.codePoints().forEach(cp -> {
            if (cp == 0x09 || cp == 0x0a || cp == 0x0d || (cp > 0x1f && !(cp >= 0xfff9 && cp <= 0xfffb))) {
                out.appendCodePoint(cp);
            }
        });
        return out.toString();
    }

    private static ShellConfig bashConfig(String shell) {
        return new ShellConfig(shell, List.of("-c"), CommandTransport.ARGV);
    }
}
