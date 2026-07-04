package works.earendil.pi.codingagent.pkg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import works.earendil.pi.codingagent.config.SettingsManager;
import works.earendil.pi.common.json.JsonCodec;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class PackageManager {

    private PackageManager() {}

    public static Path getGlobalPackageDir() {
        String userHome = System.getProperty("user.home");
        return Paths.get(userHome, ".pi", "agent", "packages");
    }

    public static Path getGlobalPackageDir(Path agentDir) {
        return agentDir.toAbsolutePath().normalize().resolve("packages");
    }

    public static Path getGlobalGitPackageDir(Path agentDir) {
        return agentDir.toAbsolutePath().normalize().resolve("git");
    }

    public static Path getGlobalNpmPackageDir(Path agentDir) {
        return agentDir.toAbsolutePath().normalize().resolve("npm");
    }

    public static Path getLocalPackageDir(Path cwd) {
        return cwd.resolve(".pi").resolve("packages");
    }

    public static Path getLocalGitPackageDir(Path cwd) {
        return cwd.resolve(".pi").resolve("git");
    }

    public static Path getLocalNpmPackageDir(Path cwd) {
        return cwd.resolve(".pi").resolve("npm");
    }

    public static String install(String source, boolean local, Path cwd) throws IOException, InterruptedException {
        return install(source, local, cwd, defaultAgentDir());
    }

    public static String install(String source, boolean local, Path cwd, Path agentDir)
            throws IOException, InterruptedException {
        return install(source, local, cwd, agentDir, null);
    }

    private static String install(String source, boolean local, Path cwd, Path agentDir, SettingsManager settingsManager)
            throws IOException, InterruptedException {
        NpmSource npmSource = parseNpmSource(source);
        if (npmSource != null) {
            return installNpm(npmSource, local, cwd, agentDir, settingsManager);
        }
        GitSource gitSource = parseGitSource(source);
        if (gitSource != null) {
            return installGit(gitSource, local, cwd, agentDir);
        }
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir(agentDir);
        Files.createDirectories(targetBase);

        String pkgName = extractPackageName(source);
        Path targetDir = targetBase.resolve(pkgName);

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

    public static String remove(String source, boolean local, Path cwd) throws IOException {
        return remove(source, local, cwd, defaultAgentDir());
    }

    public static String remove(String source, boolean local, Path cwd, Path agentDir) throws IOException {
        return remove(source, local, cwd, agentDir, null);
    }

    private static String remove(String source, boolean local, Path cwd, Path agentDir, SettingsManager settingsManager)
            throws IOException {
        NpmSource npmSource = parseNpmSource(source);
        if (npmSource != null) {
            return removeNpm(npmSource, local, cwd, agentDir, settingsManager);
        }
        GitSource gitSource = parseGitSource(source);
        if (gitSource != null) {
            return removeGit(gitSource, local, cwd, agentDir);
        }
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir(agentDir);
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
        return list(local, cwd, defaultAgentDir());
    }

    public static List<String> list(boolean local, Path cwd, Path agentDir) throws IOException {
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir(agentDir);
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
        Path gitBase = local ? getLocalGitPackageDir(cwd) : getGlobalGitPackageDir(agentDir);
        for (Path root : installedGitPackageRoots(gitBase)) {
            Path relative = gitBase.toAbsolutePath().normalize().relativize(root);
            pkgs.add("git:" + relative.toString().replace('\\', '/'));
        }
        for (Path root : installedNpmPackageRoots(local ? getLocalNpmPackageDir(cwd) : getGlobalNpmPackageDir(agentDir))) {
            String name = readPackageName(root);
            pkgs.add(name.isBlank() ? "npm:" + root.getFileName() : "npm:" + name);
        }
        return pkgs;
    }

    public static String update(String source, boolean local, Path cwd) throws IOException, InterruptedException {
        return update(source, local, cwd, defaultAgentDir());
    }

    public static String update(String source, boolean local, Path cwd, Path agentDir)
            throws IOException, InterruptedException {
        if ("self".equalsIgnoreCase(source) || "pi".equalsIgnoreCase(source)) {
            return "Pi Java CLI is managed via git pull && mvn clean install or package distribution.";
        }
        Path targetBase = local ? getLocalPackageDir(cwd) : getGlobalPackageDir(agentDir);
        if ("all".equalsIgnoreCase(source) || source == null || source.isBlank()) {
            StringBuilder sb = new StringBuilder();
            for (String pkg : list(local, cwd, agentDir)) {
                Path p = targetBase.resolve(pkg);
                if (Files.exists(p.resolve(".git"))) {
                    ProcessBuilder pb = new ProcessBuilder("git", "-C", p.toString(), "pull");
                    pb.start().waitFor();
                    sb.append("Updated ").append(pkg).append("\n");
                }
            }
            return sb.length() == 0 ? "No git packages found to update." : sb.toString().trim();
        }
        return install(source, local, cwd, agentDir);
    }

    public static String installAndPersist(String source, boolean local, Path cwd, Path agentDir,
                                           SettingsManager settingsManager)
            throws IOException, InterruptedException {
        String result = install(source, local, cwd, agentDir, settingsManager);
        boolean changed = addSourceToSettings(source, local, settingsManager);
        return result + "\nSettings packages: " + (changed ? "added " : "already contains ") + source;
    }

    public static String removeAndPersist(String source, boolean local, Path cwd, Path agentDir,
                                          SettingsManager settingsManager) throws IOException {
        String result = remove(source, local, cwd, agentDir, settingsManager);
        boolean changed = removeSourceFromSettings(source, local, settingsManager);
        return result + "\nSettings packages: " + (changed ? "removed " : "did not contain ") + source;
    }

    public static boolean addSourceToSettings(String source, boolean local, SettingsManager settingsManager)
            throws IOException {
        Objects.requireNonNull(settingsManager, "settingsManager");
        List<JsonNode> current = packageEntries(local ? settingsManager.getProjectSettings()
                : settingsManager.getGlobalSettings());
        List<JsonNode> next = new ArrayList<>();
        boolean changed = false;
        boolean matched = false;
        for (JsonNode entry : current) {
            if (!sourcesMatch(packageSource(entry), source)) {
                next.add(entry);
                continue;
            }
            matched = true;
            if (source.equals(packageSource(entry))) {
                next.add(entry);
            } else if (entry.isObject()) {
                ObjectNode object = (ObjectNode) entry.deepCopy();
                object.put("source", source);
                next.add(object);
                changed = true;
            } else {
                next.add(TextNode.valueOf(source));
                changed = true;
            }
        }
        if (!matched) {
            next.add(TextNode.valueOf(source));
            changed = true;
        }
        if (changed) {
            writePackages(next, local, settingsManager);
        }
        return changed;
    }

    public static boolean removeSourceFromSettings(String source, boolean local, SettingsManager settingsManager)
            throws IOException {
        Objects.requireNonNull(settingsManager, "settingsManager");
        List<JsonNode> current = packageEntries(local ? settingsManager.getProjectSettings()
                : settingsManager.getGlobalSettings());
        List<JsonNode> next = current.stream()
                .filter(entry -> !sourcesMatch(packageSource(entry), source))
                .toList();
        if (next.size() == current.size()) {
            return false;
        }
        writePackages(next, local, settingsManager);
        return true;
    }

    public static String configurePackageResource(String source, String resourceType, String resourcePath,
                                                  boolean enabled, boolean local, SettingsManager settingsManager)
            throws IOException {
        Objects.requireNonNull(settingsManager, "settingsManager");
        String key = normalizeResourceType(resourceType);
        String pattern = normalizeResourcePath(resourcePath);
        String marker = (enabled ? "+" : "-") + pattern;
        List<JsonNode> current = packageEntries(local ? settingsManager.getProjectSettings()
                : settingsManager.getGlobalSettings());
        List<JsonNode> next = new ArrayList<>();
        boolean found = false;
        boolean changed = false;
        for (JsonNode entry : current) {
            if (!sourcesMatch(packageSource(entry), source)) {
                next.add(entry);
                continue;
            }
            found = true;
            ObjectNode object = entry.isObject()
                    ? (ObjectNode) entry.deepCopy()
                    : JsonCodec.mapper().createObjectNode().put("source", source);
            if (!source.equals(packageSource(entry))) {
                object.put("source", source);
                changed = true;
            }
            List<String> filters = stringArray(object.path(key));
            List<String> updated = new ArrayList<>();
            for (String filter : filters) {
                if (!stripFilterPrefix(filter).equals(pattern)) {
                    updated.add(filter);
                }
            }
            updated.add(marker);
            object.set(key, JsonCodec.mapper().valueToTree(updated));
            next.add(object);
            changed = changed || !filters.equals(updated) || !entry.isObject();
        }
        if (!found) {
            return "Package config\nstatus: not found\nscope: " + (local ? "project" : "global")
                    + "\nsource: " + source;
        }
        if (changed) {
            writePackages(next, local, settingsManager);
        }
        return "Package config\nstatus: " + (changed ? "updated" : "unchanged")
                + "\nscope: " + (local ? "project" : "global")
                + "\nsource: " + source
                + "\ntype: " + key
                + "\nfilter: " + marker;
    }

    public static String listConfiguredPackages(boolean local, SettingsManager settingsManager) {
        List<JsonNode> entries = packageEntries(local ? settingsManager.getProjectSettings()
                : settingsManager.getGlobalSettings());
        StringBuilder out = new StringBuilder();
        out.append("Configured packages (").append(local ? "project" : "global").append("):");
        if (entries.isEmpty()) {
            out.append("\n  (none)");
            return out.toString();
        }
        for (JsonNode entry : entries) {
            String source = packageSource(entry);
            out.append("\n  - ").append(source.isBlank() ? "<unknown>" : source);
            if (entry.isObject()) {
                for (String key : List.of("extensions", "skills", "prompts", "themes")) {
                    List<String> filters = stringArray(entry.path(key));
                    if (!filters.isEmpty()) {
                        out.append("\n      ").append(key).append(": ").append(String.join(", ", filters));
                    }
                }
            }
        }
        return out.toString();
    }

    private static String installNpm(NpmSource source, boolean local, Path cwd, Path agentDir,
                                     SettingsManager settingsManager) throws IOException, InterruptedException {
        Path npmRoot = local ? getLocalNpmPackageDir(cwd) : getGlobalNpmPackageDir(agentDir);
        Files.createDirectories(npmRoot);
        ensureNpmProject(npmRoot);
        List<String> command = npmCommand(settingsManager);
        command.addAll(List.of("install", source.spec(), "--prefix", npmRoot.toString(), "--legacy-peer-deps"));
        runProcess(command, null);
        return "Installed npm package " + source.spec() + " to " + npmInstallPath(source, local, cwd, agentDir);
    }

    private static String removeNpm(NpmSource source, boolean local, Path cwd, Path agentDir,
                                    SettingsManager settingsManager) throws IOException {
        Path npmRoot = local ? getLocalNpmPackageDir(cwd) : getGlobalNpmPackageDir(agentDir);
        Path installedPath = npmInstallPath(source, local, cwd, agentDir);
        if (!Files.exists(npmRoot) && !Files.exists(installedPath)) {
            return "Npm package not found: " + source.name() + " in " + npmRoot;
        }
        try {
            List<String> command = npmCommand(settingsManager);
            command.addAll(List.of("uninstall", source.name(), "--prefix", npmRoot.toString()));
            runProcess(command, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while uninstalling npm package " + source.name(), e);
        }
        if (Files.exists(installedPath)) {
            deleteRecursively(installedPath);
        }
        return "Removed npm package " + source.name() + " from " + npmRoot;
    }

    private static String installGit(GitSource source, boolean local, Path cwd, Path agentDir)
            throws IOException, InterruptedException {
        Path targetDir = gitInstallPath(source, local, cwd, agentDir);
        if (Files.exists(targetDir)) {
            if (source.ref() != null && !source.ref().isBlank()) {
                runGit(targetDir, "fetch", "origin", source.ref());
                runGit(targetDir, "reset", "--hard", "FETCH_HEAD");
                runGit(targetDir, "clean", "-fdx");
                return "Updated git package " + source.host() + "/" + source.path()
                        + "@" + source.ref() + " in " + targetDir;
            }
            runGit(targetDir, "pull");
            return "Updated git package " + source.host() + "/" + source.path() + " in " + targetDir;
        }

        Files.createDirectories(targetDir.getParent());
        runProcess(List.of("git", "clone", source.repo(), targetDir.toString()), null);
        if (source.ref() != null && !source.ref().isBlank()) {
            runGit(targetDir, "checkout", source.ref());
        }
        return "Installed git package " + source.host() + "/" + source.path()
                + (source.ref() == null ? "" : "@" + source.ref()) + " to " + targetDir;
    }

    private static String removeGit(GitSource source, boolean local, Path cwd, Path agentDir) throws IOException {
        Path targetDir = gitInstallPath(source, local, cwd, agentDir);
        if (Files.exists(targetDir)) {
            deleteRecursively(targetDir);
            pruneEmptyParents(targetDir.getParent(), local ? getLocalGitPackageDir(cwd) : getGlobalGitPackageDir(agentDir));
            return "Removed git package " + source.host() + "/" + source.path() + " from " + targetDir;
        }
        return "Git package not found: " + source.host() + "/" + source.path() + " in "
                + (local ? getLocalGitPackageDir(cwd) : getGlobalGitPackageDir(agentDir));
    }

    private static Path gitInstallPath(GitSource source, boolean local, Path cwd, Path agentDir) {
        Path base = local ? getLocalGitPackageDir(cwd) : getGlobalGitPackageDir(agentDir);
        Path target = base.resolve(safeSegment(source.host()));
        for (String part : source.path().split("/")) {
            if (!part.isBlank()) {
                target = target.resolve(safeSegment(part));
            }
        }
        return target.toAbsolutePath().normalize();
    }

    private static Path npmInstallPath(NpmSource source, boolean local, Path cwd, Path agentDir) {
        Path root = (local ? getLocalNpmPackageDir(cwd) : getGlobalNpmPackageDir(agentDir))
                .resolve("node_modules");
        Path target = root;
        for (String part : source.name().split("/")) {
            if (!part.isBlank()) {
                target = target.resolve(part);
            }
        }
        return target.toAbsolutePath().normalize();
    }

    private static String extractPackageName(String source) {
        String name = source;
        NpmSource npmSource = parseNpmSource(source);
        if (npmSource != null) {
            name = npmSource.name();
        }
        GitSource gitSource = parseGitSource(source);
        if (gitSource != null) {
            name = gitSource.path();
        }
        int refIdx = name.indexOf('@', name.startsWith("@") ? 1 : 0);
        if (refIdx >= 0) {
            name = name.substring(0, refIdx);
        }
        if (name.endsWith(".git")) {
            name = name.substring(0, name.length() - 4);
        }
        int idx = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (idx >= 0) {
            name = name.substring(idx + 1);
        }
        return name.isEmpty() ? "package-" + System.currentTimeMillis() : name;
    }

    private record NpmSource(String spec, String name, String version) {}

    private static NpmSource parseNpmSource(String source) {
        if (source == null || !source.trim().startsWith("npm:")) {
            return null;
        }
        String spec = source.trim().substring("npm:".length()).trim();
        if (spec.isBlank()) {
            throw new IllegalArgumentException("npm package spec must not be empty");
        }
        String name;
        String version = null;
        if (spec.startsWith("@")) {
            int slash = spec.indexOf('/');
            if (slash <= 1 || slash == spec.length() - 1) {
                throw new IllegalArgumentException("Invalid scoped npm package spec: " + source);
            }
            int versionIdx = spec.indexOf('@', slash + 1);
            name = versionIdx >= 0 ? spec.substring(0, versionIdx) : spec;
            version = versionIdx >= 0 ? spec.substring(versionIdx + 1) : null;
        } else {
            int versionIdx = spec.indexOf('@');
            name = versionIdx >= 0 ? spec.substring(0, versionIdx) : spec;
            version = versionIdx >= 0 ? spec.substring(versionIdx + 1) : null;
        }
        if (name.isBlank() || name.contains("\\") || name.contains("..") || name.startsWith("/") || name.endsWith("/")) {
            throw new IllegalArgumentException("Invalid npm package name: " + name);
        }
        if (version != null && version.isBlank()) {
            throw new IllegalArgumentException("Invalid npm package version in spec: " + source);
        }
        return new NpmSource(spec, name, version);
    }

    private record GitSource(String repo, String host, String path, String ref) {}

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
            repo = "https://" + repo;
        }
        if (host == null || host.isBlank()) {
            return null;
        }
        path = path.replaceFirst("\\.git$", "").replaceFirst("^/+", "");
        if (path.isBlank() || path.split("/").length < 2 || unsafeGitPart(host, false) || unsafeGitPart(path, true)) {
            return null;
        }
        return new GitSource(repo, host, path, split.ref());
    }

    private record GitRepoRef(String repo, String ref) {}

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

    private static String safeSegment(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static void runGit(Path cwd, String... args) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        runProcess(command, cwd);
    }

    private static void runProcess(List<String> command, Path cwd) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);
        if (cwd != null) {
            pb.directory(cwd.toFile());
        }
        Process process = pb.start();
        byte[] output = process.getInputStream().readAllBytes();
        int code = process.waitFor();
        if (code != 0) {
            throw new IOException(String.join(" ", command) + " failed"
                    + (cwd == null ? "" : " in " + cwd)
                    + (output.length == 0 ? "" : "\n" + new String(output)));
        }
    }

    private static List<Path> installedGitPackageRoots(Path gitDir) {
        if (!Files.isDirectory(gitDir)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(gitDir, 32)) {
            return stream
                    .filter(path -> Files.isDirectory(path.resolve(".git")))
                    .sorted()
                    .map(path -> path.toAbsolutePath().normalize())
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Path defaultAgentDir() {
        return Paths.get(System.getProperty("user.home"), ".pi", "agent");
    }

    private static List<JsonNode> packageEntries(JsonNode settings) {
        JsonNode packages = settings == null ? null : settings.path("packages");
        if (packages == null || !packages.isArray()) {
            return List.of();
        }
        List<JsonNode> entries = new ArrayList<>();
        packages.forEach(entry -> {
            if (entry.isTextual() || entry.isObject()) {
                entries.add(entry.deepCopy());
            }
        });
        return List.copyOf(entries);
    }

    private static String packageSource(JsonNode entry) {
        if (entry == null) {
            return "";
        }
        if (entry.isTextual()) {
            return entry.asText();
        }
        JsonNode source = entry.path("source");
        return source.isTextual() ? source.asText() : "";
    }

    private static boolean sourcesMatch(String existing, String input) {
        if (Objects.equals(existing, input)) {
            return true;
        }
        NpmSource leftNpm = parseNpmSource(existing);
        NpmSource rightNpm = parseNpmSource(input);
        if (leftNpm != null && rightNpm != null) {
            return leftNpm.name().equals(rightNpm.name());
        }
        GitSource left = parseGitSource(existing);
        GitSource right = parseGitSource(input);
        if (left != null && right != null) {
            return left.host().equals(right.host()) && left.path().equals(right.path());
        }
        return false;
    }

    private static void writePackages(List<JsonNode> packages, boolean local, SettingsManager settingsManager)
            throws IOException {
        if (local) {
            settingsManager.setProjectPackages(packages);
        } else {
            settingsManager.setPackages(packages);
        }
    }

    private static String normalizeResourceType(String resourceType) {
        String normalized = resourceType == null ? "" : resourceType.trim().toLowerCase();
        normalized = switch (normalized) {
            case "extension" -> "extensions";
            case "skill" -> "skills";
            case "prompt" -> "prompts";
            case "theme" -> "themes";
            default -> normalized;
        };
        if (!List.of("extensions", "skills", "prompts", "themes").contains(normalized)) {
            throw new IllegalArgumentException("Resource type must be one of extensions, skills, prompts, themes");
        }
        return normalized;
    }

    private static String normalizeResourcePath(String resourcePath) {
        String normalized = resourcePath == null ? "" : resourcePath.trim().replace('\\', '/');
        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("Resource path must not be empty");
        }
        return normalized;
    }

    private static String stripFilterPrefix(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.startsWith("!") || trimmed.startsWith("+") || trimmed.startsWith("-")
                ? trimmed.substring(1)
                : trimmed;
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

    private static void pruneEmptyParents(Path start, Path root) throws IOException {
        if (start == null || root == null) {
            return;
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path current = start.toAbsolutePath().normalize();
        while (current.startsWith(normalizedRoot) && !current.equals(normalizedRoot)) {
            if (!Files.isDirectory(current)) {
                current = current.getParent();
                continue;
            }
            try (Stream<Path> entries = Files.list(current)) {
                if (entries.findAny().isPresent()) {
                    return;
                }
            }
            Files.deleteIfExists(current);
            current = current.getParent();
        }
    }

    private static List<String> npmCommand(SettingsManager settingsManager) {
        List<String> configured = settingsManager == null ? List.of() : settingsManager.getNpmCommand();
        if (configured == null || configured.isEmpty()) {
            return new ArrayList<>(List.of("npm"));
        }
        if (configured.get(0) == null || configured.get(0).isBlank()) {
            throw new IllegalArgumentException("Invalid npmCommand: first entry must be a command");
        }
        return new ArrayList<>(configured);
    }

    private static void ensureNpmProject(Path npmRoot) throws IOException {
        Files.createDirectories(npmRoot);
        Path packageJson = npmRoot.resolve("package.json");
        if (!Files.exists(packageJson)) {
            Files.writeString(packageJson, """
                    {"name":"pi-packages","private":true}
                    """);
        }
    }

    private static List<Path> installedNpmPackageRoots(Path npmRoot) {
        Path nodeModules = npmRoot.resolve("node_modules");
        if (!Files.isDirectory(nodeModules)) {
            return List.of();
        }
        List<Path> roots = new ArrayList<>();
        try (Stream<Path> stream = Files.list(nodeModules)) {
            for (Path path : stream.sorted().toList()) {
                if (!Files.isDirectory(path)) {
                    continue;
                }
                String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
                if (fileName.startsWith("@")) {
                    try (Stream<Path> scoped = Files.list(path)) {
                        scoped.filter(Files::isDirectory)
                                .sorted()
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
}
