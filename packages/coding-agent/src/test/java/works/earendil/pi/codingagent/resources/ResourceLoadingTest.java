package works.earendil.pi.codingagent.resources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.codingagent.util.Frontmatter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceLoadingTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesYamlFrontmatterAndBody() {
        Frontmatter.Parsed parsed = Frontmatter.parse("---\nname: test\ndescription: Demo\n---\nBody\n");

        assertThat(parsed.frontmatter()).containsEntry("name", "test");
        assertThat(parsed.body()).isEqualTo("Body");
    }

    @Test
    void discoversSkillMdBeforeRecursingAndFormatsPrompt() throws Exception {
        Path skills = tempDir.resolve("skills");
        Path root = skills.resolve("writer");
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("SKILL.md"), "---\nname: writer\ndescription: Write things\n---\nUse care.");
        Files.writeString(root.resolve("nested").resolve("SKILL.md"), "---\nname: nested\ndescription: Nested\n---\nNested.");

        SkillLoader.LoadSkillsResult result = SkillLoader.loadSkillsFromDir(new SkillLoader.LoadSkillsFromDirOptions(skills, "user"));

        assertThat(result.skills()).extracting(Skill::name).containsExactly("writer");
        assertThat(result.diagnostics()).isEmpty();
        assertThat(SkillLoader.formatSkillsForPrompt(result.skills()))
                .contains("<name>writer</name>")
                .contains("<description>Write things</description>");
    }

    @Test
    void reportsSkillNameCollisions() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path project = tempDir.resolve("project");
        Files.createDirectories(agentDir.resolve("skills").resolve("one"));
        Files.createDirectories(project.resolve(".pi").resolve("skills").resolve("two"));
        Files.writeString(agentDir.resolve("skills").resolve("one").resolve("SKILL.md"),
                "---\nname: same\ndescription: One\n---\nOne");
        Files.writeString(project.resolve(".pi").resolve("skills").resolve("two").resolve("SKILL.md"),
                "---\nname: same\ndescription: Two\n---\nTwo");

        SkillLoader.LoadSkillsResult result = SkillLoader.loadSkills(
                new SkillLoader.LoadSkillsOptions(project, agentDir, List.of(), true, true));

        assertThat(result.skills()).extracting(Skill::description).containsExactly("One");
        assertThat(result.diagnostics()).anyMatch(d -> d instanceof ResourceDiagnostic.Collision);
    }

    @Test
    void loadsTrustedAgentsSkillsFromCurrentAndAncestorDirectories() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path repo = tempDir.resolve("repo");
        Path nested = repo.resolve("packages").resolve("app");
        Files.createDirectories(agentDir);
        Files.createDirectories(repo.resolve(".git"));
        Files.createDirectories(repo.resolve(".agents").resolve("skills").resolve("repo-skill"));
        Files.createDirectories(nested.resolve(".agents").resolve("skills").resolve("local-skill"));
        Files.writeString(repo.resolve(".agents").resolve("skills").resolve("repo-skill").resolve("SKILL.md"),
                "---\nname: repo-skill\ndescription: Repo skill\n---\nRepo");
        Files.writeString(nested.resolve(".agents").resolve("skills").resolve("local-skill").resolve("SKILL.md"),
                "---\nname: local-skill\ndescription: Local skill\n---\nLocal");

        SkillLoader.LoadSkillsResult trusted = SkillLoader.loadSkills(
                new SkillLoader.LoadSkillsOptions(nested, agentDir, List.of(), true, true));
        SkillLoader.LoadSkillsResult untrusted = SkillLoader.loadSkills(
                new SkillLoader.LoadSkillsOptions(nested, agentDir, List.of(), true, false));

        assertThat(trusted.skills()).extracting(Skill::name).containsExactly("local-skill", "repo-skill");
        assertThat(trusted.skills()).extracting(skill -> skill.sourceInfo().scope())
                .containsExactly("project-agents", "project-agents");
        assertThat(untrusted.skills()).isEmpty();
    }

    @Test
    void loadsAndExpandsPromptTemplates() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path project = tempDir.resolve("project");
        Files.createDirectories(agentDir.resolve("prompts"));
        Files.writeString(agentDir.resolve("prompts").resolve("fix.md"),
                "---\ndescription: Fix issue\nargument-hint: FILE ISSUE\n---\nFix $1 with $2 and $@ / ${3:-none} / ${@:2:2}");

        List<PromptTemplate> templates = PromptTemplateLoader.loadPromptTemplates(
                new PromptTemplateLoader.LoadPromptTemplatesOptions(project, agentDir, List.of(), true));

        assertThat(templates).hasSize(1);
        assertThat(templates.getFirst().argumentHint()).isEqualTo("FILE ISSUE");
        assertThat(PromptTemplateLoader.parseCommandArgs("'a b' c")).containsExactly("a b", "c");
        assertThat(PromptTemplateLoader.expandPromptTemplate("/fix file.java bug extra", templates))
                .isEqualTo("Fix file.java with bug and file.java bug extra / extra / bug extra");
    }

    @Test
    void loadsContextFilesFromGlobalAndAncestorsInOrder() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path repo = tempDir.resolve("repo");
        Path nested = repo.resolve("a").resolve("b");
        Files.createDirectories(agentDir);
        Files.createDirectories(nested);
        Files.writeString(agentDir.resolve("AGENTS.md"), "global");
        Files.writeString(repo.resolve("AGENTS.md"), "repo");
        Files.writeString(repo.resolve("a").resolve("CLAUDE.md"), "a");
        Files.writeString(nested.resolve("AGENTS.md"), "nested");

        List<ProjectContextLoader.ContextFile> files = ProjectContextLoader.loadProjectContextFiles(nested, agentDir);

        assertThat(files).extracting(ProjectContextLoader.ContextFile::content)
                .containsExactly("global", "repo", "a", "nested");
    }

    @Test
    void resolvesTrustedProjectSystemPromptBeforeGlobal() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path project = tempDir.resolve("project");
        Files.createDirectories(agentDir);
        Files.createDirectories(project.resolve(".pi"));
        Files.writeString(agentDir.resolve("SYSTEM.md"), "global system");
        Files.writeString(agentDir.resolve("APPEND_SYSTEM.md"), "global append");
        Files.writeString(project.resolve(".pi").resolve("SYSTEM.md"), "project system");
        Files.writeString(project.resolve(".pi").resolve("APPEND_SYSTEM.md"), "project append");

        ProjectContextLoader.PromptSources trusted = ProjectContextLoader.resolvePromptSources(project, agentDir, true, null, null);
        ProjectContextLoader.PromptSources untrusted = ProjectContextLoader.resolvePromptSources(project, agentDir, false, null, null);

        assertThat(trusted.systemPrompt()).isEqualTo("project system");
        assertThat(trusted.appendSystemPrompt()).containsExactly("project append");
        assertThat(untrusted.systemPrompt()).isEqualTo("global system");
        assertThat(untrusted.appendSystemPrompt()).containsExactly("global append");
    }

    @Test
    void resourceLoaderAggregatesSkillsPromptsContextAndPromptSources() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Path project = tempDir.resolve("project");
        Files.createDirectories(agentDir.resolve("skills").resolve("demo"));
        Files.createDirectories(agentDir.resolve("prompts"));
        Files.createDirectories(project);
        Files.writeString(agentDir.resolve("skills").resolve("demo").resolve("SKILL.md"),
                "---\nname: demo\ndescription: Demo skill\n---\nDemo");
        Files.writeString(agentDir.resolve("prompts").resolve("do.md"), "Do $1");
        Files.writeString(project.resolve("AGENTS.md"), "ctx");
        Files.writeString(agentDir.resolve("SYSTEM.md"), "sys");

        ResourceLoader loader = new ResourceLoader(project, agentDir, true, List.of(), List.of(), true,
                false, null, null);
        loader.reload();

        assertThat(loader.skills().skills()).extracting(Skill::name).containsExactly("demo");
        assertThat(loader.prompts()).extracting(PromptTemplate::name).containsExactly("do");
        assertThat(loader.contextFiles()).extracting(ProjectContextLoader.ContextFile::content).containsExactly("ctx");
        assertThat(loader.systemPrompt()).isEqualTo("sys");
    }
}
