package works.earendil.pi.codingagent.resources;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class PackageResourceResolver {
    private PackageResourceResolver() {
    }

    public record PackageResourcePaths(List<Path> extensions, List<Path> skills, List<Path> prompts, List<Path> themes) {
        public PackageResourcePaths {
            extensions = extensions == null ? List.of() : List.copyOf(extensions);
            skills = skills == null ? List.of() : List.copyOf(skills);
            prompts = prompts == null ? List.of() : List.copyOf(prompts);
            themes = themes == null ? List.of() : List.copyOf(themes);
        }
    }

    private enum ResourceType {
        EXTENSIONS("extensions", ".jar"),
        SKILLS("skills", ".md"),
        PROMPTS("prompts", ".md"),
        THEMES("themes", ".json");

        private final String key;
        private final String extension;

        ResourceType(String key, String extension) {
            this.key = key;
            this.extension = extension;
        }
    }

    public static PackageResourcePaths resolve(Path cwd, Path agentDir, boolean projectTrusted) {
        return resolve(cwd, agentDir, projectTrusted, List.of());
    }

    public static PackageResourcePaths resolve(Path cwd, Path agentDir, boolean projectTrusted,
                                               List<JsonNode> configuredPackages) {
        Path resolvedCwd = cwd.toAbsolutePath().normalize();
        Path resolvedAgentDir = agentDir.toAbsolutePath().normalize();
        List<Path> packageRoots = new ArrayList<>();
        packageRoots.addAll(installedPackageRoots(resolvedAgentDir.resolve("packages")));
        packageRoots.addAll(installedGitPackageRoots(resolvedAgentDir.resolve("git")));
        packageRoots.addAll(installedNpmPackageRoots(resolvedAgentDir.resolve("npm")));
        if (projectTrusted) {
            packageRoots.addAll(installedPackageRoots(resolvedCwd.resolve(".pi").resolve("packages")));
            packageRoots.addAll(installedGitPackageRoots(resolvedCwd.resolve(".pi").resolve("git")));
            packageRoots.addAll(installedNpmPackageRoots(resolvedCwd.resolve(".pi").resolve("npm")));
        }
        return resolvePackageRoots(packageRoots, configuredPackages);
    }

    static PackageResourcePaths resolvePackageRoots(List<Path> packageRoots) {
        return resolvePackageRoots(packageRoots, List.of());
    }

    static PackageResourcePaths resolvePackageRoots(List<Path> packageRoots, List<JsonNode> configuredPackages) {
        Set<Path> extensions = new LinkedHashSet<>();
        Set<Path> skills = new LinkedHashSet<>();
        Set<Path> prompts = new LinkedHashSet<>();
        Set<Path> themes = new LinkedHashSet<>();
        List<PackageFilter> filters = parsePackageFilters(configuredPackages);
        for (Path rawRoot : packageRoots == null ? List.<Path>of() : packageRoots) {
            if (rawRoot == null || !Files.isDirectory(rawRoot)) {
                continue;
            }
            Path root = rawRoot.toAbsolutePath().normalize();
            PackageFilter filter = findFilter(root, filters);
            JsonNode manifest = readPiManifest(root);
            if (manifest != null && manifest.isObject()) {
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.EXTENSIONS),
                        root, ResourceType.EXTENSIONS, filter, extensions);
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.SKILLS),
                        root, ResourceType.SKILLS, filter, skills);
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.PROMPTS),
                        root, ResourceType.PROMPTS, filter, prompts);
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.THEMES),
                        root, ResourceType.THEMES, filter, themes);
            } else {
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.EXTENSIONS),
                        root, ResourceType.EXTENSIONS, filter, extensions);
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.SKILLS),
                        root, ResourceType.SKILLS, filter, skills);
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.PROMPTS),
                        root, ResourceType.PROMPTS, filter, prompts);
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.THEMES),
                        root, ResourceType.THEMES, filter, themes);
            }
        }
        return new PackageResourcePaths(new ArrayList<>(extensions), new ArrayList<>(skills),
                new ArrayList<>(prompts), new ArrayList<>(themes));
    }

    private static List<Path> installedPackageRoots(Path packagesDir) {
        if (!Files.isDirectory(packagesDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.list(packagesDir)) {
            return stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<Path> installedGitPackageRoots(Path gitDir) {
        if (!Files.isDirectory(gitDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(gitDir, 32)) {
            return stream
                    .filter(path -> Files.isDirectory(path.resolve(".git")))
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static List<Path> installedNpmPackageRoots(Path npmRoot) {
        Path nodeModules = npmRoot.resolve("node_modules");
        if (!Files.isDirectory(nodeModules)) {
            return List.of();
        }
        List<Path> roots = new ArrayList<>();
        try (Stream<Path> stream = Files.list(nodeModules)) {
            for (Path path : stream.sorted(Comparator.comparing(Path::toString)).toList()) {
                if (!Files.isDirectory(path)) {
                    continue;
                }
                String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
                if (fileName.startsWith("@")) {
                    try (Stream<Path> scoped = Files.list(path)) {
                        scoped.filter(Files::isDirectory)
                                .sorted(Comparator.comparing(Path::toString))
                                .map(p -> p.toAbsolutePath().normalize())
                                .forEach(roots::add);
                    }
                } else {
                    roots.add(path.toAbsolutePath().normalize());
                }
            }
        } catch (IOException e) {
            return List.of();
        }
        return List.copyOf(roots);
    }

    private static JsonNode readPiManifest(Path root) {
        Path packageJson = root.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            return null;
        }
        try {
            JsonNode node = JsonCodec.mapper().readTree(packageJson.toFile());
            JsonNode pi = node.path("pi");
            return pi.isObject() ? pi : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static Set<Path> resolveConventionalPaths(Path root, ResourceType type) {
        Set<Path> paths = new LinkedHashSet<>();
        Path path = root.resolve(type.key).toAbsolutePath().normalize();
        if (Files.isDirectory(path)) {
            paths.add(path);
        }
        return paths;
    }

    private static Set<Path> resolveManifestPaths(Path root, JsonNode manifest, ResourceType type) {
        Set<Path> paths = new LinkedHashSet<>();
        JsonNode values = manifest.path(type.key);
        if (!values.isArray()) {
            return paths;
        }
        List<String> entries = new ArrayList<>();
        values.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                entries.add(value.asText().trim());
            }
        });
        if (entries.isEmpty()) {
            return paths;
        }
        boolean patternMode = entries.stream().anyMatch(PackageResourceResolver::isPatternEntry);
        if (!patternMode) {
            for (String entry : entries) {
                Path resolved = resolveInside(root, entry);
                if (resolved != null && Files.exists(resolved)) {
                    paths.add(resolved);
                }
            }
            return paths;
        }

        Set<Path> candidates = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry.startsWith("!") || entry.startsWith("-")) {
                continue;
            }
            if (entry.startsWith("+")) {
                Path forced = resolveInside(root, entry.substring(1));
                if (forced != null && Files.exists(forced)) {
                    candidates.add(forced);
                }
                continue;
            }
            candidates.addAll(resolveEntryCandidates(root, entry, type));
        }
        for (String entry : entries) {
            if (entry.startsWith("!")) {
                candidates.removeIf(path -> matchesPattern(root, path, entry.substring(1)));
            } else if (entry.startsWith("-")) {
                Path excluded = resolveInside(root, entry.substring(1));
                if (excluded != null) {
                    candidates.remove(excluded);
                }
            }
        }
        paths.addAll(candidates);
        return paths;
    }

    private static void addFilteredPaths(Set<Path> allowed, Path root, ResourceType type, PackageFilter filter,
                                         Set<Path> target) {
        if (allowed.isEmpty()) {
            return;
        }
        List<String> entries = filter == null ? null : filter.entries(type);
        if (entries == null) {
            target.addAll(allowed);
            return;
        }
        if (entries.isEmpty()) {
            return;
        }
        Set<Path> candidates = new LinkedHashSet<>();
        for (String entry : entries) {
            if (entry.startsWith("!") || entry.startsWith("-")) {
                continue;
            }
            if (entry.startsWith("+")) {
                Path forced = resolveInside(root, entry.substring(1));
                if (forced != null && allowed.contains(forced)) {
                    candidates.add(forced);
                }
                continue;
            }
            candidates.addAll(resolveFilterEntryCandidates(root, entry, type, allowed));
        }
        for (String entry : entries) {
            if (entry.startsWith("!")) {
                candidates.removeIf(path -> matchesPattern(root, path, entry.substring(1)));
            } else if (entry.startsWith("-")) {
                Path excluded = resolveInside(root, entry.substring(1));
                if (excluded != null) {
                    candidates.remove(excluded);
                }
            }
        }
        target.addAll(candidates);
    }

    private static List<Path> resolveFilterEntryCandidates(Path root, String entry, ResourceType type,
                                                           Set<Path> allowed) {
        Set<Path> candidates = new LinkedHashSet<>();
        if (hasGlob(entry)) {
            for (Path path : allowedFiles(allowed, type)) {
                if (matchesPattern(root, path, entry)) {
                    candidates.add(path);
                }
            }
            return List.copyOf(candidates);
        }
        Path resolved = resolveInside(root, entry);
        if (resolved == null || !Files.exists(resolved)) {
            return List.of();
        }
        if (Files.isDirectory(resolved)) {
            for (Path path : resourceFiles(resolved, type)) {
                if (isAllowed(path, allowed)) {
                    candidates.add(path);
                }
            }
        } else if (isResourceFile(resolved, type) && isAllowed(resolved, allowed)) {
            candidates.add(resolved);
        }
        return List.copyOf(candidates);
    }

    private static List<Path> allowedFiles(Set<Path> allowed, ResourceType type) {
        List<Path> files = new ArrayList<>();
        for (Path path : allowed) {
            if (isResourceFile(path, type)) {
                files.add(path);
            } else if (Files.isDirectory(path)) {
                files.addAll(resourceFiles(path, type));
            }
        }
        return files.stream().sorted(Comparator.comparing(Path::toString)).toList();
    }

    private static boolean isAllowed(Path path, Set<Path> allowed) {
        Path normalized = path.toAbsolutePath().normalize();
        for (Path candidate : allowed) {
            Path allowedPath = candidate.toAbsolutePath().normalize();
            if (normalized.equals(allowedPath) || (Files.isDirectory(allowedPath) && normalized.startsWith(allowedPath))) {
                return true;
            }
        }
        return false;
    }

    private static List<Path> resolveEntryCandidates(Path root, String entry, ResourceType type) {
        if (hasGlob(entry)) {
            return globCandidates(root, entry, type);
        }
        Path resolved = resolveInside(root, entry);
        if (resolved == null || !Files.exists(resolved)) {
            return List.of();
        }
        if (Files.isDirectory(resolved)) {
            return resourceFiles(resolved, type);
        }
        return isResourceFile(resolved, type) ? List.of(resolved) : List.of();
    }

    private static List<Path> globCandidates(Path root, String pattern, ResourceType type) {
        List<Path> matches = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> isResourceFile(path, type))
                    .filter(path -> matchesPattern(root, path, pattern))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(matches::add);
        } catch (IOException ignored) {
        }
        return List.copyOf(matches);
    }

    private static List<Path> resourceFiles(Path dir, ResourceType type) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(path -> isResourceFile(path, type))
                    .sorted(Comparator.comparing(Path::toString))
                    .forEach(files::add);
        } catch (IOException ignored) {
        }
        return List.copyOf(files);
    }

    private static boolean isResourceFile(Path path, ResourceType type) {
        return Files.isRegularFile(path)
                && path.getFileName() != null
                && path.getFileName().toString().endsWith(type.extension);
    }

    private static boolean matchesPattern(Path root, Path path, String rawPattern) {
        String pattern = normalizePattern(rawPattern);
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        if (!hasGlob(pattern)) {
            return relative.toString().replace('\\', '/').equals(pattern);
        }
        try {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
            return matcher.matches(relative);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Path resolveInside(Path root, String raw) {
        String normalized = normalizePattern(raw);
        Path resolved = root.resolve(normalized).toAbsolutePath().normalize();
        Path normalizedRoot = root.toAbsolutePath().normalize();
        return resolved.startsWith(normalizedRoot) ? resolved : null;
    }

    private static boolean isPatternEntry(String entry) {
        return entry.startsWith("!")
                || entry.startsWith("+")
                || entry.startsWith("-")
                || hasGlob(entry);
    }

    private static boolean hasGlob(String value) {
        return value.contains("*") || value.contains("?");
    }

    private static String normalizePattern(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static List<PackageFilter> parsePackageFilters(List<JsonNode> packages) {
        if (packages == null || packages.isEmpty()) {
            return List.of();
        }
        List<PackageFilter> filters = new ArrayList<>();
        for (JsonNode entry : packages) {
            if (entry == null || !entry.isObject() || !entry.path("source").isTextual()) {
                continue;
            }
            String source = entry.path("source").asText("");
            if (source.isBlank()) {
                continue;
            }
            Map<ResourceType, List<String>> byType = new EnumMap<>(ResourceType.class);
            for (ResourceType type : ResourceType.values()) {
                if (entry.has(type.key)) {
                    byType.put(type, stringArray(entry.path(type.key)));
                }
            }
            if (!byType.isEmpty()) {
                filters.add(new PackageFilter(source, extractPackageName(source), byType));
            }
        }
        return List.copyOf(filters);
    }

    private static PackageFilter findFilter(Path packageRoot, List<PackageFilter> filters) {
        if (filters.isEmpty()) {
            return null;
        }
        String dirName = packageRoot.getFileName() == null ? "" : packageRoot.getFileName().toString();
        String packageJsonName = readPackageName(packageRoot);
        for (PackageFilter filter : filters) {
            if (dirName.equals(filter.packageName())
                    || dirName.equals(filter.source())
                    || (!packageJsonName.isBlank()
                    && (packageJsonName.equals(filter.source()) || packageJsonName.equals(filter.packageName())))) {
                return filter;
            }
        }
        return null;
    }

    private static String readPackageName(Path packageRoot) {
        Path packageJson = packageRoot.resolve("package.json");
        if (!Files.isRegularFile(packageJson)) {
            return "";
        }
        try {
            JsonNode node = JsonCodec.mapper().readTree(packageJson.toFile());
            JsonNode name = node.path("name");
            return name.isTextual() ? name.asText() : "";
        } catch (IOException e) {
            return "";
        }
    }

    private static List<String> stringArray(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) {
                values.add(value.asText().trim());
            }
        });
        return List.copyOf(values);
    }

    private static String extractPackageName(String source) {
        String name = source == null ? "" : source.trim();
        if (name.startsWith("npm:")) {
            name = name.substring(4);
            if (name.startsWith("@")) {
                int slash = name.indexOf('/');
                int versionIdx = slash >= 0 ? name.indexOf('@', slash + 1) : -1;
                return versionIdx >= 0 ? name.substring(0, versionIdx) : name;
            }
            int versionIdx = name.indexOf('@');
            return versionIdx >= 0 ? name.substring(0, versionIdx) : name;
        }
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        return name;
    }

    private record PackageFilter(String source, String packageName, Map<ResourceType, List<String>> entriesByType) {
        private List<String> entries(ResourceType type) {
            return entriesByType.get(type);
        }
    }
}
