package works.earendil.pi.server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.server.config.ServerConfig;
import works.earendil.pi.server.model.InstanceRecord;
import works.earendil.pi.server.model.InstanceStatus;
import works.earendil.pi.server.storage.ServerStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ServerSupervisorTest {

    @TempDir
    Path tempDir;

    private ServerStorage storage;
    private ServerSupervisor supervisor;
    private FakeLauncher launcher;

    @BeforeEach
    void setUp() {
        ServerConfig config = new ServerConfig(Map.of("PI_SERVER_DIR", tempDir.toString()));
        storage = new ServerStorage(config);
        launcher = new FakeLauncher();
        supervisor = new ServerSupervisor(storage, launcher);
    }

    @Test
    void spawnAndStopInstance() throws IOException {
        InstanceRecord spawned = supervisor.spawnInstance("/workspace", "test-agent");
        assertThat(spawned.status()).isEqualTo(InstanceStatus.ONLINE);
        assertThat(spawned.cwd()).isEqualTo("/workspace");
        assertThat(spawned.label()).isEqualTo("test-agent");
        assertThat(launcher.requests).hasSize(1);
        assertThat(launcher.requests.getFirst().cwd()).isEqualTo("/workspace");

        assertThat(supervisor.listLiveInstances()).hasSize(1);
        assertThat(supervisor.listInstances()).hasSize(1);

        Optional<InstanceRecord> stopped = supervisor.stopInstance(spawned.id());
        assertThat(stopped).isPresent();
        assertThat(stopped.get().status()).isEqualTo(InstanceStatus.STOPPED);
        assertThat(launcher.processes.getFirst().sentLines).contains("{\"id\":\"server-stop\",\"method\":\"exit\"}");
        assertThat(launcher.processes.getFirst().alive).isFalse();

        assertThat(supervisor.listLiveInstances()).isEmpty();
        assertThat(storage.loadInstances()).isEmpty();
    }

    @Test
    void sendsRpcMessageThroughLiveInstanceStdio() throws IOException {
        InstanceRecord spawned = supervisor.spawnInstance("/workspace", "rpc-agent");
        FakeProcess process = launcher.processes.getFirst();
        process.responses.add("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"status\":\"ok\"}}");

        Optional<String> response = supervisor.sendRpc(spawned.id(),
                "{\"id\":1,\"method\":\"list_models\"}", Duration.ofMillis(10));

        assertThat(response).contains("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"status\":\"ok\"}}");
        assertThat(process.sentLines).contains("{\"id\":1,\"method\":\"list_models\"}");
        assertThat(supervisor.getInstance(spawned.id())).isPresent();
        assertThat(supervisor.getInstance(spawned.id()).get().status()).isEqualTo(InstanceStatus.ONLINE);
    }

    @Test
    void sendRpcExchangeSkipsEventsUntilMatchingResponse() throws IOException {
        InstanceRecord spawned = supervisor.spawnInstance("/workspace", "rpc-agent");
        FakeProcess process = launcher.processes.getFirst();
        process.responses.add("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\",\"text\":\"hi\"}}");
        process.responses.add("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"status\":\"ok\"}}");

        Optional<ServerSupervisor.RpcExchange> exchange = supervisor.sendRpcExchange(spawned.id(),
                "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"prompt\",\"params\":{\"text\":\"hello\"}}",
                Duration.ofMillis(10));

        assertThat(exchange).isPresent();
        assertThat(exchange.get().response()).isEqualTo("{\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"status\":\"ok\"}}");
        assertThat(exchange.get().events()).containsExactly(
                "{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\",\"text\":\"hi\"}}");
    }

    @Test
    void rpcEventSubscriptionsReceiveIntermediateEvents() throws IOException {
        InstanceRecord first = supervisor.spawnInstance("/workspace", "rpc-agent");
        InstanceRecord second = supervisor.spawnInstance("/workspace", "other-agent");
        FakeProcess process = launcher.processes.getFirst();
        process.responses.add("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\",\"text\":\"one\"}}");
        process.responses.add("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"content_delta\",\"text\":\"two\"}}");
        process.responses.add("{\"jsonrpc\":\"2.0\",\"id\":11,\"result\":{\"status\":\"ok\"}}");
        List<ServerSupervisor.RpcEvent> allEvents = new ArrayList<>();
        List<ServerSupervisor.RpcEvent> firstEvents = new ArrayList<>();
        List<ServerSupervisor.RpcEvent> secondEvents = new ArrayList<>();
        ServerSupervisor.RpcEventSubscription global = supervisor.subscribeRpcEvents(allEvents::add);
        ServerSupervisor.RpcEventSubscription matching = supervisor.subscribeRpcEvents(first.id(), firstEvents::add);
        supervisor.subscribeRpcEvents(second.id(), secondEvents::add);
        supervisor.subscribeRpcEvents(event -> {
            throw new IllegalStateException("listener failure");
        });

        Optional<ServerSupervisor.RpcExchange> exchange = supervisor.sendRpcExchange(first.id(),
                "{\"jsonrpc\":\"2.0\",\"id\":11,\"method\":\"prompt\"}", Duration.ofMillis(10));

        assertThat(exchange).isPresent();
        assertThat(exchange.get().events()).hasSize(2);
        assertThat(allEvents).hasSize(2);
        assertThat(firstEvents).hasSize(2);
        assertThat(secondEvents).isEmpty();
        assertThat(allEvents).extracting(ServerSupervisor.RpcEvent::sequence)
                .containsExactly(1L, 2L);
        assertThat(allEvents).allSatisfy(event -> {
            assertThat(event.instanceId()).isEqualTo(first.id());
            assertThat(event.requestId()).isEqualTo("11");
            assertThat(event.rawJson()).contains("\"method\":\"event\"");
            assertThat(event.receivedAt()).isNotBlank();
        });

        global.close();
        matching.close();
        process.responses.add("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"done\"}}");
        process.responses.add("{\"jsonrpc\":\"2.0\",\"id\":12,\"result\":{\"status\":\"ok\"}}");
        supervisor.sendRpcExchange(first.id(), "{\"jsonrpc\":\"2.0\",\"id\":12,\"method\":\"prompt\"}",
                Duration.ofMillis(10));

        assertThat(allEvents).hasSize(2);
        assertThat(firstEvents).hasSize(2);
        assertThat(global.isActive()).isFalse();
        assertThat(matching.isActive()).isFalse();
    }

    @Test
    void retainsRecentRpcEventsWithoutActiveSubscriptions() throws IOException {
        InstanceRecord first = supervisor.spawnInstance("/workspace", "rpc-agent");
        InstanceRecord second = supervisor.spawnInstance("/workspace", "other-agent");
        launcher.processes.get(0).responses.add("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"first\"}}");
        launcher.processes.get(0).responses.add("{\"jsonrpc\":\"2.0\",\"id\":21,\"result\":{\"status\":\"ok\"}}");
        launcher.processes.get(1).responses.add("{\"jsonrpc\":\"2.0\",\"method\":\"event\",\"params\":{\"type\":\"second\"}}");
        launcher.processes.get(1).responses.add("{\"jsonrpc\":\"2.0\",\"id\":22,\"result\":{\"status\":\"ok\"}}");

        supervisor.sendRpcExchange(first.id(), "{\"jsonrpc\":\"2.0\",\"id\":21,\"method\":\"prompt\"}",
                Duration.ofMillis(10));
        supervisor.sendRpcExchange(second.id(), "{\"jsonrpc\":\"2.0\",\"id\":22,\"method\":\"prompt\"}",
                Duration.ofMillis(10));

        assertThat(supervisor.recentRpcEvents(null, 10))
                .extracting(ServerSupervisor.RpcEvent::requestId)
                .containsExactly("21", "22");
        assertThat(supervisor.recentRpcEvents(first.id(), 10))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.instanceId()).isEqualTo(first.id());
                    assertThat(event.rawJson()).contains("\"first\"");
                });
        assertThat(supervisor.recentRpcEvents(null, 1))
                .singleElement()
                .extracting(ServerSupervisor.RpcEvent::requestId)
                .isEqualTo("22");
    }

    @Test
    void sendRpcReturnsEmptyForMissingOrStoppedProcess() throws IOException {
        assertThat(supervisor.sendRpc("missing", "{\"id\":1}", Duration.ofMillis(1))).isEmpty();

        InstanceRecord spawned = supervisor.spawnInstance("/workspace", "rpc-agent");
        launcher.processes.getFirst().alive = false;

        assertThat(supervisor.sendRpc(spawned.id(), "{\"id\":1}", Duration.ofMillis(1))).isEmpty();
    }

    @Test
    void heartbeatRefreshesLiveInstancesAndMarksDeadProcessesError() throws IOException {
        InstanceRecord alive = supervisor.spawnInstance("/workspace", "alive-agent");
        InstanceRecord dead = supervisor.spawnInstance("/workspace", "dead-agent");
        launcher.processes.get(1).alive = false;

        List<InstanceRecord> heartbeat = supervisor.heartbeat();

        assertThat(heartbeat).hasSize(2);
        assertThat(supervisor.getInstance(alive.id())).get()
                .extracting(InstanceRecord::status)
                .isEqualTo(InstanceStatus.ONLINE);
        assertThat(supervisor.getInstance(dead.id())).get()
                .extracting(InstanceRecord::status)
                .isEqualTo(InstanceStatus.ERROR);
        assertThat(supervisor.sendRpc(dead.id(), "{\"id\":1}", Duration.ofMillis(1))).isEmpty();
        assertThat(storage.getInstance(dead.id())).get()
                .extracting(InstanceRecord::status)
                .isEqualTo(InstanceStatus.ERROR);
    }

    @Test
    void restartStaleInstancesReusesInstanceIdAndReplacesProcess() throws IOException {
        InstanceRecord spawned = supervisor.spawnInstance("/workspace", "stale-agent");
        FakeProcess original = launcher.processes.getFirst();

        assertThat(supervisor.restartStaleInstances(Duration.ofDays(1))).isEmpty();
        List<ServerSupervisor.RestartResult> restarts = supervisor.restartStaleInstances(Duration.ZERO);

        assertThat(restarts).hasSize(1);
        assertThat(restarts.getFirst().instanceId()).isEqualTo(spawned.id());
        assertThat(restarts.getFirst().restarted()).isTrue();
        assertThat(restarts.getFirst().record().status()).isEqualTo(InstanceStatus.ONLINE);
        assertThat(launcher.requests).hasSize(2);
        assertThat(launcher.requests.get(1).instanceId()).isEqualTo(spawned.id());
        assertThat(launcher.requests.get(1).cwd()).isEqualTo("/workspace");
        assertThat(original.sentLines).contains("{\"id\":\"server-restart\",\"method\":\"exit\"}");
        assertThat(original.alive).isFalse();
        assertThat(launcher.processes.get(1).alive).isTrue();
        assertThat(supervisor.getInstance(spawned.id())).get()
                .extracting(InstanceRecord::status)
                .isEqualTo(InstanceStatus.ONLINE);
    }

    @Test
    void restartPolicyAppliesBackoffAndMaxAttempts() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-30T00:00:00Z"));
        FakeLauncher policyLauncher = new FakeLauncher();
        ServerSupervisor policySupervisor = new ServerSupervisor(storage, policyLauncher, clock);
        InstanceRecord spawned = policySupervisor.spawnInstance("/workspace", "stale-agent");
        policyLauncher.failuresRemaining = 1;
        ServerSupervisor.RestartPolicy policy = new ServerSupervisor.RestartPolicy(
                2, Duration.ofSeconds(10), Duration.ofMinutes(1));

        List<ServerSupervisor.RestartResult> first = policySupervisor.restartStaleInstances(Duration.ZERO, policy);
        List<ServerSupervisor.RestartResult> backoff = policySupervisor.restartStaleInstances(Duration.ZERO, policy);
        clock.advance(Duration.ofSeconds(10));
        policyLauncher.failuresRemaining = 1;
        List<ServerSupervisor.RestartResult> second = policySupervisor.restartStaleInstances(Duration.ZERO, policy);
        clock.advance(Duration.ofSeconds(20));
        List<ServerSupervisor.RestartResult> exhausted = policySupervisor.restartStaleInstances(Duration.ZERO, policy);

        assertThat(first).hasSize(1);
        assertThat(first.getFirst().restarted()).isFalse();
        assertThat(first.getFirst().error()).contains("planned failure");
        assertThat(backoff).hasSize(1);
        assertThat(backoff.getFirst().error()).isEqualTo("restart backoff active");
        assertThat(second).hasSize(1);
        assertThat(second.getFirst().restarted()).isFalse();
        assertThat(exhausted).hasSize(1);
        assertThat(exhausted.getFirst().error()).isEqualTo("max restart attempts reached");
        assertThat(policyLauncher.requests).hasSize(3);
        assertThat(policySupervisor.getInstance(spawned.id())).get()
                .extracting(InstanceRecord::status)
                .isEqualTo(InstanceStatus.ERROR);
    }

    @Test
    void successfulRestartClearsRestartPolicyFailures() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-30T00:00:00Z"));
        FakeLauncher policyLauncher = new FakeLauncher();
        ServerSupervisor policySupervisor = new ServerSupervisor(storage, policyLauncher, clock);
        InstanceRecord spawned = policySupervisor.spawnInstance("/workspace", "stale-agent");
        ServerSupervisor.RestartPolicy policy = new ServerSupervisor.RestartPolicy(
                2, Duration.ofSeconds(10), Duration.ofMinutes(1));
        policyLauncher.failuresRemaining = 1;
        policySupervisor.restartStaleInstances(Duration.ZERO, policy);
        clock.advance(Duration.ofSeconds(10));

        List<ServerSupervisor.RestartResult> success = policySupervisor.restartStaleInstances(Duration.ZERO, policy);
        policyLauncher.failuresRemaining = 1;
        List<ServerSupervisor.RestartResult> nextFailure = policySupervisor.restartStaleInstances(Duration.ZERO, policy);

        assertThat(success.getFirst().restarted()).isTrue();
        assertThat(nextFailure.getFirst().restarted()).isFalse();
        assertThat(nextFailure.getFirst().reason()).contains("2026-06-30T00:00:20Z");
        assertThat(policySupervisor.getInstance(spawned.id())).get()
                .extracting(InstanceRecord::status)
                .isEqualTo(InstanceStatus.ERROR);
    }

    @Test
    void defaultSupervisorUsesRuntimeSettings() throws IOException {
        Files.writeString(storage.config().getRuntimeSettingsPath(), """
                {
                  "restart": {
                    "maxAttempts": 4,
                    "baseBackoffMs": 1000,
                    "maxBackoffMs": 9000
                  },
                  "logRotation": {
                    "maxBytes": 8192,
                    "maxBackups": 5
                  }
                }
                """);

        ServerSupervisor configured = new ServerSupervisor(storage);

        assertThat(configured.restartPolicy().maxAttempts()).isEqualTo(4);
        assertThat(configured.restartPolicy().baseBackoff()).isEqualTo(Duration.ofSeconds(1));
        assertThat(configured.restartPolicy().maxBackoff()).isEqualTo(Duration.ofSeconds(9));
        assertThat(configured.launcher()).isInstanceOf(RpcAgentProcessLauncher.class);
        RpcAgentProcessLauncher rpcLauncher = (RpcAgentProcessLauncher) configured.launcher();
        assertThat(rpcLauncher.logRotationPolicy().maxBytes()).isEqualTo(8192);
        assertThat(rpcLauncher.logRotationPolicy().maxBackups()).isEqualTo(5);
    }

    @Test
    void recoverAfterRestart() throws IOException {
        InstanceRecord inst = new InstanceRecord("old-id", InstanceStatus.ONLINE, "/dir", "2026-06-28T10:00:00Z", null, "worker", null, null, null);
        storage.upsertInstance(inst);

        supervisor.recoverAfterRestart();

        List<InstanceRecord> instances = storage.loadInstances();
        assertThat(instances).hasSize(1);
        assertThat(instances.get(0).status()).isEqualTo(InstanceStatus.STOPPED);
    }

    private static final class FakeLauncher implements AgentProcessLauncher {
        private final List<StartRequest> requests = new ArrayList<>();
        private final List<FakeProcess> processes = new ArrayList<>();
        private int failuresRemaining;

        @Override
        public AgentProcess start(StartRequest request) throws IOException {
            requests.add(request);
            if (failuresRemaining > 0) {
                failuresRemaining--;
                throw new IOException("planned failure");
            }
            FakeProcess process = new FakeProcess();
            processes.add(process);
            return process;
        }
    }

    private static final class FakeProcess implements AgentProcess {
        private final List<String> sentLines = new ArrayList<>();
        private final Deque<String> responses = new ArrayDeque<>();
        private boolean alive = true;

        @Override
        public void sendLine(String line) {
            sentLines.add(line);
        }

        @Override
        public Optional<String> readLine(Duration timeout) {
            return Optional.ofNullable(responses.pollFirst());
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public void stop(Duration timeout) {
            alive = false;
        }
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
