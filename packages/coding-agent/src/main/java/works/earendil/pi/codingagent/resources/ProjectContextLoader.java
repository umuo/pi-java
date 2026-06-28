package works.earendil.pi.codingagent.resources;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class ProjectContextLoader {
    private static final List<String> CONTEXT_FILE_CANDIDATES = List.of("AGENTS.md", "AGENTS.MD", "CLAUDE.md", "CLAUDE.MD");

    private ProjectContextLoader() {
    }

    public record ContextFile(Path path, String content) {
    }

    public record PromptSources(String systemPrompt, List<String> appendSystemPrompt) {
    }

    public static List<ContextFile> loadProjectContextFiles(Path cwd, Path agentDir) {
        Path resolvedCwd = cwd.toAbsolutePath().normalize();
        Path resolvedAgentDir = agentDir.toAbsolutePath().normalize();
        List<ContextFile> files = new ArrayList<>();
        Set<Path> seen = new LinkedHashSet<>();

        readContextFileFromDir(resolvedAgentDir).ifPresent(file -> {
            files.add(file);
            seen.add(file.path());
        });

        List<ContextFile> ancestors = new ArrayList<>();
        Path current = resolvedCwd;
        while (current != null) {
            readContextFileFromDir(current).ifPresent(file -> {
                if (!seen.contains(file.path())) {
                    ancestors.add(0, file);
                    seen.add(file.path());
                }
            });
            current = current.getParent();
        }
        files.addAll(ancestors);
        return List.copyOf(files);
    }

    public static PromptSources resolvePromptSources(Path cwd, Path agentDir, boolean projectTrusted,
                                                     String systemPromptSource, List<String> appendSystemPromptSource) {
        String system = resolvePromptInput(systemPromptSource != null
                ? systemPromptSource
                : discoverSystemPromptFile(cwd, agentDir, projectTrusted));
        List<String> append = new ArrayList<>();
        List<String> appendSources = appendSystemPromptSource;
        if (appendSources == null) {
            Path discovered = discoverAppendSystemPromptFile(cwd, agentDir, projectTrusted);
            appendSources = discovered == null ? List.of() : List.of(discovered.toString());
        }
        for (String source : appendSources) {
            String resolved = resolvePromptInput(source);
            if (resolved != null) {
                append.add(resolved);
            }
        }
        return new PromptSources(system, List.copyOf(append));
    }

    private static java.util.Optional<ContextFile> readContextFileFromDir(Path dir) {
        for (String candidate : CONTEXT_FILE_CANDIDATES) {
            Path path = dir.resolve(candidate).toAbsolutePath().normalize();
            if (Files.exists(path)) {
                try {
                    return java.util.Optional.of(new ContextFile(path, Files.readString(path, StandardCharsets.UTF_8)));
                } catch (IOException ignored) {
                    return java.util.Optional.empty();
                }
            }
        }
        return java.util.Optional.empty();
    }

    private static String resolvePromptInput(Object input) {
        if (input == null) {
            return null;
        }
        String value = input.toString();
        Path maybePath = Path.of(value);
        if (Files.exists(maybePath)) {
            try {
                return Files.readString(maybePath, StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                return value;
            }
        }
        return value;
    }

    private static Path discoverSystemPromptFile(Path cwd, Path agentDir, boolean projectTrusted) {
        Path project = cwd.resolve(".pi").resolve("SYSTEM.md");
        if (projectTrusted && Files.exists(project)) {
            return project;
        }
        Path global = agentDir.resolve("SYSTEM.md");
        return Files.exists(global) ? global : null;
    }

    private static Path discoverAppendSystemPromptFile(Path cwd, Path agentDir, boolean projectTrusted) {
        Path project = cwd.resolve(".pi").resolve("APPEND_SYSTEM.md");
        if (projectTrusted && Files.exists(project)) {
            return project;
        }
        Path global = agentDir.resolve("APPEND_SYSTEM.md");
        return Files.exists(global) ? global : null;
    }
}
