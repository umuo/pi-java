package works.earendil.pi.codingagent.core;

import works.earendil.pi.codingagent.tools.OutputAccumulator;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Consumer;

public final class BashExecutor {
    private BashExecutor() {
    }

    public record Options(Consumer<String> onChunk, Duration timeout) {
    }

    public record Result(String output, Integer exitCode, boolean cancelled, boolean truncated, Path fullOutputPath) {
    }

    public static Result execute(String command, Path cwd, BashOperations operations, Options options) throws Exception {
        OutputAccumulator output = new OutputAccumulator(new OutputAccumulator.Options().tempFilePrefix("pi-bash"));

        BashOperations.Result result = operations.exec(command, cwd, new BashOperations.Options(data -> {
            append(output, data);
            if (options != null && options.onChunk() != null) {
                String text = ShellSupport.sanitizeBinaryOutput(new String(data, StandardCharsets.UTF_8)).replace("\r", "");
                options.onChunk().accept(text);
            }
        }, options == null ? null : options.timeout()));

        output.finish();
        OutputAccumulator.Snapshot snapshot = output.snapshot(true);
        output.closeTempFile();
        String text = ShellSupport.sanitizeBinaryOutput(snapshot.content()).replace("\r", "");
        return new Result(text, result.exitCode(), false, snapshot.truncation().truncated(), snapshot.fullOutputPath());
    }

    private static void append(OutputAccumulator output, byte[] data) {
        try {
            output.append(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
