package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
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
                        "name", "session", "changelog", "hotkeys", "fork", "clone", "tree", "trust",
                        "login", "logout", "new", "compact", "resume", "reload", "quit");
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
}
