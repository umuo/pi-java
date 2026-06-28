package works.earendil.pi.codingagent.resources;

import java.nio.file.Path;
import java.util.List;

public final class ResourceLoader {
    private final Path cwd;
    private final Path agentDir;
    private final boolean projectTrusted;
    private final List<Path> skillPaths;
    private final List<Path> promptPaths;
    private final boolean includeDefaults;
    private final boolean noContextFiles;
    private final String systemPromptSource;
    private final List<String> appendSystemPromptSource;

    private SkillLoader.LoadSkillsResult skills = new SkillLoader.LoadSkillsResult(List.of(), List.of());
    private List<PromptTemplate> prompts = List.of();
    private List<ProjectContextLoader.ContextFile> contextFiles = List.of();
    private String systemPrompt;
    private List<String> appendSystemPrompt = List.of();

    public ResourceLoader(Path cwd, Path agentDir, boolean projectTrusted, List<Path> skillPaths, List<Path> promptPaths,
                          boolean includeDefaults, boolean noContextFiles, String systemPromptSource,
                          List<String> appendSystemPromptSource) {
        this.cwd = cwd.toAbsolutePath().normalize();
        this.agentDir = agentDir.toAbsolutePath().normalize();
        this.projectTrusted = projectTrusted;
        this.skillPaths = List.copyOf(skillPaths);
        this.promptPaths = List.copyOf(promptPaths);
        this.includeDefaults = includeDefaults;
        this.noContextFiles = noContextFiles;
        this.systemPromptSource = systemPromptSource;
        this.appendSystemPromptSource = appendSystemPromptSource;
    }

    public void reload() {
        skills = SkillLoader.loadSkills(new SkillLoader.LoadSkillsOptions(cwd, agentDir, skillPaths, includeDefaults));
        prompts = PromptTemplateLoader.loadPromptTemplates(new PromptTemplateLoader.LoadPromptTemplatesOptions(
                cwd, agentDir, promptPaths, includeDefaults));
        contextFiles = noContextFiles ? List.of() : ProjectContextLoader.loadProjectContextFiles(cwd, agentDir);
        ProjectContextLoader.PromptSources promptSources = ProjectContextLoader.resolvePromptSources(
                cwd, agentDir, projectTrusted, systemPromptSource, appendSystemPromptSource);
        systemPrompt = promptSources.systemPrompt();
        appendSystemPrompt = promptSources.appendSystemPrompt();
    }

    public SkillLoader.LoadSkillsResult skills() {
        return skills;
    }

    public List<PromptTemplate> prompts() {
        return prompts;
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
}
