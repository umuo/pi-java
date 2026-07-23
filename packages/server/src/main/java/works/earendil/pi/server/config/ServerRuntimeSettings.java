package works.earendil.pi.server.config;

import java.time.Duration;

public record ServerRuntimeSettings(RestartSettings restart, LogRotationSettings logRotation) {
    private static final RestartSettings DEFAULT_RESTART = new RestartSettings(3, Duration.ofSeconds(30),
            Duration.ofMinutes(5));
    private static final LogRotationSettings DEFAULT_LOG_ROTATION = new LogRotationSettings(1024L * 1024L, 3);

    public ServerRuntimeSettings {
        restart = restart == null ? DEFAULT_RESTART : restart;
        logRotation = logRotation == null ? DEFAULT_LOG_ROTATION : logRotation;
    }

    public static ServerRuntimeSettings defaults() {
        return new ServerRuntimeSettings(DEFAULT_RESTART, DEFAULT_LOG_ROTATION);
    }

    public record RestartSettings(int maxAttempts, Duration baseBackoff, Duration maxBackoff) {
        public RestartSettings {
            if (maxAttempts < 1) {
                maxAttempts = DEFAULT_RESTART.maxAttempts();
            }
            baseBackoff = baseBackoff == null || baseBackoff.isNegative()
                    ? DEFAULT_RESTART.baseBackoff()
                    : baseBackoff;
            maxBackoff = maxBackoff == null || maxBackoff.isNegative()
                    ? DEFAULT_RESTART.maxBackoff()
                    : maxBackoff;
            if (maxBackoff.compareTo(baseBackoff) < 0) {
                maxBackoff = baseBackoff;
            }
        }
    }

    public record LogRotationSettings(long maxBytes, int maxBackups) {
        public LogRotationSettings {
            if (maxBytes < 0) {
                maxBytes = DEFAULT_LOG_ROTATION.maxBytes();
            }
            if (maxBackups < 0) {
                maxBackups = DEFAULT_LOG_ROTATION.maxBackups();
            }
        }
    }
}
