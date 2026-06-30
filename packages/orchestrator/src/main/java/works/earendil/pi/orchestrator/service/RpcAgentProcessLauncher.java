package works.earendil.pi.orchestrator.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class RpcAgentProcessLauncher implements AgentProcessLauncher {
    private static final String CODING_AGENT_MAIN_CLASS = "works.earendil.pi.codingagent.cli.Main";
    private static final ConcurrentMap<Path, Object> LOG_LOCKS = new ConcurrentHashMap<>();

    private final List<String> command;
    private final Path stderrLogDir;
    private final LogRotationPolicy logRotationPolicy;

    public RpcAgentProcessLauncher(List<String> command) {
        this(command, null);
    }

    public RpcAgentProcessLauncher(List<String> command, Path stderrLogDir) {
        this(command, stderrLogDir, LogRotationPolicy.defaults());
    }

    public RpcAgentProcessLauncher(List<String> command, Path stderrLogDir, LogRotationPolicy logRotationPolicy) {
        if (command == null || command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        this.command = List.copyOf(command);
        this.stderrLogDir = stderrLogDir == null ? null : stderrLogDir.toAbsolutePath().normalize();
        this.logRotationPolicy = logRotationPolicy == null ? LogRotationPolicy.defaults() : logRotationPolicy;
    }

    public static RpcAgentProcessLauncher currentJava() {
        return currentJava(null);
    }

    public static RpcAgentProcessLauncher currentJava(Path stderrLogDir) {
        return currentJava(stderrLogDir, LogRotationPolicy.defaults());
    }

    public static RpcAgentProcessLauncher currentJava(Path stderrLogDir, LogRotationPolicy logRotationPolicy) {
        String javaHome = System.getProperty("java.home");
        String javaBin = Path.of(javaHome, "bin", isWindows() ? "java.exe" : "java").toString();
        String classPath = System.getProperty("java.class.path");
        return new RpcAgentProcessLauncher(List.of(javaBin, "-cp", classPath, CODING_AGENT_MAIN_CLASS, "--mode", "rpc"),
                stderrLogDir, logRotationPolicy);
    }

    @Override
    public AgentProcess start(StartRequest request) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (request.cwd() != null && !request.cwd().isBlank()) {
            builder.directory(Path.of(request.cwd()).toFile());
        }
        Process process = builder.start();
        return new ProcessAgentProcess(process, stderrLogPath(request.instanceId()), logRotationPolicy);
    }

    public List<String> command() {
        return command;
    }

    public Optional<Path> stderrLogDir() {
        return Optional.ofNullable(stderrLogDir);
    }

    public LogRotationPolicy logRotationPolicy() {
        return logRotationPolicy;
    }

    public record LogRotationPolicy(long maxBytes, int maxBackups) {
        private static final long DEFAULT_MAX_BYTES = 1024L * 1024L;
        private static final int DEFAULT_MAX_BACKUPS = 3;

        public LogRotationPolicy {
            if (maxBytes < 0) {
                throw new IllegalArgumentException("maxBytes must not be negative");
            }
            if (maxBackups < 0) {
                throw new IllegalArgumentException("maxBackups must not be negative");
            }
        }

        public static LogRotationPolicy defaults() {
            return new LogRotationPolicy(DEFAULT_MAX_BYTES, DEFAULT_MAX_BACKUPS);
        }

        public static LogRotationPolicy disabled() {
            return new LogRotationPolicy(0, 0);
        }

        private boolean enabled() {
            return maxBytes > 0 && maxBackups > 0;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    private Path stderrLogPath(String instanceId) throws IOException {
        if (stderrLogDir == null) {
            return null;
        }
        Files.createDirectories(stderrLogDir);
        String safeId = instanceId == null || instanceId.isBlank()
                ? "unknown"
                : instanceId.replaceAll("[^A-Za-z0-9._-]", "_");
        return stderrLogDir.resolve(safeId + ".stderr.log");
    }

    private static final class ProcessAgentProcess implements AgentProcess {
        private final Process process;
        private final BufferedWriter stdin;
        private final BlockingQueue<String> stdout = new LinkedBlockingQueue<>();
        private final Path stderrLogPath;
        private final Thread stdoutReader;
        private final Thread stderrDrainer;

        private ProcessAgentProcess(Process process, Path stderrLogPath, LogRotationPolicy logRotationPolicy) {
            this.process = process;
            this.stderrLogPath = stderrLogPath;
            this.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            this.stdoutReader = readerThread("pi-agent-rpc-stdout", process.getInputStream(), stdout);
            this.stderrDrainer = stderrThread("pi-agent-rpc-stderr", process.getErrorStream(), stderrLogPath,
                    logRotationPolicy);
            this.stdoutReader.start();
            this.stderrDrainer.start();
        }

        @Override
        public synchronized void sendLine(String line) throws IOException {
            if (!process.isAlive()) {
                throw new IOException("Agent RPC process is not alive");
            }
            stdin.write(line);
            stdin.newLine();
            stdin.flush();
        }

        @Override
        public Optional<String> readLine(Duration timeout) throws IOException {
            try {
                String line = stdout.poll(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
                return Optional.ofNullable(line);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for RPC response", e);
            }
        }

        @Override
        public boolean isAlive() {
            return process.isAlive();
        }

        @Override
        public Optional<Path> stderrLogPath() {
            return Optional.ofNullable(stderrLogPath);
        }

        @Override
        public synchronized void stop(Duration timeout) throws IOException {
            try {
                stdin.close();
            } catch (IOException ignored) {
            }
            if (!process.isAlive()) {
                return;
            }
            process.destroy();
            try {
                if (!process.waitFor(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                throw new IOException("Interrupted while stopping RPC process", e);
            }
        }

        private static Thread readerThread(String name, java.io.InputStream stream, BlockingQueue<String> sink) {
            Thread thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (sink != null) {
                            sink.offer(line);
                        }
                    }
                } catch (IOException ignored) {
                }
            }, name);
            thread.setDaemon(true);
            return thread;
        }

        private static Thread stderrThread(String name, java.io.InputStream stream, Path logPath,
                                           LogRotationPolicy logRotationPolicy) {
            Thread thread = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (logPath != null) {
                            appendStderrLine(logPath, line, logRotationPolicy);
                        }
                    }
                } catch (IOException ignored) {
                }
            }, name);
            thread.setDaemon(true);
            return thread;
        }

        private static void appendStderrLine(Path logPath, String line, LogRotationPolicy logRotationPolicy)
                throws IOException {
            String entry = line + System.lineSeparator();
            Object lock = LOG_LOCKS.computeIfAbsent(logPath.toAbsolutePath().normalize(), ignored -> new Object());
            synchronized (lock) {
                rotateIfNeeded(logPath, entry.getBytes(StandardCharsets.UTF_8).length, logRotationPolicy);
                Files.writeString(logPath, entry, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        }

        private static void rotateIfNeeded(Path logPath, long incomingBytes, LogRotationPolicy logRotationPolicy)
                throws IOException {
            if (logRotationPolicy == null || !logRotationPolicy.enabled() || !Files.exists(logPath)) {
                return;
            }
            long currentBytes = Files.size(logPath);
            if (currentBytes == 0 || currentBytes + incomingBytes <= logRotationPolicy.maxBytes()) {
                return;
            }

            for (int index = logRotationPolicy.maxBackups(); index >= 1; index--) {
                Path source = index == 1 ? logPath : rotatedPath(logPath, index - 1);
                Path target = rotatedPath(logPath, index);
                if (!Files.exists(source)) {
                    continue;
                }
                if (index == logRotationPolicy.maxBackups()) {
                    Files.deleteIfExists(target);
                }
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        private static Path rotatedPath(Path logPath, int index) {
            return logPath.resolveSibling(logPath.getFileName() + "." + index);
        }
    }
}
