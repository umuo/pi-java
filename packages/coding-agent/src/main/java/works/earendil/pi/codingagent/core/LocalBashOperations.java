package works.earendil.pi.codingagent.core;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class LocalBashOperations implements BashOperations {
    private final String shellPath;

    public LocalBashOperations() {
        this(null);
    }

    public LocalBashOperations(String shellPath) {
        this.shellPath = shellPath;
    }

    @Override
    public Result exec(String command, Path cwd, Options options) throws Exception {
        if (!Files.exists(cwd)) {
            throw new IllegalArgumentException("Working directory does not exist: " + cwd + "\nCannot execute bash commands.");
        }
        ShellSupport.ShellConfig shell = ShellSupport.getShellConfig(shellPath);
        List<String> argv = new ArrayList<>();
        argv.add(shell.shell());
        argv.addAll(shell.args());
        argv.add(command);
        ProcessBuilder processBuilder = new ProcessBuilder(argv)
                .directory(cwd.toFile())
                .redirectErrorStream(true);
        processBuilder.environment().remove("PI_SESSION_ID");
        processBuilder.environment().remove("PI_SESSION_FILE");
        processBuilder.environment().remove("PI_PROVIDER");
        processBuilder.environment().remove("PI_MODEL");
        processBuilder.environment().remove("PI_REASONING_LEVEL");
        processBuilder.environment().putAll(options.environment());
        Process process = processBuilder.start();
        Thread reader = new Thread(() -> read(process.getInputStream(), options.onData()), "pi-bash-output-reader");
        reader.start();
        boolean timedOut = false;
        Duration timeout = options.timeout();
        if (timeout != null && !timeout.isZero() && !timeout.isNegative()) {
            timedOut = !process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            process.waitFor();
        }
        if (timedOut) {
            ExecCommand.killProcessTree(process);
            reader.join(1000);
            throw new IllegalStateException("timeout:" + timeout.toSeconds());
        }
        reader.join();
        return new Result(process.exitValue());
    }

    private static void read(InputStream input, java.util.function.Consumer<byte[]> onData) {
        try (input) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                byte[] chunk = java.util.Arrays.copyOf(buffer, read);
                onData.accept(chunk);
            }
        } catch (Exception ignored) {
        }
    }
}
