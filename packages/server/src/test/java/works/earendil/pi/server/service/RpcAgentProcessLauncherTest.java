package works.earendil.pi.server.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
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

    @Test
    void rotatesChildProcessStderrLogs() throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        Path logDir = tempDir.resolve("logs");
        RpcAgentProcessLauncher launcher = new RpcAgentProcessLauncher(
                List.of(javaBin, "-cp", System.getProperty("java.class.path"), StderrWriter.class.getName()),
                logDir,
                new RpcAgentProcessLauncher.LogRotationPolicy(120, 2));

        AgentProcess process = launcher.start(new AgentProcessLauncher.StartRequest("agent/rotate", tempDir.toString(), "stderr"));

        Path logPath = process.stderrLogPath().orElseThrow();
        Path firstBackup = logPath.resolveSibling(logPath.getFileName() + ".1");
        Path secondBackup = logPath.resolveSibling(logPath.getFileName() + ".2");
        for (int i = 0; i < 40 && process.isAlive(); i++) {
            Thread.sleep(50);
        }
        waitForStableFiles(logPath, firstBackup, secondBackup);
        process.stop(Duration.ofMillis(100));

        assertThat(logPath).exists();
        assertThat(firstBackup).exists();
        assertThat(secondBackup).exists();
        assertThat(logPath.resolveSibling(logPath.getFileName() + ".3")).doesNotExist();
        assertThat(Files.readString(logPath)).contains("stderr-line");
        assertThat(Files.readString(firstBackup)).contains("stderr-line");
        assertThat(Files.readString(secondBackup)).contains("stderr-line");
    }

    public static final class StderrWriter {
        public static void main(String[] args) {
            for (int i = 0; i < 80; i++) {
                System.err.println("stderr-line-" + i + " abcdefghijklmnopqrstuvwxyz");
            }
        }
    }

    private static void waitForStableFiles(Path... paths) throws Exception {
        long[] previousSizes = new long[paths.length];
        int stableSamples = 0;
        for (int attempt = 0; attempt < 80; attempt++) {
            boolean allExist = true;
            boolean stable = true;
            for (int i = 0; i < paths.length; i++) {
                if (!Files.exists(paths[i])) {
                    allExist = false;
                    stable = false;
                    previousSizes[i] = -1;
                    continue;
                }
                long size;
                try {
                    size = Files.size(paths[i]);
                } catch (IOException e) {
                    allExist = false;
                    stable = false;
                    previousSizes[i] = -1;
                    continue;
                }
                stable = stable && size == previousSizes[i];
                previousSizes[i] = size;
            }
            if (allExist && stable && ++stableSamples >= 2) {
                return;
            }
            Thread.sleep(50);
        }
    }
}
