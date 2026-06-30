package works.earendil.pi.orchestrator.service;

import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.config.OrchestratorRuntimeSettings;
import works.earendil.pi.orchestrator.model.InstanceRecord;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public final class OrchestratorStatusReporter {
    private final OrchestratorStorage storage;
    private final Clock clock;

    public OrchestratorStatusReporter(OrchestratorStorage storage) {
        this(storage, Clock.systemUTC());
    }

    OrchestratorStatusReporter(OrchestratorStorage storage, Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public StatusReport snapshot() throws IOException {
        OrchestratorConfig config = storage.config();
        OrchestratorRuntimeSettings runtimeSettings = config.loadRuntimeSettings();
        List<OrchestratorStorage.InstanceLogRecord> logs = storage.listInstanceLogs();
        Map<String, List<OrchestratorStorage.InstanceLogRecord>> logsByInstance = logs.stream()
                .collect(Collectors.groupingBy(OrchestratorStorage.InstanceLogRecord::instanceId));
        List<InstanceStatusView> instances = storage.loadInstances().stream()
                .map(instance -> toInstanceView(instance, logsByInstance.getOrDefault(instance.id(), List.of())))
                .toList();
        List<LogStatusView> logViews = logs.stream()
                .map(log -> new LogStatusView(log.instanceId(), log.rotation(), log.path(), log.bytes(),
                        log.modifiedAt()))
                .toList();
        return new StatusReport(config.getOrchestratorDir(), config.getRuntimeSettingsPath(), runtimeSettings,
                instances, logViews, new EventStreamStatus(true,
                "OrchestratorSupervisor.subscribeRpcEvents(instanceId, listener)"));
    }

    private InstanceStatusView toInstanceView(InstanceRecord instance,
                                              List<OrchestratorStorage.InstanceLogRecord> logs) {
        OrchestratorStorage.InstanceLogRecord latestLog = logs.stream()
                .min(Comparator.comparingInt(OrchestratorStorage.InstanceLogRecord::rotation))
                .orElse(null);
        return new InstanceStatusView(
                instance.id(),
                instance.status() == null ? "unknown" : instance.status().getValue(),
                instance.label(),
                instance.cwd(),
                instance.lastSeenAt(),
                heartbeatAge(instance.lastSeenAt()),
                logs.size(),
                latestLog == null ? null : latestLog.path(),
                latestLog == null ? 0 : latestLog.bytes());
    }

    private String heartbeatAge(String lastSeenAt) {
        if (lastSeenAt == null || lastSeenAt.isBlank()) {
            return "unknown";
        }
        try {
            Duration age = Duration.between(Instant.parse(lastSeenAt), Instant.now(clock));
            if (age.isNegative()) {
                return "0s";
            }
            return formatDuration(age);
        } catch (DateTimeParseException e) {
            return "invalid";
        }
    }

    private static String formatDuration(Duration duration) {
        long seconds = duration.toSeconds();
        if (seconds < 60) {
            return seconds + "s";
        }
        long minutes = seconds / 60;
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = minutes / 60;
        if (hours < 48) {
            return hours + "h";
        }
        return duration.toDays() + "d";
    }

    public record InstanceStatusView(
            String id,
            String status,
            String label,
            String cwd,
            String lastSeenAt,
            String heartbeatAge,
            int logCount,
            Path latestLogPath,
            long latestLogBytes
    ) {
    }

    public record LogStatusView(String instanceId, int rotation, Path path, long bytes, String modifiedAt) {
    }

    public record EventStreamStatus(boolean available, String api) {
    }

    public record StatusReport(
            Path orchestratorDir,
            Path runtimeSettingsPath,
            OrchestratorRuntimeSettings runtimeSettings,
            List<InstanceStatusView> instances,
            List<LogStatusView> logs,
            EventStreamStatus eventStream
    ) {
        public StatusReport {
            instances = instances == null ? List.of() : List.copyOf(instances);
            logs = logs == null ? List.of() : List.copyOf(logs);
        }

        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("Orchestrator status\n");
            out.append("dir: ").append(orchestratorDir).append("\n");
            out.append("settings: ").append(runtimeSettingsPath).append("\n");
            appendRuntimeSettings(out);
            appendInstances(out);
            appendLogs(out);
            out.append("event stream: ")
                    .append(eventStream != null && eventStream.available() ? "available" : "unavailable");
            if (eventStream != null && eventStream.api() != null && !eventStream.api().isBlank()) {
                out.append(" via ").append(eventStream.api());
            }
            return out.toString().trim();
        }

        private void appendRuntimeSettings(StringBuilder out) {
            OrchestratorRuntimeSettings settings = runtimeSettings == null
                    ? OrchestratorRuntimeSettings.defaults()
                    : runtimeSettings;
            out.append("restart: maxAttempts=").append(settings.restart().maxAttempts())
                    .append(", baseBackoff=").append(settings.restart().baseBackoff().toMillis()).append("ms")
                    .append(", maxBackoff=").append(settings.restart().maxBackoff().toMillis()).append("ms")
                    .append("\n");
            boolean rotationEnabled = settings.logRotation().maxBytes() > 0;
            out.append("log rotation: enabled=").append(rotationEnabled)
                    .append(", maxBytes=").append(settings.logRotation().maxBytes())
                    .append(", maxBackups=").append(settings.logRotation().maxBackups())
                    .append("\n");
        }

        private void appendInstances(StringBuilder out) {
            out.append("instances: ").append(instances.size()).append("\n");
            if (instances.isEmpty()) {
                out.append("heartbeat: no instances\n");
                return;
            }
            for (int i = 0; i < instances.size(); i++) {
                InstanceStatusView instance = instances.get(i);
                out.append(i + 1).append(". ").append(instance.id())
                        .append(" [").append(instance.status()).append("]");
                if (instance.label() != null && !instance.label().isBlank()) {
                    out.append(" label=").append(instance.label());
                }
                out.append("\n");
                out.append("   cwd: ").append(blankFallback(instance.cwd(), "unknown")).append("\n");
                out.append("   heartbeat: lastSeen=")
                        .append(blankFallback(instance.lastSeenAt(), "unknown"))
                        .append(", age=").append(instance.heartbeatAge()).append("\n");
                out.append("   logs: ").append(instance.logCount());
                if (instance.latestLogPath() != null) {
                    out.append(", latest=").append(instance.latestLogPath())
                            .append(" (").append(instance.latestLogBytes()).append(" bytes)");
                }
                out.append("\n");
            }
        }

        private void appendLogs(StringBuilder out) {
            out.append("logs: ").append(logs.size()).append("\n");
            int limit = Math.min(5, logs.size());
            for (int i = 0; i < limit; i++) {
                LogStatusView log = logs.get(i);
                out.append(i + 1).append(". ").append(log.instanceId())
                        .append(" rotation=").append(log.rotation())
                        .append(", bytes=").append(log.bytes())
                        .append(", modified=").append(blankFallback(log.modifiedAt(), "unknown"))
                        .append("\n");
                out.append("   path: ").append(log.path()).append("\n");
            }
            if (logs.size() > limit) {
                out.append("   ... ").append(logs.size() - limit).append(" more logs\n");
            }
        }

        private static String blankFallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
