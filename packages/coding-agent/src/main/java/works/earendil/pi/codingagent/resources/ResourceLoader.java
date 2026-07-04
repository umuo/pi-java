package works.earendil.pi.codingagent.resources;

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
        this.cwd = cwd.toAbsolutePath().normalize();
        this.agentDir = agentDir.toAbsolutePath().normalize();
        this.projectTrusted = projectTrusted;
        this.skillPaths = new ArrayList<>();
        this.promptPaths = new ArrayList<>();
        this.themePaths = new ArrayList<>();
        addUnique(this.skillPaths, skillPaths);
        addUnique(this.promptPaths, promptPaths);
        addUnique(this.themePaths, themePaths);
        this.includeDefaults = includeDefaults;
        this.noContextFiles = noContextFiles;
        this.systemPromptSource = systemPromptSource;
        this.appendSystemPromptSource = appendSystemPromptSource;
    }

    public void reload() {
        skills = SkillLoader.loadSkills(new SkillLoader.LoadSkillsOptions(cwd, agentDir, skillPaths, includeDefaults,
                projectTrusted));
        prompts = PromptTemplateLoader.loadPromptTemplates(new PromptTemplateLoader.LoadPromptTemplatesOptions(
                cwd, agentDir, promptPaths, includeDefaults));
        themes = ThemeResourceLoader.loadThemes(new ThemeResourceLoader.LoadThemesOptions(cwd, agentDir, themePaths,
                includeDefaults));
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

    private Path normalize(Path path) {
        Objects.requireNonNull(path, "path");
        return path.isAbsolute()
                ? path.toAbsolutePath().normalize()
                : cwd.resolve(path).toAbsolutePath().normalize();
    }
}
