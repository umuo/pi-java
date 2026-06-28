package works.earendil.pi.codingagent.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.codingagent.core.HttpDispatcher;
import works.earendil.pi.common.json.JsonCodec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingsManagerTest {
    @TempDir
    Path tempDir;

    @Test
    void mergesGlobalAndTrustedProjectSettingsWithNestedObjects() throws Exception {
        Path cwd = project();
        Path agentDir = agent();
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "theme": "global",
                  "compaction": {"enabled": true, "reserveTokens": 100},
                  "extensions": ["/global.ts"],
                  "httpIdleTimeoutMs": 300000
                }
                """);
        Files.writeString(cwd.resolve(".pi/settings.json"), """
                {
                  "theme": "project",
                  "compaction": {"keepRecentTokens": 12},
                  "httpIdleTimeoutMs": 0
                }
                """);

        SettingsManager manager = new SettingsManager(cwd, agentDir, true);

        assertThat(manager.getTheme()).isEqualTo("project");
        assertThat(manager.getCompactionEnabled()).isTrue();
        assertThat(manager.getCompactionReserveTokens()).isEqualTo(100);
        assertThat(manager.getCompactionKeepRecentTokens()).isEqualTo(12);
        assertThat(manager.getExtensionPaths()).containsExactly("/global.ts");
        assertThat(manager.getHttpIdleTimeoutMs()).isZero();
    }

    @Test
    void skipsAndReloadsProjectSettingsBasedOnTrust() throws Exception {
        Path cwd = project();
        Path agentDir = agent();
        Files.writeString(agentDir.resolve("settings.json"), "{\"theme\":\"global\"}");
        Files.writeString(cwd.resolve(".pi/settings.json"), "{\"theme\":\"project\"}");
        SettingsManager manager = new SettingsManager(cwd, agentDir, false);

        assertThat(manager.isProjectTrusted()).isFalse();
        assertThat(manager.getTheme()).isEqualTo("global");
        assertThat(manager.getProjectSettings().size()).isZero();

        manager.setProjectTrusted(true);

        assertThat(manager.getTheme()).isEqualTo("project");
    }

    @Test
    void recordsLoadErrorsAndKeepsDefaults() throws Exception {
        Path cwd = project();
        Path agentDir = agent();
        Files.writeString(agentDir.resolve("settings.json"), "{ invalid");
        Files.writeString(cwd.resolve(".pi/settings.json"), "{ invalid project");

        SettingsManager manager = new SettingsManager(cwd, agentDir, true);

        assertThat(manager.drainErrors()).extracting(SettingsManager.SettingsError::scope)
                .containsExactlyInAnyOrder(SettingsManager.Scope.GLOBAL, SettingsManager.Scope.PROJECT);
        assertThat(manager.drainErrors()).isEmpty();
        assertThat(manager.getHttpIdleTimeoutMs()).isEqualTo(HttpDispatcher.DEFAULT_HTTP_IDLE_TIMEOUT_MS);
    }

    @Test
    void migratesLegacySettings() {
        SettingsManager manager = SettingsManager.inMemory(Map.of(
                "queueMode", "all",
                "websockets", true,
                "skills", Map.of("enableSkillCommands", false, "customDirectories", List.of("skills/a")),
                "retry", Map.of("maxDelayMs", 1234)
        ));

        assertThat(manager.getSteeringMode()).isEqualTo("all");
        assertThat(manager.getTransport()).isEqualTo("websocket");
        assertThat(manager.getSkillPaths()).containsExactly("skills/a");
        assertThat(manager.getEnableSkillCommands()).isFalse();
        assertThat(manager.load().path("retry").path("provider").path("maxRetryDelayMs").asInt()).isEqualTo(1234);
    }

    @Test
    void preservesExternallyAddedSettingsWhenWritingPatch() throws Exception {
        Path cwd = project();
        Path agentDir = agent();
        Path settings = agentDir.resolve("settings.json");
        Files.writeString(settings, "{\"theme\":\"dark\"}");
        SettingsManager manager = new SettingsManager(cwd, agentDir, true);

        Files.writeString(settings, "{\"theme\":\"dark\",\"enabledModels\":[\"a\",\"b\"]}");
        manager.setDefaultThinkingLevel("high");

        JsonNode saved = JsonCodec.parse(Files.readString(settings));
        assertThat(saved.path("theme").asText()).isEqualTo("dark");
        assertThat(saved.path("enabledModels")).extracting(JsonNode::asText).containsExactly("a", "b");
        assertThat(saved.path("defaultThinkingLevel").asText()).isEqualTo("high");
    }

    @Test
    void exposesTimeoutEditorSessionAndResourceGetters() throws Exception {
        Path cwd = project();
        Path agentDir = agent();
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  "theme": "light/dark",
                  "externalEditor": "code --wait",
                  "shellCommandPrefix": "source ~/.zshrc",
                  "sessionDir": "~/pi-sessions",
                  "httpIdleTimeoutMs": "60000",
                  "websocketConnectTimeoutMs": "disabled",
                  "packages": ["npm:pkg", {"source":"git:repo"}],
                  "extensions": ["ext.ts"],
                  "prompts": ["prompt.md"],
                  "themes": ["theme.json"],
                  "enabledModels": ["openai/*"],
                  "terminal": {"imageWidthCells": 0},
                  "images": {"blockImages": true}
                }
                """);
        SettingsManager manager = new SettingsManager(cwd, agentDir, true);

        assertThat(manager.getTheme()).isNull();
        assertThat(manager.getThemeSetting()).isEqualTo("light/dark");
        assertThat(manager.getExternalEditorCommand()).isEqualTo("code --wait");
        assertThat(manager.getShellCommandPrefix()).isEqualTo("source ~/.zshrc");
        assertThat(manager.getSessionDir()).endsWith("pi-sessions");
        assertThat(manager.getHttpIdleTimeoutMs()).isEqualTo(60_000);
        assertThat(manager.getWebSocketConnectTimeoutMs()).isZero();
        assertThat(manager.getPackages()).hasSize(2);
        assertThat(manager.getExtensionPaths()).containsExactly("ext.ts");
        assertThat(manager.getPromptTemplatePaths()).containsExactly("prompt.md");
        assertThat(manager.getThemePaths()).containsExactly("theme.json");
        assertThat(manager.getEnabledModels()).containsExactly("openai/*");
        assertThat(manager.getImageWidthCells()).isEqualTo(1);
        assertThat(manager.getBlockImages()).isTrue();
    }

    @Test
    void rejectsInvalidTimeoutAndUntrustedProjectWrites() throws Exception {
        Path cwd = project();
        Path agentDir = agent();
        Files.writeString(agentDir.resolve("settings.json"), "{\"httpIdleTimeoutMs\":-1}");
        SettingsManager manager = new SettingsManager(cwd, agentDir, false);

        assertThatThrownBy(manager::getHttpIdleTimeoutMs)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid httpIdleTimeoutMs");
        assertThatThrownBy(() -> manager.setProjectPackages(List.of("npm:new")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Project is not trusted");
    }

    @Test
    void updateKeepsJsoncAndDeepMerges() throws Exception {
        Path cwd = project();
        Path agentDir = agent();
        Files.writeString(agentDir.resolve("settings.json"), """
                {
                  // comment
                  "terminal": {"showImages": false,},
                }
                """);
        SettingsManager manager = new SettingsManager(cwd, agentDir, true);
        ObjectNode patch = JsonCodec.mapper().createObjectNode();
        patch.putObject("terminal").put("imageWidthCells", 42);

        manager.update(SettingsManager.Scope.GLOBAL, patch);

        assertThat(manager.getShowImages()).isFalse();
        assertThat(manager.getImageWidthCells()).isEqualTo(42);
    }

    private Path project() throws Exception {
        Path cwd = tempDir.resolve("project");
        Files.createDirectories(cwd.resolve(".pi"));
        return cwd;
    }

    private Path agent() throws Exception {
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(agentDir);
        return agentDir;
    }
}
