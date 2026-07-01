package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import works.earendil.pi.codingagent.resources.Skill;
import works.earendil.pi.codingagent.resources.SourceInfo;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandsTest {
    @Test
    void exposesBuiltinCommandsInTypeScriptOrder() {
        assertThat(SlashCommands.BUILTIN_SLASH_COMMANDS)
                .extracting(SlashCommands.BuiltinSlashCommand::name)
                .containsExactly("settings", "model", "models", "scoped-models", "export", "import", "share", "copy",
                        "name", "session", "grill-me", "skill-diagnostics", "teamwork-preview",
                        "orchestrator-status", "changelog", "hotkeys", "fork", "clone", "tree", "trust", "login",
                        "logout", "new", "compact", "resume", "reload", "quit");
        assertThat(SlashCommands.findBuiltin("/compact")).get()
                .extracting(SlashCommands.BuiltinSlashCommand::description)
                .isEqualTo("Manually compact the session context");
        assertThat(SlashCommands.isBuiltin("missing")).isFalse();
    }

    @Test
    void parsesInvocationNameAndArguments() {
        assertThat(SlashCommands.invocationName(" /model openai/gpt-5 ")).isEqualTo("model");
        assertThat(SlashCommands.invocationArguments(" /model openai/gpt-5 ")).isEqualTo("openai/gpt-5");
        assertThat(SlashCommands.invocationName("/reload")).isEqualTo("reload");
        assertThat(SlashCommands.invocationArguments("/reload")).isEmpty();
    }

    @Test
    void mergesExternalCommandGroupsWithoutMutatingSources() {
        SourceInfo source = SourceInfo.synthetic(Path.of("cmd.md"), "test", Path.of("."));
        SlashCommands.SlashCommandInfo extension = new SlashCommands.SlashCommandInfo("x", "Extension",
                SlashCommands.SlashCommandSource.EXTENSION, source);
        SlashCommands.SlashCommandInfo prompt = new SlashCommands.SlashCommandInfo("p", "Prompt",
                SlashCommands.SlashCommandSource.PROMPT, source);
        SlashCommands.SlashCommandInfo skill = new SlashCommands.SlashCommandInfo("s", "Skill",
                SlashCommands.SlashCommandSource.SKILL, source);

        List<SlashCommands.SlashCommandInfo> merged = SlashCommands.mergeExternalCommands(
                List.of(extension), List.of(prompt), List.of(skill));

        assertThat(merged).containsExactly(extension, prompt, skill);
        assertThat(SlashCommands.SlashCommandSource.EXTENSION.wireName()).isEqualTo("extension");
    }

    @Test
    void exposesLoadedSkillsAsSlashCommands() {
        SourceInfo source = SourceInfo.local(Path.of("demo/SKILL.md"), "user", Path.of("demo"));
        Skill skill = new Skill("demo", "Demo skill", Path.of("demo/SKILL.md"), Path.of("demo"),
                source, false);

        List<SlashCommands.SlashCommandInfo> commands = SlashCommands.skillCommands(List.of(skill));

        assertThat(commands).singleElement().satisfies(command -> {
            assertThat(command.name()).isEqualTo("skill:demo");
            assertThat(command.description()).isEqualTo("Demo skill");
            assertThat(command.source()).isEqualTo(SlashCommands.SlashCommandSource.SKILL);
            assertThat(command.sourceInfo()).isEqualTo(source);
        });
    }

    @Test
    void marksModelDisabledSkillsAsManualOnlyCommands() {
        SourceInfo source = SourceInfo.local(Path.of("manual/SKILL.md"), "user", Path.of("manual"));
        Skill skill = new Skill("manual", "Manual skill", Path.of("manual/SKILL.md"), Path.of("manual"),
                source, true);

        List<SlashCommands.SlashCommandInfo> commands = SlashCommands.skillCommands(List.of(skill));

        assertThat(commands).singleElement()
                .extracting(SlashCommands.SlashCommandInfo::description)
                .isEqualTo("Manual skill (manual only)");
    }
}
