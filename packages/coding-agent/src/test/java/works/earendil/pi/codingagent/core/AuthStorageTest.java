package works.earendil.pi.codingagent.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthStorageTest {
    @TempDir
    Path tempDir;

    @Test
    void resolvesConfigValueTemplatesAndCommands() {
        ConfigValueResolver.clearConfigValueCache();

        assertThat(ConfigValueResolver.getConfigValueEnvVarName("$TOKEN")).contains("TOKEN");
        assertThat(ConfigValueResolver.getConfigValueEnvVarNames("pre-$A-${B}-$$-$!"))
                .containsExactly("A", "B");
        assertThat(ConfigValueResolver.getMissingConfigValueEnvVarNames("$A-$B", Map.of("A", "one")))
                .containsExactly("B");
        assertThat(ConfigValueResolver.resolveConfigValue("pre-$TOKEN-${OTHER}-$$-$!",
                Map.of("TOKEN", "one", "OTHER", "two"))).contains("pre-one-two-$-!");
        assertThat(ConfigValueResolver.resolveConfigValue("$MISSING", Map.of())).isEmpty();
        assertThat(ConfigValueResolver.resolveConfigValue("!printf command-value", Map.of()))
                .contains("command-value");
        assertThat(ConfigValueResolver.resolveHeaders(Map.of("A", "$TOKEN", "B", "$MISSING"),
                Map.of("TOKEN", "one"))).containsExactly(Map.entry("A", "one"));
        assertThatThrownBy(() -> ConfigValueResolver.resolveConfigValueOrThrow("$A-$B", "api key", Map.of()))
                .hasMessageContaining("environment variables: A, B");
    }

    @Test
    void resolvesKnownProviderEnvironmentKeys() {
        assertThat(EnvApiKeys.findEnvKeys("anthropic",
                Map.of("ANTHROPIC_OAUTH_TOKEN", "oauth", "ANTHROPIC_API_KEY", "api")))
                .containsExactly("ANTHROPIC_OAUTH_TOKEN", "ANTHROPIC_API_KEY");
        assertThat(EnvApiKeys.getEnvApiKey("anthropic",
                Map.of("ANTHROPIC_OAUTH_TOKEN", "oauth", "ANTHROPIC_API_KEY", "api")))
                .contains("oauth");
        assertThat(EnvApiKeys.getEnvApiKey("amazon-bedrock", Map.of("AWS_PROFILE", "default")))
                .contains("<authenticated>");
        assertThat(EnvApiKeys.findEnvKeys("unknown", Map.of("OPENAI_API_KEY", "x"))).isNull();
    }

    @Test
    void storesListsReloadsAndRemovesApiKeyCredentials() {
        AuthStorage storage = AuthStorage.inMemory();

        storage.set("openai", new AuthStorage.ApiKeyCredential("$OPENAI_TOKEN", Map.of("OPENAI_TOKEN", "stored")));

        assertThat(storage.has("openai")).isTrue();
        assertThat(storage.hasAuth("openai")).isTrue();
        assertThat(storage.list()).containsExactly("openai");
        assertThat(storage.getProviderEnv("openai")).containsExactly(Map.entry("OPENAI_TOKEN", "stored"));
        assertThat(storage.getApiKey("openai")).contains("stored");
        assertThat(storage.getAuthStatus("openai").configured()).isTrue();
        assertThat(storage.getAuthStatus("openai").source()).isEqualTo(AuthStorage.AuthStatus.Source.STORED);

        storage.remove("openai");

        assertThat(storage.has("openai")).isFalse();
        assertThat(storage.getApiKey("openai", new AuthStorage.GetApiKeyOptions(false))).isEmpty();
    }

    @Test
    void runtimeOverrideWinsAndEnvironmentFallbackIsReported() {
        AuthStorage storage = AuthStorage.inMemory();
        storage.setEnvironment(Map.of(
                "OPENAI_API_KEY", "from-env",
                "AWS_ACCESS_KEY_ID", "aws-key",
                "AWS_SECRET_ACCESS_KEY", "aws-secret"));
        storage.set("openai", new AuthStorage.ApiKeyCredential("stored", null));
        storage.set("mistral", new AuthStorage.ApiKeyCredential("stored-mistral", null));
        storage.setRuntimeApiKey("openai", "runtime");
        storage.setRuntimeApiKey("anthropic", "runtime-anthropic");

        assertThat(storage.getApiKey("openai")).contains("runtime");
        assertThat(storage.getAuthStatus("openai").source()).isEqualTo(AuthStorage.AuthStatus.Source.STORED);
        assertThat(storage.listAuthStatuses().keySet())
                .containsExactly("openai", "mistral", "anthropic", "amazon-bedrock");
        assertThat(storage.listAuthStatuses().get("anthropic").source())
                .isEqualTo(AuthStorage.AuthStatus.Source.RUNTIME);
        assertThat(storage.listAuthStatuses().get("amazon-bedrock").source())
                .isEqualTo(AuthStorage.AuthStatus.Source.ENVIRONMENT);
        assertThat(storage.listAuthStatuses().get("amazon-bedrock").label())
                .isEqualTo("AWS credentials");

        storage.remove("openai");
        assertThat(storage.getApiKey("openai")).contains("runtime");
        storage.removeRuntimeApiKey("openai");

        AuthStorage.AuthStatus status = storage.getAuthStatus("openai");
        assertThat(status.configured()).isFalse();
        assertThat(status.source()).isEqualTo(AuthStorage.AuthStatus.Source.ENVIRONMENT);
        assertThat(status.label()).isEqualTo("OPENAI_API_KEY");
        assertThat(storage.getApiKey("openai")).contains("from-env");
    }

    @Test
    void fileBackendCreatesLockedPrivateAuthFile() throws Exception {
        Path authPath = tempDir.resolve("agent").resolve("auth.json");
        AuthStorage storage = AuthStorage.create(authPath);

        storage.set("openrouter", new AuthStorage.ApiKeyCredential("key", null));
        AuthStorage reopened = AuthStorage.create(authPath);

        assertThat(reopened.getApiKey("openrouter")).contains("key");
        assertThat(Files.exists(authPath)).isTrue();
        assertThat(Files.readString(authPath)).contains("\"openrouter\"");
    }

    @Test
    void refreshesExpiredOAuthCredentialWithStorageLock() {
        AuthStorage storage = AuthStorage.inMemory(Map.of("oauth-provider",
                new AuthStorage.OAuthCredential("old-access", "refresh", 0, Map.of())));
        FakeOAuthProvider provider = new FakeOAuthProvider(false);
        storage.registerOAuthProvider(provider);

        Optional<String> apiKey = storage.getApiKey("oauth-provider");

        assertThat(apiKey).contains("api:new-access");
        assertThat(provider.refreshCalls()).isEqualTo(1);
        assertThat((AuthStorage.OAuthCredential) storage.get("oauth-provider").orElseThrow())
                .extracting(AuthStorage.OAuthCredential::access)
                .isEqualTo("new-access");
    }

    @Test
    void reloadsAfterOAuthRefreshFailureAndUsesCredentialRefreshedElsewhere() {
        long expired = 0;
        String expiredJson = """
                {
                  "oauth-provider": {
                    "type": "oauth",
                    "access": "old-access",
                    "refresh": "refresh",
                    "expires": %d
                  }
                }
                """.formatted(expired);
        String refreshedJson = """
                {
                  "oauth-provider": {
                    "type": "oauth",
                    "access": "other-access",
                    "refresh": "refresh",
                    "expires": %d
                  }
                }
                """.formatted(Instant.now().plusSeconds(60).toEpochMilli());
        SwappingBackend backend = new SwappingBackend(expiredJson, refreshedJson);
        AuthStorage storage = AuthStorage.fromStorage(backend);
        storage.registerOAuthProvider(new FakeOAuthProvider(true));

        assertThat(storage.getApiKey("oauth-provider")).contains("api:other-access");
        assertThat(storage.drainErrors()).hasSize(1);
    }

    @Test
    void rejectsMalformedAuthStorageButKeepsErrorsDrainable() {
        AuthStorage.InMemoryAuthStorageBackend backend = new AuthStorage.InMemoryAuthStorageBackend();
        backend.withLock(current -> new AuthStorage.LockResult<>(null, "[]"));

        AuthStorage storage = AuthStorage.fromStorage(backend);

        assertThat(storage.drainErrors()).hasSize(1);
        assertThat(storage.list()).isEmpty();
    }

    private static final class FakeOAuthProvider implements AuthStorage.OAuthProvider {
        private final boolean failRefresh;
        private final AtomicInteger refreshCalls = new AtomicInteger();

        private FakeOAuthProvider(boolean failRefresh) {
            this.failRefresh = failRefresh;
        }

        @Override
        public String id() {
            return "oauth-provider";
        }

        @Override
        public AuthStorage.OAuthCredential login(AuthStorage.OAuthLoginCallbacks callbacks) {
            return new AuthStorage.OAuthCredential("login-access", "refresh",
                    Instant.now().plusSeconds(60).toEpochMilli(), Map.of());
        }

        @Override
        public AuthStorage.OAuthCredential refreshToken(AuthStorage.OAuthCredential credentials) {
            refreshCalls.incrementAndGet();
            if (failRefresh) {
                throw new IllegalStateException("refresh failed");
            }
            return new AuthStorage.OAuthCredential("new-access", credentials.refresh(),
                    Instant.now().plusSeconds(60).toEpochMilli(), Map.of());
        }

        @Override
        public String getApiKey(AuthStorage.OAuthCredential credentials) {
            return "api:" + credentials.access();
        }

        int refreshCalls() {
            return refreshCalls.get();
        }
    }

    private static final class SwappingBackend implements AuthStorage.AuthStorageBackend {
        private final String initial;
        private final String afterFailure;
        private int calls;

        private SwappingBackend(String initial, String afterFailure) {
            this.initial = initial;
            this.afterFailure = afterFailure;
        }

        @Override
        public synchronized <T> T withLock(AuthStorage.LockCallback<T> callback) {
            calls++;
            String current = calls <= 2 ? initial : afterFailure;
            AuthStorage.LockResult<T> result = callback.apply(current);
            return result.result();
        }
    }
}
