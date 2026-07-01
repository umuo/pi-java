package works.earendil.pi.orchestrator.service;

import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class OrchestratorLogTailer implements AutoCloseable {
    private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(500);

    private final OrchestratorStorage storage;
    private final String instanceId;
    private final LogLineListener listener;
    private final Duration pollInterval;
    private final Clock clock;
    private final ScheduledExecutorService executor;
    private final Map<Path, TailState> states = new HashMap<>();
    private ScheduledFuture<?> future;

    public OrchestratorLogTailer(OrchestratorStorage storage, String instanceId, LogLineListener listener) {
        this(storage, instanceId, listener, DEFAULT_POLL_INTERVAL, Clock.systemUTC());
    }

    OrchestratorLogTailer(OrchestratorStorage storage, String instanceId, LogLineListener listener,
                          Duration pollInterval, Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.instanceId = instanceId == null || instanceId.isBlank() ? null : instanceId.trim();
        this.listener = Objects.requireNonNull(listener, "listener");
        this.pollInterval = pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()
                ? DEFAULT_POLL_INTERVAL
                : pollInterval;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "pi-orchestrator-log-tail");
            thread.setDaemon(true);
            return thread;
        });
    }

    public synchronized void start() throws IOException {
        if (future != null && !future.isCancelled()) {
            return;
        }
        initializeCurrentLogs();
        future = executor.scheduleWithFixedDelay(() -> {
            try {
                pollOnce();
            } catch (IOException ignored) {
            }
        }, pollInterval.toMillis(), pollInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    public synchronized int pollOnce() throws IOException {
        int emitted = 0;
        Set<Path> currentPaths = new HashSet<>();
        for (OrchestratorStorage.InstanceLogRecord log : currentLogs()) {
            Path path = log.path();
            currentPaths.add(path);
            TailState state = states.computeIfAbsent(path, ignored -> new TailState(0, ""));
            emitted += emitNewLines(log, state);
        }
        states.keySet().removeIf(path -> !currentPaths.contains(path));
        return emitted;
    }

    private void initializeCurrentLogs() throws IOException {
        states.clear();
        for (OrchestratorStorage.InstanceLogRecord log : currentLogs()) {
            long bytes = Files.exists(log.path()) ? Files.size(log.path()) : 0;
            states.put(log.path(), new TailState(bytes, ""));
        }
    }

    private List<OrchestratorStorage.InstanceLogRecord> currentLogs() throws IOException {
        return storage.listInstanceLogs().stream()
                .filter(log -> log.rotation() == 0)
                .filter(log -> instanceId == null || instanceId.equals(log.instanceId()))
                .toList();
    }

    private int emitNewLines(OrchestratorStorage.InstanceLogRecord log, TailState state) throws IOException {
        Path path = log.path();
        if (!Files.exists(path)) {
            state.position = 0;
            state.pending = "";
            return 0;
        }
        long size = Files.size(path);
        if (size < state.position) {
            state.position = 0;
            state.pending = "";
        }
        if (size == state.position) {
            return 0;
        }
        byte[] bytes = Files.readAllBytes(path);
        int from = Math.max(0, (int) Math.min(state.position, bytes.length));
        String text = state.pending + new String(bytes, from, bytes.length - from, StandardCharsets.UTF_8);
        state.position = bytes.length;

        int splitAt = lastLineBreakEnd(text);
        if (splitAt < 0) {
            state.pending = text;
            return 0;
        }
        String complete = text.substring(0, splitAt);
        state.pending = text.substring(splitAt);
        int emitted = 0;
        for (String line : complete.lines().toList()) {
            try {
                listener.onLine(new LogLine(log.instanceId(), path, state.position, line, Instant.now(clock).toString()));
            } catch (RuntimeException ignored) {
            }
            emitted++;
        }
        return emitted;
    }

    private static int lastLineBreakEnd(String text) {
        int newline = text.lastIndexOf('\n');
        int carriageReturn = text.lastIndexOf('\r');
        int index = Math.max(newline, carriageReturn);
        if (index < 0) {
            return -1;
        }
        return index + 1;
    }

    @Override
    public synchronized void close() {
        if (future != null) {
            future.cancel(true);
        }
        future = null;
        states.clear();
        executor.shutdownNow();
    }

    private static final class TailState {
        private long position;
        private String pending;

        private TailState(long position, String pending) {
            this.position = position;
            this.pending = pending == null ? "" : pending;
        }
    }

    public record LogLine(String instanceId, Path path, long offset, String line, String receivedAt) {
    }

    @FunctionalInterface
    public interface LogLineListener {
        void onLine(LogLine line);
    }
}
