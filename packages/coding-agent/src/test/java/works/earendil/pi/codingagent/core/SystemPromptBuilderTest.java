package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.codingagent.resources.ProjectContextLoader;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.codingagent.resources.SourceInfo;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {
    @TempDir
    Path tempDir;

    @Test
    void buildsDefaultPromptWithToolsGuidelinesContextSkillsAndDate() {
        Path cwd = tempDir.resolve("project");
        Path skillPath = tempDir.resolve("skills").resolve("review").resolve("SKILL.md");
        Skill skill = new Skill("review", "Review Java code", skillPath, skillPath.getParent(),
                SourceInfo.local(skillPath, "project", skillPath.getParent()), false);

        String prompt = SystemPromptBuilder.build(new SystemPromptBuilder.BuildOptions(
                null,
                List.of("read", "bash", "edit"),
                Map.of("read", "Read file contents", "bash", "Execute a bash command", "edit", "Edit files"),
                List.of("Be concise in your responses", "Prefer tests", "Prefer tests", " "),
                "Extra system text",
                cwd,
                List.of(new ProjectContextLoader.ContextFile(cwd.resolve("AGENTS.md"), "Project rules")),
                List.of(skill),
                tempDir.resolve("README.md"),
                tempDir.resolve("docs"),
                tempDir.resolve("examples"),
                tempDir.resolve("agent"),
                LocalDate.of(2026, 6, 28)
        ));

        assertThat(prompt).contains("Available tools:\n- read: Read file contents\n- bash: Execute a bash command\n- edit: Edit files");
        assertThat(prompt).contains("- Use bash for file operations like ls, rg, find");
        assertThat(prompt).containsOnlyOnce("- Prefer tests");
        assertThat(prompt).containsOnlyOnce("- Be concise in your responses");
        assertThat(prompt).contains("Extra system text");
        assertThat(prompt).contains("<project_instructions path=\"" + cwd.resolve("AGENTS.md") + "\">\nProject rules");
        assertThat(prompt).contains("<available_skills>");
        assertThat(prompt).contains("<name>review</name>");
        assertThat(prompt).endsWith("Current date: 2026-06-28\nCurrent working directory: " + cwd.toAbsolutePath());
    }

    @Test
    void customPromptIncludesAppendContextAndOmitsSkillsWithoutReadTool() {
        Path cwd = tempDir.resolve("project");
        Path skillPath = tempDir.resolve("skills").resolve("deploy").resolve("SKILL.md");
        Skill skill = new Skill("deploy", "Deploy services", skillPath, skillPath.getParent(),
                SourceInfo.local(skillPath, "project", skillPath.getParent()), false);

        String prompt = SystemPromptBuilder.build(new SystemPromptBuilder.BuildOptions(
                "Custom base",
                List.of("bash"),
                Map.of(),
                List.of(),
                "Appendix",
                cwd,
                List.of(new ProjectContextLoader.ContextFile(cwd.resolve("CLAUDE.md"), "Local instruction")),
                List.of(skill),
                null,
                null,
                null,
                tempDir.resolve("agent"),
                LocalDate.of(2026, 1, 2)
        ));

        assertThat(prompt).startsWith("Custom base\n\nAppendix");
        assertThat(prompt).contains("<project_context>");
        assertThat(prompt).doesNotContain("<available_skills>");
        assertThat(prompt).endsWith("Current date: 2026-01-02\nCurrent working directory: " + cwd.toAbsolutePath());
    }

    @Test
    void rendersSkillDescriptionVariablesWithPromptContext() {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Path skillPath = tempDir.resolve("skills").resolve("audit").resolve("SKILL.md");
        Skill skill = new Skill("audit",
                "Audit {{cwd}} with ${agent_dir} on {{date}} using {{skill_name}} from {{skill_dir}}",
                skillPath, skillPath.getParent(), SourceInfo.local(skillPath, "project", skillPath.getParent()),
                false);

        String prompt = SystemPromptBuilder.build(new SystemPromptBuilder.BuildOptions(
                null,
                List.of("read"),
                Map.of("read", "Read file contents"),
                List.of(),
                null,
                cwd,
                List.of(),
                List.of(skill),
                tempDir.resolve("README.md"),
                tempDir.resolve("docs"),
                tempDir.resolve("examples"),
                agentDir,
                LocalDate.of(2026, 6, 30)
        ));

        assertThat(prompt).contains("<description>Audit " + cwd.toAbsolutePath().normalize()
                + " with " + agentDir.toAbsolutePath().normalize()
                + " on 2026-06-30 using audit from " + skillPath.getParent().toAbsolutePath().normalize()
                + "</description>");
    }

    @Test
    void hidesManualOnlySkillsFromModelVisiblePrompt() {
        Path cwd = tempDir.resolve("project");
        Path visiblePath = tempDir.resolve("skills").resolve("visible").resolve("SKILL.md");
        Path manualPath = tempDir.resolve("skills").resolve("manual").resolve("SKILL.md");
        Skill visible = new Skill("visible", "Visible skill", visiblePath, visiblePath.getParent(),
                SourceInfo.local(visiblePath, "project", visiblePath.getParent()), false);
        Skill manual = new Skill("manual", "Manual skill", manualPath, manualPath.getParent(),
                SourceInfo.local(manualPath, "project", manualPath.getParent()), true);

        String prompt = SystemPromptBuilder.build(new SystemPromptBuilder.BuildOptions(
                null,
                List.of("read"),
                Map.of("read", "Read file contents"),
                List.of(),
                null,
                cwd,
                List.of(),
                List.of(visible, manual),
                tempDir.resolve("README.md"),
                tempDir.resolve("docs"),
                tempDir.resolve("examples"),
                tempDir.resolve("agent"),
                LocalDate.of(2026, 7, 1)
        ));

        assertThat(prompt).contains("<name>visible</name>");
        assertThat(prompt).doesNotContain("<name>manual</name>");
        assertThat(prompt).doesNotContain("Manual skill");
    }

    @Test
    void rendersSkillTriggerHintsInSystemPrompt() {
        Path cwd = tempDir.resolve("project");
        Path skillPath = tempDir.resolve("skills").resolve("diagnose").resolve("SKILL.md");
        Skill skill = new Skill("diagnose", "Diagnose flaky tests", skillPath, skillPath.getParent(),
                SourceInfo.local(skillPath, "project", skillPath.getParent()), false, "auto",
                List.of("flaky", "timeout"), List.of("test.*failure"), List.of("**/*Test.java"));

        String prompt = SystemPromptBuilder.build(new SystemPromptBuilder.BuildOptions(
                null,
                List.of("read"),
                Map.of("read", "Read file contents"),
                List.of(),
                null,
                cwd,
                List.of(),
                List.of(skill),
                tempDir.resolve("README.md"),
                tempDir.resolve("docs"),
                tempDir.resolve("examples"),
                tempDir.resolve("agent"),
                LocalDate.of(2026, 7, 1)
        ));

        assertThat(prompt)
                .contains("When trigger hints are present")
                .contains("<activation>")
                .contains("<trigger_terms>")
                .contains("<item>flaky</item>")
                .contains("<trigger_patterns>")
                .contains("<item>test.*failure</item>")
                .contains("<trigger_globs>")
                .contains("<item>**/*Test.java</item>");
    }
}
