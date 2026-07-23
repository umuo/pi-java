package works.earendil.pi.server.service;

import works.earendil.pi.server.config.ServerConfig;
import works.earendil.pi.server.config.ServerRuntimeSettings;
import works.earendil.pi.server.model.InstanceRecord;
import works.earendil.pi.server.storage.ServerStorage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ServerStatusReporter {
    private final ServerStorage storage;
    private final Clock clock;

    public ServerStatusReporter(ServerStorage storage) {
        this(storage, Clock.systemUTC());
    }

    ServerStatusReporter(ServerStorage storage, Clock clock) {
        this.storage = Objects.requireNonNull(storage, "storage");
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    public StatusReport snapshot() throws IOException {
        ServerConfig config = storage.config();
        ServerRuntimeSettings runtimeSettings = config.loadRuntimeSettings();
        List<ServerStorage.InstanceLogRecord> logs = storage.listInstanceLogs();
        Map<String, List<ServerStorage.InstanceLogRecord>> logsByInstance = logs.stream()
                .collect(Collectors.groupingBy(ServerStorage.InstanceLogRecord::instanceId));
        List<InstanceStatusView> instances = storage.loadInstances().stream()
                .map(instance -> toInstanceView(instance, logsByInstance.getOrDefault(instance.id(), List.of())))
                .toList();
        List<LogStatusView> logViews = logs.stream()
                .map(log -> new LogStatusView(log.instanceId(), log.rotation(), log.path(), log.bytes(),
                        log.modifiedAt()))
                .toList();
        return new StatusReport(config.getServerDir(), config.getRuntimeSettingsPath(), runtimeSettings,
                instances, logViews, new EventStreamStatus(true,
                "ServerSupervisor.subscribeRpcEvents(instanceId, listener)"));
    }

    public LogTailView tailLatestLog(String instanceId, int maxLines) throws IOException {
        int lineLimit = Math.max(1, Math.min(500, maxLines));
        String normalizedInstanceId = instanceId == null || instanceId.isBlank() ? null : instanceId.trim();
        Optional<ServerStorage.InstanceLogRecord> selected = storage.listInstanceLogs().stream()
                .filter(log -> normalizedInstanceId == null || normalizedInstanceId.equals(log.instanceId()))
                .min(Comparator.comparingInt(ServerStorage.InstanceLogRecord::rotation));
        if (selected.isEmpty()) {
            String scope = normalizedInstanceId == null ? "any instance" : normalizedInstanceId;
            return new LogTailView(normalizedInstanceId, -1, null, 0, 0, "", "no logs found for " + scope);
        }

        ServerStorage.InstanceLogRecord log = selected.get();
        List<String> allLines = Files.readAllLines(log.path(), StandardCharsets.UTF_8);
        int fromIndex = Math.max(0, allLines.size() - lineLimit);
        List<String> tailLines = allLines.subList(fromIndex, allLines.size());
        return new LogTailView(log.instanceId(), log.rotation(), log.path(), log.bytes(), tailLines.size(),
                String.join(System.lineSeparator(), tailLines), "");
    }

    public DashboardView dashboard(List<ServerSupervisor.RpcEvent> recentEvents, String instanceId,
                                   int maxEvents, int maxLogLines) throws IOException {
        String normalizedInstanceId = instanceId == null || instanceId.isBlank() ? null : instanceId.trim();
        int eventLimit = Math.max(1, Math.min(200, maxEvents));
        int logLineLimit = Math.max(1, Math.min(50, maxLogLines));
        StatusReport snapshot = snapshot();
        List<InstanceStatusView> instances = snapshot.instances().stream()
                .filter(instance -> normalizedInstanceId == null || normalizedInstanceId.equals(instance.id()))
                .toList();
        List<LogStatusView> logs = snapshot.logs().stream()
                .filter(log -> normalizedInstanceId == null || normalizedInstanceId.equals(log.instanceId()))
                .toList();
        List<DashboardLogSnippet> stderr = currentLogSnippets(logs, logLineLimit);
        List<ServerSupervisor.RpcEvent> events = (recentEvents == null ? List.<ServerSupervisor.RpcEvent>of() : recentEvents).stream()
                .filter(event -> normalizedInstanceId == null || normalizedInstanceId.equals(event.instanceId()))
                .toList();
        int from = Math.max(0, events.size() - eventLimit);
        return new DashboardView(normalizedInstanceId, instances, logs, stderr, events.subList(from, events.size()));
    }

    private static List<DashboardLogSnippet> currentLogSnippets(List<LogStatusView> logs, int maxLines)
            throws IOException {
        List<DashboardLogSnippet> snippets = new java.util.ArrayList<>();
        for (LogStatusView log : logs.stream().filter(log -> log.rotation() == 0).toList()) {
            List<String> lines = Files.readAllLines(log.path(), StandardCharsets.UTF_8);
            int from = Math.max(0, lines.size() - maxLines);
            snippets.add(new DashboardLogSnippet(log.instanceId(), log.path(),
                    List.copyOf(lines.subList(from, lines.size()))));
        }
        return List.copyOf(snippets);
    }

    private InstanceStatusView toInstanceView(InstanceRecord instance,
                                              List<ServerStorage.InstanceLogRecord> logs) {
        ServerStorage.InstanceLogRecord latestLog = logs.stream()
                .min(Comparator.comparingInt(ServerStorage.InstanceLogRecord::rotation))
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

    public record LogTailView(String instanceId, int rotation, Path path, long bytes, int lines, String content,
                              String message) {
        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("Server log tail\n");
            if (message != null && !message.isBlank()) {
                out.append("message: ").append(message).append("\n");
            }
            if (instanceId != null && !instanceId.isBlank()) {
                out.append("instance: ").append(instanceId).append("\n");
            }
            if (path != null) {
                out.append("rotation: ").append(rotation).append("\n");
                out.append("path: ").append(path).append("\n");
                out.append("bytes: ").append(bytes).append("\n");
                out.append("lines: ").append(lines).append("\n");
                out.append("---");
                if (content != null && !content.isBlank()) {
                    out.append("\n").append(content);
                }
            }
            return out.toString().trim();
        }
    }

    public record EventStreamStatus(boolean available, String api) {
    }

    public record DashboardLogSnippet(String instanceId, Path path, List<String> lines) {
        public DashboardLogSnippet {
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }

    public record DashboardView(
            String instanceId,
            List<InstanceStatusView> instances,
            List<LogStatusView> logs,
            List<DashboardLogSnippet> stderr,
            List<ServerSupervisor.RpcEvent> recentEvents
    ) {
        public DashboardView {
            instances = instances == null ? List.of() : List.copyOf(instances);
            logs = logs == null ? List.of() : List.copyOf(logs);
            stderr = stderr == null ? List.of() : List.copyOf(stderr);
            recentEvents = recentEvents == null ? List.of() : List.copyOf(recentEvents);
        }

        public String render() {
            StringBuilder out = new StringBuilder();
            out.append("Server dashboard\n");
            out.append("scope: ").append(instanceId == null || instanceId.isBlank() ? "all instances" : instanceId)
                    .append("\n");
            out.append("instances: ").append(instances.size())
                    .append(" | logs: ").append(logs.size())
                    .append(" | recent events: ").append(recentEvents.size()).append("\n");
            appendInstances(out);
            appendColumns(out);
            return out.toString().trim();
        }

        public String renderInteractive() {
            return renderInteractive(100, 24, 0, 0);
        }

        public String renderInteractive(int width, int height, int eventScrollY, int stderrScrollY) {
            int safeWidth = Math.max(60, width);
            StringBuilder out = new StringBuilder();
            out.append(String.format("=== [LIVE SERVER DASHBOARD] Scope: %s | Instances: %d | Events Buf: %d ===\n",
                    blankFallback(instanceId, "ALL"), instances.size(), recentEvents.size()));
            out.append("Hotkeys: [f] filter instance | [e/E] scroll events | [s/S] scroll stderr | [r] refresh\n");
            out.append("-".repeat(Math.min(120, safeWidth))).append("\n");

            out.append("ACTIVE INSTANCES: ");
            if (instances.isEmpty()) {
                out.append("(none)");
            } else {
                out.append(instances.stream()
                        .map(i -> String.format("%s[%s|%s]", i.id(), i.status(), i.heartbeatAge()))
                        .collect(java.util.stream.Collectors.joining(", ")));
            }
            out.append("\n").append("-".repeat(Math.min(120, safeWidth))).append("\n");

            List<String> eventRows = recentEvents.stream()
                    .map(DashboardView::eventSummary)
                    .toList();
            List<String> stderrRows = stderr.stream()
                    .flatMap(snippet -> snippet.lines().stream()
                            .map(line -> snippet.instanceId() + ": " + line))
                    .toList();

            int colWidth = (safeWidth - 3) / 2;
            String evHeader = String.format("EVENTS BUFFER (scroll %d/%d)", Math.min(eventScrollY, eventRows.size()), eventRows.size());
            String stHeader = String.format("STDERR TAIL (scroll %d/%d)", Math.min(stderrScrollY, stderrRows.size()), stderrRows.size());
            out.append(fit(evHeader, colWidth)).append(" | ").append(fit(stHeader, colWidth)).append("\n");

            int maxVisibleRows = height > 8 ? height - 6 : 15;
            for (int i = 0; i < maxVisibleRows; i++) {
                int evIdx = eventScrollY + i;
                int stIdx = stderrScrollY + i;
                if (evIdx >= eventRows.size() && stIdx >= stderrRows.size()) {
                    break;
                }
                String evLine = evIdx < eventRows.size() ? eventRows.get(evIdx) : "";
                String stLine = stIdx < stderrRows.size() ? stderrRows.get(stIdx) : "";
                out.append(fit(evLine, colWidth)).append(" | ").append(fit(stLine, colWidth)).append("\n");
            }
            return out.toString().stripTrailing();
        }

        private void appendInstances(StringBuilder out) {
            out.append("instances\n");
            if (instances.isEmpty()) {
                out.append("- none\n");
                return;
            }
            for (InstanceStatusView instance : instances) {
                out.append("- ").append(instance.id())
                        .append(" [").append(instance.status()).append("]")
                        .append(" label=").append(blankFallback(instance.label(), "-"))
                        .append(" heartbeat=").append(blankFallback(instance.heartbeatAge(), "unknown"))
                        .append(" logs=").append(instance.logCount()).append("\n");
            }
        }

        private void appendColumns(StringBuilder out) {
            List<String> eventRows = recentEvents.stream()
                    .map(DashboardView::eventSummary)
                    .toList();
            List<String> stderrRows = stderr.stream()
                    .flatMap(snippet -> snippet.lines().stream()
                            .map(line -> snippet.instanceId() + ": " + line))
                    .toList();
            out.append("event stream").append(" ".repeat(47)).append("| stderr\n");
            int rows = Math.max(eventRows.size(), stderrRows.size());
            if (rows == 0) {
                out.append(fit("no recent rpc events", 59)).append(" | ")
                        .append(fit("no stderr lines", 59)).append("\n");
                return;
            }
            for (int i = 0; i < rows; i++) {
                String event = i < eventRows.size() ? eventRows.get(i) : "";
                String stderrLine = i < stderrRows.size() ? stderrRows.get(i) : "";
                out.append(fit(event, 59)).append(" | ").append(fit(stderrLine, 59)).append("\n");
            }
        }

        private static String eventSummary(ServerSupervisor.RpcEvent event) {
            return "seq=" + event.sequence()
                    + " " + blankFallback(event.instanceId(), "-")
                    + " request=" + blankFallback(event.requestId(), "-")
                    + " " + firstLine(event.rawJson());
        }

        private static String firstLine(String value) {
            if (value == null || value.isBlank()) {
                return "";
            }
            return value.split("\\R", 2)[0];
        }

        private static String fit(String value, int width) {
            String normalized = value == null ? "" : value.replace('\n', ' ');
            if (normalized.length() > width) {
                return normalized.substring(0, Math.max(0, width - 3)) + "...";
            }
            return normalized + " ".repeat(Math.max(0, width - normalized.length()));
        }

        private static String blankFallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    public record StatusReport(
            Path serverDir,
            Path runtimeSettingsPath,
            ServerRuntimeSettings runtimeSettings,
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
            out.append("Server status\n");
            out.append("dir: ").append(serverDir).append("\n");
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
            ServerRuntimeSettings settings = runtimeSettings == null
                    ? ServerRuntimeSettings.defaults()
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
