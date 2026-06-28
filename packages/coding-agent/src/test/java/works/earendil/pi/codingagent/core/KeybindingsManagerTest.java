package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class KeybindingsManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void rewritesOldKeyNamesToNamespacedIds() {
        KeybindingsManager.MigrationResult migrated = KeybindingsManager.migrateKeybindingsConfig(new java.util.LinkedHashMap<>(
                Map.of("cursorUp", List.of("up", "ctrl+p"), "expandTools", "ctrl+x")));

        assertThat(migrated.migrated()).isTrue();
        assertThat(migrated.config()).containsEntry("tui.editor.cursorUp", List.of("up", "ctrl+p"));
        assertThat(migrated.config()).containsEntry("app.tools.expand", "ctrl+x");
    }

    @Test
    void keepsNamespacedValueWhenOldAndNewNamesBothExist() {
        Map<String, Object> raw = new java.util.LinkedHashMap<>();
        raw.put("expandTools", "ctrl+x");
        raw.put("app.tools.expand", "ctrl+y");

        KeybindingsManager.MigrationResult migrated = KeybindingsManager.migrateKeybindingsConfig(raw);

        assertThat(migrated.migrated()).isTrue();
        assertThat(migrated.config()).containsExactly(Map.entry("app.tools.expand", "ctrl+y"));
    }

    @Test
    void loadsOldKeyNamesInMemoryBeforeFileIsRewritten() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(agentDir);
        Files.writeString(agentDir.resolve("keybindings.json"), """
                {
                  "selectConfirm": "enter",
                  "interrupt": "ctrl+x",
                  "ignored": 123
                }
                """);

        KeybindingsManager keybindings = KeybindingsManager.create(agentDir);

        assertThat(keybindings.getUserBindings()).containsExactly(
                Map.entry("tui.select.confirm", "enter"),
                Map.entry("app.interrupt", "ctrl+x"));
        assertThat(keybindings.getEffectiveConfig()).containsEntry("tui.select.confirm", "enter");
        assertThat(keybindings.getEffectiveConfig()).containsEntry("app.interrupt", "ctrl+x");
    }

    @Test
    void detectsUserBindingConflictsAndReloadsFile() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(agentDir);
        Path config = agentDir.resolve("keybindings.json");
        Files.writeString(config, """
                {
                  "app.clear": "ctrl+x",
                  "app.interrupt": "ctrl+x"
                }
                """);
        KeybindingsManager keybindings = KeybindingsManager.create(agentDir);

        assertThat(keybindings.getConflicts())
                .containsExactly(new KeybindingsManager.KeybindingConflict("ctrl+x", List.of("app.interrupt", "app.clear")));

        Files.writeString(config, """
                {
                  "app.clear": "ctrl+y"
                }
                """);
        keybindings.reload();

        assertThat(keybindings.getConflicts()).isEmpty();
        assertThat(keybindings.getKeys("app.clear")).containsExactly("ctrl+y");
    }

    @Test
    void matchesCommonTerminalInputSequences() {
        KeybindingsManager keybindings = new KeybindingsManager(Map.of(
                "app.clear", "ctrl+c",
                "app.interrupt", "escape",
                "app.message.dequeue", "alt+q"
        ));

        assertThat(keybindings.matches("\u0003", "app.clear")).isTrue();
        assertThat(keybindings.matches("\u001B", "app.interrupt")).isTrue();
        assertThat(keybindings.matches("\u001Bq", "app.message.dequeue")).isTrue();
        assertThat(keybindings.matches("q", "app.message.dequeue")).isFalse();
    }
}
