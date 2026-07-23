package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import works.earendil.pi.ai.model.Model;
import works.earendil.pi.ai.model.ThinkingLevel;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreUtilitiesTest {
    @TempDir
    Path tempDir;

    @Test
    void exposesDefaultsAndProviderDisplayNames() {
        assertThat(Defaults.DEFAULT_THINKING_LEVEL).isEqualTo(ThinkingLevel.MEDIUM);
        assertThat(ProviderDisplayNames.displayName("openai")).contains("OpenAI");
        assertThat(ProviderDisplayNames.displayName("xiaomi-token-plan-sgp"))
                .contains("Xiaomi MiMo Token Plan (Singapore)");
    }

    @Test
    void mergesProviderAttributionHeadersWithOverrides() {
        Model openRouter = model("openrouter", "https://openrouter.ai/api/v1");
        Map<String, String> headers = ProviderAttribution.mergeProviderAttributionHeaders(
                openRouter, () -> true, null, Map.of("X-OpenRouter-Title", "custom"));

        assertThat(headers).containsEntry("HTTP-Referer", "https://pi.dev");
        assertThat(headers).containsEntry("X-OpenRouter-Title", "custom");
        assertThat(ProviderAttribution.mergeProviderAttributionHeaders(openRouter, () -> false, null)).isNull();
    }

    @Test
    void addsOpenCodeSessionHeaders() {
        Model opencode = model("openai-compatible", "https://opencode.ai/v1");

        assertThat(ProviderAttribution.mergeProviderAttributionHeaders(opencode, () -> false, "session-1"))
                .containsEntry("x-opencode-session", "session-1")
                .containsEntry("x-opencode-client", "pi");
    }

    @Test
    void telemetryEnvOverridesSettingsValue() {
        assertThat(Telemetry.isInstallTelemetryEnabled(() -> false, "yes")).isTrue();
        assertThat(Telemetry.isInstallTelemetryEnabled(() -> true, "0")).isFalse();
        assertThat(Telemetry.isInstallTelemetryEnabled(() -> true, null)).isTrue();
    }

    @Test
    void timingsCollectAndPrintWhenEnabled() {
        MutableClock clock = new MutableClock(Instant.parse("2024-01-01T00:00:00Z"));
        Timings timings = new Timings(true, clock);
        timings.resetTimings("extensions");
        clock.advanceMillis(12);
        timings.time("load", "extensions");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        timings.printTimings(new PrintStream(out));

        assertThat(timings.timings("extensions")).containsExactly(new Timings.Timing("load", 12));
        assertThat(out.toString()).contains("--- Startup Timings: extensions ---", "TOTAL: 12ms");
    }

    @Test
    void detectsAndFormatsMissingSessionCwd() throws Exception {
        Path fallback = tempDir.resolve("fallback");
        Files.createDirectories(fallback);
        Path missing = tempDir.resolve("missing");
        Path sessionFile = tempDir.resolve("session.jsonl");
        SessionCwd.SessionCwdSource source = new SessionCwd.SessionCwdSource() {
            @Override
            public Path cwd() {
                return missing;
            }

            @Override
            public Optional<Path> sessionFile() {
                return Optional.of(sessionFile);
            }
        };

        SessionCwd.SessionCwdIssue issue = SessionCwd.getMissingSessionCwdIssue(source, fallback).orElseThrow();

        assertThat(SessionCwd.formatMissingSessionCwdError(issue))
                .contains("Stored session working directory does not exist", missing.toString(), sessionFile.toString());
        assertThat(SessionCwd.formatMissingSessionCwdPrompt(issue))
                .contains("continue in current cwd", fallback.toString());
        assertThatThrownBy(() -> SessionCwd.assertSessionCwdExists(source, fallback))
                .isInstanceOf(SessionCwd.MissingSessionCwdException.class);
    }

    @Test
    void expandsOnlyLeadingHomeDirectoryShellPaths() {
        String home = System.getProperty("user.home");

        assertThat(ShellSupport.expandHome("~/bin/custom-shell"))
                .isEqualTo(home + "/bin/custom-shell");
        assertThat(ShellSupport.expandHome("~other/bin/custom-shell"))
                .isEqualTo("~other/bin/custom-shell");
        assertThat(ShellSupport.expandHome("/bin/sh")).isEqualTo("/bin/sh");
    }

    private static Model model(String provider, String baseUrl) {
        Map<String, Object> options = new HashMap<>();
        options.put("baseUrl", baseUrl);
        return new Model(provider, "model", "Model", "api", 1000, 1000, true, false, options);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advanceMillis(long millis) {
            instant = instant.plusMillis(millis);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
