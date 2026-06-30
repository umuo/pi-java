package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;
import works.earendil.pi.codingagent.session.SessionManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
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

    @Test
    void parsesExecutionOptions() {
        TeamworkPreview.ExecutionOptions options = TeamworkPreview.parseOptions(
                "compact run implement checkout flow");

        assertThat(options.execute()).isTrue();
        assertThat(options.compact()).isTrue();
        assertThat(options.objective()).isEqualTo("implement checkout flow");
        assertThat(options.rpcTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(TeamworkPreview.shouldExecute("run fix bug")).isTrue();
        assertThat(TeamworkPreview.shouldExecute("compact")).isFalse();
    }

    @Test
    void executionUsesInjectedExecutorAndRendersResults() throws Exception {
        Path cwd = tempDir.resolve("project_run");
        Path agentDir = tempDir.resolve("agent_run");
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
        List<TeamworkPreview.ExecutionRequest> requests = new ArrayList<>();

        TeamworkPreview.ExecutionReport report = TeamworkPreview.executeFromServices(
                sessionResult.session(), services, "compact run ship settings UI", request -> {
                    requests.add(request);
                    return new TeamworkPreview.ExecutionResult(List.of(
                            new TeamworkPreview.ExecutionRoleResult("implementer", "inst-1",
                                    "{\"result\":\"ok\"}", 2, null, true),
                            new TeamworkPreview.ExecutionRoleResult("reviewer", "inst-2",
                                    null, 1, "found issue", true)
                    ));
                });

        assertThat(requests).hasSize(1);
        assertThat(requests.getFirst().cwd()).isEqualTo(cwd.toAbsolutePath().normalize());
        assertThat(requests.getFirst().objective()).isEqualTo("ship settings UI");
        assertThat(requests.getFirst().roles()).extracting(TeamworkPreview.Role::id)
                .containsExactly("implementer", "reviewer");
        assertThat(report.error()).isNull();
        assertThat(report.render())
                .contains("Teamwork execution")
                .contains("objective: ship settings UI")
                .contains("1. implementer - ok")
                .contains("events: 2")
                .contains("response: {\"result\":\"ok\"}")
                .contains("2. reviewer - error")
                .contains("error: found issue");
    }

    @Test
    void executionRequiresObjective() throws Exception {
        Path cwd = tempDir.resolve("project_missing");
        Path agentDir = tempDir.resolve("agent_missing");
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

        TeamworkPreview.ExecutionReport report = TeamworkPreview.executeFromServices(
                sessionResult.session(), services, "run", request -> {
                    throw new AssertionError("executor should not be called without objective");
                });

        assertThat(report.error()).isEqualTo("objective is required");
        assertThat(report.render()).contains("usage: /teamwork-preview run <objective>");
    }
}
