package works.earendil.pi.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SubAgentTaskCoordinatorTest {
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");

    @TempDir
    Path tempDir;

    @Test
    void spawnsPromptsAndStopsSubAgentsForEachRole() throws Exception {
        FakeLauncher launcher = new FakeLauncher();
        OrchestratorStorage storage = new OrchestratorStorage(
                new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString())));
        OrchestratorSupervisor supervisor = new OrchestratorSupervisor(storage, launcher);
        SubAgentTaskCoordinator coordinator = new SubAgentTaskCoordinator(supervisor);

        SubAgentTaskCoordinator.TaskResult result = coordinator.execute(new SubAgentTaskCoordinator.TaskRequest(
                "/workspace",
                "Ship orchestrator task delegation",
                List.of(
                        new SubAgentTaskCoordinator.Role("researcher", "map relevant files"),
                        new SubAgentTaskCoordinator.Role("reviewer", "review the patch")
                ),
                Duration.ofMillis(50),
                true
        ));

        assertThat(result.results()).hasSize(2);
        assertThat(result.results()).extracting(SubAgentTaskCoordinator.SubAgentResult::roleId)
                .containsExactly("researcher", "reviewer");
        assertThat(result.results()).allSatisfy(subResult -> {
            assertThat(subResult.response()).contains("\"result\":{\"status\":\"ok\"}");
            assertThat(subResult.events()).hasSize(1);
            assertThat(subResult.error()).isNull();
            assertThat(subResult.stopped()).isTrue();
        });
        assertThat(launcher.requests).hasSize(2);
        assertThat(launcher.requests).extracting(AgentProcessLauncher.StartRequest::label)
                .containsExactlyInAnyOrder("researcher", "reviewer");
        assertThat(launcher.processes).allSatisfy(process -> {
            assertThat(process.sentLines).anySatisfy(line -> assertThat(line)
                    .contains("\"method\":\"prompt\"")
                    .contains("Ship orchestrator task delegation"));
            assertThat(process.sentLines).anySatisfy(line -> assertThat(line)
                    .contains("\"method\":\"exit\""));
            assertThat(process.alive).isFalse();
        });
        assertThat(storage.loadInstances()).isEmpty();
    }

    private static final class FakeLauncher implements AgentProcessLauncher {
        private final List<StartRequest> requests = new ArrayList<>();
        private final List<FakeProcess> processes = new ArrayList<>();

        @Override
        public AgentProcess start(StartRequest request) {
            requests.add(request);
            FakeProcess process = new FakeProcess();
            processes.add(process);
            return process;
        }
    }

    private static final class FakeProcess implements AgentProcess {
        private final List<String> sentLines = new ArrayList<>();
        private final Deque<String> responses = new ArrayDeque<>();
        private boolean alive = true;
        private boolean eventSent;

        @Override
        public void sendLine(String line) {
            sentLines.add(line);
        }

        @Override
        public Optional<String> readLine(Duration timeout) {
            if (!responses.isEmpty()) {
                return Optional.of(responses.removeFirst());
            }
            String id = requestId();
            if (!eventSent) {
                eventSent = true;
                return Optional.of("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\",\"text\":\"working\"}}");
            }
            return Optional.of("{\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":{\"status\":\"ok\"}}");
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void stop(Duration timeout) {
            alive = false;
        }

        private String requestId() {
            if (sentLines.isEmpty()) {
                return "0";
            }
            Matcher matcher = ID_PATTERN.matcher(sentLines.getLast());
            return matcher.find() ? matcher.group(1) : "0";
        }
    }
}
