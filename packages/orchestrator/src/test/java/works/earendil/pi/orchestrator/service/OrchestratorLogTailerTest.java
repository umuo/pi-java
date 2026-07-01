package works.earendil.pi.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.orchestrator.config.OrchestratorConfig;
import works.earendil.pi.orchestrator.storage.OrchestratorStorage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestratorLogTailerTest {
    @TempDir
    Path tempDir;

    @Test
    void emitsOnlyLinesAppendedAfterStart() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);
        Files.createDirectories(config.getLogsDir());
        Files.writeString(config.getLogsDir().resolve("agent-1.stderr.log"), "existing\n");
        List<OrchestratorLogTailer.LogLine> lines = new ArrayList<>();
        OrchestratorLogTailer tailer = new OrchestratorLogTailer(storage, "agent-1", lines::add,
                Duration.ofSeconds(1), Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC));

        tailer.start();
        Files.writeString(config.getLogsDir().resolve("agent-1.stderr.log"), "next\n",
                StandardOpenOption.APPEND);

        assertThat(tailer.pollOnce()).isEqualTo(1);
        assertThat(lines).hasSize(1);
        assertThat(lines.getFirst().instanceId()).isEqualTo("agent-1");
        assertThat(lines.getFirst().line()).isEqualTo("next");
        assertThat(lines.getFirst().receivedAt()).isEqualTo("2026-07-01T00:00:00Z");
        tailer.close();
    }

    @Test
    void continuesFromBeginningWhenCurrentLogIsRotatedOrTruncated() throws Exception {
        OrchestratorConfig config = new OrchestratorConfig(Map.of("PI_ORCHESTRATOR_DIR", tempDir.toString()));
        OrchestratorStorage storage = new OrchestratorStorage(config);
        Files.createDirectories(config.getLogsDir());
        Path log = config.getLogsDir().resolve("agent-1.stderr.log");
        Files.writeString(log, "before-rotation\n");
        List<OrchestratorLogTailer.LogLine> lines = new ArrayList<>();
        OrchestratorLogTailer tailer = new OrchestratorLogTailer(storage, "agent-1", lines::add,
                Duration.ofSeconds(1), Clock.fixed(Instant.parse("2026-07-01T00:00:00Z"), ZoneOffset.UTC));

        tailer.start();
        Files.writeString(log.resolveSibling(log.getFileName() + ".1"), "before-rotation\n");
        Files.writeString(log, "after-rotation\n", StandardOpenOption.TRUNCATE_EXISTING);

        assertThat(tailer.pollOnce()).isEqualTo(1);
        assertThat(lines).extracting(OrchestratorLogTailer.LogLine::line)
                .containsExactly("after-rotation");
        tailer.close();
    }
}
