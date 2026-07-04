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
        addUnique(this.skillPaths, skillPaths);
        addUnique(this.promptPaths, promptPaths);
        addUnique(this.themePaths, themePaths);
        addJsonUnique(this.packageEntries, packageEntries);
        this.includeDefaults = includeDefaults;
        this.noContextFiles = noContextFiles;
        this.systemPromptSource = systemPromptSource;
        this.appendSystemPromptSource = appendSystemPromptSource;
    }

    public void reload() {
        PackageResourceResolver.PackageResourcePaths packagePaths = includeDefaults
                ? PackageResourceResolver.resolve(cwd, agentDir, projectTrusted, packageEntries)
                : new PackageResourceResolver.PackageResourcePaths(List.of(), List.of(), List.of(), List.of());
        skills = SkillLoader.loadSkills(new SkillLoader.LoadSkillsOptions(cwd, agentDir,
                merge(skillPaths, packagePaths.skills()), includeDefaults,
                projectTrusted));
        prompts = PromptTemplateLoader.loadPromptTemplates(new PromptTemplateLoader.LoadPromptTemplatesOptions(
                cwd, agentDir, merge(promptPaths, packagePaths.prompts()), includeDefaults));
        themes = ThemeResourceLoader.loadThemes(new ThemeResourceLoader.LoadThemesOptions(cwd, agentDir,
                merge(themePaths, packagePaths.themes()), includeDefaults));
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
            Path normalized = normalize(addition);
            if (!target.contains(normalized)) {
                target.add(normalized);
                changed = true;
            }
        }
        return changed;
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
}
