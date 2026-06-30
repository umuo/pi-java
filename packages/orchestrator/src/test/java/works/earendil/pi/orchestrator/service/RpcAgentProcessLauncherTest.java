package works.earendil.pi.orchestrator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RpcAgentProcessLauncherTest {
    @TempDir
    Path tempDir;

    @Test
    void archivesChildProcessStderrToInstanceLog() throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Path logDir = tempDir.resolve("logs");
        RpcAgentProcessLauncher launcher = new RpcAgentProcessLauncher(List.of(javaBin, "-version"), logDir);

        AgentProcess process = launcher.start(new AgentProcessLauncher.StartRequest("agent/one", tempDir.toString(), "stderr"));

        Path logPath = process.stderrLogPath().orElseThrow();
        for (int i = 0; i < 20 && (!Files.exists(logPath) || Files.readString(logPath).isBlank()); i++) {
            Thread.sleep(50);
        }
        process.stop(Duration.ofMillis(100));

        assertThat(logPath).isEqualTo(logDir.toAbsolutePath().normalize().resolve("agent_one.stderr.log"));
        assertThat(Files.readString(logPath)).containsIgnoringCase("version");
    }
}
