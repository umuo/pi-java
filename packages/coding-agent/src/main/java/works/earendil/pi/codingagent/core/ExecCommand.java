package works.earendil.pi.codingagent.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class ExecCommand {
    private ExecCommand() {
    }

    public record Options(Path cwd, Duration timeout) {
    }

    public record Result(String stdout, String stderr, int code, boolean killed) {
    }

    public static Result exec(String command, List<String> args, Options options) throws IOException, InterruptedException {
        List<String> argv = new ArrayList<>();
        argv.add(command);
        argv.addAll(args);
        ProcessBuilder builder = new ProcessBuilder(argv);
        if (options != null && options.cwd() != null) {
            builder.directory(options.cwd().toFile());
        }
        Process process = builder.start();
        boolean killed = false;
        if (options != null && options.timeout() != null && !options.timeout().isZero() && !options.timeout().isNegative()) {
            if (!process.waitFor(options.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
                killed = true;
                killProcessTree(process);
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } else {
            process.waitFor();
        }
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = killed ? 143 : process.exitValue();
        return new Result(stdout, stderr, code, killed);
    }

    public static void killProcessTree(Process process) {
        ProcessHandle handle = process.toHandle();
        handle.descendants().forEach(ProcessHandle::destroy);
        handle.destroy();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        handle.descendants().filter(ProcessHandle::isAlive).forEach(ProcessHandle::destroyForcibly);
        if (handle.isAlive()) {
            handle.destroyForcibly();
        }
    }
}
