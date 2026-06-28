package works.earendil.pi.codingagent.util;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public final class Sleep {
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());

    private Sleep() {
    }

    public static CompletableFuture<Void> sleep(Duration duration) {
        return sleep(duration, null);
    }

    public static CompletableFuture<Void> sleep(Duration duration, CompletableFuture<?> abortSignal) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        if (abortSignal != null && abortSignal.isDone()) {
            result.completeExceptionally(new IllegalStateException("Aborted"));
            return result;
        }
        var scheduled = SCHEDULER.schedule(() -> result.complete(null),
                Math.max(0, duration.toMillis()), TimeUnit.MILLISECONDS);
        if (abortSignal != null) {
            abortSignal.whenComplete((ignored, error) -> {
                scheduled.cancel(false);
                result.completeExceptionally(new IllegalStateException("Aborted"));
            });
        }
        result.whenComplete((ignored, error) -> scheduled.cancel(false));
        return result;
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "pi-sleep");
            thread.setDaemon(true);
            return thread;
        }
    }
}
