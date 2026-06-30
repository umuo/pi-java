package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamworkPreviewTest {
    @TempDir
    Path tempDir;

    @Test
    void rendersDefaultSubAgentPlanFromRuntimeServices() throws Exception {
        Path cwd = tempDir.resolve("project");
        Path agentDir = tempDir.resolve("agent");
        Files.createDirectories(cwd);
        AgentSessionServices services = AgentSessionServices.create(new AgentSessionServices.CreateOptions(
                cwd, agentDir, AuthStorage.inMemory(), null, null, null, null, true
        ));
        Model model = services.modelRegistry().getAll().getFirst();
        AgentSessionServices.CreateSessionResult sessionResult = AgentSessionServices.createAgentSessionFromServices(
                new AgentSessionServices.CreateSessionOptions(
                        services, SessionManager.inMemory(cwd), model, ThinkingLevel.OFF,
                        List.of(), List.of(), List.of(), null, List.of(),
                        (m, ctx, opts) -> null
                )
        );

        String rendered = TeamworkPreview.fromServices(sessionResult.session(), services, "").render();

        assertThat(rendered)
                .contains("Teamwork preview")
                .contains("cwd: " + cwd.toAbsolutePath().normalize())
                .contains("main model: " + model.provider() + "/" + model.modelId())
                .contains("coordination: steering=one-at-a-time, followUp=one-at-a-time")
                .contains("researcher - map the codebase")
                .contains("implementer - make the scoped code change")
                .contains("reviewer - check behavior");
    }

    @Test
    void compactPreviewUsesTwoRoles() {
        TeamworkPreview.Preview preview = new TeamworkPreview.Preview(
                Path.of("/repo"), "openai/gpt", 2, 0, "parallel", "parallel",
                List.of(
                        new TeamworkPreview.Role("implementer", "change", List.of("edit"), "patch"),
                        new TeamworkPreview.Role("reviewer", "review", List.of("read"), "findings")
                )
        );

        assertThat(preview.render())
                .contains("providers: 2 available")
                .contains("1. implementer - change")
                .contains("2. reviewer - review");
    }
}
