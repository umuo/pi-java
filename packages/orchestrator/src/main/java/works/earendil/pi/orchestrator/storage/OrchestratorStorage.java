package works.earendil.pi.orchestrator.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.model.InstanceRecord;
import works.earendil.pi.orchestrator.model.MachineRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class OrchestratorStorage {
    private static final String STDERR_LOG_SUFFIX = ".stderr.log";

    private final OrchestratorConfig config;
    private final ObjectMapper mapper;

    public OrchestratorStorage(OrchestratorConfig config) {
        this.config = config;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public OrchestratorConfig config() {
        return config;
    }

    public record InstanceLogRecord(String instanceId, Path path, long bytes, String modifiedAt, int rotation) {
    }

    private synchronized void ensureOrchestratorDir() throws IOException {
        Path dir = config.getOrchestratorDir();
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    public synchronized Optional<MachineRecord> loadMachine() throws IOException {
        Path path = config.getMachinePath();
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        return Optional.of(mapper.readValue(path.toFile(), MachineRecord.class));
    }

    public synchronized void saveMachine(MachineRecord machine) throws IOException {
        ensureOrchestratorDir();
        mapper.writeValue(config.getMachinePath().toFile(), machine);
    }

    public synchronized void deleteMachine() throws IOException {
        Path path = config.getMachinePath();
        Files.deleteIfExists(path);
    }

    public synchronized List<InstanceRecord> loadInstances() throws IOException {
        Path path = config.getInstancesPath();
        if (!Files.exists(path)) {
            return new ArrayList<>();
        }
        return mapper.readValue(path.toFile(), new TypeReference<List<InstanceRecord>>() {});
    }

    public synchronized void saveInstances(List<InstanceRecord> instances) throws IOException {
        ensureOrchestratorDir();
        mapper.writeValue(config.getInstancesPath().toFile(), instances);
    }

    public synchronized Optional<InstanceRecord> getInstance(String instanceId) throws IOException {
        return loadInstances().stream()
                .filter(inst -> inst.id().equals(instanceId))
                .findFirst();
    }

    public synchronized void upsertInstance(InstanceRecord instance) throws IOException {
        List<InstanceRecord> instances = new ArrayList<>(loadInstances());
        int index = -1;
        for (int i = 0; i < instances.size(); i++) {
            if (instances.get(i).id().equals(instance.id())) {
                index = i;
                break;
            }
        }
        if (index == -1) {
            instances.add(instance);
        } else {
            instances.set(index, instance);
        }
        saveInstances(instances);
    }

    public synchronized void removeInstance(String instanceId) throws IOException {
        List<InstanceRecord> instances = new ArrayList<>(loadInstances());
        instances.removeIf(inst -> inst.id().equals(instanceId));
        saveInstances(instances);
    }

    public synchronized List<InstanceLogRecord> listInstanceLogs() throws IOException {
        Path logsDir = config.getLogsDir();
        if (!Files.exists(logsDir)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.list(logsDir)) {
            return paths
                    .map(this::toLogRecord)
                    .flatMap(Optional::stream)
                    .sorted(Comparator.comparing(InstanceLogRecord::instanceId)
                            .thenComparingInt(InstanceLogRecord::rotation))
                    .toList();
        }
    }

    private Optional<InstanceLogRecord> toLogRecord(Path path) {
        try {
            String fileName = path.getFileName().toString();
            LogName logName = parseLogName(fileName).orElse(null);
            if (logName == null) {
                return Optional.empty();
            }
            long bytes = Files.size(path);
            String modifiedAt = Instant.ofEpochMilli(Files.getLastModifiedTime(path).toMillis()).toString();
            return Optional.of(new InstanceLogRecord(logName.instanceId(), path.toAbsolutePath().normalize(), bytes,
                    modifiedAt, logName.rotation()));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<LogName> parseLogName(String fileName) {
        int suffixIndex = fileName.indexOf(STDERR_LOG_SUFFIX);
        if (suffixIndex <= 0) {
            return Optional.empty();
        }
        String instanceId = fileName.substring(0, suffixIndex);
        String remainder = fileName.substring(suffixIndex + STDERR_LOG_SUFFIX.length());
        if (remainder.isEmpty()) {
            return Optional.of(new LogName(instanceId, 0));
        }
        if (!remainder.startsWith(".")) {
            return Optional.empty();
        }
        try {
            int rotation = Integer.parseInt(remainder.substring(1));
            return rotation > 0 ? Optional.of(new LogName(instanceId, rotation)) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private record LogName(String instanceId, int rotation) {
    }
}
