package works.earendil.pi.ai.provider;

import java.time.Duration;

public final class AssistantCallRetry {
    private AssistantCallRetry() {
    }

    public record Policy(int maxRetries, Duration initialDelay, Duration maxDelay) {
        public Policy {
            maxRetries = Math.max(0, maxRetries);
            initialDelay = initialDelay == null || initialDelay.isNegative() ? Duration.ZERO : initialDelay;
            maxDelay = maxDelay == null || maxDelay.isNegative() ? initialDelay : maxDelay;
        }
    }

    @FunctionalInterface
    public interface Call<T> {
        T execute(int attempt) throws Exception;
    }

    public interface Listener {
        default void retryScheduled(int attempt, Duration delay, Throwable error) {
        }

        default void attemptStarted(int attempt) {
        }

        default void attemptFinished(int attempt, boolean success, Throwable error) {
        }
    }

    public static <T> T execute(Call<T> call, Policy policy, Listener listener) throws Exception {
        if (call == null) {
            throw new IllegalArgumentException("Assistant call is required");
        }
        Policy effective = policy == null
                ? new Policy(2, Duration.ofMillis(250), Duration.ofSeconds(4))
                : policy;
        Listener events = listener == null ? new Listener() { } : listener;
        for (int attempt = 0; ; attempt++) {
            events.attemptStarted(attempt);
            try {
                T value = call.execute(attempt);
                events.attemptFinished(attempt, true, null);
                return value;
            } catch (Exception error) {
                events.attemptFinished(attempt, false, error);
                if (attempt >= effective.maxRetries() || !ProviderHttpSupport.isRetryableFailure(error)) {
                    throw error;
                }
                Duration delay = delay(attempt, effective);
                events.retryScheduled(attempt + 1, delay, error);
                if (!delay.isZero()) {
                    Thread.sleep(delay.toMillis());
                }
            }
        }
    }

    private static Duration delay(int failedAttempt, Policy policy) {
        long multiplier = 1L << Math.min(failedAttempt, 10);
        Duration delay = policy.initialDelay().multipliedBy(multiplier);
        return delay.compareTo(policy.maxDelay()) > 0 ? policy.maxDelay() : delay;
    }
}
