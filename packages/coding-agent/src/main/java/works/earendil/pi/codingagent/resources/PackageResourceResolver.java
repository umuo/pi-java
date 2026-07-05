package works.earendil.pi.codingagent.resources;

import com.fasterxml.jackson.databind.JsonNode;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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

    private enum PackageScope {
        GLOBAL,
        PROJECT
    }

    public static PackageResourcePaths resolve(Path cwd, Path agentDir, boolean projectTrusted) {
        return resolve(cwd, agentDir, projectTrusted, List.of());
    }

    public static PackageResourcePaths resolve(Path cwd, Path agentDir, boolean projectTrusted,
                                               List<JsonNode> configuredPackages) {
        return resolve(cwd, agentDir, projectTrusted, configuredPackages, List.of());
    }

    public static PackageResourcePaths resolve(Path cwd, Path agentDir, boolean projectTrusted,
                                               List<JsonNode> globalConfiguredPackages,
                                               List<JsonNode> projectConfiguredPackages) {
        Path resolvedCwd = cwd.toAbsolutePath().normalize();
        Path resolvedAgentDir = agentDir.toAbsolutePath().normalize();
        List<ScopedPackageRoot> packageRoots = new ArrayList<>();
        if (projectTrusted) {
            addScopedRoots(packageRoots, installedPackageRoots(resolvedCwd.resolve(".pi").resolve("packages")),
                    PackageScope.PROJECT);
            addScopedRoots(packageRoots, installedGitPackageRoots(resolvedCwd.resolve(".pi").resolve("git")),
                    PackageScope.PROJECT);
            addScopedRoots(packageRoots, installedNpmPackageRoots(resolvedCwd.resolve(".pi").resolve("npm")),
                    PackageScope.PROJECT);
        }
        addScopedRoots(packageRoots, installedPackageRoots(resolvedAgentDir.resolve("packages")), PackageScope.GLOBAL);
        addScopedRoots(packageRoots, installedGitPackageRoots(resolvedAgentDir.resolve("git")), PackageScope.GLOBAL);
        addScopedRoots(packageRoots, installedNpmPackageRoots(resolvedAgentDir.resolve("npm")), PackageScope.GLOBAL);
        return resolveScopedPackageRoots(packageRoots, resolvedCwd, resolvedAgentDir, globalConfiguredPackages,
                projectTrusted ? projectConfiguredPackages : List.of());
    }

    static PackageResourcePaths resolvePackageRoots(List<Path> packageRoots) {
        return resolvePackageRoots(packageRoots, List.of());
    }

    static PackageResourcePaths resolvePackageRoots(List<Path> packageRoots, List<JsonNode> configuredPackages) {
        List<ScopedPackageRoot> scopedRoots = new ArrayList<>();
        addScopedRoots(scopedRoots, packageRoots, PackageScope.GLOBAL);
        return resolveScopedPackageRoots(scopedRoots, null, null, configuredPackages, List.of());
    }

    private static PackageResourcePaths resolveScopedPackageRoots(List<ScopedPackageRoot> packageRoots,
                                                                  Path cwd,
                                                                  Path agentDir,
                                                                  List<JsonNode> globalConfiguredPackages,
                                                                  List<JsonNode> projectConfiguredPackages) {
        Set<Path> extensions = new LinkedHashSet<>();
        Set<Path> skills = new LinkedHashSet<>();
        Set<Path> prompts = new LinkedHashSet<>();
        Set<Path> themes = new LinkedHashSet<>();
        List<PackageConfig> configs = parsePackageConfigs(globalConfiguredPackages, projectConfiguredPackages,
                cwd, agentDir);
        Set<String> seenIdentities = new LinkedHashSet<>();
        for (ScopedPackageRoot scopedRoot : packageRoots == null ? List.<ScopedPackageRoot>of() : packageRoots) {
            Path rawRoot = scopedRoot == null ? null : scopedRoot.root();
            if (rawRoot == null || !Files.isDirectory(rawRoot)) {
                continue;
            }
            Path root = rawRoot.toAbsolutePath().normalize();
            PackageConfig config = findConfig(root, scopedRoot.scope(), configs);
            String identity = packageIdentity(root, config);
            if (!seenIdentities.add(identity)) {
                continue;
            }
            JsonNode manifest = readPiManifest(root);
            if (manifest != null && manifest.isObject()) {
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.EXTENSIONS),
                        root, ResourceType.EXTENSIONS, config, extensions);
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.SKILLS),
                        root, ResourceType.SKILLS, config, skills);
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.PROMPTS),
                        root, ResourceType.PROMPTS, config, prompts);
                addFilteredPaths(resolveManifestPaths(root, manifest, ResourceType.THEMES),
                        root, ResourceType.THEMES, config, themes);
            } else {
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.EXTENSIONS),
                        root, ResourceType.EXTENSIONS, config, extensions);
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.SKILLS),
                        root, ResourceType.SKILLS, config, skills);
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.PROMPTS),
                        root, ResourceType.PROMPTS, config, prompts);
                addFilteredPaths(resolveConventionalPaths(root, ResourceType.THEMES),
                        root, ResourceType.THEMES, config, themes);
            }
        }
        return new PackageResourcePaths(new ArrayList<>(extensions), new ArrayList<>(skills),
                new ArrayList<>(prompts), new ArrayList<>(themes));
    }

    private static void addScopedRoots(List<ScopedPackageRoot> target, List<Path> roots, PackageScope scope) {
        if (roots == null || roots.isEmpty()) {
            return;
        }
        roots.stream()
                .filter(path -> path != null)
                .map(path -> new ScopedPackageRoot(path, scope))
                .forEach(target::add);
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

    private static void addFilteredPaths(Set<Path> allowed, Path root, ResourceType type, PackageConfig filter,
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

    private static List<PackageConfig> parsePackageConfigs(List<JsonNode> globalPackages,
                                                           List<JsonNode> projectPackages,
                                                           Path cwd,
                                                           Path agentDir) {
        List<PackageConfig> configs = new ArrayList<>();
        parsePackageConfigs(globalPackages, PackageScope.GLOBAL, cwd, agentDir, configs);
        parsePackageConfigs(projectPackages, PackageScope.PROJECT, cwd, agentDir, configs);
        return List.copyOf(configs);
    }

    private static void parsePackageConfigs(List<JsonNode> packages, PackageScope scope, Path cwd, Path agentDir,
                                            List<PackageConfig> configs) {
        if (packages == null || packages.isEmpty()) {
            return;
        }
        for (JsonNode entry : packages) {
            if (entry == null || !entry.isObject() || !entry.path("source").isTextual()) {
                if (entry != null && entry.isTextual() && !entry.asText().isBlank()) {
                    String source = entry.asText();
                    configs.add(new PackageConfig(source, extractPackageName(source), identityFromSource(source,
                                    scope, cwd, agentDir),
                            scope, Map.of()));
                }
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
            configs.add(new PackageConfig(source, extractPackageName(source), identityFromSource(source,
                            scope, cwd, agentDir),
                    scope, byType));
        }
    }

    private static PackageConfig findConfig(Path packageRoot, PackageScope scope, List<PackageConfig> configs) {
        if (configs.isEmpty()) {
            return null;
        }
        String dirName = packageRoot.getFileName() == null ? "" : packageRoot.getFileName().toString();
        String packageJsonName = readPackageName(packageRoot);
        for (PackageConfig config : configs) {
            if (config.scope() != scope) {
                continue;
            }
            if (dirName.equals(config.packageName())
                    || dirName.equals(config.source())
                    || (!packageJsonName.isBlank()
                    && (packageJsonName.equals(config.source()) || packageJsonName.equals(config.packageName())))) {
                return config;
            }
        }
        return null;
    }

    private static String packageIdentity(Path packageRoot, PackageConfig config) {
        if (config != null && !config.identity().isBlank()) {
            return config.identity();
        }
        String packageJsonName = readPackageName(packageRoot);
        if (!packageJsonName.isBlank()) {
            return "name:" + packageJsonName;
        }
        String dirName = packageRoot.getFileName() == null ? "" : packageRoot.getFileName().toString();
        return "path:" + packageRoot.getParent() + "/" + dirName;
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
        NpmSource npmSource = parseNpmSource(source);
        if (npmSource != null) {
            return npmSource.name();
        }
        GitSource gitSource = parseGitSource(source);
        String name = gitSource == null ? (source == null ? "" : source.trim()) : gitSource.path();
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        return name;
    }

    private static String identityFromSource(String source, PackageScope scope, Path cwd, Path agentDir) {
        NpmSource npmSource = parseNpmSource(source);
        if (npmSource != null) {
            return "npm:" + npmSource.name();
        }
        GitSource gitSource = parseGitSource(source);
        if (gitSource != null) {
            return "git:" + gitSource.host() + "/" + gitSource.path();
        }
        String trimmed = source == null ? "" : source.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        return "local:" + resolveLocalSourcePath(trimmed, scope, cwd, agentDir);
    }

    private static String resolveLocalSourcePath(String source, PackageScope scope, Path cwd, Path agentDir) {
        Path base = switch (scope) {
            case PROJECT -> cwd == null ? Path.of("").toAbsolutePath().normalize() : cwd.resolve(".pi");
            case GLOBAL -> agentDir == null ? Path.of("").toAbsolutePath().normalize() : agentDir;
        };
        String expanded = expandHome(source);
        try {
            if (expanded.startsWith("file:")) {
                Path path = Path.of(URI.create(expanded));
                return path.toAbsolutePath().normalize().toString();
            }
        } catch (IllegalArgumentException ignored) {
        }
        Path path = Path.of(expanded);
        return (path.isAbsolute() ? path : base.resolve(path)).toAbsolutePath().normalize().toString();
    }

    private static String expandHome(String source) {
        if (source.equals("~")) {
            return System.getProperty("user.home");
        }
        if (source.startsWith("~/") || source.startsWith("~\\")) {
            return System.getProperty("user.home") + source.substring(1);
        }
        return source;
    }

    private record NpmSource(String name) {
    }

    private static NpmSource parseNpmSource(String source) {
        if (source == null || !source.trim().startsWith("npm:")) {
            return null;
        }
        String spec = source.trim().substring("npm:".length()).trim();
        if (spec.isBlank()) {
            return null;
        }
        String name;
        if (spec.startsWith("@")) {
            int slash = spec.indexOf('/');
            if (slash <= 1 || slash == spec.length() - 1) {
                return null;
            }
            int versionIdx = spec.indexOf('@', slash + 1);
            name = versionIdx >= 0 ? spec.substring(0, versionIdx) : spec;
        } else {
            int versionIdx = spec.indexOf('@');
            name = versionIdx >= 0 ? spec.substring(0, versionIdx) : spec;
        }
        return name.isBlank() ? null : new NpmSource(name);
    }

    private record GitSource(String host, String path) {
    }

    private static GitSource parseGitSource(String source) {
        if (source == null || source.isBlank()) {
            return null;
        }
        String trimmed = source.trim();
        boolean hasGitPrefix = trimmed.startsWith("git:");
        String value = hasGitPrefix ? trimmed.substring("git:".length()).trim() : trimmed;
        boolean protocol = value.startsWith("http://") || value.startsWith("https://")
                || value.startsWith("ssh://") || value.startsWith("git://") || value.startsWith("file://");
        boolean scpLike = value.startsWith("git@");
        if (!hasGitPrefix && !protocol && !scpLike) {
            return null;
        }
        GitRepoRef split = splitGitRef(value);
        String repo = split.repo();
        String host;
        String path;
        if (repo.startsWith("git@")) {
            int colon = repo.indexOf(':');
            if (colon <= "git@".length()) {
                return null;
            }
            host = repo.substring("git@".length(), colon);
            path = repo.substring(colon + 1);
        } else if (repo.startsWith("http://") || repo.startsWith("https://")
                || repo.startsWith("ssh://") || repo.startsWith("git://") || repo.startsWith("file://")) {
            try {
                URI uri = new URI(repo);
                host = uri.getHost() == null || uri.getHost().isBlank() ? "local" : uri.getHost();
                path = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/+", "");
            } catch (URISyntaxException e) {
                return null;
            }
        } else {
            int slash = repo.indexOf('/');
            if (slash <= 0) {
                return null;
            }
            host = repo.substring(0, slash);
            path = repo.substring(slash + 1);
            if (!host.contains(".") && !"localhost".equals(host)) {
                return null;
            }
        }
        host = host == null ? "" : host.toLowerCase();
        path = path == null ? "" : path.replaceFirst("\\.git$", "").replaceFirst("^/+", "");
        if (path.isBlank() || path.split("/").length < 2 || unsafeGitPart(host, false) || unsafeGitPart(path, true)) {
            return null;
        }
        return new GitSource(host, path);
    }

    private record GitRepoRef(String repo, String ref) {
    }

    private static GitRepoRef splitGitRef(String value) {
        if (value.startsWith("git@")) {
            int colon = value.indexOf(':');
            if (colon > 0) {
                String prefix = value.substring(0, colon + 1);
                String path = value.substring(colon + 1);
                int refIdx = path.indexOf('@');
                if (refIdx > 0 && refIdx < path.length() - 1) {
                    return new GitRepoRef(prefix + path.substring(0, refIdx), path.substring(refIdx + 1));
                }
            }
            return new GitRepoRef(value, null);
        }
        if (value.contains("://")) {
            try {
                URI uri = new URI(value);
                String rawPath = uri.getRawPath();
                if (rawPath != null) {
                    int refIdx = rawPath.indexOf('@');
                    if (refIdx > 1 && refIdx < rawPath.length() - 1) {
                        String repoPath = rawPath.substring(0, refIdx);
                        URI withoutRef = new URI(uri.getScheme(), uri.getAuthority(), repoPath,
                                uri.getQuery(), uri.getFragment());
                        return new GitRepoRef(withoutRef.toString(), rawPath.substring(refIdx + 1));
                    }
                }
            } catch (URISyntaxException ignored) {
                return new GitRepoRef(value, null);
            }
            return new GitRepoRef(value, null);
        }
        int slash = value.indexOf('/');
        if (slash > 0) {
            String host = value.substring(0, slash);
            String path = value.substring(slash + 1);
            int refIdx = path.indexOf('@');
            if (refIdx > 0 && refIdx < path.length() - 1) {
                return new GitRepoRef(host + "/" + path.substring(0, refIdx), path.substring(refIdx + 1));
            }
        }
        return new GitRepoRef(value, null);
    }

    private static boolean unsafeGitPart(String value, boolean allowSlash) {
        if (value == null || value.isBlank() || value.contains("\0") || value.contains("\\") || value.startsWith("/")) {
            return true;
        }
        if (!allowSlash && value.contains("/")) {
            return true;
        }
        for (String part : value.split("/")) {
            if (part.equals("..") || part.isBlank()) {
                return true;
            }
        }
        return false;
    }

    private record ScopedPackageRoot(Path root, PackageScope scope) {
    }

    private record PackageConfig(String source, String packageName, String identity, PackageScope scope,
                                 Map<ResourceType, List<String>> entriesByType) {
        private List<String> entries(ResourceType type) {
            return entriesByType.get(type);
        }
    }
}
