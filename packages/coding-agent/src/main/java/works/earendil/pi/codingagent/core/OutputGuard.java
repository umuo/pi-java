package works.earendil.pi.codingagent.core;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public final class OutputGuard {
    private static final ExecutorService RAW_STDOUT_EXECUTOR =
            Executors.newSingleThreadExecutor(new DaemonThreadFactory());

    private PrintStream rawStdout;
    private PrintStream rawStderr;
    private PrintStream originalStdout;
    private CompletableFuture<Void> rawStdoutTail = CompletableFuture.completedFuture(null);

    public synchronized void takeOverStdout() {
        if (isStdoutTakenOver()) {
            return;
        }
        rawStdout = System.out;
        rawStderr = System.err;
        originalStdout = System.out;
        System.setOut(new PrintStream(new StderrForwardingOutputStream(rawStderr), true, StandardCharsets.UTF_8));
    }

    public synchronized void restoreStdout() {
        if (!isStdoutTakenOver()) {
            return;
        }
        System.setOut(originalStdout);
        rawStdout = null;
        rawStderr = null;
        originalStdout = null;
    }

    public synchronized boolean isStdoutTakenOver() {
        return originalStdout != null;
    }

    public synchronized void writeRawStdout(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        PrintStream target = rawStdout == null ? System.out : rawStdout;
        rawStdoutTail = rawStdoutTail.thenRunAsync(() -> {
            target.print(text);
            target.flush();
        }, RAW_STDOUT_EXECUTOR);
    }

    public CompletableFuture<Void> waitForRawStdoutBackpressure() {
        while (true) {
            CompletableFuture<Void> tail;
            synchronized (this) {
                tail = rawStdoutTail;
            }
            tail.join();
            synchronized (this) {
                if (tail == rawStdoutTail) {
                    return CompletableFuture.completedFuture(null);
                }
            }
        }
    }

    public CompletableFuture<Void> flushRawStdout() {
        return waitForRawStdoutBackpressure().thenRun(() -> {
            PrintStream target;
            synchronized (this) {
                target = rawStdout == null ? System.out : rawStdout;
            }
            target.flush();
        });
    }

    private static final class StderrForwardingOutputStream extends OutputStream {
        private final PrintStream stderr;

        private StderrForwardingOutputStream(PrintStream stderr) {
            this.stderr = Objects.requireNonNull(stderr);
        }

        @Override
        public void write(int b) {
            stderr.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) {
            stderr.write(b, off, len);
        }

        @Override
        public void flush() {
            stderr.flush();
        }
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "pi-raw-stdout");
            thread.setDaemon(true);
            return thread;
        }
    }
}
