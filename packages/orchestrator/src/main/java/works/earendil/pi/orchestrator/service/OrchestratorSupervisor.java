package works.earendil.pi.orchestrator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import works.earendil.pi.orchestrator.model.InstanceRecord;
import works.earendil.pi.orchestrator.model.InstanceStatus;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OrchestratorSupervisor {
    private static final Duration DEFAULT_RPC_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_STOP_TIMEOUT = Duration.ofSeconds(2);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OrchestratorStorage storage;
    private final AgentProcessLauncher launcher;
    private final Map<String, InstanceRecord> liveInstances = new ConcurrentHashMap<>();
    private final Map<String, AgentProcess> liveProcesses = new ConcurrentHashMap<>();
    private final Map<String, Object> processLocks = new ConcurrentHashMap<>();

    public OrchestratorSupervisor(OrchestratorStorage storage) {
        this(storage, RpcAgentProcessLauncher.currentJava(storage.config().getLogsDir()));
    }

    public OrchestratorSupervisor(OrchestratorStorage storage, AgentProcessLauncher launcher) {
        this.storage = storage;
        this.launcher = launcher;
    }

    public synchronized void recoverAfterRestart() throws IOException {
        String now = Instant.now().toString();
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
        String now = Instant.now().toString();
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
        Instant now = Instant.now();
        List<RestartResult> restarted = new ArrayList<>();
        for (Map.Entry<String, InstanceRecord> entry : List.copyOf(liveInstances.entrySet())) {
            String instanceId = entry.getKey();
            InstanceRecord record = entry.getValue();
            if (!isStale(record, staleAfter, now)) {
                continue;
            }
            restarted.add(restartInstance(record, "stale for " + staleAfter));
        }
        return List.copyOf(restarted);
    }

    public synchronized InstanceRecord spawnInstance(String cwd, String label) throws IOException {
        String now = Instant.now().toString();
        String id = UUID.randomUUID().toString();
        InstanceRecord record = new InstanceRecord(id, InstanceStatus.STARTING, cwd, now, now, label, null, null, null);
        liveInstances.put(id, record);
        storage.upsertInstance(record);
        AgentProcess process = null;

        try {
            process = launcher.start(new AgentProcessLauncher.StartRequest(id, cwd, label));
            liveProcesses.put(id, process);
            processLocks.put(id, new Object());
            InstanceRecord onlineRecord = record.withStatus(InstanceStatus.ONLINE).withLastSeenAt(Instant.now().toString());
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
            InstanceRecord errRecord = record.withStatus(InstanceStatus.ERROR).withLastSeenAt(Instant.now().toString());
            liveInstances.put(id, errRecord);
            storage.upsertInstance(errRecord);
            throw new IOException("Failed to spawn instance: " + e.getMessage(), e);
        }
    }

    private RestartResult restartInstance(InstanceRecord record, String reason) throws IOException {
        AgentProcess oldProcess = liveProcesses.remove(record.id());
        processLocks.remove(record.id());
        if (oldProcess != null) {
            try {
                oldProcess.sendLine("{\"id\":\"orchestrator-restart\",\"method\":\"exit\"}");
            } catch (IOException ignored) {
            }
            try {
                oldProcess.stop(DEFAULT_STOP_TIMEOUT);
            } catch (IOException ignored) {
            }
        }

        InstanceRecord starting = record.withStatus(InstanceStatus.STARTING).withLastSeenAt(Instant.now().toString());
        liveInstances.put(record.id(), starting);
        storage.upsertInstance(starting);
        try {
            AgentProcess process = launcher.start(new AgentProcessLauncher.StartRequest(record.id(), record.cwd(), record.label()));
            liveProcesses.put(record.id(), process);
            processLocks.put(record.id(), new Object());
            InstanceRecord online = starting.withStatus(InstanceStatus.ONLINE).withLastSeenAt(Instant.now().toString());
            liveInstances.put(record.id(), online);
            storage.upsertInstance(online);
            return new RestartResult(record.id(), true, reason, online, null);
        } catch (Exception e) {
            InstanceRecord error = starting.withStatus(InstanceStatus.ERROR).withLastSeenAt(Instant.now().toString());
            liveInstances.put(record.id(), error);
            storage.upsertInstance(error);
            return new RestartResult(record.id(), false, reason, error, e.getMessage());
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
            Optional<RpcExchange> response = readRpcExchange(process, message, timeout);
            InstanceRecord live = liveInstances.get(instanceId);
            if (live != null) {
                InstanceRecord updated = live.withLastSeenAt(Instant.now().toString());
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

        InstanceRecord stopping = live.withStatus(InstanceStatus.STOPPING).withLastSeenAt(Instant.now().toString());
        liveInstances.put(instanceId, stopping);
        storage.upsertInstance(stopping);

        AgentProcess process = liveProcesses.remove(instanceId);
        processLocks.remove(instanceId);
        if (process != null) {
            try {
                process.sendLine("{\"id\":\"orchestrator-stop\",\"method\":\"exit\"}");
            } catch (IOException ignored) {
            }
            process.stop(DEFAULT_STOP_TIMEOUT);
        }

        InstanceRecord stopped = stopping.withStatus(InstanceStatus.STOPPED).withLastSeenAt(Instant.now().toString());
        liveInstances.remove(instanceId);
        storage.removeInstance(instanceId);
        return Optional.of(stopped);
    }

    public synchronized void shutdown() throws IOException {
        for (String id : List.copyOf(liveInstances.keySet())) {
            stopInstance(id);
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

    private static Optional<RpcExchange> readRpcExchange(AgentProcess process, String request, Duration timeout)
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
}
