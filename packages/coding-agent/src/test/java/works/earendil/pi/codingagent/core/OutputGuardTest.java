package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OutputGuardTest {
    @Test
    void redirectsTakenOverStdoutToStderrAndKeepsRawStdout() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        OutputGuard guard = new OutputGuard();

        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            guard.takeOverStdout();

            System.out.print("normal");
            guard.writeRawStdout("raw");
            guard.flushRawStdout().get(1, TimeUnit.SECONDS);

            assertThat(guard.isStdoutTakenOver()).isTrue();
            assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("raw");
            assertThat(err.toString(StandardCharsets.UTF_8)).isEqualTo("normal");
        } finally {
            guard.restoreStdout();
            System.setOut(originalOut);
            System.setErr(originalErr);
        }

        assertThat(guard.isStdoutTakenOver()).isFalse();
    }
}
