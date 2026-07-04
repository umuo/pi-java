package works.earendil.pi.codingagent.resources;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ThemeResourceLoader {
    private ThemeResourceLoader() {
    }

    public record LoadThemesOptions(Path cwd, Path agentDir, List<Path> themePaths, boolean includeDefaults) {
    }

    public record LoadThemesResult(List<ThemeResource> themes, List<ResourceDiagnostic> diagnostics) {
        public LoadThemesResult {
            themes = themes == null ? List.of() : List.copyOf(themes);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }
    }

    public static LoadThemesResult loadThemes(LoadThemesOptions options) {
        List<ThemeResource> loaded = new ArrayList<>();
        List<ResourceDiagnostic> diagnostics = new ArrayList<>();
        Path global = options.agentDir().resolve("themes");
        Path project = options.cwd().resolve(".pi").resolve("themes");
        if (options.includeDefaults()) {
            loaded.addAll(loadThemesFromDir(global, path -> sourceInfo(path, global, project), diagnostics));
            loaded.addAll(loadThemesFromDir(project, path -> sourceInfo(path, global, project), diagnostics));
        }
        for (Path raw : options.themePaths() == null ? List.<Path>of() : options.themePaths()) {
            if (raw == null) {
                continue;
            }
            Path path = normalize(options.cwd(), raw);
            if (!Files.exists(path)) {
                diagnostics.add(new ResourceDiagnostic.Warning("theme path does not exist", path));
                continue;
            }
            if (Files.isDirectory(path)) {
                loaded.addAll(loadThemesFromDir(path, p -> sourceInfo(p, global, project), diagnostics));
            } else if (Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json")) {
                loadThemeFromFile(path, sourceInfo(path, global, project), diagnostics).ifPresent(loaded::add);
            } else {
                diagnostics.add(new ResourceDiagnostic.Warning("theme path is not a json file", path));
            }
        }
        return dedupe(loaded, diagnostics);
    }

    private static List<ThemeResource> loadThemesFromDir(Path dir,
                                                         java.util.function.Function<Path, SourceInfo> sourceInfo,
                                                         List<ResourceDiagnostic> diagnostics) {
        if (!Files.exists(dir)) {
            return List.of();
        }
        List<ThemeResource> themes = new ArrayList<>();
        try (var stream = Files.list(dir)) {
            for (Path file : stream.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (Files.isRegularFile(file) && file.getFileName().toString().endsWith(".json")) {
                    loadThemeFromFile(file, sourceInfo.apply(file), diagnostics).ifPresent(themes::add);
                }
            }
        } catch (IOException e) {
            diagnostics.add(new ResourceDiagnostic.Warning(e.getMessage(), dir));
        }
        return List.copyOf(themes);
    }

    private static java.util.Optional<ThemeResource> loadThemeFromFile(Path file, SourceInfo sourceInfo,
                                                                       List<ResourceDiagnostic> diagnostics) {
        try {
            JsonNode node = JsonCodec.mapper().readTree(file.toFile());
            JsonNode nameNode = node.path("name");
            if (!node.isObject() || !nameNode.isTextual() || nameNode.asText().isBlank()) {
                diagnostics.add(new ResourceDiagnostic.Warning("theme name is required", file));
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new ThemeResource(nameNode.asText(), node, sourceInfo, file));
        } catch (IOException e) {
            diagnostics.add(new ResourceDiagnostic.Warning(e.getMessage(), file));
            return java.util.Optional.empty();
        }
    }

    private static LoadThemesResult dedupe(List<ThemeResource> loaded, List<ResourceDiagnostic> diagnostics) {
        Map<String, ThemeResource> byName = new LinkedHashMap<>();
        for (ThemeResource theme : loaded) {
            ThemeResource existing = byName.putIfAbsent(theme.name(), theme);
            if (existing != null) {
                diagnostics.add(new ResourceDiagnostic.Collision("theme", theme.name(), existing.filePath(),
                        theme.filePath()));
            }
        }
        return new LoadThemesResult(new ArrayList<>(byName.values()), diagnostics);
    }

    private static SourceInfo sourceInfo(Path path, Path global, Path project) {
        Path resolved = path.toAbsolutePath().normalize();
        Path g = global.toAbsolutePath().normalize();
        Path p = project.toAbsolutePath().normalize();
        if (resolved.startsWith(g)) {
            return SourceInfo.local(resolved, "user", g);
        }
        if (resolved.startsWith(p)) {
            return SourceInfo.local(resolved, "project", p);
        }
        Path baseDir = Files.isDirectory(resolved) ? resolved : resolved.getParent();
        return SourceInfo.local(resolved, "temporary", baseDir);
    }

    private static Path normalize(Path cwd, Path path) {
        return path.isAbsolute()
                ? path.toAbsolutePath().normalize()
                : cwd.resolve(path).toAbsolutePath().normalize();
    }
}
