package works.earendil.pi.codingagent.core;

import works.earendil.pi.codingagent.resources.ProjectContextLoader;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.codingagent.resources.SkillLoader;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SystemPromptBuilder {
    private static final List<String> DEFAULT_TOOLS = List.of("read", "bash", "edit", "write");

    private SystemPromptBuilder() {
    }

    public record BuildOptions(
            String customPrompt,
            List<String> selectedTools,
            Map<String, String> toolSnippets,
            List<String> promptGuidelines,
            String appendSystemPrompt,
            Path cwd,
            List<ProjectContextLoader.ContextFile> contextFiles,
            List<Skill> skills,
            Path readmePath,
            Path docsPath,
            Path examplesPath,
            Path agentDir,
            LocalDate date
    ) {
    }

    public static String build(BuildOptions options) {
        Path cwd = options.cwd() == null ? Path.of("").toAbsolutePath().normalize() : options.cwd().toAbsolutePath().normalize();
        LocalDate date = options.date() == null ? LocalDate.now() : options.date();
        List<ProjectContextLoader.ContextFile> contextFiles = options.contextFiles() == null ? List.of() : options.contextFiles();
        List<Skill> skills = options.skills() == null ? List.of() : options.skills();
        List<String> selectedTools = options.selectedTools() == null ? DEFAULT_TOOLS : options.selectedTools();
        String appendSection = options.appendSystemPrompt() == null || options.appendSystemPrompt().isEmpty()
                ? ""
                : "\n\n" + options.appendSystemPrompt();
        String promptCwd = cwd.toString().replace('\\', '/');

        if (options.customPrompt() != null) {
            StringBuilder custom = new StringBuilder(options.customPrompt());
            custom.append(appendSection);
            appendProjectContext(custom, contextFiles);
            if (selectedTools.contains("read") && !skills.isEmpty()) {
                custom.append(SkillLoader.formatSkillsForPrompt(skills,
                        new SkillLoader.SkillPromptContext(cwd, options.agentDir(), date, Map.of())));
            }
            appendDateAndCwd(custom, date, promptCwd);
            return custom.toString();
        }

        Set<String> toolSet = new LinkedHashSet<>(selectedTools);
        Map<String, String> snippets = options.toolSnippets() == null ? Map.of() : options.toolSnippets();
        List<String> visibleTools = selectedTools.stream()
                .filter(name -> snippets.get(name) != null && !snippets.get(name).isBlank())
                .toList();
        String toolsList = visibleTools.isEmpty()
                ? "(none)"
                : String.join("\n", visibleTools.stream()
                .map(name -> "- " + name + ": " + snippets.get(name))
                .toList());

        String guidelines = buildGuidelines(toolSet, options.promptGuidelines());
        Path readmePath = defaultPath(options.readmePath(), "README.md");
        Path docsPath = defaultPath(options.docsPath(), "docs");
        Path examplesPath = defaultPath(options.examplesPath(), "examples");

        StringBuilder prompt = new StringBuilder();
        prompt.append("""
                You are an expert coding assistant operating inside pi, a coding agent harness. You help users by reading files, executing commands, editing code, and writing new files.

                Available tools:
                """);
        prompt.append(toolsList);
        prompt.append("""

                In addition to the tools above, you may have access to other custom tools depending on the project.

                Guidelines:
                """);
        prompt.append(guidelines);
        prompt.append("\n\nPi documentation (read only when the user asks about pi itself, its SDK, extensions, themes, skills, or TUI):\n");
        prompt.append("- Main documentation: ").append(readmePath).append('\n');
        prompt.append("- Additional docs: ").append(docsPath).append('\n');
        prompt.append("- Examples: ").append(examplesPath).append(" (extensions, custom tools, SDK)\n");
        prompt.append("- When reading pi docs or examples, resolve docs/... under Additional docs and examples/... under Examples, not the current working directory\n");
        prompt.append("- When asked about: extensions (docs/extensions.md, examples/extensions/), themes (docs/themes.md), skills (docs/skills.md), prompt templates (docs/prompt-templates.md), TUI components (docs/tui.md), keybindings (docs/keybindings.md), SDK integrations (docs/sdk.md), custom providers (docs/custom-provider.md), adding models (docs/models.md), pi packages (docs/packages.md)\n");
        prompt.append("- When working on pi topics, read the docs and examples, and follow .md cross-references before implementing\n");
        prompt.append("- Always read pi .md files completely and follow links to related docs (e.g., tui.md for TUI API details)");
        prompt.append(appendSection);
        appendProjectContext(prompt, contextFiles);
        if (toolSet.contains("read") && !skills.isEmpty()) {
            prompt.append(SkillLoader.formatSkillsForPrompt(skills,
                    new SkillLoader.SkillPromptContext(cwd, options.agentDir(), date, Map.of())));
        }
        appendDateAndCwd(prompt, date, promptCwd);
        return prompt.toString();
    }

    private static String buildGuidelines(Set<String> tools, List<String> promptGuidelines) {
        List<String> guidelines = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        if (tools.contains("bash") && !tools.contains("grep") && !tools.contains("find") && !tools.contains("ls")) {
            addGuideline(guidelines, seen, "Use bash for file operations like ls, rg, find");
        }
        if (promptGuidelines != null) {
            for (String guideline : promptGuidelines) {
                if (guideline != null) {
                    addGuideline(guidelines, seen, guideline.trim());
                }
            }
        }
        addGuideline(guidelines, seen, "Be concise in your responses");
        addGuideline(guidelines, seen, "Show file paths clearly when working with files");
        return String.join("\n", guidelines.stream().map(guideline -> "- " + guideline).toList());
    }

    private static void addGuideline(List<String> guidelines, Set<String> seen, String guideline) {
        if (!guideline.isBlank() && seen.add(guideline)) {
            guidelines.add(guideline);
        }
    }

    private static void appendProjectContext(StringBuilder prompt, List<ProjectContextLoader.ContextFile> contextFiles) {
        if (contextFiles.isEmpty()) {
            return;
        }
        prompt.append("\n\n<project_context>\n\n");
        prompt.append("Project-specific instructions and guidelines:\n\n");
        for (ProjectContextLoader.ContextFile contextFile : contextFiles) {
            prompt.append("<project_instructions path=\"")
                    .append(contextFile.path())
                    .append("\">\n")
                    .append(contextFile.content())
                    .append("\n</project_instructions>\n\n");
        }
        prompt.append("</project_context>\n");
    }

    private static void appendDateAndCwd(StringBuilder prompt, LocalDate date, String promptCwd) {
        prompt.append("\nCurrent date: ").append(date);
        prompt.append("\nCurrent working directory: ").append(promptCwd);
    }

    private static Path defaultPath(Path path, String fallback) {
        return path == null ? Path.of("").toAbsolutePath().normalize().resolve(fallback) : path.toAbsolutePath().normalize();
    }
}
