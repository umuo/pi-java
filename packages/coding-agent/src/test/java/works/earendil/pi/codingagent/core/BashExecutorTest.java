package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BashExecutorTest {
    @TempDir
    Path tempDir;

    @Test
    void executesCommandAndStreamsSanitizedOutput() throws Exception {
        List<String> chunks = new ArrayList<>();
        BashExecutor.Result result = BashExecutor.execute(
                "printf '\\033[31mhello\\033[0m\\n'",
                tempDir,
                new LocalBashOperations(),
                new BashExecutor.Options(chunks::add, Duration.ofSeconds(5)));

        assertThat(result.exitCode()).isZero();
        assertThat(result.output()).isEqualTo("hello\n");
        assertThat(String.join("", chunks)).isEqualTo("hello\n");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void execCommandCapturesStdoutStderrAndCode() throws Exception {
        ExecCommand.Result result = ExecCommand.exec("/bin/sh",
                List.of("-c", "echo out; echo err >&2; exit 7"),
                new ExecCommand.Options(tempDir, Duration.ofSeconds(5)));

        assertThat(result.stdout()).isEqualTo("out\n");
        assertThat(result.stderr()).isEqualTo("err\n");
        assertThat(result.code()).isEqualTo(7);
        assertThat(result.killed()).isFalse();
    }

    @Test
    void usesOutputAccumulatorForTruncatedFullOutput() throws Exception {
        BashExecutor.Result result = BashExecutor.execute(
                "ignored",
                tempDir,
                (command, cwd, options) -> {
                    for (int i = 0; i < 2100; i++) {
                        options.onData().accept(("line-" + i + "\n").getBytes(StandardCharsets.UTF_8));
                    }
                    return new BashOperations.Result(0);
                },
                new BashExecutor.Options(null, Duration.ofSeconds(5)));

        assertThat(result.truncated()).isTrue();
        assertThat(result.output()).startsWith("line-100");
        assertThat(result.output()).endsWith("line-2099");
        assertThat(result.fullOutputPath()).exists();
        assertThat(Files.readString(result.fullOutputPath())).contains("line-0\n").contains("line-2099\n");
    }
}
