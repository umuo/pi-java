package works.earendil.pi.codingagent.core;

import java.util.Optional;
import java.util.function.BooleanSupplier;

public final class Telemetry {
    private Telemetry() {
    }

    public static boolean isInstallTelemetryEnabled(BooleanSupplier settingsValue) {
        return isInstallTelemetryEnabled(settingsValue, System.getenv("PI_TELEMETRY"));
    }

    public static boolean isInstallTelemetryEnabled(BooleanSupplier settingsValue, String telemetryEnv) {
        return telemetryEnv != null ? isTruthyEnvFlag(telemetryEnv) : settingsValue.getAsBoolean();
    }

    public static Optional<Boolean> envFlag(String value) {
        return value == null ? Optional.empty() : Optional.of(isTruthyEnvFlag(value));
    }

    private static boolean isTruthyEnvFlag(String value) {
        return value != null && (value.equals("1")
                || value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("yes"));
    }
}
