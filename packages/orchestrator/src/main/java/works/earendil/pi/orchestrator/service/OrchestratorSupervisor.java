package works.earendil.pi.orchestrator.service;

import works.earendil.pi.orchestrator.model.InstanceRecord;
import works.earendil.pi.orchestrator.model.InstanceStatus;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class OrchestratorSupervisor {
    private final OrchestratorStorage storage;
    private final Map<String, InstanceRecord> liveInstances = new ConcurrentHashMap<>();

    public OrchestratorSupervisor(OrchestratorStorage storage) {
        this.storage = storage;
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

    public synchronized InstanceRecord spawnInstance(String cwd, String label) throws IOException {
        String now = Instant.now().toString();
        String id = UUID.randomUUID().toString();
        InstanceRecord record = new InstanceRecord(id, InstanceStatus.STARTING, cwd, now, now, label, null, null, null);
        liveInstances.put(id, record);
        storage.upsertInstance(record);

        try {
            InstanceRecord onlineRecord = record.withStatus(InstanceStatus.ONLINE).withLastSeenAt(Instant.now().toString());
            liveInstances.put(id, onlineRecord);
            storage.upsertInstance(onlineRecord);
            return onlineRecord;
        } catch (Exception e) {
            InstanceRecord errRecord = record.withStatus(InstanceStatus.ERROR).withLastSeenAt(Instant.now().toString());
            liveInstances.put(id, errRecord);
            storage.upsertInstance(errRecord);
            throw new IOException("Failed to spawn instance: " + e.getMessage(), e);
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
}
