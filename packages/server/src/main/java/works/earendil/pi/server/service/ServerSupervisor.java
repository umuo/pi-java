package works.earendil.pi.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import works.earendil.pi.server.config.ServerRuntimeSettings;
import works.earendil.pi.server.model.InstanceRecord;
import works.earendil.pi.server.model.InstanceStatus;
import works.earendil.pi.server.storage.ServerStorage;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

public final class ServerSupervisor {
    private static final Duration DEFAULT_RPC_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(2);
    private static final RestartPolicy DEFAULT_RESTART_POLICY = new RestartPolicy(3, Duration.ofSeconds(30), Duration.ofMinutes(5));
    private static final int RECENT_RPC_EVENT_HISTORY_LIMIT = 200;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ServerStorage storage;
    private final AgentProcessLauncher launcher;
    private final Clock clock;
    private final RestartPolicy defaultRestartPolicy;
    private final Map<String, InstanceRecord> liveInstances = new ConcurrentHashMap<>();
    private final Map<String, AgentProcess> liveProcesses = new ConcurrentHashMap<>();
    private final Map<String, Object> processLocks = new ConcurrentHashMap<>();
    private final Map<String, RestartTracker> restartTrackers = new ConcurrentHashMap<>();
    private final List<RpcEventSubscription> rpcEventSubscriptions = new CopyOnWriteArrayList<>();
    private final Deque<RpcEvent> recentRpcEvents = new ArrayDeque<>();
    private final Object recentRpcEventsLock = new Object();
    private final AtomicLong rpcEventSequences = new AtomicLong(1);

    public ServerSupervisor(ServerStorage storage) {
        this(storage, defaultLauncher(storage), defaultRestartPolicy(storage.config().getRuntimeSettings()),
                Clock.systemUTC());
    }

    public ServerSupervisor(ServerStorage storage, AgentProcessLauncher launcher) {
        this(storage, launcher, DEFAULT_RESTART_POLICY, Clock.systemUTC());
    }

    ServerSupervisor(ServerStorage storage, AgentProcessLauncher launcher, Clock clock) {
        this(storage, launcher, DEFAULT_RESTART_POLICY, clock);
    }

    ServerSupervisor(ServerStorage storage, AgentProcessLauncher launcher, RestartPolicy restartPolicy,
                           Clock clock) {
        this.storage = storage;
        this.launcher = launcher;
        this.defaultRestartPolicy = restartPolicy == null ? DEFAULT_RESTART_POLICY : restartPolicy;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public synchronized void recoverAfterRestart() throws IOException {
        String now = now().toString();
        List<InstanceRecord> instances = storage.loadInstances();
        List<InstanceRecord> recovered = new ArrayList<>();
        for (InstanceRecord inst : instances) {
            if (inst.status() == InstanceStatus.ONLINE || inst.status() == InstanceStatus.STARTING) {
                recovered.add(new InstanceRecord(inst.id(), InstanceStatus.STOPPED, inst.cwd(), inst.createdAt(), now, inst.label(), inst.sessionId(), inst.sessionFile(), inst.radiusPiId()));
            } else {
                recovered.add(inst.withLastSeenAt(now));
            }
        }
        storage.saveInstances(recovered);
        liveInstances.clear();
        liveProcesses.clear();
        processLocks.clear();
        restartTrackers.clear();
        synchronized (recentRpcEventsLock) {
            recentRpcEvents.clear();
        }
    }

    public List<InstanceRecord> listInstances() throws IOException {
        return storage.loadInstances();
    }

    public List<InstanceRecord> listLiveInstances() {
        return List.copyOf(liveInstances.values());
    }

    public Optional<InstanceRecord> getInstance(String instanceId) throws IOException {
        InstanceRecord live = liveInstances.get(instanceId);
        if (live != null) {
            return Optional.of(live);
        }
        return storage.getInstance(instanceId);
    }

    public synchronized List<InstanceRecord> heartbeat() throws IOException {
        String now = now().toString();
        List<InstanceRecord> updated = new ArrayList<>();
        for (Map.Entry<String, InstanceRecord> entry : List.copyOf(liveInstances.entrySet())) {
            String instanceId = entry.getKey();
            InstanceRecord record = entry.getValue();
            AgentProcess process = liveProcesses.get(instanceId);
            InstanceRecord next;
            if (process != null && process.isAlive()) {
                next = record.withLastSeenAt(now);
            } else {
                liveProcesses.remove(instanceId);
                processLocks.remove(instanceId);
                next = record.withStatus(InstanceStatus.ERROR).withLastSeenAt(now);
            }
            liveInstances.put(instanceId, next);
            storage.upsertInstance(next);
            updated.add(next);
        }
        return List.copyOf(updated);
    }

    public synchronized List<RestartResult> restartStaleInstances(Duration staleAfter) throws IOException {
        return restartStaleInstances(staleAfter, defaultRestartPolicy);
    }

    public synchronized List<RestartResult> restartStaleInstances(Duration staleAfter, RestartPolicy policy)
            throws IOException {
        RestartPolicy effectivePolicy = policy == null ? DEFAULT_RESTART_POLICY : policy;
        Instant now = now();
        List<RestartResult> restarted = new ArrayList<>();
        for (Map.Entry<String, InstanceRecord> entry : List.copyOf(liveInstances.entrySet())) {
            String instanceId = entry.getKey();
            InstanceRecord record = entry.getValue();
            RestartTracker tracker = restartTrackers.get(instanceId);
            if (!isStale(record, staleAfter, now) && !(record.status() == InstanceStatus.ERROR && tracker != null)) {
                continue;
            }
            if (tracker != null && tracker.attempts() >= effectivePolicy.maxAttempts()) {
                restarted.add(new RestartResult(instanceId, false,
                        "restart attempts exhausted after " + tracker.attempts() + " failures", record,
                        "max restart attempts reached"));
                continue;
            }
            if (tracker != null && tracker.nextAllowedAt().isAfter(now)) {
                restarted.add(new RestartResult(instanceId, false,
                        "restart backoff until " + tracker.nextAllowedAt(), record,
                        "restart backoff active"));
                continue;
            }
            restarted.add(restartInstance(record, "stale for " + staleAfter, effectivePolicy));
        }
        return List.copyOf(restarted);
    }

    public synchronized InstanceRecord spawnInstance(String cwd, String label) throws IOException {
        String now = now().toString();
        String id = UUID.randomUUID().toString();
        InstanceRecord record = new InstanceRecord(id, InstanceStatus.STARTING, cwd, now, now, label, null, null, null);
        liveInstances.put(id, record);
        storage.upsertInstance(record);
        AgentProcess process = null;

        try {
            process = launcher.start(new AgentProcessLauncher.StartRequest(id, cwd, label));
            liveProcesses.put(id, process);
            processLocks.put(id, new Object());
            InstanceRecord onlineRecord = record.withStatus(InstanceStatus.ONLINE).withLastSeenAt(now().toString());
            liveInstances.put(id, onlineRecord);
            storage.upsertInstance(onlineRecord);
            return onlineRecord;
        } catch (Exception e) {
            if (process != null) {
                try {
                    process.stop(DEFAULT_STOP_TIMEOUT);
                } catch (IOException ignored) {
                }
            }
            liveProcesses.remove(id);
            processLocks.remove(id);
            InstanceRecord errRecord = record.withStatus(InstanceStatus.ERROR).withLastSeenAt(now().toString());
            liveInstances.put(id, errRecord);
            storage.upsertInstance(errRecord);
            throw new IOException("Failed to spawn instance: " + e.getMessage(), e);
        }
    }

    private RestartResult restartInstance(InstanceRecord record, String reason, RestartPolicy policy) throws IOException {
        AgentProcess oldProcess = liveProcesses.remove(record.id());
        processLocks.remove(record.id());
        if (oldProcess != null) {
            try {
                oldProcess.sendLine("{\"id\":\"server-restart\",\"method\":\"exit\"}");
            } catch (IOException ignored) {
            }
            try {
                oldProcess.stop(DEFAULT_STOP_TIMEOUT);
            } catch (IOException ignored) {
            }
        }

        InstanceRecord starting = record.withStatus(InstanceStatus.STARTING).withLastSeenAt(now().toString());
        liveInstances.put(record.id(), starting);
        storage.upsertInstance(starting);
        try {
            AgentProcess process = launcher.start(new AgentProcessLauncher.StartRequest(record.id(), record.cwd(), record.label()));
            liveProcesses.put(record.id(), process);
            processLocks.put(record.id(), new Object());
            restartTrackers.remove(record.id());
            InstanceRecord online = starting.withStatus(InstanceStatus.ONLINE).withLastSeenAt(now().toString());
            liveInstances.put(record.id(), online);
            storage.upsertInstance(online);
            return new RestartResult(record.id(), true, reason, online, null);
        } catch (Exception e) {
            RestartTracker tracker = nextTracker(record.id(), policy);
            InstanceRecord error = starting.withStatus(InstanceStatus.ERROR).withLastSeenAt(now().toString());
            liveInstances.put(record.id(), error);
            storage.upsertInstance(error);
            return new RestartResult(record.id(), false,
                    reason + "; next restart allowed at " + tracker.nextAllowedAt(), error, e.getMessage());
        }
    }

    public Optional<String> sendRpc(String instanceId, String message) throws IOException {
        return sendRpcExchange(instanceId, message, DEFAULT_RPC_TIMEOUT).map(RpcExchange::response);
    }

    public Optional<String> sendRpc(String instanceId, String message, Duration timeout) throws IOException {
        return sendRpcExchange(instanceId, message, timeout).map(RpcExchange::response);
    }

    public Optional<RpcExchange> sendRpcExchange(String instanceId, String message, Duration timeout) throws IOException {
        AgentProcess process = liveProcesses.get(instanceId);
        if (process == null || !process.isAlive()) {
            return Optional.empty();
        }
        Object lock = processLocks.computeIfAbsent(instanceId, ignored -> new Object());
        synchronized (lock) {
            process.sendLine(message);
            Optional<RpcExchange> response = readRpcExchange(process, message, timeout,
                    event -> publishRpcEvent(instanceId, message, event));
            InstanceRecord live = liveInstances.get(instanceId);
            if (live != null) {
                InstanceRecord updated = live.withLastSeenAt(now().toString());
                liveInstances.put(instanceId, updated);
                storage.upsertInstance(updated);
            }
            return response;
        }
    }

    public synchronized Optional<InstanceRecord> stopInstance(String instanceId) throws IOException {
        InstanceRecord live = liveInstances.get(instanceId);
        if (live == null) {
            Optional<InstanceRecord> stored = storage.getInstance(instanceId);
            if (stored.isEmpty()) {
                return Optional.empty();
            }
            live = stored.get();
        }

        InstanceRecord stopping = live.withStatus(InstanceStatus.STOPPING).withLastSeenAt(now().toString());
        liveInstances.put(instanceId, stopping);
        storage.upsertInstance(stopping);

        AgentProcess process = liveProcesses.remove(instanceId);
        processLocks.remove(instanceId);
        if (process != null) {
            try {
                process.sendLine("{\"id\":\"server-stop\",\"method\":\"exit\"}");
            } catch (IOException ignored) {
            }
            process.stop(DEFAULT_STOP_TIMEOUT);
        }

        InstanceRecord stopped = stopping.withStatus(InstanceStatus.STOPPED).withLastSeenAt(now().toString());
        liveInstances.remove(instanceId);
        restartTrackers.remove(instanceId);
        storage.removeInstance(instanceId);
        return Optional.of(stopped);
    }

    public synchronized void shutdown() throws IOException {
        for (String id : List.copyOf(liveInstances.keySet())) {
            stopInstance(id);
        }
    }

    RestartPolicy restartPolicy() {
        return defaultRestartPolicy;
    }

    AgentProcessLauncher launcher() {
        return launcher;
    }

    public RpcEventSubscription subscribeRpcEvents(RpcEventListener listener) {
        return subscribeRpcEvents(null, listener);
    }

    public RpcEventSubscription subscribeRpcEvents(String instanceId, RpcEventListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener is required");
        }
        RpcEventSubscription subscription = new RpcEventSubscription(
                instanceId == null || instanceId.isBlank() ? null : instanceId, listener);
        rpcEventSubscriptions.add(subscription);
        return subscription;
    }

    public List<RpcEvent> recentRpcEvents(String instanceId, int maxEvents) {
        String normalizedInstanceId = instanceId == null || instanceId.isBlank() ? null : instanceId;
        int limit = Math.max(1, Math.min(RECENT_RPC_EVENT_HISTORY_LIMIT, maxEvents));
        synchronized (recentRpcEventsLock) {
            List<RpcEvent> filtered = recentRpcEvents.stream()
                    .filter(event -> normalizedInstanceId == null || normalizedInstanceId.equals(event.instanceId()))
                    .toList();
            int from = Math.max(0, filtered.size() - limit);
            return List.copyOf(filtered.subList(from, filtered.size()));
        }
    }

    public record RpcExchange(String response, List<String> events) {
        public RpcExchange {
            events = events == null ? List.of() : List.copyOf(events);
        }
    }

    public record RestartResult(String instanceId, boolean restarted, String reason, InstanceRecord record,
                                String error) {
    }

    public record RestartPolicy(int maxAttempts, Duration baseBackoff, Duration maxBackoff) {
        public RestartPolicy {
            if (maxAttempts < 1) {
                throw new IllegalArgumentException("maxAttempts must be positive");
            }
            baseBackoff = baseBackoff == null || baseBackoff.isNegative() ? Duration.ZERO : baseBackoff;
            maxBackoff = maxBackoff == null || maxBackoff.isNegative() ? baseBackoff : maxBackoff;
            if (maxBackoff.compareTo(baseBackoff) < 0) {
                maxBackoff = baseBackoff;
            }
        }
    }

    private record RestartTracker(int attempts, Instant nextAllowedAt) {
    }

    public record RpcEvent(long sequence, String instanceId, String requestId, String rawJson, String receivedAt) {
    }

    @FunctionalInterface
    public interface RpcEventListener {
        void onEvent(RpcEvent event);
    }

    public final class RpcEventSubscription implements AutoCloseable {
        private final String instanceId;
        private final RpcEventListener listener;
        private volatile boolean active = true;

        private RpcEventSubscription(String instanceId, RpcEventListener listener) {
            this.instanceId = instanceId;
            this.listener = listener;
        }

        @Override
        public void close() {
            active = false;
            rpcEventSubscriptions.remove(this);
        }

        public boolean isActive() {
            return active;
        }

        private boolean matches(String candidateInstanceId) {
            return active && (instanceId == null || instanceId.equals(candidateInstanceId));
        }
    }

    private static Optional<RpcExchange> readRpcExchange(AgentProcess process, String request, Duration timeout)
            throws IOException {
        return readRpcExchange(process, request, timeout, ignored -> {
        });
    }

    private static Optional<RpcExchange> readRpcExchange(AgentProcess process, String request, Duration timeout,
                                                        java.util.function.Consumer<String> eventConsumer)
            throws IOException {
        String expectedId = jsonFieldText(request, "id");
        List<String> events = new ArrayList<>();
        if (expectedId == null || expectedId.isBlank()) {
            return process.readLine(timeout).map(line -> new RpcExchange(line, events));
        }

        long deadlineNanos = System.nanoTime() + Math.max(0, timeout.toNanos());
        while (true) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return Optional.empty();
            }
            Optional<String> line = process.readLine(Duration.ofNanos(remainingNanos));
            if (line.isEmpty()) {
                return Optional.empty();
            }
            String value = line.get();
            if (expectedId.equals(jsonFieldText(value, "id"))) {
                return Optional.of(new RpcExchange(value, events));
            }
            events.add(value);
            eventConsumer.accept(value);
        }
    }

    private static String jsonFieldText(String json, String field) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(json).get(field);
            if (node == null || node.isNull()) {
                return null;
            }
            return node.isTextual() ? node.asText() : node.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isStale(InstanceRecord record, Duration staleAfter, Instant now) {
        if (record == null || staleAfter == null || staleAfter.isNegative()) {
            return false;
        }
        if (record.status() != InstanceStatus.ONLINE && record.status() != InstanceStatus.STARTING) {
            return false;
        }
        Instant lastSeen = parseInstant(record.lastSeenAt()).orElseGet(() -> parseInstant(record.createdAt()).orElse(now));
        return !lastSeen.plus(staleAfter).isAfter(now);
    }

    private static Optional<Instant> parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.parse(value));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private RestartTracker nextTracker(String instanceId, RestartPolicy policy) {
        RestartTracker current = restartTrackers.get(instanceId);
        int attempts = current == null ? 1 : current.attempts() + 1;
        Duration delay = restartDelay(policy, attempts);
        RestartTracker next = new RestartTracker(attempts, now().plus(delay));
        restartTrackers.put(instanceId, next);
        return next;
    }

    private static Duration restartDelay(RestartPolicy policy, int attempts) {
        if (policy.baseBackoff().isZero()) {
            return Duration.ZERO;
        }
        long multiplier = 1L << Math.min(Math.max(0, attempts - 1), 30);
        Duration delay;
        try {
            delay = policy.baseBackoff().multipliedBy(multiplier);
        } catch (ArithmeticException e) {
            delay = policy.maxBackoff();
        }
        return delay.compareTo(policy.maxBackoff()) > 0 ? policy.maxBackoff() : delay;
    }

    private Instant now() {
        return Instant.now(clock);
    }

    private void publishRpcEvent(String instanceId, String request, String rawJson) {
        RpcEvent event = new RpcEvent(rpcEventSequences.getAndIncrement(), instanceId, jsonFieldText(request, "id"),
                rawJson, now().toString());
        rememberRpcEvent(event);
        if (rpcEventSubscriptions.isEmpty()) {
            return;
        }
        for (RpcEventSubscription subscription : rpcEventSubscriptions) {
            if (!subscription.matches(instanceId)) {
                continue;
            }
            try {
                subscription.listener.onEvent(event);
            } catch (RuntimeException ignored) {
            }
        }
    }

    private void rememberRpcEvent(RpcEvent event) {
        synchronized (recentRpcEventsLock) {
            recentRpcEvents.addLast(event);
            while (recentRpcEvents.size() > RECENT_RPC_EVENT_HISTORY_LIMIT) {
                recentRpcEvents.removeFirst();
            }
        }
    }

    private static AgentProcessLauncher defaultLauncher(ServerStorage storage) {
        ServerRuntimeSettings settings = storage.config().getRuntimeSettings();
        return RpcAgentProcessLauncher.currentJava(storage.config().getLogsDir(), logRotationPolicy(settings));
    }

    private static RestartPolicy defaultRestartPolicy(ServerRuntimeSettings settings) {
        ServerRuntimeSettings.RestartSettings restart = settings == null
                ? ServerRuntimeSettings.defaults().restart()
                : settings.restart();
        return new RestartPolicy(restart.maxAttempts(), restart.baseBackoff(), restart.maxBackoff());
    }

    private static RpcAgentProcessLauncher.LogRotationPolicy logRotationPolicy(ServerRuntimeSettings settings) {
        ServerRuntimeSettings.LogRotationSettings logRotation = settings == null
                ? ServerRuntimeSettings.defaults().logRotation()
                : settings.logRotation();
        return new RpcAgentProcessLauncher.LogRotationPolicy(logRotation.maxBytes(), logRotation.maxBackups());
    }
}
