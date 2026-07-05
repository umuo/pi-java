package works.earendil.pi.codingagent.resources;

import com.fasterxml.jackson.databind.JsonNode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ResourceLoader {
    private final Path cwd;
    private final Path agentDir;
    private final boolean projectTrusted;
    private final List<Path> skillPaths;
    private final List<Path> promptPaths;
    private final List<Path> themePaths;
    private final List<JsonNode> packageEntries;
    private final List<JsonNode> globalPackageEntries;
    private final List<JsonNode> projectPackageEntries;
    private final boolean includeDefaults;
    private final boolean noContextFiles;
    private final String systemPromptSource;
    private final List<String> appendSystemPromptSource;

    private SkillLoader.LoadSkillsResult skills = new SkillLoader.LoadSkillsResult(List.of(), List.of());
    private List<PromptTemplate> prompts = List.of();
    private ThemeResourceLoader.LoadThemesResult themes = new ThemeResourceLoader.LoadThemesResult(List.of(), List.of());
    private List<ProjectContextLoader.ContextFile> contextFiles = List.of();
    private String systemPrompt;
    private List<String> appendSystemPrompt = List.of();

    public ResourceLoader(Path cwd, Path agentDir, boolean projectTrusted, List<Path> skillPaths, List<Path> promptPaths,
                          boolean includeDefaults, boolean noContextFiles, String systemPromptSource,
                          List<String> appendSystemPromptSource) {
        this(cwd, agentDir, projectTrusted, skillPaths, promptPaths, List.of(), includeDefaults, noContextFiles,
                systemPromptSource, appendSystemPromptSource);
    }

    public ResourceLoader(Path cwd, Path agentDir, boolean projectTrusted, List<Path> skillPaths, List<Path> promptPaths,
                          List<Path> themePaths, boolean includeDefaults, boolean noContextFiles,
                          String systemPromptSource, List<String> appendSystemPromptSource) {
        this(cwd, agentDir, projectTrusted, skillPaths, promptPaths, themePaths, List.of(), includeDefaults,
                noContextFiles, systemPromptSource, appendSystemPromptSource);
    }

    public ResourceLoader(Path cwd, Path agentDir, boolean projectTrusted, List<Path> skillPaths, List<Path> promptPaths,
                          List<Path> themePaths, List<JsonNode> packageEntries, boolean includeDefaults,
                          boolean noContextFiles, String systemPromptSource, List<String> appendSystemPromptSource) {
        this.cwd = cwd.toAbsolutePath().normalize();
        this.agentDir = agentDir.toAbsolutePath().normalize();
        this.projectTrusted = projectTrusted;
        this.skillPaths = new ArrayList<>();
        this.promptPaths = new ArrayList<>();
        this.themePaths = new ArrayList<>();
        this.packageEntries = new ArrayList<>();
        this.globalPackageEntries = new ArrayList<>();
        this.projectPackageEntries = new ArrayList<>();
        addUnique(this.skillPaths, skillPaths);
        addUnique(this.promptPaths, promptPaths);
        addUnique(this.themePaths, themePaths);
        addJsonUnique(this.packageEntries, packageEntries);
        addJsonUnique(this.globalPackageEntries, packageEntries);
        this.includeDefaults = includeDefaults;
        this.noContextFiles = noContextFiles;
        this.systemPromptSource = systemPromptSource;
        this.appendSystemPromptSource = appendSystemPromptSource;
    }

    public ResourceLoader(Path cwd, Path agentDir, boolean projectTrusted, List<Path> skillPaths, List<Path> promptPaths,
                          List<Path> themePaths, List<JsonNode> globalPackageEntries,
                          List<JsonNode> projectPackageEntries, boolean includeDefaults,
                          boolean noContextFiles, String systemPromptSource,
                          List<String> appendSystemPromptSource) {
        this.cwd = cwd.toAbsolutePath().normalize();
        this.agentDir = agentDir.toAbsolutePath().normalize();
        this.projectTrusted = projectTrusted;
        this.skillPaths = new ArrayList<>();
        this.promptPaths = new ArrayList<>();
        this.themePaths = new ArrayList<>();
        this.packageEntries = new ArrayList<>();
        this.globalPackageEntries = new ArrayList<>();
        this.projectPackageEntries = new ArrayList<>();
        addUnique(this.skillPaths, skillPaths);
        addUnique(this.promptPaths, promptPaths);
        addUnique(this.themePaths, themePaths);
        addJsonUnique(this.globalPackageEntries, globalPackageEntries);
        addJsonUnique(this.projectPackageEntries, projectPackageEntries);
        addJsonUnique(this.packageEntries, globalPackageEntries);
        addJsonUnique(this.packageEntries, projectPackageEntries);
        this.includeDefaults = includeDefaults;
        this.noContextFiles = noContextFiles;
        this.systemPromptSource = systemPromptSource;
        this.appendSystemPromptSource = appendSystemPromptSource;
    }

    public void reload() {
        ResourcePathFilter skillFilter = ResourcePathFilter.from(skillPaths, cwd, agentDir);
        ResourcePathFilter promptFilter = ResourcePathFilter.from(promptPaths, cwd, agentDir);
        ResourcePathFilter themeFilter = ResourcePathFilter.from(themePaths, cwd, agentDir);
        PackageResourceResolver.PackageResourcePaths packagePaths = includeDefaults
                ? PackageResourceResolver.resolve(cwd, agentDir, projectTrusted,
                globalPackageEntries, projectPackageEntries)
                : new PackageResourceResolver.PackageResourcePaths(List.of(), List.of(), List.of(), List.of());
        skills = SkillLoader.loadSkills(new SkillLoader.LoadSkillsOptions(cwd, agentDir,
                merge(skillFilter.enabledPaths(), packagePaths.skills()), includeDefaults,
                projectTrusted));
        skills = new SkillLoader.LoadSkillsResult(skills.skills().stream()
                .filter(skill -> !skillFilter.disabled(skill.filePath()))
                .toList(), skills.diagnostics());
        prompts = PromptTemplateLoader.loadPromptTemplates(new PromptTemplateLoader.LoadPromptTemplatesOptions(
                cwd, agentDir, merge(promptFilter.enabledPaths(), packagePaths.prompts()), includeDefaults));
        prompts = prompts.stream()
                .filter(prompt -> !promptFilter.disabled(prompt.filePath()))
                .toList();
        themes = ThemeResourceLoader.loadThemes(new ThemeResourceLoader.LoadThemesOptions(cwd, agentDir,
                merge(themeFilter.enabledPaths(), packagePaths.themes()), includeDefaults));
        themes = new ThemeResourceLoader.LoadThemesResult(themes.themes().stream()
                .filter(theme -> !themeFilter.disabled(theme.filePath()))
                .toList(), themes.diagnostics());
        contextFiles = noContextFiles ? List.of() : ProjectContextLoader.loadProjectContextFiles(cwd, agentDir);
        ProjectContextLoader.PromptSources promptSources = ProjectContextLoader.resolvePromptSources(
                cwd, agentDir, projectTrusted, systemPromptSource, appendSystemPromptSource);
        systemPrompt = promptSources.systemPrompt();
        appendSystemPrompt = promptSources.appendSystemPrompt();
    }

    public void extendResources(List<Path> additionalSkillPaths, List<Path> additionalPromptPaths) {
        extendResources(additionalSkillPaths, additionalPromptPaths, List.of());
    }

    public void extendResources(List<Path> additionalSkillPaths, List<Path> additionalPromptPaths,
                                List<Path> additionalThemePaths) {
        boolean changed = false;
        changed |= addUnique(skillPaths, additionalSkillPaths);
        changed |= addUnique(promptPaths, additionalPromptPaths);
        changed |= addUnique(themePaths, additionalThemePaths);
        if (changed) {
            reload();
        }
    }

    public SkillLoader.LoadSkillsResult skills() {
        return skills;
    }

    public List<PromptTemplate> prompts() {
        return prompts;
    }

    public ThemeResourceLoader.LoadThemesResult themes() {
        return themes;
    }

    public List<ProjectContextLoader.ContextFile> contextFiles() {
        return contextFiles;
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public List<String> appendSystemPrompt() {
        return appendSystemPrompt;
    }

    private boolean addUnique(List<Path> target, List<Path> additions) {
        if (additions == null || additions.isEmpty()) {
            return false;
        }
        boolean changed = false;
        for (Path addition : additions) {
            if (addition == null) {
                continue;
            }
            Path normalized = hasResourceFilterPrefix(addition) ? addition : normalize(addition);
            if (!target.contains(normalized)) {
                target.add(normalized);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean hasResourceFilterPrefix(Path path) {
        String value = path.toString();
        return !value.isBlank() && List.of('+', '-', '!').contains(value.charAt(0));
    }

    private void addJsonUnique(List<JsonNode> target, List<JsonNode> additions) {
        if (additions == null || additions.isEmpty()) {
            return;
        }
        for (JsonNode addition : additions) {
            if (addition != null && target.stream().noneMatch(existing -> existing.equals(addition))) {
                target.add(addition.deepCopy());
            }
        }
    }

    private Path normalize(Path path) {
        Objects.requireNonNull(path, "path");
        return path.isAbsolute()
                ? path.toAbsolutePath().normalize()
                : cwd.resolve(path).toAbsolutePath().normalize();
    }

    private static List<Path> merge(List<Path> first, List<Path> second) {
        if ((first == null || first.isEmpty()) && (second == null || second.isEmpty())) {
            return List.of();
        }
        List<Path> merged = new ArrayList<>();
        if (first != null) {
            merged.addAll(first);
        }
        if (second != null) {
            for (Path path : second) {
                if (path != null && !merged.contains(path)) {
                    merged.add(path);
                }
            }
        }
        return List.copyOf(merged);
    }

    private record ResourcePathFilter(List<Path> enabledPaths, List<String> disabledPatterns,
                                      Path cwd, Path agentDir) {
        static ResourcePathFilter from(List<Path> rawPaths, Path cwd, Path agentDir) {
            if (rawPaths == null || rawPaths.isEmpty()) {
                return new ResourcePathFilter(List.of(), List.of(), cwd, agentDir);
            }
            List<Path> enabled = new ArrayList<>();
            List<String> disabled = new ArrayList<>();
            for (Path rawPath : rawPaths) {
                if (rawPath == null) {
                    continue;
                }
                String raw = rawPath.toString();
                if (raw.isBlank()) {
                    continue;
                }
                char prefix = raw.charAt(0);
                if (prefix == '-' || prefix == '!') {
                    String pattern = normalizePattern(raw.substring(1));
                    if (!pattern.isBlank()) {
                        disabled.add(pattern);
                    }
                    continue;
                }
                String path = prefix == '+' ? raw.substring(1) : raw;
                if (!path.isBlank()) {
                    enabled.add(Path.of(path));
                }
            }
            return new ResourcePathFilter(List.copyOf(enabled), List.copyOf(disabled), cwd, agentDir);
        }

        boolean disabled(Path path) {
            if (path == null || disabledPatterns.isEmpty()) {
                return false;
            }
            Path normalized = path.toAbsolutePath().normalize();
            for (String pattern : disabledPatterns) {
                if (matches(normalized, cwd, pattern)
                        || matches(normalized, agentDir, pattern)
                        || matches(normalized, cwd.resolve(".pi"), pattern)) {
                    return true;
                }
            }
            return false;
        }

        private static boolean matches(Path path, Path base, String pattern) {
            Path normalizedBase = base.toAbsolutePath().normalize();
            if (!path.startsWith(normalizedBase)) {
                return false;
            }
            return normalizePattern(normalizedBase.relativize(path).toString()).equals(pattern);
        }

        private static String normalizePattern(String value) {
            String pattern = value == null ? "" : value.trim().replace('\\', '/');
            while (pattern.startsWith("./")) {
                pattern = pattern.substring(2);
            }
            while (pattern.startsWith("/")) {
                pattern = pattern.substring(1);
            }
            return pattern;
        }
    }
}
